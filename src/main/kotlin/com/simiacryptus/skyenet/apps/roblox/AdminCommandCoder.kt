package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.ClientUtil.toContentList
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil

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
        val message = ui.newMessage()
        message.echo(userMessage)

        val model = ChatModels.GPT4
        val response = (api as OpenAIClient).chat(
            ApiModel.ChatRequest(
                messages = ArrayList(
                    listOf(
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.system, content = """
                        You will convert the natural language description of an action for a Roblox game command into a Lua definition
                    """.trimIndent().toContentList()
                        ),
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.user,
                            content = "Modify the user's walkspeed".toContentList()
                        ),
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.assistant, content = """
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
                    """.trimIndent().toContentList()
                        ),
                        ApiModel.ChatMessage(role = ApiModel.Role.user, content = userMessage.toContentList())
                    )
                ),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        message.add(MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: ""))
    }

}