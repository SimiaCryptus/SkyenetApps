package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.ClientUtil.toContentList
import com.simiacryptus.skyenet.application.ApplicationInterface
import com.simiacryptus.skyenet.application.ApplicationServer
import com.simiacryptus.skyenet.platform.Session
import com.simiacryptus.skyenet.platform.User
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil

class AdminCommandCoder(
    applicationName: String = "Roblox AdminCommand Coder",
    temperature: Double = 0.1,
    ) : ApplicationServer(
    applicationName = applicationName,
    temperature = temperature
) {

    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val sessionMessage = ui.newMessage(SocketManagerBase.randomID(), spinner, false)
        sessionMessage.append("""<div>$userMessage</div>""", true)

        val model = ChatModels.GPT4
        val response = (api as OpenAIClient).chat(
            com.simiacryptus.jopenai.ApiModel.ChatRequest(
                messages = ArrayList(
                    listOf(com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.system, content = """
                        You will convert the natural language description of an action for a Roblox game command into a Lua definition
                    """.trimIndent().toContentList()),
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.user, content = "Modify the user's walkspeed".toContentList()),
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.assistant, content = """
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
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.user, content = userMessage.toContentList())
                )
                ),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        sessionMessage.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

}