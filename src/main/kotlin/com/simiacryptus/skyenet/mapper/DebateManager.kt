package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.skyenet.mapper.DebateActors.*
import com.simiacryptus.util.JsonUtil

open class DebateManager(
    val api: OpenAIClient,
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
    val moderator: ParsedActorConfig<DebateSetup> = Companion.moderator(api),
    val summarizor: ActorConfig = Companion.summarizor(api),
) {

    fun debate(userMessage: String, session: PersistentSessionBase, sessionDiv: SessionDiv, domainName: String) {
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
        val moderatorResponse = this.moderator.parse(userMessage)
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(moderatorResponse.text)}</div>""", verbose)
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(moderatorResponse.obj)}</pre>""", false)

        val totalSummary = (moderatorResponse.obj.questions?.list ?: emptyList()).parallelStream().map { question ->
            val summarizorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            val answers = (moderatorResponse.obj.debators?.list ?: emptyList()).parallelStream()
                .map { actor -> answer(session, actor, question) }.toList()
            summarizorDiv.append(
                """<div>Summarizing: ${
                    ChatSessionFlexmark.renderMarkdown(question.text ?: "").trim()
                }</div>""", true
            )
            val summarizorResponse = summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray())
            summarizorDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(summarizorResponse)}</div>""", false)
            summarizorResponse
        }.toList()

        val argumentList = outlines.values.flatMap {
            it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } +
                (moderatorResponse.obj.questions?.list?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet())
        val projectorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        projectorDiv.append("""<div>Embedding Projector</div>""", true)
        val response = EmbeddingVisualizer(
            api = api,
            sessionDataStorage = sessionDataStorage,
            sessionID = sessionDiv.sessionID(),
            appPath = "debate_mapper_ro",
            host = domainName
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        val conclusionDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(totalSummary)}</pre>""", true)
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray())
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(summarizorResponse)}</pre>""", false)
        conclusionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(summarizorResponse)}</div>""", false)
    }

    val outlines = mutableMapOf<String, Outline>()

    private fun answer(
        session: PersistentSessionBase,
        actor: Debator,
        question: Question
    ): String {
        val resonseDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        resonseDiv.append(
            """<div>${actor.name?.trim() ?: ""} - ${
                ChatSessionFlexmark.renderMarkdown(question.text ?: "").trim()
            }</div>""",
            true
        )
        val debator = getActorConfig(actor)
        val debatorResponse = debator.parse(question.text ?: "")
        resonseDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(debatorResponse.text)}</div>""", false)
        outlines[actor.name!! + ": " + question.text!!] = debatorResponse.obj
        if (verbose) {
            resonseDiv.append(
                """<pre>${
                    JsonUtil.toJson(debatorResponse.obj).trim()
                }</pre>""", false
            )
        }
        return debatorResponse.text
    }

    open fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(api, actor)

}