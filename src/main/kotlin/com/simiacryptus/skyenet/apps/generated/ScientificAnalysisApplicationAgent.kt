package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.webui.application.ApplicationInterface


open class ScientificAnalysisApplicationAgent(
    val ui: ApplicationInterface,
    val api: API,
) {

    data class TopicKeywords(
        @Description("List of extracted keywords from the topic")
        val keywords: List<String>? = null
    ) : ValidatedObject {
        override fun validate() = when {
            keywords.isNullOrEmpty() -> "keywords are required"
            else -> null
        }
    }

    private val topicParser by lazy {

        val topicParser = ParsedActor(
            resultClass = TopicKeywords::class.java,
            model = OpenAIModels.GPT35Turbo,
            prompt = """
            You are an NLP expert. Your task is to parse the given topic text and extract key elements such as keywords. Ensure the keywords are relevant and significant to the topic.
        """.trimIndent(),
            name = "TopicParser"
        )
        topicParser
    }

    data class Hypotheses(
        @Description("List of generated hypotheses")
        val hypotheses: List<String>? = null
    ) : ValidatedObject {
        override fun validate() = when {
            hypotheses.isNullOrEmpty() -> "hypotheses are required"
            else -> null
        }
    }

    private val hypothesisGenerator by lazy {

        val hypothesisGenerator = ParsedActor(
            resultClass = Hypotheses::class.java,
            model = OpenAIModels.GPT35Turbo,
            prompt = """
            You are a scientific research assistant. Based on the provided keywords, generate a list of plausible and diverse scientific hypotheses. Ensure each hypothesis is clear and concise.
        """.trimIndent()
        )
        hypothesisGenerator
    }

    data class ComparativeAnalysis(
        @Description("The comparative analysis of hypotheses")
        val analysis: List<Map<String, String>>? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            analysis.isNullOrEmpty() -> "analysis is required"
            else -> null
        }
    }

    private val criticalExaminer by lazy {

        val criticalExaminer = ParsedActor(
            resultClass = ComparativeAnalysis::class.java,
            model = OpenAIModels.GPT35Turbo,
            prompt = """
    You are a scientific reviewer. Evaluate the given hypotheses based on feasibility, novelty, and impact. Provide a comparative analysis for each hypothesis, identifying potential biases and limitations.
    """.trimIndent(),
        )
        criticalExaminer
    }

    data class ExperimentIdeas(
        @Description("List of experiment ideas with details")
        val experiments: List<Map<String, String>> = listOf()
    ) : ValidatedObject {
        override fun validate() = when {
            experiments.isEmpty() -> "experiments list is required"
            else -> null
        }
    }

    private val experimentSuggester by lazy {

        val experimentSuggester = ParsedActor(
            resultClass = ExperimentIdeas::class.java,
            model = OpenAIModels.GPT35Turbo,
            prompt = """
            You are a scientific experiment designer. For each given hypothesis, generate detailed experiment ideas. 
            Include methodology, required resources, potential challenges, and expected outcomes.
        """.trimIndent()
        )
        experimentSuggester
    }

    data class FeedbackAnalysis(
        @Description("The feedback provided by the user")
        val feedback: Map<String, String>? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            feedback.isNullOrEmpty() -> "feedback is required"
            else -> null
        }
    }

    private val feedbackProcessor by lazy {

        val feedbackProcessor = ParsedActor(
            resultClass = FeedbackAnalysis::class.java,
            model = OpenAIModels.GPT35Turbo,
            prompt = """
            You are a feedback analyzer. Process the provided user feedback to refine the hypotheses and experiment suggestions. Update the internal models and knowledge bases accordingly.
        """.trimIndent()
        )
        feedbackProcessor
    }

    fun scientificAnalysisApplication(topic: String) {
        val task = ui.newTask()
        try {
            task.header("Scientific Analysis Application")
            task.add("Starting analysis for topic: $topic")

            // Step 1: Parse the topic to extract keywords
            task.add("Step 1: Parsing Topic")
            val parsedKeywords = parseTopic(topic)
            task.add("Extracted Keywords: ${parsedKeywords.keywords?.joinToString(", ")}")

            // Step 2: Generate hypotheses based on the extracted keywords
            task.add("Step 2: Generating Hypotheses")
            val generatedHypotheses = generateHypotheses(parsedKeywords.keywords ?: listOf())
            task.add("Generated Hypotheses: ${generatedHypotheses.hypotheses?.joinToString(", ")}")

            // Step 3: Critically examine and compare the generated hypotheses
            task.add("Step 3: Examining Hypotheses")
            val comparativeAnalysis = examineHypotheses(generatedHypotheses.hypotheses ?: listOf())
            task.add("Comparative Analysis: ${comparativeAnalysis.analysis?.joinToString(", ")}")

            // Step 4: Suggest experiments to investigate each hypothesis
            task.add("Step 4: Suggesting Experiments")
            val experimentIdeas = suggestExperiments(generatedHypotheses.hypotheses ?: listOf())
            task.add("Suggested Experiments: ${experimentIdeas.experiments.joinToString(", ")}")

            // Step 5: Collect and process user feedback
            task.add("Step 5: Collecting Feedback")
            val feedback =
                mapOf("exampleFeedbackKey" to "exampleFeedbackValue") // Placeholder for actual feedback collection
            processFeedback(feedback)
            task.add("Processed Feedback Successfully")

            task.complete()
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun parseTopic(topic: String): TopicKeywords {
        val task = ui.newTask()
        return try {
            task.header("Parsing Topic")
            task.add("Received topic: $topic")

            // Use the topicParser actor to parse the topic
            val response = topicParser.answer(listOf(topic), api = api)
            task.add("Parsed keywords: ${response.text}")

            // Validate and return the parsed keywords
            val parsedKeywords = response.obj
            task.complete()
            parsedKeywords
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun examineHypotheses(hypotheses: List<String>): ComparativeAnalysis {
        val task = ui.newTask()
        return try {
            task.header("Examining Hypotheses")
            task.add("Received hypotheses: ${hypotheses.joinToString(", ")}")

            // Use the criticalExaminer actor to examine the hypotheses
            val response = criticalExaminer.answer(hypotheses, api = api)
            task.add("Comparative analysis: ${response.text}")

            // Validate and return the comparative analysis
            val comparativeAnalysis = response.obj
            task.complete()
            comparativeAnalysis
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun processFeedback(feedback: Map<String, String>) {
        val task = ui.newTask()
        try {
            task.header("Processing Feedback")
            task.add("Received feedback: ${feedback.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")

            // Use the feedbackProcessor actor to process the feedback
            val response = feedbackProcessor.answer(
                listOf(feedback.entries.joinToString(", ") { "${it.key}: ${it.value}" }),
                api = api
            )
            task.add("Feedback analysis: ${response.text}")

            // Validate and process the feedback analysis
            val feedbackAnalysis = response.obj
            task.add("Processed feedback successfully.")
            task.complete()
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun generateHypotheses(keywords: List<String>): Hypotheses {
        val task = ui.newTask()
        return try {
            task.header("Generating Hypotheses")
            task.add("Received keywords: ${keywords.joinToString(", ")}")

            // Use the hypothesisGenerator actor to generate hypotheses
            val response = hypothesisGenerator.answer(listOf(keywords.joinToString(", ")), api = api)
            task.add("Generated hypotheses: ${response.text}")

            // Validate and return the generated hypotheses
            val generatedHypotheses = response.obj
            task.complete()
            generatedHypotheses
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    fun suggestExperiments(hypotheses: List<String>): ExperimentIdeas {
        val task = ui.newTask()
        return try {
            task.header("Suggesting Experiments")
            task.add("Received hypotheses: ${hypotheses.joinToString(", ")}")

            // Use the experimentSuggester actor to suggest experiments
            val response = experimentSuggester.answer(hypotheses, api = api)
            task.add("Suggested experiments: ${response.text}")

            // Validate and return the suggested experiments
            val suggestedExperiments = response.obj
            task.complete()
            suggestedExperiments
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ScientificAnalysisApplicationAgent::class.java)

    }
}
