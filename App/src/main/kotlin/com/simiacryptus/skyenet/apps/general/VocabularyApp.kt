package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.Acceptable
import com.simiacryptus.skyenet.AgentPatterns
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.StorageInterface.Companion.long64
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask.Companion.toPng
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage


open class VocabularyApp(
    applicationName: String = "Vocabulary List Generator",
    path: String = "/vocabulary",
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
            VocabularyAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: ChatModels.GPT35Turbo,
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
    model: OpenAITextModel = ChatModels.GPT35Turbo,
    temperature: Double = 0.3,
    val path: String,
) : ActorSystem<VocabularyActors.ActorType>(
    VocabularyActors(
        model = model,
        temperature = temperature,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val inputProcessorActor by lazy { getActor(VocabularyActors.ActorType.INPUT_PROCESSOR_ACTOR) as ParsedActor<VocabularyActors.UserInput> }
    private val aidefinitionGeneratorActor by lazy { getActor(VocabularyActors.ActorType.AIDEFINITION_GENERATOR_ACTOR) as ParsedActor<VocabularyActors.TermDefinition> }
    private val illustrationGeneratorActor by lazy { getActor(VocabularyActors.ActorType.ILLUSTRATION_GENERATOR_ACTOR) as ImageActor }

    fun generate(userInput: String) {
        val task = ui.newTask()
        try {
            val toInput = { it: String -> listOf(it) }
            val parsedInput = Acceptable<ParsedResponse<VocabularyActors.UserInput>>(
                task = ui.newTask(),
                userMessage = userInput,
                heading = renderMarkdown(userInput, ui = ui),
                initialResponse = { it: String -> inputProcessorActor.answer(toInput(it), api = api) },
                outputFn = { design: ParsedResponse<VocabularyActors.UserInput> ->
                    //          renderMarkdown("${design.text}\n\n```json\n${toJson(design.obj)/*.indent("  ")*/}\n```")
                    AgentPatterns.displayMapInTabs(
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
            val definitions = mutableListOf<String>()
            val illustrations = mutableListOf<BufferedImage>()

            // Process each term to generate its definition and illustration
            try {
                for (term in terms) {
                    val response = aidefinitionGeneratorActor.answer(
                        listOf(
                            "Generate a definition for the term '$term' tailored for a '${parsedInput.targetAudience}' audience in a '${parsedInput.style}' style."
                        ), api = api
                    )
                    val definition = VocabularyActors.TermDefinition(term, response.text).definition
                    definitions.add(definition)
                    task.add("Generated definition for term: $term")
                    task.verbose(renderMarkdown("```json\n${toJson(definition)/*.indent("  ")*/}\n```", ui = ui))

                    val illustration = illustrationGeneratorActor.answer(
                        listOf(
                            "Generate an illustration that visually represents the term '$term' to ${parsedInput.targetAudience} in a '${parsedInput.style}' style."
                        ), api = api
                    ).image
                    illustrations.add(illustration)
                    task.add("Generated illustration for term: $term")
                    task.image(illustration)
                }
            } catch (e: Throwable) {
                task.error(ui, e)
                throw e
            }

            task.complete(
                "<a href='${
                    task.saveFile(
                        "vocabulary.html",
                        buildString {
                            append("<html><body><ul>")
                            append(terms.zip(definitions.zip(illustrations)) { term, defAndIllus ->
                                CompiledTerm(term, defAndIllus.first, defAndIllus.second)
                            }.joinToString("\n") { term ->
                                val imgname = "${long64()}.png"
                                val saveFile = task.saveFile(imgname, term.illustration.toPng())
                                task.add("<img src='$path/$saveFile' alt='${term.term}' />")
                                """<li>
                  |<h2>${term.term}</h2>
                  |<p>${term.definition}</p>
                  |<img src="$imgname" alt='${term.term}' />
                  |</li>
                  |""".trimMargin()
                            })
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
    val model: OpenAITextModel = ChatModels.GPT4Turbo,
    val temperature: Double = 0.3,
) {


    private val userInterfaceActor = SimpleActor(
        prompt = """
            You are an interactive user interface assistant. Your role is to help users create a vocabulary list. For each term, you will guide them to define the term in a given style for a specified target audience. Additionally, you will assist in producing an illustration to represent each term.
        """.trimIndent(),
        name = "UserInterfaceActor",
        model = ChatModels.GPT35Turbo,
        temperature = 0.3
    )


    data class UserInput(
        val terms: List<String>,
        val targetAudience: String,
        val style: String
    )

    private val inputProcessorActor = ParsedActor(
//    parserClass = UserInputParser::class.java,
        resultClass = UserInput::class.java,
        model = ChatModels.GPT35Turbo,
        prompt = """
            Parse and validate the input terms and user preferences for generating a vocabulary list. Ensure the terms are valid, and preferences like target audience, definition style, and output format are correctly identified.
        """.trimIndent(),
        parsingModel = ChatModels.GPT35Turbo,
    )


    data class TermDefinition(
        val term: String,
        val definition: String
    )

    private val aidefinitionGeneratorActor = ParsedActor(
//    parserClass = TermDefinitionParser::class.java,
        resultClass = TermDefinition::class.java,
        prompt = """
            You are an AI designed to generate definitions for terms. For each term provided, produce a clear and concise definition that is easy to understand.
        """.trimIndent(),
        model = ChatModels.GPT35Turbo,
        temperature = 0.3,
        parsingModel = ChatModels.GPT35Turbo,
    )


    private val illustrationGeneratorActor = ImageActor(
        prompt = "Generate an illustration that visually represents the given term and its definition in a creative and understandable manner.",
        imageModel = ImageModels.DallE3,
        temperature = 0.3,
        width = 1024,
        height = 1024,
        textModel = ChatModels.GPT35Turbo
    )

    enum class ActorType {
        USER_INTERFACE_ACTOR,
        INPUT_PROCESSOR_ACTOR,
        AIDEFINITION_GENERATOR_ACTOR,
        ILLUSTRATION_GENERATOR_ACTOR,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.USER_INTERFACE_ACTOR to userInterfaceActor,
        ActorType.INPUT_PROCESSOR_ACTOR to inputProcessorActor,
        ActorType.AIDEFINITION_GENERATOR_ACTOR to aidefinitionGeneratorActor,
        ActorType.ILLUSTRATION_GENERATOR_ACTOR to illustrationGeneratorActor,
    )

    companion object {
        val log = LoggerFactory.getLogger(VocabularyActors::class.java)
    }
}
