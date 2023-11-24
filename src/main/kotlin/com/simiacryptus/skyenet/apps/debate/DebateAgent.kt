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
import com.simiacryptus.skyenet.webui.util.TensorflowProjector
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown

class DebateAgent(
    val api: API,
    dataStorage: DataStorage,
    userId: User?,
    session: Session,
    val ui: ApplicationInterface,
    val domainName: String
) : ActorSystem<ActorType>(DebateActors.actorMap, dataStorage, userId, session) {
    private val outlines = mutableMapOf<String, Outline>()
    @Suppress("UNCHECKED_CAST")
    private val moderator get() = getActor(ActorType.MODERATOR) as ParsedActor<DebateSetup>
    private val summarizor get() = getActor(ActorType.SUMMARIZOR) as SimpleActor

    fun debate(userMessage: String) {
        val message = ui.newMessage()
        message.echo(renderMarkdown(userMessage))
        val moderatorResponse = this.moderator.answer(*this.moderator.chatMessages(userMessage), api = api)
        message.complete(renderMarkdown(moderatorResponse.getText()))
        message.verbose(toJson(moderatorResponse.getObj()))

        val totalSummary =
            (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().map { question ->
                val message = ui.newMessage()
                val answers = (moderatorResponse.getObj().debators?.list ?: emptyList()).parallelStream()
                    .map { actor -> answer(ui, actor, question) }.toList()
                message.header("Summarizing: ${renderMarkdown(question.text ?: "")}")
                val summarizorResponse =
                    summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray(), api = api)
                message.complete(renderMarkdown(summarizorResponse))
                summarizorResponse
            }.toList()

        val argumentList = outlines.values.flatMap {
            it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } +
                (moderatorResponse.getObj().questions?.list?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet())
        val projectorMessage = ui.newMessage()
        projectorMessage.header("Embedding Projector")
        val response = TensorflowProjector(
            api = api,
            dataStorage = dataStorage,
            sessionID = session,
            appPath = "debate_mapper",
            host = domainName,
            session = ui,
            userId = user,
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorMessage.complete(response)

        val conclusionMessage = ui.newMessage()
        conclusionMessage.verbose(toJson(totalSummary))
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray(), api = api)
        conclusionMessage.verbose(toJson(summarizorResponse))
        conclusionMessage.complete(renderMarkdown(summarizorResponse))
    }

    private fun answer(
        session: ApplicationInterface,
        actor: Debator,
        question: Question
    ): String {
        val message = session.newMessage()
        message.add((actor.name?.trim() ?: "") + " - " + renderMarkdown(question.text ?: "").trim())
        val debator = getActorConfig(actor)
        val response = debator.answer(*debator.chatMessages(question.text ?: ""), api = api)
        message.complete(renderMarkdown(response.getText()))
        outlines[actor.name!! + ": " + question.text!!] = response.getObj()
        message.verbose(toJson(response.getObj()))
        return response.getText()
    }

    private fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(actor)

}