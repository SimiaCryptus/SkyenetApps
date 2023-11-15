package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class OutlineApp(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {

    data class Settings(
        val depth: Int = 0,
        val temperature: Double = 0.3,
        val minTokensForExpansion : Int = 16,
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
        val settings = getSettings<Settings>(sessionId, session.userId)
        OutlineBuilder(
            api = socket.api,
            dataStorage = dataStorage,
            iterations = settings?.depth ?: 1,
            temperature = settings?.temperature ?: 0.3,
            minSize = settings?.minTokensForExpansion ?: 16,
            writeFinalEssay = settings?.writeFinalEssay ?: false,
            showProjector = settings?.showProjector ?: true,
        ).buildMap(userMessage, session, sessionDiv, domainName)
    }

    companion object {
        val log = LoggerFactory.getLogger(OutlineApp::class.java)
    }


}