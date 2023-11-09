package com.simiacryptus.skyenet.actors

import com.simiacryptus.openai.OpenAIClient

abstract class BaseActor<T>(
    val prompt: String,
    val name: String? = null,
    val api: OpenAIClient = OpenAIClient(),
    val model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
    val temperature: Double = 0.3,
) {

    open val minTokens: Int = -1

    open fun response(vararg messages: OpenAIClient.ChatMessage) = api.chat(
        OpenAIClient.ChatRequest(
            messages = messages.toList().toTypedArray(),
            temperature = temperature,
            model = model.modelName,
        ),
        model = model
    )
    abstract fun answer(vararg messages: OpenAIClient.ChatMessage): T
    open fun answer(vararg questions: String): T = answer(*chatMessages(*questions))

    open fun chatMessages(vararg questions: String) = arrayOf(
        OpenAIClient.ChatMessage(
            role = OpenAIClient.ChatMessage.Role.system,
            content = prompt
        ),
    ) + questions.map {
        OpenAIClient.ChatMessage(
            role = OpenAIClient.ChatMessage.Role.user,
            content = it
        )
    }

}