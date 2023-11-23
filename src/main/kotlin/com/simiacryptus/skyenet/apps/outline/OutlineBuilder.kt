package com.simiacryptus.skyenet.apps.outline

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.GPT4Tokenizer
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.outline.OutlineActors.ActorType
import com.simiacryptus.skyenet.apps.outline.OutlineManager.NodeList
import com.simiacryptus.skyenet.apps.outline.OutlineManager.OutlinedText
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionMessage
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.util.TensorflowProjector
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class OutlineBuilder(
    val api: API,
    dataStorage: DataStorage,
    session: Session,
    userId: User?,
    temperature: Double,
    private val iterations: Int,
    private val minSize: Int,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean,
    val userMessage: String,
    val ui: ApplicationInterface,
    val domainName: String
) : ActorSystem<ActorType>(OutlineActors.actorMap(temperature), dataStorage, userId, session) {
    init {
        require(iterations > -1)
    }

    @Suppress("UNCHECKED_CAST")
    private val initial get() = getActor(ActorType.INITIAL) as ParsedActor<NodeList>
    private val summary get() = getActor(ActorType.FINAL) as SimpleActor

    @Suppress("UNCHECKED_CAST")
    private val expand get() = getActor(ActorType.EXPAND) as ParsedActor<NodeList>
    private val activeThreadCounter = AtomicInteger(0)
    private val pool = MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newCachedThreadPool())

    fun buildMap() {
        val message = ui.newMessage()
        val outlineManager = try {
            message.echo(renderMarkdown(this.userMessage))
            val root = initial.answer(*initial.chatMessages(this.userMessage), api = api)
            message.add(renderMarkdown(root.getText()))
            message.verbose(toJson(root.getObj()))
            message.complete()
            OutlineManager(OutlinedText(root.getText(), root.getObj()))
        } catch (e: Exception) {
            message.error(e)
            throw e
        }

        if (iterations > 0) {
            processRecursive(outlineManager, outlineManager.rootNode, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(user, session)
        sessionDir.resolve("nodes.json").writeText(toJson(outlineManager.nodes))

        val finalOutlineMessage = ui.newMessage()
        finalOutlineMessage.header("Final Outline")
        val finalOutline = outlineManager.buildFinalOutline()
        finalOutlineMessage.verbose(toJson(finalOutline))
        val textOutline = finalOutline.getTextOutline()
        finalOutlineMessage.complete(renderMarkdown(textOutline))
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.md").writeText(textOutline)

        if (showProjector) {
            val projectorMessage = ui.newMessage()
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
            val finalRenderMessage = ui.newMessage()
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

    private val tokenizer = GPT4Tokenizer(false)
    private fun buildFinalEssay(
        nodeList: NodeList,
        manager: OutlineManager
    ): String = if (tokenizer.estimateTokenCount(nodeList.getTextOutline()) > (summary.model.maxTokens * 0.6).toInt()) {
        manager.expandNodes(nodeList)?.joinToString("\n") { buildFinalEssay(it, manager) } ?: ""
    } else {
        summary.answer(nodeList.getTextOutline(), api = api)
    }

    private fun processRecursive(
        manager: OutlineManager,
        node: OutlinedText,
        depth: Int
    ) {
        val terminalNodeMap = node.outline.getTerminalNodeMap()
        if (terminalNodeMap.isEmpty()) {
            val errorMessage = "No terminal nodes: ${node.text}"
            log.warn(errorMessage)
            ui.newMessage().error(errorMessage)
            return
        }
        for ((item, childNode) in terminalNodeMap) {
            pool.submit {
                activeThreadCounter.incrementAndGet()
                val message = ui.newMessage()
                try {
                    val newNode = processNode(node, item, manager, message) ?: return@submit
                    synchronized(manager.expansionMap) {
                        if (!manager.expansionMap.containsKey(childNode)) {
                            manager.expansionMap[childNode] = newNode
                        } else {
                            val existingNode = manager.expansionMap[childNode]!!
                            val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
                            log.warn(errorMessage)
                            ui.newMessage().error(errorMessage)
                        }
                    }
                    message.complete()
                    if (depth > 0) processRecursive(manager, newNode, depth - 1)
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
        message: SessionMessage,
    ): OutlinedText? {
        if (tokenizer.estimateTokenCount(parent.text) <= minSize) {
            log.debug("Skipping: ${parent.text}")
            return null
        }
        message.header("Expand $sectionName")
        val answer = expand.answer(*expand.chatMessages(this.userMessage, parent.text, sectionName), api = api)
        message.add(renderMarkdown(answer.getText()))
        message.verbose(toJson(answer.getObj()))
        val newNode = OutlinedText(answer.getText(), answer.getObj())
        outlineManager.nodes.add(newNode)
        return newNode
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineBuilder::class.java)
    }

}


