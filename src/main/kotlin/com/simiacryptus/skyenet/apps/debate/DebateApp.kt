package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.skyenet.sessions.PersistentSessionBase
import com.simiacryptus.skyenet.sessions.SessionDiv
import com.simiacryptus.skyenet.sessions.ChatApplicationBase
import com.simiacryptus.skyenet.sessions.MessageWebSocket
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "DebateMapper",
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
            DebateManager(
                api = socket.api,
                verbose = true,
                sessionDataStorage = sessionDataStorage
            ).debate(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}