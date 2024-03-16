package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerActors.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.util.StringSplitter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory


open class PresentationDesignerApp(
  applicationName: String = "Presentation Generator v1.3",
) : ApplicationServer(
  applicationName = applicationName,
  path = "/presentation",
) {

  override val description: String
    @Language("HTML")
    get() = "<div>" + MarkdownUtil.renderMarkdown(
      """
        Welcome to the Presentation Designer, an app designed to help you create presentations with ease.
        
        Enter a prompt, and the Presentation Designer will generate a presentation for you, complete with slides, images, and speaking notes!                  
      """.trimIndent()
    ) + "</div>"

  data class Settings(
    val model: OpenAITextModel = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
    val voice : String? = "alloy",
    val voiceSpeed : Double? = 1.0,
    val budget : Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
      PresentationDesignerAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        ui = ui,
        api = api,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
        voice = settings?.voice ?: "alloy",
        voiceSpeed = settings?.voiceSpeed ?: 1.0,
      ).main(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(PresentationDesignerApp::class.java)
  }

}


open class PresentationDesignerAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: OpenAITextModel = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
  val voice: String = "alloy",
  val voiceSpeed: Double = 1.0,
) : ActorSystem<ActorType>(
  PresentationDesignerActors(
    model = model,
    temperature = temperature,
    voice = voice,
    voiceSpeed = voiceSpeed,
  ).actorMap.map { it.key.name to it.value.javaClass }.toMap(), dataStorage, user, session
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
      task.add(MarkdownUtil.renderMarkdown("Slides generated: \n${ideaListResponse.obj.slides.joinToString("\n") { "* " + it.title }}"))

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
            slideTask.error(ui, e)
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
      task.error(ui, e)
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
    task.add("""<div>${MarkdownUtil.renderMarkdown(summary)}</div>""".trimIndent())
    val slideContent = slideLayout.answer(listOf(summary), api = api).replace("image.png", imageURL)
    //language=HTML
    task.add("""<div>$slideContent</div>""".trimIndent())
    val speakingNotes = speakerNotes.answer(list, api = api).obj.content ?: ""
    task.verbose(MarkdownUtil.renderMarkdown(speakingNotes), tag = "div")
    val mp3data = partition(speakingNotes).map { narrator.answer(listOf(it), api = api).mp3data }
    val mp3links =
      mp3data.withIndex().map { (i, it) -> if (null != it) task.saveFile("slide$idx-$i.mp3", it) else "" }
    mp3links.forEach { task.add("""<audio preload="none" controls><source src='$it' type='audio/mpeg'></audio>""") }
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
              """<audio preload="none" controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
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
            """<audio preload="none" controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
          } ?: ""
        }
          |  <div class='slide-notes'>
          |  ${
          MarkdownUtil.renderMarkdown(
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
            """<audio preload="none" controls><source src='${mp3link.replace(refBase, "")}' type='audio/mpeg'></audio>"""
          } ?: ""
        }
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |  <div class='slide-content'>${it.fullContent.replace(refBase, "")}</div>
          |  <div class='slide-notes'>${MarkdownUtil.renderMarkdown(it.speakingNotes)}</div>
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
    |  overflow: auto;
    |  width: 100%;
    |  height: -webkit-fill-available;
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

  }
}


