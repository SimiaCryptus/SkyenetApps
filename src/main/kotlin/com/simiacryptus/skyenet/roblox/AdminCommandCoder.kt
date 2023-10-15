package com.simiacryptus.skyenet.roblox

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import java.awt.Desktop
import java.net.URI

class AdminCommandCoder(
    applicationName: String,
    baseURL: String,
    oauthConfig: String? = null,
    temperature: Double = 0.1
) : SkyenetMacroChat(
    applicationName = applicationName,
    baseURL = baseURL,
    temperature = temperature,
    oauthConfig = oauthConfig
) {

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        sessionDiv.append("""<div>$userMessage</div>""", true)

        val model = OpenAIClient.Models.GPT4
        val response = api.chat(
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

        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

    companion object {

        const val port = 8771
        const val baseURL = "http://localhost:$port"

        @JvmStatic
        fun main(args: Array<String>) {
            val httpServer = AdminCommandCoder("RobloxLuaCoder", baseURL).start(port)
            Desktop.getDesktop().browse(URI(baseURL))
            httpServer.join()
        }
    }

}