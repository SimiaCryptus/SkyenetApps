package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import java.util.function.Function

interface DebateActors {

  interface DebateParser : Function<String, DebateSetup> {
    @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
    override fun apply(text: String): DebateSetup
  }

  data class DebateSetup(
    val debaters: Debaters? = null,
    val questions: Questions? = null,
  )

  data class Debaters(
    val list: List<Debater>? = null,
  )

  data class Questions(
    val list: List<Question>? = null,
  )

  data class Debater(
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
    override fun validate(): String? {
      val joinToString = arguments?.filter { it.validate() != null }?.map { it.validate() }?.joinToString("\n")
      return if (joinToString.isNullOrBlank()) null else joinToString
    }

  }

  data class Argument(
      val point_name: String? = null,
      val text: String? = null,
  ) : ValidatedObject {
    override fun validate(): String? = when {
      null == point_name -> "point_name is required"
      point_name.isEmpty() -> "point_name is required"
      else -> null
    }

  }

  enum class ActorType {
    MODERATOR,
    SUMMARIZOR,
  }

  companion object {

    val actorMap
      get() = mapOf(
          ActorType.MODERATOR to moderator(),
          ActorType.SUMMARIZOR to summarizor(),
      )

    fun getActorConfig(actor: Debater) = ParsedActor(
      parserClass = OutlineParser::class.java,
      prompt = """You are a debater: ${actor.name}.
                              |You will provide a well-reasoned and supported argument for your position.
                              |Details about you: ${actor.description}
                              """.trimMargin(),
      model = ChatModels.GPT4,
      parsingModel = ChatModels.GPT35Turbo,
    )

    private fun moderator() = ParsedActor(
      DebateParser::class.java,
      prompt = """You will take a user request, and plan a debate. You will introduce the debaters, and then provide a list of questions to ask.""",
      model = ChatModels.GPT4,
      parsingModel = ChatModels.GPT35Turbo,
    )

    private fun summarizor() = SimpleActor(
        prompt = """You are a helpful writing assistant, tasked with writing a markdown document combining the user massages given in an impartial manner""",
        model = ChatModels.GPT4,
    )

  }
}