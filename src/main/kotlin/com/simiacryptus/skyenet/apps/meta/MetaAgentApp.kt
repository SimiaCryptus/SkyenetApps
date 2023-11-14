package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : ApplicationBase(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
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