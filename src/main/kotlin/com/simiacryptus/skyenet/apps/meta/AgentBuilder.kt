package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.initialDesigner
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.util.JsonUtil
import java.lang.ref.WeakReference
import java.util.*

open class AgentBuilder(
    val api: OpenAIClient,
    val verbose: Boolean = true,
    val sessionDataStorage: SessionDataStorage,
    private val initialDesigner: ParsedActor<AgentDesign> = initialDesigner(api),
) {

    private var userPrompt: String? = null


    fun buildAgent(
        userMessage: String,
        session: SessionBase,
        sessionDiv: SessionDiv,
        domainName: String
    ) {
        this.userPrompt = userMessage
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
        val design = initialDesigner.answer(*initialDesigner.chatMessages(userMessage))
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(design.getText())}</div>""", verbose)
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(design.getObj())}</pre>""", false)

        val actorImpls = design.getObj().actors?.map { actorDesign ->
            val actorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            actorDiv.append("""<div>Actor: ${actorDesign.javaIdentifier}</div>""", true)
            val simpleActorDesigner = MetaActors.simpleActorDesigner(api)
            val parsedActorDesigner = MetaActors.parsedActorDesigner(api)
            val codingActorDesigner = MetaActors.codingActorDesigner(api)
            val messages = simpleActorDesigner.chatMessages(
                userMessage,
                design.getText(),
                "Implement ${actorDesign.javaIdentifier!!}"
            )
            val response = when {
                actorDesign.type == "simple" -> simpleActorDesigner.answer(*messages)
                actorDesign.type == "parsed" -> parsedActorDesigner.answer(*messages)
                actorDesign.type == "coding" -> codingActorDesigner.answer(*messages)
                else -> throw IllegalArgumentException("Unknown actor type: ${actorDesign.type}")
            }
            val code = response.getCode()
            actorDiv.append("""<pre>${ChatSessionFlexmark.renderMarkdown(code)}</pre>""", false)
            actorDesign.javaIdentifier to code
        }?.toMap() ?: mapOf()

        var flowCodeBuffer = StringBuilder()
        design.getObj().logicFlow?.items?.forEach { logicFlowItem ->
            val logicFlowDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            logicFlowDiv.append("""<div>Logic Flow: ${logicFlowItem.name}</div>""", true)
            val logicFlowDesigner = MetaActors.flowStepDesigner(api)
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
            val response = logicFlowDesigner.answerWithPrefix(codePrefix = codePrefix, *messages)
            val code = response.getCode()
            flowCodeBuffer.append(code)
            logicFlowDiv.append("""<pre>${ChatSessionFlexmark.renderMarkdown(code)}</pre>""", false)
        }

        val finalCodeDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        finalCodeDiv.append("""<div>Final Code</div>""", true)
        var code = """
            |${actorImpls.values.joinToString("\n\n") ?: ""}
            |
            |${flowCodeBuffer}
            |""".trimMargin()
        val (imports, otherCode) = code.split("\n").partition { it.trim().startsWith("import ") }
        code = imports.joinToString("\n") + "\n" + otherCode.joinToString("\n")

        finalCodeDiv.append("""<pre>${ChatSessionFlexmark.renderMarkdown(code)}</pre>""", false)


//        val initialDesignDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
//        initialDesignDiv.append("<div>Final Outline</div>", true)


    }
}


