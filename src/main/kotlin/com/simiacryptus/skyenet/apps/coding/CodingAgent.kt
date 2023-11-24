package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.CodingActor.CodeResult
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionMessage
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

class CodingAgent(
    val api: API,
    dataStorage: DataStorage,
    session: Session,
    user: User?,
    val ui: ApplicationInterface,
    val interpreter: KClass<KotlinInterpreter>,
    val symbols: Map<String, Any>,
) : ActorSystem<CodingAgent.ActorTypes>(
    actorMap(interpreter, symbols), dataStorage, user, session
) {
    val actor by lazy { getActor(ActorTypes.CodingActor) as CodingActor }

    enum class ActorTypes {
        CodingActor
    }

    fun start(
        userMessage: String,
    ) {
        val message = ui.newMessage()
        try {
            message.echo(MarkdownUtil.renderMarkdown(userMessage))
            val response = actor.answer(userMessage, api = api)
            displayCode(user, ui, message, response, userMessage, api)
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(e)
        }
    }

    private fun displayCode(
        user: User?,
        ui: ApplicationInterface,
        message: SessionMessage,
        response: CodeResult,
        userMessage: String,
        api: API
    ) {
        try {
            message.add(
                MarkdownUtil.renderMarkdown(
                    //language=Markdown
                    """
                |```${actor.interpreter.getLanguage().lowercase(Locale.getDefault())}
                |${response.getCode()}
                |```
                """.trimMargin().trim()
                )
            )

            val canPlay = ApplicationServices.authorizationManager.isAuthorized(
                this::class.java,
                user,
                AuthorizationManager.OperationType.Execute
            )
            val playLink = message.add(if (!canPlay) "" else {
                ui.hrefLink("▶", "href-link play-button") {
                    val header = message.header("Running...")
                    try {
                        val result = response.run()
                        header?.clear()
                        message.header("Result")
                        message.add(result.resultValue, tag = "pre")
                        message.header("Output")
                        message.add(result.resultOutput, tag = "pre")
                        message.complete()
                    } catch (e: Throwable) {
                        log.warn("Error", e)
                        message.error(e)
                    }
                }
            })

            var formHandle: StringBuilder? = null
            formHandle = message.add(ui.textInput { feedback ->
                try {
                    formHandle?.clear()
                    playLink?.clear()
                    message.echo(MarkdownUtil.renderMarkdown(feedback))
                    val revisedCode = actor.answer(userMessage, response.getCode(), feedback, api = api)
                    displayCode(user, ui, message, revisedCode, userMessage, api)
                } catch (e: Throwable) {
                    log.warn("Error", e)
                    message.error(e)
                }
            })

            message.complete()
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CodingAgent::class.java)

        fun actorMap(interpreterKClass: KClass<KotlinInterpreter>, symbols: Map<String, Any>) = mapOf(
            ActorTypes.CodingActor to CodingActor(interpreterKClass, symbols = symbols)
        )
    }

}