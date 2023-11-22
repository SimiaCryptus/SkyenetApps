package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.debate.DebateActors.*
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import com.simiacryptus.skyenet.webui.util.EmbeddingVisualizer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown

class DebateBuilder(
    val api: API,
    dataStorage: DataStorage,
    userId: User?,
    session: Session
) : ActorSystem<ActorType>(DebateActors.actorMap, dataStorage, userId, session) {
    private val outlines = mutableMapOf<String, Outline>()
    @Suppress("UNCHECKED_CAST")
    private val moderator get() = getActor(ActorType.MODERATOR) as ParsedActor<DebateSetup>
    private val summarizor get() = getActor(ActorType.SUMMARIZOR) as SimpleActor

    fun debate(userMessage: String, ui: ApplicationInterface, domainName: String) {
        val sessionMessage = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        sessionMessage.append("""<div>${renderMarkdown(userMessage)}</div>""", true)
        val moderatorResponse = this.moderator.answer(*this.moderator.chatMessages(userMessage), api = api)
        sessionMessage.append("""<div>${renderMarkdown(moderatorResponse.getText())}</div>""", true)
        sessionMessage.append("""<pre class="verbose">${toJson(moderatorResponse.getObj())}</pre>""", false)

        val totalSummary =
            (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().map { question ->
                val summarizorDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
                val answers = (moderatorResponse.getObj().debators?.list ?: emptyList()).parallelStream()
                    .map { actor -> answer(ui, actor, question) }.toList()
                summarizorDiv.append(
                    """<div>Summarizing: ${
                        renderMarkdown(question.text ?: "").trim()
                    }</div>""", true
                )
                val summarizorResponse =
                    summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray(), api = api)
                summarizorDiv.append("""<div>${renderMarkdown(summarizorResponse)}</div>""", false)
                summarizorResponse
            }.toList()

        val argumentList = outlines.values.flatMap {
            it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } +
                (moderatorResponse.getObj().questions?.list?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet())
        val projectorDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        projectorDiv.append("""<div>Embedding Projector</div>""", true)
        val response = EmbeddingVisualizer(
            api = api,
            dataStorage = dataStorage,
            sessionID = sessionMessage.sessionID(),
            appPath = "debate_mapper",
            host = domainName,
            session = ui,
            userId = userId,
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        val conclusionDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        conclusionDiv.append("""<pre class="verbose">${toJson(totalSummary)}</pre>""", true)
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray(), api = api)
        conclusionDiv.append("""<pre class="verbose">${toJson(summarizorResponse)}</pre>""", false)
        conclusionDiv.append("""<div>${renderMarkdown(summarizorResponse)}</div>""", false)
    }

    private fun answer(
        session: ApplicationInterface,
        actor: Debator,
        question: Question
    ): String {
        val resonseDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        resonseDiv.append(
            """<div>${actor.name?.trim() ?: ""} - ${
                renderMarkdown(question.text ?: "").trim()
            }</div>""",
            true
        )
        val debator = getActorConfig(actor)
        val response = debator.answer(*debator.chatMessages(question.text ?: ""), api = api)
        resonseDiv.append("""<div>${renderMarkdown(response.getText())}</div>""", false)
        outlines[actor.name!! + ": " + question.text!!] = response.getObj()
        resonseDiv.append("""<pre class="verbose">${toJson(response.getObj()).trim()}</pre>""", false)
        return response.getText()
    }

    private fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(actor)

}