package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.util.GPT4Tokenizer
import com.simiacryptus.jopenai.describe.JsonDescriber
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.OutlineManager.NodeList
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.util.TensorflowProjector
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

open class OutlineApp(
    applicationName: String = "Outline Expansion Concept Map v1.1",
    val domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/idea_mapper",
) {
    override val description: String
        @Language("HTML")
        get() = ("<div>" + renderMarkdown(
            """
          The Outline Agent is an AI-powered tool for exploring concepts via outline creation and expansion.
          
          Here's how it works:
          
          1. **Generate Initial Outline**: Provide your main idea or topic, and the Outline Agent will create an initial outline.
          2. **Iterative Expansion**: The agent then expands on each section of your outline, adding depth and detail.
          3. **Construct Final Outline**: Once your outline is fully expanded, the agent can compile it into a single outline. This presents the information in a clear and concise manner, making it easy to review.
          4. **Visualize Embeddings**: Each section of your outline is represented as a vector in a high-dimensional space. The Outline Agent uses an Embedding Projector to visualize these vectors, allowing you to explore the relationships between different ideas and concepts.
          5. **Customizable Experience**: You can set the number of iterations and the model used for each to control the depth and price, making it possible to generate sizable outputs.
          
          Start your journey into concept space today with the Outline Agent! 📝✨
          """.trimIndent()
        ) + "</div>")

    data class Settings(
        val models: List<ChatModel> = listOf(
            OpenAIModels.GPT4o,
            OpenAIModels.GPT4oMini
        ),
        val parsingModel: ChatModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.3,
        val minTokensForExpansion: Int = 16,
        val showProjector: Boolean = true,
        val writeFinalEssay: Boolean = false,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings<Settings>(session, user)!!
        OutlineAgent(
            api = api,
            dataStorage = dataStorage,
            session = session,
            user = user,
            temperature = settings.temperature,
            models = settings.models.drop(1),
            firstLevelModel = settings.models.first(),
            parsingModel = settings.parsingModel,
            minSize = settings.minTokensForExpansion,
            writeFinalEssay = settings.writeFinalEssay,
            showProjector = settings.showProjector,
            userMessage = userMessage,
            ui = ui,
        ).buildMap()
    }

}

