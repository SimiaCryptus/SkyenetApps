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
        INITIAL,
        HIGH_LEVEL,
        DETAIL,
        SIMPLE,
        IMAGE,
        PARSED,
        CODING,
        FLOW_STEP,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.INITIAL to initialDesigner(),
        ActorType.HIGH_LEVEL to highLevelDesigner(),
        ActorType.DETAIL to detailedDesigner(),
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

    data class ActorDesign(
        @Description("Java class name of the actor")
        val name: String = "",
        val description: String? = null,
        @Description("simple, parsed, image, or coding")
        val type: String = "",
        @Description("string, code, image, or a simple java identifier (class name without package - no inner classes, no generics)")
        val resultType: String = "",
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            name.chars().anyMatch { !Character.isJavaIdentifierPart(it) } -> "name must be a valid java identifier"
            null == type -> "type is required"
            type.isEmpty() -> "type is required"
            type.lowercase().notIn("simple", "parsed", "coding", "image") -> "type must be simple, parsed, coding, or image"
            resultType?.isEmpty() != false -> "resultType is required"
            resultType.lowercase().notIn("string", "code", "image") && !validClassName(resultType) -> "resultType must be string, code, image, or a valid class name"
            else -> null
        }

        private fun validClassName(resultType: String) = when {
            resultType.isEmpty() -> false
            "[A-Z][a-zA-Z0-9_<>]{3,}".toRegex().matches(resultType) -> true
            else -> false
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
        
            Your task is to conceptualize the architecture of an "agent" system that uses gpt "actors" to model a creative process.
            The system should be procedural in its overall structure, with creative steps modeled by gpt actors.
        
            The system will interact with users through a web-based interface, where users can initiate processes with a single prompt.
        
            Provide a high-level design that includes:
            1. The types of actors needed and their high-level roles.
            2. The user interface flow with types of tasks and messages.
            3. The sequence of tasks contributing to the creative process.
        
            The input to your design process is a single "user prompt" string.
    """.trimIndent().trim(),
        temperature = temperature
    )

    @Language("Markdown")
    fun detailedDesigner() = ParsedActor(
        DesignParser::class.java,
        model = model,
        prompt = """
            You are a detailed software designer.
            
            Your task is to expand on the high-level architecture provided by the High-Level Designer.
            You need to detail the components, logic, data structures, and technical specifications for implementation.
            
            All actors process inputs in the form of ChatGPT messages (often a single string) but vary in their output.
            There are three types of actors:
            1. "Simple" actors contain a system directive, and simply return the chat model's response to the user query.
            2. "Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt.
               This natural-language response is then parsed into a typed object, which can be used in the application logic.
            3. "Coding" actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
               The code environment can easily be augmented with symbols, which are described to the code generation model and are also available to the execution runtime.
               This can be used for incremental code generation, where symbols defined by previous code generation actors can be used by later actors.
               This can also be used to translate user requests into executed code, i.e. requested actions performed by the system.
               Supported languages are Scala, Kotlin, and Groovy.
            4. "Image" actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
               This image prompt is then used to generate an image, which is returned to the user.
            
            The design should include:
            1. Detailed logic and flow for each component.
            2. Inputs and outputs for each step in the process.
            3. Detailed descriptions of each actor, including purpose and usage.
            4. For "Coding" actors, the symbols used for code generation and execution.
            5. For "Parsed" actors, the data structures for parsing responses into typed objects.
            6. Interactive elements in the user interface and their server-side functions.
            
            The input to your design process is the high-level design document from the High-Level Designer.
    """.trimIndent().trim(),
        temperature = temperature
    )


    @Language("Markdown")
    fun initialDesigner() = ParsedActor(
        DesignParser::class.java,
        model = model,
        prompt = """
            |You are a software architect.
            |
            |Your task is to design an "agent" system that uses gpt "actors" to construct a model of a creative process.
            |This "creative process" can be thought of as a cognitive process, an individual's work process, or an organizational process.
            |The idea is that the overall structure is procedural and can be modeled in code, but individual steps are creative and can be modeled with gpt.
            |
            |All actors process inputs in the form of ChatGPT messages (often a single string) but vary in their output.
            |There are three types of actors:
            |1. "Simple" actors contain a system directive, and simply return the chat model's response to the user query.
            |2. "Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt.
            |   This natural-language response is then parsed into a typed object, which can be used in the application logic.
            |3. "Coding" actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
            |   The code environment can easily be augmented with symbols, which are described to the code generation model and are also available to the execution runtime.
            |   This can be used for incremental code generation, where symbols defined by previous code generation actors can be used by later actors.
            |   This can also be used to translate user requests into executed code, i.e. requested actions performed by the system.
            |   Supported languages are Scala, Kotlin, and Groovy.
            |4. "Image" actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
            |   This image prompt is then used to generate an image, which is returned to the user.
            |   
            |The system provides a web-based user interface composed of a series of "tasks" that contain messages.
            |All agent applications are initialized with a single user prompt, which is used to generate the first task.
            |Messages can be header, normal, verbose, image, error, or "complete". 
            |Until the error or complete is called, a progress bar is displayed.
            |Messages can contain interactive elements including text input and links which trigger server-side lambda functions.
            |A single process can use multiple tasks in a single or multi-threaded manner.
            |
            |Respond to the user's idea by breaking down the requested system into a fully-detailed component design including:
            |1. Logic - how the actors interact with each other to produce the desired result, including:
            |    1. complete description of the logic and flow
            |    2. inputs and outputs for each step
            |    3. actors used and where
            |2. Actors - Each individual actor, including:
            |    1. a description of the actor's purpose and how it is used
            |    2. if a coding actor, a description of the symbols used
            |    3. if a parsed actor, a description of the data structure used
            |
            |Unless otherwise stated, the input to the entire process being designed is a single "user prompt" string.
            |""".trimMargin().trim(),
        temperature = temperature,
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
        |        name.isNullOrBlank() -> false
        |        else -> true
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
        |```kotlin
        |fun useExampleSimpleActor(): String {
        |    val answer = exampleSimpleActor().answer(listOf("This is an example question."), api = api)
        |    log.info("Answer: " + answer)
        |    return answer
        |}
        |```
        |
        |Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt. 
        |This natural-language response is then parsed into a typed object, which can be used in the application logic.
        |```kotlin
        |import com.simiacryptus.jopenai.util.JsonUtil
        |import com.simiacryptus.skyenet.core.actors.ParsedActor
        |import com.simiacryptus.skyenet.core.actors.CodingActor
        |
        |fun <T:Any> useExampleParsedActor(parsedActor: ParsedActor<T>): T {
        |    val answer = parsedActor.answer(listOf("This is an example question."), api = api)
        |    log.info("Natural Language Answer: " + answer.getText());
        |    log.info("Parsed Answer: " + JsonUtil.toJson(answer.getObj()));
        |    return answer.getObj()
        |}
        |```
        |
        |Coding actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
        |```kotlin
        |fun useExampleCodingActor(): CodingActor.CodeResult {
        |    val answer = exampleCodingActor().answer(CodingActor.CodeRequest(listOf("This is an example question.")), api = api)
        |    log.info("Answer: " + answer.getCode())
        |    val executionResult = answer.result()
        |    log.info("Execution Log: " + executionResult.resultOutput)
        |    log.info("Execution Result: " + executionResult.resultValue)
        |    return answer
        |}
        |```
        |
        |Image actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
        |```kotlin
        |fun useExampleImageActor(): BufferedImage {
        |    val answer = exampleImageActor().answer(listOf("Example image description"), api = api)
        |    log.info("Rendering Prompt: " + answer.getText())
        |    return answer.getImage()
        |}
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
        |    task.header("Main Function")
        |    task.add("Normal message")
        |    task.verbose("Verbose output - not shown by default")
        |    task.add(ui.textInput { log.info("Message Recieved: " + it) })
        |    task.add(ui.hrefLink("Click Me!") { log.info("Link clicked") })
        |    task.complete()
        |    return
        |} catch (e: Throwable) {
        |    task.error(e)
        |    throw e
        |}
        |```
        |
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

