package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.meta.MetaActors.AgentDesign
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.initialDesigner
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.util.JsonUtil

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

        design.getObj().actors?.forEach { actorDesign ->
            val actorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            actorDiv.append("""<div>Actor: ${actorDesign.name}</div>""", true)
            val simpleActorDesigner = MetaActors.simpleActorDesigner(api)
            val parsedActorDesigner = MetaActors.parsedActorDesigner(api)
            val codingActorDesigner = MetaActors.codingActorDesigner(api)
            val messages = simpleActorDesigner.chatMessages(
                userMessage,
                design.getText(),
                "Implement ${actorDesign.name!!}"
            )
            val response = when {
                actorDesign.type == "simple" -> simpleActorDesigner.answer(*messages)
                actorDesign.type == "parsed" -> parsedActorDesigner.answer(*messages)
                actorDesign.type == "coding" -> codingActorDesigner.answer(*messages)
                else -> throw IllegalArgumentException("Unknown actor type: ${actorDesign.type}")
            }
            actorDiv.append(
                """<pre>${ChatSessionFlexmark.renderMarkdown(response.getCode())}</pre>""",
                false
            )
        }

//        val initialDesignDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
//        initialDesignDiv.append("<div>Final Outline</div>", true)


    }
}


