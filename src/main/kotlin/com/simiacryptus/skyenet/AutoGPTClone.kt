package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import com.simiacryptus.util.JsonUtil

class AutoGPTClone(
    applicationName: String,
    baseURL: String,
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {
    interface AutoGptAPI {

        data class AgentPrompt(
            val name: String = "",
            val role: String = "",
            val goals: List<String> = listOf(),
            val constraints: List<String> = listOf(),
        )

        data class AgentConfig(
            val commands: List<String> = listOf(),
            val resources: List<String> = listOf(),
            val performanceEvaluation: List<String> = listOf(),
        )

        data class AgentResponse(
            val thoughts: List<AgentThoughts> = listOf(),
            val command: String = "",
        )

        data class AgentThoughts(
            val text: String = "",
            val reasoning: String = "",
            val plan: String = "",
            val criticism: String = "",
            val speak: String = "",
        )

        fun parsePrompt(prompt: String): AgentPrompt

        fun respond(prompt: AgentPrompt, config: AgentConfig): AgentResponse

        fun continueChat(prompt: AgentPrompt, config: AgentConfig, history: List<AgentSystemResponse>): AgentResponse

        data class AgentSystemResponse(
            val agentResponse: AgentResponse = AgentResponse(),
            val systemResponse: SystemResponse = SystemResponse(),
        )

        data class SystemResponse(
            val text: String = "",
        )


    }

    val projectAPI = ChatProxy(
        clazz = AutoGptAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT4,
        temperature = temperature
    ).create()

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        try {
            sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            val prompt = projectAPI.parsePrompt(userMessage)
            val agentConfig = AutoGptAPI.AgentConfig(
                commands = listOf("Do it"),
                resources = listOf(""),
                performanceEvaluation = listOf(
                    "Continuously review and analyze your actions to ensure you are performing to the best of your abilities.",
                    "Constructively self-criticize your big-picture behavior constantly.",
                    "Reflect on past decisions and strategies to refine your approach.",
                    "Every command has a cost, so be smart and efficient. Aim to complete tasks in the least number of steps."
                ),
            )
            val response = projectAPI.respond(prompt, agentConfig)
            sessionDiv.append(
                """<div>${ChatSessionFlexmark.renderMarkdown("""
                    |```json
                    |${JsonUtil.toJson(response)}
                    |```
                    """.trimMargin())
                }</div>""", false
            )


        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

}