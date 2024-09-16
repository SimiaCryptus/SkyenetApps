package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.camelCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.imports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.pascalCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.sortCode
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.stripImports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.upperSnakeCase
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.interpreter.Interpreter
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.function.Function
import kotlin.reflect.KClass

open class MetaAgentApp(
    applicationName: String = "Meta-Agent-Agent v1.1",
    temperature: Double = 0.1,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/meta_agent",
) {
    override val description: String
        @Language("Markdown")
        get() = "<div>${

            renderMarkdown(
                """
                **It's agents all the way down!**
                Welcome to the MetaAgentAgent, an innovative tool designed to streamline the process of creating custom AI agents. 
                This powerful system leverages the capabilities of OpenAI's language models to assist you in designing and implementing your very own AI agent tailored to your specific needs and preferences.
                
                Here's how it works:
                1. **Provide a Prompt**: Describe the purpose of your agent.
                2. **High Level Design**: A multi-step high-level design process will guide you through the creation of your agent. During each phase, you can provide feedback and iterate. When you're satisfied with the design, you can move on to the next step.
                3. **Implementation**: The MetaAgentAgent will generate the code for your agent, which you can then download and tailor to your needs.
                
                Get started with MetaAgentAgent today and bring your custom AI agent to life with ease! 
                Whether you're looking to automate customer service, streamline data analysis, or create an interactive chatbot, MetaAgentAgent is here to help you make it happen.
            """.trimIndent()
            )
        }</div>"

    data class Settings(
        val model: ChatModels = OpenAIModels.GPT4o,
        val validateCode: Boolean = true,
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
            MetaAgentAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
                autoEvaluate = settings?.validateCode ?: true,
                temperature = settings?.temperature ?: 0.3,
            ).buildAgent(userMessage = userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetaAgentApp::class.java)
    }

}

