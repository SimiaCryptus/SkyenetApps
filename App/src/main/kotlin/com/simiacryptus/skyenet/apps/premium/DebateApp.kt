package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

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