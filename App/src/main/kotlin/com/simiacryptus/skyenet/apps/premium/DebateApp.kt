package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.util.TensorflowProjector
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.function.Function

open class DebateApp(
    applicationName: String = "Automated Debate Concept Map v1.2",
    val domainName: String,
) : ApplicationServer(
  applicationName = applicationName,
    path = "/debate",
) {
    data class Settings(
        val model: ChatModels = ChatModels.GPT35Turbo,
        val temperature: Double = 0.2,
        val budget : Double = 2.0,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

    override val description: String
        @Language("HTML")
        get() = "<div>" + MarkdownUtil.renderMarkdown(
            """
              Welcome to the Debate Agent, an app designed to expore the landscape of ideas with a focus on multiple perspectives.
              
              Here's what you can expect from the Debate Agent:
              
              * **Initial Prompt**: Submit your questions or statements, and watch as the AI-powered moderator and debaters explore the topic, providing a range of perspectives and insights.
              * **Visual Insights**: Explore the intricate landscape of debate topics and responses through our Embedding Projector, a feature that visually maps out the relationships between different arguments and ideas.
              
              Similar to our Outline Agent, we hope you find the Debate Agent to be a useful tool for exploring the landscape of ideas.
          """.trimIndent()
        ) + "</div>"

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
            DebateAgent(
                api = api,
                dataStorage = dataStorage,
                userId = user,
                session = session,
                ui = ui,
                domainName = domainName,
                model = settings?.model ?: ChatModels.GPT35Turbo,
                temperature = settings?.temperature ?: 0.3,
            ).debate(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}

class DebateAgent(
    val api: API,
    dataStorage: StorageInterface,
    userId: User?,
    session: Session,
    val ui: ApplicationInterface,
    val domainName: String,
    val model : ChatModels = ChatModels.GPT4,
    val temperature: Double = 0.3,
    private val debateActors: DebateActors = DebateActors(model, temperature)
) : ActorSystem<DebateActors.ActorType>(debateActors.actorMap, dataStorage, userId, session) {
    private val outlines = mutableMapOf<String, DebateActors.Outline>()

    @Suppress("UNCHECKED_CAST")
    private val moderator get() = getActor(DebateActors.ActorType.MODERATOR) as ParsedActor<DebateActors.DebateSetup>

    fun debate(userMessage: String) {

        //language=HTML
        ui.newTask().complete(
            """
      <style>
        .response-message-question {
          font-weight: bold;
        }
        .response-message-actor {
          font-style: italic;
        }
      </style>
    """.trimIndent()
        )

        val moderatorTask = ui.newTask()
        moderatorTask.echo(MarkdownUtil.renderMarkdown(userMessage))
        val moderatorResponse = this.moderator.answer(listOf(userMessage), api = api)
        moderatorTask.add(MarkdownUtil.renderMarkdown(moderatorResponse.text))
        moderatorTask.verbose(JsonUtil.toJson(moderatorResponse.obj))
        moderatorTask.complete()

        val allStatements =
            (moderatorResponse.obj.questions?.list ?: emptyList()).parallelStream().flatMap { question ->
                val questionTask = ui.newTask()
                questionTask.header(
                    MarkdownUtil.renderMarkdown(question.text ?: "").trim(),
                    classname = "response-message response-message-question"
                )
                try {
                    val result = (moderatorResponse.obj.debaters?.list ?: emptyList())
                        .map { actor ->
                            questionTask.header(actor.name?.trim() ?: "", classname = "response-message response-message-actor")
                            val response = debateActors.getActorConfig(actor).answer(listOf(question.text ?: ""), api = api)
                            outlines[actor.name!! + ": " + question.text!!] = response.obj
                            questionTask.add(MarkdownUtil.renderMarkdown(response.text))
                            questionTask.verbose(JsonUtil.toJson(response.obj))
                            response.obj.arguments?.map { it.text ?: "" } ?: emptyList()
                        }
                    questionTask.complete()
                    result.flatten().stream()
                } catch (e: Exception) {
                    questionTask.error(ui, e)
                    throw e
                }
            }.toList() + (moderatorResponse.obj.questions?.list?.mapNotNull { it.text }
                ?.filter { it.isNotBlank() } ?: emptyList())

        ui.newTask().complete(
            TensorflowProjector(
                api = api,
                dataStorage = dataStorage,
                sessionID = session,
                appPath = "debate_mapper",
                host = domainName,
                session = ui,
                userId = user,
            ).writeTensorflowEmbeddingProjectorHtml(*(allStatements).toTypedArray<String>())
        )

    }

}
class DebateActors(val model: ChatModels, val temperature: Double) {

    interface DebateParser : Function<String, DebateSetup> {
        @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): DebateSetup
    }

    data class DebateSetup(
        val debaters: Debaters? = null,
        val questions: Questions? = null,
    )

    data class Debaters(
        val list: List<Debater>? = null,
    )

    data class Questions(
        val list: List<Question>? = null,
    )

    data class Debater(
        val name: String? = null,
        val description: String? = null,
    )

    data class Question(
        val text: String? = null,
    )

    interface OutlineParser : Function<String, Outline> {
        @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): Outline
    }

    data class Outline(
        val arguments: List<Argument>? = null,
    ) : ValidatedObject {
        override fun validate(): String? {
            val joinToString = arguments?.filter { it.validate() != null }?.map { it.validate() }?.joinToString("\n")
            return if (joinToString.isNullOrBlank()) null else joinToString
        }

    }

    data class Argument(
        val point_name: String? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == point_name -> "point_name is required"
            point_name.isEmpty() -> "point_name is required"
            else -> null
        }

    }

    enum class ActorType {
        MODERATOR,
        SUMMARIZER,
    }

    val actorMap
        get() = mapOf(
            ActorType.MODERATOR to moderator(),
            ActorType.SUMMARIZER to summarizer(),
        )

    fun getActorConfig(actor: Debater) = ParsedActor(
        parserClass = OutlineParser::class.java,
        prompt = """You are a debater: ${actor.name}.
                              |You will provide a well-reasoned and supported argument for your position.
                              |Details about you: ${actor.description}
                              """.trimMargin(),
        model = model,
        parsingModel = ChatModels.GPT35Turbo,
        temperature = temperature,
    )

    private fun moderator() = ParsedActor(
        DebateParser::class.java,
        prompt = """You will take a user request, and plan a debate. You will introduce the debaters, and then provide a list of questions to ask.""",
        model = model,
        parsingModel = ChatModels.GPT35Turbo,
        temperature = temperature,
    )

    private fun summarizer() = SimpleActor(
        prompt = """You are a helpful writing assistant, tasked with writing a markdown document combining the user massages given in an impartial manner""",
        model = model,
        temperature = temperature,
    )

    companion object {

    }
}