package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookActors.ActorType
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookActors.StoryData
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.TextToSpeechActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*

open class IllustratedStorybookAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT4Turbo,
  temperature: Double = 0.3,
  imageModel: ImageModels = ImageModels.DallE2,
  val voice: String = "alloy",
  val voiceSpeed: Double = 1.0,
) : ActorSystem<ActorType>(
  IllustratedStorybookActors(
    model = model,
    temperature = temperature,
    imageModel = imageModel,
    voice = voice,
    voiceSpeed = voiceSpeed,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val storyGeneratorActor by lazy { getActor(ActorType.STORY_GENERATOR_ACTOR) as ParsedActor<StoryData> }
  private val illustrationGeneratorActor by lazy { getActor(ActorType.ILLUSTRATION_GENERATOR_ACTOR) as ImageActor }

  @Suppress("UNCHECKED_CAST")
  private val requirementsActor by lazy { getActor(ActorType.REQUIREMENTS_ACTOR) as ParsedActor<UserPreferencesContent> }
  private val narratorActor by lazy { getActor(ActorType.NARRATOR) as TextToSpeechActor }

  private fun agentSystemArchitecture(userPreferencesContent: UserPreferencesContent) {
    val task = ui.newTask()
    try {
      task.header("Starting Storybook Generation Process")

      // Step 1: Generate the story text using the Story Generator Actor
      task.add("Generating the story based on user preferences...")
      val storyData: StoryData = storyGeneratorActor(userPreferencesContent)
      task.add("Story generated successfully with title: '${storyData.title}'")

      // Step 2: Generate illustrations for each paragraph of the story
      task.add("Generating illustrations for the story...")
      val illustrations = (storyData.paragraphs?.map { paragraph ->
        pool.submit<Pair<String, BufferedImage>?> { illustrationGeneratorActor(paragraph, userPreferencesContent) }
      }?.toTypedArray() ?: emptyArray()).map { it.get() }
      task.add("Illustrations generated successfully.")

      task.add("Generating narration for the story...")
      val narrations = (storyData.paragraphs?.withIndex()?.map { (idx, paragraph) ->
        pool.submit<String> { narratorActor.answer(listOf(paragraph), api).mp3data?.let {
          val fileLocation = task.saveFile("narration$idx.mp3", it)
          task.add("<audio controls><source src='$fileLocation' type='audio/mpeg'></audio>")
          fileLocation
        } }
      }?.toTypedArray() ?: emptyArray()).map { it.get() }

      // Step 3: Format the story and illustrations into an HTML document
      task.add("Formatting the storybook into HTML...")
      val htmlStorybook = htmlFormatter(storyData, illustrations, userPreferencesContent, narrations)
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
  private fun htmlFormatter(
    storyText: StoryData,
    illustrations: List<Pair<String, BufferedImage>?>,
    userPreferencesContent: UserPreferencesContent,
    narrations: List<String?>
  ): String {
    val task = ui.newTask()
    try {
      task.header("Formatting Storybook")

      // Initialize HTML content with a StringBuilder
      val htmlContent = StringBuilder()

      // Start of HTML document
      //language=HTML
      htmlContent.append("""
        |<html>
        |<head><title>${storyText.title}</title></head>
        |<body>
        |<style>
        |    body {
        |        font-family: 'Arial', sans-serif;
        |    }
        |
        |    .story-title {
        |        text-align: center;
        |        font-size: 2em;
        |        margin-top: 20px;
        |    }
        |
        |    .story-paragraph {
        |        text-align: justify;
        |        margin: 15px;
        |    }
        |
        |    .story-illustration {
        |        text-align: center;
        |        margin: 20px;
        |    }
        |
        |    .story-illustration img {
        |        max-width: 100%;
        |        height: auto;
        |    }
        |</style>
        |<div class='story-title'>
        |  ${storyText.title}
        |  <button id='playAll'>Play All</button>
        |</div>
        |<script>
        |document.getElementById('playAll').addEventListener('click', function() {
        |  const slides = document.querySelectorAll('.story-page');
        |  let currentSlide = 0;
        |  function playNext() {
        |    if (currentSlide >= slides.length) return;
        |    const slide = slides[currentSlide];
        |    const audio = slide.querySelector('audio');
        |    const image = slide.querySelector('.story-illustration');
        |    if (audio) {
        |      image.scrollIntoView({ behavior: 'smooth', block: 'start' });
        |      audio.play();
        |      audio.onended = function() {
        |        currentSlide++;
        |        playNext();
        |      };
        |    } else {
        |      currentSlide++;
        |      playNext();
        |    }
        |  }
        |  playNext();
        |});
        |</script>
        """.trimMargin()
      )

      val indexedNarrations = narrations.withIndex().associate { (idx, narration) -> idx to narration }

      // Add each paragraph and corresponding illustration
      storyText.paragraphs?.forEachIndexed { index, paragraph ->
        val prefix = "fileIndex/$session/"
        val narration = (if (index >= indexedNarrations.size) null else indexedNarrations[index]) ?: ""
        val illustration = (if (index >= illustrations.size) null else illustrations[index]?.first) ?: ""
        //language=HTML
        htmlContent.append(
          """
            |<div class='story-page'>
            |    <div class='story-illustration'>${illustration.replace(prefix, "")}</div>
            |    <audio controls><source src='${narration.replace(prefix, "")}' type='audio/mpeg'></audio>
            |    <div class='story-paragraph'>$paragraph</div>
            |</div>
            |""".trimMargin()
        )
      }

      // End of HTML document
      htmlContent.append("</body></html>")

      task.complete("Storybook complete.")
      return htmlContent.toString()
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
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
  private fun illustrationGeneratorActor(
    segment: String,
    userPreferencesContent: UserPreferencesContent
  ): Pair<String, BufferedImage>? {
    val task = ui.newTask()
    try {
      //task.add(renderMarkdown(segment))

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
      task.add(renderMarkdown(illustrationResponse.text), className = "illustration-caption")
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
  private fun storyGeneratorActor(userPreferencesContent: UserPreferencesContent): StoryData {
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
      task.add(renderMarkdown(storyResponse.text))
      task.verbose(JsonUtil.toJson(storyResponse.obj))

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