package com.simiacryptus.skyenet.apps.generated


import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.util.function.Function


open class IllustratedStorybookGeneratorSystemApp(
  applicationName: String = "Illustrated Storybook Generator System",
  temperature: Double = 0.1,
) : ApplicationServer(
  applicationName = applicationName,
  temperature = temperature,
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
  )
  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

  override fun newSession(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      IllustratedStorybookGeneratorSystemAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).illustratedStorybookGeneratorSystem(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(IllustratedStorybookGeneratorSystemApp::class.java)
  }

}


open class IllustratedStorybookGeneratorSystemAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val api: API,
  val ui: ApplicationInterface,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<IllustratedStorybookGeneratorSystemActors.ActorType>(IllustratedStorybookGeneratorSystemActors(
  model = model,
  temperature = temperature,
).actorMap, dataStorage, user, session) {

  @Suppress("UNCHECKED_CAST")
  private val narrativeActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.NARRATIVE_ACTOR) as ParsedActor<String> }
  private val characterizationActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.CHARACTERIZATION_ACTOR) as ParsedActor<String> }
  private val dialogueActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.DIALOGUE_ACTOR) as ParsedActor<String> }
  private val editingActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.EDITING_ACTOR) as ParsedActor<String> }
  private val accessibilityActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.ACCESSIBILITY_ACTOR) as ParsedActor<String> }
  private val illustrationActor by lazy { getActor(IllustratedStorybookGeneratorSystemActors.ActorType.ILLUSTRATION_ACTOR) as ImageActor }



  data class StoryComponents(
    val narrative: String,
    val characters: List<String>,
    val dialogues: List<String>
  )

  data class ProcessedUserPreferences(
    val genre: String,
    val theme: String,
    val length: Int,
    val characterDetails: String,
    val dialogueContext: String,
    val editContext: String,
    val accessibilityFeatures: List<String>
  )

  fun gptActorsLayer(appLogicOutput: ProcessedUserPreferences) {
    val task = ui.newTask()
    try {
      task.header("Processing with GPT Actors")

      // Generate the narrative based on user preferences
      task.add("Generating narrative...")
      val narrativeResponse = narrativeActor.answer(
        listOf(appLogicOutput.genre, appLogicOutput.theme, appLogicOutput.length.toString()),
        api = api
      )
      val narrative = narrativeResponse.obj
      task.add("Narrative generated: $narrative")

      // Develop characters for the story
      task.add("Creating characters...")
      val charactersResponse = characterizationActor.answer(
        listOf(appLogicOutput.characterDetails),
        api = api
      )
      val characters = charactersResponse.obj
      task.add("Characters created: $characters")

      // Generate dialogues for the story
      task.add("Crafting dialogues...")
      val dialoguesResponse = dialogueActor.answer(
        listOf(appLogicOutput.dialogueContext),
        api = api
      )
      val dialogues = dialoguesResponse.obj
      task.add("Dialogues crafted: $dialogues")

      // Edit the generated content
      task.add("Refining the story content...")
      val editedContentResponse = editingActor.answer(
        listOf(appLogicOutput.editContext),
        api = api
      )
      val editedContent = editedContentResponse.obj
      task.add("Content refined: $editedContent")

      // Adapt the content for accessibility
      task.add("Adapting content for accessibility...")
      val accessibilityAdaptationsResponse = accessibilityActor.answer(
        listOf(appLogicOutput.accessibilityFeatures.joinToString()),
        api = api
      )
      val accessibilityAdaptations = accessibilityAdaptationsResponse.obj
      task.add("Accessibility adaptations applied: $accessibilityAdaptations")

      // Generate illustrations for the story
      task.add("Creating illustrations...")
      val illustrationResponse = illustrationActor.answer(
        listOf(narrative),
        api = api
      )
      val illustration = illustrationResponse.getImage()
      task.image(illustration)

      task.complete("GPT Actors have successfully processed the storybook components.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  // Define a data class to hold user preferences
  data class UserPreferences(
    val genre: String,
    val theme: String,
    val length: Int,
    val characterDetails: String,
    val dialogueContext: String,
    val editContext: String,
    val accessibilityFeatures: List<String>
  )


  fun illustratedStorybookGeneratorSystem(text: String) {
    val task = ui.newTask()
    try {
      task.header("Welcome to the Illustrated Storybook Generator System")

      // Parse the user input to extract preferences (for simplicity, we'll assume the input is a JSON String)
      val userPreferences = JsonUtil.fromJson<UserPreferences>(text, UserPreferences::class.java)

      // Generate the narrative based on user preferences
      task.add("Generating the narrative based on your preferences...")
      val narrativeResponse = narrativeActor.answer(
        listOf("Create a ${userPreferences.genre}-themed story with a focus on ${userPreferences.theme}, approximately ${userPreferences.length} words."),
        api = api
      )
      val narrative = narrativeResponse.obj
      task.add("Narrative generated: $narrative")

      // Develop characters for the story
      task.add("Creating characters for your story...")
      val charactersResponse = characterizationActor.answer(
        listOf("Develop characters for a ${userPreferences.genre} story set in ${userPreferences.characterDetails}."),
        api = api
      )
      val characters = charactersResponse.obj
      task.add("Characters created: $characters")

      // Generate dialogues for the story
      task.add("Crafting dialogues for your characters...")
      val dialoguesResponse = dialogueActor.answer(
        listOf("Generate dialogues for the following scene in a ${userPreferences.genre} story where ${userPreferences.dialogueContext}."),
        api = api
      )
      val dialogues = dialoguesResponse.obj
      task.add("Dialogues crafted: $dialogues")

      // Edit the generated content
      task.add("Refining the story content for clarity and style...")
      val editedContentResponse = editingActor.answer(
        listOf("Edit the following text for grammar, style, and consistency with a ${userPreferences.genre} theme: ${userPreferences.editContext}."),
        api = api
      )
      val editedContent = editedContentResponse.obj
      task.add("Content refined: $editedContent")

      // Adapt the content for accessibility
      task.add("Adapting the content for accessibility based on your preferences...")
      val accessibilityAdaptationsResponse = accessibilityActor.answer(
        listOf("Adapt the following storybook content for ${userPreferences.accessibilityFeatures.joinToString()}, considering user specific needs."),
        api = api
      )
      val accessibilityAdaptations = accessibilityAdaptationsResponse.obj
      task.add("Accessibility adaptations applied: $accessibilityAdaptations")

      // Generate illustrations for the story
      task.add("Creating illustrations for your story...")
      val illustrationResponse = illustrationActor.answer(
        listOf("Create an illustration for a scene in a ${userPreferences.genre} story where ${narrative}."),
        api = api
      )
      val illustration = illustrationResponse.getImage()
      task.image(illustration)

      // Compile the storybook data
      val storybookData = StorybookData(
        title = "Generated Storybook",
        narrative = narrative,
        characters = listOf(characters),
        dialogues = listOf(dialogues),
        illustrations = listOf(illustration),
        accessibilityAdaptations = accessibilityAdaptations
      )

      // Save the storybook data to the data storage layer
      dataStorageLayer(storybookData)

      task.complete("Your illustrated storybook has been generated successfully!")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun illustrationGenerationLayer(gptActorsOutput: StoryComponents) {
    val task = ui.newTask()
    try {
      task.header("Generating Illustrations for the Storybook")

      // Generate illustrations for the narrative
      task.add("Creating illustrations for the narrative...")
      val narrativeIllustration = illustrationActor.answer(
        listOf(gptActorsOutput.narrative),
        api = api
      ).getImage()
      task.image(narrativeIllustration)

      // Optionally, generate illustrations for characters and dialogues if needed
      // For simplicity, we'll just generate one illustration for the narrative
      // Additional illustrations can be generated similarly by iterating over characters and dialogues

      task.complete("Illustrations generated successfully.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun applicationLogicLayer(uiOutput: UserPreferences) {
    val task = ui.newTask()
    try {
      task.header("Starting Illustrated Storybook Generation")

      // Step 1: Generate the narrative based on user preferences
      task.add("Generating the narrative based on your preferences...")
      val narrativeResponse = narrativeActor.answer(listOf(uiOutput.genre, uiOutput.theme, uiOutput.length.toString()), api = api)
      val narrative = narrativeResponse.obj
      task.add("Narrative generated: $narrative")

      // Step 2: Develop characters for the story
      task.add("Creating characters for your story...")
      val charactersResponse = characterizationActor.answer(listOf(uiOutput.characterDetails), api = api)
      val characters = charactersResponse.obj
      task.add("Characters created: $characters")

      // Step 3: Generate dialogues for the story
      task.add("Crafting dialogues for your characters...")
      val dialoguesResponse = dialogueActor.answer(listOf(uiOutput.dialogueContext), api = api)
      val dialogues = dialoguesResponse.obj
      task.add("Dialogues crafted: $dialogues")

      // Step 4: Edit the generated content
      task.add("Refining the story content for clarity and style...")
      val editedContentResponse = editingActor.answer(listOf(uiOutput.editContext), api = api)
      val editedContent = editedContentResponse.obj
      task.add("Content refined: $editedContent")

      // Step 5: Adapt the content for accessibility
      task.add("Adapting the content for accessibility based on your preferences...")
      val accessibilityAdaptationsResponse = accessibilityActor.answer(listOf(uiOutput.accessibilityFeatures.joinToString()), api = api)
      val accessibilityAdaptations = accessibilityAdaptationsResponse.obj
      task.add("Accessibility adaptations applied: $accessibilityAdaptations")

      // Step 6: Generate illustrations for the story
      task.add("Creating illustrations for your story...")
      val illustrationResponse = illustrationActor.answer(listOf(narrative), api = api)
      val illustration = illustrationResponse.getImage() // Corrected to use getImage()
      // Display the illustration to the user using the UI object
      task.image(illustration)

      // Step 7: Compile the storybook
      task.add("Compiling the illustrated storybook...")
      // Logic to compile the storybook goes here
      task.add("Storybook compiled successfully.")

      // Step 8: Display the storybook preview to the user
      task.add("Displaying the storybook preview...")
      // Logic to display the preview goes here
      task.add("Preview displayed.")

      // Step 9: Provide options for the user to download or share the storybook
      task.add("Providing options to download or share your storybook...")
      // Logic to provide download and sharing options goes here
      task.add("Options provided.")

      task.complete("Your illustrated storybook is ready!")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  // Placeholder data class for user input
  data class UserInput(
    val genre: String,
    val theme: String,
    val length: Int,
    val characterDetails: String,
    val dialogueContext: String,
    val editContext: String,
    val accessibilityFeatures: List<String>
  )

  fun uiLayer(userInput: UserInput) {
    val task = ui.newTask()
    try {
      task.header("Illustrated Storybook Generator")

      // Step 1: Generate the narrative based on user input
      task.add("Generating the narrative...")
      val narrativeResponse = narrativeActor.answer(listOf(userInput.genre, userInput.theme, userInput.length.toString()), api = api)
      val narrative = narrativeResponse.obj // Extract the narrative text
      task.add("Narrative generated successfully: $narrative")

      // Step 2: Develop characters for the story
      task.add("Creating characters...")
      val charactersResponse = characterizationActor.answer(listOf(userInput.characterDetails), api = api)
      val characters = charactersResponse.obj // Extract the characters text
      task.add("Characters created successfully: $characters")

      // Step 3: Generate dialogues for the story
      task.add("Crafting dialogues...")
      val dialoguesResponse = dialogueActor.answer(listOf(userInput.dialogueContext), api = api)
      val dialogues = dialoguesResponse.obj // Extract the dialogues text
      task.add("Dialogues crafted successfully: $dialogues")

      // Step 4: Edit the generated content
      task.add("Refining the story content...")
      val editedContentResponse = editingActor.answer(listOf(userInput.editContext), api = api)
      val editedContent = editedContentResponse.obj // Extract the edited content text
      task.add("Content refined successfully: $editedContent")

      // Step 5: Adapt the content for accessibility
      task.add("Adapting content for accessibility...")
      val accessibilityAdaptationsResponse = accessibilityActor.answer(listOf(userInput.accessibilityFeatures.joinToString()), api = api)
      val accessibilityAdaptations = accessibilityAdaptationsResponse.obj // Extract the accessibility adaptations
      task.add("Accessibility adaptations applied successfully: $accessibilityAdaptations")

      // Step 6: Generate illustrations for the story
      task.add("Creating illustrations...")
      illustrationActor.answer(listOf(narrative), api = api)
      task.add("Illustrations created successfully.")

      // Step 7: Compile the storybook
      task.add("Compiling the illustrated storybook...")
      // Logic to compile the storybook goes here
      task.add("Storybook compiled successfully.")

      // Step 8: Display the storybook preview to the user
      task.add("Displaying the storybook preview...")
      // Logic to display the preview goes here
      task.add("Preview displayed.")

      // Step 9: Provide options for the user to download or share the storybook
      task.add("Providing download and sharing options...")
      // Logic to provide download and sharing options goes here
      task.add("Options provided.")

      task.complete("Storybook generation completed successfully.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }


  data class StorybookData(
    val title: String,
    val narrative: String,
    val characters: List<String>,
    val dialogues: List<String>,
    val illustrations: List<java.awt.image.BufferedImage>,
    val accessibilityAdaptations: String
    // Add other fields as necessary
  )

  fun dataStorageLayer(finalStorybook: StorybookData) {
    val task = ui.newTask()
    try {
      task.header("Saving Storybook Data")

      // Serialize the final storybook data to JSON
      val storybookJson = JsonUtil.toJson(finalStorybook)
      task.add("Serialized storybook data to JSON.")

      // Define a filename for the storybook data
      val filename = "storybook_${finalStorybook.title.replace(" ", "_")}.json"

      // Save the JSON to the data storage
      val savedStorybook = dataStorage.setJson(user, session, filename, storybookJson)
      task.add("Saved storybook data to storage with filename: $filename")

      // Optionally, save illustrations to the data storage
      finalStorybook.illustrations.forEachIndexed { index, image ->
        // Logic to save each IllustratedStorybookGeneratorSystemActors.image goes here
        // For example, convert BufferedImage to a file format and save
      }

      task.complete("Storybook data saved successfully.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  data class ExternalContentData(
    val textForNarration: String,
    val socialMediaMessage: String
  )

//  fun externalServicesLayer(externalContent: ExternalContentData) {
//    val task = ui.newTask()
//    try {
//      task.header("Processing External Services")
//
//      // Text-to-Speech Conversion
//      task.add("Converting text to speech...")
//      val speechRequest = com.simiacryptus.jopenai.ApiModel.SpeechRequest(
//        input = externalContent.textForNarration,
//        model = "text-to-speech-model", // Replace with the actual model name
//        response_format = "audio/mpeg",
//        speed = 1.0, // Default speed
//        voice = "en-US-Wavenet-D" // Replace with the desired voice model
//      )
//      val speechAudio = api.createSpeech(speechRequest)
//
//      // Check if the audio data is not null and log the length
//      if (speechAudio != null) {
//        task.add("Text-to-speech conversion completed. Audio data length: ${speechAudio.size} bytes")
//      } else {
//        task.add("Text-to-speech conversion failed. No audio data received.")
//      }
//
//      // Social Media Sharing Preparation
//      task.add("Preparing content for social media sharing...")
//      // Logic to prepare content for sharing goes here
//      // This could involve creating a post with the provided message and any relevant media
//      task.add("Content prepared for social media sharing: ${externalContent.socialMediaMessage}")
//
//      task.complete("External services processed successfully.")
//    } catch (e: Throwable) {
//      task.error(e)
//      throw e
//    }
//  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(IllustratedStorybookGeneratorSystemAgent::class.java)

  }
}


class IllustratedStorybookGeneratorSystemActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  // Define a parser that simply returns the input text
  class NarrativeParser : Function<String, String> {
    override fun apply(text: String): String {
      return text // Simply return the input text
    }
  }

  // Instantiate the narrativeActor with the NarrativeParser
  val narrativeActor = ParsedActor<String>(
    parserClass = NarrativeParser::class.java,
    prompt = """
            You are a creative assistant capable of generating engaging narratives.
            When provided with a genre, theme, and length, you will produce a story outline.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Define the parser interface for the Characterization Actor
  interface CharacterizationParser : Function<String, String> {
    override fun apply(text: String): String {
      // In this simple case, we're just returning the input text.
      // This could be expanded to parse the text into a more complex data structure.
      return text
    }
  }

  // Instantiate the Characterization Actor
  val characterizationActor = ParsedActor<String>(
    parserClass = CharacterizationParser::class.java,
    prompt = """
            You are an AI that generates detailed character profiles for stories.
            Create a character with a unique name, personality, and role in the story.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Define a simple parser that just returns the input string
  class SimpleStringParser : Function<String, String> {
    override fun apply(text: String): String {
      // No actual parsing is done here, just return the input text
      return text
    }
  }

  // Instantiate the dialogueActor with the simple string parser
  val dialogueActor = ParsedActor<String>(
    parserClass = SimpleStringParser::class.java,
    prompt = """
            You are an AI creating dialogues for a story. Generate engaging and contextually relevant conversations between characters.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Define the parser interface that will be used to edit the text
  interface EditingParser : Function<String, String> {
    override fun apply(text: String): String {
      // Implement text editing logic here
      // For the sake of example, let's say it just returns the input text
      return text
    }
  }

  // Instantiate the editingActor using the ParsedActor constructor
  val editingActor = ParsedActor<String>(
    parserClass = EditingParser::class.java,
    prompt = """
            You are an assistant that improves text content.
            Make the text grammatically correct, improve its style, and ensure it is clear and concise.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Define the data class for the result of the accessibility adaptation
  data class AccessibilityResult(
    val originalContent: String,
    val adaptedContent: String,
    val adaptations: List<Adaptation>
  ) : ValidatedObject {
    data class Adaptation(
      val type: String,
      val description: String
    )

    override fun validate(): String? {
      if (adaptedContent.isBlank()) return "Adapted content cannot be blank"
      if (adaptations.isEmpty()) return "At least one adaptation must be provided"
      return null
    }
  }

  // Define the parser interface for the accessibility actor
  interface AccessibilityParser : Function<String, AccessibilityResult> {
    override fun apply(text: String): AccessibilityResult
  }

  // Instantiate the ParsedActor with the AccessibilityParser class and the appropriate prompt
  val accessibilityActor = ParsedActor<AccessibilityResult>(
    parserClass = AccessibilityParser::class.java,
    prompt = """
            You are an assistant that adapts storybook content for accessibility. 
            Given a piece of content, provide an adapted version that includes accessibility features such as larger fonts, audio descriptions, and high-contrast colors.
            """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  val illustrationActor: ImageActor = ImageActor(
    prompt = "Create a captivating and detailed illustration that visually narrates the following scene from a children's storybook:",
    name = "IllustrationActor",
    imageModel = ImageModels.DallE2,
    temperature = 0.7,
    width = 1024,
    height = 1024
  )

  enum class ActorType {
    NARRATIVE_ACTOR,
    CHARACTERIZATION_ACTOR,
    DIALOGUE_ACTOR,
    EDITING_ACTOR,
    ACCESSIBILITY_ACTOR,
    ILLUSTRATION_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
    ActorType.NARRATIVE_ACTOR to narrativeActor,
    ActorType.CHARACTERIZATION_ACTOR to characterizationActor,
    ActorType.DIALOGUE_ACTOR to dialogueActor,
    ActorType.EDITING_ACTOR to editingActor,
    ActorType.ACCESSIBILITY_ACTOR to accessibilityActor,
    ActorType.ILLUSTRATION_ACTOR to illustrationActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(IllustratedStorybookGeneratorSystemActors::class.java)
  }
}
