package com.simiacryptus.skyenet.actors

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.Brain
import com.simiacryptus.skyenet.Brain.Companion.indent
import com.simiacryptus.skyenet.Brain.Companion.superMethod
import com.simiacryptus.skyenet.Heart
import com.simiacryptus.skyenet.body.SessionServerUtil
import com.simiacryptus.skyenet.body.SessionServerUtil.asJava
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import com.simiacryptus.util.describe.AbbrevWhitelistYamlDescriber
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

open class CodingActor(
    val symbols: Map<String, Any> = mapOf(),
    val interpreterClass: KClass<out Heart> = ScalaLocalInterpreter::class,
    val describer: AbbrevWhitelistYamlDescriber = AbbrevWhitelistYamlDescriber(
        "com.simiacryptus",
        "com.github.simiacryptus"
    ),
    name: String? = null,
    api: OpenAIClient = OpenAIClient(),
    model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
    temperature: Double = 0.3,
) : BaseActor<String>(
    prompt = "",
    name = name,
    api = api,
    model = model,
    temperature = temperature,
) {
    override val prompt: String
        get() {
            val types = ArrayList<Class<*>>()
            val apiobjs = symbols.map { (name, utilityObj) ->
                val clazz = Class.forName(utilityObj.javaClass.typeName)
                val methods = clazz.methods
                    .filter { Modifier.isPublic(it.modifiers) }
                    .filter { it.declaringClass == clazz }
                    .filter { !it.isSynthetic }
                    .map { it.superMethod() ?: it }
                    .filter { it.declaringClass != Object::class.java }
                types.addAll(methods.flatMap { (listOf(it.returnType) + it.parameters.map { it.type }).filter { it != clazz } })
                types.addAll(clazz.declaredClasses.filter { Modifier.isPublic(it.modifiers) })
                """
                        |$name:
                        |  operations:
                        |    ${Brain.joinYamlList(methods.map { describer.describe(it) }).indent().indent()}
                        |""".trimMargin().trim()
            }.toTypedArray<String>()
            val typeDescriptions = types
                .filter { !it.isPrimitive }
                .filter { !it.isSynthetic }
                .filter { !it.name.startsWith("java.") }
                .filter { !setOf("void").contains(it.name) }
                .distinct().map {
                    """
                    |${it.simpleName}:
                    |  ${describer.describe(it).indent()}
                    """.trimMargin().trim()
                }.toTypedArray<String>()
            val apiDescription = """
                    |api_objects:
                    |  ${apiobjs.joinToString("\n").indent()}
                    |components:
                    |  schemas:
                    |    ${typeDescriptions.joinToString("\n").indent().indent()}
                """.trimMargin()
            return """
                        |You will translate natural language instructions into 
                        |an implementation using ${interpreter.getLanguage()} and the script context.
                        |Use ``` code blocks labeled with ${interpreter.getLanguage()} where appropriate.
                        |Defined symbols include ${symbols.keys.joinToString(", ")}.
                        |The runtime context is described below:
                        |
                        |$apiDescription
                        |""".trimMargin().trim()

        }

    open val brain by lazy {
        Brain(
            api = api,
            symbols = symbols.mapValues { it as Object }.asJava,
            language = interpreter.getLanguage(),
            describer = describer,
            model = model
        )
    }

    open val interpreter by lazy { interpreterClass.java.getConstructor(Map::class.java).newInstance(symbols) }

    override fun answer(vararg questions: String): String = answer(*chatMessages(*questions))

    override fun answer(vararg messages: OpenAIClient.ChatMessage): String {
        val response = brain.implement(*messages)
        val codeBlocks = Brain.extractCodeBlocks(response)
        var renderedResponse = SessionServerUtil.getRenderedResponse(codeBlocks)
        var codedInstruction = SessionServerUtil.getCode(interpreter.getLanguage(), codeBlocks)
        log.info("Response: $renderedResponse")
        log.info("Code: $codedInstruction")
        for (int in 0..3) {
            try {
                interpreter.validate(codedInstruction)
                break
            } catch (ex: Throwable) {
                log.info("Validation failed", ex)
                val respondWithCode = brain.fixCommand(codedInstruction, ex, "", *messages)
                renderedResponse = SessionServerUtil.getRenderedResponse(respondWithCode.second)
                codedInstruction = SessionServerUtil.getCode(interpreter.getLanguage(), respondWithCode.second)
                log.info("Response: $renderedResponse")
                log.info("Code: $codedInstruction")
            }
        }
        return codedInstruction
    }

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(CodingActor::class.java)
    }
}

