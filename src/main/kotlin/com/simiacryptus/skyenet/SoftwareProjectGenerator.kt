package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSession
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import com.simiacryptus.util.JsonUtil
import org.intellij.lang.annotations.Language

class SoftwareProjectGenerator(
    applicationName: String,
    baseURL: String,
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    baseURL = baseURL,
    temperature = temperature,
    oauthConfig = oauthConfig,
) {

    @Language("Markdown")
    private val todo = """
    
    * Separate projects/files into individual message elements
    * On review iteration, overwrite the message element
    
    """

    interface ProjectAPI {

        data class ProjectParameters(
            val title: String = "",
            val description: String = "",
            val programmingLanguage: String = "",
            val requirements: List<String> = listOf(),
        )

        fun parseProject(projectDescription: String): ProjectParameters

        fun modify(
            projectParameters: ProjectParameters, userInput: String
        ): ProjectParameters

        fun expandProject(project: ProjectParameters): FileSpecList

        data class FileSpecList(
            val items: List<FileSpec>
        )

        data class FileSpec(
            val filepath: String = "",
            val requirements: List<String> = listOf(),
        )

        fun implementFile(file: FileSpec): FileImpl

        data class FileImpl(
            val filepath: String = "",
            val language: String = "",
            val text: String = "",
        )


    }

    val projectAPI by lazy {
        ChatProxy(
            clazz = ProjectAPI::class.java, api = api, model = OpenAIClient.Models.GPT4, temperature = temperature
        ).create()
    }

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sendUpdate: (String, Boolean) -> Unit
    ) {
        try {
            sendUpdate("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            val projectParameters = projectAPI.parseProject(userMessage)
            reviewProject(session, sessionUI, projectParameters, sendUpdate, sessionId)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun reviewProject(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        projectParameters: ProjectAPI.ProjectParameters,
        sendUpdate: (String, Boolean) -> Unit,
        sessionId: String
    ) {
        iterate(sessionUI, projectParameters, sendUpdate, { feedback ->
            //language=HTML
            sendUpdate("""<div>$feedback</div>""", true)
            reviewProject(session, sessionUI, projectAPI.modify(projectParameters, feedback), sendUpdate, sessionId)
        }, "Create File Specs") {
            val sendUpdate = session.newUpdate(ChatSession.randomID(), spinner)
            sendUpdate("", true)
            val fileSpecs = projectAPI.expandProject(projectParameters)
            //language=HTML
            sendUpdate("""<div><pre>${JsonUtil.toJson(fileSpecs)}</pre></div>""", false)
            fileSpecs.items.forEach { fileSpec ->
                //language=HTML
                sendUpdate("""<div>${
                    sessionUI.hrefLink {
                        val sendUpdate = session.newUpdate(ChatSession.randomID(), spinner)
                        onFileSelect(sessionId, fileSpec, sendUpdate)
                    }
                }${fileSpec.filepath}</a></div>""", false)
            }
        }
    }

    private fun onFileSelect(
        sessionId: String, file: ProjectAPI.FileSpec, sendUpdate: (String, Boolean) -> Unit
    ) {
        //language=HTML
        sendUpdate("<hr/><div><em>${file.filepath}</em></div>", true)
        val fileImpl = projectAPI.implementFile(file)
        val fileImplText = fileImpl.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        //language=HTML
        sendUpdate("<pre>${fileImplText}</pre>", false)
        val toFile = sessionDataStorage.getSessionDir(sessionId).toPath().resolve(file.filepath).toFile()
        toFile.parentFile.mkdirs()
        toFile.writeText(fileImplText)
    }

}