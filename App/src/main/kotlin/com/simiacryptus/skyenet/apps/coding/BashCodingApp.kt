package com.simiacryptus.skyenet.apps.coding

import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.interpreter.BashInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import java.io.File

class BashCodingApp(
  val env: Map<String, String> = mapOf(
    //"PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games:/snap/bin"
  ),
  val workingDir: File
) : ApplicationServer(
  "Bash Coding Assistant") {

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    object : CodingAgent<BashInterpreter>(
      api = api,
      dataStorage = dataStorage,
      session = session,
      user = user,
      ui = ui,
      interpreter = BashInterpreter::class,
      symbols =   mapOf(
        "env" to env,
        "workingDir" to workingDir.absolutePath
      ),
      temperature = 0.1,
    ){

    }.start(
      userMessage = userMessage,
    )
  }
}