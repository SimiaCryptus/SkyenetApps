
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import java.util.function.Function


open class SoftwareProjectGeneratorApp(
  applicationName: String = "SoftwareProjectGenerator",
  temperature: Double = 0.1,
) : ApplicationServer(
  applicationName = applicationName,
  path = "/softwareProjectGenerator",
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
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
      SoftwareProjectGeneratorAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).softwareProjectGenerator(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(SoftwareProjectGeneratorApp::class.java)
  }

}


open class SoftwareProjectGeneratorAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<SoftwareProjectGeneratorActors.ActorType>(
  SoftwareProjectGeneratorActors(
    model = model,
    temperature = temperature,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val simpleActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.SIMPLE_ACTOR) as SimpleActor }
  private val parsedActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.PARSED_ACTOR) as ParsedActor<String> }

  fun softwareProjectGenerator(userPrompt: String) {
    val task = ui.newTask()
    try {
      task.header("Software Project Generator")

      // Step 1: Project Structure Analysis
      task.add("Analyzing project requirements based on your description.")
      val projectStructure = parsedActor.answer(listOf(userPrompt), api = api)
      task.add("Project structure analysis complete: ${projectStructure.obj}")

      // Step 2: Code Generation for Project Scaffolding
      task.add("Generating project scaffolding based on the analyzed structure.")
      val scaffoldingCode =
        simpleActor.answer(listOf("Generate project scaffolding for: ${projectStructure.obj}"), api = api)
      task.add("Project scaffolding generated:\n$scaffoldingCode")

      // Step 3: Feature Development
      task.add("Developing features based on the project requirements.")
      // Assuming projectStructure contains a list of features, iterate and generate code for each
      val features = (projectStructure.obj as? Map<String, Any>)?.get("features") as? List<String>
        ?: throw IllegalArgumentException("Invalid project structure: cannot find features list")
      features.forEach { feature ->
        val featureCode = simpleActor.answer(listOf("Generate code for feature: $feature"), api = api)
        task.add("Generated code for feature '$feature':\n$featureCode")
      }

      // Step 4: Interactive Refinement (Optional)
      // This step would involve user interaction to refine the project, which is not implemented here due to the static nature of this example.

      // Step 5: Finalization
      task.add("Finalizing the project with build scripts and documentation.")
      val finalizationCode = simpleActor.answer(
        listOf("Finalize the project based on the following structure: ${projectStructure.obj}"),
        api = api
      )
      task.add("Project finalization details:\n$finalizationCode")

      task.complete("Software project generation is complete.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }


  // Assuming `parsedActor` is already defined and instantiated as a ParsedActor<String>
  // and `ui` is an instance of ApplicationInterface

  fun projectStructureAnalysis() {
    val task = ui.newTask()
    try {
      task.header("Project Structure Analysis")
      task.add("Please describe the software project you would like to generate.")

      // Capture user input for the project description
      val projectDescription = ui.textInput { userInput ->
        task.add("Analyzing project requirements for: $userInput")

        // Use the parsedActor to analyze the project structure based on user input
        val projectStructure = parsedActor.answer(listOf(userInput), api = api)
        task.add("Project structure analysis complete.")

        // Log the structured project requirements (for demonstration purposes)
        task.verbose("Structured Project Requirements: ${projectStructure.obj}")

        // Continue with the next steps of the project generation process
        // ...
      }

      task.complete("Project structure analysis initiated. Please enter your project description.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  fun codeGeneration(projectStructure: Any): String {
    val task = ui.newTask()
    try {
      task.header("Generating Project Code")

      // Convert project structure to JSON SoftwareProjectGeneratorActors.SoftwareProjectGeneratorActors.string for the actor
      val projectStructureJson = com.simiacryptus.jopenai.util.JsonUtil.toJson(projectStructure)
      task.add("Received project structure: $projectStructureJson")

      // Generate code using the simple actor
      val codeGenerationPrompt =
        "Based on the following project structure, generate the necessary code and configuration files:\n$projectStructureJson"
      val generatedCode = simpleActor.answer(listOf(codeGenerationPrompt), api = api)

      // Display generated code to the user
      task.add("Generated code:\n$generatedCode")

      task.complete("Code generation complete.")
      return generatedCode
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  fun featureDevelopment(projectStructure: Any) {
    val task = ui.newTask()
    try {
      task.header("Feature Development")

      // Assuming projectStructure is a complex object that contains a list of features to be developed.
      // We will need to cast it to the appropriate type that we expect (e.g., ProjectStructure).
      // For the sake of this example, let's assume it's a Map with a key "features" that contains a List of Strings.
      val features = (projectStructure as? Map<String, Any>)?.get("features") as? List<String>
        ?: throw IllegalArgumentException("Invalid project structure: cannot find features list")

      // Iterate over each feature and use the simpleActor to generate code for it
      features.forEach { feature ->
        task.add("Developing feature: $feature")
        val featureCode = simpleActor.answer(listOf("Generate code for feature: $feature"), api = api)
        task.add("Generated code for feature '$feature':\n$featureCode")
      }

      task.complete("All features have been developed.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }


  fun interactiveRefinement(projectStructure: Any) {
    val task = ui.newTask()
    try {
      task.header("Interactive Refinement")
      task.add("Please provide details to refine your project, such as adding features or changing configurations.")

      // Interactive refinement loop
      var continueRefinement = true
      while (continueRefinement) {
        // Get user input for refinement
        task.add(ui.textInput(Consumer { userInput ->
          // Process the user input using the parsedActor
          val refinementResponse = parsedActor.answer(listOf(userInput), api = api)
          task.add("Refinement processed: ${refinementResponse.text}")

          // Update the project structure with the new refinement
          // Assuming projectStructure can be updated with the response from parsedActor
          // This is a placeholder for the actual logic to update the project structure
          // projectStructure.updateWith(refinementResponse.getObj())

          // Display updated project structure
          task.add("Updated project structure: $projectStructure")

          // Ask the user if they want to continue refining the project
          task.add(ui.hrefLink("Click here to add more refinements") { continueRefinement = true })
          task.add(ui.hrefLink("Click here if you are done refining") { continueRefinement = false })
        }))

        // Break the loop if the user indicates they are done refining
        if (!continueRefinement) {
          break
        }
      }

      task.complete("Project refinement complete.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  fun finalization(projectStructure: Any) {
    val task = ui.newTask()
    try {
      task.header("Finalizing Project")

      // Serialize the project structure to JSON for the actor to understand
      val projectStructureJson = com.simiacryptus.jopenai.util.JsonUtil.toJson(projectStructure)

      // Generate the finalization code using the simple actor
      val finalizationPrompt = "Given the project structure: $projectStructureJson\n" +
          "Generate the necessary build scripts, deployment configurations, and documentation."
      val finalizationResponse = simpleActor.answer(listOf(finalizationPrompt), api = api)
      val finalizationCode = finalizationResponse

      // Display the generated finalization details to the user
      task.add("Generated finalization details:")
      task.add(finalizationCode)

      task.complete("Project finalization is complete.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(SoftwareProjectGeneratorAgent::class.java)

  }
}


class SoftwareProjectGeneratorActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  val simpleActor = SimpleActor(
    prompt = """
            You are a software project generator. You will assist users in creating the scaffolding for their software projects by interpreting their requirements and generating the necessary code and project structure.
        """.trimIndent(),
    name = "SoftwareProjectGenerator",
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  // Identity parser that returns the input string
  class IdentityParser : Function<String, String> {
    override fun apply(text: String): String = text
  }

  // Instantiation function for the ParsedActor with String parser
  val parsedActor = ParsedActor<String>(
    parserClass = IdentityParser::class.java,
    prompt = "You are a sophisticated AI capable of understanding and generating text based on input.",
    model = ChatModels.GPT35Turbo,
    temperature = 0.3,
    parsingModel = ChatModels.GPT35Turbo
  )

  enum class ActorType {
    SIMPLE_ACTOR,
    PARSED_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    ActorType.SIMPLE_ACTOR to simpleActor,
    ActorType.PARSED_ACTOR to parsedActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(SoftwareProjectGeneratorActors::class.java)
  }
}