class PresentationDesignerActors(
  val model: OpenAITextModel = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
  voice: String = "alloy",
  voiceSpeed: Double = 1.0,
) {


  private val initialAuthor = ParsedActor(
//    parserClass = OutlineParser::class.java,
    resultClass = Outline::class.java,
    model = ChatModels.GPT4Turbo,
    parsingModel = ChatModels.GPT35Turbo,
    prompt = """
            You are a high-level presentation planner. Based on an input topic, provide a list of slides with a brief description of each.
        """.trimIndent()
  )


  // Define the data class to represent an outline item
  data class SlideInfo(
    @Description("The title of the slide.")
    val title: String,
    @Description("The detailed content for each outline point.")
    val content: String? = null
  ) : ValidatedObject {
    override fun validate() = when {
      title.isBlank() -> "title is required"
      else -> null
    }
  }

  // Define the data class to represent the entire outline
  data class Outline(
    val slides: List<SlideInfo>
  ) : ValidatedObject {
    override fun validate() = when {
      slides.isEmpty() -> "items are required"
      else -> null
    }
  }

  data class SlideDetails(
    val title: String? = null,
    val html: String? = null,
  ) : ValidatedObject {
    override fun validate() = when {
      title.isNullOrBlank() -> "Title is required"
      html.isNullOrBlank() -> "HTML is required"
      else -> null
    }
  }


  data class Content(
    val detailedContent: List<String> // Assuming the key is the outline point title and the value is the detailed content
  ) : ValidatedObject {
    override fun validate() = when {
      detailedContent.isEmpty() -> "Detailed content is required"
      else -> null
    }
  }

  private val contentExpander = ParsedActor(
    model = ChatModels.GPT4Turbo,
    prompt = """
      You are an assistant that expands outlines into detailed content. 
      Given an outline for a slide in a presentation, provide a comprehensive explanation or description for it.
      """.trimIndent(),
    parsingModel = ChatModels.GPT35Turbo,
//    parserClass = SlideDetailsParser::class.java
    resultClass = SlideDetails::class.java,
  )


  private val slideSummarizer = SimpleActor(
    prompt = """
        You are a writing assistant. Your task is to summarize content from a speech. 
        When you receive content, summarize it into about 100 words.
        """.trimIndent(),
    name = "StyleFormatter",
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )

  private val slideFormatter = SimpleActor(
    prompt = """
        You are a presentation slide designer. Your task is to present content it in a visually appealing and consumable form. 
        When you receive content, format it using HTML and CSS to create a professional and polished look.
        The HTML output should be contained within a div with class="slide" with an aspect ratio of 16:9.
        In addition, incorporate a single image into the slide named "image.png" with proper sizing and placement.
        """.trimIndent(),
    name = "StyleFormatter",
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )

  private val contentFormatter = SimpleActor(
    prompt = """
        You are a style formatter. Your task is to apply visual styling to the content provided to you. 
        When you receive content, format it using HTML and CSS to create a professional and polished look.
        """.trimIndent(),
    name = "StyleFormatter",
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )

  data class SpeakingNotes(
    @Description("The markdown-formatted speaking notes.")
    val content: String? = null
  ) : ValidatedObject {
    override fun validate() = when {
      content.isNullOrBlank() -> "Refined content is required"
      else -> null
    }
  }

  private val speakerNotes = ParsedActor(
//    parserClass = RefinerParser::class.java,
    resultClass = SpeakingNotes::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that creates speaking transcripts from content. 
            Given a piece of content, transform it into the input for a text-to-speech system.
            Do not use formatting or HTML tags. Use capitalization and punctuation for emphasis.
        """.trimIndent(),
    parsingModel = ChatModels.GPT35Turbo,
  )

  private val imageRenderer = ImageActor(
    prompt = """
            Your task is to provide a useful image to accompany the content provided to you.
            You will reply with an image description which will then be rendered.
        """.trimIndent(),
    name = "ImageRenderer",
    imageModel = ImageModels.DallE3,
    textModel = ChatModels.GPT35Turbo,
    temperature = 0.3
  )

  private val narrator = TextToSpeechActor(voice = voice, speed = voiceSpeed, models = ChatModels.GPT35Turbo)

  enum class ActorType {
    INITIAL_AUTHOR,
    CONTENT_EXPANDER,
    CONTENT_LAYOUT,
    SLIDE_SUMMARY,
    SLIDE_LAYOUT,
    SPEAKER_NOTES,
    IMAGE_RENDERER,
    NARRATOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    ActorType.INITIAL_AUTHOR to initialAuthor,
    ActorType.CONTENT_EXPANDER to contentExpander,
    ActorType.SLIDE_LAYOUT to slideFormatter,
    ActorType.SLIDE_SUMMARY to slideSummarizer,
    ActorType.CONTENT_LAYOUT to contentFormatter,
    ActorType.SPEAKER_NOTES to speakerNotes,
    ActorType.IMAGE_RENDERER to imageRenderer,
    ActorType.NARRATOR to narrator,
  )

  companion object {
    val log = LoggerFactory.getLogger(PresentationDesignerActors::class.java)
  }
}