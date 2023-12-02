package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.util.function.Function


open class AIbasedIllustratedStorybookGeneratorApp(
applicationName: String = "AI-based Illustrated Storybook Generator",
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
      AIbasedIllustratedStorybookGeneratorAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).aiBasedIllustratedStorybookGenerator(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(AIbasedIllustratedStorybookGeneratorApp::class.java)
  }

}


open class AIbasedIllustratedStorybookGeneratorAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<AIbasedIllustratedStorybookGeneratorActors.ActorType>(AIbasedIllustratedStorybookGeneratorActors(
model = model,
temperature = temperature,
).actorMap, dataStorage, user, session) {

  @Suppress("UNCHECKED_CAST")
  private val plotGeneratorActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.PLOT_GENERATOR_ACTOR) as ParsedActor<String> }
  private val characterCreatorActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.CHARACTER_CREATOR_ACTOR) as ParsedActor<String> }
  private val dialogueGeneratorActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.DIALOGUE_GENERATOR_ACTOR) as ParsedActor<String> }
  private val illustrationCoordinatorActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.ILLUSTRATION_COORDINATOR_ACTOR) as ImageActor }
  private val styleCustomizerActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.STYLE_CUSTOMIZER_ACTOR) as ImageActor }
  private val bookLayoutActor by lazy { getActor(AIbasedIllustratedStorybookGeneratorActors.ActorType.BOOK_LAYOUT_ACTOR) as CodingActor }

  fun aiBasedIllustratedStorybookGenerator(user_preferences: String) {
    val task = ui.newTask()
    try {
      task.header("AI-Based Illustrated Storybook Generator")

      // Step 1: Generate the plot based on user preferences
      task.add("Generating the story plot...")
      val plotResponse = plotGeneratorActor.answer(listOf(user_preferences), api = api)
      val plotText = plotResponse.text
      task.add("Plot generated successfully: $plotText")

      // Step 2: Create characters for the story
      task.add("Creating characters for the story...")
      val characterResponse = characterCreatorActor.answer(listOf(user_preferences), api = api)
      val characterText = characterResponse.text
      task.add("Characters created successfully: $characterText")

      // Step 3: Generate dialogues for the characters
      task.add("Generating dialogues for the characters...")
      val dialogueResponse = dialogueGeneratorActor.answer(listOf(plotText, characterText), api = api)
      val dialogueText = dialogueResponse.text
      task.add("Dialogues generated successfully: $dialogueText")

      // Step 4: Coordinate illustrations for the story
      task.add("Coordinating illustrations for the story...")
      val illustrationResponse = illustrationCoordinatorActor.answer(listOf(plotText, characterText), api = api)
      val illustration = illustrationResponse.image
      task.add("Illustrations coordinated successfully.")
      task.image(illustration) // Display the illustration to the user

      // Step 5: Customize the style of the illustrations
      task.add("Customizing the style of the illustrations...")
      val styleCustomizationResponse = styleCustomizerActor.answer(listOf(user_preferences), api = api)
      val styledIllustration = styleCustomizationResponse.image
      task.add("Illustrations customized successfully.")
      task.image(styledIllustration) // Display the styled illustration to the user

      // Step 6: Design the layout of the storybook
      task.add("Designing the layout of the storybook...")
      val layoutCodeResult = bookLayoutActor.answer(
        CodingActor.CodeRequest(listOf(plotText, dialogueText, styledIllustration.toString())), api = api
      )
      val layoutCode = layoutCodeResult.code
      task.add("Book layout code generated successfully: $layoutCode")

      // Execute the generated code to produce the book layout
      val executionResult = layoutCodeResult.result
      val bookLayoutOutput = executionResult.resultOutput
      task.add("Book layout designed successfully. Here is the layout output:")
      task.add(bookLayoutOutput) // Display the book layout output to the user

      task.complete("Storybook generation complete. Enjoy your personalized illustrated storybook!")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun characterCreatorActor(CCA: ParsedActor<String>) {
    val task = ui.newTask()
    try {
      task.header("Character Creator")

      // Ask the user for input regarding the story preferences for character creation
      task.add(ui.textInput { preferences ->
        task.add("Creating characters based on the following preferences: $preferences")

        // Call the CCA actor to generate character profiles
        val characterResponse = CCA.answer(listOf(preferences), api = api)
        val characterText = characterResponse.text // Extract the text part of the response

        task.add("Characters created successfully. Here are the profiles:")

        // Display the generated character profiles to the user
        task.add(characterText)

        // Optionally, you can parse the characterResponse into a structured format if needed
        // For this example, we are assuming characterResponse is a string containing the character profiles
      })

      task.complete("Character creation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun plotGeneratorActor(PGA: ParsedActor<String>) {
    val task = ui.newTask()
    try {
      task.header("Plot Generator")

      // Ask the user for input regarding the story preferences
      task.add(ui.textInput { preferences ->
        task.add("Generating plot based on the following preferences: $preferences")

        // Call the PGA actor to generate the plot
        val plotResponse = PGA.answer(listOf(preferences), api = api)
        val plotText = plotResponse.text // Extract the text part of the response

        task.add("Plot generated successfully. Here is the outline:")

        // Display the generated plot to the user
        task.add(plotText)

        // Optionally, you can parse the plotResponse into a structured format if needed
        // For this example, we are assuming plotResponse is a string containing the plot outline
      })

      task.complete("Plot generation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun illustrationCoordinatorActor(ICA: ImageActor, plot_with_dialogue: String, user_preferences: String) {
    val task = ui.newTask()
    try {
      task.header("Illustration Coordinator")

      // Combine the plot and dialogue with user preferences to form the illustration prompt
      val illustrationPrompt = "Generate an illustration for a scene with the following details: $plot_with_dialogue " +
          "and according to user preferences: $user_preferences"

      // Call the ICA actor to generate the illustration
      val illustrationResponse = ICA.answer(listOf(illustrationPrompt), api = api)
      val illustration = illustrationResponse.image // Extract the image part of the response

      task.add("Illustration generated successfully. Here is the image:")

      // Display the generated illustration to the user
      task.image(illustration)

      task.complete("Illustration coordination complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun styleCustomizerActor(SCA: ImageActor, illustrations: List<String>, user_preferences: String) {
    val task = ui.newTask()
    try {
      task.header("Style Customizer")

      // Iterate over each illustration URL or description
      for (illustration in illustrations) {
        // Construct the prompt to customize the illustration based on user preferences
        val styleCustomizationPrompt = "Customize the following illustration to match the style and color palette preferences: $user_preferences"

        // Call the SCA actor to customize the illustration
        val styleCustomizationResponse = SCA.answer(listOf(styleCustomizationPrompt), api = api)
        val customizedIllustration = styleCustomizationResponse.image // Extract the image part of the response

        task.add("Illustration customized successfully. Here is the styled image:")

        // Display the customized illustration to the user
        task.image(customizedIllustration)
      }

      task.complete("Style customization complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun bookLayoutActor(BLA: CodingActor, plot_with_dialogue: String, styled_illustrations: List<String>) {
    val task = ui.newTask()
    try {
      task.header("Book Layout Design")

      // Construct a code request to design the book layout with the given plot and illustrations
      val codeRequest = CodingActor.CodeRequest(
        listOf(
          "Arrange the provided text and illustrations into a book layout.",
          "Text content: $plot_with_dialogue",
          "Illustrations: $styled_illustrations"
        )
      )

      // Call the BLA actor to generate the book layout code
      val layoutCodeResult = BLA.answer(codeRequest, api = api)
      val layoutCode = layoutCodeResult.code // Extract the generated code

      task.add("Book layout code generated successfully. Here is the code snippet:")

      // Display the generated code snippet to the user
      task.add(layoutCode)

      // Execute the generated code to produce the book layout
      val executionResult = layoutCodeResult.result
      val bookLayoutOutput = executionResult.resultOutput
      // The resultValue is not directly displayable, it would be the result of the code execution, e.g., a data structure

      task.add("Book layout designed successfully. Here is the layout output:")

      // Display the book layout output to the user
      task.add(bookLayoutOutput)

      task.complete("Book layout design complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  fun dialogueGeneratorActor(DGA: ParsedActor<String>, plot: String, characters: List<String>) {
    val task = ui.newTask()
    try {
      task.header("Dialogue Generator")

      // Construct a conversation thread that includes the plot and characters
      val conversationThread = listOf("Plot: $plot") + characters.map { "Character: $it" }

      // Call the DGA actor to generate dialogues
      val dialogueResponse = DGA.answer(conversationThread, api = api)
      val dialogueText = dialogueResponse.text // Extract the text part of the response

      task.add("Dialogues generated successfully. Here are the dialogues:")

      // Display the generated dialogues to the user
      task.add(dialogueText)

      // Optionally, you can parse the dialogueResponse into a structured format if needed
      // For this example, we are assuming dialogueResponse is a string containing the dialogues
      task.complete("Dialogue generation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AIbasedIllustratedStorybookGeneratorAgent::class.java)

  }
}


class AIbasedIllustratedStorybookGeneratorActors(
val model: ChatModels = ChatModels.GPT4Turbo,
val temperature: Double = 0.3,
) {


  // Define a parser that simply returns the string response.
  class StringParser : Function<String, String> {
    override fun apply(text: String): String {
      return text
    }
  }

  // Instantiate the plotGeneratorActor using the ParsedActor constructor.
  val plotGeneratorActor = ParsedActor<String>(
    parserClass = StringParser::class.java,
    prompt = """
            You are an AI capable of generating creative and engaging story plots. 
            When given a set of parameters such as genre, themes, and length, 
            you will produce a structured plot outline with a title, synopsis, 
            conflict, resolution, and key plot points.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Define the parser interface
  interface CharacterParser : Function<String, String> {
    override fun apply(text: String): String {
      // Here you would implement the logic to parse the text into a structured format.
      // For the sake of this example, let's assume the text is already in the desired JSON format.
      return text
    }
  }

  // Define the function to instantiate the ParsedActor
  fun createCharacterCreatorActor(): ParsedActor<String> {
    return ParsedActor(
      parserClass = CharacterParser::class.java,
      prompt = """
                You are an AI creating character profiles for a story. 
                Generate detailed descriptions including names, personalities, roles, and physical appearances.
            """.trimIndent(),
      model = ChatModels.GPT35Turbo,
      temperature = 0.3
    )
  }

  // Instantiate the characterCreatorActor
  val characterCreatorActor: ParsedActor<String> = createCharacterCreatorActor()


  // Define the parser interface for dialogue generation
  interface DialogueParser : Function<String, String> {
    override fun apply(text: String): String {
      // Here you would implement the logic to parse the GPT response
      // For simplicity, we are returning the text as-is
      return text
    }
  }

  // Instantiate the dialogueGeneratorActor using the ParsedActor constructor
  val dialogueGeneratorActor = ParsedActor<String>(
    parserClass = DialogueParser::class.java,
    prompt = """
            You are an AI creating dialogues for characters in a story.
            Generate engaging and character-specific dialogues based on the provided plot and character profiles.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  val illustrationCoordinatorActor = ImageActor(
    prompt = "Generate an illustration that visually represents the following scene description:",
    name = "IllustrationCoordinator",
    imageModel = ImageModels.DallE2, // Assuming Dall-E 2 is the desired model for image generation
    temperature = 0.3, // Adjust temperature to control the randomness of the image generation
    width = 1024, // Set the width of the generated image
    height = 1024 // Set the height of the generated image
  )


  val styleCustomizerActor = ImageActor(
    prompt = """
            You are an AI that applies style and color preferences to illustrations.
            Customize the given illustration to match the user's desired aesthetic, style, and color palette.
        """.trimIndent(),
    imageModel = ImageModels.DallE2, // Assuming DallE2 can be used for style customization
    temperature = 0.3, // A lower temperature for more precise control over the style
    width = 1024, // Width of the output image
    height = 1024 // Height of the output image
  )


  val bookLayoutActor: CodingActor = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf(
      // Add predefined symbols/functions that the actor might need for layout generation
      // For example, "addTextBlock", "addImage", "applyStyle", etc.
    ),
    details = """
            You are a book layout generator.
    
            Your role is to design the layout of a digital storybook. You will arrange the generated text and illustrations into a coherent book format, allowing for user customization and interaction.
    
            You have access to a layout engine or library capable of arranging text and images on a page. Your environment includes functions for adding text blocks, images, and styling elements.
    
            Expected code structure:
            * An 'execute' method that takes the story content and illustration data as input and returns the layout.
            * Functions to add text blocks, images, and apply styles to the layout.
            * The ability to output the final layout in a format suitable for web display or print (e.g., HTML, PDF).
        """.trimIndent(),
    name = "BookLayoutActor"
  )

  enum class ActorType {
    PLOT_GENERATOR_ACTOR,
    CHARACTER_CREATOR_ACTOR,
    DIALOGUE_GENERATOR_ACTOR,
    ILLUSTRATION_COORDINATOR_ACTOR,
    STYLE_CUSTOMIZER_ACTOR,
    BOOK_LAYOUT_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
    ActorType.PLOT_GENERATOR_ACTOR to plotGeneratorActor,
    ActorType.CHARACTER_CREATOR_ACTOR to characterCreatorActor,
    ActorType.DIALOGUE_GENERATOR_ACTOR to dialogueGeneratorActor,
    ActorType.ILLUSTRATION_COORDINATOR_ACTOR to illustrationCoordinatorActor,
    ActorType.STYLE_CUSTOMIZER_ACTOR to styleCustomizerActor,
    ActorType.BOOK_LAYOUT_ACTOR to bookLayoutActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(AIbasedIllustratedStorybookGeneratorActors::class.java)
  }
}
