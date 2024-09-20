package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory

open class CreativeWritingAssistantApp(
    applicationName: String = "CreativeWritingAssistant",
    path: String = "/",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
) {

    data class Settings(
        val model: ChatModels = OpenAIModels.GPT35Turbo,
        val temperature: Double = 0.1,
    )

    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            val settings = getSettings<Settings>(session, user)
            CreativeWritingAssistantAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                ui = ui,
                api = api,
            ).creativeWritingAssistant(
                CreativeWritingAssistantAgent.UserInput(
                    CreativeWritingAssistantAgent.InputType.TEXT,
                    userMessage
                )
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CreativeWritingAssistantApp::class.java)
    }

}