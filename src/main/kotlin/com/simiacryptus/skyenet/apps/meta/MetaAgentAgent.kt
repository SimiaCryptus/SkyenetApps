package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.ClientUtil.toContentList
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.meta.MetaAgentActors.ActorType
import com.simiacryptus.skyenet.apps.meta.MetaAgentActors.AgentDesign
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.camelCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.imports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.pascalCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.sortCode
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.stripImports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.upperSnakeCase
import com.simiacryptus.skyenet.core.actors.CodingActor.FailedToImplementException
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.ParsedResponse
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean


open class MetaAgentAgent(
  user: User?,
  session: Session,
  dataStorage: DataStorage,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  val autoEvaluate: Boolean = true,
  temperature: Double = 0.3,
) : ActorSystem<ActorType>(MetaAgentActors(
  symbols = mapOf(
    "user" to user,
    "session" to session,
    "dataStorage" to dataStorage,
    "ui" to ui,
    "api" to api,
  ).filterValues { null != it }.mapValues { it.value!! },
  model = model,
  temperature = temperature,
).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val initialDesigner by lazy { getActor(ActorType.INITIAL) as ParsedActor<AgentDesign> }
  private val simpleActorDesigner by lazy { getActor(ActorType.SIMPLE) as CodingActor }
  private val imageActorDesigner by lazy { getActor(ActorType.IMAGE) as CodingActor }
  private val parsedActorDesigner by lazy { getActor(ActorType.PARSED) as CodingActor }
  private val codingActorDesigner by lazy { getActor(ActorType.CODING) as CodingActor }
  private val flowStepDesigner by lazy { getActor(ActorType.FLOW_STEP) as CodingActor }

  fun buildAgent(userMessage: String) {
    val design = initialDesign(userMessage)
    val actImpls = implementActors(userMessage, design)
    val flowImpl = getFlowStepCode(userMessage, design, actImpls)
    val mainImpl = getMainFunction(userMessage, design, actImpls, flowImpl)
    buildFinalCode(actImpls, flowImpl, mainImpl, design)
  }

  private fun buildFinalCode(
    actImpls: Map<String, String>,
    flowImpl: Map<String, String>,
    mainImpl: String,
    design: ParsedResponse<AgentDesign>
  ) {
    val task = ui.newTask()
    try {
      task.header("Final Code")

      val imports =
        (actImpls.values + flowImpl.values + listOf(mainImpl)).flatMap { it.imports() }
          .toSortedSet().joinToString("\n")

      val classBaseName = design.getObj().name?.pascalCase() ?: "MyAgent"

      val actorInits = design.getObj().actors?.joinToString("\n") { actor ->
        """private val ${actor.name?.camelCase()} by lazy { getActor(${classBaseName}Actors.ActorType.${actor.name?.upperSnakeCase()}) as ${
          when (actor.type) {
            "simple" -> "SimpleActor"
            "parsed" -> "ParsedActor<${actor.resultType}>"
            "coding" -> "CodingActor"
            "image" -> "ImageActor"
            else -> throw IllegalArgumentException("Unknown actor type: ${actor.type}")
          }
        } }"""
      } ?: ""

      val actorMapEntries = design.getObj().actors?.joinToString("\n") { actor ->
        """ActorType.${actor.name?.upperSnakeCase()} to ${actor.name?.camelCase()},"""
      } ?: ""

      val actorEnumDefs = design.getObj().actors?.joinToString("\n") { actor ->
        """${actor.name?.upperSnakeCase()},"""
      } ?: ""

      @Language("kotlin") val appCode = """
        |import com.simiacryptus.jopenai.API
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.webui.application.ApplicationServer
        |import com.simiacryptus.skyenet.core.platform.Session
        |import com.simiacryptus.skyenet.core.platform.User
        |import com.simiacryptus.skyenet.webui.session.*
        |import org.slf4j.LoggerFactory
        |
        |open class ${classBaseName}App(
        |    applicationName: String = "${design.getObj().name}",
        |    temperature: Double = 0.1,
        |) : ApplicationServer(
        |    applicationName = applicationName,
        |    temperature = temperature,
        |) {
        |
        |    data class Settings(
        |        val model: ChatModels = ChatModels.GPT35Turbo,
        |        val temperature: Double = 0.1,
        |    )
        |    override val settingsClass: Class<*> get() = Settings::class.java
        |    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T
        |
        |    override fun newSession(
        |        session: Session,
        |        user: User?,
        |        userMessage: String,
        |        ui: ApplicationInterface,
        |        api: API
        |    ) {
        |        try {
        |            val settings = getSettings<Settings>(session, user)
        |            ${classBaseName}Agent(
        |                user = user,
        |                session = session,
        |                dataStorage = dataStorage,
        |                api = api,
        |                ui = ui,
        |                model = settings?.model ?: ChatModels.GPT35Turbo,
        |                temperature = settings?.temperature ?: 0.3,
        |            ).${design.getObj().name?.camelCase()}(userMessage)
        |        } catch (e: Throwable) {
        |            log.warn("Error", e)
        |        }
        |    }
        |
        |    companion object {
        |        private val log = LoggerFactory.getLogger(${classBaseName}App::class.java)
        |    }
        |
        |}
        """.trimMargin()

      @Language("kotlin") var agentCode = """
        |import com.simiacryptus.jopenai.API
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.core.actors.ActorSystem
        |import com.simiacryptus.skyenet.core.actors.CodingActor
        |import com.simiacryptus.skyenet.core.actors.ParsedActor
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |import com.simiacryptus.skyenet.core.platform.DataStorage
        |import com.simiacryptus.skyenet.core.platform.Session
        |import com.simiacryptus.skyenet.core.platform.User
        |import com.simiacryptus.skyenet.webui.application.ApplicationInterface
        |
        |open class ${classBaseName}Agent(
        |    user: User?,
        |    session: Session,
        |    dataStorage: DataStorage,
        |    val ui: ApplicationInterface,
        |    val api: API,
        |    model: ChatModels = ChatModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |) : ActorSystem<${classBaseName}Actors.ActorType>(${classBaseName}Actors(
        |    model = model,
        |    temperature = temperature,
        |).actorMap, dataStorage, user, session) {
        |
        |    @Suppress("UNCHECKED_CAST")
        |    ${actorInits.indent("    ")}
        |
        |    ${mainImpl.trimIndent().stripImports().indent("    ")}
        |
        |    ${flowImpl.values.joinToString("\n\n") { flowStep -> flowStep.trimIndent() }.stripImports().indent("    ")}
        |
        |    companion object {
        |        private val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Agent::class.java)
        |
        |    }
        |}
        """.trimMargin()

      agentCode = design.getObj().actors?.map { it.resultType }?.filterNotNull()?.fold(agentCode)
      { code, type -> code.replace(type, "${classBaseName}Actors.$type") } ?: agentCode

      @Language("kotlin") val agentsCode = """
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.core.actors.BaseActor
        |
        |class ${classBaseName}Actors(
        |    val model: ChatModels = ChatModels.GPT4Turbo,
        |    val temperature: Double = 0.3,
        |) {
        |
        |    ${actImpls.values.joinToString("\n\n") { it.trimIndent() }.stripImports().indent("    ")}
        |
        |    enum class ActorType {
        |        ${actorEnumDefs.indent("        ")}
        |    }
        |
        |    val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
        |        ${actorMapEntries.indent("        ")}
        |    )
        |
        |    companion object {
        |        val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Actors::class.java)
        |    }
        |}
        """.trimMargin()

      //language=MARKDOWN
      val code = """
        |```kotlin
        |${listOf(imports, appCode, agentCode, agentsCode).joinToString("\n\n") { it.trimIndent() }.sortCode()}
        |```
        |""".trimMargin()

      //language=HTML
      task.complete(renderMarkdown(code))
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  private fun initialDesign(userMessage: String): ParsedResponse<AgentDesign> = iterate(
    ui = ui,
    userMessage = userMessage,
    initialResponse = { this.initialDesigner.answer(listOf(it), api = this.api) },
    reviseResponse = { userMessage, design, userResponse ->
      this.initialDesigner.answer(
        *(this.initialDesigner.chatMessages(listOf(userMessage)) +
            listOf(
              design.getText().toContentList() to Role.assistant,
              userResponse.toContentList() to Role.user
            ).toMessageList()),
        input = listOf(userMessage),
        api = this.api
      )
    },
  )

  private fun getMainFunction(
    userMessage: String,
    design: ParsedResponse<AgentDesign>,
    actorImpls: Map<String, String>,
    flowStepCode: Map<String, String>
  ): String {
    val task = ui.newTask()
    try {
      task.header("Main Function")
      val codeRequest = CodingActor.CodeRequest(
        messages = listOf(
          userMessage,
          design.getText(),
          "Implement `fun ${design.getObj().name?.camelCase()}(${
            listOf(design.getObj().mainInput!!)
              .joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") }
          })`"
        ),
        codePrefix = (actorImpls.values + flowStepCode.values)
          .joinToString("\n\n") { it.trimIndent() }.sortCode(),
        autoEvaluate = autoEvaluate
      )
      val mainFunction = flowStepDesigner.answer(codeRequest, api = api).getCode()
      task.verbose(
        renderMarkdown(
          """
                        |```kotlin
                        |$mainFunction
                        |```
                        """.trimMargin()
        ), tag = "div"
      )
      task.complete()
      return mainFunction
    } catch (e: FailedToImplementException) {
      task.error(e)
      return e.code ?: throw e
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  private fun implementActors(
    userMessage: String,
    design: ParsedResponse<AgentDesign>,
  ) = design.getObj().actors?.map { actorDesign ->
    val task = ui.newTask()
    try {
      implementActor(task, actorDesign, userMessage, design)
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }?.toMap() ?: mapOf()

  private fun implementActor(
    task: SessionTask,
    actorDesign: MetaAgentActors.ActorDesign,
    userMessage: String,
    design: ParsedResponse<AgentDesign>
  ): Pair<String, String> {
    //language=HTML
    task.header("Actor: ${actorDesign.name}")
    val type = actorDesign.type ?: ""
    val codeRequest = CodingActor.CodeRequest(
      listOf(
        userMessage,
        design.getText(),
        "Implement `val ${(actorDesign.name!!).camelCase()} : ${
          when (type.lowercase()) {
            "simple" -> "SimpleActor"
            "parsed" -> "ParsedActor" + if (actorDesign.resultType != null) "<${actorDesign.resultType}>" else ""
            "coding" -> "CodingActor"
            "image" -> "ImageActor"
            else -> throw IllegalArgumentException("Unknown actor type: $type")
          }
        }`"
      ),
      autoEvaluate = autoEvaluate
    )
    val response = when (type.lowercase()) {
      "simple" -> simpleActorDesigner.answer(codeRequest, api = api)
      "parsed" -> parsedActorDesigner.answer(codeRequest, api = api)
      "coding" -> codingActorDesigner.answer(codeRequest, api = api)
      "image" -> imageActorDesigner.answer(codeRequest, api = api)
      else -> throw IllegalArgumentException("Unknown actor type: $type")
    }
    val code = response.getCode()
    //language=HTML
    task.verbose(
      renderMarkdown(
        """
        |```kotlin
        |$code
        |```
        """.trimMargin()
      ), tag = "div"
    )
    task.complete()
    return actorDesign.name to code
  }

  private fun getFlowStepCode(
    userMessage: String,
    design: ParsedResponse<AgentDesign>,
    actorImpls: Map<String, String>,
  ): Map<String, String> = design.getObj().logicFlow?.items?.map { logicFlowItem ->
    val message = ui.newTask()
    try {
      message.header("Logic Flow: ${logicFlowItem.name}")
      // TODO: Fix logic-actor dependencies: Need to import data structures used by inputs
      //val codePrefix = logicFlowItem.actors?.mapNotNull { actorImpls[it] }?.joinToString("\n\n") ?: ""
      val codePrefix = (actorImpls.values).joinToString("\n\n") { it.trimIndent() }.sortCode()
      val response = flowStepDesigner.answer(
        CodingActor.CodeRequest(
          messages = listOf(
            userMessage,
            design.getText(),
            "Implement `fun ${(logicFlowItem.name!!).camelCase()}(${
              logicFlowItem.inputs?.joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") } ?: ""
            })`"
          ),
          autoEvaluate = autoEvaluate,
          codePrefix = codePrefix
        ), api = api
      )
      val code = response.getCode()
      //language=HTML
      message.verbose(
        renderMarkdown(
          """
                    |```kotlin
                    |$code
                    |```
                    """.trimMargin()
        ), tag = "div"
      )
      message.complete()
      logicFlowItem.name!! to code!!
    } catch (e: FailedToImplementException) {
      message.error(e)
      if (autoEvaluate) {
        message.error("Cannot proceed with code generation")
        throw e
      }
      logicFlowItem.name!! to e.code!!
    } catch (e: Throwable) {
      message.error(e)
      throw e
    }
  }?.toMap() ?: mapOf()

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(MetaAgentAgent::class.java)

    fun <T : Any> iterate(
      ui: ApplicationInterface,
      userMessage: String,
      initialResponse: (String) -> ParsedResponse<T>,
      reviseResponse: (String, ParsedResponse<T>, String) -> ParsedResponse<T>,
    ): ParsedResponse<T> {
      val task = ui.newTask()
      val design = try {
        task.echo(renderMarkdown(userMessage))
        var design = initialResponse(userMessage)
        task.add(renderMarkdown(design.getText()))
        var textInputHandle: StringBuilder? = null
        var acceptHandle: StringBuilder? = null
        val onAccept = Semaphore(0)
        var textInput: String? = null
        var acceptLink: String? = null
        val feedbackGuard = AtomicBoolean(false)
        val acceptGuard = AtomicBoolean(false)
        textInput = ui.textInput { userResponse ->
          if (feedbackGuard.getAndSet(true)) return@textInput
          textInputHandle?.clear()
          acceptHandle?.clear()
          task.echo(renderMarkdown(userResponse))
          design = reviseResponse(userMessage, design, userResponse)
          task.add(renderMarkdown(design.getText()))
          textInputHandle = task.add(textInput!!)
          acceptHandle = task.complete(acceptLink!!)
          feedbackGuard.set(false)
        }
        acceptLink = ui.hrefLink("Accept") {
          if (acceptGuard.getAndSet(true)) return@hrefLink
          textInputHandle?.clear()
          acceptHandle?.clear()
          task.add("")
          onAccept.release()
        }
        textInputHandle = task.add(textInput)
        acceptHandle = task.complete(acceptLink)
        onAccept.acquire()
        task.verbose(JsonUtil.toJson(design.getObj()))
        task.complete()
        design
      } catch (e: Throwable) {
        task.error(e)
        throw e
      }
      return design
    }

  }
}

fun List<Pair<List<ApiModel.ContentPart>, Role>>.toMessageList(): Array<ApiModel.ChatMessage> =
  this.map { (content, role) ->
    ApiModel.ChatMessage(
      role = role,
      content = content
    )
  }.toTypedArray()



