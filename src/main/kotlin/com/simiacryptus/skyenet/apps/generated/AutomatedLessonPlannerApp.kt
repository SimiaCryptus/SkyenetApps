package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.util.function.Function


open class AutomatedLessonPlannerApp(
  applicationName: String = "Automated Lesson Planner",
) : ApplicationServer(
  applicationName = applicationName,
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
  )
  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

  override fun newSession(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      AutomatedLessonPlannerAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).automatedLessonPlanner(userMessage as Map<String, Any>)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(AutomatedLessonPlannerApp::class.java)
  }

}



open class AutomatedLessonPlannerAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<AutomatedLessonPlannerActors.ActorType>(AutomatedLessonPlannerActors(
  model = model,
  temperature = temperature,
).actorMap, dataStorage, user, session) {

  @Suppress("UNCHECKED_CAST")
  private val curriculumMapperActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.CURRICULUM_MAPPER_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.CurriculumMapping> }
  private val personalizationActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.PERSONALIZATION_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.PersonalizedPlan> }
  private val resourceAllocatorActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.RESOURCE_ALLOCATOR_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.ResourceAllocation> }
  private val timeManagerActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.TIME_MANAGER_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.TimeAllocation> }
  private val continuityActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.CONTINUITY_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.LessonContinuity> }
  private val assessmentIntegratorActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.ASSESSMENT_INTEGRATOR_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.AssessmentIntegration> }
  private val adaptationActor by lazy { getActor(AutomatedLessonPlannerActors.ActorType.ADAPTATION_ACTOR) as ParsedActor<AutomatedLessonPlannerActors.LearningAdaptations> }

  fun automatedLessonPlanner(userInputs: Map<String, Any>) {
    val task = ui.newTask()
    try {
      task.header("Automated Lesson Planner")

      // Extract user inputs
      val curriculumData = userInputs["curriculumData"] as? Map<String, Any> ?: throw IllegalArgumentException("Curriculum data is required")
      val studentData = userInputs["studentData"] as? Map<String, Any> ?: throw IllegalArgumentException("Student data is required")
      val resourcesData = userInputs["resourcesData"] as? Map<String, Any> ?: throw IllegalArgumentException("Resources data is required")
      val timeData = userInputs["timeData"] as? Map<String, Any> ?: throw IllegalArgumentException("Time data is required")
      val continuityData = userInputs["continuityData"] as? Map<String, Any> ?: throw IllegalArgumentException("Continuity data is required")
      val assessmentData = userInputs["assessmentData"] as? Map<String, Any> ?: throw IllegalArgumentException("Assessment data is required")
      val adaptationsData = userInputs["adaptationsData"] as? Map<String, Any> ?: throw IllegalArgumentException("Adaptations data is required")

      // Use the actors to process the inputs
      val curriculumMapping = curriculumMapperActor(curriculumData, curriculumData)
      task.add("Curriculum Mapping: ${curriculumMapping.standards}")

      val personalizedPlan = personalizationActor(userInputs, studentData)
      task.add("Personalized Plan: ${personalizedPlan.studentPreferences}")

      val resourceAllocation = resourceAllocatorActor(userInputs, resourcesData)
      task.add("Resource Allocation: ${resourceAllocation.requiredMaterials}")

      val timeAllocation = timeManagerActor(userInputs, timeData["lessonDuration"] as? Int ?: throw IllegalArgumentException("Lesson duration is required"))
      task.add("Time Allocation: ${timeAllocation.activityDurations}")

      val lessonContinuity = continuityActor(userInputs, continuityData)
      task.add("Lesson Continuity: Previous Lesson - ${lessonContinuity.previousLessonSummary}")

      val assessmentIntegration = assessmentIntegratorActor(userInputs, assessmentData)
      task.add("Assessment Integration: Formative - ${assessmentIntegration.formativeAssessments}")

      val learningAdaptations = adaptationActor(userInputs, adaptationsData)
      task.add("Learning Adaptations: ${learningAdaptations.adaptations}")

      // Compile all outputs into a Lesson Plan Document
      val lessonPlanDocument = "Lesson Plan Document Content Here..."
      // Save Lesson Plan Document to file storage (pseudo-code)
      // val documentLink = fileStorage.save(lessonPlanDocument)

      // Send message output to web interface with document link
      task.complete("Lesson plan created successfully. You can download it from the link provided.")
      // task.add(ui.hrefLink("Download Lesson Plan", documentLink) { log.info("Lesson plan downloaded") })

    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }

  // This is a placeholder for the actual call to the function with user inputs
  val userInputs = mapOf<String, Any>(
    // Populate the map with user input data
  )
  // automatedLessonPlanner(userInputs) // Uncomment this line to call the function with actual user inputs

  fun curriculumMapperActor(inputData: Map<String, Any>, curriculumStandards: Map<String, Any>): AutomatedLessonPlannerActors.CurriculumMapping {
    // Convert the inputData and curriculumStandards to a conversation thread list
    val conversationThread = listOf(
      "Subject: ${inputData["subject"]}, Grade: ${inputData["grade"]}, Topics: ${inputData["topics"]}",
      "Curriculum Standards: ${curriculumStandards["standards"]}"
    )

    // Use the curriculumMapperActor to process the input and get the response
    val response = curriculumMapperActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.CurriculumMapping object from the response
    return response.obj
  }

  fun adaptationActor(lessonPlan: Map<String, Any>, learningStyleAdaptations: Map<String, Any>): AutomatedLessonPlannerActors.LearningAdaptations {
    // Convert the lesson plan and learning style adaptations to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: Title - ${lessonPlan["title"]}, Objectives - ${lessonPlan["objectives"]}",
      "Learning Style Adaptations: ${learningStyleAdaptations["adaptations"]}"
    )

    // Use the adaptationActor to process the input and get the response
    val response = adaptationActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.LearningAdaptations object from the response
    return response.obj
  }

  fun continuityActor(lessonPlan: Map<String, Any>, previousLessons: Map<String, Any>): AutomatedLessonPlannerActors.LessonContinuity {
    // Convert the lesson plan and previous lessons to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: Title - ${lessonPlan["title"]}, Objectives - ${lessonPlan["objectives"]}",
      "Previous Lessons: ${previousLessons["summaries"]}"
    )

    // Use the continuityActor to process the input and get the response
    val response = continuityActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.LessonContinuity object from the response
    return response.obj
  }

  fun resourceAllocatorActor(lessonPlan: Map<String, Any>, availableResources: Map<String, Any>): AutomatedLessonPlannerActors.ResourceAllocation {
    // Convert the lesson plan and available resources to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: ${lessonPlan["title"]} - Objectives: ${lessonPlan["objectives"]}",
      "Available Resources: ${availableResources["resources"]}"
    )

    // Use the resourceAllocatorActor to process the input and get the response
    val response = resourceAllocatorActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.ResourceAllocation object from the response
    return response.obj
  }

  fun personalizationActor(lessonPlan: Map<String, Any>, studentData: Map<String, Any>): AutomatedLessonPlannerActors.PersonalizedPlan {
    // Convert the lesson plan and student data to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: ${lessonPlan["title"]} - ${lessonPlan["objectives"]}",
      "Student Data: Preferences - ${studentData["preferences"]}, Performance - ${studentData["performance"]}"
    )

    // Use the personalizationActor to process the input and get the response
    val response = personalizationActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.PersonalizedPlan object from the response
    return response.obj
  }

  fun timeManagerActor(lessonPlan: Map<String, Any>, lessonDuration: Int): AutomatedLessonPlannerActors.TimeAllocation {
    // Convert the lesson plan and duration to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: ${lessonPlan["title"]} - Activities: ${lessonPlan["activities"]}",
      "Total Lesson Duration: $lessonDuration minutes"
    )

    // Use the timeManagerActor to process the input and get the response
    val response = timeManagerActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.TimeAllocation object from the response
    return response.obj
  }

  fun assessmentIntegratorActor(lessonPlan: Map<String, Any>, assessmentMethods: Map<String, Any>): AutomatedLessonPlannerActors.AssessmentIntegration {
    // Convert the lesson plan and assessment methods to a conversation thread list
    val conversationThread = listOf(
      "Lesson Plan: Title - ${lessonPlan["title"]}, Objectives - ${lessonPlan["objectives"]}",
      "Assessment Methods: Formative - ${assessmentMethods["formative"]}, Summative - ${assessmentMethods["summative"]}"
    )

    // Use the assessmentIntegratorActor to process the input and get the response
    val response = assessmentIntegratorActor.answer(conversationThread, api = api)

    // Return the parsed AutomatedLessonPlannerActors.AssessmentIntegration object from the response
    return response.obj
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AutomatedLessonPlannerAgent::class.java)

  }
}



class AutomatedLessonPlannerActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  data class CurriculumMapping(
    @Description("The subject area of the curriculum")
    val subjectArea: String? = null,
    @Description("The grade level for the curriculum")
    val gradeLevel: String? = null,
    @Description("A list of curriculum standards")
    val standards: List<String>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      subjectArea.isNullOrBlank() -> "subjectArea is required"
      gradeLevel.isNullOrBlank() -> "gradeLevel is required"
      standards.isNullOrEmpty() -> "standards are required"
      else -> null
    }
  }

  interface CurriculumMapperParser : Function<String, CurriculumMapping> {
    @Description("Parse the text into a CurriculumMapping data structure.")
    override fun apply(text: String): CurriculumMapping
  }

  val curriculumMapperActor = ParsedActor<CurriculumMapping>(
    parserClass = CurriculumMapperParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant specialized in curriculum mapping.
            Your task is to analyze the input data and map it to the relevant curriculum standards.
            Input data: "Subject: Mathematics, Grade: 7, Topics: Algebra, Geometry"
            Based on the input data, provide the curriculum mapping in a structured format.
        """.trimIndent()
  )


  data class PersonalizedPlan(
    @Description("A map of student preferences for personalization.")
    val studentPreferences: Map<String, Any>? = null,
    @Description("A map of student performance data for personalization.")
    val performanceData: Map<String, Any>? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (studentPreferences == null || studentPreferences.isEmpty()) return "studentPreferences is required"
      if (performanceData == null || performanceData.isEmpty()) return "performanceData is required"
      return null
    }
  }

  interface PersonalizationParser : Function<String, PersonalizedPlan> {
    @Description("Parse the text response into a PersonalizedPlan data structure.")
    override fun apply(text: String): PersonalizedPlan
  }

  val personalizationActor = ParsedActor<PersonalizedPlan>(
    parserClass = PersonalizationParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that personalizes lesson plans based on student data.
            Given the student preferences and performance data, create a personalized lesson plan.
        """.trimIndent()
  )


  // Define the ResourceAllocation data class
  data class ResourceAllocation(
    @Description("A list of required materials for the lesson.")
    val requiredMaterials: List<String>? = null,
    @Description("A list of suggested supplementary materials for the lesson.")
    val suggestedSupplementaryMaterials: List<String>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      requiredMaterials.isNullOrEmpty() -> "requiredMaterials is required"
      else -> null
    }
  }

  // Define the ResourceAllocatorParser interface
  interface ResourceAllocatorParser : Function<String, ResourceAllocation> {
    @Description("Parse the text response into a ResourceAllocation data structure.")
    override fun apply(text: String): ResourceAllocation
  }

  // Instantiate the resourceAllocatorActor using the ParsedActor constructor
  val resourceAllocatorActor = ParsedActor<ResourceAllocation>(
    parserClass = ResourceAllocatorParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that allocates resources for lesson plans.
            Given a set of constraints and available materials, suggest the best use of resources.
        """.trimIndent()
  )


  data class TimeAllocation(
    @Description("A map of activities and their corresponding durations in minutes.")
    val activityDurations: Map<String, Int>
  ) : ValidatedObject {
    override fun validate() = when {
      activityDurations.isEmpty() -> "At least one activity with its duration is required."
      activityDurations.any { it.value <= 0 } -> "Activity durations must be greater than zero."
      else -> null
    }
  }

  interface TimeAllocationParser : Function<String, TimeAllocation> {
    @Description("Parse the text into a TimeAllocation data structure.")
    override fun apply(text: String): TimeAllocation
  }

  val timeManagerActor = ParsedActor<TimeAllocation>(
    parserClass = TimeAllocationParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant for managing time allocation in lesson plans.
            Given a list of activities and a total lesson duration, allocate time to each activity.
            Produce a structured output with activity names and their corresponding durations in minutes.
        """.trimIndent()
  )


  data class LessonContinuity(
    @Description("A summary of the previous lesson.")
    val previousLessonSummary: String? = null,
    @Description("A preview of the next lesson.")
    val nextLessonPreview: String? = null
  ) : ValidatedObject {
    override fun validate() = when {
      previousLessonSummary.isNullOrBlank() -> "Previous lesson summary is required."
      nextLessonPreview.isNullOrBlank() -> "Next lesson preview is required."
      else -> null
    }
  }

  interface ContinuityParser : Function<String, LessonContinuity> {
    @Description("Parse the text into a LessonContinuity data structure.")
    override fun apply(text: String): LessonContinuity
  }

  val continuityActor = ParsedActor<LessonContinuity>(
    parserClass = ContinuityParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that helps in planning educational lessons. Your task is to ensure continuity between lessons.
            Given a description of a previous lesson and the objectives of the upcoming lesson, provide a summary of the previous lesson and a preview of the next lesson.
        """.trimIndent()
  )


  data class AssessmentIntegration(
    @Description("A list of formative assessment methods to be used.")
    val formativeAssessments: List<String>? = null,
    @Description("A list of summative assessment methods to be used.")
    val summativeAssessments: List<String>? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (formativeAssessments.isNullOrEmpty() && summativeAssessments.isNullOrEmpty()) {
        return "At least one formative or summative assessment method is required."
      }
      return null
    }
  }

  interface AssessmentIntegratorParser : Function<String, AssessmentIntegration> {
    @Description("Parse the text response into an AssessmentIntegration data structure.")
    override fun apply(text: String): AssessmentIntegration
  }

  val assessmentIntegratorActor = ParsedActor<AssessmentIntegration>(
    parserClass = AssessmentIntegratorParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that helps integrate various assessment methods into lesson plans.
            Based on the provided information, suggest appropriate formative and summative assessments.
        """.trimIndent()
  )


  data class LearningAdaptations(
    @Description("Adaptations for different learning styles and needs.")
    val adaptations: Map<String, String>
  ) : ValidatedObject {
    override fun validate(): String? {
      if (adaptations.isEmpty()) return "Adaptations map cannot be empty."
      return null
    }
  }

  interface LearningAdaptationsParser : Function<String, LearningAdaptations> {
    @Description("Parse the text into a LearningAdaptations data structure.")
    override fun apply(text: String): LearningAdaptations
  }

  val adaptationActor = ParsedActor<LearningAdaptations>(
    parserClass = LearningAdaptationsParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that provides adaptations for lesson plans to accommodate different learning styles and special needs.
            Based on the provided information, suggest appropriate adaptations.
        """.trimIndent()
  )

  enum class ActorType {
    CURRICULUM_MAPPER_ACTOR,
    PERSONALIZATION_ACTOR,
    RESOURCE_ALLOCATOR_ACTOR,
    TIME_MANAGER_ACTOR,
    CONTINUITY_ACTOR,
    ASSESSMENT_INTEGRATOR_ACTOR,
    ADAPTATION_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
    ActorType.CURRICULUM_MAPPER_ACTOR to curriculumMapperActor,
    ActorType.PERSONALIZATION_ACTOR to personalizationActor,
    ActorType.RESOURCE_ALLOCATOR_ACTOR to resourceAllocatorActor,
    ActorType.TIME_MANAGER_ACTOR to timeManagerActor,
    ActorType.CONTINUITY_ACTOR to continuityActor,
    ActorType.ASSESSMENT_INTEGRATOR_ACTOR to assessmentIntegratorActor,
    ActorType.ADAPTATION_ACTOR to adaptationActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(AutomatedLessonPlannerActors::class.java)
  }
}
