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
import com.simiacryptus.skyenet.webui.util.TensorflowProjector
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
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
    private val questionSeeder get() = getActor(ActorType.INITIAL) as ParsedActor<NodeList>
    private val finalWriter get() = getActor(ActorType.FINAL) as SimpleActor

    @Suppress("UNCHECKED_CAST")
    private val expandWriter get() = getActor(ActorType.EXPAND) as ParsedActor<NodeList>
    private val activeThreadCounter = AtomicInteger(0)
    private val pool = MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newCachedThreadPool())

    fun buildMap() {
        val message = ui.newMessage()
        val outlineManager = try {
            //language=HTML
            message.append("""<div class="user-message">${renderMarkdown(this.userMessage)}</div>""")
            val root = questionSeeder.answer(*questionSeeder.chatMessages(this.userMessage), api = api)
            //language=HTML
            message.append("""<div class="response-message">${renderMarkdown(root.getText())}</div>""")
            //language=HTML
            message.complete("""<pre class="verbose">${toJson(root.getObj())}</pre>""")
            OutlineManager(OutlinedText(root.getText(), root.getObj()))
        } catch (e: Exception) {
            //language=HTML
            message.complete("""<div class="error">Error: ${e.message}</div>""")
            throw e
        }

        if (iterations > 0) {
            processRecursive(outlineManager, outlineManager.rootNode, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(userId, sessionId)
        sessionDir.resolve("nodes.json").writeText(toJson(outlineManager.nodes))

        val finalOutlineMessage = ui.newMessage()
        //language=HTML
        finalOutlineMessage.append("""<div class="response-header">Final Outline</div>""")
        val finalOutline = outlineManager.buildFinalOutline()
        //language=HTML
        finalOutlineMessage.append("""<pre class="verbose">${toJson(finalOutline)}</pre>""")
        val textOutline = finalOutline.getTextOutline()
        //language=HTML
        finalOutlineMessage.complete("""<pre class="response-message">$textOutline</pre>""")
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.txt").writeText(textOutline)

        if (showProjector) {
            val projectorMessage = ui.newMessage()
            //language=HTML
            projectorMessage.append("""<div class="response-header">Embedding Projector</div>""")
            try {
                val response = TensorflowProjector(
                    api = api,
                    dataStorage = dataStorage,
                    sessionID = message.sessionID(),
                    appPath = "idea_mapper",
                    host = domainName,
                    session = ui,
                    userId = userId,
                ).writeTensorflowEmbeddingProjectorHtml(
                    *outlineManager.getLeafDescriptions(finalOutline).toTypedArray()
                )
                //language=HTML
                projectorMessage.complete("""<div class="response-message">$response</div>""")
            } catch (e: Exception) {
                log.warn("Error", e)
                //language=HTML
                projectorMessage.complete("""<div class="error">Error: ${e.message}</div>""")
            }
        }

        if (writeFinalEssay) {
            val finalRenderMessage = ui.newMessage()
            //language=HTML
            finalRenderMessage.append("""<div class="response-header">Final Render</div>""")
            try {
                val finalEssay = buildFinalEssay(finalOutline, outlineManager)
                sessionDir.resolve("finalEssay.md").writeText(finalEssay)
                //language=HTML
                finalRenderMessage.complete("""<div class="response-message">${renderMarkdown(finalEssay)}</div>""")
            } catch (e: Exception) {
                log.warn("Error", e)
                //language=HTML
                finalRenderMessage.complete("""<div class="error">Error: ${e.message}</div>""")
            }
        }
    }

    private fun buildFinalEssay(
        nodeList: NodeList,
        manager: OutlineManager
    ): String = try {
        if (GPT4Tokenizer(false).estimateTokenCount(nodeList.getTextOutline()) > (finalWriter.model.maxTokens * 0.6).toInt()) {
            manager.expandNodes(nodeList)?.joinToString("\n") { buildFinalEssay(it, manager) } ?: ""
        } else {
            finalWriter.answer(nodeList.getTextOutline(), api = api)
        }
    } catch (e: Exception) {
        log.warn("Error", e)
        ""
    }

    private fun processRecursive(
        manager: OutlineManager,
        node: OutlinedText,
        depth: Int
    ) {
        val terminalNodeMap = node.outline.getTerminalNodeMap()
        if (terminalNodeMap.isEmpty()) {
            log.warn("No terminal nodes: ${node.text}")
            ui.newMessage().complete("""<div class="error">No terminal nodes: ${node.text}</div>""")
            return
        }
        for ((item, childNode) in terminalNodeMap) {
            pool.submit {
                activeThreadCounter.incrementAndGet()
                try {
                    val newNode = processNode(node, item, manager) ?: return@submit
                    synchronized(manager.expansionMap) {
                        if (!manager.expansionMap.containsKey(childNode)) {
                            manager.expansionMap[childNode] = newNode
                        } else {
                            val existingNode = manager.expansionMap[childNode]!!
                            val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
                            log.warn(errorMessage)
                            ui.newMessage().complete("""<div class="error">$errorMessage</div>""")
                        }
                    }
                    if (depth > 0) processRecursive(manager, newNode, depth - 1)
                } catch (e: Exception) {
                    log.warn("Error in processRecursive", e)
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
    ): OutlinedText? {
        if (GPT4Tokenizer(false).estimateTokenCount(parent.text) <= minSize) {
            log.debug("Skipping: ${parent.text}")
            return null
        }
        val message = ui.newMessage()
        //language=HTML
        message.append("""<div class="response-header">Expand $sectionName</div>""")
        try {
            val answer = expandWriter.answer(*expandWriter.chatMessages(this.userMessage ?: "", parent.text, sectionName), api = api)
            //language=HTML
            message.append("""<div class="response-message">${renderMarkdown(answer.getText())}</div>""")
            //language=HTML
            message.complete("""<pre class="verbose">${toJson(answer.getObj())}</pre>""")
            val newNode = OutlinedText(answer.getText(), answer.getObj())
            outlineManager.nodes.add(newNode)
            return newNode
        } catch (e: Exception) {
            log.info("Error in outline builder ${sessionId}", e)
            message.append("""<div class="error">Error: ${e.message}</div>""")
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineBuilder::class.java)
    }

}


