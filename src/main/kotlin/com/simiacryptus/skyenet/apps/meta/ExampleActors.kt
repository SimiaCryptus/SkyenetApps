package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
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
        override fun validate(): String? = when {
            name.isNullOrBlank() -> "name is required"
            else -> null
        }
    }

    fun exampleParsedActor() = ParsedActor(
      ExampleParser::class.java,
      prompt = """
              |You are a question answering assistant.
              |""".trimMargin().trim(),
      model = ChatModels.GPT4Turbo,
      parsingModel = ChatModels.GPT35Turbo,
    )

    fun <T:Any> useExampleParsedActor(parsedActor: ParsedActor<T>): T {
        val answer = parsedActor.answer(listOf("This is an example question."), api = api)
        log.info("Natural Language Answer: " + answer.text);
        log.info("Parsed Answer: " + JsonUtil.toJson(answer.obj));
        return answer.obj
    }

    companion object {

        val api : API = OpenAIClient()
        val log = LoggerFactory.getLogger(ExampleActors::class.java)

        fun exampleCodingActor() = CodingActor(
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
        )

        @Language("Markdown")fun exampleSimpleActor() = SimpleActor(
            prompt = """
            |You are a writing assistant.
            """.trimMargin().trim(),
        )


        @Language("Markdown")fun exampleImageActor() = ImageActor()

        fun useExampleImageActor(): BufferedImage {
            val answer = exampleImageActor().answer(listOf("Example image description"), api = api)
            log.info("Rendering Prompt: " + answer.getText())
            return answer.getImage()
        }

        fun useExampleCodingActor(): CodingActor.CodeResult {
            val answer = exampleCodingActor().answer(CodingActor.CodeRequest(listOf("This is an example question.")), api = api)
            log.info("Answer: " + answer.getCode())
            val executionResult = answer.result()
            log.info("Execution Log: " + executionResult.resultOutput)
            log.info("Execution Result: " + executionResult.resultValue)
            return answer
        }

        fun useExampleSimpleActor(): String {
            val answer = exampleSimpleActor().answer(listOf("This is an example question."), api = api)
            log.info("Answer: " + answer)
            return answer
        }

    }
}