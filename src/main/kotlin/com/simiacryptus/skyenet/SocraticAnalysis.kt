package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.util.JsonUtil

class SocraticAnalysis(
    applicationName: String = "SocraticAnalysis",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    interface API {

        data class Summary(
            val title: String = "",
            val description: String = "",
            val notes: List<String> = listOf(),
        )

        fun parse(topicSummary: String): Summary

        fun examine(
            summary: Summary,
            focus: String
        ): Questions

        data class Questions(
            val items: List<Question>
        ) {
            operator fun plus(other: Questions): Questions {
                return Questions(items + other.items)
            }
        }

        data class Question(
            val question: String = "",
            val suggestedAnswers: List<String> = listOf(),
        )

        fun update(
            summary: Summary,
            question: Questions,
            userInput: String
        ): Summary

        fun expandProject(summary: Summary): FullText

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
        sessionDiv: SessionDiv
    ) {
        try {
            sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            review(session, sessionUI, virtualAPI.parse(userMessage), sessionDiv, sessionId)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun review(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        summary: API.Summary,
        sessionDiv: SessionDiv,
        sessionId: String
    ) {
        val questions = virtualAPI.examine(
            summary,
            """Clarify and provide additional details and context"""
        ) + virtualAPI.examine(
            summary,
            """Provide a critique of the topic"""
        )
        sessionDiv.append("""<pre>${JsonUtil.toJson(questions)}</pre>""", true)
        iterate(sessionUI, sessionDiv, summary, { summary: API.Summary, feedback: String ->
            //language=HTML
            sessionDiv.append("""<div>$feedback</div>""", true)
            review(session, sessionUI, virtualAPI.update(summary, questions, feedback), sessionDiv, sessionId)
        }, mapOf("End of questions" to { summary: API.Summary ->
            val sessionDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
            sessionDiv.append("", true)
            //language=HTML
            sessionDiv.append("""<div><pre>${JsonUtil.toJson(virtualAPI.expandProject(summary))}</pre></div>""", false)
        }))
    }

}