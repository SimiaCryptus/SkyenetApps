package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.util.describe.Description
import java.util.function.Function

interface OutlineActors {

    interface OutlineParser : Function<String, Outline> {
        @Description("Break down the text into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): Outline
    }

    data class Outline(
        val items: List<Item>? = null,
    ) : ValidatedObject {
        override fun validate() = items?.all { it.validate() } ?: false

    }

    data class Item(
        val section_name: String? = null,
        var children: Outline? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == section_name -> false
            section_name.isEmpty() -> false
            else -> true
        }

    }
    companion object {
        fun Outline.deepClone(): Outline =
             Outline(this.items?.map { it.deepClone() })

        fun Item.deepClone(): Item = Item(
            section_name = this.section_name,
            children = this.children?.deepClone(),
            text = this.text
        )

        fun Outline.getTextOutline(): String {
            val sb = StringBuilder()
            items?.forEach { item ->
                sb.append(item.getTextOutline().trim())
                sb.append("\n")
            }
            return sb.toString()
        }

        fun Item.getTextOutline(): String {
            val sb = StringBuilder()
            sb.append("* " + ((text?.replace("\n", "\\n") ?: section_name)?.trim() ?: ""))
            sb.append("\n")
            val childrenTxt = children?.getTextOutline()?.replace("\n", "\n\t")?.trim() ?: ""
            if(childrenTxt.isNotEmpty()) sb.append("\t" + childrenTxt)
            return sb.toString()
        }

        fun Outline.getTerminalNodeMap(): Map<String, Item> = items?.flatMap { item ->
            val children = item.children
            if(children?.items?.isEmpty() != false) listOf(item.section_name!! to item)
            else children.getTerminalNodeMap().map { (key, value) -> item.section_name + " / " + key to value }
        }?.toMap() ?: emptyMap()

        fun questionSeeder(api: OpenAIClient) = ParsedActorConfig(
            OutlineParser::class.java,
            api = api,
            prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
            model = OpenAIClient.Models.GPT4Turbo,
        )
        fun finalWriter(api: OpenAIClient) = ActorConfig(
            api,
            prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
            model = OpenAIClient.Models.GPT4Turbo,
        )
        fun actors(api: OpenAIClient): List<ParsedActorConfig<Outline>> = listOf(
            object : ParsedActorConfig<Outline>(
                parserClass = OutlineParser::class.java,
                api = api,
                action = "Expand",
                prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
                model = OpenAIClient.Models.GPT35Turbo
            ) {
                override val minTokens = 70 // Do not expand if the data is too short
            },
        )
    }
}