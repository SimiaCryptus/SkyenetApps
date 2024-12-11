package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Role
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.skyenet.AgentPatterns
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookActors.ActorType.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.StandardOpenOption

open class IllustratedStorybookApp(
    applicationName: String = "Illustrated Storybook Generator v1.1",
    domainName: String
) : ApplicationServer(
    applicationName = applicationName,
    path = "/illustrated_storybook",
) {

    override val description: String
        @Language("HTML")
        get() = "<div>" + renderMarkdown(
            """
        Welcome to the Illustrated Storybook Generator, an app designed to help you create illustrated storybooks with ease.
        
        Enter a prompt, and the Illustrated Storybook Generator will generate a storybook for you, complete with images and text!
      """.trimIndent()
        ) + "</div>"

    data class Settings(
        val model: TextModel? = OpenAIModels.GPT4o,
        val temperature: Double? = 0.5,
        val imageModel: ImageModels? = ImageModels.DallE3,
        val voice: String? = "alloy",
        val voiceSpeed: Double? = 1.1,
        val budget: Double = 2.0,
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
            IllustratedStorybookAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                ui = ui,
                api = api,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
                temperature = settings?.temperature ?: 0.3,
                imageModel = settings?.imageModel ?: ImageModels.DallE2,
                voice = settings?.voice ?: "alloy",
                voiceSpeed = settings?.voiceSpeed ?: 1.0,
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
    model: TextModel = OpenAIModels.GPT4o,
    temperature: Double = 0.3,
    imageModel: ImageModels = ImageModels.DallE2,
    val voice: String = "alloy",
    val voiceSpeed: Double = 1.0,
) : ActorSystem<IllustratedStorybookActors.ActorType>(
    IllustratedStorybookActors(
        model = model,
        temperature = temperature,
        imageModel = imageModel,
        voice = voice,
        voiceSpeed = voiceSpeed,
        api2 = ApplicationServices.clientManager.getOpenAIClient(session,user),
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {
    private val tabbedDisplay = TabbedDisplay(ui.newTask())

    @Suppress("UNCHECKED_CAST")
    private val storyGeneratorActor by lazy { getActor(STORY_GENERATOR_ACTOR) as ParsedActor<IllustratedStorybookActors.StoryData> }
    private val illustrationGeneratorActor by lazy { getActor(ILLUSTRATION_GENERATOR_ACTOR) as ImageActor }

    @Suppress("UNCHECKED_CAST")
    private val requirementsActor by lazy { getActor(REQUIREMENTS_ACTOR) as ParsedActor<UserPreferencesContent> }
    private val narratorActor by lazy { getActor(NARRATOR) as TextToSpeechActor }

    private fun agentSystemArchitecture(userPreferencesContent: UserPreferencesContent) {
        val task = ui.newTask(root = false).apply { tabbedDisplay["Generation"] = placeholder }
        try {
            task.header("Starting Storybook Generation Process")

            // Step 1: Generate the story text using the Story Generator Actor
            task.add("Generating the story based on user preferences...")
            val storyData: IllustratedStorybookActors.StoryData = storyGeneratorActor(userPreferencesContent, task)
            task.add("Story generated successfully with title: '${storyData.title}'")

            // Step 2: Generate illustrations for each paragraph of the story
            task.add("Generating illustrations for the story...")
            val illustrationTabs = TabbedDisplay(task)
            val illustrations = (storyData.paragraphs?.withIndex()?.map { (index, paragraph) ->
                val task = ui.newTask(root = false)
                illustrationTabs[index.toString()] = task.placeholder
                pool.submit<Pair<String, BufferedImage>?> {
                    illustrationGeneratorActor(
                        paragraph,
                        userPreferencesContent,
                        task
                    )
                }
            }?.toTypedArray() ?: emptyArray()).map { it.get() }
            task.add("Illustrations generated successfully.")

            task.add("Generating narration for the story...")
            val narrations = (storyData.paragraphs?.withIndex()?.map { (idx, paragraph) ->
                if (paragraph.isBlank()) return@map null
                pool.submit<String> {
                    narratorActor.setOpenAI(
                        ApplicationServices.clientManager.getOpenAIClient(session,user)
                    ).answer(listOf(paragraph), api).mp3data?.let {
                        val fileLocation = task.saveFile("narration$idx.mp3", it)
                        task.add("""<audio preload="none" controls><source src='$fileLocation' type='audio/mpeg'></audio>""")
                        fileLocation
                    }
                }
            }?.toTypedArray() ?: emptyArray()).map { it?.get() }
            task.complete("Narration generated successfully.")

            // Step 3: Format the story and illustrations into an HTML document
            val outputTask = ui.newTask(root = false).apply { tabbedDisplay["Story Output"] = placeholder }
            outputTask.add("Formatting the storybook into HTML...")
            val htmlStorybook = htmlFormatter(storyData, illustrations, narrations, outputTask)
            val savedStorybookPath = fileManager(htmlStorybook, outputTask)
            outputTask.complete("<a href='$savedStorybookPath' target='_blank'>Storybook Ready!</a>")
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun inputHandler(userMessage: String) {
        val task = ui.newTask(root = false)
        tabbedDisplay["User Input"] = task.placeholder
        try {
            task.echo(userMessage)
            val toInput = { it: String -> listOf(it) }
            val parsedInput = Discussable<ParsedResponse<UserPreferencesContent>>(
                task = task,
                userMessage = { userMessage },
                heading = renderMarkdown(userMessage, ui = ui),
                initialResponse = { it: String -> requirementsActor.answer(toInput(it), api = api) },
                outputFn = { design: ParsedResponse<UserPreferencesContent> ->
                    //          renderMarkdown("${design.text}\n\n```json\n${JsonUtil.toJson(design.obj)/*.indent("  ")*/}\n```")
                    AgentPatterns.displayMapInTabs(
                        mapOf(
                            "Text" to renderMarkdown(design.text, ui = ui),
                            "JSON" to renderMarkdown(
                                "```json\n${JsonUtil.toJson(design.obj)/*.indent("  ")*/}\n```",
                                ui = ui
                            ),
                        )
                    )
                },
                ui = ui,
                reviseResponse = { userMessages: List<Pair<String, Role>> ->
                    requirementsActor.respond(
                        messages = (userMessages.map {
                            ApiModel.ChatMessage(
                                it.second,
                                it.first.toContentList()
                            )
                        }.toTypedArray<ApiModel.ChatMessage>()),
                        input = toInput(userMessage),
                        api = api
                    )
                },
            ).call().obj
            agentSystemArchitecture(parsedInput)
            task.complete("Generation complete!")
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    private fun htmlFormatter(
        storyText: IllustratedStorybookActors.StoryData,
        illustrations: List<Pair<String, BufferedImage>?>,
        narrations: List<String?>,
        task: SessionTask
    ): String {
        try {
            task.header("Formatting Storybook")
            val htmlContent = StringBuilder()

            //language=HTML
            htmlContent.append(
                """
        |<html>
        |<head><title>${storyText.title}</title></head>
        |<body>
        |<style>
        |    body {
        |        font-family: 'Arial', sans-serif;
        |    }
        |
        |    @media print {
        |        .story-page {
        |            page-break-after: always;
        |            width: 100vw;
        |            height: 80vh;
        |        }
        |        .story-title {
        |            page-break-after: always;
        |            width: 100vw;
        |            height: 90vh;
        |            vertical-align: center;
        |        }
        |        audio {
        |            display: none;
        |        }
        |        button {
        |            display: none;
        |        }
        |    }
        |
        |    .story-title {
        |        text-align: center;
        |        font-size: 2.5em;
        |        margin-top: 20px;
        |        height: 20vh;
        |    }
        |
        |    .story-paragraph {
        |        text-align: justify;
        |        font-size: 1.75em;
        |        margin: 15px;
        |        line-height: 1.5;
        |        font-family: cursive;
        |    }
        |
        |    .story-illustration {
        |        text-align: center;
        |        margin: 20px;
        |    }
        |
        |    .story-illustration img {
        |        max-width: 75%;
        |        height: auto;
        |    }
        |    
        |    body {
        |        font-family: 'Arial', sans-serif;
        |    }
        |
        |</style>
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
            |    <audio preload="none" controls><source src='${
                        narration.replace(
                            prefix,
                            ""
                        )
                    }' type='audio/mpeg'></audio>
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
            task.error(ui, e)
            throw e
        }
    }


    private fun fileManager(
        htmlStorybook: String,
        task: SessionTask
    ): String {
        try {
            task.header("Saving Storybook File")

            // Generate a unique file name for the storybook
            val fileName = "storybook_${Session.long64()}.html"
            val directoryPath = dataStorage.getSessionDir(user, session).toPath()
            val filePath = directoryPath.resolve(fileName)

            // Ensure the directory exists
            Files.createDirectories(directoryPath)

            // Write the HTML content to the file
            Files.writeString(filePath, htmlStorybook, StandardOpenOption.CREATE_NEW)

            task.verbose("Storybook saved successfully at: $filePath")

            // Return the path to the saved file as a string
            return "fileIndex/$session/$fileName"
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        } finally {
            task.complete("File management complete.")
        }
    }


    // Define the function for generating illustrations for a given story segment
    private fun illustrationGeneratorActor(
        segment: String,
        userPreferencesContent: UserPreferencesContent,
        task: SessionTask
    ): Pair<String, BufferedImage>? {
        try {
            //task.add(renderMarkdown(segment))

            // Construct the conversation thread with the story segment and user preferences
            val conversationThread = listOf(
                segment,
                "The illustration should reflect the genre: ${userPreferencesContent.genre}",
                "It should be appropriate for the target age group: ${userPreferencesContent.targetAgeGroup}",
                "Please include the following elements if possible: ${
                    userPreferencesContent.specificElements?.joinToString(
                        ", "
                    )
                }",
                "The illustration style should be: ${userPreferencesContent.illustrationStyle}"
            )

            // Generate the illustration using the illustrationGeneratorActor
            val illustrationResponse = illustrationGeneratorActor.setImageAPI(
                ApplicationServices.clientManager.getOpenAIClient(session,user)
            ).answer(conversationThread, api = api)

            // Log the AgentSystemArchitectureActors.image description
            task.add(renderMarkdown(illustrationResponse.text, ui = ui), additionalClasses = "illustration-caption")
            val imageHtml = task.image(illustrationResponse.image).toString()
            task.complete()

            // Return the generated AgentSystemArchitectureActors.image
            return imageHtml to illustrationResponse.image
        } catch (e: Throwable) {
            task.error(ui, e)
            return null
        }
    }

    // Define the structure for user preferences
    data class UserPreferencesContent(
        val genre: String? = null,
        val targetAgeGroup: String? = null,
        val specificElements: List<String>? = null,
        val writingStyle: String? = null,
        val characterDetails: List<CharacterDetail>? = null,
        val purpose: String? = null,
        val illustrationStyle: String? = null
    )

    data class CharacterDetail(
        val name: String? = null,
        val description: String? = null,
        val role: String? = null
    )

    // Implement the storyGeneratorActor function
    private fun storyGeneratorActor(
        userPreferencesContent: UserPreferencesContent,
        task: SessionTask,
    ): IllustratedStorybookActors.StoryData {
        try {
            task.header("Generating Story")

            // Construct the conversation thread with user preferences
            val conversationThread = listOf(
                "Genre: ${userPreferencesContent.genre}",
                "Target Age Group: ${userPreferencesContent.targetAgeGroup}",
                "Specific Elements: ${userPreferencesContent.specificElements?.joinToString(", ")}",
                "Writing Style: ${userPreferencesContent.writingStyle}",
                "Character Details: ${userPreferencesContent.characterDetails?.joinToString(", ")}",
                "Purpose/Point: ${userPreferencesContent.purpose}"
            )

            // Generate the story using the storyGeneratorActor
            val storyResponse = storyGeneratorActor.answer(conversationThread, api = api)

            // Log the natural language answer
            task.add(renderMarkdown(storyResponse.text, ui = ui))
            task.verbose(JsonUtil.toJson(storyResponse.obj))

            // Return the parsed story data
            return storyResponse.obj
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        } finally {
            task.complete("Story generation complete.")
        }
    }

    companion object
}

class IllustratedStorybookActors(
    val model: TextModel = OpenAIModels.GPT4o,
    val temperature: Double = 0.3,
    val imageModel: ImageModels = ImageModels.DallE2,
    voice: String = "alloy",
    voiceSpeed: Double = 1.0,
    api2: OpenAIClient,
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

    private val requirementsActor = ParsedActor(
        resultClass = IllustratedStorybookAgent.UserPreferencesContent::class.java,
        model = OpenAIModels.GPT4oMini,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            |You are helping gather requirements for a storybook.
            |Respond to the user by suggesting a genre, target age group, specific elements to include in the story,
            |writing style, character details, purpose/point of the writing, and the illustration style.
         """.trimMargin()
    )

    private val storyGeneratorActor = ParsedActor(
        resultClass = StoryData::class.java,
        model = OpenAIModels.GPT4o,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            |You are an AI creating a story for a digital storybook. Generate a story that includes a title, storyline, dialogue, and descriptions.
            |The story should be engaging and suitable for the specified target age group and genre.
            |The story should also reflect the specified writing style, include the provided character details, and align with the purpose/point of the writing.
        """.trimMargin()
    )


    private val illustrationGeneratorActor = ImageActor(
        prompt = "In less than 200 words, briefly describe an illustration to be created for a story with the given details",
        name = "IllustrationGenerator",
        imageModel = imageModel, // Assuming DallE2 is suitable for generating storybook illustrations
        temperature = 0.5, // Adjust temperature for creativity vs. coherence
        width = 1024, // Width of the generated image
        height = 1024, // Height of the generated image
        textModel = OpenAIModels.GPT4oMini
    ).apply {
        setImageAPI(api2)
    }

    private val narrator = TextToSpeechActor(voice = voice, speed = voiceSpeed, models = OpenAIModels.GPT4oMini)

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
        val log: Logger = LoggerFactory.getLogger(IllustratedStorybookActors::class.java)
    }
}