package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.util.JsonUtil

class SoftwareProjectGenerator(
    applicationName: String = "SoftwareProjectGenerator",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {

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

        fun implementFile(projectParameters: ProjectParameters, file: FileSpec): FileImpl

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
        sessionDiv: SessionDiv
    ) {
        try {
            sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
            val projectParameters = projectAPI.parseProject(userMessage)
            reviewProject(session, sessionUI, projectParameters, sessionDiv, sessionId)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    private fun reviewProject(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        projectParameters: ProjectAPI.ProjectParameters,
        sessionDiv: SessionDiv,
        sessionId: String
    ) {
        iterate(sessionUI, sessionDiv, projectParameters, { projectParameters: ProjectAPI.ProjectParameters, feedback: String ->
            //language=HTML
            sessionDiv.append("""<div>$feedback</div>""", true)
            reviewProject(session, sessionUI, projectAPI.modify(projectParameters, feedback), sessionDiv, sessionId)
        }, mapOf("Create File Specs" to { projectParameters: ProjectAPI.ProjectParameters ->
            projectToFiles(session, sessionUI, projectParameters, sessionDiv, sessionId)
        }))
    }

    private fun projectToFiles(
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        projectParameters: ProjectAPI.ProjectParameters,
        sessionDiv: SessionDiv,
        sessionId: String
    ) {
        val sessionDiv = session.newSessionDiv(ChatSession.randomID(), spinner)
        sessionDiv.append("", true)
        val fileSpecs = projectAPI.expandProject(projectParameters)
        //language=HTML
        sessionDiv.append("""<div><pre>${JsonUtil.toJson(fileSpecs)}</pre></div>""", false)
        fileSpecs.items.forEach { fileSpec ->
            //language=HTML
            sessionDiv.append("""<div>${
                sessionUI.hrefLink {
                    val sessionDiv = session.newSessionDiv(ChatSession.randomID(), spinner)
                    onFileSelect(sessionId, projectParameters, fileSpec, sessionDiv)
                }
            }${fileSpec.filepath}</a></div>""", false)
        }
    }

    private fun onFileSelect(
        sessionId: String,
        projectParameters: ProjectAPI.ProjectParameters,
        file: ProjectAPI.FileSpec,
        sessionDiv: SessionDiv
    ) {
        //language=HTML
        sessionDiv.append("<hr/><div><em>${file.filepath}</em></div>", true)
        val fileImpl = projectAPI.implementFile(projectParameters, file)
        val fileImplText = fileImpl.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        //language=HTML
        sessionDiv.append("<pre>${fileImplText}</pre>", false)
        val toFile = sessionDataStorage.getSessionDir(sessionId).toPath().resolve(file.filepath).toFile()
        toFile.parentFile.mkdirs()
        toFile.writeText(fileImplText)
    }

}