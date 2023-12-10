package com.simiacryptus.skyenet.apps.beta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.function.Function
import javax.imageio.ImageIO


open class IllustratedStorybookApp(
  applicationName: String = "Illustrated Storybook Generator",
  domainName: String
) : ApplicationServer(
  applicationName = applicationName,
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT4Turbo,
    val temperature: Double = 0.5,
    val imageModel: ImageModels = ImageModels.DallE3
  )
  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      IllustratedStorybookAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        ui = ui,
        api = api,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
        imageModel = settings?.imageModel ?: ImageModels.DallE2,
      ).inputHandler(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(IllustratedStorybookApp::class.java)
  }

}



open class IllustratedStorybookAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT4Turbo,
  temperature: Double = 0.3,
  imageModel: ImageModels = ImageModels.DallE2,
) : ActorSystem<IllustratedStorybookActors.ActorType>(
  IllustratedStorybookActors(
  model = model,
  temperature = temperature,
  imageModel = imageModel,
).actorMap, dataStorage, user, session) {

  @Suppress("UNCHECKED_CAST")
  private val storyGeneratorActor by lazy { getActor(IllustratedStorybookActors.ActorType.STORY_GENERATOR_ACTOR) as ParsedActor<IllustratedStorybookActors.StoryData> }
  private val illustrationGeneratorActor by lazy { getActor(IllustratedStorybookActors.ActorType.ILLUSTRATION_GENERATOR_ACTOR) as ImageActor }
  @Suppress("UNCHECKED_CAST")
  private val requirementsActor by lazy { getActor(IllustratedStorybookActors.ActorType.REQUIREMENTS_ACTOR) as ParsedActor<UserPreferencesContent> }

  private fun agentSystemArchitecture(userPreferencesContent: UserPreferencesContent) {
    val task = ui.newTask()
    try {
      task.header("Starting Storybook Generation Process")

      // Step 1: Generate the story text using the Story Generator Actor
      task.add("Generating the story based on user preferences...")
      val storyData = storyGeneratorActor(userPreferencesContent)
      task.add("Story generated successfully with title: '${storyData.title}'")

      // Step 2: Generate illustrations for each paragraph of the story
      task.add("Generating illustrations for the story...")
      val illustrations = storyData.paragraphs?.map { paragraph ->
        illustrationGeneratorActor(paragraph, userPreferencesContent)
      } ?: emptyList()
      task.add("Illustrations generated successfully.")

      // Step 3: Format the story and illustrations into an HTML document
      task.add("Formatting the storybook into HTML...")
      val htmlStorybook = htmlFormatter(storyData, illustrations, userPreferencesContent)
      val savedStorybookPath = fileManager(htmlStorybook)
      task.complete("<a href='$savedStorybookPath' target='_blank'>Storybook Ready!</a>")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun inputHandler(userMessage: String) {
    val task = ui.newTask()
    try {
      task.echo(userMessage)
      val answer = requirementsActor.answer(listOf(userMessage), api = api)
      task.add(renderMarkdown(answer.text))
      task.verbose(toJson(answer.obj))
      agentSystemArchitecture(answer.obj)
      task.complete("Generation complete!")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }


  // Assuming the storyText is of type AgentSystemArchitectureActors.StoryData and illustrations is a List<BufferedImage>
  private fun htmlFormatter(storyText: IllustratedStorybookActors.StoryData, illustrations: List<BufferedImage?>, userPreferencesContent: UserPreferencesContent): String {
    val task = ui.newTask()
    try {
      task.header("Formatting Storybook")

      // Initialize HTML content with a StringBuilder
      val htmlContent = StringBuilder()

      // Start of HTML document
      htmlContent.append("<html><head><title>${storyText.title}</title></head><body>")

      // Add CSS styles for the storybook
      htmlContent.append("""
                <style>
                    body {
                        font-family: 'Arial', sans-serif;
                    }
                    .story-title {
                        text-align: center;
                        font-size: 2em;
                        margin-top: 20px;
                    }
                    .story-paragraph {
                        text-align: justify;
                        margin: 15px;
                    }
                    .story-illustration {
                        text-align: center;
                        margin: 20px;
                    }
                    .story-illustration img {
                        max-width: 100%;
                        height: auto;
                    }
                </style>
            """.trimIndent())

      // Add the story title
      htmlContent.append("<div class='story-title'>${storyText.title}</div>")

      // Add each paragraph and corresponding illustration
      storyText.paragraphs?.forEachIndexed { index, paragraph ->
        htmlContent.append("<div class='story-paragraph'>$paragraph</div>")
        if (index < illustrations.size) {
          val illustration = illustrations[index]
          if(illustration != null) {
            val base64Image = encodeToBase64(illustration)
            htmlContent.append("<div class='story-illustration'><img src='data:AgentSystemArchitectureActors.image/png;base64,$base64Image' /></div>")
          }
        }
      }

      // End of HTML document
      htmlContent.append("</body></html>")

      task.complete("Storybook formatting complete.")
      return htmlContent.toString()
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  // Helper function to encode BufferedImage to Base64 string
  private fun encodeToBase64(image: BufferedImage): String {
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "png", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
  }


  private fun fileManager(htmlStorybook: String): String {
    val task = ui.newTask()
    try {
      task.header("Saving Storybook File")

      // Generate a unique file name for the storybook
      val fileName = "storybook_${UUID.randomUUID()}.html"
      val directoryPath = dataStorage.getSessionDir(user, session).toPath()
      val filePath = directoryPath.resolve(fileName)

      // Ensure the directory exists
      Files.createDirectories(directoryPath)

      // Write the HTML content to the file
      Files.writeString(filePath, htmlStorybook, StandardOpenOption.CREATE_NEW)

      task.add("Storybook saved successfully at: $filePath")

      // Return the path to the saved file as a string
      return "fileIndex/$session/"+ filePath.toString()
    } catch (e: Throwable) {
      task.error(e)
      throw e
    } finally {
      task.complete("File management complete.")
    }
  }


  // Define the function for generating illustrations for a given story segment
  private fun illustrationGeneratorActor(segment: String, userPreferencesContent: UserPreferencesContent): BufferedImage? {
    val task = ui.newTask()
    try {
      task.header("Generating Illustration")

      // Construct the conversation thread with the story segment and user preferences
      val conversationThread = listOf(
        segment,
        "The illustration should reflect the genre: ${userPreferencesContent.genre}",
        "It should be appropriate for the target age group: ${userPreferencesContent.targetAgeGroup}",
        "Please include the following elements if possible: ${userPreferencesContent.specificElements.joinToString(", ")}"
      )

      // Generate the illustration using the illustrationGeneratorActor
      val illustrationResponse = illustrationGeneratorActor.answer(conversationThread, api = api)

      // Log the AgentSystemArchitectureActors.image description
      task.add("Illustration description: ${illustrationResponse.text}")
      task.image(illustrationResponse.image)
      task.complete()

      // Return the generated AgentSystemArchitectureActors.image
      return illustrationResponse.image
    } catch (e: Throwable) {
      task.error(e)
      return null
    }
  }

  // Define the structure for user preferences
  data class UserPreferencesContent(
    val genre: String,
    val targetAgeGroup: String,
    val specificElements: List<String> // List of specific elements to include in the story
  )

  // Implement the storyGeneratorActor function
  private fun storyGeneratorActor(userPreferencesContent: UserPreferencesContent): IllustratedStorybookActors.StoryData {
    val task = ui.newTask()
    try {
      task.header("Generating Story")

      // Construct the conversation thread with user preferences
      val conversationThread = listOf(
        "Genre: ${userPreferencesContent.genre}",
        "Target Age Group: ${userPreferencesContent.targetAgeGroup}",
        "Specific Elements: ${userPreferencesContent.specificElements.joinToString(", ")}"
      )

      // Generate the story using the storyGeneratorActor
      val storyResponse = storyGeneratorActor.answer(conversationThread, api = api)

      // Log the natural language answer
      task.add("Story generated: ${storyResponse.text}")

      // Return the parsed story data
      return storyResponse.obj
    } catch (e: Throwable) {
      task.error(e)
      throw e
    } finally {
      task.complete("Story generation complete.")
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(IllustratedStorybookAgent::class.java)

  }
}



class IllustratedStorybookActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
  val imageModel: ImageModels = ImageModels.DallE2,
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

  enum class ActorType {
    REQUIREMENTS_ACTOR,
    STORY_GENERATOR_ACTOR,
    ILLUSTRATION_GENERATOR_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
    ActorType.STORY_GENERATOR_ACTOR to storyGeneratorActor,
    ActorType.ILLUSTRATION_GENERATOR_ACTOR to illustrationGeneratorActor,
    ActorType.REQUIREMENTS_ACTOR to requirementsActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(IllustratedStorybookActors::class.java)
  }
}
