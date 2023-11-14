package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.util.EmbeddingVisualizer
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.apps.debate.DebateActors.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.util.JsonUtil

open class DebateManager(
    val api: OpenAIClient,
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
    private val moderator: ParsedActor<DebateSetup> = Companion.moderator(),
    private val summarizor: SimpleActor = Companion.summarizor(),
) {

    private val outlines = mutableMapOf<String, Outline>()

    fun debate(userMessage: String, session: SessionBase, sessionDiv: SessionDiv, domainName: String) {
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(userMessage)}</div>""", true)
        val moderatorResponse = this.moderator.answer(*this.moderator.chatMessages(userMessage), api = api)
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(moderatorResponse.getText())}</div>""", verbose)
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(moderatorResponse.getObj())}</pre>""", false)

        val totalSummary =
            (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().map { question ->
                val summarizorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
                val answers = (moderatorResponse.getObj().debators?.list ?: emptyList()).parallelStream()
                    .map { actor -> answer(session, actor, question) }.toList()
                summarizorDiv.append(
                    """<div>Summarizing: ${
                        MarkdownUtil.renderMarkdown(question.text ?: "").trim()
                    }</div>""", true
                )
                val summarizorResponse =
                    summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray(), api = api)
                summarizorDiv.append("""<div>${MarkdownUtil.renderMarkdown(summarizorResponse)}</div>""", false)
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
            sessionDataStorage = sessionDataStorage,
            sessionID = sessionDiv.sessionID(),
            appPath = "debate_mapper_ro",
            host = domainName
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        val conclusionDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(totalSummary)}</pre>""", true)
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray(), api = api)
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(summarizorResponse)}</pre>""", false)
        conclusionDiv.append("""<div>${MarkdownUtil.renderMarkdown(summarizorResponse)}</div>""", false)
    }

    private fun answer(
        session: SessionBase,
        actor: Debator,
        question: Question
    ): String {
        val resonseDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner, false)
        resonseDiv.append(
            """<div>${actor.name?.trim() ?: ""} - ${
                MarkdownUtil.renderMarkdown(question.text ?: "").trim()
            }</div>""",
            true
        )
        val debator = getActorConfig(actor)
        val debatorResponse = debator.answer(*debator.chatMessages(question.text ?: ""), api = api)
        resonseDiv.append("""<div>${MarkdownUtil.renderMarkdown(debatorResponse.getText())}</div>""", false)
        outlines[actor.name!! + ": " + question.text!!] = debatorResponse.getObj()
        if (verbose) {
            resonseDiv.append(
                """<pre>${
                    JsonUtil.toJson(debatorResponse.getObj()).trim()
                }</pre>""", false
            )
        }
        return debatorResponse.getText()
    }

    open fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(actor)

}