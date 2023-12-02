package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.Interpreter
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.intellij.lang.annotations.Language
import java.util.function.Function
import kotlin.reflect.KClass


class MetaAgentActors(
    private val interpreterClass: KClass<out Interpreter> = KotlinInterpreter::class,
    val symbols: Map<String, Any> = mapOf(),
    val model: ChatModels = ChatModels.GPT4Turbo,
    val temperature: Double = 0.3,
) {

    enum class ActorType {
        HIGH_LEVEL,
        DETAIL,
        SIMPLE,
        IMAGE,
        PARSED,
        CODING,
        FLOW_STEP,
        ACTORS,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.HIGH_LEVEL to highLevelDesigner(),
        ActorType.DETAIL to detailedDesigner(),
        ActorType.ACTORS to actorDesigner(),
        ActorType.SIMPLE to simpleActorDesigner(),
        ActorType.IMAGE to imageActorDesigner(),
        ActorType.PARSED to parsedActorDesigner(),
        ActorType.CODING to codingActorDesigner(),
        ActorType.FLOW_STEP to flowStepDesigner(),
    )

    interface DesignParser : Function<String, AgentDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentDesign
    }

    interface FlowParser : Function<String, AgentFlowDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentFlowDesign
    }

    data class AgentFlowDesign(
        val name: String? = null,
        val description: String? = null,
        val mainInput: DataInfo? = null,
        val logicFlow: LogicFlow? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == logicFlow -> "logicFlow is required"
            null != logicFlow.validate() -> logicFlow.validate()
            else -> null
        }
    }

    data class AgentDesign(
        val name: String? = null,
        val description: String? = null,
        val mainInput: DataInfo? = null,
        val logicFlow: LogicFlow? = null,
        val actors: List<ActorDesign>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == logicFlow -> "logicFlow is required"
            null == actors -> "actors is required"
            actors.isEmpty() -> "actors is required"
            null != logicFlow.validate() -> logicFlow.validate()
            !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }.joinToString("\n")
            else -> null
        }
    }

    interface ActorParser : Function<String, AgentActorDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentActorDesign
    }

    data class AgentActorDesign(
        val actors: List<ActorDesign>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == actors -> "actors is required"
            actors.isEmpty() -> "actors is required"
            !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }.joinToString("\n")
            else -> null
        }
    }

    data class ActorDesign(
        @Description("Java class name of the actor")
        val name: String = "",
        val description: String? = null,
        @Description("simple, parsed, image, or coding")
        val type: String = "",
        @Description("Simple actors: string; Image actors: image; Coding actors: code; Parsed actors: a simple java class name for the data structure")
        val resultClass: String = "",
    ) : ValidatedObject {
        val simpleClassName : String get() = resultClass.split(".").last()
        override fun validate(): String? = when {
            name.isEmpty() -> "name is required"
            name.chars().anyMatch { !Character.isJavaIdentifierPart(it) } -> "name must be a valid java identifier"
            type.isEmpty() -> "type is required"
            type.lowercase().notIn("simple", "parsed", "coding", "image") -> "type must be simple, parsed, coding, or image"
            resultClass.isEmpty() -> "resultType is required"
            resultClass.lowercase().notIn("string", "code", "image") && !validClassName(resultClass) -> "resultType must be string, code, image, or a valid class name"
            else -> null
        }

        private fun validClassName(resultType: String): Boolean {
            return when {
                resultType.isEmpty() -> false
                validClassNamePattern.matches(resultType) -> true
                else -> false
            }
        }

        companion object {
            val validClassNamePattern = "[A-Za-z][a-zA-Z0-9_<>.]{3,}".toRegex()
        }

    }

    data class LogicFlow(
        val items: List<LogicFlowItem>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = items?.map { it.validate() }?.firstOrNull { !it.isNullOrBlank() }
    }

    data class LogicFlowItem(
        val name: String? = null,
        val description: String? = null,
        val actors: List<String>? = null,
        @Description("symbol names of variables/values used as input to this step")
        val inputs: List<DataInfo>? = null,
        @Description("description of the output of this step")
        val output: DataInfo? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            //inputs?.isEmpty() != false && inputs?.isEmpty() != false -> "inputs is required"
            else -> null
        }
    }

    data class DataInfo(
        val name: String? = null,
        val description: String? = null,
        val type: String? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            null == type -> "type is required"
            type.isEmpty() -> "type is required"
            else -> null
        }
    }

    @Language("Markdown")
    fun highLevelDesigner() = SimpleActor(
        model = model,
        prompt = """
            You are a high-level software architect.
            
            Your task is to gather requirements and detail the idea provided by the user query.
            You will propose detailed requirements for inputs, outputs, and logic.
            Your proposed design can be reviewed by the user, who may request changes.
            The system in general will be an AI-based automated assistant using an interactive web-based interface.
        """.trimIndent().trim(),
        temperature = temperature
    )

    @Language("Markdown")
    fun detailedDesigner() = ParsedActor(
        FlowParser::class.java,
        prompt = """
            You are a detailed software designer.
            
            Your task is to expand on the high-level architecture and conceptualize the architecture of an "agent" system that uses gpt "actors" to model a creative process.
            The system should be procedural in its overall structure, with creative steps modeled by gpt actors.
            
            User and system interactions can include:
            1. Threading operations
            2. Message output (html+images) sent to the web interface
            3. User input via text input or link clicks
            4. File storage and retrieval
            5. Additional tools as specified by the user
            
            The design should include:
            1. Details on each individual actor including purpose and description
            2. Pseudocode for the overall logic flow, including threads, loops, and conditionals
        """.trimIndent().trim(),
        model = model,
        temperature = temperature,
        parsingModel = ChatModels.GPT4Turbo
    )


    @Language("Markdown")
    fun actorDesigner() = ParsedActor(
        ActorParser::class.java,
        prompt = """
            You are an AI actor designer.
            
            Your task is to expand on a high-level design with requirements for each actor.
            
            For each actor in the given design, detail:
            
            1. The purpose of the actor
            2. Actor Type, which can be one of:
                1. "Simple" actors work like a chatbot, and simply return the chat model's response to the system and user prompts
                2. "Parsed" actors produce complex data structures as output, which can be used in the application logic
                    * **IMPORTANT**: If the output is a string, use a "simple" actor instead
                3. "Coding" actors are used to invoke tools via dynamically compiled scripts
                4. "Image" actors produce images from a user (and system) prompt.
            3. Required details for each actor type:
                1. Simple and Image actors
                    1. System prompt
                2. Parsed actors
                    1. system prompt
                    2. output data structure 
                        1. java class name
                        2. definition
                3. Coding actors
                    1. defined symbols and functions
                    2. libraries used
        """.trimIndent().trim(),
        model = model,
        temperature = temperature,
        parsingModel = ChatModels.GPT35Turbo
    )

    @Language("Markdown")
    fun simpleActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a "simple" actor that takes part in a larger system.
        |"Simple" actors contain a system directive and can process a list of user messages into a response.
        |
        |For context, here is the constructor signature for SimpleActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.core.actors.SimpleActor
        |import org.intellij.lang.annotations.Language
        |import com.simiacryptus.jopenai.models.ChatModels
        |
        |class SimpleActor(
        |    prompt: String,
        |    name: String? = null,
        |    model: ChatModels = ChatModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt and a name:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.SimpleActor
        |import com.simiacryptus.skyenet.heart.KotlinInterpreter
        |import org.intellij.lang.annotations.Language
        |
        |@Language("Markdown")fun exampleSimpleActor() = SimpleActor(
        |    prompt = ""${'"'}
        |    |You are a writing assistant.
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the SimpleActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    )

    @Language("Markdown")
    fun imageActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a "image" actor that takes part in a larger system.
        |"Image" actors contain a system directive and can process a list of user messages into a response.
        |
        |For context, here is the constructor signature for ImageActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.models.ImageModels
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |
        |class ImageActor(
        |    prompt: String = "Transform the user request into an image generation prompt that the user will like",
        |    name: String? = null,
        |    textModel: ChatModels = ChatModels.GPT35Turbo,
        |    val imageModel: ImageModels = ImageModels.DallE2,
        |    temperature: Double = 0.3,
        |    val width: Int = 1024,
        |    val height: Int = 1024,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt and a name:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.ImageActor
        |
        |fun exampleSimpleActor() = ImageActor(
        |    prompt = ""${'"'}
        |    |You are a writing assistant.
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the ImageActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    )

    @Language("Markdown")
    fun parsedActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "parsed" actor that takes part in a larger system.
        |"Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
        |
        |For context, here is the constructor signature for ParsedActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import java.util.function.Function
        |
        |open class ParsedActor<T:Any>(
        |    val parserClass: Class<out Function<String, T>>,
        |    prompt: String,
        |    val action: String? = null,
        |    model: ChatModels = ChatModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and parsing class:
        |```kotlin
        |import com.simiacryptus.jopenai.describe.Description
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.proxy.ValidatedObject
        |import com.simiacryptus.skyenet.core.actors.ParsedActor
        |import java.util.function.Function
        |
        |interface ExampleParser : Function<String, ExampleResult> {
        |    @Description("Break down the text into a data structure.")
        |    override fun apply(text: String): ExampleResult
        |}
        |
        |data class ExampleResult(
        |    @Description("The name of the example")
        |    val name: String? = null,
        |) : ValidatedObject {
        |    override fun validate() = when {
        |        name.isNullOrBlank() -> "name is required"
        |        else -> null
        |    }
        |}
        |
        |fun exampleParsedActor() = ParsedActor<ExampleResult>(
        |    parserClass = ExampleParser::class.java,
        |    model = ChatModels.GPT4Turbo,
        |    prompt = ""${'"'}
        |            |You are a question answering assistant.
        |            |""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the ParsedActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    )

    @Language("Markdown")
    fun codingActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "script" or "coding" actor that takes part in a larger system.
        |"Script" actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
        |
        |For context, here is the constructor signature for CodingActor class:
        |```kotlin
        |package com.simiacryptus.skyenet.core.actors
        |
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
        |import com.simiacryptus.jopenai.describe.TypeDescriber
        |import com.simiacryptus.skyenet.core.Interpreter
        |import kotlin.reflect.KClass
        |
        |class CodingActor(
        |    val interpreterClass: KClass<out Interpreter>,
        |    val symbols: Map<String, Any> = mapOf(),
        |    val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
        |        "com.simiacryptus",
        |        "com.github.simiacryptus"
        |    ),
        |    name: String? = interpreterClass.simpleName,
        |    val details: String? = null,
        |    model: ChatModels = ChatModels.GPT35Turbo,
        |    val fallbackModel: ChatModels = ChatModels.GPT4Turbo,
        |    temperature: Double = 0.1,
        |    private val fixIterations: Int = 3,
        |    private val fixRetries: Int = 2,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and a standard configuration:
        |```kotlin
        |import com.simiacryptus.skyenet.core.actors.CodingActor
        |import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
        |
        |fun exampleCodingActor() = CodingActor(
        |    interpreterClass = KotlinInterpreter::class,
        |    details = ""${'"'}
        |    |You are a software implementation assistant.
        |    |
        |    |Defined functions:
        |    |* ...
        |    |
        |    |Expected code structure:
        |    |* ...
        |    ""${'"'}.trimMargin().trim(),
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the CodingActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        model = model,
        temperature = temperature,
    )

    @Language("Markdown")
    fun flowStepDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        details = """
        |You are a software implementor.
        |
        |Your task is to implement logic for an "agent" system that uses gpt "actors" to construct a model of a creative process.
        |This "creative process" can be thought of as a cognitive process, an individual's work process, or an organizational process.
        |The idea is that the overall structure is procedural and can be modeled in code, but individual steps are creative and can be modeled with gpt.
        |
        |Actors process inputs in the form of ChatGPT messages (often a single string) but vary in their output.
        |Usage examples of each actor type follows:
        |
        |Simple actors contain a system directive, and simply return the chat model's response to the user query.
        |Simple actors answer queries consisting of a list of strings representing a conversation thread, and respond with a string.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.SimpleActor
        |val answer : String = actor.answer(listOf("This is an example question."), api = api)
        |log.info("Answer: " + answer)
        |```
        |
        |Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt.
        |This natural-language response is then parsed into a typed object, which can be used in the application logic.
        |Parsed actors answer queries consisting of a list of strings representing a conversation thread, and responds with an object containing text and a parsed object.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.ParsedActor<T>
        |val answer : com.simiacryptus.skyenet.core.actors.ParsedResponse<T> = actor.answer(listOf("This is an example question."), api = api)
        |log.info("Natural Language Answer: " + answer.text)
        |log.info("Parsed Answer: " + com.simiacryptus.jopenai.util.JsonUtil.toJson(answer.obj))
        |```
        |
        |Coding actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
        |Coding actors answer queries expressed using CodeRequest, and responds with an object that defines a code block and an execution method.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.CodingActor
        |val answer : com.simiacryptus.skyenet.core.actors.CodingActor.CodeResult = actor.answer(listOf("This is an example question."), api = api)
        |log.info("Implemented Code: " + answer.code)
        |val executionResult : com.simiacryptus.skyenet.core.actors.CodingActor.ExecutionResult = answer.result
        |log.info("Execution Log: " + executionResult.resultOutput)
        |log.info("Execution Result: " + executionResult.resultValue)
        |```
        |
        |Image actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
        |Image actors answer queries consisting of a list of strings representing a conversation thread, and respond with an image.
        |```kotlin
        |val actor : com.simiacryptus.skyenet.core.actors.ImageActor
        |val answer : com.simiacryptus.skyenet.core.actors.ImageResponse = actor.answer(listOf("This is an example question."), api = api)
        |log.info("Image description: " + answer.text)
        |val image : BufferedImage = answer.image
        |```
        |
        |**IMPORTANT**: Do not define new actors. Use the provided actors specified in the preceding messages.
        |
        |While implementing logic, the progress should be displayed to the user using the `ui` object.
        |The UI display generally follows a pattern similar to:
        |
        |```kotlin
        |val task = ui.newTask()
        |try {
        |  task.header("Main Function")
        |  task.add("Normal message")
        |  task.verbose("Verbose output - not shown by default")
        |  task.add(ui.textInput { log.info("Message Recieved: " + it) })
        |  task.add(ui.hrefLink("Click Me!") { log.info("Link clicked") })
        |  task.complete()
        |} catch (e: Throwable) {
        |  task.error(e)
        |  throw e
        |}
        |```
        |""".trimMargin().trim(),
        model = model,
        temperature = temperature,
        runtimeSymbols = mapOf(
            "log" to log
        ),
    )

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(MetaAgentActors::class.java)
        fun <T> T.notIn(vararg examples: T) = !examples.contains(this)

    }
}

