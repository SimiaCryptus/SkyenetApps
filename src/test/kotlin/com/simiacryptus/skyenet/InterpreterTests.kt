package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterpreterTests {

    @Test
    fun test() {
        val interpreter = KotlinInterpreter()
        val code = """
            import com.simiacryptus.jopenai.OpenAIClient
            import com.simiacryptus.skyenet.core.actors.ParsedActor
            import com.simiacryptus.skyenet.heart.GroovyInterpreter
            import com.simiacryptus.skyenet.heart.KotlinInterpreter
            import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
            
            data class DesignProposal(
                val architecture: String,
                val technologyStack: List<String>,
                val designPatterns: List<String>
            )
            
            fun designProposalActor(api: OpenAIClient) = ParsedActor2(
                KotlinInterpreter::class,
                symbols = mapOf(),
                api = api,
                model = Models.GPT35Turbo,
                temperature = 0.3,
                parser = { response ->
                    val lines = response.split("\n")
                    val architecture = lines[0]
                    val technologyStack = lines[1].split(", ")
                    val designPatterns = lines[2].split(", ")
                    DesignProposal(architecture, technologyStack, designPatterns)
                }
            )
            
            """.trimIndent()
        val validate = interpreter.validate(code)
        println("validate = ${validate}")
        //assert(validate != null) // <- Expect an error since `parser` is not a valid parameter
        @Language("kotlin") val result = interpreter.run(code)
        assertEquals(result, null) // Not an expression
    }


    @Test
    fun test2() {
        val interpreter = KotlinInterpreter()
        val code = """
            com.simiacryptus.skyenet.InterpreterTests.Companion.testFn()
            """.trimIndent()
        val validate = interpreter.validate(code)
        assertEquals(validate, null) // <- Expect an error since `parser` is not a valid parameter
        @Language("kotlin") val result = interpreter.run(code)
        assertEquals(result, "test") // Not an expression
    }

    companion object
}