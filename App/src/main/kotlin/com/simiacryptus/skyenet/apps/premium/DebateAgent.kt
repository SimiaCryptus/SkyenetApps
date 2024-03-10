package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.premium.DebateActors.*
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.util.TensorflowProjector

class DebateAgent(
  val api: API,
  dataStorage: StorageInterface,
  userId: User?,
  session: Session,
  val ui: ApplicationInterface,
  val domainName: String,
  val model : ChatModels = ChatModels.GPT4,
  val temperature: Double = 0.3,
  private val debateActors: DebateActors = DebateActors(model, temperature)
) : ActorSystem<ActorType>(debateActors.actorMap, dataStorage, userId, session) {
  private val outlines = mutableMapOf<String, Outline>()

  @Suppress("UNCHECKED_CAST")
  private val moderator get() = getActor(ActorType.MODERATOR) as ParsedActor<DebateSetup>

  fun debate(userMessage: String) {

    //language=HTML
    ui.newTask().complete(
      """
      <style>
        .response-message-question {
          font-weight: bold;
        }
        .response-message-actor {
          font-style: italic;
        }
      </style>
    """.trimIndent()
    )

    val moderatorTask = ui.newTask()
    moderatorTask.echo(renderMarkdown(userMessage))
    val moderatorResponse = this.moderator.answer(listOf(userMessage), api = api)
    moderatorTask.add(renderMarkdown(moderatorResponse.text))
    moderatorTask.verbose(toJson(moderatorResponse.obj))
    moderatorTask.complete()

    val allStatements =
      (moderatorResponse.obj.questions?.list ?: emptyList()).parallelStream().flatMap { question ->
        val questionTask = ui.newTask()
        questionTask.header(
          renderMarkdown(question.text ?: "").trim(),
          classname = "response-message response-message-question"
        )
        try {
          val result = (moderatorResponse.obj.debaters?.list ?: emptyList())
            .map { actor ->
              questionTask.header(actor.name?.trim() ?: "", classname = "response-message response-message-actor")
              val response = debateActors.getActorConfig(actor).answer(listOf(question.text ?: ""), api = api)
              outlines[actor.name!! + ": " + question.text!!] = response.obj
              questionTask.add(renderMarkdown(response.text))
              questionTask.verbose(toJson(response.obj))
              response.obj.arguments?.map { it.text ?: "" } ?: emptyList()
            }
          questionTask.complete()
          result.flatten().stream()
        } catch (e: Exception) {
          questionTask.error(ui, e)
          throw e
        }
      }.toList() + (moderatorResponse.obj.questions?.list?.mapNotNull { it.text }
        ?.filter { it.isNotBlank() } ?: emptyList())

    ui.newTask().complete(
      TensorflowProjector(
        api = api,
        dataStorage = dataStorage,
        sessionID = session,
        appPath = "debate_mapper",
        host = domainName,
        session = ui,
        userId = user,
      ).writeTensorflowEmbeddingProjectorHtml(*(allStatements).toTypedArray<String>())
    )

  }

}