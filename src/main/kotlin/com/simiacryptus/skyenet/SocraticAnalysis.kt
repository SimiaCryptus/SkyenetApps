package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSession
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import com.simiacryptus.util.JsonUtil

class SocraticAnalysis(
    applicationName: String,
    baseURL: String,
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    baseURL = baseURL,
    temperature = temperature,
    oauthConfig = oauthConfig,
) {

    interface API {

        data class TopicSummary(
            val title: String = "",
            val description: String = "",
            val notes: List<String> = listOf(),
        )

        fun parse(topicSummary: String): TopicSummary

        fun examine(
            topicSummary: TopicSummary,
            focus: String
        ): Questions

        data class Questions(
            val items: List<Question>
        )

        data class Question(
            val question: String = "",
            val suggestedAnswers: List<String> = listOf(),
        )

        fun update(
            topicSummary: TopicSummary,
            question: Questions,
            userInput: String
        ): TopicSummary

        fun expandProject(summary: TopicSummary): FullText

        data class FullText(
            val text: String = ""
        )

    }

    val virtualAPI by lazy {
        ChatProxy(
            clazz = API::class.java, api = api, model = OpenAIClient.Models.GPT4, temperature = temperature
        ).create()
    }

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sendUpdate: (String, Boolean) -> Unit
    ) {
        try {
            sendUpdate("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            reviewProject(session, sessionUI, virtualAPI.parse(userMessage), sendUpdate, sessionId)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun reviewProject(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        topicSummary: API.TopicSummary,
        sendUpdate: (String, Boolean) -> Unit,
        sessionId: String
    ) {
        val questions = virtualAPI.examine(topicSummary,
            """Ask questions to clarify the topic and provide additional details and context."""
        )
        sendUpdate("""<pre>${JsonUtil.toJson(questions)}</pre>""", true)
        iterate(sessionUI, topicSummary, sendUpdate, { feedback ->
            //language=HTML
            sendUpdate("""<div>$feedback</div>""", true)
            reviewProject(session, sessionUI, virtualAPI.update(topicSummary, questions, feedback), sendUpdate, sessionId)
        }, "End of questions") {
            val sendUpdate = session.newUpdate(ChatSession.randomID(), spinner)
            sendUpdate("", true)
            //language=HTML
            sendUpdate("""<div><pre>${JsonUtil.toJson(virtualAPI.expandProject(topicSummary))}</pre></div>""", false)
        }
    }

}