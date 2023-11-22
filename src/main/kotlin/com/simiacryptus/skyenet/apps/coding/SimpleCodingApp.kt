package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.openai.OpenAIAPI
import com.simiacryptus.skyenet.application.ApplicationServer
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.application.ApplicationInterface
import com.simiacryptus.skyenet.platform.*
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
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
        api: OpenAIAPI
    ) {
        try {
            val sessionMessage = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
            sessionMessage.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""", true)
            val response = actor.answer(userMessage, api = api)
            val canPlay = ApplicationServices.authorizationManager.isAuthorized(
                this::class.java,
                user,
                AuthorizationManager.OperationType.Execute
            )
            val playLink = if(!canPlay) "" else {
                ui.hrefLink("▶", "href-link play-button") {
                    //language=HTML
                    sessionMessage.append("""<div class="response-header">Running...</div>""", true)
                    val result = response.run()
                    //language=HTML
                    sessionMessage.append(
                        """
                        |<div class="response-message">
                        |<pre>${result.resultValue}</pre>
                        |<pre>${result.resultOutput}</pre>
                        |</div>
                        """.trimMargin(), false
                    )
                }
            }
            //language=HTML
            sessionMessage.append("""<div class="response-message">${
                //language=MARKDOWN
                renderMarkdown("""
                |```${actor.interpreter.getLanguage().lowercase(Locale.getDefault())}
                |${response.getCode()}
                |```
                |$playLink
                """.trimMargin().trim())
            }</div>""", false)
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