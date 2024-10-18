package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.util.TensorflowProjector
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "Automated Debate Concept Map v1.3",
    val domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/debate",
) {
    data class Settings(
        val model: ChatModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.2,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings() as T

    override val description: String
        @Language("HTML")
        get() = "<div>" + MarkdownUtil.renderMarkdown(
            """
              Welcome to the Debate Agent, an app designed to explore the landscape of ideas with a focus on multiple perspectives.
              
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
            DebateAgent(
                api = api,
                dataStorage = dataStorage,
                userId = user,
                session = session,
                ui = ui,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
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
    val model: ChatModel = OpenAIModels.GPT4o,
    val temperature: Double = 0.3,
    private val debateActors: DebateActors = DebateActors(model, temperature)
) : ActorSystem<DebateActors.ActorType>(
    debateActors.actorMap.map { it.key.name to it.value }.toMap(),
    dataStorage,
    userId,
    session
) {
    private val outlines = mutableMapOf<String, DebateActors.Outline>()

    @Suppress("UNCHECKED_CAST")
    private val moderator get() = getActor(DebateActors.ActorType.MODERATOR) as ParsedActor<DebateActors.DebateSetup>

    val tabs = TabbedDisplay(ui.newTask())

    fun debate(userMessage: String) {

        //language=HTML
        val mainTask = ui.newTask(false).apply { tabs["Main"] = placeholder }
        mainTask.complete(
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

        mainTask.echo(MarkdownUtil.renderMarkdown(userMessage, ui = ui))
        val moderatorResponse = this.moderator.answer(listOf(userMessage), api = api)
        mainTask.add(MarkdownUtil.renderMarkdown(moderatorResponse.text, ui = ui))
        mainTask.verbose(JsonUtil.toJson(moderatorResponse.obj))
        mainTask.complete()

        val questionTabs = TabbedDisplay(mainTask)
        val allStatements =
            (moderatorResponse.obj.questions?.list ?: emptyList()).parallelStream().map { question ->
                val questionTask = ui.newTask(false).apply { questionTabs[question.text!!] = placeholder }
                questionTask.header(
                    MarkdownUtil.renderMarkdown(question.text ?: "", ui = ui).trim(),
                    classname = "response-message response-message-question"
                )
                try {
                    val responseTabs = TabbedDisplay(questionTask)
                    val debaters = (moderatorResponse.obj.debaters?.list ?: emptyList()).toMutableList()
                    debaters.shuffle()
                    val result = List(debaters.size) { index ->
                        val actor = debaters[index]
                        val responseTask =
                            ui.newTask(false).apply { responseTabs[actor.name!!] = placeholder }
                        responseTask.header(MarkdownUtil.renderMarkdown(actor.name ?: "", ui = ui).trim())
                        val previousResponses = debaters.subList(0, index).mapNotNull { previousActor ->
                            """
                                **${previousActor.name}**: ${
                                    outlines[previousActor.name!! + ": " + question.text!!]?.arguments?.joinToString("\n") {
                                        val t = it.text ?: ""
                                        t
                                    }?.trim()?.replace("\n", "\n  ")
                                }
                                """.trimIndent().trim()
                        }
                        val response = debateActors.getActorConfig(actor)
                                .answer(listOf(question.text ?: "") + previousResponses, api = api)
                        outlines[actor.name!! + ": " + question.text!!] = response.obj
                        responseTask.add(MarkdownUtil.renderMarkdown(response.text, ui = ui))
                        responseTask.verbose(JsonUtil.toJson(response.obj))
                        responseTask.complete()
                        response.obj.arguments?.map { it.text ?: "" } ?: emptyList()
                    }
                    questionTask.complete()
                    result
                } catch (e: Exception) {
                    questionTask.error(ui, e)
                    throw e
                }
            }.toList().flatten().flatten() + (moderatorResponse.obj.questions?.list?.mapNotNull { it.text }
                ?.filter { it.isNotBlank() } ?: emptyList())

        ui.newTask(false).apply { tabs["Projector"] = placeholder }.complete(
            TensorflowProjector(
                api = api,
                dataStorage = dataStorage,
                sessionID = session,
                session = ui,
                userId = user,
            ).writeTensorflowEmbeddingProjectorHtml(*(allStatements.toList().toTypedArray<String>()))
        )

    }

}

class DebateActors(val model: ChatModel, val temperature: Double) {

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
        resultClass = Outline::class.java,
        prompt = """You are a debater: ${actor.name}.
            |
            |You will provide a well-reasoned and supported argument for your position.
            |You will comment on and respond to the arguments of the other debaters.
            |
            |Details about you: ${actor.description}
        """.trimMargin(),
        model = model,
        parsingModel = OpenAIModels.GPT4oMini,
        temperature = temperature,
    )

    private fun moderator() = ParsedActor(
        resultClass = DebateSetup::class.java,
        prompt = """
            You will take a user request, and plan a debate. 
            You will introduce the debaters, and then provide a list of questions to ask.
            Debaters should be chosen as recognized experts in the field with household name status.
            """.trimIndent(),
        model = model,
        parsingModel = OpenAIModels.GPT4oMini,
        temperature = temperature,
    )

    private fun summarizer() = SimpleActor(
        prompt = """You are a helpful writing assistant, tasked with writing a markdown document combining the user massages given in an impartial manner""",
        model = model,
        temperature = temperature,
    )

    companion object
}