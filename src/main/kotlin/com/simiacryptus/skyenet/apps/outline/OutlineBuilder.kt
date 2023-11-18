package com.simiacryptus.skyenet.apps.outline

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.openai.GPT4Tokenizer
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.ApplicationBase
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.expansionAuthor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.finalWriter
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTerminalNodeMap
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTextOutline
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.initialAuthor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Outline
import com.simiacryptus.skyenet.config.DataStorage
import com.simiacryptus.skyenet.util.EmbeddingVisualizer
import com.simiacryptus.skyenet.session.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.util.JsonUtil.toJson
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

open class OutlineBuilder(
    val api: OpenAIClient,
    val dataStorage: DataStorage,
    private val iterations: Int,
    private val temperature: Double,
    private val questionSeeder: ParsedActor<Outline> = initialAuthor(temperature),
    private val finalWriter: SimpleActor = finalWriter(temperature),
    private val expandWriter: ParsedActor<Outline> = expansionAuthor(temperature),
    private val minSize: Int,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean,
) : OutlineManager() {
    init {
        require(iterations > -1)
    }

    private var userQuestion: String? = null

    private val activeThreadCounter = AtomicInteger(0)

    fun buildMap(
        userMessage: String,
        session: SessionBase,
        sessionDiv: SessionDiv,
        domainName: String
    ) {
        //language=HTML
        sessionDiv.append("""<div class="user-message">${MarkdownUtil.renderMarkdown(userMessage)}</div>""", true)
        val answer = questionSeeder.answer(*questionSeeder.chatMessages(userMessage), api = api)
        //language=HTML
        sessionDiv.append("""<div class="response-message">${MarkdownUtil.renderMarkdown(answer.getText())}</div>""", true)
        val outline = answer.getObj()
        //language=HTML
        sessionDiv.append("""<pre class='verbose'>${toJson(outline)}</pre>""", false)

        this.userQuestion = userMessage
        root = Node(answer.getText(), outline)
        if (iterations > 0) {
            process(session, root!!, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(session.userId, session.sessionId)
        sessionDir.resolve("nodes.json").writeText(toJson(nodes))
        sessionDir.resolve("relationships.json").writeText(toJson(relationships))

        val finalOutlineDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner)
        //language=HTML
        finalOutlineDiv.append("""<div class="response-header">Final Outline</div>""", true)
        val finalOutline = buildFinalOutline()
        //language=HTML
        finalOutlineDiv.append("""<pre class='verbose'>${toJson(finalOutline)}</pre>""", true)
        val textOutline = finalOutline.getTextOutline()
        //language=HTML
        finalOutlineDiv.append("""<pre class='response-message'>$textOutline</pre>""", false)
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.txt").writeText(textOutline)

        if (showProjector) {
            val projectorDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner)
            //language=HTML
            projectorDiv.append("""<div class="response-header">Embedding Projector</div>""", true)
            val response = EmbeddingVisualizer(
                api = api,
                dataStorage = dataStorage,
                sessionID = sessionDiv.sessionID(),
                appPath = "idea_mapper",
                host = domainName,
                session = session
            ).writeTensorflowEmbeddingProjectorHtml(*getAllItems(finalOutline).toTypedArray())
            //language=HTML
            projectorDiv.append("""<div class="response-message">$response</div>""", false)
        }

        if (writeFinalEssay) {
            val finalRenderDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner)
            //language=HTML
            finalRenderDiv.append("""<div class="response-header">Final Render</div>""", true)
            val finalEssay = getFinalEssay(finalOutline)
            sessionDir.resolve("finalEssay.md").writeText(finalEssay)
            //language=HTML
            finalRenderDiv.append("""<div class="response-message">${MarkdownUtil.renderMarkdown(finalEssay)}</div>""", false)
        }
    }

    private fun getFinalEssay(
        finalOutline: Outline
    ): String = try {
        if (GPT4Tokenizer(false).estimateTokenCount(finalOutline.getTextOutline()) > (finalWriter.model.maxTokens * 0.6).toInt()) {
            explode(finalOutline)?.joinToString("\n") { getFinalEssay(it) } ?: ""
        } else {
            log.debug("Outline: \n\t${finalOutline.getTextOutline().replace("\n", "\n\t")}")
            val answer = finalWriter.answer(finalOutline.getTextOutline(), api = api)
            log.debug("Rendering: \n\t${answer.replace("\n", "\n\t")}")
            answer
        }
    } catch (e: Exception) {
        log.warn("Error", e)
        ""
    }

    val pool = MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newCachedThreadPool())

    private fun process(
        session: SessionBase,
        node: Node,
        depth: Int
    ) {
        for ((item, childNode) in node.outline.getTerminalNodeMap()) {
            pool.submit {
                activeThreadCounter.incrementAndGet()
                try {
                    val newNode = process(node, expandWriter, item, session) ?: return@submit
                    synchronized(expandedOutlineNodeMap) {
                        if (!expandedOutlineNodeMap.containsKey(childNode)) {
                            expandedOutlineNodeMap[childNode] = newNode
                        } else {
                            val existingNode = expandedOutlineNodeMap[childNode]!!
                            log.warn("Conflict: ${existingNode.data} vs ${newNode.data}")
                            relationships.add(Relationship(existingNode, newNode, "Conflict"))
                        }
                    }
                    if (depth > 0) process(session, newNode, depth - 1)
                } finally {
                    activeThreadCounter.decrementAndGet()
                }
            }
        }
    }

    private fun process(
        parent: Node,
        actor: ParsedActor<Outline>,
        sectionName: String,
        session: SessionBase
    ): Node? {
        if (GPT4Tokenizer(false).estimateTokenCount(parent.data) <= minSize) {
            log.debug("Skipping: ${parent.data}")
            return null
        }
        val newSessionDiv = session.newSessionDiv(SessionBase.randomID(), ApplicationBase.spinner)
        //language=HTML
        newSessionDiv.append("""<div class="response-header">Expand $sectionName</div>""", true)

        val answer = actor.answer(*actor.chatMessages(userQuestion ?: "", parent.data, sectionName), api = api)
        //language=HTML
        newSessionDiv.append("""<div class="response-message">${MarkdownUtil.renderMarkdown(answer.getText())}</div>""", true)
        //language=HTML
        newSessionDiv.append("""<pre class="verbose">${toJson(answer.getObj())}</pre>""", false)

        val newNode = Node(answer.getText(), answer.getObj())
        nodes.add(newNode)
        relationships.add(Relationship(parent, newNode, "Expanded $sectionName"))
        return newNode
    }
    companion object {
        private val log = LoggerFactory.getLogger(OutlineBuilder::class.java)
    }

}


