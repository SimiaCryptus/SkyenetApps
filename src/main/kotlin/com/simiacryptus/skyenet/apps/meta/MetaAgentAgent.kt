package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.meta.MetaAgentActors.ActorType
import com.simiacryptus.skyenet.apps.meta.MetaAgentActors.AgentDesign
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.camelCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.imports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.pascalCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.sortCode
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.stripImports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.upperSnakeCase
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.ParsedResponse
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language


open class MetaAgentAgent(
    user: User?,
    session: Session,
    dataStorage: DataStorage,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModels = ChatModels.GPT35Turbo,
    autoEvaluate: Boolean = true,
    temperature: Double = 0.3,
) : ActorSystem<ActorType>(MetaAgentActors(
    symbols = mapOf(
        "user" to user,
        "session" to session,
        "dataStorage" to dataStorage,
        "ui" to ui,
        "api" to api,
    ).filterValues { null != it }.mapValues { it.value!! },
    model = model,
    autoEvaluate = autoEvaluate,
    temperature = temperature,
).actorMap, dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val initialDesigner by lazy { getActor(ActorType.INITIAL) as ParsedActor<AgentDesign> }
    private val simpleActorDesigner by lazy { getActor(ActorType.SIMPLE) as CodingActor }
    private val imageActorDesigner by lazy { getActor(ActorType.IMAGE) as CodingActor }
    private val parsedActorDesigner by lazy { getActor(ActorType.PARSED) as CodingActor }
    private val codingActorDesigner by lazy { getActor(ActorType.CODING) as CodingActor }
    private val flowStepDesigner by lazy { getActor(ActorType.FLOW_STEP) as CodingActor }

    fun buildAgent(userMessage: String) {
        val rootMessage = ui.newTask()
        val design = try {
            rootMessage.echo(renderMarkdown(userMessage))
            val design = initialDesigner.answer(*initialDesigner.chatMessages(userMessage), api = api)
            rootMessage.complete(renderMarkdown(design.getText()))
            rootMessage.verbose(JsonUtil.toJson(design.getObj()))
            rootMessage.complete()
            design
        } catch (e: Throwable) {
            rootMessage.error(e)
            throw e
        }

        val actImpls = implementActors(userMessage, design)
        val flowImpl = getFlowStepCode(userMessage, design, actImpls)
        val mainImpl = getMainFunction(userMessage, design, actImpls, flowImpl)

        val finalCodeMessage = ui.newTask()
        try {
            finalCodeMessage.header("Final Code")

            val imports =
                (actImpls.values + flowImpl.values + listOf(mainImpl)).flatMap { it.imports() }
                    .toSortedSet().joinToString("\n")

            val classBaseName = design.getObj().name?.pascalCase() ?: "MyAgent"

            val actorInits = design.getObj().actors?.joinToString("\n") { actor ->
                """private val ${actor.name?.camelCase()} by lazy { getActor(${classBaseName}Actors.ActorType.${actor.name?.upperSnakeCase()}) as ${
                    when (actor.type) {
                        "simple" -> "SimpleActor"
                        "parsed" -> "ParsedActor"
                        "coding" -> "CodingActor"
                        "image" -> "ImageActor"
                        else -> throw IllegalArgumentException("Unknown actor type: ${actor.type}")
                    }
                } }"""
            } ?: ""

            val actorMapEntries = design.getObj().actors?.joinToString("\n") { actor ->
                """ActorType.${actor.name?.upperSnakeCase()} to ${actor.name?.camelCase()}(),"""
            } ?: ""

            val actorEnumDefs = design.getObj().actors?.joinToString("\n") { actor ->
                """${actor.name?.upperSnakeCase()},"""
            } ?: ""

            @Language("kotlin") val appCode = """
            |import com.simiacryptus.jopenai.OpenAIAPI
            |import com.simiacryptus.jopenai.models.ChatModels
            |import com.simiacryptus.skyenet.application.ApplicationBase
            |import com.simiacryptus.skyenet.platform.Session
            |import com.simiacryptus.skyenet.platform.User
            |import com.simiacryptus.skyenet.session.*
            |import org.slf4j.LoggerFactory
            |
            |open class ${classBaseName}App(
            |    applicationName: String = "${design.getObj().name}",
            |    temperature: Double = 0.1,
            |) : ApplicationBase(
            |    applicationName = applicationName,
            |    temperature = temperature,
            |) {
            |
            |    data class Settings(
            |        val model: ChatModels = ChatModels.GPT35Turbo,
            |        val temperature: Double = 0.1,
            |    )
            |    override val settingsClass: Class<*> get() = Settings::class.java
            |    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T
            |
            |    override fun newSession(
            |        session: Session,
            |        user: User?,
            |        userMessage: String,
            |        ui: ApplicationInterface,
            |        api: OpenAIAPI
            |    ) {
            |        try {
            |            val settings = getSettings<Settings>(session, user)
            |            ${classBaseName}Agent(
            |                user = user,
            |                session = session,
            |                dataStorage = dataStorage,
            |                api = api,
            |                ui = ui,
            |                model = settings?.model ?: ChatModels.GPT35Turbo,
            |                temperature = settings?.temperature ?: 0.3,
            |            ).${design.getObj().name?.camelCase()}(userMessage)
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

            @Language("kotlin") val agentCode = """
            |import com.simiacryptus.jopenai.OpenAIAPI
            |import com.simiacryptus.jopenai.models.ChatModels
            |import com.simiacryptus.skyenet.core.actors.ActorSystem
            |import com.simiacryptus.skyenet.core.actors.CodingActor
            |import com.simiacryptus.skyenet.core.actors.ParsedActor
            |import com.simiacryptus.skyenet.platform.DataStorage
            |import com.simiacryptus.skyenet.platform.Session
            |import com.simiacryptus.skyenet.platform.User
            |import com.simiacryptus.skyenet.session.ApplicationInterface
            |
            |open class ${classBaseName}Agent(
            |    user: User?,
            |    session: Session,
            |    dataStorage: DataStorage,
            |    val ui: ApplicationInterface,
            |    val api: OpenAIAPI,
            |    model: ChatModels = ChatModels.GPT35Turbo,
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
            |    ${flowImpl.values.joinToString("\n\n") { it.trimIndent() }.stripImports().indent("    ")}
            |
            |    companion object {
            |        private val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Agent::class.java)
            |
            |    }
            |}
            """.trimMargin()

            @Language("kotlin") val agentsCode = """
            |import com.simiacryptus.jopenai.models.ChatModels
            |import com.simiacryptus.skyenet.core.actors.BaseActor
            |
            |class ${classBaseName}Actors(
            |    val model: ChatModels = ChatModels.GPT4Turbo,
            |    val temperature: Double = 0.3,
            |) {
            |
            |    enum class ActorType {
            |        ${actorEnumDefs.indent("        ")}
            |    }
            |
            |    val actorMap: Map<ActorType, BaseActor<out Any>> = mapOf(
            |        ${actorMapEntries.indent("        ")}
            |    )
            |
            |    ${actImpls.values.joinToString("\n\n") { it.trimIndent() }.stripImports().indent("    ")}
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
            finalCodeMessage.complete(renderMarkdown(code))
        } catch (e: Throwable) {
            finalCodeMessage.error(e)
            throw e
        }
    }
    fun demo() {
        val task = ui.newTask()
        try {
            task.header("Main Function")
            task.add("Normal message")
            task.add(ui.textInput { log.info("Message Recieved: " + it) })
            task.add(ui.hrefLink("Click Me!") { log.info("Link clicked") })
            task.verbose("Verbose output - not shown by default")
            task.complete()
            return
        } catch (e: Throwable) {
            task.error(e)
            throw e
        }
    }

    private fun getMainFunction(
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
        actorImpls: Map<String, String>,
        flowStepCode: Map<String, String>
    ): String {
        val message = ui.newTask()
        try {
            message.header("Main Function")
            val mainFunction = flowStepDesigner.answerWithPrefix(
                codePrefix = (actorImpls.values + flowStepCode.values).joinToString(
                    "\n\n"
                ) { it.trimIndent() }.sortCode(), *flowStepDesigner.chatMessages(
                    userMessage,
                    design.getText(),
                    "Implement `fun ${design.getObj().name?.camelCase()}(${
                        listOf(design.getObj().mainInput!!).joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") }
                    })`"
                ), api = api
            ).getCode()
            message.complete(
                renderMarkdown(
                    """
                        |```kotlin
                        |$mainFunction
                        |```
                        """.trimMargin()
                )
            )
            return mainFunction
        } catch (e: Throwable) {
            message.error(e)
            throw e
        }
    }

    private fun implementActors(
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
    ) = design.getObj().actors?.map { actorDesign ->
        val message = ui.newTask()
        try {
            //language=HTML
            message.header("Actor: ${actorDesign.name}")
            val type = actorDesign.type ?: ""
            val messages = simpleActorDesigner.chatMessages(
                userMessage,
                design.getText(),
                "Implement `fun ${(actorDesign.name!!).camelCase()}() : ${
                    when (type) {
                        "simple" -> "SimpleActor"
                        "parsed" -> "ParsedActor"
                        "coding" -> "CodingActor"
                        "image" -> "ImageActor"
                        else -> throw IllegalArgumentException("Unknown actor type: $type")
                    }
                }`"
            )
            val response = when {
                type == "simple" -> simpleActorDesigner.answer(*messages, api = api)
                type == "parsed" -> parsedActorDesigner.answer(*messages, api = api)
                type == "coding" -> codingActorDesigner.answer(*messages, api = api)
                type == "image" -> imageActorDesigner.answer(*messages, api = api)
                else -> throw IllegalArgumentException("Unknown actor type: $type")
            }
            val code = response.getCode()
            //language=HTML
            message.complete(
                renderMarkdown(
                    """
                    |```kotlin
                    |$code
                    |```
                    """.trimMargin()
                )
            )
            actorDesign.name to code
        } catch (e: Throwable) {
            message.error(e)
            throw e
        }
    }?.toMap() ?: mapOf()

    private fun getFlowStepCode(
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
        actorImpls: Map<String, String>,
    ) = design.getObj().logicFlow?.items?.map { logicFlowItem ->
        val message = ui.newTask()
        try {
            message.header("Logic Flow: ${logicFlowItem.name}")
            val messages = flowStepDesigner.chatMessages(
                userMessage,
                design.getText(),
                "Implement `fun ${(logicFlowItem.name!!).camelCase()}(${
                    logicFlowItem.inputs?.joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") } ?: ""
                })`"
            )
            val codePrefix = logicFlowItem.actors?.mapNotNull { actorImpls[it] }?.joinToString("\n\n") ?: ""
            val response = flowStepDesigner.answerWithPrefix(codePrefix = codePrefix, *messages, api = api)
            val code = response.getCode()
            //language=HTML
            message.complete(
                renderMarkdown(
                    """
                    |```kotlin
                    |$code
                    |```
                    """.trimMargin()
                )
            )
            logicFlowItem.name to code
        } catch (e: Throwable) {
            message.error(e)
            throw e
        }
    }?.toMap() ?: mapOf()

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(MetaAgentAgent::class.java)

    }
}



