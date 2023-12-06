package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.GPT4Tokenizer
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.outline.OutlineActors.ActorType
import com.simiacryptus.skyenet.apps.outline.OutlineManager.NodeList
import com.simiacryptus.skyenet.apps.outline.OutlineManager.OutlinedText
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.util.TensorflowProjector
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class OutlineAgent(
    val api: API,
    dataStorage: StorageInterface,
    session: Session,
    user: User?,
    temperature: Double,
    val models: List<ChatModels>,
    private val minSize: Int,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean,
    val userMessage: String,
    val ui: ApplicationInterface,
    val domainName: String
) : ActorSystem<ActorType>(OutlineActors.actorMap(temperature), dataStorage, user, session) {
    init {
        require(models.isNotEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private val initial get() = getActor(ActorType.INITIAL) as ParsedActor<NodeList>
    private val summary get() = getActor(ActorType.FINAL) as SimpleActor

    @Suppress("UNCHECKED_CAST")
    private val expand get() = getActor(ActorType.EXPAND) as ParsedActor<NodeList>
    private val activeThreadCounter = AtomicInteger(0)
    private val tokenizer = GPT4Tokenizer(false)

    fun buildMap() {
        val message = ui.newTask()
        val outlineManager = try {
            message.echo(renderMarkdown(this.userMessage))
            val root = initial.answer(listOf(this.userMessage), api = api)
            message.add(renderMarkdown(root.text))
            message.verbose(toJson(root.obj))
            message.complete()
            OutlineManager(OutlinedText(root.text, root.obj))
        } catch (e: Exception) {
            message.error(e)
            throw e
        }

        if (models.isNotEmpty()) {
            processRecursive(outlineManager, outlineManager.rootNode, models)
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(user, session)
        sessionDir.resolve("nodes.json").writeText(toJson(outlineManager.nodes))

        val finalOutlineMessage = ui.newTask()
        finalOutlineMessage.header("Final Outline")
        val finalOutline = outlineManager.buildFinalOutline()
        finalOutlineMessage.verbose(toJson(finalOutline))
        val textOutline = finalOutline.getTextOutline()
        finalOutlineMessage.complete(renderMarkdown(textOutline))
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.md").writeText(textOutline)

        if (showProjector) {
            val projectorMessage = ui.newTask()
            projectorMessage.header("Embedding Projector")
            try {
                val response = TensorflowProjector(
                    api = api,
                    dataStorage = dataStorage,
                    sessionID = session,
                    appPath = "idea_mapper",
                    host = domainName,
                    session = ui,
                    userId = user,
                ).writeTensorflowEmbeddingProjectorHtml(
                    *outlineManager.getLeafDescriptions(finalOutline).toTypedArray()
                )
                projectorMessage.complete(response)
            } catch (e: Exception) {
                log.warn("Error", e)
                projectorMessage.error(e)
            }
        }

        if (writeFinalEssay) {
            val finalRenderMessage = ui.newTask()
            finalRenderMessage.header("Final Render")
            try {
                val finalEssay = buildFinalEssay(finalOutline, outlineManager)
                sessionDir.resolve("finalEssay.md").writeText(finalEssay)
                finalRenderMessage.complete(renderMarkdown(finalEssay))
            } catch (e: Exception) {
                log.warn("Error", e)
                finalRenderMessage.error(e)
            }
        }
    }

    private fun buildFinalEssay(
        nodeList: NodeList,
        manager: OutlineManager
    ): String = if (tokenizer.estimateTokenCount(nodeList.getTextOutline()) > (summary.model.maxTokens * 0.6).toInt()) {
        manager.expandNodes(nodeList)?.joinToString("\n") { buildFinalEssay(it, manager) } ?: ""
    } else {
        summary.answer(listOf(nodeList.getTextOutline()), api = api)
    }

    private fun processRecursive(
        manager: OutlineManager,
        node: OutlinedText,
        models: List<ChatModels>
    ) {
        val terminalNodeMap = node.outline.getTerminalNodeMap()
        if (terminalNodeMap.isEmpty()) {
            val errorMessage = "No terminal nodes: ${node.text}"
            log.warn(errorMessage)
            ui.newTask().error(RuntimeException(errorMessage))
            return
        }
        for ((item, childNode) in terminalNodeMap) {
            pool.submit {
                activeThreadCounter.incrementAndGet()
                val message = ui.newTask()
                try {
                    val newNode = processNode(node, item, manager, message, models.first()) ?: return@submit
                    synchronized(manager.expansionMap) {
                        if (!manager.expansionMap.containsKey(childNode)) {
                            manager.expansionMap[childNode] = newNode
                        } else {
                            val existingNode = manager.expansionMap[childNode]!!
                            val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
                            log.warn(errorMessage)
                            ui.newTask().error(RuntimeException(errorMessage))
                        }
                    }
                    message.complete()
                    if (models.size > 1) processRecursive(manager, newNode, models.drop(1))
                } catch (e: Exception) {
                    log.warn("Error in processRecursive", e)
                    message.error(e)
                } finally {
                    activeThreadCounter.decrementAndGet()
                }
            }
        }
    }

    private fun processNode(
        parent: OutlinedText,
        sectionName: String,
        outlineManager: OutlineManager,
        message: SessionTask,
        model: ChatModels,
    ): OutlinedText? {
        if (tokenizer.estimateTokenCount(parent.text) <= minSize) {
            log.debug("Skipping: ${parent.text}")
            return null
        }
        message.header("Expand $sectionName")
        val answer = expand.withModel(model).answer(listOf(this.userMessage, parent.text, sectionName), api = api)
        message.add(renderMarkdown(answer.text))
        message.verbose(toJson(answer.obj))
        val newNode = OutlinedText(answer.text, answer.obj)
        outlineManager.nodes.add(newNode)
        return newNode
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineAgent::class.java)
    }

}


