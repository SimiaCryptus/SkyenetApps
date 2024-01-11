package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerActors.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.util.StringSplitter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory


open class PresentationDesignerAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
  val voice: String = "alloy",
  val voiceSpeed: Double = 1.0,
) : ActorSystem<ActorType>(
  PresentationDesignerActors(
    model = model,
    temperature = temperature,
    voice = voice,
    voiceSpeed = voiceSpeed,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val initialAuthor by lazy { getActor(ActorType.INITIAL_AUTHOR) as ParsedActor<Outline> }
  private val slideAuthor by lazy { getActor(ActorType.CONTENT_EXPANDER) as ParsedActor<SlideDetails> }
  private val slideLayout by lazy { getActor(ActorType.SLIDE_LAYOUT) as SimpleActor }
  private val slideSummary by lazy { getActor(ActorType.SLIDE_SUMMARY) as SimpleActor }
  private val contentLayout by lazy { getActor(ActorType.CONTENT_LAYOUT) as SimpleActor }
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
        val slideTask = ui.newTask()
        slideTask.header("Generating slide $idx: ${content.title}")
        pool.submit<SlideContents> {
          try {
            slideContents(idx, content, slideTask)
          } catch (e: Throwable) {
            slideTask.error(e)
            null
          } finally {
            slideTask.complete("Slide $idx complete.")
          }
        }
      }.mapNotNull { it.get() }

      // Step 4: Present the results
      writeReports(styledSlidesAndNotes, task)

      task.complete("Presentation generation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  private fun slideContents(
    idx: Int,
    content: SlideDetails,
    task: SessionTask
  ): SlideContents {
    val list = listOf(content.html!!)
    val image = imageRenderer.answer(list, api = api).image
    val imageStr = task.image(image)
      .toString() // eg <div><div class="response-message"><img src="fileIndex/G-20240110-96a96b54/fb5e6766-0d57-4914-a147-2924913f4927.png" /></div></div>
    val imageURL = imageStr.substringAfter("src=\"").substringBefore("\"")
    val fullHtml = contentLayout.answer(list, api = api)
    //language=HTML
    task.add("""<div>$fullHtml</div>""".trimIndent())
    val summary = slideSummary.answer(list, api = api)
    //language=HTML
    task.add("""<div>${renderMarkdown(summary)}</div>""".trimIndent())
    val slideContent = slideLayout.answer(listOf(summary), api = api).replace("image.png", imageURL)
    //language=HTML
    task.add("""<div>$slideContent</div>""".trimIndent())
    val speakingNotes = speakerNotes.answer(list, api = api).obj.content ?: ""
    task.verbose(renderMarkdown(speakingNotes), tag = "div")
    val mp3data = partition(speakingNotes).map { narrator.answer(listOf(it), api = api).mp3data }
    val mp3links =
      mp3data.withIndex().map { (i, it) -> if (null != it) task.saveFile("slide$idx-$i.mp3", it) else "" }
    mp3links.forEach { task.add("<audio controls><source src='$it' type='audio/mpeg'></audio>") }
    return SlideContents(
      slideContent = slideContent,
      fullContent = fullHtml,
      speakingNotes = speakingNotes,
      image = imageStr,
      mp3links = mp3links,
    )
  }

  private fun writeReports(
    slideContents: List<SlideContents>,
    task: SessionTask
  ) {
    val refBase = "fileIndex/$session/"
    dataStorage.getSessionDir(user, session).resolve("slides.html").writeText(
      """
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
        slideContents.withIndex().joinToString("\n") { (i, it) ->
          """
          |
          |<div class='slide-container' id='slide$i'>
          |  ${
            it.mp3links?.joinToString("\n") { mp3link ->
              """<audio controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
            } ?: ""
          }
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |</div>
          |
          """.trimMargin()
        }
      }
          |</body>
          |</html>
          """.trimMargin())
    task.add("<a href='${refBase}slides.html'>Slides generated</a>")

    dataStorage.getSessionDir(user, session).resolve("notes.html").writeText("""
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
      slideContents.withIndex().joinToString("\n") { (i, it) ->
        """
          |
          |<div class='slide-container' id='slide$i'>
          |  ${
          it.mp3links?.joinToString("\n") { mp3link ->
            """<audio controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
          } ?: ""
        }
          |  <div class='slide-notes'>
          |  ${
          renderMarkdown(
            """
          |```markdown
          |${it.speakingNotes}
          |```""".trimMargin()
          )
        }
          |</div>
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |  <div class='slide-content'>${it.fullContent.replace(refBase, "")}</div>
          |</div>
          |
          """.trimMargin()
      }
    }
          |</body>
          |</html>
          """.trimMargin())
    task.add("<a href='${refBase}notes.html'>Notes generated</a>")

    dataStorage.getSessionDir(user, session).resolve("combined.html").writeText("""
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
      slideContents.withIndex().joinToString("\n") { (i, it) ->
        """
          |
          |<div class='slide-container' id='slide$i'>
          |  <div class='slide-image'>${it.image.replace(refBase, "")}</div>
          |  ${
          it.mp3links?.joinToString("\n") { mp3link ->
            """<audio controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
          } ?: ""
        }
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |  <div class='slide-content'>${it.fullContent.replace(refBase, "")}</div>
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
    task.add("<a href='${refBase}combined.html'>Combined report generated</a>")
  }

  private val css = """
    |.slide-container {
    |  display: inline-block;
    |  width: 50%;
    |  vertical-align: top;
    |}
    |""".trimMargin()

  @Language("HTML")
  private val playAll = """
    |<button id='playAll'>Play All Slides</button>
    |<script>
    |    document.getElementById('playAll').addEventListener('click', function () {
    |        const slides = document.querySelectorAll('.slide-container');
    |        let currentSlide = 0;
    |        let currentAudio = 0;
    |        
    |        function playNextSlide() {
    |            if (currentSlide >= slides.length) return;
    |            const slide = slides[currentSlide];
    |            const audios = slide.querySelectorAll('audio');
    |            if (currentAudio >= audios.length) {
    |                currentSlide++;
    |                currentAudio = 0;
    |                playNextSlide();
    |                return;
    |            }
    |            const audio = audios[currentAudio];
    |            const image = slide.querySelector('.slide-content');
    |            if (audio) {
    |                image.scrollIntoView({behavior: 'smooth', block: 'start', inline: 'start'});
    |                audio.play();
    |                audio.onended = function () {
    |                    currentAudio++;
    |                    playNextSlide();
    |                };
    |            } else {
    |                currentAudio++;
    |                playNextSlide();
    |            }
    |        }
    |
    |        playNextSlide();
    |    });
    |</script>
    |""".trimMargin()

  private fun partition(speakingNotes: String): List<String> = when {
    speakingNotes.length < 4000 -> listOf(speakingNotes)
    else -> StringSplitter.split(
      speakingNotes, mapOf(
        "." to 2.0,
        " " to 1.0,
        ", " to 2.0,
      )
    ).toList().flatMap { partition(it) }
  }

  data class SlideContents(
    val slideContent: String,
    val fullContent: String,
    val speakingNotes: String,
    val image: String,
    val mp3links: List<String>? = null,
  )

  companion object {
    private val log = LoggerFactory.getLogger(PresentationDesignerAgent::class.java)

  }
}