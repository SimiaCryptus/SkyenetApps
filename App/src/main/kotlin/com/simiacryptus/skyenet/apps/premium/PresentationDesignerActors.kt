package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.*
import org.slf4j.LoggerFactory
import java.util.function.Function

class PresentationDesignerActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
  voice: String = "alloy",
  voiceSpeed: Double = 1.0,
) {


  data class IdeaList(
    @Description("A list of creative concepts.")
    val concepts: List<String>
  ) : ValidatedObject {
    override fun validate() = when {
      concepts.isEmpty() -> "At least one concept is required"
      concepts.any { it.isBlank() } -> "Concepts cannot be blank"
      else -> null
    }
  }

  interface IdeaListParser : Function<String, IdeaList> {
    @Description("Parses the text response into a list of creative concepts.")
    override fun apply(text: String): IdeaList
  }

  val initialAuthor = ParsedActor(
    parserClass = OutlineParser::class.java,
    model = ChatModels.GPT4Turbo,
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

  // Define the interface for parsing the text response into an Outline object
  interface OutlineParser : Function<String, Outline> {
    override fun apply(text: String): Outline
  }

  // Instantiate the outlineCreator as a ParsedActor<Outline>
  val outlineCreator = ParsedActor(
    parserClass = OutlineParser::class.java,
    prompt = """
            You are an assistant that creates structured outlines for presentations.
            Generate detailed outlines for each slide described by the input.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  data class Content(
    val detailedContent: List<String> // Assuming the key is the outline point title and the value is the detailed content
  ) : ValidatedObject {
    override fun validate() = when {
      detailedContent.isEmpty() -> "Detailed content is required"
      else -> null
    }
  }

  interface ContentParser : Function<String, Content> {
    @Description("Parse the text response into detailed content for each outline point.")
    override fun apply(text: String): Content
  }

  interface SlideDetailsParser : Function<String, SlideDetails> {
    override fun apply(text: String): SlideDetails
  }

  val contentExpander = ParsedActor(
    model = ChatModels.GPT4Turbo,
    prompt = """
            You are an assistant that expands outlines into detailed content. 
            Given an outline for a slide in a presentation, provide a comprehensive explanation or description for it.
        """.trimIndent(),
    parserClass = SlideDetailsParser::class.java
  )


  val styleFormatter = SimpleActor(
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

  interface RefinerParser : Function<String, SpeakingNotes> {
    @Description("Parse the response into a RefinedContent data structure.")
    override fun apply(text: String): SpeakingNotes
  }

  val speakerNotes = ParsedActor(
    parserClass = RefinerParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that refines content into speaking notes. Given a piece of content, refine it into speaking notes.
        """.trimIndent()
  )

  val imageRenderer = ImageActor(
    prompt = """
            Your task is to provide a useful image to accompany the content provided to you.
            You will reply with an image description which will then be rendered.
        """.trimIndent(),
    name = "ImageRenderer",
    imageModel = ImageModels.DallE3,
    temperature = 0.3
  )

  val narrator = TextToSpeechActor(voice = voice, speed = voiceSpeed)

  enum class ActorType {
    INITIAL_AUTHOR,
    CONTENT_EXPANDER,
    SLIDE_LAYOUT,
    SPEAKER_NOTES,
    IMAGE_RENDERER,
    NARRATOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    ActorType.INITIAL_AUTHOR to initialAuthor,
    ActorType.CONTENT_EXPANDER to contentExpander,
    ActorType.SLIDE_LAYOUT to styleFormatter,
    ActorType.SPEAKER_NOTES to speakerNotes,
    ActorType.IMAGE_RENDERER to imageRenderer,
    ActorType.NARRATOR to narrator,
  )

  companion object {
    val log = LoggerFactory.getLogger(PresentationDesignerActors::class.java)
  }
}