package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SkyenetMacroChat

class NewsParodyGenerator(
    applicationName: String,
    baseURL: String,
    oauthConfig: String? = null,
    temperature: Double = 0.3
) : SkyenetMacroChat(
    applicationName = applicationName,
    baseURL = baseURL,
    temperature = temperature,
    oauthConfig = oauthConfig
) {
    interface NewsParodyAPI {

        fun generateNewsCategories(description: String, count: Int = 10): CategoryList

        data class CategoryList(
            val items: List<NewsCategory>
        )

        data class NewsCategory(
            val title: String = "",
            val description: String = ""
        )

        fun generateNewsStoryIdeas(category: NewsCategory, count: Int = 10): NewsStoryDescriptionList

        data class NewsStoryDescriptionList(
            val items: List<NewsStoryDescription>
        )

        data class NewsStoryDescription(
            val who: String = "",
            val what: String = "",
            val `when`: String = "",
            val where: String = "",
            val why: String = "",
            val headline: String = "",
            val punchline: String = "",
            val twist: String = ""
        )

        fun generateNewsStoryText(story: NewsStoryDescription): NewsStoryText

        data class NewsStoryText(
            val text: String = ""
        )

    }

    private val newsParodyAPI = ChatProxy(
        clazz = NewsParodyAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = temperature
    ).create()

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sendUpdate: (String, Boolean) -> Unit
    ) {
        try {
            sendUpdate(ChatSessionFlexmark.renderMarkdown(userMessage), true)
            newsParodyAPI.generateNewsCategories(userMessage).items.forEach { category ->
                sendUpdate(
                    """${
                        sessionUI.hrefLink {
                            processCategory(sessionUI, sendUpdate, category)
                        }
                    }${category.title}</a>""",
                    true
                )

            }
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun processCategory(
        sessionUI: SessionUI,
        sendUpdate: (String, Boolean) -> Unit,
        category: NewsParodyAPI.NewsCategory
    ) {
        sendUpdate("""<hr/><div><em>${category.title}</em></div>""", true)
        newsParodyAPI.generateNewsStoryIdeas(category).items.forEach { idea ->
            sendUpdate(
                """${ sessionUI.hrefLink { processIdea(sendUpdate, idea) } }${idea.headline}</a>""",
                false
            )
        }
    }

    private fun processIdea(
        sendUpdate: (String, Boolean) -> Unit,
        idea: NewsParodyAPI.NewsStoryDescription
    ) {
        sendUpdate("""<hr/><div><em>${idea.headline}</em></div>""", true)
        val story = newsParodyAPI.generateNewsStoryText(idea)
        sendUpdate(
            ChatSessionFlexmark.renderMarkdown(story.text),
            false
        )
    }

}