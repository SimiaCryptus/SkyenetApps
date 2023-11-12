package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.sessions.PersistentSessionBase
import com.simiacryptus.skyenet.sessions.SessionDiv
import com.simiacryptus.skyenet.sessions.ChatApplicationBase
import com.simiacryptus.skyenet.sessions.MessageWebSocket
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : ChatApplicationBase(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionDiv: SessionDiv,
        socket: MessageWebSocket
    ) {
        try {
            AgentBuilder(
                api = socket.api,
                verbose = true,
                sessionDataStorage = sessionDataStorage
            ).buildAgent(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(MetaAgentApp::class.java)
    }


}