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
        val message = ui.newMessage()
        message.append("""<div>${renderMarkdown(userMessage)}</div>""")
        val moderatorResponse = this.moderator.answer(*this.moderator.chatMessages(userMessage), api = api)
        message.append("""<div>${renderMarkdown(moderatorResponse.getText())}</div>""")
        message.complete("""<pre class="verbose">${toJson(moderatorResponse.getObj())}</pre>""")

        val totalSummary =
            (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().map { question ->
                val message = ui.newMessage()
                val answers = (moderatorResponse.getObj().debators?.list ?: emptyList()).parallelStream()
                    .map { actor -> answer(ui, actor, question) }.toList()
                message.append(
                    """<div>Summarizing: ${
                        renderMarkdown(question.text ?: "").trim()
                    }</div>"""
                )
                val summarizorResponse =
                    summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray(), api = api)
                message.complete("""<div>${renderMarkdown(summarizorResponse)}</div>""")
                summarizorResponse
            }.toList()

        val argumentList = outlines.values.flatMap {
            it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } +
                (moderatorResponse.getObj().questions?.list?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet())
        val projectorMessage = ui.newMessage()
        projectorMessage.append("""<div>Embedding Projector</div>""")
        val response = TensorflowProjector(
            api = api,
            dataStorage = dataStorage,
            sessionID = message.sessionID(),
            appPath = "debate_mapper",
            host = domainName,
            session = ui,
            userId = userId,
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorMessage.complete("""<div>$response</div>""")

        val conclusionMessage = ui.newMessage()
        conclusionMessage.append("""<pre class="verbose">${toJson(totalSummary)}</pre>""")
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray(), api = api)
        conclusionMessage.complete("""<pre class="verbose">${toJson(summarizorResponse)}</pre>""")
        conclusionMessage.complete("""<div>${renderMarkdown(summarizorResponse)}</div>""")
    }

    private fun answer(
        session: ApplicationInterface,
        actor: Debator,
        question: Question
    ): String {
        val message = session.newMessage()
        message.append(
            """<div>${actor.name?.trim() ?: ""} - ${
                renderMarkdown(question.text ?: "").trim()
            }</div>"""
        )
        val debator = getActorConfig(actor)
        val response = debator.answer(*debator.chatMessages(question.text ?: ""), api = api)
        message.complete("""<div>${renderMarkdown(response.getText())}</div>""")
        outlines[actor.name!! + ": " + question.text!!] = response.getObj()
        message.complete("""<pre class="verbose">${toJson(response.getObj()).trim()}</pre>""")
        return response.getText()
    }

    private fun getActorConfig(actor: Debator) =
        DebateActors.getActorConfig(actor)

}