package com.simiacryptus.skyenet.apps.debate

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

open class DebateApp(
    applicationName: String = "DebateMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationServer(
  applicationName = applicationName,
) {
    override val description: String
        @Language("HTML")
        get() = "<div>" + MarkdownUtil.renderMarkdown(
            """
              Welcome to the Debate Agent, an interactive platform designed to engage users in thought-provoking debates using advanced AI technology. Our Debate Agent leverages the power of machine learning to simulate a dynamic debating environment, where users can pose questions and receive articulate responses from virtual debaters.

              Here's what you can expect from the Debate Agent:

              * **Real-time Interaction**: Engage in debates by submitting your questions or statements, and watch as the AI-powered moderator and debaters craft their responses, providing you with a rich, conversational experience.
              * **Diverse Perspectives**: Encounter a range of virtual debaters, each with unique characteristics and viewpoints, ensuring a multifaceted discussion that challenges your thinking and broadens your perspective.
              * **Visual Insights**: Explore the intricate landscape of debate topics and responses through our Embedding Projector, a feature that visually maps out the relationships between different arguments and ideas.
              * **User-Friendly Interface**: Navigate the platform with ease, thanks to a straightforward and accessible Application Interface that guides you through the debate process.
              * **Personalized Experience**: Tailor the debate to your interests by posing questions that matter to you, and watch as the AI adapts to the context of the discussion, providing relevant and engaging content.

              Whether you're a seasoned debater looking to hone your skills or a curious mind eager to explore new ideas, the Debate Agent is the perfect companion for an enriching and intellectually stimulating experience. Join the debate today and let your voice be heard!
          """.trimIndent()
        ) + "</div>"

    override fun userMessage(
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