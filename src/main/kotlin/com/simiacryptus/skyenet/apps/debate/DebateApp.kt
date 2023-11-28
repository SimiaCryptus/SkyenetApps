package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "DebateMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    temperature = temperature,
) {
    override val description: String
        @Language("HTML")
        get() = """
            <div>
            The debate app simulates a debate in order to analyze a topic from a number of perspectives.
            Produced statements are then used to generate a high-dimensional map of the topic space.
            </div>
        """.trimIndent()


    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            DebateAgent(
                api = api,
                dataStorage = dataStorage,
                userId = user,
                session = session,
                ui = ui,
                domainName = domainName
            ).debate(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebateApp::class.java)
    }

}