package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language

open class OutlineApp(
    applicationName: String = "Outline Expansion Concept Map v1.0",
    val domainName: String,
) : ApplicationServer(
  applicationName = applicationName,
    path = "/idea_mapper",
) {
    override val description: String
        @Language("HTML")
        get() = ("<div>" + renderMarkdown("""
          The Outline Agent is an AI-powered tool for exploring concepts via outline creation and expansion.
          
          Here's how it works:
          
          1. **Generate Initial Outline**: Provide your main idea or topic, and the Outline Agent will create an initial outline.
          2. **Iterative Expansion**: The agent then expands on each section of your outline, adding depth and detail.
          3. **Construct Final Outline**: Once your outline is fully expanded, the agent can compile it into a single outline. This presents the information in a clear and concise manner, making it easy to review.
          4. **Visualize Embeddings**: Each section of your outline is represented as a vector in a high-dimensional space. The Outline Agent uses an Embedding Projector to visualize these vectors, allowing you to explore the relationships between different ideas and concepts.
          5. **Customizable Experience**: You can set the number of iterations and the model used for each to control the depth and price, making it possible to generate sizable outputs.
          
          Start your journey into concept space today with the Outline Agent! 📝✨
          """.trimIndent()) + "</div>")

    data class Settings(
        val models: List<ChatModels> = listOf(
            ChatModels.GPT35Turbo
        ),
        val temperature: Double = 0.3,
        val minTokensForExpansion : Int = 16,
        val showProjector: Boolean = true,
        val writeFinalEssay: Boolean = false,
        val budget : Double = 2.0,
    )
    override val settingsClass: Class<*> get() = Settings::class.java
    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings<Settings>(session, user)!!
        (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
        OutlineAgent(
            api = api,
            dataStorage = dataStorage,
            models = settings.models,
            temperature = settings.temperature,
            minSize = settings.minTokensForExpansion,
            writeFinalEssay = settings.writeFinalEssay,
            showProjector = settings.showProjector,
            user = user,
            session = session,
            userMessage = userMessage,
            ui = ui,
            domainName = domainName,
        ).buildMap()
    }
}