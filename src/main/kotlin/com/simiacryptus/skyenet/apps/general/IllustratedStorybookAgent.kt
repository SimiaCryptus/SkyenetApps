package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import javax.imageio.ImageIO

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
      val illustrations = (storyData.paragraphs?.map { paragraph ->
        illustrationGeneratorActor(paragraph, userPreferencesContent)
      } ?: emptyList())
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
      task.add(MarkdownUtil.renderMarkdown(answer.text))
      task.verbose(JsonUtil.toJson(answer.obj))
      agentSystemArchitecture(answer.obj)
      task.complete("Generation complete!")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }


  // Assuming the storyText is of type AgentSystemArchitectureActors.StoryData and illustrations is a List<BufferedImage>
  private fun htmlFormatter(storyText: IllustratedStorybookActors.StoryData, illustrations: List<Pair<String, BufferedImage>?>, userPreferencesContent: UserPreferencesContent): String {
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
            // <img src="fileIndex/G-20240102-e3de5003/0642f463-00a9-41db-9857-cced3a09150f.png"> -> <img src="0642f463-00a9-41db-9857-cced3a09150f.png">
            val img = illustration.first.replace("fileIndex/$session/", "")
            htmlContent.append("<div class='story-illustration'>$img</div>")
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
      return "fileIndex/$session/$fileName"
    } catch (e: Throwable) {
      task.error(e)
      throw e
    } finally {
      task.complete("File management complete.")
    }
  }


  // Define the function for generating illustrations for a given story segment
  private fun illustrationGeneratorActor(segment: String, userPreferencesContent: UserPreferencesContent): Pair<String, BufferedImage>? {
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
      val imageHtml = task.image(illustrationResponse.image).toString()
      task.complete()

      // Return the generated AgentSystemArchitectureActors.image
      return imageHtml to illustrationResponse.image
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