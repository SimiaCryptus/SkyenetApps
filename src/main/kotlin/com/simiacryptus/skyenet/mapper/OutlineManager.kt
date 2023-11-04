package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.GPT4Tokenizer
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.skyenet.mapper.OutlineAPI.*
import com.simiacryptus.skyenet.mapper.OutlineAPI.Companion.deepClone
import com.simiacryptus.skyenet.mapper.OutlineAPI.Companion.getTerminalNodeMap
import com.simiacryptus.skyenet.mapper.OutlineAPI.Companion.getTextOutline
import com.simiacryptus.util.JsonUtil
import java.util.concurrent.atomic.AtomicInteger

class OutlineManager(
    val api: OpenAIClient,
    val virtualAPI: OutlineAPI = ChatProxy(
        clazz = OutlineAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = 0.1,
    ).create(),
    val verbose: Boolean,
    val sessionDataStorage: SessionDataStorage,
    val questionSeeder: ActorConfig = ActorConfig(
        api = api,
        prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
        model = OpenAIClient.Models.GPT4,
    ),
    val finalWriter: ActorConfig = ActorConfig(
        api,
        prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
        model = OpenAIClient.Models.GPT4,
    ),
    val actors: List<ActorConfig> = listOf(
        ActorConfig(
            api,
            action = "Expand",
            prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
            minTokens = 70, // Do not expand if the data is too short
            model = OpenAIClient.Models.GPT35Turbo
        ),
    ),
) {

    class ActorConfig(
        val api : OpenAIClient = OpenAIClient(),
        val prompt: String,
        val action: String? = null,
        val model: OpenAIClient.Models = OpenAIClient.Models.GPT35Turbo,
        val minTokens: Int = 0,
        val temperature: Double = 0.3,
    ) {

        fun answer(vararg questions: String): String = answer(*chatMessages(*questions))

        fun chatMessages(vararg questions: String) = arrayOf(
            OpenAIClient.ChatMessage(
                role = OpenAIClient.ChatMessage.Role.system,
                content = prompt
            ),
        ) + questions.map {
            OpenAIClient.ChatMessage(
                role = OpenAIClient.ChatMessage.Role.user,
                content = it
            )
        }

        fun answer(vararg messages: OpenAIClient.ChatMessage): String = api.chat(
            OpenAIClient.ChatRequest(
                messages = messages.toList().toTypedArray(),
                temperature = temperature,
                model = model.modelName,
            ),
            model = model
        ).choices.first().message?.content ?: throw RuntimeException("No response")
    }
    data class Node(
        val data: String,
        val outline: Outline,
    )

    data class Relationship(
        val from: Node,
        val to: Node,
        val name: String
    )

    private fun Outline.setAllParents(parent: Item? = null): Outline {
        this.items?.forEach { item ->
            if(parent != null) parentMap[item] = parent
            item.children?.setAllParents(item)
        }
        return this
    }

    private var userQuestion: String? = null
    private var root: Node? = null
    private val relationships = mutableListOf<Relationship>()
    private val nodes = mutableListOf<Node>()
    private val parentMap: MutableMap<Item, Item> = mutableMapOf()
    private val expandedOutlineNodeMap = mutableMapOf<Item, Node>()
    private val activeThreadCounter = AtomicInteger(0)

    private fun buildFinalOutline(): Outline {
        val clonedRoot = root?.outline?.deepClone()
        replaceWithExpandedNodes(clonedRoot)
        return clonedRoot ?: Outline()
    }

    private fun replaceWithExpandedNodes(node: Outline?) {
        node?.items?.forEach { item ->
            val expandedOutline = expandedOutlineNodeMap[item]?.outline?.deepClone()
            if (expandedOutline == node) return@forEach
            if (expandedOutline != null) {
                if(1 == expandedOutline.items?.size) {
                    item.children = expandedOutline.items.first().children
                } else if ((expandedOutline.items?.size ?: 0) > 1) {
                    item.children = expandedOutline
                } else {
                    // No expansion
                }
            }
            replaceWithExpandedNodes(item.children)
        }
    }

    fun buildMap(
        userMessage: String,
        session: SessionBase,
        sessionDiv: SessionDiv,
        domainName: String
    ) {
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(userMessage)}</div>""", true)
        val answer = questionSeeder.answer(userMessage)
        sessionDiv.append("""<div>${ChatSessionFlexmark.renderMarkdown(answer)}</div>""", verbose)
        val outline = virtualAPI.toOutline(answer)
        if(verbose) sessionDiv.append("""<pre>${JsonUtil.toJson(outline)}</pre>""", false)

        this.userQuestion = userQuestion
        root = Node(answer, outline.setAllParents())
        process(session, root!!)
        while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
        while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish

        val finalOutlineDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        finalOutlineDiv.append("<div>Final Outline</div>", true)
        sessionDataStorage.getSessionDir(session.sessionId).resolve("nodes.json").writeText(
            JsonUtil.toJson(nodes)
        )
        sessionDataStorage.getSessionDir(session.sessionId).resolve("relationships.json").writeText(
            JsonUtil.toJson(relationships)
        )
        val finalOutline = buildFinalOutline()
        sessionDataStorage.getSessionDir(session.sessionId).resolve("finalOutline.json").writeText(
            JsonUtil.toJson(finalOutline)
        )

        val list = getAllItems(finalOutline)
        val projectorDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        projectorDiv.append("""<div>Embedding Projector</div>""", true)
        val response = EmbeddingVisualizer(
            api = api,
            sessionDataStorage = sessionDataStorage,
            sessionID = sessionDiv.sessionID(),
            appPath = "idea_mapper",
            host = domainName
        ).writeTensorflowEmbeddingProjectorHtml(*list.toTypedArray())
        projectorDiv.append("""<div>$response</div>""", false)

        if(verbose) finalOutlineDiv.append("<pre>${JsonUtil.toJson(finalOutline)}</pre>", true)
        val textOutline = finalOutline.getTextOutline()
        finalOutlineDiv.append("<pre>$textOutline</pre>", false)
        sessionDataStorage.getSessionDir(session.sessionId).resolve("textOutline.txt").writeText(textOutline)

        val finalRenderDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        finalRenderDiv.append("<div>Final Render</div>", true)
        val finalEssay = getFinalEssay(finalOutline)
        sessionDataStorage.getSessionDir(session.sessionId).resolve("finalEssay.md").writeText(finalEssay)

        finalRenderDiv.append("<div>${ChatSessionFlexmark.renderMarkdown(finalEssay)}</div>", false)
    }

    private fun getAllItems(outline: Outline): List<String>  = outline.items?.flatMap { getAllItems(it) } ?: listOf()

    private fun getAllItems(outline: Item): List<String> = listOf(outline.text ?: "") + (outline.children?.items?.flatMap { getAllItems(it) } ?: listOf())

    private fun getFinalEssay(
        finalOutline: Outline
    ): String = try {
        if (GPT4Tokenizer(false).estimateTokenCount(finalOutline.getTextOutline()) > (finalWriter.model.maxTokens * 0.6).toInt()) {
            explode(finalOutline)?.joinToString("\n") { getFinalEssay(it) } ?: ""
        } else {
            OutlineMapper.log.debug("Outline: \n\t${finalOutline.getTextOutline().replace("\n", "\n\t")}")
            val answer = finalWriter.answer(finalOutline.getTextOutline())
            OutlineMapper.log.debug("Rendering: \n\t${answer.replace("\n", "\n\t")}")
            answer
        }
    } catch (e: Throwable) {
        OutlineMapper.log.warn("Error", e)
        ""
    }

    private fun explode(item: Item): List<Item> {
        val size = item.children?.items?.size ?: 0
        if(size > 1) return item.children?.items?.map { item ->
            Item(
                section_name = item.section_name,
                text = item.text,
                children = Outline(listOf(item.deepClone())),
            )
        } ?: listOf() else if (size == 0) {
            return listOf(item)
        } else {
            // size == 1
            val child = item.children?.items?.first() ?: return listOf(item)
            val explodedChild = explode(child)
            return explodedChild.map { item ->
                Item(
                    section_name = item.section_name,
                    text = item.text,
                    children = Outline(listOf(item.deepClone())),
                )
            }
        }
    }

    private fun explode(outline: Outline): List<Outline>? {
        val size = outline.items?.size ?: 0
        if(size > 1) return outline.items?.map { item ->
            Outline(listOf(item.deepClone()))
        }
        else if (size == 0) {
            return listOf(outline)
        } else {
            // size == 1
            val child = outline.items?.first() ?: return listOf(outline)
            val explodedChild = explode(child)
            return explodedChild.map { item ->
                Outline(listOf(item.deepClone()))
            }
        }
    }

    private fun process(
        session: SessionBase,
        node: Node,
        depth: Int = 1
    ) {
        for ((item, childNode) in node.outline.getTerminalNodeMap()) {
            for (actor in actors) {
                Thread {
                    activeThreadCounter.incrementAndGet()
                    try {
                        val newNode = process(node, actor, item, session)
                        if(newNode == null) return@Thread
                        if(actor.action == "Expand") {
                            if (!expandedOutlineNodeMap.containsKey(childNode)) {
                                expandedOutlineNodeMap[childNode] = newNode
                            } else {
                                val existingNode = expandedOutlineNodeMap[childNode]!!
                                OutlineMapper.log.warn("Conflict: ${existingNode.data} vs ${newNode.data}")
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
        actor: ActorConfig,
        section_name: String,
        session: SessionBase
    ): Node? {
        // if token count of parent.data is below 128, skip
        if (GPT4Tokenizer(false).estimateTokenCount(parent.data) <= actor.minTokens) {
            OutlineMapper.log.debug("Skipping: ${parent.data}")
            return null
        }
        val newSessionDiv = session.newSessionDiv(ChatSession.randomID(), SkyenetSessionServerBase.spinner)
        val action = actor.action!!
        newSessionDiv.append("<div>$action $section_name</div>", true)

        val answer = actor.answer(*actor.chatMessages(userQuestion ?: "", parent.data, section_name))
        newSessionDiv.append("<div>${ChatSessionFlexmark.renderMarkdown(answer)}</div>",
            verbose
        )
        val outline = virtualAPI.toOutline(answer).setAllParents()
        if(verbose) newSessionDiv.append("<pre>${JsonUtil.toJson(outline)}</pre>", false)

        val newNode = Node(answer, outline)
        nodes.add(newNode)
        relationships.add(Relationship(parent, newNode, "$action " + section_name))

        return newNode
    }


}