open class MetaAgentAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModels = OpenAIModels.GPT4oMini,
    var autoEvaluate: Boolean = true,
    temperature: Double = 0.3,
) : ActorSystem<MetaAgentActors.ActorType>(
    MetaAgentActors(
        symbols = mapOf(
            //"user" to user,
            //"session" to session,
            //"dataStorage" to dataStorage,
            "ui" to ui,
            "api" to api,
            "pool" to ApplicationServices.clientManager.getPool(session, user),
        ),
        model = model,
        temperature = temperature,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    private val highLevelDesigner by lazy { getActor(MetaAgentActors.ActorType.HIGH_LEVEL) as SimpleActor }

    @Suppress("UNCHECKED_CAST")
    private val detailDesigner by lazy { getActor(MetaAgentActors.ActorType.DETAIL) as ParsedActor<MetaAgentActors.AgentFlowDesign> }

    @Suppress("UNCHECKED_CAST")
    private val actorDesigner by lazy { getActor(MetaAgentActors.ActorType.ACTORS) as ParsedActor<MetaAgentActors.AgentActorDesign> }
    private val simpleActorDesigner by lazy { getActor(MetaAgentActors.ActorType.SIMPLE) as CodingActor }
    private val imageActorDesigner by lazy { getActor(MetaAgentActors.ActorType.IMAGE) as CodingActor }
    private val parsedActorDesigner by lazy { getActor(MetaAgentActors.ActorType.PARSED) as CodingActor }
    private val codingActorDesigner by lazy { getActor(MetaAgentActors.ActorType.CODING) as CodingActor }
    private val flowStepDesigner by lazy { getActor(MetaAgentActors.ActorType.FLOW_STEP) as CodingActor }


    @Language("kotlin")
    val standardImports = """
        |import com.simiacryptus.jopenai.API
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.core.actors.BaseActor
        |import com.simiacryptus.skyenet.core.actors.ActorSystem
        |import com.simiacryptus.skyenet.core.actors.CodingActor
        |import com.simiacryptus.skyenet.core.actors.ParsedActor
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |import com.simiacryptus.skyenet.core.platform.file.DataStorage
        |import com.simiacryptus.skyenet.core.platform.Session
        |import com.simiacryptus.skyenet.core.platform.StorageInterface
        |import com.simiacryptus.skyenet.core.platform.User
        |import com.simiacryptus.skyenet.webui.application.ApplicationServer
        |import com.simiacryptus.skyenet.webui.session.*
        |import com.simiacryptus.skyenet.webui.application.ApplicationInterface
        |import java.awt.image.BufferedImage
        |import org.slf4j.LoggerFactory
        |""".trimMargin()

    fun buildAgent(userMessage: String) {
        val design = initialDesign(userMessage)
        val actImpls = implementActors(userMessage, design)
        val flowImpl = getFlowStepCode(userMessage, design, actImpls)
        val mainImpl = getMainFunction(userMessage, design, actImpls, flowImpl)
        buildFinalCode(actImpls, flowImpl, mainImpl, design)
    }

    private fun buildFinalCode(
        actImpls: Map<String, String>,
        flowImpl: Map<String, String>,
        mainImpl: String,
        design: ParsedResponse<MetaAgentActors.AgentDesign>
    ) {
        val task = ui.newTask()
        try {
            task.header("Final Code")

            val imports =
                (actImpls.values + flowImpl.values + listOf(mainImpl)).flatMap { it.imports() }
                    .toSortedSet().joinToString("\n")

            val classBaseName = design.obj.name?.pascalCase() ?: "MyAgent"

            val actorInits = design.obj.actors?.joinToString("\n") { actor ->
                """private val ${actor.name.camelCase()} by lazy { getActor(${classBaseName}Actors.ActorType.${actor.name.upperSnakeCase()}) as ${
                    when (actor.type.lowercase()) {
                        "simple" -> "SimpleActor"
                        "parsed" -> "ParsedActor<${actor.simpleClassName}>"
                        "coding" -> "CodingActor"
                        "image" -> "ImageActor"
                        "tts" -> "TextToSpeechActor"
                        else -> throw IllegalArgumentException("Unknown actor type: ${actor.type}")
                    }
                } }"""
            } ?: ""

            val actorMapEntries = design.obj.actors?.joinToString("\n") { actor ->
                """ActorType.${actor.name.upperSnakeCase()} to ${actor.name.camelCase()},"""
            } ?: ""

            val actorEnumDefs = design.obj.actors?.joinToString("\n") { actor ->
                """${actor.name.upperSnakeCase()},"""
            } ?: ""

            @Language("kotlin") val appCode = """
        |$standardImports
        |
        |open class ${classBaseName}App(
        |    applicationName: String = "${design.obj.name}",
        |    path: String = "/${design.obj.path ?: ""}",
        |) : ApplicationServer(
        |    applicationName = applicationName,
        |    path = path,
        |) {
        |
        |    data class Settings(
        |        val model: ChatModels = OpenAIModels.GPT35Turbo,
        |        val temperature: Double = 0.1,
        |    )
        |    override val settingsClass: Class<*> get() = Settings::class.java
        |    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T
        |
        |    override fun userMessage(
        |        session: Session,
        |        user: User?,
        |        userMessage: String,
        |        ui: ApplicationInterface,
        |        api: API
        |    ) {
        |        try {
        |            val settings = getSettings<Settings>(session, user)
        |            ${classBaseName}Agent(
        |                user = user,
        |                session = session,
        |                dataStorage = dataStorage,
        |                api = api,
        |                ui = ui,
        |                model = settings?.model ?: OpenAIModels.GPT35Turbo,
        |                temperature = settings?.temperature ?: 0.3,
        |            ).${design.obj.name?.camelCase()}(userMessage)
        |        } catch (e: Throwable) {
        |            log.warn("Error", e)
        |        }
        |    }
        |
        |    companion object {
        |        private val log = LoggerFactory.getLogger(${classBaseName}App::class.java)
        |    }
        |
        |}
        """.trimMargin()

            @Language("kotlin") var agentCode = """
        |$standardImports
        |
        |open class ${classBaseName}Agent(
        |    user: User?,
        |    session: Session,
        |    dataStorage: StorageInterface,
        |    val ui: ApplicationInterface,
        |    val api: API,
        |    model: ChatModels = OpenAIModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |) : ActorSystem<${classBaseName}Actors.ActorType>(${classBaseName}Actors(
        |    model = model,
        |    temperature = temperature,
        |).actorMap, dataStorage, user, session) {
        |
        |    @Suppress("UNCHECKED_CAST")
        |    ${actorInits.indent("    ")}
        |
        |    ${mainImpl.trimIndent().stripImports().indent("    ")}
        |
        |    ${flowImpl.values.joinToString("\n\n") { flowStep -> flowStep.trimIndent() }.stripImports().indent("    ")}
        |
        |    companion object {
        |        private val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Agent::class.java)
        |
        |    }
        |}
        """.trimMargin()

            agentCode = design.obj.actors?.map { it.simpleClassName }?.fold(agentCode)
            { code, type -> code.replace("(?<![\\w.])$type(?![\\w])".toRegex(), "${classBaseName}Actors.$type") }
                ?: agentCode

            @Language("kotlin") val agentsCode = """
        |$standardImports
        |
        |class ${classBaseName}Actors(
        |    val model: ChatModels = OpenAIModels.GPT4o,
        |    val temperature: Double = 0.3,
        |) {
        |
        |    ${actImpls.values.joinToString("\n\n") { it.trimIndent() }.stripImports().indent("    ")}
        |
        |    enum class ActorType {
        |        ${actorEnumDefs.indent("        ")}
        |    }
        |
        |    val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
        |        ${actorMapEntries.indent("        ")}
        |    )
        |
        |    companion object {
        |        val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Actors::class.java)
        |    }
        |}
        """.trimMargin()

            //language=MARKDOWN
            val code = """
        |```kotlin
        |${listOf(imports, appCode, agentCode, agentsCode).joinToString("\n\n") { it.trimIndent() }.sortCode()}
        |```
        |""".trimMargin()

            //language=HTML
            task.complete(renderMarkdown(code, ui = ui))
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    private fun initialDesign(input: String): ParsedResponse<MetaAgentActors.AgentDesign> {
        val toInput = { it: String -> listOf(it) }
        val highLevelDesign = Discussable(
            task = ui.newTask(),
            userMessage = { input },
            heading = renderMarkdown(input, ui = ui),
            initialResponse = { it: String -> highLevelDesigner.answer(toInput(it), api = api) },
            outputFn = { design -> renderMarkdown(design.toString(), ui = ui) },
            ui = ui,
            reviseResponse = { userMessages: List<Pair<String, Role>> ->
                highLevelDesigner.respond(
                    messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
                        .toTypedArray<ApiModel.ChatMessage>()),
                    input = toInput(input),
                    api = api
                )
            },
        ).call()
        val toInput1 = { it: String -> listOf(it) }
        val flowDesign = Discussable(
            task = ui.newTask(),
            userMessage = { highLevelDesign },
            heading = "Flow Design",
            initialResponse = { it: String -> detailDesigner.answer(toInput1(it), api = api) },
            outputFn = { design: ParsedResponse<MetaAgentActors.AgentFlowDesign> ->
                try {
                    renderMarkdown(design.toString(), ui = ui) + JsonUtil.toJson(design.obj)
                } catch (e: Throwable) {
                    renderMarkdown(e.message ?: e.toString(), ui = ui)
                }
            },
            ui = ui,
            reviseResponse = { userMessages: List<Pair<String, Role>> ->
                detailDesigner.respond(
                    messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
                        .toTypedArray<ApiModel.ChatMessage>()),
                    input = toInput1(highLevelDesign),
                    api = api
                )
            },
        ).call()
        val toInput2 = { it: String -> listOf(it) }
        val actorDesignParsedResponse: ParsedResponse<MetaAgentActors.AgentActorDesign> = Discussable(
            task = ui.newTask(),
            userMessage = { flowDesign.text },
            heading = "Actor Design",
            initialResponse = { it: String -> actorDesigner.answer(toInput2(it), api = api) },
            outputFn = { design: ParsedResponse<MetaAgentActors.AgentActorDesign> ->
                try {
                    renderMarkdown(design.toString(), ui = ui) + JsonUtil.toJson(design.obj)
                } catch (e: Throwable) {
                    renderMarkdown(e.message ?: e.toString(), ui = ui)
                }
            },
            ui = ui,
            reviseResponse = { userMessages: List<Pair<String, Role>> ->
                actorDesigner.respond(
                    messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
                        .toTypedArray<ApiModel.ChatMessage>()),
                    input = toInput2(flowDesign.text),
                    api = api
                )
            },
        ).call()
        return object : ParsedResponse<MetaAgentActors.AgentDesign>(MetaAgentActors.AgentDesign::class.java) {
            override val text get() = flowDesign.text + "\n" + actorDesignParsedResponse.text
            override val obj
                get() = MetaAgentActors.AgentDesign(
                    name = flowDesign.obj.name,
                    description = flowDesign.obj.description,
                    mainInput = flowDesign.obj.mainInput,
                    logicFlow = flowDesign.obj.logicFlow,
                    actors = actorDesignParsedResponse.obj.actors,
                )
        }
    }

    private fun getMainFunction(
        userMessage: String,
        design: ParsedResponse<MetaAgentActors.AgentDesign>,
        actorImpls: Map<String, String>,
        flowStepCode: Map<String, String>
    ): String {
        val task = ui.newTask()
        try {
            task.header("Main Function")
            val codeRequest = CodingActor.CodeRequest(
                messages = listOf(
                    userMessage to Role.user,
                    design.text to Role.assistant,
                    "Implement `fun ${design.obj.name?.camelCase()}(${
                        listOf(design.obj.mainInput!!)
                            .joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") }
                    })`" to Role.user
                ),
                codePrefix = (standardImports + (actorImpls.values + flowStepCode.values)
                    .joinToString("\n\n") { it.trimIndent() }).sortCode(),
                autoEvaluate = autoEvaluate
            )
            val mainFunction = execWrap { flowStepDesigner.answer(codeRequest, api = api).code }
            task.verbose(
                renderMarkdown(
                    """
          |```kotlin
          |$mainFunction
          |```
          """.trimMargin(), ui = ui
                ), tag = "div"
            )
            task.complete()
            return mainFunction
        } catch (e: CodingActor.FailedToImplementException) {
            task.verbose(e.code ?: throw e)
            task.error(ui, e)
            return e.code ?: throw e
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    private fun implementActors(
        userMessage: String,
        design: ParsedResponse<MetaAgentActors.AgentDesign>,
    ) = design.obj.actors?.map { actorDesign ->
        pool.submit<Pair<String, String>> {
            val task = ui.newTask()
            try {
                implementActor(task, actorDesign, userMessage, design)
            } catch (e: Throwable) {
                task.error(ui, e)
                throw e
            }
        }
    }?.toTypedArray()?.associate { it.get() } ?: mapOf()

    private fun implementActor(
        task: SessionTask,
        actorDesign: MetaAgentActors.ActorDesign,
        userMessage: String,
        design: ParsedResponse<MetaAgentActors.AgentDesign>
    ): Pair<String, String> {
        //language=HTML
        task.header("Actor: ${actorDesign.name}")
        val type = actorDesign.type
        val codeRequest = CodingActor.CodeRequest(
            listOf(
                userMessage to Role.user,
                design.text to Role.assistant,
                "Implement `val ${(actorDesign.name).camelCase()} : ${
                    when (type.lowercase()) {
                        "simple" -> "SimpleActor"
                        "parsed" -> "ParsedActor<${actorDesign.simpleClassName}>"
                        "coding" -> "CodingActor"
                        "image" -> "ImageActor"
                        "tts" -> "TextToSpeechActor"
                        else -> throw IllegalArgumentException("Unknown actor type: $type")
                    }
                }`" to Role.user
            ),
            autoEvaluate = autoEvaluate
        )
        val response = execWrap {
            when (type.lowercase()) {
                "simple" -> simpleActorDesigner.answer(codeRequest, api = api)
                "parsed" -> parsedActorDesigner.answer(codeRequest, api = api)
                "coding" -> codingActorDesigner.answer(codeRequest, api = api)
                "image" -> imageActorDesigner.answer(codeRequest, api = api)
                "mp3" -> throw NotImplementedError() // TODO: Implement
                else -> throw IllegalArgumentException("Unknown actor type: $type")
            }
        }
        val code = response.code
        //language=HTML
        task.verbose(
            renderMarkdown(
                """
        |```kotlin
        |$code
        |```
        """.trimMargin(), ui = ui
            ), tag = "div"
        )
        task.complete()
        return actorDesign.name to code
    }


    private fun <T> execWrap(fn: () -> T): T {
        val classLoader = Thread.currentThread().contextClassLoader
        val prevCL = KotlinInterpreter.classLoader
        KotlinInterpreter.classLoader = classLoader //req.javaClass.classLoader
        return try {
            WebAppClassLoader.runWithServerClassAccess {
                require(null != classLoader.loadClass("org.eclipse.jetty.server.Response"))
                require(null != classLoader.loadClass("org.eclipse.jetty.server.Request"))
                fn()
            }
        } finally {
            KotlinInterpreter.classLoader = prevCL
        }
    }

    private fun getFlowStepCode(
        userMessage: String,
        design: ParsedResponse<MetaAgentActors.AgentDesign>,
        actorImpls: Map<String, String>,
    ): Map<String, String> {
        val flowImpls = HashMap<String, String>()
        design.obj.logicFlow?.items?.forEach { logicFlowItem ->
            val message = ui.newTask()
            try {

                message.header("Logic Flow: ${logicFlowItem.name}")
                val code = try {
                    execWrap {
                        flowStepDesigner.answer(
                            CodingActor.CodeRequest(
                                messages = listOf(
                                    userMessage to Role.user,
                                    design.text to Role.assistant,
                                    "Implement `fun ${(logicFlowItem.name!!).camelCase()}(${
                                        logicFlowItem.inputs?.joinToString<MetaAgentActors.DataInfo>(", ") { (it.name ?: "") + " : " + (it.type ?: "") } ?: ""
                                    })`" to Role.user
                                ),
                                autoEvaluate = autoEvaluate,
                                codePrefix = (actorImpls.values + flowImpls.values)
                                    .joinToString("\n\n") { it.trimIndent() }.sortCode()
                            ), api = api
                        ).code
                    }
                } catch (e: CodingActor.FailedToImplementException) {
                    message.error(ui, e)
                    autoEvaluate = false
                    e.code
                }
                //language=HTML
                message.verbose(
                    renderMarkdown(
                        """
            |```kotlin
            |$code
            |```
            """.trimMargin(), ui = ui
                    ), tag = "div"
                )
                message.complete()
                flowImpls[logicFlowItem.name!!] = code!!
            } catch (e: Throwable) {
                message.error(ui, e)
                throw e
            }
        }
        return flowImpls
    }

    companion object
}

class MetaAgentActors(
    private val interpreterClass: KClass<out Interpreter> = KotlinInterpreter::class,
    val symbols: Map<String, Any> = mapOf(),
    val model: ChatModels = OpenAIModels.GPT4o,
    val temperature: Double = 0.3,
) {

    enum class ActorType {
        HIGH_LEVEL,
        DETAIL,
        SIMPLE,
        IMAGE,
        PARSED,
        CODING,
        FLOW_STEP,
        ACTORS,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.HIGH_LEVEL to highLevelDesigner(),
        ActorType.DETAIL to detailedDesigner(),
        ActorType.ACTORS to actorDesigner(),
        ActorType.SIMPLE to simpleActorDesigner(),
        ActorType.IMAGE to imageActorDesigner(),
        ActorType.PARSED to parsedActorDesigner(),
        ActorType.CODING to codingActorDesigner(),
        ActorType.FLOW_STEP to flowStepDesigner(),
    )

    interface FlowParser : Function<String, AgentFlowDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentFlowDesign
    }

    data class AgentFlowDesign(
        val name: String? = null,
        val description: String? = null,
        val mainInput: DataInfo? = null,
        val logicFlow: LogicFlow? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == logicFlow -> "logicFlow is required"
            null != logicFlow.validate() -> logicFlow.validate()
            else -> null
        }
    }

    data class AgentDesign(
        val name: String? = null,
        val path: String? = null,
        val description: String? = null,
        val mainInput: DataInfo? = null,
        val logicFlow: LogicFlow? = null,
        val actors: List<ActorDesign>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == logicFlow -> "logicFlow is required"
            null == actors -> "actors is required"
            actors.isEmpty() -> "actors is required"
            null != logicFlow.validate() -> logicFlow.validate()
            !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }
                .joinToString("\n")

            else -> null
        }
    }

    interface ActorParser : Function<String, AgentActorDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentActorDesign
    }

    data class AgentActorDesign(
        val actors: List<ActorDesign>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == actors -> "actors is required"
            actors.isEmpty() -> "actors is required"
            !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }
                .joinToString("\n")

            else -> null
        }
    }

    data class ActorDesign(
        @Description("Java class name of the actor")
        val name: String = "",
        val description: String? = null,
        @Description("simple, parsed, image, tts, or coding")
        val type: String = "",
        @Description("Simple actors: string; Image actors: image; Coding actors: code; Text-to-speech actors: mp3; Parsed actors: a simple java class name for the data structure")
        val resultClass: String = "",
    ) : ValidatedObject {
        val simpleClassName: String get() = resultClass.split(".").last()
        override fun validate(): String? = when {
            name.isEmpty() -> "name is required"
            name.chars().anyMatch { !Character.isJavaIdentifierPart(it) } -> "name must be a valid java identifier"
            type.isEmpty() -> "type is required"
            type.lowercase().notIn(
                "simple",
                "parsed",
                "coding",
                "image",
                "tts"
            ) -> "type must be simple, parsed, coding, tts, or image"

            resultClass.isEmpty() -> "resultType is required"
            resultClass.lowercase().notIn(
                "string",
                "code",
                "image",
                "mp3"
            ) && !validClassName(resultClass) -> "resultType must be string, code, image, mp3, or a valid class name"

            else -> null
        }

        private fun validClassName(resultType: String): Boolean {
            return when {
                resultType.isEmpty() -> false
                validClassNamePattern.matches(resultType) -> true
                else -> false
            }
        }

        companion object {
            val validClassNamePattern = "[A-Za-z][a-zA-Z0-9_<>.]{3,}".toRegex()
        }

    }

    data class LogicFlow(
        val items: List<LogicFlowItem>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = items?.map { it.validate() }?.firstOrNull { !it.isNullOrBlank() }
    }

    data class LogicFlowItem(
        val name: String? = null,
        val description: String? = null,
        val actors: List<String>? = null,
        @Description("symbol names of variables/values used as input to this step")
        val inputs: List<DataInfo>? = null,
        @Description("description of the output of this step")
        val output: DataInfo? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            //inputs?.isEmpty() != false && inputs?.isEmpty() != false -> "inputs is required"
            else -> null
        }
    }

    data class DataInfo(
        val name: String? = null,
        val description: String? = null,
        val type: String? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            null == type -> "type is required"
            type.isEmpty() -> "type is required"
            else -> null
        }
    }

    @Language("Markdown")
    fun highLevelDesigner() = SimpleActor(
        model = model,
        prompt = """
            You are a high-level software architect.
            
            Your task is to gather requirements and detail the idea provided by the user query.
            You will propose detailed requirements for inputs, outputs, and logic.
            Your proposed design can be reviewed by the user, who may request changes.
            The system in general will be an AI-based automated assistant using an interactive web-based interface.
        """.trimIndent().trim(),
        temperature = temperature
    )

    @Language("Markdown")
    fun detailedDesigner() = ParsedActor(
        FlowParser::class.java,
        prompt = """
            You are a detailed software designer.
            
            Your task is to expand on the high-level architecture and conceptualize the architecture of an "agent" system that uses gpt "actors" to model a creative process.
            The system should be procedural in its overall structure, with creative steps modeled by gpt actors.
            
            System interactions can include:
            1. File storage and retrieval - the user and application both have access to a shared session folder 
            2. Threading operations - Individual actors and actions can be run in parallel using java threading
            
            User interactions can include:
            1. Messages (html & images) sent to the web interface
            2. User input via text input or link clicks, handled via callbacks
            
            Important design patterns include:
            1. Iterative Thinking - user feedback loops and step-by-step thinking using sequences of specialized actors
            2. Parse-and-Expand - an initial actor is used to generate a data structure which is then expanded by various potentially recursive actors
            3. File Builder - the main web interface is used to monitor and control the application, but main output is generally written to files and displayed as links
            
            Output should include:
            1. Details on each individual actor including purpose and description
            2. Pseudocode for the overall logic flow
            3. Data structures used to pass and handle information
        """.trimIndent().trim(),
        model = model,
        temperature = temperature,
        parsingModel = OpenAIModels.GPT4o
    )


    @Language("Markdown")
    fun actorDesigner() = ParsedActor(
        ActorParser::class.java,
        prompt = """
            You are an AI actor designer.
            
            Your task is to expand on a high-level design with requirements for each actor.
            
            For each actor in the given design, detail:
            
            1. The purpose of the actor
            2. Actor Type, which can be one of:
                1. "Simple" actors work like a chatbot, and simply return the chat model's response to the system and user prompts
                2. "Parsed" actors produce complex data structures as output, which can be used in the application logic
                    * **IMPORTANT**: If the output is a string, use a "simple" actor instead
                3. "Coding" actors are used to invoke tools via dynamically compiled scripts
                4. "Image" actors produce images from a user (and system) prompt.
            3. Required details for each actor type:
                1. Simple and Image actors
                    1. System prompt
                2. Parsed actors
                    1. system prompt
                    2. output data structure 
                        1. java class name
                        2. definition
                3. Coding actors
                    1. defined symbols and functions
                    2. libraries used
        """.trimIndent().trim(),
        model = model,
        temperature = temperature,
        parsingModel = OpenAIModels.GPT4oMini
    )

    @Language("Markdown")
    fun simpleActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a "simple" actor that takes part in a larger system.
        |"Simple" actors contain a system directive and can process a list of user messages into a response.
        |
        |For context, here is the constructor signature for SimpleActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.core.actors.SimpleActor
        |import org.intellij.lang.annotations.Language
        |import com.simiacryptus.jopenai.models.ChatModels
        |
        |class SimpleActor(
        |    prompt: String,
        |    name: String? = null,
        |    model: ChatModels = OpenAIModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt and a name:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.SimpleActor
        |import com.simiacryptus.skyenet.heart.KotlinInterpreter
        |import org.intellij.lang.annotations.Language
        |
        |@Language("Markdown")fun exampleSimpleActor() = SimpleActor(
        |    prompt = ""${'"'}
        |    |You are a writing assistant.
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the SimpleActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    ).apply { evalFormat = false }

    @Language("Markdown")
    fun imageActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a "image" actor that takes part in a larger system.
        |"Image" actors contain a system directive and can process a list of user messages into a response.
        |
        |For context, here is the constructor signature for ImageActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.models.ImageModels
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |
        |class ImageActor(
        |    prompt: String = "Transform the user request into an image generation prompt that the user will like",
        |    name: String? = null,
        |    textModel: ChatModels = OpenAIModels.GPT35Turbo,
        |    val imageModel: ImageModels = ImageModels.DallE2,
        |    temperature: Double = 0.3,
        |    val width: Int = 1024,
        |    val height: Int = 1024,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt and a name:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |
        |fun exampleSimpleActor() = ImageActor(
        |    prompt = ""${'"'}
        |    |You are a writing assistant.
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the ImageActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    ).apply { evalFormat = false }

    @Language("Markdown")
    fun parsedActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "parsed" actor that takes part in a larger system.
        |"Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
        |
        |For context, here is the constructor signature for ParsedActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import java.util.function.Function
        |
        |open class ParsedActor<T:Any>(
        |    val parserClass: Class<out Function<String, T>>,
        |    prompt: String,
        |    val action: String? = null,
        |    model: ChatModels = OpenAIModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and parsing class:
        |```kotlin
        |import com.simiacryptus.jopenai.describe.Description
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.proxy.ValidatedObject
        |import com.simiacryptus.skyenet.core.actors.ParsedActor
        |import java.util.function.Function
        |
        |interface ExampleParser : Function<String, ExampleResult> {
        |    @Description("Break down the text into a data structure.")
        |    override fun apply(text: String): ExampleResult
        |}
        |
        |data class ExampleResult(
        |    @Description("The name of the example")
        |    val name: String? = null,
        |) : ValidatedObject {
        |    override fun validate() = when {
        |        name.isNullOrBlank() -> "name is required"
        |        else -> null
        |    }
        |}
        |
        |fun exampleParsedActor() = ParsedActor<ExampleResult>(
        |    parserClass = ExampleParser::class.java,
        |    model = OpenAIModels.GPT4o,
        |    prompt = ""${'"'}
        |            |You are a question answering assistant.
        |            |""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the ParsedActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    ).apply { evalFormat = false }

    @Language("Markdown")
    fun codingActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "script" or "coding" actor that takes part in a larger system.
        |"Script" actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
        |
        |For context, here is the constructor signature for CodingActor class:
        |```kotlin
        |package com.simiacryptus.skyenet.core.actors
        |
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
        |import com.simiacryptus.jopenai.describe.TypeDescriber
        |import com.simiacryptus.skyenet.interpreter.Interpreter
        |import kotlin.reflect.KClass
        |
        |class CodingActor(
        |    val interpreterClass: KClass<out Interpreter>,
        |    val symbols: Map<String, Any> = mapOf(),
        |    val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
        |        "com.simiacryptus",
        |        "com.github.simiacryptus"
        |    ),
        |    name: String? = interpreterClass.simpleName,
        |    val details: String? = null,
        |    model: ChatModels = OpenAIModels.GPT35Turbo,
        |    val fallbackModel: ChatModels = OpenAIModels.GPT4o,
        |    temperature: Double = 0.1,
        |    private val fixIterations: Int = 3,
        |    private val fixRetries: Int = 2,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and a standard configuration:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.CodingActor
        |import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
        |
        |fun exampleCodingActor() = CodingActor(
        |    interpreterClass = KotlinInterpreter::class,
        |    details = ""${'"'}
        |    |You are a software implementation assistant.
        |    |
        |    |Defined functions:
        |    |* ...
        |    |
        |    |Expected code structure:
        |    |* ...
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the CodingActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    ).apply { evalFormat = false }

    @Language("Markdown")
    fun flowStepDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementor.
        |
        |Your task is to implement logic for an "agent" system that uses gpt "actors" to construct a model of a creative process.
        |This "creative process" can be thought of as a cognitive process, an individual's work process, or an organizational process.
        |The idea is that the overall structure is procedural and can be modeled in code, but individual steps are creative and can be modeled with gpt.
        |
        |Actors process inputs in the form of ChatGPT messages (often a single string) but vary in their output.
        |Usage examples of each actor type follows:
        |
        |Simple actors contain a system directive, and simply return the chat model's response to the user query.
        |Simple actors answer queries consisting of a list of strings representing a conversation thread, and respond with a string.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.SimpleActor
        |val answer : String = actor.answer(listOf("This is an example question"), api = api)
        |log.info("Answer: " + answer)
        |```
        |
        |Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt.
        |This natural-language response is then parsed into a typed object, which can be used in the application logic.
        |Parsed actors answer queries consisting of a list of strings representing a conversation thread, and responds with an object containing text and a parsed object.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.ParsedActor<T>
        |val answer : com.simiacryptus.skyenet.core.actors.ParsedResponse<T> = actor.answer(listOf("This is some example data"), api = api)
        |log.info("Natural Language Answer: " + answer.text)
        |log.info("Parsed Answer: " + com.simiacryptus.jopenai.util.JsonUtil.toJson(answer.obj))
        |```
        |
        |Coding actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
        |Coding actors answer queries expressed using CodeRequest, and responds with an object that defines a code block and an execution method.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.CodingActor
        |val answer : com.simiacryptus.skyenet.core.actors.CodingActor.CodeResult = actor.answer(listOf("Do an example task"), api = api)
        |log.info("Implemented Code: " + answer.code)
        |val executionResult : com.simiacryptus.skyenet.core.actors.CodingActor.ExecutionResult = answer.result
        |log.info("Execution Log: " + executionResult.resultOutput)
        |log.info("Execution Result: " + executionResult.resultValue)
        |```
        |
        |Image actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
        |Image actors answer queries consisting of a list of strings representing a conversation thread, and respond with an image.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.ImageActor
        |val answer : com.simiacryptus.skyenet.core.actors.ImageResponse = actor.answer(listOf("Draw an example image"), api = api)
        |log.info("Image description: " + answer.text)
        |val image : BufferedImage = answer.image
        |```
        |
        |While implementing logic, the progress should be displayed to the user using the `ui` object.
        |The UI display generally follows a pattern similar to:
        |```kotlin
        |val task = ui.newTask()
        |try {
        |  task.header("Main Function")
        |  task.add("Normal message")
        |  task.verbose("Verbose output - not shown by default")
        |  task.add(ui.textInput { log.info("Message Received: " + it) })
        |  task.add(ui.hrefLink("Click Me!") { log.info("Link clicked") })
        |  task.complete()
        |} catch (e: Throwable) {
        |  task.error(e)
        |  throw e
        |}
        |```
        |
        |**IMPORTANT**: Do not redefine any symbol defined in the preceding code messages.
        |
        |""".trimMargin().trim(),
        model = model,
        temperature = temperature,
        runtimeSymbols = mapOf(
            "log" to log
        ),
    ).apply { evalFormat = false }

    companion object {
        val log = LoggerFactory.getLogger(MetaAgentActors::class.java)
        fun <T> T.notIn(vararg examples: T) = !examples.contains(this)

    }
}
