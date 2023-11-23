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

class BehaviorScriptCoder(
    applicationName: String = "Roblox BehaviorScript Coder",
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
        api: API,
    ) {
        val message = ui.newMessage()
        message.append("""<div>$userMessage</div>""")

        val model = ChatModels.GPT4
        val response = (api as OpenAIClient).chat(
            ApiModel.ChatRequest(
                messages = ArrayList(
                    listOf(
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.system, content = """
                        You will convert the natural language description of an behavior for a Roblox game script into a Lua definition
                    """.trimIndent().toContentList()
                        ),
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.user,
                            content = "Kill the player on touch".toContentList()
                        ),
                        ApiModel.ChatMessage(
                            role = ApiModel.Role.assistant, content = """
                        ```lua
                        function handlePart(part)
                        	part.Touched:Connect(function(hit)
                        		local humanoid = hit.Parent:FindFirstChild('Humanoid')
                        		if humanoid then
                        			humanoid.Health = 0
                        		end
                        	end)
                        end

                        function handleNode(model)
                        	for i, part in pairs(model:GetChildren()) do
                        		if part:IsA('Part') then
                        			handlePart(part)
                        		else
                        			handleNode(part)
                        		end
                        	end
                        end

                        handleNode(script.Parent)
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

        message.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""")
    }

}