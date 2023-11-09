package com.simiacryptus.skyenet.actors

import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import org.slf4j.LoggerFactory

open class CodingActorTestApp(
    val actor: CodingActor,
    applicationName: String = "CodingActorTest_"+actor.javaClass.simpleName,
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            val moderatorResponse = actor.answer(userMessage)
            sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(moderatorResponse)}</div>""", false)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(CodingActorTestApp::class.java)
    }

}