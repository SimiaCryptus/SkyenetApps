package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "Meta-Agent-Agent v1.0",
    temperature: Double = 0.1,
) : ApplicationServer(
  applicationName = applicationName,
    path = "/meta_agent",
) {
    override val description: String
        @Language("Markdown")
        get() = "<div>${
            
            renderMarkdown("""
                **It's agents all the way down!**
                Welcome to the MetaAgentAgent, an innovative tool designed to streamline the process of creating custom AI agents. 
                This powerful system leverages the capabilities of OpenAI's language models to assist you in designing and implementing your very own AI agent tailored to your specific needs and preferences.
                
                Here's how it works:
                1. **Provide a Prompt**: Describe the purpose of your agent.
                2. **High Level Design**: A multi-step high-level design process will guide you through the creation of your agent. During each phase, you can provide feedback and iterate. When you're satisfied with the design, you can move on to the next step.
                3. **Implementation**: The MetaAgentAgent will generate the code for your agent, which you can then download and tailor to your needs.
                
                Get started with MetaAgentAgent today and bring your custom AI agent to life with ease! 
                Whether you're looking to automate customer service, streamline data analysis, or create an interactive chatbot, MetaAgentAgent is here to help you make it happen.
            """.trimIndent())
        }</div>"

    data class Settings(
        val model: ChatModels = ChatModels.GPT4Turbo,
        val validateCode: Boolean = true,
        val temperature: Double = 0.2,
        val budget : Double = 2.0,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

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
            MetaAgentAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: ChatModels.GPT35Turbo,
                autoEvaluate = settings?.validateCode ?: true,
                temperature = settings?.temperature ?: 0.3,
            ).buildAgent(userMessage = userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetaAgentApp::class.java)
    }

}