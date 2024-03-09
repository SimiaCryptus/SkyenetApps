package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory


open class PresentationDesignerApp(
  applicationName: String = "Presentation Generator v1.3",
) : ApplicationServer(
  applicationName = applicationName,
  path = "/presentation",
) {

  override val description: String
    @Language("HTML")
    get() = "<div>" + MarkdownUtil.renderMarkdown(
      """
        Welcome to the Presentation Designer, an app designed to help you create presentations with ease.
        
        Enter a prompt, and the Presentation Designer will generate a presentation for you, complete with slides, images, and speaking notes!                  
      """.trimIndent()
    ) + "</div>"

  data class Settings(
    val model: OpenAITextModel = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
    val voice : String? = "alloy",
    val voiceSpeed : Double? = 1.0,
    val budget : Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
      PresentationDesignerAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        ui = ui,
        api = api,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
        voice = settings?.voice ?: "alloy",
        voiceSpeed = settings?.voiceSpeed ?: 1.0,
      ).main(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(PresentationDesignerApp::class.java)
  }

}
