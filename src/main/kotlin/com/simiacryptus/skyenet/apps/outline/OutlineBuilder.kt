package com.simiacryptus.skyenet.apps.outline

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.GPT4Tokenizer
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.outline.OutlineActors.ActorType
import com.simiacryptus.skyenet.apps.outline.OutlineManager.NodeList
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.DataStorage
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SocketManagerBase
import com.simiacryptus.skyenet.webui.util.EmbeddingVisualizer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class OutlineBuilder(
    val api: API,
    dataStorage: DataStorage,
    private val iterations: Int,
    private val temperature: Double,
    private val minSize: Int,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean,
    userId: User?,
    session: Session
) : ActorSystem<ActorType>(OutlineActors.actorMap(temperature), dataStorage, userId, session) {
    init {
        require(iterations > -1)
    }

    @Suppress("UNCHECKED_CAST")
    private val questionSeeder get() = getActor(ActorType.INITIAL) as ParsedActor<NodeList>
    private val finalWriter get() = getActor(ActorType.FINAL) as SimpleActor

    @Suppress("UNCHECKED_CAST")
    private val expandWriter get() = getActor(ActorType.EXPAND) as ParsedActor<NodeList>

    private var userQuestion: String? = null
    private val activeThreadCounter = AtomicInteger(0)
    private val pool = MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newCachedThreadPool())

    fun buildMap(
        userMessage: String,
        ui: ApplicationInterface,
        domainName: String
    ) {
        val sessionMessage = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner, false)
        //language=HTML
        sessionMessage.append("""<div class="user-message">${renderMarkdown(userMessage)}</div>""", true)
        val answer = questionSeeder.answer(*questionSeeder.chatMessages(userMessage), api = api)
        //language=HTML
        sessionMessage.append(
            """<div class="response-message">${renderMarkdown(answer.getText())}</div>""",
            true
        )
        val outline = answer.getObj()
        //language=HTML
        sessionMessage.append("""<pre class="verbose">${toJson(outline)}</pre>""", false)

        this.userQuestion = userMessage
        val outlineManager = OutlineManager(OutlineManager.OutlinedText(answer.getText(), outline))
        if (iterations > 0) {
            process(ui, outlineManager, outlineManager.rootNode, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(userId, sessionId)
        sessionDir.resolve("nodes.json").writeText(toJson(outlineManager.nodes))

        val finalOutlineDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner)
        //language=HTML
        finalOutlineDiv.append("""<div class="response-header">Final Outline</div>""", true)
        val finalOutline = outlineManager.buildFinalOutline()
        //language=HTML
        finalOutlineDiv.append("""<pre class="verbose">${toJson(finalOutline)}</pre>""", true)
        val textOutline = finalOutline.getTextOutline()
        //language=HTML
        finalOutlineDiv.append("""<pre class="response-message">$textOutline</pre>""", false)
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.txt").writeText(textOutline)

        if (showProjector) {
            val projectorDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner)
            //language=HTML
            projectorDiv.append("""<div class="response-header">Embedding Projector</div>""", true)
            val response = EmbeddingVisualizer(
                api = api,
                dataStorage = dataStorage,
                sessionID = sessionMessage.sessionID(),
                appPath = "idea_mapper",
                host = domainName,
                session = ui,
                userId = userId,
            ).writeTensorflowEmbeddingProjectorHtml(*outlineManager.getLeafDescriptions(finalOutline).toTypedArray())
            //language=HTML
            projectorDiv.append("""<div class="response-message">$response</div>""", false)
        }

        if (writeFinalEssay) {
            val finalRenderDiv = ui.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner)
            //language=HTML
            finalRenderDiv.append("""<div class="response-header">Final Render</div>""", true)
            val finalEssay = getFinalEssay(finalOutline, outlineManager)
            sessionDir.resolve("finalEssay.md").writeText(finalEssay)
            //language=HTML
            finalRenderDiv.append(
                """<div class="response-message">${renderMarkdown(finalEssay)}</div>""",
                false
            )
        }
    }

    private fun getFinalEssay(
        nodeList: NodeList,
        manager: OutlineManager
    ): String = try {
        if (GPT4Tokenizer(false).estimateTokenCount(nodeList.getTextOutline()) > (finalWriter.model.maxTokens * 0.6).toInt()) {
            manager.expandNodes(nodeList)?.joinToString("\n") { getFinalEssay(it, manager) } ?: ""
        } else {
            log.debug("Outline: \n\t${nodeList.getTextOutline().replace("\n", "\n\t")}")
            val answer = finalWriter.answer(nodeList.getTextOutline(), api = api)
            log.debug("Rendering: \n\t${answer.replace("\n", "\n\t")}")
            answer
        }
    } catch (e: Exception) {
        log.warn("Error", e)
        ""
    }

    private fun process(
        session: ApplicationInterface,
        manager : OutlineManager,
        node: OutlineManager.OutlinedText,
        depth: Int
    ) {
        for ((item, childNode) in node.outline.getTerminalNodeMap()) {
            pool.submit {
                activeThreadCounter.incrementAndGet()
                try {
                    val newNode = process(node, expandWriter, item, session, manager) ?: return@submit
                    synchronized(manager.expansionMap) {
                        if (!manager.expansionMap.containsKey(childNode)) {
                            manager.expansionMap[childNode] = newNode
                        } else {
                            val existingNode = manager.expansionMap[childNode]!!
                            log.warn("Conflict: ${existingNode.text} vs ${newNode.text}")
                        }
                    }
                    if (depth > 0) process(session, manager, newNode, depth - 1)
                } finally {
                    activeThreadCounter.decrementAndGet()
                }
            }
        }
    }

    private fun process(
        parent: OutlineManager.OutlinedText,
        actor: ParsedActor<NodeList>,
        sectionName: String,
        session: ApplicationInterface,
        outlineManager : OutlineManager,
    ): OutlineManager.OutlinedText? {
        if (GPT4Tokenizer(false).estimateTokenCount(parent.text) <= minSize) {
            log.debug("Skipping: ${parent.text}")
            return null
        }
        val newSessionDiv = session.newMessage(SocketManagerBase.randomID(), ApplicationServer.spinner)
        //language=HTML
        newSessionDiv.append("""<div class="response-header">Expand $sectionName</div>""", true)

        val answer = actor.answer(*actor.chatMessages(userQuestion ?: "", parent.text, sectionName), api = api)
        //language=HTML
        newSessionDiv.append(
            """<div class="response-message">${renderMarkdown(answer.getText())}</div>""",
            true
        )
        //language=HTML
        newSessionDiv.append("""<pre class="verbose">${toJson(answer.getObj())}</pre>""", false)

        val newNode = OutlineManager.OutlinedText(answer.getText(), answer.getObj())
        outlineManager.nodes.add(newNode)
        return newNode
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineBuilder::class.java)
    }

}


