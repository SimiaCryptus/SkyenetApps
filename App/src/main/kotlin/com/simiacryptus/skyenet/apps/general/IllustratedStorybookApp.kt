package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

open class IllustratedStorybookApp(
  applicationName: String = "Illustrated Storybook Generator v1.0",
  domainName: String
) : ApplicationServer(
  applicationName = applicationName,
) {

  override val description: String
    @Language("HTML")
    get() = "<div>" + MarkdownUtil.renderMarkdown(
      """
        Welcome to the Illustrated Storybook Generator, an app designed to help you create illustrated storybooks with ease.
        
        Enter a prompt, and the Illustrated Storybook Generator will generate a storybook for you, complete with images and text!
      """.trimIndent()
    ) + "</div>"

  data class Settings(
    val model: ChatModels = ChatModels.GPT4Turbo,
    val temperature: Double = 0.5,
    val imageModel: ImageModels = ImageModels.DallE3
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
    try {
      val settings = getSettings<Settings>(session, user)
      IllustratedStorybookAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        ui = ui,
        api = api,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
        imageModel = settings?.imageModel ?: ImageModels.DallE2,
      ).inputHandler(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(IllustratedStorybookApp::class.java)
  }

}