package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIAPI
import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.actors.ActorSystem
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.ParsedResponse
import com.simiacryptus.skyenet.apps.meta.MetaActors.ActorType
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.platform.DataStorage
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.ApplicationInterface
import com.simiacryptus.skyenet.session.SocketManagerBase
import com.simiacryptus.skyenet.session.SessionMessage
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.util.JsonUtil
import java.util.*


open class AgentBuilder(
    user: User?,
    session: Session,
    dataStorage: DataStorage,
    val api: OpenAIAPI,
    val model: ChatModels = ChatModels.GPT35Turbo,
    val ui: ApplicationInterface,
    val userMessage: String,
    val autoEvaluate: Boolean = true,
    val temperature: Double = 0.3,
) : ActorSystem<ActorType>(MetaActors(
    symbols = mapOf(
        "dataStorage" to dataStorage,
        "ui" to ui,
        "session" to session,
        "user" to user,
    ).filterValues { null != it }.mapValues { it.value!! },
    model = model,
    autoEvaluate = autoEvaluate,
    temperature = temperature,
).actorMap, dataStorage, user, session) {

    @Suppress("UNCHECKED_CAST")
    private val initialDesigner by lazy { getActor(ActorType.INITIAL) as ParsedActor<AgentDesign> }
    private val simpleActorDesigner by lazy { getActor(ActorType.SIMPLE) as CodingActor }
    private val parsedActorDesigner by lazy { getActor(ActorType.PARSED) as CodingActor }
    private val codingActorDesigner by lazy { getActor(ActorType.CODING) as CodingActor }
    private val flowStepDesigner by lazy { getActor(ActorType.FLOW_STEP) as CodingActor }

    private var userPrompt: String? = null

    fun buildAgent(
    ) {
        try {
            val rootMessage = ui.newMessage(SocketManagerBase.randomID(), ApplicationBase.spinner, false)
            this.userPrompt = userMessage
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

            val finalCodeDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationBase.spinner, false)
            //language=HTML
            finalCodeDiv.append("""<div class="response-header">Final Code</div>""", true)
            //language=MARKDOWN
            val code = """
            |```kotlin
            |${(actImpls.values + flowImpl.values + listOf(mainImpl)).joinToString("\n\n") { it.trimIndent() }.sortCode { body ->
                """
                |class ${design.getObj().name?.pascalCase() ?: "MyClass"}() { 
                |    ${body.trimIndent().lines().joinToString("\n") { "|    $it" }}
                |}
                """.trimMargin()
            } }
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
        val mainFunctionDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationBase.spinner, false)
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
        val actorDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationBase.spinner, false)
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
        val logicFlowDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationBase.spinner, false)
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

        fun String.camelCase(locale: Locale = Locale.getDefault()) : String {
                val words = this.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                return words.first().lowercase(locale) + words.drop(1).joinToString("") {
                    it.replaceFirstChar { c -> when {
                            c.isLowerCase() -> c.titlecase(locale)
                            else -> c.toString()
                        }
                    }
                }
            }

        fun String.pascalCase(locale: Locale = Locale.getDefault()) : String =
            split(" ").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") {
                it.replaceFirstChar { c ->
                    when {
                        c.isLowerCase() -> c.titlecase(locale)
                        else -> c.toString()
                    }
                }
            }

        fun String.sortCode(bodyWrapper : (String) -> String = { it }): String {
            val (imports, otherCode) = this.split("\n").partition { it.trim().startsWith("import ") }
            return imports.distinct().sorted().joinToString("\n") + "\n\n" + bodyWrapper(otherCode.joinToString("\n"))
        }

    }
}



