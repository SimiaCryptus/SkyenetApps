package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.PoolSystem
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


open class CreativeWritingAssistantAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
) : PoolSystem(dataStorage, user, session) {

    private val contentGenerator by lazy {

        val contentGenerator = SimpleActor(
            prompt = """
            You are a creative writing assistant. Generate creative content such as story ideas, plot outlines, and character descriptions based on the given prompt and user preferences. Ensure the content is engaging and matches the specified tone and style.
        """.trimIndent(),
            name = "ContentGenerator",
            model = OpenAIModels.GPT35Turbo,
            temperature = 0.7
        )
        contentGenerator
    }

    data class GrammarCheckResult(
        @Description("The corrected text")
        val correctedText: String? = null,
        @Description("List of suggestions for improvement")
        val suggestions: List<String>? = null
    ) : ValidatedObject {
        override fun validate() = when {
            correctedText.isNullOrBlank() -> "correctedText is required"
            suggestions == null -> "suggestions are required"
            else -> null
        }
    }

    private val grammarChecker by lazy {

        val grammarChecker = ParsedActor(
            resultClass = GrammarCheckResult::class.java,
            prompt = """
            You are a grammar and style checker. Analyze the given text for grammar, punctuation, and style issues. Provide corrections and suggestions to improve the text.
        """.trimIndent(),
            name = "GrammarChecker",
            model = OpenAIModels.GPT35Turbo,
            temperature = 0.3
        )
        grammarChecker
    }
    private val toneAdjuster by lazy {

        val toneAdjuster = SimpleActor(
            prompt = """
                You are a tone and style adjuster. Adjust the given text to match the specified tone and style. Ensure the adjusted text maintains coherence and readability.
            """.trimIndent(),
            name = "Tone Adjuster",
            model = OpenAIModels.GPT35Turbo,
            temperature = 0.3
        )
        toneAdjuster
    }

    data class FeedbackReport(
        @Description("The analyzed text")
        val text: String? = null,
        @Description("List of suggestions for improvement")
        val suggestions: List<String>? = null,
        @Description("List of corrections made")
        val corrections: List<String>? = null
    ) : ValidatedObject {
        override fun validate() = when {
            text.isNullOrBlank() -> "text is required"
            suggestions.isNullOrEmpty() -> "suggestions are required"
            corrections.isNullOrEmpty() -> "corrections are required"
            else -> null
        }
    }

    private val feedbackAnalyzer by lazy {

        ParsedActor<FeedbackReport>(
            resultClass = FeedbackReport::class.java,
            prompt = """
            You are a feedback analyzer. Analyze the given text for coherence, structure, and readability. 
            Provide a detailed feedback report with suggestions for improvement.
        """.trimIndent(),
            name = "FeedbackAnalyzer",
            model = OpenAIModels.GPT35Turbo,
            temperature = 0.3
        )

    }

    data class UserInput(
        val type: InputType,
        val text: String? = null,
        val voiceData: ByteArray? = null,
        val filePath: String? = null,
        val preferences: UserPreferences? = null
    )

    enum class InputType {
        TEXT, VOICE, FILE, PREFERENCES
    }

    fun creativeWritingAssistant(input: UserInput) {
        val task = ui.newTask()
        try {
            task.header("Creative Writing Assistant")

            when (input.type) {
                InputType.TEXT -> {
                    task.add("Processing text input...")
                    if (input.text != null) {
                        processTextInput(input.text, input.preferences ?: UserPreferences("", "", emptyList()))
                    } else {
                        task.add("Error: No text input provided")
                    }
                }

                InputType.VOICE -> {
                    task.add("Processing voice input...")
                    input.voiceData?.let {
                        val recognizedText = convertVoiceToText(it)
                        processTextInput(recognizedText, input.preferences ?: UserPreferences("", "", emptyList()))
                    }
                }

                InputType.FILE -> {
                    task.add("Processing file input...")
                    input.filePath?.let {
                        val fileContent = File(it).readText()
                        processTextInput(fileContent, input.preferences ?: UserPreferences("", "", emptyList()))
                    }
                }

                InputType.PREFERENCES -> {
                    task.add("Updating user preferences...")
                    // Assuming preferences are updated in some global state or session
                    // Update preferences logic here
                    task.add("Preferences updated: ${input.preferences}")
                }
            }

            task.complete()
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        }
    }

    class VoiceRecognitionService {
        fun recognize(voiceData: ByteArray): String {
            // Hypothetical implementation of voice recognition
            // In a real-world scenario, this would involve calling an external API
            // and processing the response to extract the recognized text.
            return "Recognized text from voice data"
        }
    }

    private fun convertVoiceToText(voiceData: ByteArray): String {
        val task = ui.newTask()
        val executorService = Executors.newSingleThreadExecutor()

        try {
            task.header("Converting Voice to Text")
            task.add("Processing voice data...")

            val voiceRecognitionService = VoiceRecognitionService()
            val future = executorService.submit<String> {
                voiceRecognitionService.recognize(voiceData)
            }

            val recognizedText = future.get()
            task.add("Recognized Text: $recognizedText")
            task.complete()

            return recognizedText
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        } finally {
            executorService.shutdown()
        }
    }

    data class UserPreferences(
        val tone: String,
        val style: String,
        val writingGoals: List<String>
    )

    private fun processTextInput(text: String, preferences: UserPreferences) {
        val task = ui.newTask()
        val executorService = Executors.newFixedThreadPool(10)

        try {
            task.header("Processing Text Input")


            val generatedContent = generateContent(text, preferences, task, executorService)
            val grammarResult = checkGrammar(generatedContent, task, executorService)
            val adjustedText = adjustToneAndStyle(grammarResult.correctedText ?: "", preferences, task, executorService)
            provideFeedback(adjustedText, task, executorService)

            task.complete()
        } catch (e: Throwable) {
            task.error(ui, e)
            throw e
        } finally {
            executorService.shutdown()
        }
    }

    private fun generateContent(
        text: String,
        preferences: UserPreferences,
        task: SessionTask,
        executorService: ExecutorService
    ): String {
        task.add("Generating creative content...")
        val contentFuture = executorService.submit<String> {
            contentGenerator.answer(listOf(text, preferences.toString()), api = api)
        }
        val generatedContent = contentFuture.get()
        task.add("Generated Content: $generatedContent")
        return generatedContent
    }

    private fun checkGrammar(
        text: String,
        task: SessionTask,
        executorService: ExecutorService
    ): GrammarCheckResult {
        task.add("Checking grammar and style...")
        val grammarFuture = executorService.submit<GrammarCheckResult> {
            grammarChecker.answer(listOf(text), api = api).obj
        }
        val grammarResult = grammarFuture.get()
        task.add("Corrected Text: ${grammarResult.correctedText}")
        task.add("Suggestions: ${grammarResult.suggestions?.joinToString(", ")}")
        return grammarResult
    }

    private fun adjustToneAndStyle(
        text: String,
        preferences: UserPreferences,
        task: SessionTask,
        executorService: ExecutorService
    ): String {
        task.add("Adjusting tone and style...")
        val toneFuture = executorService.submit<String> {
            toneAdjuster.answer(
                listOf(text, preferences.tone, preferences.style),
                api = api
            )
        }
        val adjustedText = toneFuture.get()
        task.add("Adjusted Text: $adjustedText")
        return adjustedText
    }

    private fun provideFeedback(text: String, task: SessionTask, executorService: ExecutorService) {
        task.add("Analyzing feedback...")
        val feedbackFuture = executorService.submit<FeedbackReport> {
            feedbackAnalyzer.answer(listOf(text), api = api).obj
        }
        val feedbackReport = feedbackFuture.get()
        task.add("Feedback Report: ${feedbackReport.text}")
        task.add("Suggestions: ${feedbackReport.suggestions?.joinToString(", ")}")
        task.add("Corrections: ${feedbackReport.corrections?.joinToString(", ")}")
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(CreativeWritingAssistantAgent::class.java)

    }
}