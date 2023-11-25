//import com.simiacryptus.jopenai.API
//import com.simiacryptus.jopenai.describe.Description
//import com.simiacryptus.jopenai.models.ChatModels
//import com.simiacryptus.jopenai.models.ImageModels
//import com.simiacryptus.jopenai.proxy.ValidatedObject
//import com.simiacryptus.skyenet.core.actors.ActorSystem
//import com.simiacryptus.skyenet.core.actors.BaseActor
//import com.simiacryptus.skyenet.core.actors.CodingActor
//import com.simiacryptus.skyenet.core.actors.CodingActor.CodeRequest
//import com.simiacryptus.skyenet.core.actors.ImageActor
//import com.simiacryptus.skyenet.core.actors.ParsedActor
//import com.simiacryptus.skyenet.core.actors.SimpleActor
//import com.simiacryptus.skyenet.core.platform.DataStorage
//import com.simiacryptus.skyenet.core.platform.Session
//import com.simiacryptus.skyenet.core.platform.User
//import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
//import com.simiacryptus.skyenet.webui.application.ApplicationInterface
//import com.simiacryptus.skyenet.webui.application.ApplicationServer
//import com.simiacryptus.skyenet.webui.session.*
//import java.awt.image.BufferedImage
//import java.util.function.Function
//import org.slf4j.LoggerFactory
//
//
//
//open class SoftwareProjectGeneratorApp(
//    applicationName: String = "SoftwareProjectGenerator",
//    temperature: Double = 0.1,
//) : ApplicationServer(
//    applicationName = applicationName,
//    temperature = temperature,
//) {
//
//    data class Settings(
//        val model: ChatModels = ChatModels.GPT35Turbo,
//        val temperature: Double = 0.1,
//    )
//    override val settingsClass: Class<*> get() = Settings::class.java
//    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T
//
//    override fun newSession(
//        session: Session,
//        user: User?,
//        userMessage: String,
//        ui: ApplicationInterface,
//        api: API
//    ) {
//        try {
//            val settings = getSettings<Settings>(session, user)
//            SoftwareProjectGeneratorAgent(
//                user = user,
//                session = session,
//                dataStorage = dataStorage,
//                api = api,
//                ui = ui,
//                model = settings?.model ?: ChatModels.GPT35Turbo,
//                temperature = settings?.temperature ?: 0.3,
//            ).softwareProjectGenerator(userMessage)
//        } catch (e: Throwable) {
//            log.warn("Error", e)
//        }
//    }
//
//    companion object {
//        private val log = LoggerFactory.getLogger(SoftwareProjectGeneratorApp::class.java)
//    }
//
//}
//
//
//open class SoftwareProjectGeneratorAgent(
//    user: User?,
//    session: Session,
//    dataStorage: DataStorage,
//    val ui: ApplicationInterface,
//    val api: API,
//    model: ChatModels = ChatModels.GPT35Turbo,
//    temperature: Double = 0.3,
//) : ActorSystem<SoftwareProjectGeneratorActors.ActorType>(SoftwareProjectGeneratorActors(
//    model = model,
//    temperature = temperature,
//).actorMap, dataStorage, user, session) {
//
//    @Suppress("UNCHECKED_CAST")
//    private val ideaInterpreterActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.IDEA_INTERPRETER_ACTOR) as ParsedActor }
//    private val projectStructureActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.PROJECT_STRUCTURE_ACTOR) as CodingActor }
//    private val codeSkeletonActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.CODE_SKELETON_ACTOR) as CodingActor }
//    private val documentationActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.DOCUMENTATION_ACTOR) as SimpleActor }
//    private val resourceAllocatorActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.RESOURCE_ALLOCATOR_ACTOR) as ParsedActor }
//    private val imageActor by lazy { getActor(SoftwareProjectGeneratorActors.ActorType.IMAGE_ACTOR) as ImageActor }
//
//    fun softwareProjectGenerator(userPrompt: String) {
//        val task = ui.newTask()
//        try {
//            task.header("Software Project Generator")
//
//            // Step 1: Interpret the Idea
//            task.add("Interpreting the project idea...")
//            val parsedRequirements = interpretIdea(userPrompt)
//            task.verbose("Interpreted Requirements: $parsedRequirements")
//
//            // Step 2: Generate Project Structure
//            task.add("Generating project structure...")
//            val projectStructureCodeResult = generateProjectStructure(parsedRequirements)
//            task.verbose("Project Structure Code: ${projectStructureCodeResult.getCode()}")
//
//            // Step 3: Generate Code Skeleton
//            task.add("Generating code skeleton...")
//            val codeSkeletonCodeResult = generateCodeSkeleton(parsedRequirements)
//            task.verbose("Code Skeleton: ${codeSkeletonCodeResult.getCode()}")
//
//            // Step 4: Create Documentation
//            task.add("Creating initial documentation...")
//            val documentation = createDocumentation(userPrompt, parsedRequirements)
//            task.verbose("Documentation: $documentation")
//
//            // Step 5: Allocate Resources
//            task.add("Allocating resources for the project...")
//            val resourcesConfiguration = allocateResources(parsedRequirements)
//            task.verbose("Resources Configuration: $resourcesConfiguration")
//
//            // Step 6: Generate Visual Assets
//            task.add("Generating visual assets for the project...")
//            val visualAssets = generateVisualAssets(userPrompt, parsedRequirements)
//            task.image(visualAssets)
//
//            task.complete("Software project generation completed successfully.")
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//    // Implement the helper functions here, ensuring that nullable types are handled correctly and that the correct properties are referenced.
//    // ...
//
//    // Note: The helper functions should be implemented as shown in the previous steps, with corrections for nullable types and proper referencing of properties.
//
//    fun interpretIdea(userPrompt: String): ParsedRequirements {
//        val task = ui.newTask()
//        try {
//            task.header("Interpreting Project Idea")
//            task.add("Analyzing the project idea to extract key requirements...")
//
//            // Use the Idea Interpreter Actor to parse the user's project idea
//            val response = ideaInterpreterActor.answer(listOf(userPrompt), api = api)
//            val parsedRequirements = response.getObj() ?: throw IllegalArgumentException("Failed to parse the project idea.")
//
//            task.add("Successfully interpreted the project idea.")
//            task.complete("Interpretation complete.")
//
//            return parsedRequirements
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//    // Assuming ParsedRequirements is a class that holds the structured requirements for the project
//    data class ParsedRequirements(
//        val projectName: String,
//        val programmingLanguage: String,
//        val projectType: String,
//        val features: List<String>
//    )
//
//    fun generateProjectStructure(parsedRequirements: ParsedRequirements): CodingActor.CodeResult {
//        val task = ui.newTask()
//        try {
//            task.header("Generating Project Structure")
//
//            // Convert the parsed requirements into a list of strings to be used as prompts for the actor
//            val prompts = listOf(
//                "Project Name: ${parsedRequirements.projectName}",
//                "Programming Language: ${parsedRequirements.programmingLanguage}",
//                "Project Type: ${parsedRequirements.projectType}",
//                "Features: ${parsedRequirements.features.joinToString(", ")}"
//            )
//
//            // Create a code request with the prompts
//            val codeRequest = CodingActor.CodeRequest(prompts)
//
//            // Use the projectStructureActor to generate the project structure
//            val codeResult = projectStructureActor.answer(codeRequest, api = api)
//
//            // Log the generated code
//            task.add("Generated project structure code:\n${codeResult.getCode()}")
//
//            // Return the result
//            task.complete("Project structure generation complete.")
//            return codeResult
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//
//    // Assuming ParsedRequirements is a data class that holds structured project requirements
//    data class ParsedRequirements(
//        val projectName: String,
//        val programmingLanguage: String,
//        val projectType: String,
//        val features: List<String>
//        // Add more fields as necessary
//    )
//
//    fun generateCodeSkeleton(parsedRequirements: ParsedRequirements): CodingActor.CodeResult {
//        val task = ui.newTask()
//        try {
//            task.header("Generating Code Skeleton")
//
//            // Convert the parsed requirements into a list of strings to be used as prompts for the CodingActor
//            val prompts = listOf(
//                "Project Name: ${parsedRequirements.projectName}",
//                "Programming Language: ${parsedRequirements.programmingLanguage}",
//                "Project Type: ${parsedRequirements.projectType}",
//                "Features: ${parsedRequirements.features.joinToString(", ")}"
//                // Add more prompts based on the fields of ParsedRequirements
//            )
//
//            // Create a CodeRequest with the prompts
//            val codeRequest = CodeRequest(prompts)
//
//            // Use the CodeSkeletonActor to generate the code skeleton
//            val codeResult = codeSkeletonActor.answer(codeRequest, api = api)
//
//            // Log the generated code
//            task.add("Generated code skeleton:")
//            task.verbose(codeResult.getCode())
//
//            // Return the result
//            task.complete("Code skeleton generation complete.")
//            return codeResult
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//    data class ParsedRequirements(
//        val projectName: String,
//        val projectType: String,
//        val targetPlatform: String,
//        val programmingLanguage: String,
//        val features: List<String>
//    )
//
//    fun createDocumentation(userPrompt: String, parsedRequirements: ParsedRequirements): String {
//        val task = ui.newTask()
//        try {
//            task.header("Creating Documentation")
//
//            // Combine the user prompt with the structured requirements to form the full prompt for the actor.
//            val fullPrompt = """
//    Project Name: ${parsedRequirements.projectName}
//    Project Type: ${parsedRequirements.projectType}
//    Target Platform: ${parsedRequirements.targetPlatform}
//    Programming Language: ${parsedRequirements.programmingLanguage}
//    Features: ${parsedRequirements.features.joinToString(separator = ", ")}
//
//    User Prompt: $userPrompt
//
//    Based on the above information, create initial documentation for the software project.
//            """.trimMargin()
//
//            // Use the documentation actor to generate the documentation.
//            val documentation = documentationActor.answer(listOf(fullPrompt), api = api)
//
//            // Display the generated documentation to the user.
//            task.add("Generated Documentation:\n$documentation")
//
//            task.complete("Documentation creation complete.")
//            return documentation
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//    // Assuming ParsedRequirements is a class with the following structure:
//    data class ParsedRequirements(
//        val projectName: String,
//        val projectType: String,
//        val programmingLanguage: String,
//        val features: List<String>
//    )
//
//    fun allocateResources(parsedRequirements: ParsedRequirements): Configuration {
//        val task = ui.newTask()
//        try {
//            task.header("Allocating Resources")
//            task.add("Generating configurations for databases, APIs, and external services based on the project requirements.")
//
//            // Convert the parsed requirements into a natural language description
//            val requirementsDescription = with(parsedRequirements) {
//                "The project '$projectName' is a $projectType written in $programmingLanguage and requires the following features: ${features.joinToString(", ")}."
//            }
//
//            // Use the resourceAllocatorActor to generate the configurations
//            val parsedResponse = resourceAllocatorActor.answer(listOf(requirementsDescription), api = api)
//            val configuration = parsedResponse.getObj() // Extract the Configuration object from the parsed response
//
//            // Log the generated configurations
//            task.add("Database Configuration: ${configuration.databaseConfig ?: "None"}")
//            task.add("API Configuration: ${configuration.apiConfig ?: "None"}")
//            task.add("Services Configuration: ${configuration.servicesConfig ?: "None"}")
//
//            task.complete("Resource allocation completed successfully.")
//            return configuration
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//
//    // Assuming ParsedRequirements is a defined class that contains structured project requirements
//    class ParsedRequirements(val projectName: String) {
//        // ... other properties
//    }
//
//    fun generateVisualAssets(userPrompt: String, parsedRequirements: ParsedRequirements): BufferedImage {
//        val task = ui.newTask()
//        try {
//            task.header("Generating Visual Assets")
//            task.add("Creating visual assets for the project: ${parsedRequirements.projectName}")
//
//            // Create an ImageActor instance with appropriate configuration
//            val imageActor = ImageActor(
//                prompt = "Create a logo image for the project named '${parsedRequirements.projectName}' with the following characteristics: $userPrompt",
//                name = "ImageGenerator",
//                textModel = ChatModels.GPT35Turbo,
//                imageModel = ImageModels.DallE2,
//                temperature = 0.3,
//                width = 1024,
//                height = 1024
//            )
//
//            // Generate the image using the ImageActor
//            val imageResponse = imageActor.answer(listOf(userPrompt), api = api)
//            val image = imageResponse.getImage()
//
//            task.add("Visual assets generated successfully.")
//            task.image(image)
//            task.complete("Visual assets generation complete.")
//
//            return image
//        } catch (e: Throwable) {
//            task.error(e)
//            throw e
//        }
//    }
//
//    companion object {
//        private val log = org.slf4j.LoggerFactory.getLogger(SoftwareProjectGeneratorAgent::class.java)
//
//    }
//}
//
//
//class SoftwareProjectGeneratorActors(
//    val model: ChatModels = ChatModels.GPT4Turbo,
//    val temperature: Double = 0.3,
//) {
//
//    enum class ActorType {
//        IDEA_INTERPRETER_ACTOR,
//        PROJECT_STRUCTURE_ACTOR,
//        CODE_SKELETON_ACTOR,
//        DOCUMENTATION_ACTOR,
//        RESOURCE_ALLOCATOR_ACTOR,
//        IMAGE_ACTOR,
//    }
//
//    val actorMap: Map<ActorType, BaseActor<out Any>> = mapOf(
//        ActorType.IDEA_INTERPRETER_ACTOR to ideaInterpreterActor,
//        ActorType.PROJECT_STRUCTURE_ACTOR to projectStructureActor,
//        ActorType.CODE_SKELETON_ACTOR to codeSkeletonActor,
//        ActorType.DOCUMENTATION_ACTOR to documentationActor,
//        ActorType.RESOURCE_ALLOCATOR_ACTOR to resourceAllocatorActor,
//        ActorType.IMAGE_ACTOR to imageActor,
//    )
//
//
//    data class ParsedRequirements(
//        @Description("The type of software to be developed.")
//        val softwareType: String? = null,
//        @Description("The target platform for the software.")
//        val targetPlatform: String? = null,
//        @Description("The programming language preferences.")
//        val programmingLanguage: String? = null,
//        @Description("Specific features or functionalities required.")
//        val features: List<String>? = null
//    ) : ValidatedObject {
//        override fun validate(): String? = when {
//            softwareType.isNullOrBlank() -> "software type is required"
//            targetPlatform.isNullOrBlank() -> "target platform is required"
//            programmingLanguage.isNullOrBlank() -> "programming language is required"
//            features.isNullOrEmpty() -> "features are required"
//            else -> null
//        }
//    }
//
//    interface IdeaInterpreter : Function<String, ParsedRequirements> {
//        @Description("Interpret the text into structured requirements for a software project.")
//        override fun apply(text: String): ParsedRequirements
//    }
//
//    val ideaInterpreterActor = ParsedActor<ParsedRequirements>(
//        parserClass = IdeaInterpreter::class.java,
//        model = ChatModels.GPT35Turbo,
//        prompt = """
//            You are an intelligent assistant that interprets software project ideas.
//            Your task is to analyze the description and extract key requirements such as software type, target platform, programming language, and features.
//            Provide a structured summary of these requirements.
//        """.trimIndent()
//    )
//
//
//    val projectStructureActor: CodingActor = CodingActor(
//        interpreterClass = KotlinInterpreter::class,
//        symbols = mapOf(
//            // Predefined symbols and functions can be added here if needed
//        ),
//        details = """
//            You are a coding actor responsible for generating the basic project structure for software projects.
//            Based on the interpreted requirements, you will create a directory and file structure, including placeholder files.
//            You will also generate basic configuration files and READMEs necessary for the project's initial setup.
//
//            Expected code structure:
//            * Project root directory
//            * Source directory (src)
//            * Main application file
//            * Configuration files (e.g., build.gradle for Gradle projects)
//            * Documentation files (e.g., README.md)
//
//            You will use Kotlin to define the structure and generate the necessary files.
//        """.trimIndent(),
//        temperature = 0.1 // Low temperature for deterministic output
//    )
//
//
//    val codeSkeletonActor: CodingActor = CodingActor(
//        interpreterClass = KotlinInterpreter::class,
//        symbols = mapOf(
//            // Add any necessary predefined symbols/functions here
//        ),
//        details = """
//            You are a coding actor responsible for generating a code skeleton for software projects.
//
//            Based on the structured requirements and features provided, you will:
//            - Identify the main components of the project (e.g., classes, functions, modules).
//            - Generate a code skeleton for each component with basic class definitions, function signatures, and module outlines.
//            - Ensure that the code skeleton adheres to the specified programming language and project conventions.
//            - Provide placeholder comments and TODOs to guide further development.
//
//            The code skeleton should be organized and ready for the user to start implementing the project's functionality.
//        """.trimIndent(),
//        // Additional configuration parameters can be set as needed
//    )
//
//
//    val documentationActor: SimpleActor = SimpleActor(
//        prompt = """
//            You are a documentation assistant. Your role is to create initial documentation for software projects. Use the project description and requirements to generate helpful documentation that can be expanded upon as the project develops. This includes project description, setup instructions, usage examples, and any other relevant information that would assist a developer in understanding and using the software.
//        """.trimIndent(),
//        model = ChatModels.GPT35Turbo,
//        temperature = 0.3
//    )
//
//
//    data class Configuration(
//        @Description("The configuration for the database")
//        val databaseConfig: String? = null,
//        @Description("The configuration for the API")
//        val apiConfig: String? = null,
//        @Description("The configuration for the external services")
//        val servicesConfig: String? = null
//    ) : ValidatedObject {
//        override fun validate(): String? = when {
//            databaseConfig.isNullOrBlank() -> "database configuration is required"
//            apiConfig.isNullOrBlank() -> "api configuration is required"
//            servicesConfig.isNullOrBlank() -> "services configuration is required"
//            else -> null
//        }
//    }
//
//    interface ConfigurationParser : Function<String, Configuration> {
//        @Description("Parse the text response into a Configuration data class.")
//        override fun apply(text: String): Configuration
//    }
//
//    val resourceAllocatorActor = ParsedActor<Configuration>(
//        parserClass = ConfigurationParser::class.java,
//        prompt = """
//            You are an assistant that generates configuration for software projects.
//            Given a project's requirements, you will provide the necessary configuration for databases, APIs, and external services.
//            Your responses should be structured and concise.
//        """.trimIndent(),
//        model = ChatModels.GPT35Turbo,
//        temperature = 0.3
//    )
//
//
//    val imageActor = ImageActor(
//        prompt = "Create an image based on the following user request:",
//        name = "ImageGenerator",
//        textModel = ChatModels.GPT35Turbo,
//        imageModel = ImageModels.DallE2,
//        temperature = 0.3,
//        width = 1024,
//        height = 1024
//    )
//
//    companion object {
//        val log = org.slf4j.LoggerFactory.getLogger(SoftwareProjectGeneratorActors::class.java)
//    }
//}
