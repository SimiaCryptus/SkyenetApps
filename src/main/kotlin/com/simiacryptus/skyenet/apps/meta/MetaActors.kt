package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.util.describe.Description
import org.intellij.lang.annotations.Language
import java.util.function.Function

interface MetaActors {

    interface DesignParser : Function<String, AgentDesign> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): AgentDesign
    }

    data class AgentDesign(
        val logicFlow: LogicFlow? = null,
        val actors: List<ActorDesign>? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == logicFlow -> false
            null == actors -> false
            actors.isEmpty() -> false
            logicFlow?.validate() == false -> false
            actors?.all { it.validate() } == false -> false
            else -> true
        }
    }

    data class ActorDesign(
        val name: String? = null,
        val description: String? = null,
        @Description("simple, parsed, or coding")
        val type: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == name -> false
            name.isEmpty() -> false
            null == description -> false
            description.isEmpty() -> false
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
        val actorsUsed: List<String>? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == name -> false
            name.isEmpty() -> false
            else -> true
        }

    }

    companion object {

        fun initialDesigner(api: OpenAIClient) = ParsedActor(
            DesignParser::class.java,
            api = api,
            model = OpenAIClient.Models.GPT4Turbo,
            prompt = """
                |You are a software design assistant.
                |
                |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
                |
                |There are three types of actors:
                |1. Simple actors contain a system directive and can process a list of user messages into a response
                |2. Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
                |3. Script actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
                |
                |Combined with traditional programming and existing jvm libraries, and also vector embeddings/databases using gpt models, we form complete "actor applications" or "agents". Optional UI elements exist to provide interactivity appropriate to a web application.
                |
                |As an example, consider an "Idea Mapping" agent containing 3 actors:
                |1. Initial author actor to answer a user query and parse the initial response essay into an outline
                |2. Expansion actor to expand the initial essay based on each parsed outline section
                |3. Final actor to generate a final essay based on the expanded outline, after tree manipulations such as substitution and partitioning
                |
                |The result of this example is the ability to generate much longer, more insightful explorations of concepts via queries to ChatGPT.
                |
                |Some important design principles:
                |1. ChatGPT has an optimal token window of still around 4k to "logic" although it now can support 128k of input tokens.
                |2. The logic of each individual actor is specialized and focused on a single task. This both conserves the cognitive space of the model, and allows the system to be more easily understood and debugged.
                |
                |Respond to the user's idea with a detailed technical document for a proposed system design including:
                |1. the actors used, including a description of the actor's purpose and how it is used
                |2. a step-by-step outline of the logical flow of the system
                |
                |""".trimMargin().trim(),
        )

        @Language("Markdown")fun simpleActorDesigner(api: OpenAIClient) = CodingActor(
            interpreterClass = KotlinInterpreter::class,
            details = """
            |You are a software implementation assistant.
            |Your task is to implement a "simple" actor that takes part in a larger system.
            |"Simple" actors contain a system directive and can process a list of user messages into a response.
            |
            |Code example:
            |```kotlin
            |import com.simiacryptus.openai.OpenAIClient
            |import com.simiacryptus.skyenet.actors.SimpleActor
            |
            |fun exampleActor(api: OpenAIClient) = SimpleActor(
            |    prompt = "You are a helpful writing assistant. Respond in detail to the user's prompt",
            |    api = api,
            |)
            |```
            |
            |The constructor signature for (final) SimpleActor class is:
            |```kotlin
            |class SimpleActor(
            |    prompt: String,
            |    name: String? = null,
            |    api: OpenAIClient = OpenAIClient(),
            |    model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
            |    temperature: Double = 0.3,
            |)
            |```
            |
            |Respond to the request with an instantiation function of the requested actor.
            |
            """.trimMargin().trim(),
            model = OpenAIClient.Models.GPT35Turbo,
            api = api,
        )

        @Language("Markdown")
        fun parsedActorDesigner(api: OpenAIClient) = CodingActor(
            interpreterClass = KotlinInterpreter::class,
            details = """
            |
            |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
            |Your task is to implement a "parsed" actor that takes part in a larger system.
            |"Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
            |
            |Code example:
            |```kotlin
            |import com.simiacryptus.openai.OpenAIClient
            |import com.simiacryptus.openai.proxy.ValidatedObject
            |import com.simiacryptus.util.describe.Description
            |import com.simiacryptus.skyenet.actors.ParsedActor
            |
            |data class Outline(
            |    val items: List<Item>? = null,
            |) : ValidatedObject {
            |    override fun validate() = items?.all { it.validate() } ?: false
            |}
            |
            |data class Item(
            |    val section_name: String? = null,
            |    val children: Outline? = null,
            |    val text: String? = null,
            |) : ValidatedObject {
            |    override fun validate() = when {
            |        null == section_name -> false
            |        section_name.isEmpty() -> false
            |        else -> true
            |    }
            |}
            |
            |interface OutlineParser : java.util.function.Function<String, Outline> {
            |    @Description("Break down the text into a recursive outline of the main ideas and supporting details.")
            |    override fun apply(text: String): Outline
            |}
            |
            |fun exampleActor(api: OpenAIClient) = ParsedActor(
            |    OutlineParser::class.java,
            |    prompt = "You are a helpful writing assistant. Respond in detail to the user's prompt",
            |    api = api,
            |)
            |```
            |
            |The constructor signature for the (final) ParsedActor class is:
            |```kotlin
            |class ParsedActor<T>(
            |    val parserClass: Class<out Function<String, T>>,
            |    prompt: String,
            |    val action: String? = null,
            |    api: OpenAIClient = OpenAIClient(),
            |    model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
            |    temperature: Double = 0.3,
            |)
            |```
            |
            |Respond to the request with an instantiation function of the requested actor.
            """.trimMargin().trim(),
            model = OpenAIClient.Models.GPT35Turbo,
            api = api,
        )

        @Language("Markdown")fun codingActorDesigner(api: OpenAIClient) = CodingActor(
            interpreterClass = KotlinInterpreter::class,
            details = """
            |
            |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
            |Your task is to implement a "script" or "coding" actor that takes part in a larger system.
            |"Script" actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
            |
            |Code example:
            |```kotlin
            |import com.simiacryptus.openai.OpenAIClient
            |import com.simiacryptus.skyenet.actors.CodingActor
            |import com.simiacryptus.skyenet.heart.GroovyInterpreter
            |import com.simiacryptus.skyenet.heart.KotlinInterpreter
            |import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
            |
            |fun exampleActor(api: OpenAIClient) = CodingActor(
            |    KotlinInterpreter::class,
            |    symbols = mapOf(
            |        "foo" to SomeObject()
            |    ),
            |    api = api,
            |)
            |```
            |
            |The constructor signature for the (final) CodingActor class is:
            |```kotlin
            |class CodingActor(
            |    private val interpreterClass: KClass<out Heart>,
            |    private val symbols: Map<String, Any> = mapOf(),
            |    name: String? = interpreterClass.simpleName,
            |    val details: String? = null,
            |    api: OpenAIClient = OpenAIClient(),
            |    model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
            |    temperature: Double = 0.3,
            |)
            |```
            |
            |Respond to the request with an instantiation function of the requested actor.
            |
            """.trimMargin().trim(),
            model = OpenAIClient.Models.GPT35Turbo,
            api = api,
        )
    }
}

private fun <T> T.notIn(vararg examples: T) = !examples.contains(this)
