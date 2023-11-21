package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIAPI
import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.1,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {

    data class Settings(
        val model: ChatModels = ChatModels.GPT4Turbo,
        val autoEvaluate: Boolean = false,
        val temperature: Double = 0.1,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: OpenAIAPI
    ) {
        try {
            val settings = getSettings<Settings>(session, user)
            AgentBuilder(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: ChatModels.GPT35Turbo,
                autoEvaluate = settings?.autoEvaluate ?: true,
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