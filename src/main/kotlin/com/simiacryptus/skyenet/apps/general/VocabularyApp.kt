package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Role
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.util.JsonUtil.toJson
import com.simiacryptus.skyenet.Discussable
import com.simiacryptus.skyenet.AgentPatterns.displayMapInTabs
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.VocabularyActors.ActorType.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SessionTask.Companion.toPng
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage


open class VocabularyApp(
    applicationName: String = "Vocabulary List Generator v1.0",
    path: String = "/vocabulary",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
) {

    data class Settings(
        val model: ChatModel = OpenAIModels.GPT4o,
        val temperature: Double = 0.1,
        val parsingModel: ChatModel = OpenAIModels.GPT4o,
        val imageModel: ImageModels = ImageModels.DallE3,
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
            VocabularyAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
                parsingModel = settings?.parsingModel ?: OpenAIModels.GPT4oMini,
                imageModel = settings?.imageModel ?: ImageModels.DallE3,
                temperature = settings?.temperature ?: 0.3,
                path = path
            ).generate(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VocabularyApp::class.java)
    }

}


open class VocabularyAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModel = OpenAIModels.GPT4oMini,
    val parsingModel: ChatModel = OpenAIModels.GPT4oMini,
    val imageModel: ImageModels = ImageModels.DallE3,
    temperature: Double = 0.3,
    val path: String,
) : ActorSystem<VocabularyActors.ActorType>(
    VocabularyActors(
        model = model,
        parsingModel = parsingModel,
        imageModel = imageModel,
        temperature = temperature,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val inputProcessorActor by lazy { getActor(INPUT_PROCESSOR_ACTOR) as ParsedActor<VocabularyActors.UserInput> }
    private val aidefinitionGeneratorActor by lazy { getActor(AIDEFINITION_GENERATOR_ACTOR) as ParsedActor<VocabularyActors.TermDefinition> }
    private val illustrationGeneratorActor by lazy { getActor(ILLUSTRATION_GENERATOR_ACTOR) as ImageActor }

    val tabs = TabbedDisplay(ui.newTask())

    fun generate(userInput: String) {
        val task: SessionTask = ui.newTask(false).apply { tabs["Generation"] = placeholder }
        try {
            val toInput = { it: String -> listOf(it) }
            val parsedInput = Discussable(
                task = task,
                userMessage = { userInput },
                heading = renderMarkdown(userInput, ui = ui),
                initialResponse = { it: String -> inputProcessorActor.answer(toInput(it), api = api) },
                outputFn = { design ->
                    displayMapInTabs(
                        mapOf(
                            "Text" to renderMarkdown(design.text, ui = ui),
                            "JSON" to renderMarkdown("```json\n${toJson(design.obj)/*.indent("  ")*/}\n```", ui = ui),
                        )
                    )
                },
                ui = ui,
                reviseResponse = { userMessages: List<Pair<String, Role>> ->
                    inputProcessorActor.respond(
                        messages = (userMessages.map<Pair<String, Role>, ApiModel.ChatMessage> {
                            ApiModel.ChatMessage(
                                it.second,
                                it.first.toContentList()
                            )
                        }.toTypedArray<ApiModel.ChatMessage>()),
                        input = toInput(userInput),
                        api = api
                    )
                },
            ).call().obj

            task.add(renderMarkdown("```json\n${toJson(parsedInput)/*.indent("  ")*/}\n```", ui = ui))

            // Initialize lists to hold terms, definitions, and illustrations
            val terms = parsedInput.terms
            val definitions = mutableListOf<CompiledTerm>()

            // Process each term to generate its definition and illustration
            val wordTabs = TabbedDisplay(task)
            (terms?: emptyList()).map { term ->
                val task = ui.newTask(false).apply { wordTabs[term] = placeholder }
                pool.submit {
                    try {
                        val response = aidefinitionGeneratorActor.answer(
                            listOf(
                                "Generate a definition for the term '$term' tailored for a '${parsedInput.targetAudience}' audience in a '${parsedInput.style}' style."
                            ), api = api
                        )
                        val definition = VocabularyActors.TermDefinition(term, response.text).definition ?: "{}"
                        task.header(term)
                        task.add(renderMarkdown(definition, ui = ui))

                        val illustration = illustrationGeneratorActor.setImageAPI(
                            ApplicationServices.clientManager.getOpenAIClient(session,user)
                        ).answer(
                            listOf(
                                "Generate an illustration that visually represents the term '$term' to ${parsedInput.targetAudience} in a '${parsedInput.style}' style."
                            ), api = api
                        ).image
                        task.image(illustration)

                        definitions.add(CompiledTerm(term, definition, illustration))
                    } catch (e: Throwable) {
                        task.error(ui, e)
                    }
                }
            }.toTypedArray().forEach { it.get() }

            val task: SessionTask = ui.newTask(false).apply { tabs["Output"] = placeholder }
            val imageTask = ui.newTask(false).apply { tabs["Images"] = placeholder }
            task.complete(
                "<a href='${
                    task.saveFile(
                        "vocabulary.html",
                        buildString {
                            append("<html><body><ul>")
                            append(definitions.joinToString("\n") { term ->
                                val imgname = "${Session.long64()}.png"
                                val saveFile = task.saveFile(imgname, term.illustration.toPng())
                                imageTask.add("<img src='$path/$saveFile' alt='${term.term}' />")
                                """<li>
                  |<h2>${term.term}</h2>
                  |<p>${term.definition}</p>
                  |<img src="$imgname" alt='${term.term}' />
                  |</li>
                  |""".trimMargin()
                            } ?: "")
                            append("</ul></body></html>")
                        }.toByteArray()
                    )
                }'>vocabulary.html</a> Updated"
            )
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    data class CompiledTerm(
        val term: String,
        val definition: String,
        val illustration: BufferedImage
    )

    companion object
}

class VocabularyActors(
    val model: ChatModel = OpenAIModels.GPT4o,
    val parsingModel: ChatModel = OpenAIModels.GPT4oMini,
    val imageModel: ImageModels = ImageModels.DallE3,
    val temperature: Double = 0.3,
) {


    private val userInterfaceActor = SimpleActor(
        prompt = """
            You are an interactive user interface assistant. Your role is to help users create a vocabulary list. For each term, you will guide them to define the term in a given style for a specified target audience. Additionally, you will assist in producing an illustration to represent each term.
        """.trimIndent(),
        name = "UserInterfaceActor",
        model = model,
        temperature = temperature
    )


    data class UserInput(
        @Description("A list of terms to define")
        val terms: List<String>? = null,
        @Description("The target audience for the definitions")
        val targetAudience: String? = null,
        @Description("The style of the definitions")
        val style: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            terms == null -> "Please provide a list of terms to define"
            terms.isEmpty() -> "Please provide at least one term to define"
            targetAudience == null -> "Please specify the target audience for the definitions"
            style == null -> "Please specify the style of the definitions"
            else -> null
        }
    }

    private val inputProcessorActor = ParsedActor(
        resultClass = UserInput::class.java,
        model = model,
        prompt = """
            Parse and validate the input terms and user preferences for generating a vocabulary list. Ensure the terms are valid, and preferences like target audience, definition style, and output format are correctly identified.
        """.trimIndent(),
        parsingModel = parsingModel,
    )


    data class TermDefinition(
        val term: String? = null,
        val definition: String? = null
    )

    private val aidefinitionGeneratorActor = ParsedActor(
        resultClass = TermDefinition::class.java,
        prompt = """
            You are an AI designed to generate definitions for terms. For each term provided, produce a clear and concise definition that is easy to understand.
        """.trimIndent(),
        model = model,
        temperature = temperature,
        parsingModel = parsingModel,
    )


    private val illustrationGeneratorActor = ImageActor(
        prompt = "Generate an illustration that visually represents the given term and its definition in a creative and understandable manner.",
        imageModel = imageModel,
        temperature = temperature,
        width = 1024,
        height = 1024,
        textModel = model
    )

    enum class ActorType {
        USER_INTERFACE_ACTOR,
        INPUT_PROCESSOR_ACTOR,
        AIDEFINITION_GENERATOR_ACTOR,
        ILLUSTRATION_GENERATOR_ACTOR,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        USER_INTERFACE_ACTOR to userInterfaceActor,
        INPUT_PROCESSOR_ACTOR to inputProcessorActor,
        AIDEFINITION_GENERATOR_ACTOR to aidefinitionGeneratorActor,
        ILLUSTRATION_GENERATOR_ACTOR to illustrationGeneratorActor,
    )

    companion object {
        val log = LoggerFactory.getLogger(VocabularyActors::class.java)
    }
}
