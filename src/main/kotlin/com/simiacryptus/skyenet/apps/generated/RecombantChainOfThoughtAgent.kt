package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.PoolSystem
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface


open class RecombantChainOfThoughtAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: ChatModel = OpenAIModels.GPT35Turbo,
    temperature: Double = 0.3,
) : PoolSystem(dataStorage, user, session) {


    val queryRefiner = SimpleActor(
        prompt = """
You are a Query Refiner. Your task is to take an initial user query and refine it for clarity and precision. Identify any ambiguities or unclear parts and generate clarification questions to improve the query. Once the user provides responses, refine the query accordingly.
        """.trimMargin().trim(),
        name = "QueryRefiner",
        model = OpenAIModels.GPT35Turbo,
        temperature = 0.3
    )


    data class QueryComponents(
        @Description("List of query components")
        val components: List<String> = listOf()
    ) : ValidatedObject {
        override fun validate() = when {
            components.isEmpty() -> "components list is required"
            else -> null
        }
    }

    val queryDecomposer = ParsedActor<QueryComponents>(
        resultClass = QueryComponents::class.java,
        model = OpenAIModels.GPT35Turbo,
        prompt = """
You are a Query Decomposer. Your task is to analyze a refined query and break it down into smaller, manageable components. Identify logical parts and decompose the query accordingly.
            """.trimMargin().trim()
    )


    val componentAnalyzer = SimpleActor(
        prompt = """
            You are a Component Analyzer. Your task is to analyze a given query component. Preprocess the component, perform analysis using NLP techniques, and generate insights for the component.
        """.trimIndent(),
        name = "ComponentAnalyzer",
        model = OpenAIModels.GPT35Turbo,
        temperature = 0.3
    )


    data class CombinedInsights(
        @Description("The combined insights from different query components")
        val combinedInsights: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            combinedInsights.isNullOrBlank() -> "combinedInsights is required"
            else -> null
        }
    }

    val insightRecombiner = ParsedActor<CombinedInsights>(
        resultClass = CombinedInsights::class.java,
        model = OpenAIModels.GPT35Turbo,
        prompt = """
You are an Insight Recombiner. Your task is to integrate insights from different query components while maintaining contextual coherence. Generate a comprehensive response by combining these insights.
            """.trimMargin().trim(),
    )


    val responseGenerator = SimpleActor(
        prompt = """
You are a Response Generator. Your task is to take combined insights and formulate a coherent and comprehensive response. Use Natural Language Generation (NLG) techniques to generate the response and format it for presentation.
        """.trimMargin().trim(),
        name = "ResponseGenerator",
        model = OpenAIModels.GPT35Turbo,
        temperature = 0.3
    )


    val textAnalyzer = CodingActor(
        interpreterClass = KotlinInterpreter::class,
        symbols = mapOf(
            "preprocess" to { text: String ->
                // Implementation of text preprocessing
                text.lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "")
            },
            "performSentimentAnalysis" to { text: String ->
                // Implementation of sentiment analysis (dummy implementation)
                if (text.contains("good")) 1.0 else if (text.contains("bad")) -1.0 else 0.0
            },
            "extractKeyTopics" to { text: String ->
                // Implementation of key topic extraction (dummy implementation)
                text.split(" ").distinct().filter { it.length > 3 }
            }
        ),
        details = """
You are a Text Analyzer. Your task is to analyze input text for sentiment and key topics.
Defined functions:
* preprocess(text: String): String - Preprocesses the input text by normalizing and removing noise.
* performSentimentAnalysis(text: String): Double - Analyzes the sentiment of the text and returns a score between -1 and 1.
* extractKeyTopics(text: String): List<String> - Extracts key topics from the text.

Expected code structure:
* The main function should be `analyze(text: String): AnalysisResult`.
* Use the defined functions to preprocess the text, perform sentiment analysis, and extract key topics.
        """.trimMargin().trim(),
        model = OpenAIModels.GPT4o,
        temperature = 0.1
    )


    fun aiAgentSystemUsingGPTActors(userQuery: String) {
        val task = ui.newTask()
        try {
            task.header("AI Agent System Using GPT Actors")
            task.add("Starting the AI agent system to process the user query.")

            // Step 1: Refine the user query
            task.add("Step 1: Refining the user query.")
            val refinedQuery = queryRefiner(userQuery)
            task.add("Refined Query: $refinedQuery")

            // Step 2: Decompose the refined query into components
            task.add("Step 2: Decomposing the refined query into components.")
            val queryComponents = queryDecomposer(refinedQuery)
            task.add("Query Components: ${queryComponents.joinToString(", ")}")

            // Step 3: Analyze each query component
            task.add("Step 3: Analyzing each query component.")
            val componentAnalyses = queryComponents.map { component ->
                componentAnalyzer(component)
            }
            task.add("Component Analyses: ${componentAnalyses.joinToString("\n")}")

            // Step 4: Recombine insights from component analyses
            task.add("Step 4: Recombining insights from component analyses.")
            val combinedInsights = insightRecombiner(componentAnalyses)
            task.add("Combined Insights: $combinedInsights")

            // Step 5: Generate the final response
            task.add("Step 5: Generating the final response.")
            val finalResponse = responseGenerator(combinedInsights)
            task.add("Final Response: $finalResponse")

            // Display the final response
            task.add("AI Agent System has successfully processed the query.")
            task.add("Final Response: $finalResponse")

            task.complete()
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun componentAnalyzer(queryComponent: String): String {
        val task = ui.newTask()
        return try {
            task.header("Component Analysis")
            task.add("Analyzing the query component for insights.")

            // Query component to be analyzed
            task.add("Query Component: $queryComponent")

            // Analyzing the component using the ComponentAnalyzer actor
            val analysisResult = componentAnalyzer.answer(listOf(queryComponent), api = api)

            // Displaying the analysis result
            task.add("Analysis Result: $analysisResult")

            task.complete()
            analysisResult
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun insightRecombiner(componentAnalyses: List<String>): String {
        val task = ui.newTask()
        return try {
            task.header("Insight Recombination")
            task.add("Combining insights from different query components while maintaining context.")

            // Displaying the component analyses
            task.add("Component Analyses: ${componentAnalyses.joinToString("\n")}")

            // Combining insights using the InsightRecombiner actor
            val recombinationResult = insightRecombiner.answer(componentAnalyses, api = api)

            // Displaying the combined insights
            val combinedInsights = recombinationResult.obj.combinedInsights
            task.add("Combined Insights: $combinedInsights")

            task.complete()
            combinedInsights ?: throw IllegalStateException("Combined insights are null")
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun queryDecomposer(refinedQuery: String): List<String> {
        val task = ui.newTask()
        return try {
            task.header("Query Decomposition")
            task.add("Decomposing the refined query into smaller, manageable components.")

            // Refined query
            task.add("Refined Query: $refinedQuery")

            // Decomposing the query using the QueryDecomposer actor
            val decompositionResult = queryDecomposer.answer(listOf(refinedQuery), api = api)

            // Displaying the decomposed components
            val components = decompositionResult.obj.components
            task.add("Decomposed Components: ${components.joinToString(", ")}")

            task.complete()
            components
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun responseGenerator(combinedInsights: String): String {
        val task = ui.newTask()
        return try {
            task.header("Response Generation")
            task.add("Generating a coherent and comprehensive response based on combined insights.")

            // Combined insights
            task.add("Combined Insights: $combinedInsights")

            // Generating the final response using the ResponseGenerator actor
            val finalResponse = responseGenerator.answer(listOf(combinedInsights), api = api)

            // Displaying the final response
            task.add("Final Response: $finalResponse")

            task.complete()
            finalResponse
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun queryRefiner(userQuery: String): String {
        val task = ui.newTask()
        return try {
            task.header("Query Refinement")
            task.add("Refining the user query for clarity and precision.")

            // Initial user query
            task.add("User Query: $userQuery")

            // Refining the query using the QueryRefiner actor
            val refinedQuery = queryRefiner.answer(listOf(userQuery), api = api)

            // Displaying the refined query
            task.add("Refined Query: $refinedQuery")

            task.complete()
            refinedQuery
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(RecombantChainOfThoughtAgent::class.java)

    }
}
