package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.ActorSystem
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.util.EmbeddingVisualizer
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.debate.DebateActors.*
import com.simiacryptus.skyenet.config.DataStorage
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.util.JsonUtil.toJson

class DebateBuilder(
    val api: OpenAIClient,
    val dataStorage: DataStorage,
    userId: String?,
    sessionId: String
) : ActorSystem<ActorType>(DebateActors.actorMap, dataStorage, userId, sessionId) {
    private val outlines = mutableMapOf<String, Outline>()
    @Suppress("UNCHECKED_CAST")
    private val moderator get() = getActor(ActorType.MODERATOR) as ParsedActor<DebateSetup>
    private val summarizor get() = getActor(ActorType.SUMMARIZOR) as SimpleActor

    fun debate(userMessage: String, session: SessionBase, sessionDiv: SessionDiv, domainName: String) {
        sessionDiv.append("""<div>${renderMarkdown(userMessage)}</div>""", true)
        val moderatorResponse = this.moderator.answer(*this.moderator.chatMessages(userMessage), api = api)
        sessionDiv.append("""<div>${renderMarkdown(moderatorResponse.getText())}</div>""", true)
        sessionDiv.append("""<pre class='verbose'>${toJson(moderatorResponse.getObj())}</pre>""", false)

        val totalSummary =
            (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().map { question ->
                val summarizorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
                val answers = (moderatorResponse.getObj().debators?.list ?: emptyList()).parallelStream()
                    .map { actor -> answer(session, actor, question) }.toList()
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
        val projectorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
        projectorDiv.append("""<div>Embedding Projector</div>""", true)
        val response = EmbeddingVisualizer(
            api = api,
            dataStorage = dataStorage,
            sessionID = sessionDiv.sessionID(),
            appPath = "debate_mapper",
            host = domainName,
            session = session
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        val conclusionDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
        conclusionDiv.append("""<pre class='verbose'>${toJson(totalSummary)}</pre>""", true)
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray(), api = api)
        conclusionDiv.append("""<pre class='verbose'>${toJson(summarizorResponse)}</pre>""", false)
        conclusionDiv.append("""<div>${renderMarkdown(summarizorResponse)}</div>""", false)
    }

    private fun answer(
        session: SessionBase,
        actor: Debator,
        question: Question
    ): String {
        val resonseDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
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
        resonseDiv.append("""<pre class='verbose'>${toJson(response.getObj()).trim()}</pre>""", false)
        return response.getText()
    }

    open fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(actor)

}