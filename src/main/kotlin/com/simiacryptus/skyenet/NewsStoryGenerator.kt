package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat

class NewsStoryGenerator(
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
    interface NewsStoryAPI {

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
            val punchline: String = ""
        )

        fun generateNewsStoryText(story: NewsStoryDescription): NewsStoryText

        data class NewsStoryText(
            val text: String = ""
        )

    }

    private val newsStoryAPI = ChatProxy(
        clazz = NewsStoryAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = temperature
    ).create()

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            sessionDiv.append(ChatSessionFlexmark.renderMarkdown(userMessage), true)
            newsStoryAPI.generateNewsCategories(userMessage).items.forEach { category ->
                sessionDiv.append(
                    """${
                        sessionUI.hrefLink {
                            processCategory(sessionUI, sessionDiv, category)
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
        sessionDiv: SessionDiv,
        category: NewsStoryAPI.NewsCategory
    ) {
        sessionDiv.append("""<hr/><div><em>${category.title}</em></div>""", true)
        newsStoryAPI.generateNewsStoryIdeas(category).items.forEach { idea ->
            sessionDiv.append(
                """${ sessionUI.hrefLink { processIdea(sessionDiv, idea) } }${idea.headline}</a>""",
                false
            )
        }
    }

    private fun processIdea(
        sessionDiv: SessionDiv,
        idea: NewsStoryAPI.NewsStoryDescription
    ) {
        sessionDiv.append("""<hr/><div><em>${idea.headline}</em></div>""", true)
        val story = newsStoryAPI.generateNewsStoryText(idea)
        sessionDiv.append(
            ChatSessionFlexmark.renderMarkdown(story.text),
            false
        )
    }

}