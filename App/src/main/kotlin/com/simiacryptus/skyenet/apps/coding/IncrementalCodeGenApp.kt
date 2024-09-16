package com.simiacryptus.skyenet.apps.hybrid

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.AgentPatterns
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ApplicationServices.clientManager
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

class IncrementalCodeGenApp(
    applicationName: String = "Incremental Code Generation v1.1",
    path: String = "/incremental_codegen",
    domainName: String = "localhost",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
) {
    data class Settings(
        val model: ChatModels = OpenAIModels.GPT4o,
        val parsingModel: ChatModels = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.2,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            val settings = getSettings<Settings>(session, user)
            IncrementalCodeGenAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: OpenAIModels.GPT4o,
                parsingModel = settings?.parsingModel ?: OpenAIModels.GPT4oMini,
                temperature = settings?.temperature ?: 0.3,
            ).startProcess(userMessage = userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(IncrementalCodeGenApp::class.java)
    }
}

class IncrementalCodeGenAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModels = OpenAIModels.GPT4o,
    parsingModel: ChatModels = OpenAIModels.GPT4oMini,
    temperature: Double = 0.3,
    val actorMap: Map<ActorTypes, BaseActor<*, *>> = mapOf<ActorTypes, BaseActor<*, *>>(
        ActorTypes.TaskBreakdown to ParsedActor(
            resultClass = TaskBreakdownResult::class.java,
            prompt = """
      Analyze the user request and break it down into smaller, actionable tasks suitable for direct implementation in code.
      Each task should be clearly defined, with explicit mention of any specific requirements, constraints, and the expected outcome.
      Prioritize tasks based on dependencies and logical sequence of implementation. Provide a brief rationale for the division and ordering of tasks.
      """.trimIndent(),
            model = model,
            parsingModel = parsingModel,
            temperature = temperature,
        ),
        ActorTypes.CodeGenerator to CodingActor(
            interpreterClass = KotlinInterpreter::class,
            symbols = mapOf(),
            details = """
      Generate code that fulfills the specified tasks, ensuring the code is not only efficient and readable but also adheres to best practices in software development.
      The code should be well-structured, with clear separation of concerns and modularity to facilitate future maintenance and scalability.
      Include inline comments to explain complex logic, important decisions, and the purpose of major functions and modules. Consider edge cases and error handling in your implementation.
      """.trimIndent(),
            model = model,
            temperature = temperature,
        ),
        ActorTypes.CodeReviewer to SimpleActor(
            prompt = """
      Conduct a comprehensive review of the generated code, focusing on its efficiency, readability, maintainability, and adherence to coding standards and best practices.
      Evaluate the code's structure, naming conventions, modularity, and use of design patterns. Identify any potential improvements and provide specific, actionable suggestions for enhancing the code's quality, performance, and readability. Highlight any areas that may be prone to errors or bugs.
      """.trimIndent(),
            model = model,
            temperature = temperature,

            ),
        ActorTypes.DocumentationGenerator to SimpleActor(
            prompt = """
      Create detailed and clear documentation for the provided code, covering its purpose, functionality, inputs, outputs, and any assumptions or limitations.
      Use a structured and consistent format that facilitates easy understanding and navigation. Include code examples where applicable, and explain the rationale behind key design decisions and algorithm choices.
      Document any known issues or areas for improvement, providing guidance for future developers on how to extend or maintain the code.
      """.trimIndent(),
            model = model,
            temperature = temperature,
        )
    )
) : ActorSystem<IncrementalCodeGenAgent.ActorTypes>(
    actorMap.map { it.key.name to it.value }.toMap(),
    dataStorage,
    user,
    session
) {
    val documentationGeneratorActor by lazy { actorMap[ActorTypes.DocumentationGenerator] as SimpleActor }

    data class TaskBreakdownResult(
        val tasksByID: Map<String, Task>? = null,
        val finalTaskID: String? = null,
    )

    data class Task(
        val description: String? = null,
        var dependencies: List<String>? = null,
        val taskType: TaskType? = null,
    )

    enum class TaskType {
        Design,
        Coding_Schema,
        Coding_General,
        Coding_Tests,
        Documentation,
    }

    val taskBreakdownActor by lazy { actorMap[ActorTypes.TaskBreakdown] as ParsedActor<TaskBreakdownResult> }
    val codeGeneratorActor by lazy { actorMap[ActorTypes.CodeGenerator] as CodingActor }

    fun startProcess(userMessage: String) {
        val toInput = { it: String -> listOf(userMessage, it) }
        val highLevelPlan = Discussable(
            task = ui.newTask(),
            userMessage = { userMessage },
            heading = userMessage,
            initialResponse = { it: String -> taskBreakdownActor.answer(toInput(it), api = api) },
            outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
                //        renderMarkdown("${design.text}\n\n```json\n${toJson(design.obj)/*.indent("  ")*/}\n```")
                AgentPatterns.displayMapInTabs(
                    mapOf(
                        "Text" to renderMarkdown(design.text, ui = ui),
                        "JSON" to renderMarkdown("```json\n${toJson(design.obj)/*.indent("  ")*/}\n```", ui = ui),
                    )
                )
            },
            ui = ui,
            reviseResponse = { userMessages: List<Pair<String, Role>> ->
                taskBreakdownActor.respond(
                    messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
                        .toTypedArray<ApiModel.ChatMessage>()),
                    input = toInput(userMessage),
                    api = api
                )
            },
        ).call()
        val pool: ThreadPoolExecutor = clientManager.getPool(session, user)
        val genState = GenState(
            subTasks = highLevelPlan.obj.tasksByID?.toMutableMap() ?: mutableMapOf(),
            generatedCodes = mutableMapOf(),
            generatedDocs = mutableMapOf(),
            taskIds = executionOrder(highLevelPlan.obj.tasksByID ?: emptyMap()).toMutableList(),
            completedTasks = mutableListOf()
        )
        try {
            ui.newTask()
                .complete(
                    renderMarkdown(
                        "## Task Graph\n```mermaid\n${buildMermaidGraph(genState.subTasks)}\n```",
                        ui = ui
                    )
                )
            while (genState.taskIds.isNotEmpty()) {
                val taskId = genState.taskIds.removeAt(0)
                val subTask = genState.subTasks[taskId] ?: throw RuntimeException("Task not found: $taskId")
                subTask.dependencies
                    ?.associate { it to genState.taskFutures[it] }
                    ?.forEach { (id, future) ->
                        try {
                            future?.get() ?: log.warn("Dependency not found: $id")
                        } catch (e: Throwable) {
                            log.warn("Error", e)
                        }
                    }
                genState.taskFutures[taskId] = pool.submit {
                    runTask(
                        taskId = taskId,
                        subTask = subTask,
                        userMessage = userMessage,
                        highLevelPlan = highLevelPlan,
                        genState = genState
                    )
                }
            }
            genState.taskFutures.forEach { (id, future) ->
                try {
                    future.get() ?: log.warn("Dependency not found: $id")
                } catch (e: Throwable) {
                    log.warn("Error", e)
                }
            }
            genState.completedTasks.joinToString("\n") { taskId ->
                """
          // ${genState.subTasks[taskId]?.description ?: "Unknown"}
          ${genState.generatedCodes[taskId]?.code ?: ""}
        """.trimIndent()
            }.let { summary ->
                ui.newTask().complete(
                    renderMarkdown(
                        "# Completed Code\n```kotlin\n${summary.let { /*escapeHtml4*/(it)/*.indent("  ")*/ }}\n```",
                        ui = ui
                    )
                )
            }
        } catch (e: Throwable) {
            ui.newTask().error(ui, e)
            log.warn("Error during incremental code generation process", e)
        }
    }

    data class GenState(
        val subTasks: MutableMap<String, Task>,
        val generatedCodes: MutableMap<String, CodingActor.CodeResult>,
        val generatedDocs: MutableMap<String, String>,
        val taskIds: MutableList<String>,
        val completedTasks: MutableList<String>,
        val taskFutures: MutableMap<String, Future<*>> = mutableMapOf(),
    )

    private fun runTask(
        taskId: String,
        subTask: Task,
        userMessage: String,
        highLevelPlan: ParsedResponse<TaskBreakdownResult>,
        genState: GenState,
    ) {
        val task = ui.newTask()
        try {
            val dependencies = subTask.dependencies?.toMutableList() ?: mutableListOf()
            dependencies += getAllDependencies(subTask, genState.subTasks)
            task.add(
                renderMarkdown(
                    "## Task: ${subTask.description ?: ""}\n\nDependencies:\n${
                        dependencies.joinToString(
                            "\n"
                        ) { "- $it" }
                    }", ui = ui
                )
            )
            val priorCode = dependencies
                .flatMap { genState.subTasks[it]?.dependencies ?: emptyList() }
                .joinToString("\n") {
                    """
          // ${genState.subTasks[it]?.description ?: "Unknown"}
          ${genState.generatedCodes[it]?.code ?: ""}
        """.trimIndent()
                }
            when (subTask.taskType) {
                TaskType.Coding_General, TaskType.Coding_Tests, TaskType.Coding_Schema -> {
                    task.add(
                        renderMarkdown(
                            "Prior Code:\n```kotlin\n${priorCode.let { /*escapeHtml4*/(it)/*.indent("  ")*/ }}\n```",
                            ui = ui
                        )
                    )
                    val codeRequest = CodingActor.CodeRequest(
                        codePrefix = priorCode,
                        messages = listOf(
                            highLevelPlan.text to Role.user,
                            ("Build ${subTask.description ?: ""}") to Role.user
                        ),
                    )
                    val codeResult = codeGeneratorActor.answer(codeRequest, api)
                    task.complete(
                        renderMarkdown(
                            "## Generated Code\n```kotlin\n${codeResult.code.let { /*escapeHtml4*/(it)/*.indent("  ")*/ }}\n```\n",
                            ui = ui
                        )
                    )
                    genState.generatedCodes[taskId] = codeResult
                }

                TaskType.Documentation -> {
                    val docResult = documentationGeneratorActor.answer(listOf(priorCode), api)
                    task.complete(renderMarkdown("## Generated Documentation\n$docResult", ui = ui))
                    genState.generatedDocs[taskId] = docResult
                }

                TaskType.Design -> {
                    val input1 = "Expand ${subTask.description ?: ""}"
                    val toInput = { it: String ->
                        listOf(
                            userMessage,
                            highLevelPlan.text,
                            it
                        )
                    }
                    val subPlan = Discussable(
                        task = ui.newTask(),
                        userMessage = { input1 },
                        heading = "Expand ${subTask.description ?: ""}",
                        initialResponse = { it: String -> taskBreakdownActor.answer(toInput(it), api = api) },
                        outputFn = { design: ParsedResponse<TaskBreakdownResult> ->
                            //              renderMarkdown("${design.text}\n\n```json\n${toJson(design.obj)/*.indent("  ")*/}\n```")
                            AgentPatterns.displayMapInTabs(
                                mapOf(
                                    "Text" to renderMarkdown(design.text, ui = ui),
                                    "JSON" to renderMarkdown(
                                        "```json\n${toJson(design.obj)/*.indent("  ")*/}\n```",
                                        ui = ui
                                    ),
                                )
                            )
                        },
                        ui = ui,
                        reviseResponse = { userMessages: List<Pair<String, Role>> ->
                            taskBreakdownActor.respond(
                                messages = (userMessages.map {
                                    ApiModel.ChatMessage(
                                        it.second,
                                        it.first.toContentList()
                                    )
                                }.toTypedArray<ApiModel.ChatMessage>()),
                                input = toInput(input1),
                                api = api
                            )
                        },
                    ).call()
                    var newTasks = subPlan.obj.tasksByID
                    val conflictingKeys = newTasks?.keys?.intersect(genState.subTasks.keys)
                    newTasks = newTasks?.entries?.associate { (key, value) ->
                        (when {
                            conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
                            else -> key
                        }) to value.copy(dependencies = value.dependencies?.map { key ->
                            when {
                                conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
                                else -> key
                            }
                        })
                    }
                    genState.subTasks.putAll(newTasks ?: emptyMap())
                    executionOrder(newTasks ?: emptyMap()).reversed().forEach { genState.taskIds.add(0, it) }
                    genState.subTasks.values.forEach {
                        it.dependencies = it.dependencies?.map { dep ->
                            when {
                                dep == taskId -> subPlan.obj.finalTaskID ?: dep
                                else -> dep
                            }
                        }
                    }
                    task.complete(
                        renderMarkdown(
                            "## Task Dependency Graph\n```mermaid\n${buildMermaidGraph(genState.subTasks)}\n```",
                            ui = ui
                        )
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            task.error(ui, e)
            log.warn("Error during task execution", e)
        } finally {
            genState.completedTasks.add(taskId)
        }
    }

    private fun getAllDependencies(subTask: Task, subTasks: MutableMap<String, Task>): List<String> {
        return getAllDependenciesHelper(subTask, subTasks, mutableSetOf())
    }

    private fun getAllDependenciesHelper(
        subTask: Task,
        subTasks: MutableMap<String, Task>,
        visited: MutableSet<String>
    ): List<String> {
        val dependencies = subTask.dependencies?.toMutableList() ?: mutableListOf()
        subTask.dependencies?.forEach { dep ->
            if (dep in visited) return@forEach
            val subTask = subTasks[dep]
            if (subTask != null) {
                visited.add(dep)
                dependencies.addAll(getAllDependenciesHelper(subTask, subTasks, visited))
            }
        }
        return dependencies
    }

    private fun executionOrder(tasks: Map<String, Task>): List<String> {
        val taskIds: MutableList<String> = mutableListOf()
        val taskMap = tasks.toMutableMap()
        while (taskMap.isNotEmpty()) {
            val nextTasks = taskMap.filter { (_, task) -> task.dependencies?.all { taskIds.contains(it) } ?: true }
            if (nextTasks.isEmpty()) {
                throw RuntimeException("Circular dependency detected in task breakdown")
            }
            taskIds.addAll(nextTasks.keys)
            nextTasks.keys.forEach { taskMap.remove(it) }
        }
        return taskIds
    }

    private fun buildMermaidGraph(subTasks: Map<String, Task>): String {
        val graphBuilder = StringBuilder("graph TD;\n")
        val escapeMermaidCharacters: (String) -> String = { input ->
            input.replace("\"", "\\\"")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
        }
        subTasks.forEach { (taskId, task) ->
            val taskId = taskId.replace(" ", "_")
            val escapedDescription = escapeMermaidCharacters(task.description ?: "")
            graphBuilder.append("    ${taskId}[\"${escapedDescription}\"];\n")
            task.dependencies?.forEach { dependency ->
                graphBuilder.append("    ${dependency.replace(" ", "_")} --> ${taskId};\n")
            }
        }
        return graphBuilder.toString()
    }

    enum class ActorTypes {
        TaskBreakdown,
        CodeGenerator,
        CodeReviewer,
        DocumentationGenerator,
    }

    companion object {
        private val log = LoggerFactory.getLogger(IncrementalCodeGenAgent::class.java)
    }
}
