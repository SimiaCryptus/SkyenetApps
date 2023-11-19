package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.apps.meta.AgentBuilder
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.platform.ApplicationServices
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.platform.AuthorizationManager
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.util.*

open class SimpleCodingApp(
    applicationName: String,
    private val actor: CodingActor,
    temperature: Double = 0.1,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {
    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
    ) {
        try {
            sessionDiv.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""", true)
            val response = actor.answer(userMessage, api = socket.api)
            val canPlay = ApplicationServices.authorizationManager.isAuthorized(
                this::class.java,
                socket.user?.email,
                AuthorizationManager.OperationType.Execute
            )
            val playLink = if(!canPlay) "" else {
                session.htmlTools(sessionDiv.divID()).hrefLink("▶", "href-link play-button") {
                    //language=HTML
                    sessionDiv.append("""<div class="response-header">Running...</div>""", true)
                    val result = response.run()
                    //language=HTML
                    sessionDiv.append(
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
            sessionDiv.append("""<div class="response-message">${
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
            session.send("""${SessionBase.randomID()},<div class="error">${renderMarkdown(e.message ?: "")}</div>""")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimpleCodingApp::class.java)
    }
}