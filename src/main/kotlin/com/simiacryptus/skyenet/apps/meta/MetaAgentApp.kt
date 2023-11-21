package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.ApplicationSession
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.platform.SessionID
import com.simiacryptus.skyenet.platform.UserInfo
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
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(sessionId: SessionID): T? = Settings() as T

    override fun processMessage(
        sessionId: SessionID,
        userId: UserInfo?,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
    ) {
        try {
            val settings = getSettings<Settings>(sessionId, userId)
            AgentBuilder(
                userId = userId,
                sessionId = sessionId,
                userMessage = userMessage,
                api = socket.api,
                dataStorage = dataStorage,
                session = session,
                sessionDiv = sessionDiv,
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