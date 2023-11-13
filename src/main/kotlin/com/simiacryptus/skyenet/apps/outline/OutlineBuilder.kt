package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.openai.GPT4Tokenizer
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.expansionAuthor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.finalWriter
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTerminalNodeMap
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTextOutline
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.initialAuthor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Outline
import com.simiacryptus.skyenet.servers.EmbeddingVisualizer
import com.simiacryptus.skyenet.sessions.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.util.JsonUtil.toJson
import java.util.concurrent.atomic.AtomicInteger

internal open class OutlineBuilder(
    val api: OpenAIClient,
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
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
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(userMessage)}</div>""", true)
        val answer = questionSeeder.answer(*questionSeeder.chatMessages(userMessage), api = api)
        sessionDiv.append("""<div>${MarkdownUtil.renderMarkdown(answer.getText())}</div>""", verbose)
        val outline = answer.getObj()
        if (verbose) sessionDiv.append("""<pre>${toJson(outline)}</pre>""", false)

        this.userQuestion = userMessage
        root = Node(answer.getText(), outline)
        if (iterations > 0) {
            process(session, root!!, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = sessionDataStorage.getSessionDir(session.sessionId)
        sessionDir.resolve("nodes.json").writeText(toJson(nodes))
        sessionDir.resolve("relationships.json").writeText(toJson(relationships))

        val finalOutlineDiv = session.newSessionDiv(ChatSession.randomID(), ApplicationBase.spinner)
        finalOutlineDiv.append("<div>Final Outline</div>", true)
        val finalOutline = buildFinalOutline()
        if (verbose) finalOutlineDiv.append("<pre>${toJson(finalOutline)}</pre>", true)
        val textOutline = finalOutline.getTextOutline()
        finalOutlineDiv.append("<pre>$textOutline</pre>", false)
        sessionDir.resolve("finalOutline.json").writeText(toJson(finalOutline))
        sessionDir.resolve("textOutline.txt").writeText(textOutline)

        if(showProjector) {
            val projectorDiv = session.newSessionDiv(ChatSession.randomID(), ApplicationBase.spinner)
            projectorDiv.append("""<div>Embedding Projector</div>""", true)
            val response = EmbeddingVisualizer(
                api = api,
                sessionDataStorage = sessionDataStorage,
                sessionID = sessionDiv.sessionID(),
                appPath = "idea_mapper_ro",
                host = domainName
            ).writeTensorflowEmbeddingProjectorHtml(*getAllItems(finalOutline).toTypedArray())
            projectorDiv.append("""<div>$response</div>""", false)
        }

        if (writeFinalEssay) {
            val finalRenderDiv = session.newSessionDiv(ChatSession.randomID(), ApplicationBase.spinner)
            finalRenderDiv.append("<div>Final Render</div>", true)
            val finalEssay = getFinalEssay(finalOutline)
            sessionDir.resolve("finalEssay.md").writeText(finalEssay)
            finalRenderDiv.append("<div>${MarkdownUtil.renderMarkdown(finalEssay)}</div>", false)
        }
    }

    private fun getFinalEssay(
        finalOutline: Outline
    ): String = try {
        if (GPT4Tokenizer(false).estimateTokenCount(finalOutline.getTextOutline()) > (finalWriter.model.maxTokens * 0.6).toInt()) {
            explode(finalOutline)?.joinToString("\n") { getFinalEssay(it) } ?: ""
        } else {
            OutlineApp.log.debug("Outline: \n\t${finalOutline.getTextOutline().replace("\n", "\n\t")}")
            val answer = finalWriter.answer(finalOutline.getTextOutline(), api = api)
            OutlineApp.log.debug("Rendering: \n\t${answer.replace("\n", "\n\t")}")
            answer
        }
    } catch (e: Throwable) {
        OutlineApp.log.warn("Error", e)
        ""
    }


    private fun process(
        session: SessionBase,
        node: Node,
        depth: Int
    ) {
        for ((item, childNode) in node.outline.getTerminalNodeMap()) {
            Thread {
                activeThreadCounter.incrementAndGet()
                try {
                    val newNode = process(node, expandWriter, item, session) ?: return@Thread
                    synchronized(expandedOutlineNodeMap) {
                        if (!expandedOutlineNodeMap.containsKey(childNode)) {
                            expandedOutlineNodeMap[childNode] = newNode
                        } else {
                            val existingNode = expandedOutlineNodeMap[childNode]!!
                            OutlineApp.log.warn("Conflict: ${existingNode.data} vs ${newNode.data}")
                            relationships.add(Relationship(existingNode, newNode, "Conflict"))
                        }
                    }
                    if (depth > 0) process(session, newNode, depth - 1)
                } finally {
                    activeThreadCounter.decrementAndGet()
                }
            }.start()
        }
    }

    private fun process(
        parent: Node,
        actor: ParsedActor<Outline>,
        sectionName: String,
        session: SessionBase
    ): Node? {
        if (GPT4Tokenizer(false).estimateTokenCount(parent.data) <= minSize) {
            OutlineApp.log.debug("Skipping: ${parent.data}")
            return null
        }
        val newSessionDiv = session.newSessionDiv(ChatSession.randomID(), ApplicationBase.spinner)
        newSessionDiv.append("<div>Expand $sectionName</div>", true)

        val answer = actor.answer(*actor.chatMessages(userQuestion ?: "", parent.data, sectionName), api = api)
        newSessionDiv.append("<div>${MarkdownUtil.renderMarkdown(answer.getText())}</div>", verbose)
        if (verbose) newSessionDiv.append("<pre>${toJson(answer.getObj())}</pre>", false)

        val newNode = Node(answer.getText(), answer.getObj())
        nodes.add(newNode)
        relationships.add(Relationship(parent, newNode, "Expanded $sectionName"))
        return newNode
    }

}


