package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

open class OutlineMapper(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    private val knowledgeManager = OutlineManager(
        api = OpenAIClient(logLevel = Level.DEBUG),
        verbose = false,
        sessionDataStorage = sessionDataStorage
    )

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            knowledgeManager.buildMap(userMessage, session, sessionDiv)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(OutlineMapper::class.java)
    }


}