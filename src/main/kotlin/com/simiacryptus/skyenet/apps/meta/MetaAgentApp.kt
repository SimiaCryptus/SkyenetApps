package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.session.ApplicationSocketManager
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.3,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {

    data class Settings(
        val model: ChatModels = ChatModels.GPT4Turbo,
        val autoEvaluate: Boolean = false,
        val temperature: Double = 0.3,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        socketManager: ApplicationSocketManager.ApplicationInterface,
        sessionMessage: SessionMessage,
        socket: ChatSocket
    ) {
        try {
            val settings = getSettings<Settings>(session, user)
            AgentBuilder(
                userId = user,
                sessionId = session,
                userMessage = userMessage,
                api = socket.api,
                dataStorage = dataStorage,
                session = socketManager,
                sessionMessage = sessionMessage,
                model = settings?.model ?: ChatModels.GPT35Turbo,
                autoEvaluate = settings?.autoEvaluate ?: true,
                temperature = settings?.temperature ?: 0.3,
            ).buildAgent()
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetaAgentApp::class.java)
    }


}