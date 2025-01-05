package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory

open class ScientificAnalysisApplicationApp(
    applicationName: String = "ScientificAnalysisApplication",
    path: String = "/",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
) {

    data class Settings(
        val model: ChatModel = OpenAIModels.GPT35Turbo,
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
            ScientificAnalysisApplicationAgent(
                ui = ui,
              api = api,
            ).scientificAnalysisApplication(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScientificAnalysisApplicationApp::class.java)
    }

}