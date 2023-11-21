package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.openai.OpenAIAPI
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.*
import org.slf4j.LoggerFactory

open class OutlineApp(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    val domainName: String,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature,
) {

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
        api: OpenAIAPI
    ) {
        val settings = getSettings<Settings>(session, user)
        OutlineBuilder(
            api = api,
            dataStorage = dataStorage,
            iterations = settings?.depth ?: 1,
            temperature = settings?.temperature ?: 0.3,
            minSize = settings?.minTokensForExpansion ?: 16,
            writeFinalEssay = settings?.writeFinalEssay ?: false,
            showProjector = settings?.showProjector ?: true,
            userId = user,
            session = session,
        ).buildMap(userMessage, ui, domainName)
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineApp::class.java)
    }


}