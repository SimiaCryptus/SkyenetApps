package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.Heart
import com.simiacryptus.skyenet.actors.BaseActor
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.jopenai.describe.Description
import org.intellij.lang.annotations.Language
import java.util.function.Function
import kotlin.reflect.KClass


class MetaActors(
    private val interpreterClass: KClass<out Heart> = KotlinInterpreter::class,
    val symbols: Map<String, Any> = mapOf(),
    val model: ChatModels = ChatModels.GPT4Turbo,
    val autoEvaluate: Boolean = true,
    val temperature: Double = 0.3,
) {

    enum class ActorType {
        INITIAL,
        SIMPLE,
        PARSED,
        CODING,
        FLOW_STEP,
    }

    val actorMap: Map<ActorType, BaseActor<out Any>> = mapOf(
        ActorType.INITIAL to initialDesigner(),
        ActorType.SIMPLE to simpleActorDesigner(),
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
        override fun validate() = when {
            null == logicFlow -> false
            null == actors -> false
            actors.isEmpty() -> false
            !logicFlow.validate() -> false
            !actors.all { it.validate() } -> false
            else -> true
        }
    }

    data class ActorDesign(
        @Description("Java class name of the actor")
        val name: String? = null,
        val description: String? = null,
        @Description("simple, parsed, or coding")
        val type: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == name -> false
            name.isEmpty() -> false
            name.chars().anyMatch { !Character.isJavaIdentifierPart(it) } -> false
            null == type -> false
            type.isEmpty() -> false
            type.notIn("simple", "parsed", "coding") -> false
            else -> true
        }

    }

    data class LogicFlow(
        val items: List<LogicFlowItem>? = null,
    ) : ValidatedObject {
        override fun validate() = items?.all { it.validate() } ?: false
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
        override fun validate() = when {
            null == name -> false
            name.isEmpty() -> false
            //inputs?.isEmpty() != false && inputs?.isEmpty() != false -> false
            else -> true
        }
    }

    data class DataInfo(
        val name: String? = null,
        val description: String? = null,
        val type: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == name -> false
            name.isEmpty() -> false
            null == type -> false
            type.isEmpty() -> false
            else -> true
        }
    }

    @Language("Markdown")
    fun initialDesigner() = ParsedActor(
        DesignParser::class.java,
        model = model,
        prompt = """
            |You are a software architect.
            |
            |Your task is to design a system that uses gpt "actors" to construct a model of a creative process.
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
            |
            |Some important design principles:
            |1. ChatGPT has an optimal token window of still around 4k to "logic" although it now can support 128k of input tokens.
            |2. The logic of each actor is specialized and focused on a single task.
            |   This both conserves the cognitive space of the model, and allows the system to be more easily understood and debugged.
            |
            |Respond to the user's idea by breaking down the requested system into a component design including:
            |1. Actors
            |    1. a description of the actor's purpose and how it is used
            |    2. a description of the actor's input and output
            |    3. a description of the actor's logic
            |2. Logical Flow - how the actors interact with each other to produce the desired result
            |    1. step description
            |    2. input
            |    3. output
            |    4. actors used
            |
            |Unless otherwise stated, the input to the entire process being designed is a single "user prompt" string.""".trimMargin()
            .trim(),
        temperature = temperature,
    )

    @Language("Markdown")
    fun simpleActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        model = model,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a "simple" actor that takes part in a larger system.
        |"Simple" actors contain a system directive and can process a list of user messages into a response.
        |
        |For context, here is the constructor signature for SimpleActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.skyenet.actors.SimpleActor
        |import org.intellij.lang.annotations.Language
        |import com.simiacryptus.jopenai.models.OpenAITextModel
        |
        |class SimpleActor(
        |    prompt: String,
        |    name: String? = null,
        |    model: OpenAITextModel = ChatModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt and a name:
        |```kotlin
        |import com.simiacryptus.skyenet.actors.SimpleActor
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
        autoEvaluate = autoEvaluate,
        temperature = temperature,
    )

    @Language("Markdown")
    fun parsedActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        model = model,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "parsed" actor that takes part in a larger system.
        |"Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
        |
        |For context, here is the constructor signature for ParsedActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.models.OpenAITextModel
        |import java.util.function.Function
        |
        |open class ParsedActor<T:Any>(
        |    val parserClass: Class<out Function<String, T>>,
        |    prompt: String,
        |    val action: String? = null,
        |    model: OpenAITextModel = ChatModels.GPT35Turbo,
        |    temperature: Double = 0.3,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and parsing class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.proxy.ValidatedObject
        |import com.simiacryptus.skyenet.actors.ParsedActor
        |import com.simiacryptus.util.describe.Description
        |import org.intellij.lang.annotations.Language
        |import java.util.function.Function
        |
        |interface ExampleParser : Function<String, ExampleResult> {
        |    @Description("Break down the text into a data structure.")
        |    override fun apply(text: String): ExampleResult
        |}
        |
        |data class ExampleResult(
        |    val name: String? = null,
        |) : ValidatedObject {
        |    override fun validate() = when {
        |        name.isNullOrBlank() -> false
        |        else -> true
        |    }
        |}
        |
        |fun exampleParsedActor() = ParsedActor(
        |    ExampleParser::class.java,
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
        autoEvaluate = autoEvaluate,
        temperature = temperature,
    )

    @Language("Markdown")
    fun codingActorDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        model = model,
        details = """
        |
        |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
        |Your task is to implement a "script" or "coding" actor that takes part in a larger system.
        |"Script" actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
        |
        |For context, here is the constructor signature for CodingActor class:
        |```kotlin
        |import com.simiacryptus.jopenai.models.ChatModels
        |import com.simiacryptus.jopenai.models.OpenAITextModel
        |import com.simiacryptus.skyenet.Heart
        |import com.simiacryptus.util.describe.AbbrevWhitelistYamlDescriber
        |import com.simiacryptus.util.describe.TypeDescriber
        |import kotlin.reflect.KClass
        |
        |@Suppress("unused", "MemberVisibilityCanBePrivate")
        |open class CodingActor(
        |    private val interpreterClass: KClass<out Heart>,
        |    private val symbols: Map<String, Any> = mapOf(),
        |    private val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
        |        "com.simiacryptus",
        |        "com.github.simiacryptus"
        |    ),
        |    name: String? = interpreterClass.simpleName,
        |    val details: String? = null,
        |    model: OpenAITextModel = ChatModels.GPT35Turbo,
        |    val fallbackModel: OpenAITextModel = ChatModels.GPT4Turbo,
        |    temperature: Double = 0.1,
        |    val autoEvaluate: Boolean = false,
        |)
        |```
        |
        |In this code example an example actor is defined with a prompt, name, and a standard configuration:
        |```kotlin
        |import com.simiacryptus.skyenet.actors.CodingActor
        |import com.simiacryptus.skyenet.heart.KotlinInterpreter
        |import org.intellij.lang.annotations.Language
        |
        |@Language("Markdown")fun exampleCodingActor() = CodingActor(
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
        |    autoEvaluate = autoEvaluate,
        |)
        |```
        |
        |Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        |DO NOT subclass the CodingActor class. Use the constructor directly within the function.
        |
        """.trimMargin().trim(),
        autoEvaluate = autoEvaluate,
        temperature = temperature,
    )

    @Language("Markdown")
    fun flowStepDesigner() = CodingActor(
        interpreterClass = interpreterClass,
        symbols = symbols,
        model = model,
        details = """
        |You are a software implementation assistant.
        |Your task is to implement a step in the logic flow of a ChatGPT-based actor system.
        |Respond to the user request with an implementation of the requested logic flow step.
        |Preceding "assistant" messages define the existing code of the system, which you will append to.
        """.trimMargin().trim(),
        autoEvaluate = autoEvaluate,
        temperature = temperature,
    )

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(MetaActors::class.java)
        fun <T> T.notIn(vararg examples: T) = !examples.contains(this)

    }
}

