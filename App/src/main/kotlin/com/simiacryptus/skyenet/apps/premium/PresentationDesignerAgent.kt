package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerActors.*
import com.simiacryptus.skyenet.core.actors.*
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
) : ActorSystem<ActorType>(
  PresentationDesignerActors(
    model = model,
    temperature = temperature,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val initialAuthor by lazy { getActor(ActorType.INITIAL_AUTHOR) as ParsedActor<Outline> }
  private val slideAuthor by lazy { getActor(ActorType.CONTENT_EXPANDER) as ParsedActor<SlideDetails> }
  private val slideLayout by lazy { getActor(ActorType.SLIDE_LAYOUT) as SimpleActor }
  private val speakerNotes by lazy { getActor(ActorType.SPEAKER_NOTES) as ParsedActor<SpeakingNotes> }
  private val imageRenderer by lazy { getActor(ActorType.IMAGE_RENDERER) as ImageActor }
  private val narrator get() = getActor(ActorType.NARRATOR) as TextToSpeechActor

  fun main(userRequest: String) {
    val task = ui.newTask()
    try {
      task.echo(userRequest)
      task.header("Starting Presentation Generation")

      // Step 1: Generate ideas based on the user's request
      val ideaListResponse = initialAuthor.answer(listOf(userRequest), api = api)
      task.add(renderMarkdown("Slides generated: \n${ideaListResponse.obj.slides.joinToString("\n") { "* " + it.title }}"))

      // Step 2: Expand the outline into detailed content
      val content = ideaListResponse.obj.slides.mapNotNull { it.content }.map { slide ->
        pool.submit<SlideDetails> { slideAuthor.answer(listOf(userRequest, slide), api = api).obj }
      }.mapNotNull { it.get() }

      // Step 3: Style the slides and generate speaking notes
      val styledSlidesAndNotes = content.withIndex().map { (idx, content) ->
        val list = listOf(content.html!!)
        val slideTask = ui.newTask()
        slideTask.header("Generating slide $idx: ${content.title}")
        pool.submit<StyledSlideAndNotes> {
          try {
            val image = imageRenderer.answer(list, api = api).image
            val imageStr = slideTask.image(image).toString()
            val layout = slideLayout.answer(list, api = api)
            //language=HTML
            slideTask.add(
              """
              <div>$layout</div>
            """.trimIndent()
            )
            val speakingNotes = speakerNotes.answer(list, api = api).obj.content ?: ""
            slideTask.verbose(renderMarkdown(speakingNotes), tag = "div")
            val mp3data = narrator.answer(listOf(speakingNotes), api = api).mp3data
            val mp3link = if (null != mp3data) {
              val mp3link = slideTask.saveFile(
                "slide$idx.mp3",
                mp3data
              )
              slideTask.add(
                "<audio controls><source src='$mp3link' type='audio/mpeg'></audio>"
              )
              mp3link
            } else null
            StyledSlideAndNotes(
              styledContent = layout,
              speakingNotes = speakingNotes,
              image = imageStr,
              mp3link = mp3link,
            )
          } catch (e: Throwable) {
            slideTask.error(e)
            throw e
          } finally {
            slideTask.complete("Slide $idx complete.")
          }
        }
      }.mapNotNull { it.get() }

      // Step 4: Present the results
      val fileRefBase = "fileIndex/$session/"
      dataStorage.getSessionDir(user, session).resolve("slides.html").writeText("""
        |<html>
        |<head>
        |<style>
        |.slide {
        |  display: inline-block;
        |  width: 50%;
        |  vertical-align: top;
        |}
        |</style>
        |</head>
        |<body>
        |<button id='playAll'>Play All Slides</button>
        |<script>
        |document.getElementById('playAll').addEventListener('click', function() {
        |  const slides = document.querySelectorAll('.slide');
        |  let currentSlide = 0;
        |  function playNextSlide() {
        |    if (currentSlide >= slides.length) return;
        |    const slide = slides[currentSlide];
        |    const audio = slide.querySelector('audio');
        |    const image = slide.querySelector('.slide-image');
        |    if (audio) {
        |      image.scrollIntoView({ behavior: 'smooth', block: 'center' });
        |      audio.play();
        |      audio.onended = function() {
        |        currentSlide++;
        |        playNextSlide();
        |      };
        |    } else {
        |      currentSlide++;
        |      playNextSlide();
        |    }
        |  }
        |  playNextSlide();
        |});
        |</script>
        |${
        styledSlidesAndNotes.withIndex().joinToString("\n") { (i, it) ->
          """
        |
        |<div class='slide' id='slide$i'>
        |  <div class='slide-image'>${it.image.replace(fileRefBase, "")}</div>
        |  <audio controls><source src='${it.mp3link?.replace(fileRefBase, "") ?: ""}' type='audio/mpeg'></audio>
        |  <div class='slide-content'>${it.styledContent}</div>
        |</div>
        |
        """.trimMargin()
        }
      }
        |</body>
        |</html>
        """.trimMargin())
      task.add("<a href='${fileRefBase}slides.html'>Slides generated</a>")

      dataStorage.getSessionDir(user, session).resolve("notes.html").writeText("""
        |<html>
        |<head>
        |<style>
        |.slide {
        |  display: inline-block;
        |  width: 50%;
        |  vertical-align: top;
        |}
        |</style>
        |</head>
        |<body>
        |<button id='playAll'>Play All Slides</button>
        |<script>
        |document.getElementById('playAll').addEventListener('click', function() {
        |  const slides = document.querySelectorAll('.slide');
        |  let currentSlide = 0;
        |  function playNextSlide() {
        |    if (currentSlide >= slides.length) return;
        |    const slide = slides[currentSlide];
        |    const audio = slide.querySelector('audio');
        |    const image = slide.querySelector('.slide-image');
        |    if (audio) {
        |      image.scrollIntoView({ behavior: 'smooth', block: 'center' });
        |      audio.play();
        |      audio.onended = function() {
        |        currentSlide++;
        |        playNextSlide();
        |      };
        |    } else {
        |      currentSlide++;
        |      playNextSlide();
        |    }
        |  }
        |  playNextSlide();
        |});
        |</script>
        |
        |${
        styledSlidesAndNotes.withIndex().joinToString("\n") { (i, it) ->
          """
        |
        |<div class='slide' id='slide$i'>
        |  <audio controls><source src='${it.mp3link?.replace(fileRefBase, "") ?: ""}' type='audio/mpeg'></audio>
        |  <div class='slide-notes'>
        |  ${renderMarkdown("""
        |```markdown
        |${it.speakingNotes}
        |```""".trimMargin())}
        |</div>
        |  <div class='slide-content'>${it.styledContent}</div>
        |</div>
        |
        """.trimMargin()
        }
      }
        |</body>
        |</html>
        """.trimMargin())
      task.add("<a href='${fileRefBase}notes.html'>Notes generated</a>")

      dataStorage.getSessionDir(user, session).resolve("combined.html").writeText("""
        |<html>
        |<head>
        |<style>
        |.slide {
        |  display: inline-block;
        |  width: 50%;
        |  vertical-align: top;
        |}
        |</style>
        |</head>
        |<body>
        |<button id='playAll'>Play All Slides</button>
        |<script>
        |document.getElementById('playAll').addEventListener('click', function() {
        |  const slides = document.querySelectorAll('.slide');
        |  let currentSlide = 0;
        |  function playNextSlide() {
        |    if (currentSlide >= slides.length) return;
        |    const slide = slides[currentSlide];
        |    const audio = slide.querySelector('audio');
        |    const image = slide.querySelector('.slide-image');
        |    if (audio) {
        |      image.scrollIntoView({ behavior: 'smooth', block: 'center' });
        |      audio.play();
        |      audio.onended = function() {
        |        currentSlide++;
        |        playNextSlide();
        |      };
        |    } else {
        |      currentSlide++;
        |      playNextSlide();
        |    }
        |  }
        |  playNextSlide();
        |});
        |</script>
        |
        |${
        styledSlidesAndNotes.withIndex().joinToString("\n") { (i, it) ->
          """
        |
        |<div class='slide' id='slide$i'>
        |  <div class='slide-image'>${it.image.replace(fileRefBase, "")}</div>
        |  <audio controls><source src='${it.mp3link?.replace(fileRefBase, "") ?: ""}' type='audio/mpeg'></audio>
        |  <div class='slide-content'>${it.styledContent}</div>
        |  <div class='slide-notes'>${renderMarkdown(it.speakingNotes)}</div>
        |</div>
        |
        """.trimMargin()
        }
      }
        |</body>
        |</html>
        """.trimMargin()
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
    val mp3link: String? = null,
  )

  data class UserRequest(
    val topic: String,
    val details: String? = null
  )

  companion object {
    private val log = LoggerFactory.getLogger(PresentationDesignerAgent::class.java)

  }
}