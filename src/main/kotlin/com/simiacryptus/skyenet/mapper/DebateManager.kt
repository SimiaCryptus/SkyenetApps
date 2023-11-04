package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.util.JsonUtil

class DebateManager(
    val api: OpenAIClient,
    val virtualAPI: DebateAPI = ChatProxy(
        clazz = DebateAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = 0.1,
    ).create(),
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
    val moderator: ActorConfig = ActorConfig(
        api = api,
        prompt = """You will take a user request, and plan a debate. You will introduce the debaters, and then provide a list of questions to ask.""",
        model = OpenAIClient.Models.GPT4,
    ),
    val summarizor: ActorConfig = ActorConfig(
        api,
        prompt = """You are a helpful writing assistant, tasked with writing a markdown document combining the user massages given in an impartial manner""",
        model = OpenAIClient.Models.GPT4,
    ),
) {
    class ActorConfig(
        val api: OpenAIClient = OpenAIClient(),
        val prompt: String,
        val action: String? = null,
        val model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
        val temperature: Double = 0.3,
    ) {

        fun answer(vararg questions: String): String = answer(*chatMessages(*questions))

        fun chatMessages(vararg questions: String) = arrayOf(
            OpenAIClient.ChatMessage(
                role = OpenAIClient.ChatMessage.Role.system,
                content = prompt
            ),
        ) + questions.map {
            OpenAIClient.ChatMessage(
                role = OpenAIClient.ChatMessage.Role.user,
                content = it
            )
        }

        fun answer(vararg messages: OpenAIClient.ChatMessage): String = api.chat(
            OpenAIClient.ChatRequest(
                messages = messages.toList().toTypedArray(),
                temperature = temperature,
                model = model.modelName,
            ),
            model = model
        ).choices.first().message?.content ?: throw RuntimeException("No response")
    }

    fun debate(userMessage: String, session: PersistentSessionBase, sessionDiv: SessionDiv, domainName: String) {
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
        val moderatorResponse = moderator.answer(userMessage)
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(moderatorResponse)}</div>""", verbose)
        val debateOutline = virtualAPI.toDebateSetup(moderatorResponse)
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(debateOutline)}</pre>""", false)

        val totalSummary = (debateOutline.questions?.list ?: emptyList()).parallelStream().map { question ->
            val summarizorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            val answers = (debateOutline.debators?.list ?: emptyList()).parallelStream()
                .map { actor -> answer(session, actor, question) }.toList()
            summarizorDiv.append(
                """<div>Summarizing: ${
                    ChatSessionFlexmark.renderMarkdown(question.text ?: "").trim()
                }</div>""", true
            )
            val summarizorResponse = summarizor.answer(*(listOf((question.text ?: "").trim()) + answers).toTypedArray())
            summarizorDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(summarizorResponse)}</div>""", false)
            summarizorResponse
        }.toList()

        val argumentList = outlines.values.flatMap { it.arguments?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet() } +
                (debateOutline.questions?.list?.map { it.text ?: "" }?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
        val projectorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        projectorDiv.append("""<div>Embedding Projector</div>""", true)
        val response = EmbeddingVisualizer(
            api = api,
            sessionDataStorage = sessionDataStorage,
            sessionID = sessionDiv.sessionID(),
            appPath = "debate_mapper",
            host = domainName
        ).writeTensorflowEmbeddingProjectorHtml(*argumentList.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        val conclusionDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(totalSummary)}</pre>""", true)
        val summarizorResponse = summarizor.answer(*totalSummary.toTypedArray())
        if (verbose) conclusionDiv.append("""<pre>${JsonUtil.toJson(summarizorResponse)}</pre>""", false)
        conclusionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(summarizorResponse)}</div>""", false)
    }

    val outlines = mutableMapOf<String, DebateAPI.Outline>()

    private fun answer(
        session: PersistentSessionBase,
        actor: DebateAPI.Debator,
        question: DebateAPI.Question
    ): String {
        val resonseDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        resonseDiv.append(
            """<div>${actor.name?.trim() ?: ""} - ${
                ChatSessionFlexmark.renderMarkdown(question.text ?: "").trim()
            }</div>""",
            true
        )
        val debatorResponse = ActorConfig(
            api = api,
            prompt = """You are a debater: ${actor.name}.
                        |You will provide a well-reasoned and supported argument for your position.
                        |Details about you: ${actor.description}
                        """.trimMargin(),
            model = OpenAIClient.Models.GPT4,
        ).answer(question.text ?: "")
        resonseDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(debatorResponse)}</div>""", false)
        val outline: DebateAPI.Outline = virtualAPI.toOutline(debatorResponse)
        outlines[actor.name!! + ": " + question.text!!] = outline
        if (verbose) {
            resonseDiv.append(
                """<pre>${
                    JsonUtil.toJson(outline).trim()
                }</pre>""", false
            )
        }

        return debatorResponse
    }
}