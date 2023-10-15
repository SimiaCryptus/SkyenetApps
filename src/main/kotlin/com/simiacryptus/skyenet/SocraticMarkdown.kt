package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSession
import com.simiacryptus.skyenet.body.ChatSessionFlexmark.Companion.renderMarkdown
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.describe.Description

open class SocraticMarkdown(
    applicationName: String = "SocraticMarkdown",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    private val examinationQuestions: List<String> = listOf(
        """Clarify and provide additional details and context""",
        """Provide a critique of the topic"""
    )
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    interface API {

        fun formatMarkdown(topicSummary: String): MarkdownString

        data class MarkdownString(
            val markdown: String = ""
        )

        fun examine(
            summary: String,
            focus: String
        ): Questions

        data class Questions(
            val items: List<Question>
        ) {
            operator fun plus(other: Questions): Questions {
                return Questions(items + other.items)
            }

            fun toMarkdown(): String {
                return items.joinToString("\n") { question ->
                    """### ${question.question}
                        |${question.choices.joinToString("\n") { answer -> "1. [ ] $answer" }}
                        |""".trimMargin()
                }
            }
        }

        data class Question(
            val question: String = "",
            val choices: List<String> = listOf(),
        )

        @Description("Integrate the user's feedback into the summary")
        fun integrateFeedback(
            @Description("Current summary")
            summary: MarkdownString,
            @Description("Questions posed to the user - feedback may reference these")
            question: Questions,
            @Description("User feedback - changes to the summary are described here")
            userInput: String
        ): MarkdownString

        fun summarize(summary: String): FullText

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
            sessionDiv.append("""<div>${renderMarkdown(userMessage)}</div>""", true)
            review(session, sessionUI, virtualAPI.formatMarkdown(userMessage).markdown, sessionDiv, sessionId)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun review(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        markdownData: String,
        sessionDiv: SessionDiv,
        sessionId: String
    ) {
        val questions = examinationQuestions.map { question ->
            virtualAPI.examine(markdownData, question)
        }.reduce { a, b -> a + b }
        //sessionDiv.append("""<pre>${JsonUtil.toJson(questions)}</pre>""", true)
        sessionDiv.append("""<div>${renderMarkdown(questions.toMarkdown())}</div>""", true)
        iterate(sessionUI, sessionDiv, markdownData, { summary: String, feedback: String ->
            //language=HTML
            sessionDiv.append("""<div>$feedback</div>""", true)
            review(
                session,
                sessionUI,
                virtualAPI.integrateFeedback(API.MarkdownString(summary), questions, feedback).markdown,
                sessionDiv,
                sessionId
            )
        }, actions(session), ::renderMarkdown)
    }

    open fun actions(session: PersistentSessionBase) = mapOf(
        "Summarize" to { summary: String ->
            val sessionDiv = session.newSessionDiv(ChatSession.randomID(), spinner)
            sessionDiv.append("", true)
            sessionDiv.append(
                //language=HTML
                """<div><pre>${JsonUtil.toJson(virtualAPI.summarize(summary))}</pre></div>""",
                false
            )
        }
    )

}