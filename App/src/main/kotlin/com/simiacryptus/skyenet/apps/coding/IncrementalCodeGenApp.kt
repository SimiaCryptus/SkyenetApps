package com.simiacryptus.skyenet.apps.hybrid

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.AgentPatterns
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory

class IncrementalCodeGenApp(
  applicationName: String = "Incremental Code Generation v1.0",
  path: String = "/incremental_codegen",
  domainName: String = "localhost",
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
) {
  data class Settings(
    val model: ChatModels = ChatModels.GPT4Turbo,
    val parsingModel: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.2,
    val budget: Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
      IncrementalCodeGenAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT4Turbo,
        parsingModel = settings?.parsingModel ?: ChatModels.GPT35Turbo,
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
  model: ChatModels = ChatModels.GPT4Turbo,
  parsingModel: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
  val actorMap: Map<ActorTypes, BaseActor<*, *>> = mapOf<ActorTypes, BaseActor<*, *>>(
    ActorTypes.TaskBreakdown to ParsedActor(
      resultClass = TaskBreakdownResult::class.java,
      prompt = """
       Break down the user request into smaller, manageable tasks, focusing on identifying clear, actionable items that can be individually addressed. 
       Ensure each task is well-defined and includes any specific requirements or constraints.
        """.trimIndent(),
      model = model,
      parsingModel = parsingModel,
      temperature = temperature,
    ),
    ActorTypes.CodeGenerator to CodingActor(
      interpreterClass = KotlinInterpreter::class,
      symbols = mapOf(),
      details = """
       Generate code based on the specified tasks, ensuring the code is efficient, readable, and well-structured. 
       Include comments to explain complex logic or important decisions made during the coding process.
        """.trimIndent(),
      model = model,
      temperature = temperature,
    ),
    ActorTypes.CodeReviewer to SimpleActor(
      prompt = """
       Review the generated code for optimization and best practices, focusing on identifying potential improvements in code efficiency, readability, and adherence to coding standards. 
       Provide specific suggestions for enhancements.
        """.trimIndent(),
      model = model,
      temperature = temperature,

      ),
    ActorTypes.DocumentationGenerator to SimpleActor(
      prompt = """
      Generate comprehensive and clear documentation for the provided code snippets, including descriptions of the code's purpose, inputs, outputs, and any assumptions or limitations. 
      Use a structured format that is easy to follow.
       """.trimIndent(),
      model = model,
      temperature = temperature,
    )
  )
) : ActorSystem<IncrementalCodeGenAgent.ActorTypes>(
  actorMap.map { it.key.name to it.value.javaClass }.toMap(),
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
    CodeGeneration,
    CodeReview,
    Documentation,
    Testing,
  }

  val taskBreakdownActor by lazy { actorMap[ActorTypes.TaskBreakdown] as ParsedActor<TaskBreakdownResult> }
  val codeGeneratorActor by lazy { actorMap[ActorTypes.CodeGenerator] as CodingActor }
  val codeReviewerActor by lazy { actorMap[ActorTypes.CodeReviewer] as SimpleActor }

  fun startProcess(userMessage: String) {
    val highLevelPlan = AgentPatterns.iterate(
      input = userMessage,
      heading = userMessage,
      actor = taskBreakdownActor,
      toInput = { listOf(userMessage, it) },
      api = api,
      ui = ui,
      outputFn = { task, design ->
        task.add(renderMarkdown("${design.text}\n\n```json\n${toJson(design.obj)}\n```"))
      }
    )

    val task = ui.newTask()
    try {
      var subTasks = highLevelPlan.obj.tasksByID?.toMutableMap() ?: mutableMapOf()
      val generatedCodes = mutableMapOf<String, CodingActor.CodeResult>()
      val generatedDocs = mutableMapOf<String, String>()

      task.add(renderMarkdown("## Task Dependency Graph\n```mermaid\n${buildMermaidGraph(subTasks ?: emptyMap())}\n```"))

      val taskIds = executionOrder(subTasks ?: emptyMap()).toMutableList()
      val completedTasks = mutableListOf<String>()
      while (taskIds.isNotEmpty()) {
        val taskId = taskIds.removeAt(0)
        val subTask = subTasks?.get(taskId) ?: throw RuntimeException("Task not found: $taskId")
        val dependencies = subTask.dependencies?.associate { it to subTasks[it] }
        val priorCode =
          dependencies?.filter { it.value?.taskType == TaskType.CodeGeneration }?.entries?.joinToString("\n") { (id, task) ->
            generatedCodes[id]?.code ?: throw RuntimeException("Code not found for dependency: $id")
          }
        try {
          when (subTask.taskType) {

            TaskType.CodeGeneration -> {
              val codeRequest = CodingActor.CodeRequest(
                codePrefix = priorCode ?: "",
                messages = listOf(
                  highLevelPlan.text to ApiModel.Role.user,
                  ("Build ${subTask.description ?: ""}") to ApiModel.Role.user
                ),
              )
              val codeResult = codeGeneratorActor.answer(codeRequest, api)
              ui.newTask().add(renderMarkdown("Generated Code: \n```kotlin\n${codeResult.code}\n```\n"))
              generatedCodes[taskId] = codeResult
            }

            TaskType.CodeReview -> {
              val reviewResult = codeReviewerActor.answer(listOf(priorCode ?: ""), api)
              ui.newTask().add(renderMarkdown("Code Review: $reviewResult"))
            }

            TaskType.Documentation -> {
              val docResult = documentationGeneratorActor.answer(listOf(priorCode ?: ""), api)
              ui.newTask().add(renderMarkdown("Generated Documentation: $docResult"))
              generatedDocs[taskId] = docResult
            }

            TaskType.Design -> {
              val subPlan = AgentPatterns.iterate(
                input = "Expand ${ subTask.description ?: "" }",
                heading = "Expand ${ subTask.description ?: "" }",
                actor = taskBreakdownActor,
                toInput = { listOf(
                  userMessage,
                  highLevelPlan.text,
                  it
                ) },
                api = api,
                ui = ui,
                outputFn = { task, design ->
                  task.add(renderMarkdown("${design.text}\n\n```json\n${toJson(design.obj)}\n```"))
                }
              )
              var newTasks = subPlan.obj.tasksByID
              val conflictingKeys = newTasks?.keys?.intersect(subTasks.keys)
              newTasks = newTasks?.entries?.associate { (key, value) -> (when {
                conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
                else -> key
              }) to value.copy(dependencies = value.dependencies?.map { key -> when {
                conflictingKeys?.contains(key) == true -> "${taskId}_${key}"
                else -> key
              } }) }
              subTasks.putAll(newTasks ?: emptyMap())
              executionOrder(newTasks ?: emptyMap()).reversed().forEach { taskIds.add(0, it) }
              subTasks.values.forEach { it.dependencies = it.dependencies?.map { dep -> when {
                dep == taskId -> subPlan.obj.finalTaskID ?: dep
                else -> dep
              } } }
              ui.newTask().add(renderMarkdown("## Task Dependency Graph\n```mermaid\n${buildMermaidGraph(subTasks)}\n```"))
            }

            else -> null
          }
        } finally {
          completedTasks.add(taskId)
        }
      }

      task.complete("Process completed successfully.")
    } catch (e: Throwable) {
      task.error(ui, e)
      log.warn("Error during incremental code generation process", e)
    }
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
      // Add node for the task
      val taskId = taskId.replace(" ", "_")
      val escapedDescription = escapeMermaidCharacters(task.description ?: "")
      graphBuilder.append("    ${taskId}[\"${escapedDescription}\"];\n")
      // Add edges for dependencies
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
