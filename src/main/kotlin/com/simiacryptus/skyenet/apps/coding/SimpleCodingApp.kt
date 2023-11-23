package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.util.*

open class SimpleCodingApp(
    applicationName: String,
    private val actor: CodingActor,
    temperature: Double = 0.1,
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
            val message = ui.newMessage()
            message.echo(renderMarkdown(userMessage))
            val response = actor.answer(userMessage, api = api)
            val canPlay = ApplicationServices.authorizationManager.isAuthorized(
                this::class.java,
                user,
                AuthorizationManager.OperationType.Execute
            )
            val playLink = if (!canPlay) "" else {
                ui.hrefLink("▶", "href-link play-button") {
                    //language=HTML
                    message.header("Running...")
                    val result = response.run()
                    //language=HTML
                    message.complete(
                        """
                        |<pre>${result.resultValue}</pre>
                        |<pre>${result.resultOutput}</pre>
                        """.trimMargin()
                    )
                }
            }
            //language=HTML
            message.complete(
                renderMarkdown(
                    """
                    |```${actor.interpreter.getLanguage().lowercase(Locale.getDefault())}
                    |${response.getCode()}
                    |```
                    |$playLink
                    """.trimMargin().trim()
                )
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
            //language=HTML
            ui.newMessage().error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimpleCodingApp::class.java)
    }
}