class OutlineAgent(
    val api: API,
    dataStorage: StorageInterface,
    session: Session,
    user: User?,
    temperature: Double,
    val models: List<ChatModel>,
    val firstLevelModel: ChatModel,
    val parsingModel: ChatModel,
    private val minSize: Int,
    val writeFinalEssay: Boolean,
    val showProjector: Boolean,
    val userMessage: String,
    val ui: ApplicationInterface
) : ActorSystem<OutlineActors.ActorType>(
    OutlineActors.actorMap(temperature, firstLevelModel, parsingModel).map { it.key.name to it.value }.toMap(),
    dataStorage,
    user,
    session
) {
    private val tabbedDisplay = TabbedDisplay(ui.newTask())

    init {
        require(models.isNotEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private val initial get() = getActor(OutlineActors.ActorType.INITIAL) as ParsedActor<NodeList>
    private val summary get() = getActor(OutlineActors.ActorType.FINAL) as SimpleActor

    @Suppress("UNCHECKED_CAST")
    private val expand get() = getActor(OutlineActors.ActorType.EXPAND) as ParsedActor<NodeList>
    private val activeThreadCounter = AtomicInteger(0)
    private val tokenizer = GPT4Tokenizer(false)

    fun buildMap() {
        val message = ui.newTask(false)
        tabbedDisplay["Content"] = message.placeholder
        val outlineManager = try {
            message.echo(renderMarkdown(this.userMessage, ui = ui))
            val root = initial.answer(listOf(this.userMessage), api = api)
            message.add(renderMarkdown(root.text, ui = ui))
            message.verbose(JsonUtil.toJson(root.obj))
            message.complete()
            OutlineManager(OutlineManager.OutlinedText(root.text, root.obj))
        } catch (e: Exception) {
            message.error(ui, e)
            throw e
        }

        if (models.isNotEmpty()) {
            processRecursive(outlineManager, outlineManager.rootNode, models, message)
            while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
            while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
        }

        val sessionDir = dataStorage.getSessionDir(user, session)
        sessionDir.resolve("nodes.json").writeText(JsonUtil.toJson(outlineManager.nodes))

        val finalOutlineMessage = ui.newTask(false)
        tabbedDisplay["Outline"] = finalOutlineMessage.placeholder
        finalOutlineMessage.header("Final Outline")
        val finalOutline = outlineManager.buildFinalOutline()
        finalOutlineMessage.verbose(JsonUtil.toJson(finalOutline))
        val textOutline = finalOutline?.let { NodeList(it) }?.getTextOutline() ?: ""
        finalOutlineMessage.complete(renderMarkdown(textOutline, ui = ui))
        sessionDir.resolve("finalOutline.json").writeText(JsonUtil.toJson(finalOutline))
        sessionDir.resolve("textOutline.md").writeText(textOutline)

        if (showProjector) {
            val projectorMessage = ui.newTask(false)
            tabbedDisplay["Projector"] = projectorMessage.placeholder
            projectorMessage.header("Embedding Projector")
            try {
                val response = TensorflowProjector(
                    api = api,
                    dataStorage = dataStorage,
                    sessionID = session,
                    session = ui,
                    userId = user,
                ).writeTensorflowEmbeddingProjectorHtml(
                    *outlineManager.getLeafDescriptions(finalOutline?.let { NodeList(it) }!!).toTypedArray()
                )
                projectorMessage.complete(response)
            } catch (e: Exception) {
                log.warn("Error", e)
                projectorMessage.error(ui, e)
            }
        }

        if (writeFinalEssay) {
            val finalRenderMessage = ui.newTask(false)
            tabbedDisplay["Final Essay"] = finalRenderMessage.placeholder
            finalRenderMessage.header("Final Render")
            try {
                val finalEssay = buildFinalEssay(finalOutline?.let { NodeList(it) }!!, outlineManager)
                sessionDir.resolve("finalEssay.md").writeText(finalEssay)
                finalRenderMessage.complete(renderMarkdown(finalEssay, ui = ui))
            } catch (e: Exception) {
                log.warn("Error", e)
                finalRenderMessage.error(ui, e)
            }
        }
        tabbedDisplay.update()
    }

    private fun buildFinalEssay(
        nodeList: NodeList,
        manager: OutlineManager
    ): String =
        if (tokenizer.estimateTokenCount(nodeList.getTextOutline()) > (summary.model.maxTotalTokens * 0.6).toInt()) {
            manager.expandNodes(nodeList)?.joinToString("\n") { buildFinalEssay(it, manager) } ?: ""
        } else {
            summary.answer(listOf(nodeList.getTextOutline()), api = api)
        }

    private fun processRecursive(
        manager: OutlineManager,
        node: OutlineManager.OutlinedText,
        models: List<ChatModel>,
        task: SessionTask
    ) {
        val tabbedDisplay = TabbedDisplay(task)
        val terminalNodeMap = node.outline.getTerminalNodeMap()
        if (terminalNodeMap.isEmpty()) {
            val errorMessage = "No terminal nodes: ${node.text}"
            log.warn(errorMessage)
            task.error(ui, RuntimeException(errorMessage))
            return
        }
        for ((item, childNode) in terminalNodeMap) {
            activeThreadCounter.incrementAndGet()
            val message = ui.newTask(false)
            tabbedDisplay[item] = message.placeholder
            pool.submit {
                try {
                    val newNode = processNode(node, item, manager, message, models.first()) ?: return@submit
                    synchronized(manager.expansionMap) {
                        if (!manager.expansionMap.containsKey(childNode)) {
                            manager.expansionMap[childNode] = newNode
                        } else {
                            val existingNode = manager.expansionMap[childNode]!!
                            val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
                            log.warn(errorMessage)
                            message.error(ui, RuntimeException(errorMessage))
                        }
                    }
                    if (models.size > 1) processRecursive(manager, newNode, models.drop(1), message)
                } catch (e: Exception) {
                    log.warn("Error in processRecursive", e)
                    message.error(ui, e)
                } finally {
                    activeThreadCounter.decrementAndGet()
                }
            }
        }
        task.complete()
    }

    private fun processNode(
        parent: OutlineManager.OutlinedText,
        sectionName: String,
        outlineManager: OutlineManager,
        message: SessionTask,
        model: ChatModel,
    ): OutlineManager.OutlinedText? {
        if (tokenizer.estimateTokenCount(parent.text) <= minSize) {
            log.debug("Skipping: ${parent.text}")
            return null
        }
        message.header("Expand $sectionName")
        val answer = expand.withModel(model).answer(listOf(this.userMessage, parent.text, sectionName), api = api)
        message.add(renderMarkdown(answer.text, ui = ui))
        message.verbose(JsonUtil.toJson(answer.obj), false)
        val newNode = OutlineManager.OutlinedText(answer.text, answer.obj)
        outlineManager.nodes.add(newNode)
        return newNode
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutlineAgent::class.java)
    }

}

interface OutlineActors {

    enum class ActorType {
        INITIAL,
        EXPAND,
        FINAL,
    }

    companion object {

        val log = LoggerFactory.getLogger(OutlineActors::class.java)

        fun actorMap(temperature: Double, firstLevelModel: ChatModel, parsingModel: ChatModel) = mapOf(
            ActorType.INITIAL to initialAuthor(temperature, firstLevelModel, parsingModel),
            ActorType.EXPAND to expansionAuthor(temperature, parsingModel),
            ActorType.FINAL to finalWriter(temperature, firstLevelModel),
        )

        private fun initialAuthor(temperature: Double, model: ChatModel, parsingModel: ChatModel) = ParsedActor(
            NodeList::class.java,
            prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
            model = model,
            temperature = temperature,
            parsingModel = parsingModel,
            describer = object : JsonDescriber(
                mutableSetOf("com.simiacryptus", "com.github.simiacryptus")
            ) {
                override val includeMethods: Boolean get() = false
            },
            exampleInstance = exampleNodeList(),
        )

        private fun exampleNodeList() = NodeList(
            listOf(
                OutlineManager.Node(name = "Main Idea", description = "Main Idea Description"),
                OutlineManager.Node(
                    name = "Supporting Idea",
                    description = "Supporting Idea Description",
                    children = listOf(
                        OutlineManager.Node(name = "Sub Idea", description = "Sub Idea Description")
                    )
                )
            )
        )

        private fun expansionAuthor(
            temperature: Double,
            parsingModel: ChatModel
        ): ParsedActor<NodeList> =
            ParsedActor(
                resultClass = NodeList::class.java,
                prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
                name = "Expand",
                model = parsingModel,
                temperature = temperature,
                parsingModel = parsingModel,
                exampleInstance = exampleNodeList(),
            )

        private fun finalWriter(temperature: Double, model: ChatModel) = SimpleActor(
            prompt = """You are a helpful writing assistant. Transform the outline into a well written essay. Do not summarize. Use markdown for formatting.""",
            model = model,
            temperature = temperature,
        )

    }
}