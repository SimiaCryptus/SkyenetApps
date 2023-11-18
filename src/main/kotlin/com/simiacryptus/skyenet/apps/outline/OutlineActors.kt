package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
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
        val children: Outline? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == section_name -> false
            section_name.isEmpty() -> false
            else -> true
        }
    }

    enum class ActorType {
        INITIAL,
        EXPAND,
        FINAL,
    }

    companion object {

        val log = org.slf4j.LoggerFactory.getLogger(OutlineActors::class.java)

        fun actorMap(temperature: Double) = mapOf(
            ActorType.INITIAL to initialAuthor(temperature),
            ActorType.EXPAND to expansionAuthor(temperature),
            ActorType.FINAL to finalWriter(temperature),
        )

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
            if (childrenTxt.isNotEmpty()) sb.append("\t" + childrenTxt)
            return sb.toString()
        }

        fun Outline.getTerminalNodeMap(): Map<String, Item> {
            return items?.map { item ->
                if (item.children?.items?.isEmpty() != false) mapOf(item.section_name!! to item)
                else item.children?.getTerminalNodeMap()?.mapKeys { key -> item.section_name + " / " + key } ?: mapOf()
            }?.flatMap { it.entries.map { it.key to it.value } }?.toList()?.toMap() ?: emptyMap()
        }

        fun initialAuthor(temperature: Double) = ParsedActor(
            OutlineParser::class.java,
            prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
            model = ChatModels.GPT4Turbo,
            temperature = temperature,
        )

        fun expansionAuthor(temperature: Double): ParsedActor<Outline> = ParsedActor(
            parserClass = OutlineParser::class.java,
            action = "Expand",
            prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
            model = ChatModels.GPT35Turbo,
            temperature = temperature,
        )

        fun finalWriter(temperature: Double) = SimpleActor(
            prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
            model = ChatModels.GPT4Turbo,
            temperature = temperature,
        )

    }
}