package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language

open class OutlineApp(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    temperature = temperature,
) {
    override val description: String
        @Language("HTML")
        get() = ("<div>" + renderMarkdown("""
              ### **Welcome to the Outline Agent!**
    
              Are you struggling to organize your thoughts or structure your writing? Say hello to the Outline Agent, your personal assistant for creating detailed outlines and essays!
    
              The Outline Agent is a sophisticated tool designed to help you turn a single idea into a well-structured document. Whether you're a student, a researcher, or a professional writer, this agent can streamline your writing process and enhance the clarity of your work.
    
              Here's what the Outline Agent can do for you:
    
              1. **Generate Initial Outlines**: Just provide your main idea or topic, and the Outline Agent will create an initial outline to kickstart your writing project.
              2. **Iterative Expansion**: The agent can iteratively expand on each section of your outline, adding depth and detail to your initial framework.
              3. **Finalize Your Essay**: Once your outline is fully expanded, the agent can compile it into a final essay, ensuring that your document is coherent and comprehensive.
              4. **Visualize Ideas**: If you're a visual learner, you'll love the Embedding Projector feature. It allows you to see a visual representation of your ideas and how they relate to each other.
              5. **Customizable Experience**: You can set the number of iterations for expanding your outline, the minimum size for sections, and whether you want to see the final essay or the projector visualization.
              6. **Interactive and User-Friendly**: With a user-friendly interface, the Outline Agent makes it easy to interact with the system, receive updates on the progress, and access your documents.
              7. **Safe and Secure**: Your data is stored securely, and you can access your session's directory to retrieve all your generated outlines and essays.
    
              The Outline Agent is powered by advanced AI technology, ensuring that your outlines are not only structured but also creative and engaging. Get ready to transform your ideas into beautifully crafted documents with ease!
    
              Start your journey to better writing today with the Outline Agent! 📝✨
          """.trimIndent()) + "</div>")

    data class Settings(
        val depth: Int = 0,
        val temperature: Double = 0.3,
        val minTokensForExpansion : Int = 16,
        val showProjector: Boolean = true,
        val writeFinalEssay: Boolean = false,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings<Settings>(session, user)
        OutlineAgent(
            api = api,
            dataStorage = dataStorage,
            iterations = settings?.depth ?: 1,
            temperature = settings?.temperature ?: 0.3,
            minSize = settings?.minTokensForExpansion ?: 16,
            writeFinalEssay = settings?.writeFinalEssay ?: false,
            showProjector = settings?.showProjector ?: true,
            user = user,
            session = session,
            userMessage = userMessage,
            ui = ui,
            domainName = domainName,
        ).buildMap()
    }
}