package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.webui.PersistentSessionBase
import com.simiacryptus.skyenet.webui.SessionDiv
import com.simiacryptus.skyenet.webui.MacroChat
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

open class OutlineApp(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : MacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    data class Settings(
        val depth: Int = 0,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(sessionId: String): T? = Settings() as T

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            OutlineBuilder(
                api = OpenAIClient(
                    logLevel = Level.DEBUG,
                    auxillaryLogOutputStream = mutableListOf(
                        sessionDataStorage.getSessionDir(sessionId).resolve("openai.log").outputStream().buffered()
                    )
                ),
                verbose = false,
                sessionDataStorage = sessionDataStorage,
                iterations = getSettings<Settings>(sessionId)?.depth ?: 1,
            ).buildMap(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(OutlineApp::class.java)
    }


}