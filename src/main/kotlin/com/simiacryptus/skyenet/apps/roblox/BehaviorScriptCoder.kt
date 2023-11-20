package com.simiacryptus.skyenet.apps.roblox

import com.simiacryptus.openai.models.ChatModels
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.OpenAIClientBase.Companion.toContentList
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.chat.ChatSocket
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil

class BehaviorScriptCoder(
    applicationName: String = "Roblox BehaviorScript Coder",
    temperature: Double = 0.1,
) : ApplicationBase(
    applicationName = applicationName,
    temperature = temperature
) {

    override fun processMessage(
        sessionId: String,
        userId: String?,
        userMessage: String,
        session: ApplicationSession,
        sessionDiv: SessionDiv,
        socket: ChatSocket,
    ) {
        sessionDiv.append("""<div>$userMessage</div>""", true)

        val model = ChatModels.GPT4
        val response = socket.api.chat(
            OpenAIClient.ChatRequest(
                messages = ArrayList(listOf(
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.system, content = """
                        You will convert the natural language description of an behavior for a Roblox game script into a Lua definition
                    """.trimIndent().toContentList()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.user, content = "Kill the player on touch".toContentList()),
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.assistant, content = """
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
                    OpenAIClient.ChatMessage(role = OpenAIClient.Role.user, content = userMessage.toContentList())
                )),
                temperature = temperature,
                model = model.modelName,
            ), model
        )

        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(response.choices.get(0).message?.content ?: "")}</div>""", true)
    }

}