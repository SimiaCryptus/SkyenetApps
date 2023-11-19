package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.actors.ActorSystem
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.meta.MetaActors.ActorType
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.platform.DataStorage
import com.simiacryptus.skyenet.session.SessionBase
import com.simiacryptus.skyenet.session.SessionDiv
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.util.JsonUtil

open class AgentBuilder(
    val api: OpenAIClient,
    @Suppress("unused") val dataStorage: DataStorage,
    userId: String?,
    sessionId: String
) : ActorSystem<ActorType>(MetaActors.actorMap, dataStorage, userId, sessionId) {

    @Suppress("UNCHECKED_CAST")
    private val initialDesigner get() = getActor(ActorType.INITIAL) as ParsedActor<AgentDesign>
    private val simpleActorDesigner get() = getActor(ActorType.SIMPLE) as CodingActor
    private val parsedActorDesigner get() = getActor(ActorType.PARSED) as CodingActor
    private val codingActorDesigner get() = getActor(ActorType.CODING) as CodingActor
    private val flowStepDesigner get() = getActor(ActorType.FLOW_STEP) as CodingActor

    private var userPrompt: String? = null


    fun buildAgent(
        userMessage: String,
        session: SessionBase,
        sessionDiv: SessionDiv
    ) {
        try {
            this.userPrompt = userMessage
            //language=HTML
            sessionDiv.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""", true)
            val design = initialDesigner.answer(*initialDesigner.chatMessages(userMessage), api = api)
            //language=HTML
            sessionDiv.append("""<div class="response-message">${renderMarkdown(design.getText())}</div>""", true)
            //language=HTML
            sessionDiv.append("""<pre class="verbose">${JsonUtil.toJson(design.getObj())}</pre>""", false)

            val actorImpls = design.getObj().actors?.map { actorDesign ->
                val actorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
                //language=HTML
                actorDiv.append("""<div class="response-header">Actor: ${actorDesign.javaIdentifier}</div>""", true)
                val messages = simpleActorDesigner.chatMessages(
                    userMessage,
                    design.getText(),
                    "Implement ${actorDesign.javaIdentifier!!}"
                )
                val type = actorDesign.type ?: ""
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
                            """.trimMargin())
                    }</div>""", false
                )
                actorDesign.javaIdentifier to code
            }?.toMap() ?: mapOf()

            var flowCodeBuffer = StringBuilder()
            design.getObj().logicFlow?.items?.forEach { logicFlowItem ->
                val logicFlowDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
                //language=HTML
                logicFlowDiv.append("""<div class="response-header">Logic Flow: ${logicFlowItem.name}</div>""", true)
                val logicFlowDesigner = flowStepDesigner
                val messages = logicFlowDesigner.chatMessages(
                    userMessage,
                    design.getText(),
                    "Implement ${logicFlowItem.name!!}"
                )
                val codePrefix = """
                |${logicFlowItem.actorsUsed?.mapNotNull { actorImpls[it] }?.joinToString("\n\n") ?: ""}
                |
                |${flowCodeBuffer}
                |""".trimMargin()
                val response = logicFlowDesigner.answerWithPrefix(codePrefix = codePrefix, *messages, api = api)
                val code = response.getCode()
                flowCodeBuffer.append(code)
                //language=HTML
                logicFlowDiv.append(
                    """<div class="response-message">${
                        //language=MARKDOWN
                        renderMarkdown(
                            """
                            |```kotlin
                            |$code
                            |```
                            """.trimMargin())
                    }</div>""", false
                )
            }

            val finalCodeDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
            //language=HTML
            finalCodeDiv.append("""<div class="response-header">Final Code</div>""", true)
            //language=MARKDOWN
            val (imports, otherCode) = (actorImpls.values + flowCodeBuffer.split("\n")).partition { it.trim().startsWith("import ") }
            var code = """
            |```kotlin
            |${imports}
            |
            |${otherCode}
            |```
            |""".trimMargin()
            //language=HTML
            finalCodeDiv.append("""<div class="response-message">${renderMarkdown(code)}</div>""", false)
        } catch (e: Throwable) {
            log.warn("Error", e)
            session.send("""${SessionBase.randomID()},<div class="error">${renderMarkdown(e.message ?: "")}</div>""")
        }

    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(AgentBuilder::class.java)
    }
}


