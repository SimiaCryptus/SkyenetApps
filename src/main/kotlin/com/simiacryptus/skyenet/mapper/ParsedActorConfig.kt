package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import java.util.function.Function

open class ParsedActorConfig<T>(
    parserClass: Class<out Function<String, T>>,
    prompt: String,
    action: String? = null,
    api: OpenAIClient,
    model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
    temperature: Double = 0.3,
) : ActorConfig(
    api = api,
    prompt = prompt,
    action = action,
    model = model,
    temperature = temperature,
) {
    private val parser: Function<String, T> = ChatProxy(
        clazz = parserClass,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = temperature,
    ).create()

    inner class ParsedResponse(vararg messages: OpenAIClient.ChatMessage) {
        val text: String by lazy { answer(*messages) }
        val obj: T by lazy { parser.apply(text) }
    }

    fun parse(vararg questions: String) = ParsedResponse(*chatMessages(*questions))
    fun parse(vararg messages: OpenAIClient.ChatMessage) = ParsedResponse(*messages)
}