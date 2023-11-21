package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.ApplicationSession
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.platform.SessionID
import com.simiacryptus.skyenet.platform.UserInfo
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "DebateMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: SessionID,
        userId: UserInfo?,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
    ) {
        try {
            DebateBuilder(
                api = socket.api,
                dataStorage = dataStorage,
                userId = userId,
                sessionId = sessionId
            ).debate(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}