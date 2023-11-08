package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.OpenAIClient

open class ActorConfig(
    val api: OpenAIClient,
    val prompt: String,
    val action: String? = null,
    val model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
    val temperature: Double = 0.3,
) {
    open val minTokens: Int = -1

    fun answer(vararg questions: String): String = answer(*chatMessages(*questions))

    fun chatMessages(vararg questions: String) = arrayOf(
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

    fun answer(vararg messages: OpenAIClient.ChatMessage): String = api.chat(
        OpenAIClient.ChatRequest(
            messages = messages.toList().toTypedArray(),
            temperature = temperature,
            model = model.modelName,
        ),
        model = model
    ).choices.first().message?.content ?: throw RuntimeException("No response")
}