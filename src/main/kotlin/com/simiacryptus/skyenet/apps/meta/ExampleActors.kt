package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.jopenai.describe.Description
import org.intellij.lang.annotations.Language
import java.util.function.Function

/**
 * This class provides a strongly-typed example for use in creating this app's actor generation prompts.
 *
 * TODO: Auto-generate the actor definitions from this class.
 */
interface ExampleActors {

    interface ExampleParser : Function<String, ExampleResult> {
        @Description("Break down the text into a data structure.")
        override fun apply(text: String): ExampleResult
    }

    data class ExampleResult(
        val name: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            name.isNullOrBlank() -> false
            else -> true
        }
    }

    fun exampleParsedActor() = ParsedActor(
        ExampleParser::class.java,
        model = ChatModels.GPT4Turbo,
        prompt = """
                |You are a question answering assistant.
                |""".trimMargin().trim(),
    )

    companion object {

        @Language("Markdown")fun exampleCodingActor() = CodingActor(
            interpreterClass = KotlinInterpreter::class,
            details = """
            |You are a software implementation assistant.
            |
            |Defined functions:
            |* ...
            |
            |Expected code structure:
            |* ...
            """.trimMargin().trim(),
            autoEvaluate = true,
        )


        @Language("Markdown")fun exampleSimpleActor() = SimpleActor(
            prompt = """
            |You are a writing assistant.
            """.trimMargin().trim(),
        )

    }
}