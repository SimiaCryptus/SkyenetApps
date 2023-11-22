package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.application.ApplicationServer
import com.simiacryptus.skyenet.Brain.Companion.indent
import com.simiacryptus.skyenet.actors.ActorSystem
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.ParsedResponse
import com.simiacryptus.skyenet.application.ApplicationInterface
import com.simiacryptus.skyenet.apps.meta.MetaActors.ActorType
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.platform.DataStorage
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.SocketManagerBase
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.jopenai.util.JsonUtil
import org.intellij.lang.annotations.Language
import java.util.*


open class AgentBuilder(
    user: User?,
    session: Session,
    dataStorage: DataStorage,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModels = ChatModels.GPT35Turbo,
    autoEvaluate: Boolean = true,
    temperature: Double = 0.3,
) : ActorSystem<ActorType>(MetaActors(
    symbols = mapOf(
        "user" to user,
        "session" to session,
        "dataStorage" to dataStorage,
        "ui" to ui,
    ).filterValues { null != it }.mapValues { it.value!! },
    model = model,
    autoEvaluate = autoEvaluate,
    temperature = temperature,
).actorMap, dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val initialDesigner by lazy { getActor(ActorType.INITIAL) as ParsedActor<AgentDesign> }
    private val simpleActorDesigner by lazy { getActor(ActorType.SIMPLE) as CodingActor }
    private val parsedActorDesigner by lazy { getActor(ActorType.PARSED) as CodingActor }
    private val codingActorDesigner by lazy { getActor(ActorType.CODING) as CodingActor }
    private val flowStepDesigner by lazy { getActor(ActorType.FLOW_STEP) as CodingActor }

    fun buildAgent(userMessage: String) {
        try {
            val rootMessage = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
            //language=HTML
            rootMessage.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""", true)
            val design = initialDesigner.answer(*initialDesigner.chatMessages(userMessage), api = api)
            //language=HTML
            rootMessage.append("""<div class="response-message">${renderMarkdown(design.getText())}</div>""", true)
            //language=HTML
            rootMessage.append("""<pre class="verbose">${JsonUtil.toJson(design.getObj())}</pre>""", false)

            val actImpls = implementActors(ui, userMessage, design)
            val flowImpl = getFlowStepCode(ui, userMessage, design, actImpls)
            val mainImpl = getMainFunction(ui, userMessage, design, actImpls, flowImpl)

            val finalCodeDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
            //language=HTML
            finalCodeDiv.append("""<div class="response-header">Final Code</div>""", true)

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
            |            ${classBaseName}Builder(
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

            @Language("kotlin") val builderCode = """
            |import com.simiacryptus.jopenai.OpenAIAPI
            |import com.simiacryptus.jopenai.models.ChatModels
            |import com.simiacryptus.skyenet.actors.ActorSystem
            |import com.simiacryptus.skyenet.actors.CodingActor
            |import com.simiacryptus.skyenet.actors.ParsedActor
            |import com.simiacryptus.skyenet.platform.DataStorage
            |import com.simiacryptus.skyenet.platform.Session
            |import com.simiacryptus.skyenet.platform.User
            |import com.simiacryptus.skyenet.session.ApplicationInterface
            |
            |open class ${classBaseName}Builder(
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
            |        private val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Builder::class.java)
            |
            |    }
            |}
            """.trimMargin()

            @Language("kotlin") val agentsCode = """
            |import com.simiacryptus.jopenai.models.ChatModels
            |import com.simiacryptus.skyenet.actors.BaseActor
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
            |${listOf(imports, appCode, builderCode, agentsCode).joinToString("\n\n") { it.trimIndent() }.sortCode()}
            |```
            |""".trimMargin()

            //language=HTML
            finalCodeDiv.append("""<div class="response-message">${renderMarkdown(code)}</div>""", false)
        } catch (e: Throwable) {
            log.warn("Error", e)
            ui.send("""${SocketManagerBase.randomID()},<div class="error">${renderMarkdown(e.message ?: "")}</div>""")
        }
    }

    private fun getMainFunction(
        session: ApplicationInterface,
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
        actorImpls: Map<String, String>,
        flowStepCode: Map<String, String>
    ): String {
        val mainFunctionDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        mainFunctionDiv.append("""<div class="response-header">Main Function</div>""", true)
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
        mainFunctionDiv.append(
            """<div class="response-message">${
                //language=MARKDOWN
                renderMarkdown(
                    """
                    |```kotlin
                    |$mainFunction
                    |```
                    """.trimMargin()
                )
            }</div>""", false
        )
        return mainFunction
    }

    private fun implementActors(
        session: ApplicationInterface,
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
    ) = design.getObj().actors?.map { actorDesign ->
        val actorDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        //language=HTML
        actorDiv.append("""<div class="response-header">Actor: ${actorDesign.name}</div>""", true)
        val type = actorDesign.type ?: ""
        val messages = simpleActorDesigner.chatMessages(
            userMessage,
            design.getText(),
            "Implement `fun ${(actorDesign.name!!).camelCase()}() : ${
                when (type) {
                    "simple" -> "SimpleActor"
                    "parsed" -> "ParsedActor"
                    "coding" -> "CodingActor"
                    else -> throw IllegalArgumentException("Unknown actor type: $type")
                }
            }`"
        )
        val response = when {
            type == "simple" -> simpleActorDesigner.answer(*messages, api = api)
            type == "parsed" -> parsedActorDesigner.answer(*messages, api = api)
            type == "coding" -> codingActorDesigner.answer(*messages, api = api)
            else -> throw IllegalArgumentException("Unknown actor type: $type")
        }
        val code = response.getCode()
        //language=HTML
        actorDiv.append(
            """<div class="response-message">${
                //language=MARKDOWN
                renderMarkdown(
                    """
                                |```kotlin
                                |$code
                                |```
                                """.trimMargin()
                )
            }</div>""", false
        )
        actorDesign.name to code
    }?.toMap() ?: mapOf()

    private fun getFlowStepCode(
        session: ApplicationInterface,
        userMessage: String,
        design: ParsedResponse<AgentDesign>,
        actorImpls: Map<String, String>,
    ) = design.getObj().logicFlow?.items?.map { logicFlowItem ->
        val logicFlowDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        //language=HTML
        logicFlowDiv.append(
            """<div class="response-header">Logic Flow: ${logicFlowItem.name}</div>""",
            true
        )
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
        logicFlowDiv.append(
            """<div class="response-message">${
                //language=MARKDOWN
                renderMarkdown(
                    """
                                |```kotlin
                                |$code
                                |```
                                """.trimMargin()
                )
            }</div>""", false
        )
        logicFlowItem.name to code
    }?.toMap() ?: mapOf()

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(AgentBuilder::class.java)

        fun String.camelCase(locale: Locale = Locale.getDefault()): String {
            val words = fromPascalCase(locale).split(" ").map { it.trim() }.filter { it.isNotEmpty() }
            return words.first().lowercase(locale) + words.drop(1).joinToString("") {
                it.replaceFirstChar { c ->
                    when {
                        c.isLowerCase() -> c.titlecase(locale)
                        else -> c.toString()
                    }
                }
            }
        }

        fun String.pascalCase(locale: Locale = Locale.getDefault()): String =
            fromPascalCase(locale).split(" ").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") {
                it.replaceFirstChar { c ->
                    when {
                        c.isLowerCase() -> c.titlecase(locale)
                        else -> c.toString()
                    }
                }
            }

        // Detect changes in the case of the first letter and prepend a space
        fun String.fromPascalCase(locale: Locale = Locale.getDefault()): String = buildString {
            var lastChar = ' '
            for (c in this@fromPascalCase) {
                if (c.isUpperCase() && lastChar.isLowerCase()) append(' ')
                append(c)
                lastChar = c
            }
        }
        fun String.upperSnakeCase(locale: Locale = Locale.getDefault()): String =
            fromPascalCase(locale).split(" ").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("_") {
                it.replaceFirstChar { c ->
                    when {
                        c.isLowerCase() -> c.titlecase(locale)
                        else -> c.toString()
                    }
                }
            }.uppercase(locale)

        fun String.sortCode(bodyWrapper: (String) -> String = { it }): String {
            val (imports, otherCode) = this.split("\n").partition { it.trim().startsWith("import ") }
            return imports.distinct().sorted().joinToString("\n") + "\n\n" + bodyWrapper(otherCode.joinToString("\n"))
        }

        fun String.imports(): List<String> {
            return this.split("\n").filter { it.trim().startsWith("import ") }.distinct().sorted()
        }

        fun String.stripImports(): String {
            return this.split("\n").filter { !it.trim().startsWith("import ") }.joinToString("\n")
        }

    }
}



