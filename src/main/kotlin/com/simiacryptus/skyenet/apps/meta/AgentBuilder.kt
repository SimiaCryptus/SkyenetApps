package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.codingActorDesigner
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.initialDesigner
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.parsedActorDesigner
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.simpleActorDesigner
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.util.JsonUtil

open class AgentBuilder(
    val api: OpenAIClient,
    val verbose: Boolean = true,
    @Suppress("unused") val sessionDataStorage: SessionDataStorage,
    private val initialDesigner: ParsedActor<AgentDesign> = initialDesigner(),
) {

    private var userPrompt: String? = null


    fun buildAgent(
        userMessage: String,
        session: SessionBase,
        sessionDiv: SessionDiv,
        domainName: String
    ) {
        this.userPrompt = userMessage
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(userMessage)}</div>""", true)
        val design = initialDesigner.answer(*initialDesigner.chatMessages(userMessage), api = api)
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(design.getText())}</div>""", verbose)
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(design.getObj())}</pre>""", false)

        val actorImpls = design.getObj().actors?.map { actorDesign ->
            val actorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
            actorDiv.append("""<div>Actor: ${actorDesign.javaIdentifier}</div>""", true)
            val messages = simpleActorDesigner().chatMessages(
                userMessage,
                design.getText(),
                "Implement ${actorDesign.javaIdentifier!!}"
            )
            val type = actorDesign.type ?: ""
            val response = when {
                type == "simple" -> simpleActorDesigner().answer(*messages, api = api)
                type == "parsed" -> parsedActorDesigner().answer(*messages, api = api)
                type == "coding" -> codingActorDesigner().answer(*messages, api = api)
                else -> throw IllegalArgumentException("Unknown actor type: $type")
            }
            val code = response.getCode()
            actorDiv.append("""<pre>${MarkdownUtil.renderMarkdown(code)}</pre>""", false)
            actorDesign.javaIdentifier to code
        }?.toMap() ?: mapOf()

        var flowCodeBuffer = StringBuilder()
        design.getObj().logicFlow?.items?.forEach { logicFlowItem ->
            val logicFlowDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
            logicFlowDiv.append("""<div>Logic Flow: ${logicFlowItem.name}</div>""", true)
            val logicFlowDesigner = MetaActors.flowStepDesigner()
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
            logicFlowDiv.append("""<pre>${MarkdownUtil.renderMarkdown(code)}</pre>""", false)
        }

        val finalCodeDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
        finalCodeDiv.append("""<div>Final Code</div>""", true)
        var code = """
            |${actorImpls.values.joinToString("\n\n")}
            |
            |${flowCodeBuffer}
            |""".trimMargin()
        val (imports, otherCode) = code.split("\n").partition { it.trim().startsWith("import ") }
        code = imports.joinToString("\n") + "\n" + otherCode.joinToString("\n")

        finalCodeDiv.append("""<pre>${MarkdownUtil.renderMarkdown(code)}</pre>""", false)


//        val initialDesignDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
//        initialDesignDiv.append("<div>Final Outline</div>", true)


    }
}


