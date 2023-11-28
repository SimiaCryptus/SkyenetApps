package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.debate.DebateActors.*
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.util.TensorflowProjector

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

  fun debate(userMessage: String) {
    val moderatorTask = ui.newTask()
    moderatorTask.echo(renderMarkdown(userMessage))
    val moderatorResponse = this.moderator.answer(listOf(userMessage), api = api)
    moderatorTask.add(renderMarkdown(moderatorResponse.getText()))
    moderatorTask.verbose(toJson(moderatorResponse.getObj()))
    moderatorTask.complete()

    val projectorTask = ui.newTask()
    projectorTask.header("Embedding Projector")
    val totalSummary =
      (moderatorResponse.getObj().questions?.list ?: emptyList()).parallelStream().flatMap { question ->
        (moderatorResponse.getObj().debaters?.list ?: emptyList()).parallelStream()
          .map { actor -> answer(ui, actor, question) }
      }.toList()
    projectorTask.verbose(toJson(totalSummary))
    val outlineStatements = outlines.values.flatMap {
      it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val moderatorStatements = moderatorResponse.getObj().questions?.list?.map {
      it.text ?: ""
    }?.filter { it.isNotBlank() } ?: emptyList()
    val response = TensorflowProjector(
      api = api,
      dataStorage = dataStorage,
      sessionID = session,
      appPath = "debate_mapper",
      host = domainName,
      session = ui,
      userId = user,
    ).writeTensorflowEmbeddingProjectorHtml(*(outlineStatements + moderatorStatements).toTypedArray())
    projectorTask.complete(response)
  }

  private fun answer(
    session: ApplicationInterface,
    actor: Debater,
    question: Question
  ): String {
    val task = session.newTask()
    try {
      task.header((actor.name?.trim() ?: "") + " - " + renderMarkdown(question.text ?: "").trim())
      val response = getActorConfig(actor).answer(listOf(question.text ?: ""), api = api)
      outlines[actor.name!! + ": " + question.text!!] = response.getObj()
      task.add(renderMarkdown(response.getText()))
      task.verbose(toJson(response.getObj()))
      task.complete()
      return response.getText()
    } catch (e: Exception) {
      task.error(e)
      throw e
    }
  }

  private fun getActorConfig(actor: Debater) =
    DebateActors.getActorConfig(actor)

}