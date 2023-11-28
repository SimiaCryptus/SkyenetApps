package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
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
        get() = """
            <div>
            The outline app provides a recursive expansion method to expand a topic.
            Produced statements are then used to generate a high-dimensional map of the topic space.
            </div>
        """.trimIndent()

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