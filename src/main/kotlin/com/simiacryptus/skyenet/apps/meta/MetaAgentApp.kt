package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.1,
) : ApplicationServer(
  applicationName = applicationName,
) {
    override val description: String
        get() = "<div>${
            renderMarkdown("""
                Welcome to the MetaAgentAgent, an innovative tool designed to streamline the process of creating custom AI agents. This powerful system leverages the capabilities of OpenAI's language models to assist you in designing and implementing your very own AI agent tailored to your specific needs and preferences.

                Here's what you can expect from the MetaAgentAgent:

                1. **Easy-to-Use Interface**: The MetaAgentAgent provides a user-friendly interface that guides you through the process of defining the behavior and capabilities of your AI agent. Whether you're a seasoned developer or new to AI, you'll find the experience intuitive and straightforward.

                2. **Customizable Agent Design**: With MetaAgentAgent, you have the freedom to specify the characteristics and functions of your agent. You can define its name, the types of interactions it will handle, and the logic it follows to perform tasks.

                3. **Automated Code Generation**: Once you've outlined your agent's design, MetaAgentAgent takes over to automatically generate the underlying code. It crafts everything from the main function to the individual actor implementations, ensuring that your agent is ready to perform as envisioned.

                4. **Interactive Feedback Loop**: As you interact with the system, MetaAgentAgent provides real-time feedback and suggestions to refine your agent's design. You can iterate on your ideas, making adjustments until you're satisfied with the outcome.

                5. **Seamless Integration**: The generated code is neatly organized and includes all necessary imports, making it easy to integrate your new AI agent into existing projects or platforms.

                6. **Transparent Process**: Throughout the creation process, you'll have a clear view of the code being generated. You can learn from the system's decisions and even modify the code to better suit your project's requirements.

                7. **Support for Multiple Actor Types**: Whether your agent needs to handle simple text responses, parse complex data, or work with images, MetaAgentAgent provides support for a variety of actor types to ensure your agent can handle diverse tasks.

                8. **Ready to Deploy**: The final output is a complete, deployable Kotlin application. You can take the generated code and run your agent on your preferred platform with minimal setup required.

                Get started with MetaAgentAgent today and bring your custom AI agent to life with ease! Whether you're looking to automate customer service, streamline data analysis, or create an interactive chatbot, MetaAgentAgent is here to help you make it happen.
            """.trimIndent())
        }</div>"

    data class Settings(
        val model: ChatModels = ChatModels.GPT4Turbo,
        val validateCode: Boolean = true,
        val temperature: Double = 0.2,
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