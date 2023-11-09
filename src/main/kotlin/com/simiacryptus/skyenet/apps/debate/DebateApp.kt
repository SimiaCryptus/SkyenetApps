package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

open class DebateApp(
    applicationName: String = "DebateMapper",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            DebateManager(
                api = OpenAIClient(
                    logLevel = Level.DEBUG,
                    auxillaryLogOutputStream = mutableListOf(
                        sessionDataStorage.getSessionDir(sessionId).resolve("openai.log").outputStream().buffered()
                    )
                ),
                verbose = true,
                sessionDataStorage = sessionDataStorage
            ).debate(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}