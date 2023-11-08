package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.util.describe.Description
import java.util.function.Function

interface DebateActors {

    interface DebateParser : Function<String, DebateSetup> {
        @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): DebateSetup
    }

    data class DebateSetup (
        val debators: Debators? = null,
        val questions: Questions? = null,
    )

    data class Debators(
        val list: List<Debator>? = null,
    )

    data class Questions(
        val list: List<Question>? = null,
    )

    data class Debator(
        val name: String? = null,
        val description: String? = null,
    )

    data class Question(
        val text: String? = null,
    )
    interface OutlineParser : Function<String, Outline> {
        @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): Outline
    }

    data class Outline(
        val arguments: List<Argument>? = null,
    ) : ValidatedObject {
        override fun validate() = arguments?.all { it.validate() } ?: false

    }

    data class Argument(
        val point_name: String? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == point_name -> false
            point_name.isEmpty() -> false
            else -> true
        }

    }

    companion object {
        fun Outline.deepClone(): Outline =
            Outline(this.arguments?.map { it.deepClone() })

        fun Argument.deepClone(): Argument = Argument(
            point_name = this.point_name,
            text = this.text,
        )
        fun getActorConfig(api: OpenAIClient, actor: Debator) = ParsedActorConfig(
            parserClass = OutlineParser::class.java,
            api = api,
            prompt = """You are a debater: ${actor.name}.
                                |You will provide a well-reasoned and supported argument for your position.
                                |Details about you: ${actor.description}
                                """.trimMargin(),
            model = OpenAIClient.Models.GPT4,
        )

        fun moderator(api: OpenAIClient) = ParsedActorConfig(
            DebateParser::class.java,
            api = api,
            prompt = """You will take a user request, and plan a debate. You will introduce the debaters, and then provide a list of questions to ask.""",
            model = OpenAIClient.Models.GPT4,
        )
        fun summarizor(api: OpenAIClient) = ActorConfig(
            api,
            prompt = """You are a helpful writing assistant, tasked with writing a markdown document combining the user massages given in an impartial manner""",
            model = OpenAIClient.Models.GPT4,
        )

    }
}