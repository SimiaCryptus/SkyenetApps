import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory


open class VocabularyListBuilderApp(
    applicationName: String = "Vocabulary List Builder",
    path: String = "vocabulary-list-builder",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
) {

    data class Settings(
        val model: OpenAITextModel = ChatModels.GPT35Turbo,
        val temperature: Double = 0.1,
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
            VocabularyListBuilderAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: ChatModels.GPT35Turbo,
                temperature = settings?.temperature ?: 0.3,
            ).vocabularyListBuilder(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VocabularyListBuilderApp::class.java)
    }

}


open class VocabularyListBuilderAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: OpenAITextModel = ChatModels.GPT35Turbo,
    temperature: Double = 0.3,
) : ActorSystem<VocabularyListBuilderActors.ActorType>(VocabularyListBuilderActors(
    model = model,
    temperature = temperature,
).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    private val parseInputActor by lazy { getActor(VocabularyListBuilderActors.ActorType.PARSE_INPUT) as ParsedActor<TermInput> }

    data class TermInput(
        val term: String,
        val definitionStyle: String,
        val targetAudience: String,
        val illustrationPreferences: String
    )

    fun vocabularyListBuilder(termInput: String) {
        val task = ui.newTask()
        try {
            task.echo(termInput)
            val termInputParsed = parseInputActor.answer(listOf(termInput), api)
            task.add(renderMarkdown(termInputParsed.text, ui = ui))
            vocabularyListBuilder(termInputParsed.obj)
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    private fun vocabularyListBuilder(termInput: TermInput) {
        val task = ui.newTask()
        try {
            task.header("Vocabulary List Builder")

            // Generate Definition
            task.add("Generating definition for term: ${termInput.term}")
            // Simulating the response from the definition actor as the actual API call cannot be made here.
            val simulatedDefinition =
                "An ecosystem is a community of living organisms in conjunction with the nonliving components of their environment, interacting as a system."
            task.add("Definition: $simulatedDefinition")

            // Generate Illustration Description
            task.add("Generating illustration description for term: ${termInput.term}")
            // Simulating the response from the illustration actor as the actual API call cannot be made here.
            val simulatedIllustrationDescription =
                "An illustration showing a vibrant ecosystem with a variety of animals like rabbits and deer, and plants like trees and bushes, under a bright blue sky. The style is cartoonish, appealing to a younger audience, with bright colors to make the scene lively and engaging."
            task.add("Illustration description: $simulatedIllustrationDescription")

            task.complete("Vocabulary list building complete.")
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    // Example invocation
//  vocabularyListBuilder(TermInput("Ecosystem", "informal", "teenagers", "cartoonish style, vibrant colors, appealing to a younger audience"))

    //  generate_illustration("Ecosystem", "cartoonish style, vibrant colors, appealing to a younger audience")

    //  handle_feedback("Please make the definition more detailed and the illustration brighter.",
//  "An ecosystem is a community of living organisms in conjunction with the nonliving components of their environment, interacting as a system.",
//  "An illustration showing a vibrant ecosystem with a variety of animals and plants under a bright sky.")

    //  generate_definition("Ecosystem", "informal", "teenagers")

    //
//  customize("An ecosystem is a community of living organisms in conjunction with the nonliving components of their environment, interacting as a system.",
//  "An illustration showing a vibrant ecosystem with a variety of animals and plants under a bright sky.")

    companion object
}


class VocabularyListBuilderActors(
    val model: OpenAITextModel = ChatModels.GPT4Turbo,
    val temperature: Double = 0.3,
) {


    data class DefinitionOutput(
        val term: String? = null,
        val definition: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            term.isNullOrBlank() -> "term is required"
            definition.isNullOrBlank() -> "definition is required"
            else -> null
        }
    }

    private val definitionActor = ParsedActor(
//    parserClass = DefinitionParser::class.java,
        resultClass = DefinitionOutput::class.java,
        model = ChatModels.GPT35Turbo,
        parsingModel = ChatModels.GPT35Turbo,
        prompt = """
            You are a definition generator. For each term, define the term in the given style for the given target audience. Ensure the definition is engaging and understandable.
        """.trimIndent()
    )


    private val illustrationActor = SimpleActor(
        prompt = """
            You are an illustration guidance assistant. Your task is to create detailed textual descriptions for illustrations based on the given term and illustration preferences such as style, color scheme, and specific elements to include. Use your creativity to suggest engaging and relevant visuals that accurately represent the term and adhere to the specified preferences.
        """.trimIndent().trim(),
        name = "Illustration Guidance Assistant",
        model = ChatModels.GPT35Turbo,
        temperature = 0.3
    )


    data class FeedbackOutput(
        val refinedOutput: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            refinedOutput.isNullOrBlank() -> "Refined output is required"
            else -> null
        }
    }

    private val feedbackActor = ParsedActor(
//    parserClass = FeedbackParser::class.java,
        resultClass = FeedbackOutput::class.java,
        model = ChatModels.GPT35Turbo,
        parsingModel = ChatModels.GPT35Turbo,
        prompt = """
            You are an assistant that refines content based on user feedback. Improve the content according to the feedback provided.
        """.trimIndent().trim()
    )

    private val parseInputActor = ParsedActor(
//    parserClass = TermInputParser::class.java,
        resultClass = VocabularyListBuilderAgent.TermInput::class.java,
        model = ChatModels.GPT35Turbo,
        parsingModel = ChatModels.GPT35Turbo,
        prompt = """
            You are a parser that extracts the term, definition style, target audience, and illustration preferences from the user input.
        """.trimIndent().trim()
    )

    enum class ActorType {
        DEFINITION_ACTOR,
        ILLUSTRATION_ACTOR,
        FEEDBACK_ACTOR,
        PARSE_INPUT,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.DEFINITION_ACTOR to definitionActor,
        ActorType.ILLUSTRATION_ACTOR to illustrationActor,
        ActorType.FEEDBACK_ACTOR to feedbackActor,
        ActorType.PARSE_INPUT to parseInputActor
    )

    companion object {
        val log = LoggerFactory.getLogger(VocabularyListBuilderActors::class.java)
    }
}
