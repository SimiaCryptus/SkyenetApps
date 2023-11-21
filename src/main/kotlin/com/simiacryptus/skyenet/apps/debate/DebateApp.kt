package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.openai.OpenAIAPI
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
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

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: OpenAIAPI
    ) {
        try {
            DebateBuilder(
                api = api,
                dataStorage = dataStorage,
                userId = user,
                session = session
            ).debate(userMessage, ui, sessionMessage, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}