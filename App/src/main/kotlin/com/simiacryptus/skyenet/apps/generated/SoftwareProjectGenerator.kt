import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
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


open class SoftwareProjectGeneratorApp(
    applicationName: String = "SoftwareProjectGenerator v1.0",
    temperature: Double = 0.1,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/softwareProjectGenerator",
) {

    data class Settings(
        val model: OpenAITextModel = ChatModels.GPT4oMini,
        val temperature: Double = 0.1,
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
            (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
            SoftwareProjectGeneratorAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: ChatModels.GPT4oMini,
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
    model: OpenAITextModel = ChatModels.GPT4oMini,
    temperature: Double = 0.3,
) : ActorSystem<SoftwareProjectGeneratorActors.ActorType>(
    SoftwareProjectGeneratorActors(
        model = model,
        temperature = temperature,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
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


    companion object
}


class SoftwareProjectGeneratorActors(
    val model: OpenAITextModel = ChatModels.GPT4o,
    val temperature: Double = 0.3,
) {


    private val simpleActor = SimpleActor(
        prompt = """
            You are a software project generator. You will assist users in creating the scaffolding for their software projects by interpreting their requirements and generating the necessary code and project structure.
        """.trimIndent(),
        name = "SoftwareProjectGenerator",
        model = ChatModels.GPT4oMini,
        temperature = 0.3
    )


    // Instantiation function for the ParsedActor with String parser
    private val parsedActor = ParsedActor(
//    parserClass = IdentityParser::class.java,
        resultClass = String::class.java,
        prompt = "You are a sophisticated AI capable of understanding and generating text based on input.",
        model = ChatModels.GPT4oMini,
        temperature = 0.3,
        parsingModel = ChatModels.GPT4oMini
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
        val log = LoggerFactory.getLogger(SoftwareProjectGeneratorActors::class.java)
    }
}
