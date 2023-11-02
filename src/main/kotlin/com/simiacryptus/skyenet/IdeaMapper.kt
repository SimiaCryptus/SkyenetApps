package com.simiacryptus.skyenet

import com.simiacryptus.openai.GPT4Tokenizer
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.OpenAIClient.ChatRequest
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.skyenet.IdeaMapper.API.*
import com.simiacryptus.skyenet.body.*
import com.simiacryptus.skyenet.body.ChatSessionFlexmark.Companion.renderMarkdown
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.describe.Description
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.concurrent.atomic.AtomicInteger

open class IdeaMapper(
    applicationName: String = "IdeaMapper",
    temperature: Double = 0.3,
    oauthConfig: String? = null,
) : SkyenetMacroChat(
    applicationName = applicationName,
    oauthConfig = oauthConfig,
    temperature = temperature,
) {
    override val api: OpenAIClient
        get() = OpenAIClient(logLevel = Level.DEBUG)

    inner class ActorConfig(
        val prompt: String,
        val action: String? = null,
        val model: OpenAIClient.Models = OpenAIClient.Models.GPT4,
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
            ChatRequest(
                messages = messages.toList().toTypedArray(),
                temperature = temperature,
                model = model.modelName,
            ),
            model = model
        ).choices.first().message?.content ?: throw RuntimeException("No response")
    }

    open val actors = listOf(
        ActorConfig(
            prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
            action="Expand"
        ),
    )
    open val questionSeeder = ActorConfig(
        prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
    )
    open val finalWriter = ActorConfig(
        prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
        model = OpenAIClient.Models.GPT35Turbo,
    )

    interface API {

        @Description("Break down the text into a recursive outline of the main ideas and supporting details.")
        fun toOutline(text: String): Outline

        data class Outline(
            val items: List<OutlineItem>? = null,
        ) : ValidatedObject {
            override fun validate() = items?.all { it.validate() } ?: false


            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Outline

                return items == other.items
            }

            override fun hashCode(): Int {
                return items?.hashCode() ?: 0
            }
        }

        data class OutlineItem(
            val section_name: String? = null,
            var children: Outline? = null,
            val text: String? = null,
        ) : ValidatedObject {
            override fun validate() = when {
                null == section_name -> false
                section_name.isEmpty() -> false
                else -> true
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as OutlineItem

                if (section_name != other.section_name) return false
                if (children != other.children) return false
                if (text != other.text) return false

                return true
            }

            override fun hashCode(): Int {
                var result = section_name?.hashCode() ?: 0
                result = 31 * result + (children?.hashCode() ?: 0)
                result = 31 * result + (text?.hashCode() ?: 0)
                return result
            }
        }
    }

    private val virtualAPI by lazy {
        ChatProxy(
            clazz = API::class.java,
            api = api,
            model = OpenAIClient.Models.GPT35Turbo,
            temperature = temperature
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
            sessionDiv.append("""<div>${renderMarkdown(userMessage)}</div>""", true)
            val answer = questionSeeder.answer(userMessage)
            sessionDiv.append("""<div>${renderMarkdown(answer)}</div>""", true)
            val outline = virtualAPI.toOutline(answer)
            sessionDiv.append("""<pre>${JsonUtil.toJson(outline)}</pre>""", false)
            knowledgeManager.seed(userMessage, answer, outline, session)
        } catch (e: Throwable) {
            logger.warn("Error", e)
        }
    }

    data class KnowledgeNode(
        val data: String,
        val outline: Outline,
    )

    data class NodeRelationship(
        val from: KnowledgeNode,
        val to: KnowledgeNode,
        val name: String
    )

    inner class KnowledgeManager {

        private fun Outline.setAllParents(parent: OutlineItem? = null): Outline {
            this.items?.forEach { item ->
                if(parent != null) parentMap[item] = parent
                item.children?.setAllParents(item)
            }
            return this
        }

        private var userQuestion: String? = null
        private var root: KnowledgeNode? = null
        private val relationships = mutableListOf<NodeRelationship>()
        private val nodes = mutableListOf<KnowledgeNode>()
        private val parentMap: MutableMap<OutlineItem, OutlineItem> = mutableMapOf()
        private val expandedOutlineNodeMap = mutableMapOf<OutlineItem, KnowledgeNode>()
        private val activeThreadCounter = AtomicInteger(0)

        fun buildFinalOutline(): Outline {
            val clonedRoot = root?.outline?.deepClone()
            replaceWithExpandedNodes(clonedRoot)
            return clonedRoot ?: Outline()
        }

        private fun replaceWithExpandedNodes(node: Outline?) {
            node?.items?.forEach { item ->
                if (expandedOutlineNodeMap.containsKey(item)) {
                    item.children = expandedOutlineNodeMap[item]?.outline?.deepClone()
                }
                replaceWithExpandedNodes(item.children)
            }
        }

        private fun Outline.deepClone(): Outline = Outline(this.items?.map { it.deepClone() })

        private fun OutlineItem.deepClone() = OutlineItem(
            section_name = this.section_name,
            children = this.children?.deepClone(),
            text = this.text
        )

        private fun Outline.getTextOutline(): String {
            val sb = StringBuilder()
            items?.forEach { item ->
                sb.append(item.getTextOutline().trim())
                sb.append("\n")
            }
            return sb.toString()
        }

        private fun OutlineItem.getTextOutline(): String {
            val sb = StringBuilder()
            sb.append((text?.replace("\n", "\\n") ?: section_name)?.trim() ?: "")
            sb.append("\n")
            children?.items?.forEach { item ->
                sb.append(item.getTextOutline().replace("\n", "\n\t").trim())
                sb.append("\n")
            }
            return sb.toString()
        }

        fun seed(
            userQuestion: String,
            answer: String,
            outline: Outline,
            session: SessionBase
        ) {
            this.userQuestion = userQuestion
            root = KnowledgeNode(answer, outline.setAllParents())
            process(session, root!!)
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish

            val newSessionDiv = session.newSessionDiv(ChatSession.randomID(), spinner)
            newSessionDiv.append("<div>Final Outline</div>", true)
            sessionDataStorage.getSessionDir(session.sessionId).resolve("nodes.json").writeText(JsonUtil.toJson(nodes))
            val finalOutline = buildFinalOutline()
            sessionDataStorage.getSessionDir(session.sessionId).resolve("relationships.json").writeText(JsonUtil.toJson(relationships))
            sessionDataStorage.getSessionDir(session.sessionId).resolve("finalOutline.json").writeText(JsonUtil.toJson(finalOutline))

            newSessionDiv.append("<pre>${JsonUtil.toJson(finalOutline)}</pre>", true)
            val textOutline = finalOutline.getTextOutline()
            newSessionDiv.append("<pre>$textOutline</pre>", true)
            sessionDataStorage.getSessionDir(session.sessionId).resolve("textOutline.txt").writeText(textOutline)

            val finalEssay = getFinalEssay(textOutline, finalOutline)
            sessionDataStorage.getSessionDir(session.sessionId).resolve("finalEssay.md").writeText(finalEssay)

            newSessionDiv.append("<div>${renderMarkdown(finalEssay)}</div>", false)

        }

        private fun getFinalEssay(
            textOutline: String,
            finalOutline: Outline
        ): String = if (GPT4Tokenizer(false).estimateTokenCount(textOutline) <= (8192 * 1.2).toInt()) {
                finalWriter.answer(textOutline)
            } else {
                finalOutline.items?.joinToString("\n") { item ->
                    getFinalEssay(item.getTextOutline(), item.children ?: Outline())
                } ?: ""
            }

        private fun Outline.getTerminalNodeMap(): Map<String, OutlineItem> = items?.flatMap { item ->
            val children = item.children
            if(children?.items?.isEmpty() ?: true) listOf(item.section_name!! to item)
            else children?.getTerminalNodeMap()?.map { (key, value) -> item.section_name + " / " + key to value } ?: listOf(item.section_name!! to item)
        }?.toMap() ?: emptyMap()

        private fun process(
            session: SessionBase,
            node: KnowledgeNode,
            depth: Int = 1
        ) {
            for ((item, childNode) in node.outline.getTerminalNodeMap()) {
                for (actor in actors) {
                    Thread {
                        activeThreadCounter.incrementAndGet()
                        try {
                            val newNode = process(node, actor, item, session)
                            if(actor.action == "Expand") {
                                if (!expandedOutlineNodeMap.containsKey(childNode)) {
                                    expandedOutlineNodeMap[childNode] = newNode
                                } else {
                                    val existingNode = expandedOutlineNodeMap[childNode]!!
                                    log.warn("Conflict: ${existingNode.data} vs ${newNode.data}")
                                    relationships.add(NodeRelationship(existingNode, newNode, "Conflict"))
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
            parent: KnowledgeNode,
            actor: ActorConfig,
            section_name: String,
            session: SessionBase
        ): KnowledgeNode {
            val newSessionDiv = session.newSessionDiv(ChatSession.randomID(), spinner)
            val action = actor.action!!
            newSessionDiv.append("<div>$action $section_name</div>", true)

            val answer = actor.answer(*actor.chatMessages(parent.data, section_name))
            newSessionDiv.append("<div>${renderMarkdown(answer)}</div>", true)
            val outline = virtualAPI.toOutline(answer).setAllParents()
            newSessionDiv.append("<pre>${JsonUtil.toJson(outline)}</pre>", false)

            val newNode = KnowledgeNode(answer, outline)
            nodes.add(newNode)
            relationships.add(NodeRelationship(parent, newNode, "$action " + section_name))

            return newNode
        }

    }

    private val knowledgeManager = KnowledgeManager()

    companion object {
        val log = LoggerFactory.getLogger(IdeaMapper::class.java)
    }


}