package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.interpreter.ProcessInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import java.io.File

class BashCodingApp : ApplicationServer(
    applicationName = "Bash Coding Assistant v1.1",
    path = "/bash",
) {

    data class Settings(
        val env: Map<String, String> = mapOf(),
        val workingDir: String = ".",
        val language: String = "bash",
        val command: List<String> = listOf("bash"),
        val model: ChatModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.1,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings<Settings>(session, user)
        CodingAgent(
            api = api,
            dataStorage = dataStorage,
            session = session,
            user = user,
            ui = ui,
            interpreter = ProcessInterpreter::class,
            symbols = mapOf(
                "env" to (settings?.env ?: mapOf()),
                "workingDir" to File(settings?.workingDir ?: ".").absolutePath,
                "language" to (settings?.language ?: "bash"),
                "command" to (settings?.command ?: listOf("bash")),
            ),
            temperature = (settings?.temperature ?: 0.1),
            model = (settings?.model!!),
            mainTask = ui.newTask()
        ).start(
            userMessage = userMessage,
        )
    }
}