package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ApiModel.Role
import com.simiacryptus.jopenai.ClientUtil.toContentList
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.meta.MetaAgentActors.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.camelCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.imports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.pascalCase
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.sortCode
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.stripImports
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.upperSnakeCase
import com.simiacryptus.skyenet.core.actors.CodingActor.FailedToImplementException
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
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
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  var autoEvaluate: Boolean = true,
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

  private val highLevelDesigner by lazy { getActor(ActorType.HIGH_LEVEL) as SimpleActor }
  @Suppress("UNCHECKED_CAST")
  private val detailDesigner by lazy { getActor(ActorType.DETAIL) as ParsedActor<AgentFlowDesign> }
  @Suppress("UNCHECKED_CAST")
  private val actorDesigner by lazy { getActor(ActorType.ACTORS) as ParsedActor<AgentActorDesign> }
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

      val classBaseName = design.obj.name?.pascalCase() ?: "MyAgent"

      val actorInits = design.obj.actors?.joinToString("\n") { actor ->
        """private val ${actor.name.camelCase()} by lazy { getActor(${classBaseName}Actors.ActorType.${actor.name.upperSnakeCase()}) as ${
          when (actor.type.lowercase()) {
            "simple" -> "SimpleActor"
            "parsed" -> "ParsedActor<${actor.resultType}>"
            "coding" -> "CodingActor"
            "image" -> "ImageActor"
            else -> throw IllegalArgumentException("Unknown actor type: ${actor.type}")
          }
        } }"""
      } ?: ""

      val actorMapEntries = design.obj.actors?.joinToString("\n") { actor ->
        """ActorType.${actor.name?.upperSnakeCase()} to ${actor.name?.camelCase()},"""
      } ?: ""

      val actorEnumDefs = design.obj.actors?.joinToString("\n") { actor ->
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
        |    applicationName: String = "${design.obj.name}",
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
        |            ).${design.obj.name?.camelCase()}(userMessage)
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
        |import com.simiacryptus.skyenet.core.platform.file.DataStorage
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

      agentCode = design.obj.actors?.map { it.resultType }?.filterNotNull()?.fold(agentCode)
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

  private fun initialDesign(input: String): ParsedResponse<AgentDesign> {
    val highLevelDesign = iterate(input, highLevelDesigner, { listOf(it) }, api, ui)
    val flowDesign = iterate(highLevelDesign, detailDesigner, { listOf(it) }, api, ui)
    val actorDesignParsedResponse: ParsedResponse<AgentActorDesign> = iterate(flowDesign.text, actorDesigner, { listOf(it) }, api, ui)
    return object : ParsedResponse<AgentDesign>(AgentDesign::class.java) {
      override val text get() = flowDesign.text  + "\n" + actorDesignParsedResponse.text
      override val obj get() = AgentDesign(
        name = flowDesign.obj.name,
        description = flowDesign.obj.description,
        mainInput = flowDesign.obj.mainInput,
        logicFlow = flowDesign.obj.logicFlow,
        actors = actorDesignParsedResponse.obj.actors,
      )
    }
  }

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
          design.text,
          "Implement `fun ${design.obj.name?.camelCase()}(${
            listOf(design.obj.mainInput!!)
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
  ) = design.obj.actors?.map { actorDesign ->
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
        design.text,
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
  ): Map<String, String> {
    val flowImpls = HashMap<String, String>()
    design.obj.logicFlow?.items?.forEach { logicFlowItem ->
      val message = ui.newTask()
      try {
        message.header("Logic Flow: ${logicFlowItem.name}")
        val code = try {
          flowStepDesigner.answer(
            CodingActor.CodeRequest(
              messages = listOf<String>(
                userMessage,
                design.text,
                "Implement `fun ${(logicFlowItem.name!!).camelCase()}(${
                  logicFlowItem.inputs?.joinToString<DataInfo>(", ") { (it.name ?: "") + " : " + (it.type ?: "") } ?: ""
                })`"
              ),
              autoEvaluate = autoEvaluate,
              codePrefix = (actorImpls.values + flowImpls.values)
                .joinToString("\n\n") { it.trimIndent() }.sortCode()
            ), api = api
          ).getCode()
        } catch (e: FailedToImplementException) {
          message.error(e)
          autoEvaluate = false
          e.code
        }
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
        flowImpls[logicFlowItem.name!!] = code!!
      } catch (e: Throwable) {
        message.error(e)
        throw e
      }
    }
    return flowImpls
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(MetaAgentAgent::class.java)

    fun <T : Any> iterate(
      ui: ApplicationInterface,
      userMessage: String,
      initialResponse: (String) -> T,
      reviseResponse: (String, T, String) -> T,
    ): T {
      val task = ui.newTask()
      val design = try {
        task.echo(renderMarkdown(userMessage))
        var design = initialResponse(userMessage)
        task.add(renderMarkdown(design.toString()))
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
          task.add(renderMarkdown(design.toString()))
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
        val d = design
        if(d is ParsedResponse<*>) task.verbose(JsonUtil.toJson(d.obj!!))
        task.complete()
        design
      } catch (e: Throwable) {
        task.error(e)
        throw e
      }
      return design
    }

    fun List<Pair<List<ApiModel.ContentPart>, Role>>.toMessageList(): Array<ApiModel.ChatMessage> =
      this.map { (content, role) ->
        ApiModel.ChatMessage(
          role = role,
          content = content
        )
      }.toTypedArray()

    fun <I:Any,T:Any> iterate(
      input: String,
      actor: BaseActor<I, T>,
      toInput: (String) -> I,
      api: API,
      ui: ApplicationInterface,
    ) = iterate(
      ui = ui,
      userMessage = input,
      initialResponse = { actor.answer(toInput(it), api = api) },
      reviseResponse = { userMessage : String, design : T, userResponse: String ->
        val input = toInput(userMessage)
        actor.answer(
          messages = *actor.chatMessages(input) +
              listOf(
                design.toString().toContentList() to Role.assistant,
                userResponse.toContentList() to Role.user
              ).toMessageList(),
          input = input,
          api = api
        )
      },
    )

  }
}

