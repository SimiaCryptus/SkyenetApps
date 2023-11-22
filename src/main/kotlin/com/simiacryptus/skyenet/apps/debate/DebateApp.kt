package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.application.ApplicationInterface
import com.simiacryptus.skyenet.application.ApplicationServer
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "DebateMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    temperature = temperature,
) {

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            DebateBuilder(
                api = api,
                dataStorage = dataStorage,
                userId = user,
                session = session
            ).debate(userMessage, ui, domainName)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}