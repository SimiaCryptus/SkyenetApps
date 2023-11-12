package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.webui.PersistentSessionBase
import com.simiacryptus.skyenet.webui.SessionDiv
import com.simiacryptus.skyenet.webui.MacroChat
import com.simiacryptus.skyenet.webui.MessageWebSocket
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

open class MetaAgentApp(
    applicationName: String = "MetaAgent",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : MacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
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