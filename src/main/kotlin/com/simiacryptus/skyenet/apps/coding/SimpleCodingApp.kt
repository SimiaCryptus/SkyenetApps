package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
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
            message.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""")
            val response = actor.answer(userMessage, api = api)
            val canPlay = ApplicationServices.authorizationManager.isAuthorized(
                this::class.java,
                user,
                AuthorizationManager.OperationType.Execute
            )
            val playLink = if (!canPlay) "" else {
                ui.hrefLink("▶", "href-link play-button") {
                    //language=HTML
                    message.append("""<div class="response-header">Running...</div>""")
                    val result = response.run()
                    //language=HTML
                    message.complete(
                        """
                        |<div class="response-message">
                        |<pre>${result.resultValue}</pre>
                        |<pre>${result.resultOutput}</pre>
                        |</div>
                        """.trimMargin()
                    )
                }
            }
            //language=HTML
            message.complete(
                """<div class="response-message">${
                    //language=MARKDOWN
                    renderMarkdown(
                        """
                |```${actor.interpreter.getLanguage().lowercase(Locale.getDefault())}
                |${response.getCode()}
                |```
                |$playLink
                """.trimMargin().trim()
                    )
                }</div>"""
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
            //language=HTML
            ui.send("""${SocketManagerBase.randomID()},<div class="error">${renderMarkdown(e.message ?: "")}</div>""")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimpleCodingApp::class.java)
    }
}