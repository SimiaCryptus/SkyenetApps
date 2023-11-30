package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.apps.outline.OutlineManager.NodeList
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import java.util.function.Function

interface OutlineActors {

    interface OutlineParser : Function<String, NodeList> {
        @Description("Break down the text into a recursive outline of the main ideas and supporting details.")
        override fun apply(text: String): NodeList
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

        private fun initialAuthor(temperature: Double) = ParsedActor(
          OutlineParser::class.java,
          prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
          model = ChatModels.GPT4Turbo,
          temperature = temperature,
          parsingModel = ChatModels.GPT35Turbo,
        )

        private fun expansionAuthor(temperature: Double): ParsedActor<NodeList> = ParsedActor(
          parserClass = OutlineParser::class.java,
          prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
          name = "Expand",
          model = ChatModels.GPT35Turbo,
          temperature = temperature,
          parsingModel = ChatModels.GPT35Turbo,
        )

        private fun finalWriter(temperature: Double) = SimpleActor(
            prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
            model = ChatModels.GPT4Turbo,
            temperature = temperature,
        )

    }
}