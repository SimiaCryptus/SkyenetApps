package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ClientUtil.toContentList
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
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
        val sessionMessage = ui.newMessage(SocketManagerBase.randomID(), spinner, false)
        sessionMessage.append("""<div>$userMessage</div>""", true)

        val model = ChatModels.GPT4
        val response = (api as OpenAIClient).chat(
            com.simiacryptus.jopenai.ApiModel.ChatRequest(
                messages = ArrayList(listOf(
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.system, content = """
                        You will convert the natural language description of an behavior for a Roblox game script into a Lua definition
                    """.trimIndent().toContentList()),
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.user, content = "Kill the player on touch".toContentList()),
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.assistant, content = """
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
                    """.trimIndent().toContentList()),
                    com.simiacryptus.jopenai.ApiModel.ChatMessage(role = com.simiacryptus.jopenai.ApiModel.Role.user, content = userMessage.toContentList())
                )),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        sessionMessage.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

}