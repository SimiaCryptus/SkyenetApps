package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookActors.ActorType.*
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.TextToSpeechActor
import java.util.function.Function

class IllustratedStorybookActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
  val imageModel: ImageModels = ImageModels.DallE2,
  voice: String = "alloy",
  voiceSpeed: Double = 1.0,
) {

  data class StoryData(
    @Description("The title of the story")
    val title: String? = null,
    @Description("The paragraphs of the story")
    val paragraphs: List<String>? = null,
    @Description("The genre of the story")
    val genre: String? = null,
    @Description("The target age group for the story")
    val targetAgeGroup: String? = null
  ) : ValidatedObject {
    override fun validate() = when {
      title.isNullOrBlank() -> "Title is required"
      paragraphs.isNullOrEmpty() -> "Paragraphs are required"
      genre.isNullOrBlank() -> "Genre is required"
      targetAgeGroup.isNullOrBlank() -> "Target age group is required"
      else -> null
    }
  }

  interface StoryDataParser : Function<String, StoryData> {
    @Description("Parse the text into a StoryData structure.")
    override fun apply(text: String): StoryData
  }

  interface UserPreferencesContentParser : Function<String, IllustratedStorybookAgent.UserPreferencesContent> {
    @Description("Parse the text into a UserPreferencesContent structure.")
    override fun apply(text: String): IllustratedStorybookAgent.UserPreferencesContent
  }

  private val requirementsActor = ParsedActor<IllustratedStorybookAgent.UserPreferencesContent>(
    parserClass = UserPreferencesContentParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are helping gather requirements for a storybook. 
            Respond to the user by suggesting a genre, target age group, and specific elements to include in the story.
        """.trimIndent()
  )

  private val storyGeneratorActor = ParsedActor<StoryData>(
    parserClass = StoryDataParser::class.java,
    model = ChatModels.GPT4Turbo,
    prompt = """
            You are an AI creating a story for a digital storybook. Generate a story that includes a title, storyline, dialogue, and descriptions.
            The story should be engaging and suitable for the specified target age group and genre.
        """.trimIndent()
  )


  private val illustrationGeneratorActor = ImageActor(
    prompt = "In less than 200 words, briefly describe an illustration to be created for a story with the given details",
    name = "IllustrationGenerator",
    imageModel = imageModel, // Assuming DallE2 is suitable for generating storybook illustrations
    temperature = 0.5, // Adjust temperature for creativity vs. coherence
    width = 1024, // Width of the generated image
    height = 1024 // Height of the generated image
  )

  private val narrator = TextToSpeechActor(voice = voice, speed = voiceSpeed)

  enum class ActorType {
    REQUIREMENTS_ACTOR,
    STORY_GENERATOR_ACTOR,
    ILLUSTRATION_GENERATOR_ACTOR,
    NARRATOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    STORY_GENERATOR_ACTOR to storyGeneratorActor,
    ILLUSTRATION_GENERATOR_ACTOR to illustrationGeneratorActor,
    REQUIREMENTS_ACTOR to requirementsActor,
    NARRATOR to narrator,
  )


  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(IllustratedStorybookActors::class.java)
  }
}
