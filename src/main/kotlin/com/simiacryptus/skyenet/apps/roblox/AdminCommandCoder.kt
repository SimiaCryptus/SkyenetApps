package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.OpenAIClientBase.Companion.toContentList
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil

class AdminCommandCoder(
    applicationName: String = "AdminCommandCoder",
    temperature: Double = 0.1,
    ) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket
    ) {
        sessionDiv.append("""<div>$userMessage</div>""", true)

        val model = ChatModels.GPT4
        val response = socket.api.chat(
            OpenAIClient.ChatRequest(
                messages = ArrayList(
                    listOf(OpenAIClient.ChatMessage(role = OpenAIClient.Role.system, content = """
                        You will convert the natural language description of an action for a Roblox game command into a Lua definition
                    """.trimIndent().toContentList()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.user, content = "Modify the user's walkspeed".toContentList()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.assistant, content = """
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
                    """.trimIndent().toContentList()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.user, content = userMessage.toContentList())
                )
                ),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

}