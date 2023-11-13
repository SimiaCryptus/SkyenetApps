package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.openai.GPT4Tokenizer
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.actors
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.finalWriter
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTerminalNodeMap
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.getTextOutline
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.questionSeeder
import com.simiacryptus.skyenet.apps.outline.OutlineActors.Outline
import com.simiacryptus.skyenet.servers.EmbeddingVisualizer
import com.simiacryptus.skyenet.sessions.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.util.JsonUtil
import java.util.concurrent.atomic.AtomicInteger

internal open class OutlineBuilder(
    val api: OpenAIClient,
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
    private val iterations: Int,
    private val questionSeeder: ParsedActor<Outline> = questionSeeder(),
    private val finalWriter: SimpleActor = finalWriter(),
    private val actors: List<ParsedActor<Outline>> = actors(),
    private val minSize: Int = 128,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean = true,
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
        if (verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(outline)}</pre>""", false)

        this.userQuestion = userMessage
        root = Node(answer.getText(), outline.setAllParents())
        if (iterations > 0) {
            process(session, root!!, (iterations - 1))
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        sessionDataStorage.getSessionDir(session.sessionId).resolve("nodes.json").writeText(
            JsonUtil.toJson(nodes)
        )
        sessionDataStorage.getSessionDir(session.sessionId).resolve("relationships.json").writeText(
            JsonUtil.toJson(relationships)
        )

        val finalOutlineDiv = session.newSessionDiv(ChatSession.randomID(), ApplicationBase.spinner)
        finalOutlineDiv.append("<div>Final Outline</div>", true)
        val finalOutline = buildFinalOutline()
        if (verbose) finalOutlineDiv.append("<pre>${JsonUtil.toJson(finalOutline)}</pre>", true)
        val textOutline = finalOutline.getTextOutline()
        finalOutlineDiv.append("<pre>$textOutline</pre>", false)
        sessionDataStorage.getSessionDir(session.sessionId).resolve("finalOutline.json").writeText(
            JsonUtil.toJson(finalOutline)
        )
        sessionDataStorage.getSessionDir(session.sessionId).resolve("textOutline.txt").writeText(textOutline)

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
            sessionDataStorage.getSessionDir(session.sessionId).resolve("finalEssay.md").writeText(finalEssay)
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
            for (actor in actors) {
                Thread {
                    activeThreadCounter.incrementAndGet()
                    try {
                        val newNode = process(node, actor, item, session)
                        if (newNode == null) return@Thread
                        if (actor.name == "Expand") {
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
        val action = actor.name!!
        newSessionDiv.append("<div>$action $sectionName</div>", true)

        val answer = actor.answer(*actor.chatMessages(userQuestion ?: "", parent.data, sectionName), api = api)
        newSessionDiv.append(
            "<div>${MarkdownUtil.renderMarkdown(answer.getText())}</div>",
            verbose
        )
        val outline = answer.getObj().setAllParents()
        if (verbose) newSessionDiv.append("<pre>${JsonUtil.toJson(outline)}</pre>", false)

        val newNode = Node(answer.getText(), outline)
        nodes.add(newNode)
        relationships.add(Relationship(parent, newNode, "$action " + sectionName))

        return newNode
    }

}


