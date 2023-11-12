package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.sessions.*
import com.simiacryptus.skyenet.util.MarkdownUtil

class AdminCommandCoder(
    applicationName: String = "AdminCommandCoder",
    oauthConfig: String? = null,
    temperature: Double = 0.1,
    ) : ChatApplicationBase(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionDiv: SessionDiv,
        socket: MessageWebSocket
    ) {
        sessionDiv.append("""<div>$userMessage</div>""", true)

        val model = OpenAIClient.Models.GPT4
        val response = socket.api.chat(
            OpenAIClient.ChatRequest(
                messages = arrayOf(
                    OpenAIClient.ChatMessage(role = OpenAIClient.ChatMessage.Role.system, content = """
                        You will convert the natural language description of an action for a Roblox game command into a Lua definition
                    """.trimIndent()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.ChatMessage.Role.user, content = "Modify the user's walkspeed"),
                    OpenAIClient.ChatMessage(role = OpenAIClient.ChatMessage.Role.assistant, content = """
                        ```lua
                        {
                            PrimaryAlias = "walkspeed",
                            SecondaryAlias = "speed",
                            PermissionLevel = 0,
                            Function = function(player: Player, args: { string })
                                local character = player.Character
                                if character then
                                    local humanoid = character:FindFirstChildOfClass("Humanoid")
                                    if humanoid then
                                        local speed = args[1]
                                        if speed and tonumber(speed) then
                                            humanoid.WalkSpeed = tonumber(speed) :: number
                                        end
                                    end
                                end
                            end,
                        }
                        ```
                    """.trimIndent()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.ChatMessage.Role.user, content = userMessage)
                ),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

}