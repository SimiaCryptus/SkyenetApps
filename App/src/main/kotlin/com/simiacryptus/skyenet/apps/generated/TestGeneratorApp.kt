package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.JsonUtil.toJson
import com.simiacryptus.skyenet.apps.generated.TestGeneratorActors.*
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.CodingActor.Companion.indent
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.util.MarkdownUtil
import org.slf4j.LoggerFactory

open class TestGeneratorApp(
  applicationName: String = "Test Generator",
  path: String = "/testgenerator",
) : ApplicationServer(
  applicationName = applicationName,
  path = path,
) {

  data class Settings(
    val model: OpenAITextModel = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
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
    try {
      val settings = getSettings<Settings>(session, user)
      TestGeneratorAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).testGenerator(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(TestGeneratorApp::class.java)
  }

}

open class TestGeneratorAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: OpenAITextModel = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<ActorType>(
  TestGeneratorActors(
    model = model,
    temperature = temperature,
  ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val inputHandler by lazy { getActor(ActorType.INPUT_HANDLER) as ParsedActor<TopicIdentificationResult> }
  private val topicIdentificationActor by lazy { getActor(ActorType.TOPIC_IDENTIFICATION_ACTOR) as ParsedActor<TopicIdentificationResult> }
  private val questionGenerationActor by lazy { getActor(ActorType.QUESTION_GENERATION_ACTOR) as ParsedActor<QuestionSet> }
  private val answerGenerationActor by lazy { getActor(ActorType.ANSWER_GENERATION_ACTOR) as ParsedActor<AnswerSet> }

  fun testGenerator(prompt: String) {
    val task = ui.newTask()
    try {
      task.echo(prompt)
      val params = toJson(inputHandler.answer(listOf(prompt), api).obj)
      task.add("Identifying topics and keywords in the text")
      val topics = topicIdentificationActor.answer(listOf(prompt, params), api).obj.topics?.map { it.name }!!
      val questions = topics.flatMap { topic ->
        task.add("Generating questions for the topic: $topic")
        val questionSet = questionGenerationActor.answer(listOf(prompt, params, topic!!), api).obj
        task.verbose(MarkdownUtil.renderMarkdown("Questions for the topic: $topic\n```json\n${toJson(questionSet)/*.indent("  ")*/}\n```", ui=ui))
        questionSet.questions?.map { question ->
          question.copy(text = "[$topic] ${question.text}")
        } ?: emptyList()
      }
      val answers = questions.flatMap { question ->
        task.add("Generating answers for the question: ${question.text}")
        val answers = answerGenerationActor.answer(listOf(prompt, params, question.text!!), api).obj.answers!!
        task.verbose(
          MarkdownUtil.renderMarkdown(
            "Answers for the question:\n```text\n${question.text/*.indent("  ")*/}\n```\n\n```json\n${
              toJson(
                answers
              )/*.indent("  ")*/
            }\n```\n", ui=ui
          )
        )
        answers
      }
      task.complete("Test generation process completed successfully.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  companion object {

  }
}


class TestGeneratorActors(
  val model: OpenAITextModel = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  data class TaskInfo(
    val name: String? = null,
    val description: String? = null,
  )

  private val inputHandler = ParsedActor(
//    parserClass = TaskParser::class.java,
    resultClass = TaskInfo::class.java,
    model = ChatModels.GPT35Turbo,
    parsingModel = ChatModels.GPT35Turbo,
    prompt = """
            You are an AI-based automated assistant capable of processing user requests and generating responses. Given a task description, provide a summary of the task and any relevant details to guide the subsequent steps in the process.
        """.trimIndent()
  )


  data class TopicIdentificationResult(
    val topics: List<Topic>? = null
  ) {
    data class Topic(
      val name: String? = null,
      val keywords: List<String>? = null
    )
  }

  private val topicIdentificationActor = ParsedActor(
//    parserClass = TopicIdentificationParser::class.java,
    resultClass = TopicIdentificationResult::class.java,
    model = ChatModels.GPT35Turbo,
    parsingModel = ChatModels.GPT35Turbo,
    prompt = """
            Given a piece of text, identify the key topics and associated keywords. For each topic, provide a concise name and a list of relevant keywords.
        """.trimIndent().trim()
  )


  data class Question(
    val text: String? = null,
    val type: String? = null, // E.g., "multiple-choice", "true/false", "short-answer"
    val difficulty: String? = null // E.g., "easy", "medium", "hard"
  )

  data class QuestionSet(
    val questions: List<Question>? = null
  )

  private val questionGenerationActor = ParsedActor(
//    parserClass = QuestionSetParser::class.java,
    resultClass = QuestionSet::class.java,
    model = ChatModels.GPT35Turbo,
    parsingModel = ChatModels.GPT35Turbo,
    prompt = """
            Given a topic, generate a set of quiz questions. Include multiple-choice, true/false, and short answer questions of varying difficulty levels.
        """.trimIndent()
  )


  data class Answer(
    val text: String? = null,
    val isCorrect: Boolean? = null,
    val explanation: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (text.isNullOrBlank()) return "Answer text is required"
      // Explanation is optional, so no validation needed for it
      return null
    }
  }

  data class AnswerSet(
    val answers: List<Answer>? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (answers.isNullOrEmpty()) return "At least one answer is required"
      if (answers.none { it.isCorrect ?: false }) return "At least one answer must be marked as correct"
      return null
    }
  }

  private val answerGenerationActor = ParsedActor(
//    parserClass = AnswerParser::class.java,
    resultClass = AnswerSet::class.java,
    prompt = """
            You are an assistant capable of generating answers for quiz questions. For each question, generate multiple answers, indicating which are correct or incorrect, and provide explanations for the correctness of each answer.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    parsingModel = ChatModels.GPT35Turbo,
    temperature = 0.3
  )

  enum class ActorType {
    INPUT_HANDLER,
    TOPIC_IDENTIFICATION_ACTOR,
    QUESTION_GENERATION_ACTOR,
    ANSWER_GENERATION_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    ActorType.INPUT_HANDLER to inputHandler,
    ActorType.TOPIC_IDENTIFICATION_ACTOR to topicIdentificationActor,
    ActorType.QUESTION_GENERATION_ACTOR to questionGenerationActor,
    ActorType.ANSWER_GENERATION_ACTOR to answerGenerationActor,
  )

  companion object {
    val log = LoggerFactory.getLogger(TestGeneratorActors::class.java)
  }
}
