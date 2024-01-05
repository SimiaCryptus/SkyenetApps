package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory

open class PresentationDesignerAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<PresentationDesignerActors.ActorType>(
  PresentationDesignerActors(
    model = model,
    temperature = temperature,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val initialAuthor by lazy { getActor(PresentationDesignerActors.ActorType.INITIAL_AUTHOR) as ParsedActor<PresentationDesignerActors.Outline> }
  private val slideAuthor by lazy { getActor(PresentationDesignerActors.ActorType.CONTENT_EXPANDER) as SimpleActor }
  private val slideLayout by lazy { getActor(PresentationDesignerActors.ActorType.SLIDE_LAYOUT) as SimpleActor }
  private val speakerNotes by lazy { getActor(PresentationDesignerActors.ActorType.SPEAKER_NOTES) as ParsedActor<PresentationDesignerActors.SpeakingNotes> }
  private val imageRenderer by lazy { getActor(PresentationDesignerActors.ActorType.IMAGE_RENDERER) as ImageActor }

  fun main(userRequest: String) {
    val task = ui.newTask()
    try {
      task.echo(userRequest)
      task.header("Starting Presentation Generation")

      // Step 1: Generate ideas based on the user's request
      val ideaListResponse = initialAuthor.answer(listOf(userRequest), api = api)
      task.add(renderMarkdown("Slides generated: \n${ideaListResponse.obj.slides.joinToString("\n") { "* " + it.title }}"))

      // Step 2: Expand the outline into detailed content
      val contentTasks = ideaListResponse.obj.slides.mapNotNull { it.content }.map { slide ->
        pool.submit<String> { slideAuthor.answer(listOf(userRequest, slide), api = api) }
      }.mapNotNull { it.get() }

      // Step 3: Style the slides and generate speaking notes
      val styledSlidesAndNotesTasks = contentTasks.map { content ->
        val list = listOf(content)
        pool.submit<StyledSlideAndNotes> {
          StyledSlideAndNotes(
            styledContent = slideLayout.answer(list, api = api),
            speakingNotes = speakerNotes.answer(list, api = api).obj.content ?: "",
            image = task.image(imageRenderer.answer(list, api = api).image).toString(),
          )
        }
      }.mapNotNull { it.get() }

      // Step 4: Present the results
      val fileRefBase = "fileIndex/$session/"
      dataStorage.getSessionDir(user, session).resolve("slides.html").writeText(
        styledSlidesAndNotesTasks.joinToString("\n") {
          """
          |
          |${it.image.replace(fileRefBase,"")}
          |${it.styledContent}
          |
        """.trimMargin()
        }
      )
      task.add("<a href='${fileRefBase}slides.html'>Slides generated</a>")

      dataStorage.getSessionDir(user, session).resolve("notes.html").writeText(
        styledSlidesAndNotesTasks.joinToString("\n") {
          """
          |
          |${it.image.replace(fileRefBase,"")}
          |
          |${renderMarkdown(it.speakingNotes)}
          |
        """.trimMargin()
        }
      )
      task.add("<a href='${fileRefBase}notes.html'>Notes generated</a>")

      dataStorage.getSessionDir(user, session).resolve("combined.html").writeText(
        styledSlidesAndNotesTasks.joinToString("\n") { slideAndNotes ->
          """
          |<div class='slide-pair'>
          |    <div class='slide-image'>${slideAndNotes.image.replace(fileRefBase,"")}</div>
          |    <div class='slide-content'>${slideAndNotes.styledContent}</div>
          |    <div class='slide-notes'>${renderMarkdown(slideAndNotes.speakingNotes)}</div>
          |</div>
        """.trimMargin()
        }
      )
      task.add("<a href='${fileRefBase}combined.html'>Combined report generated</a>")

      task.complete("Presentation generation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  data class StyledSlideAndNotes(
    val styledContent: String,
    val speakingNotes: String,
    val image: String,
  )

  data class UserRequest(
    val topic: String,
    val details: String? = null
  )

  companion object {
    private val log = LoggerFactory.getLogger(PresentationDesignerAgent::class.java)

  }
}