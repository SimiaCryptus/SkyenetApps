package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class OutlineApp(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
    val domainName: String,
) : ApplicationBase(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    data class Settings(
        val depth: Int = 0,
        val temperature: Double = 0.3,
        val minTokensForExpansion : Int = 16,
        val verbose: Boolean = false,
        val showProjector: Boolean = true,
        val writeFinalEssay: Boolean = false,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(sessionId: String): T? = Settings() as T

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
    ) {
        try {
            val settings = getSettings<Settings>(sessionId)
            OutlineBuilder(
                api = socket.api,
                verbose = settings?.verbose ?: false,
                sessionDataStorage = sessionDataStorage,
                iterations = settings?.depth ?: 1,
                minSize = settings?.minTokensForExpansion ?: 16,
                writeFinalEssay = settings?.writeFinalEssay ?: false,
                showProjector = settings?.showProjector ?: true,
                temperature = settings?.temperature ?: 0.3,
            ).buildMap(userMessage, session, sessionDiv, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(OutlineApp::class.java)
    }


}