package com.simiacryptus.skyenet.apps.generated


import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.apps.generated.AutomatedLessonPlannerArchitectureActors.Activity
import com.simiacryptus.skyenet.core.actors.ActorSystem
import com.simiacryptus.skyenet.core.actors.BaseActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory


open class AutomatedLessonPlannerArchitectureApp(
    applicationName: String = "Automated Lesson Planner v1.0",
    domainName: String,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/lesson_planner"
) {

    data class Settings(
        val model: OpenAITextModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.1,
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
        try {
            val settings = getSettings<Settings>(session, user)
            AutomatedLessonPlannerArchitectureAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
                temperature = settings?.temperature ?: 0.3,
            ).automatedLessonPlannerArchitecture(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AutomatedLessonPlannerArchitectureApp::class.java)
    }

}


open class AutomatedLessonPlannerArchitectureAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    model: OpenAITextModel = OpenAIModels.GPT4oMini,
    temperature: Double = 0.3,
) : ActorSystem<AutomatedLessonPlannerArchitectureActors.ActorType>(
    AutomatedLessonPlannerArchitectureActors(
        model = model,
        temperature = temperature,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val curriculumMapperActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.CURRICULUM_MAPPER_ACTOR) as ParsedActor<AutomatedLessonPlannerArchitectureActors.CurriculumMapping> }

    @Suppress("UNCHECKED_CAST")
    private val resourceAllocatorActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.RESOURCE_ALLOCATOR_ACTOR) as ParsedActor<AutomatedLessonPlannerArchitectureActors.ResourceAllocation> }

    @Suppress("UNCHECKED_CAST")
    private val timeManagerActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.TIME_MANAGER_ACTOR) as ParsedActor<AutomatedLessonPlannerArchitectureActors.LessonTimeline> }

    @Suppress("UNCHECKED_CAST")
    private val assessmentPlannerActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.ASSESSMENT_PLANNER_ACTOR) as ParsedActor<AutomatedLessonPlannerArchitectureActors.AssessmentPlan> }
    private val customizationActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.CUSTOMIZATION_ACTOR) as SimpleActor }

    @Suppress("UNCHECKED_CAST")
    private val feedbackAnalyzerActor by lazy { getActor(AutomatedLessonPlannerArchitectureActors.ActorType.FEEDBACK_ANALYZER_ACTOR) as ParsedActor<AutomatedLessonPlannerArchitectureActors.FeedbackAnalysis> }

    fun automatedLessonPlannerArchitecture(requirements: String) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the automated lesson planner
            task.header("Automated Lesson Planner")

            // Add a message to the task output to show the requirements
            task.add("Received the following requirements for the automated lesson planner:")
            task.add(requirements, tag = "pre") // Display the requirements in a preformatted text block

            // Step 1: Map learning objectives to curriculum standards
            task.add("Step 1: Mapping learning objectives to curriculum standards.")
            // Normally, we would collect learning objectives from the user, but for this example, we'll use a predefined list
            val learningObjectives =
                listOf("Understand the concept of photosynthesis", "Solve basic algebraic equations")
            mapLearningObjectivesToStandards(learningObjectives)

            // Step 2: Suggest activities based on available resources
            task.add("Step 2: Suggesting activities based on available resources.")
            // Normally, we would collect available resources from the user, but for this example, we'll use a predefined list
            val availableResources = listOf("Whiteboard", "Projector", "Lab equipment")
            suggestActivities(availableResources)

            // Step 3: Create a lesson timeline
            task.add("Step 3: Creating a lesson timeline.")
            // Normally, we would collect activities and time constraints from the user, but for this example, we'll use predefined data
            val activities = listOf(
                Activity(name = "Introduction to Photosynthesis", description = "Lecture with slides"),
                Activity(name = "Photosynthesis Lab", description = "Hands-on experiment")
            )
            val totalLessonTime = 90 // 90 minutes
            createLessonTimeline(activities, totalLessonTime)

            // Step 4: Suggest assessment methods
            task.add("Step 4: Suggesting assessment methods.")
            suggestAssessmentMethods(learningObjectives)

            // Step 5: Allow teachers to customize the generated lesson plan
            task.add("Step 5: Customizing the lesson plan.")
            // Normally, we would present the draft lesson plan to the user and allow them to customize it
            // For this example, we'll use a placeholder AutomatedLessonPlannerArchitectureActors.string as the initial lesson plan
            val initialLessonPlan = "Placeholder lesson plan content"
            customizeLessonPlan(initialLessonPlan)

            // Step 6: Analyze teacher feedback for continuous improvement
            task.add("Step 6: Analyzing teacher feedback.")
            // Normally, we would collect feedback from the user, but for this example, we'll use a predefined feedback AutomatedLessonPlannerArchitectureActors.string
            val feedback = "The lesson plan was well-structured, but the lab activity required too much time."
            analyzeFeedback(feedback)

            // Complete the task with a final message
            task.complete("Automated lesson planner process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to allow teachers to customize the generated lesson plan
    private fun customizeLessonPlan(initialLessonPlan: String) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the customization process
            task.header("Customizing Lesson Plan")

            // Add a message to the task output to show the initial lesson plan
            task.add("Initial Lesson Plan:")
            task.add(initialLessonPlan, tag = "pre") // Display the initial plan in a preformatted text block

            // Provide a text input form for the teacher to enter customization requests
            task.add(ui.textInput { customizationRequest ->
                // Log the received customization request
                task.echo("Received customization request: $customizationRequest")

                // Use the customizationActor to process the customization request
                val response = customizationActor.answer(listOf(customizationRequest), api = api)

                // Display the customized lesson plan
                task.add("Customized Lesson Plan:")
                task.add(response, tag = "pre") // Display the customized plan in a preformatted text block

                // Complete the task with a final message
                task.complete("Lesson plan customization process completed.")
            })

            // Add instructions for the user
            task.add("Please enter your customization requests for the lesson plan in the text box above and submit.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to analyze teacher feedback for continuous improvement
    private fun analyzeFeedback(feedback: String) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the feedback analysis process
            task.header("Analyzing Feedback")

            // Add a message to the task output to show the feedback being processed
            task.add("Processing the following feedback: $feedback")

            // Use the feedbackAnalyzerActor to analyze the feedback and provide suggestions for improvement
            val response = feedbackAnalyzerActor.answer(listOf(feedback), api = api)

            // Check if the response is valid
            val feedbackAnalysis = response.obj
            val validationResult = feedbackAnalysis.validate()
            if (validationResult != null) {
                // If the response is not valid, display an error message
                task.error(ui, Exception(validationResult))
            } else {
                // If the response is valid, display the suggestions for improvement
                task.add("Feedback analysis completed successfully. Suggestions for improvement:")
                feedbackAnalysis.improvementSuggestions.forEach { suggestion ->
                    task.add("Suggestion: \"$suggestion\"")
                }
            }

            // Complete the task with a final message
            task.complete("Feedback analysis process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to map learning objectives to curriculum standards
    private fun mapLearningObjectivesToStandards(learningObjectives: List<String>) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the process
            task.header("Mapping Learning Objectives to Curriculum Standards")

            // Add a message to the task output to show the learning objectives being processed
            task.add("Processing the following learning objectives: ${learningObjectives.joinToString(", ")}")

            // Use the curriculumMapperActor to get the curriculum mapping
            val response = curriculumMapperActor.answer(learningObjectives, api = api)

            // Check if the response is valid
            val mapping = response.obj
            val validationResult = mapping.validate()
            if (validationResult != null) {
                // If the response is not valid, display an error message
                task.error(ui, Exception(validationResult))
            } else {
                // If the response is valid, display the curriculum mapping
                task.add("Curriculum mapping completed successfully.")
                mapping.standardMappings.forEach { (objective, standard) ->
                    task.add("Learning Objective: \"$objective\" maps to Curriculum Standard: \"$standard\"")
                }
            }

            // Complete the task with a final message
            task.complete("Curriculum mapping process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to create a lesson timeline based on activities and time constraints
    private fun createLessonTimeline(activities: List<Activity>, totalLessonTime: Int) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the process
            task.header("Creating Lesson Timeline")

            // Add a message to the task output to show the activities being processed
            task.add("Organizing the following activities within a $totalLessonTime-minute lesson:")
            activities.forEach { activity ->
                task.add("Activity: \"${activity.name}\" - ${activity.description}")
            }

            // Use the timeManagerActor to get the lesson timeline
            val response = timeManagerActor.answer(activities.map { it.name ?: "" }, api = api)

            // Check if the response is valid
            val timeline = response.obj
            val validationResult = timeline.validate()
            if (validationResult != null) {
                // If the response is not valid, display an error message
                task.error(ui, Exception(validationResult))
            } else {
                // If the response is valid, display the lesson timeline
                task.add("Lesson timeline created successfully.")
                timeline.activityTiming?.forEach { (activity, timeBlock) ->
                    task.add("Activity: \"${activity.name}\" - Start: ${timeBlock.startTime}, End: ${timeBlock.endTime}")
                }
            }

            // Complete the task with a final message
            task.complete("Lesson timeline creation process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to suggest assessment methods based on learning objectives
    private fun suggestAssessmentMethods(learningObjectives: List<String>) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the process
            task.header("Suggesting Assessment Methods")

            // Add a message to the task output to show the learning objectives being processed
            task.add(
                "Processing the following learning objectives for assessment methods: ${
                    learningObjectives.joinToString(
                        ", "
                    )
                }"
            )

            // Use the assessmentPlannerActor to get the suggested assessment methods
            val response = assessmentPlannerActor.answer(learningObjectives, api = api)

            // Check if the response is valid
            val assessmentPlan = response.obj
            val validationResult = assessmentPlan.validate()
            if (validationResult != null) {
                // If the response is not valid, display an error message
                task.error(ui, Exception(validationResult))
            } else {
                // If the response is valid, display the suggested assessment methods
                task.add("Assessment methods suggested successfully.")
                assessmentPlan.assessmentMethods.forEach { method ->
                    task.add("Assessment Method: \"${method.type}\" - ${method.description}")
                }
            }

            // Complete the task with a final message
            task.complete("Assessment methods suggestion process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    // Function to suggest activities based on available resources
    private fun suggestActivities(availableResources: List<String>) {
        // Create a new task in the UI to show progress
        val task = ui.newTask()
        try {
            // Add a header to the task output to indicate the start of the process
            task.header("Suggesting Activities Based on Available Resources")

            // Add a message to the task output to show the resources being processed
            task.add("Processing the following available resources: ${availableResources.joinToString(", ")}")

            // Use the resourceAllocatorActor to get the suggested activities
            val response = resourceAllocatorActor.answer(availableResources, api = api)

            // Check if the response is valid
            val allocation = response.obj
            val validationResult = allocation.validate()
            if (validationResult != null) {
                // If the response is not valid, display an error message
                task.error(ui, Exception(validationResult))
            } else {
                // If the response is valid, display the suggested activities
                task.add("Suggested activities based on the available resources:")
                allocation.suggestedActivities.forEach { activity ->
                    task.add("Activity: \"$activity\"")
                }
            }

            // Complete the task with a final message
            task.complete("Activity suggestion process completed.")
        } catch (e: Throwable) {
            // If an error occurs, display it in the task output
            task.error(ui, e)
            throw e
        }
    }

    companion object
}


class AutomatedLessonPlannerArchitectureActors(
    val model: OpenAITextModel = OpenAIModels.GPT4o,
    val temperature: Double = 0.3,
) {


    // Define the data class for the curriculum mapping result
    data class CurriculumMapping(
        @Description("A list of learning objectives.")
        val learningObjectives: List<String>,

        @Description("A map of learning objectives to curriculum standards.")
        val standardMappings: Map<String, String>
    ) : ValidatedObject {
        override fun validate(): String? {
            if (learningObjectives.isEmpty()) return "Learning objectives are required."
            if (standardMappings.isEmpty()) return "Standard mappings are required."
            return null
        }
    }

    // Instantiate the curriculumMapperActor
    private val curriculumMapperActor = ParsedActor(
//    parserClass = CurriculumMappingParser::class.java,
        resultClass = CurriculumMapping::class.java,
        model = OpenAIModels.GPT4oMini,
        prompt = """
            You are an assistant that maps learning objectives to curriculum standards.
            Given a list of learning objectives, provide the corresponding curriculum standards.
        """.trimIndent(),
        parsingModel = OpenAIModels.GPT4oMini
    )


    data class ResourceAllocation(
        @Description("A list of available resources.")
        val availableResources: List<String>,
        @Description("A list of suggested activities based on the available resources.")
        val suggestedActivities: List<String>
    ) : ValidatedObject {
        override fun validate() = when {
            availableResources.isEmpty() -> "Available resources are required."
            suggestedActivities.isEmpty() -> "Suggested activities are required."
            else -> null
        }
    }

    private val resourceAllocatorActor = ParsedActor(
//    parserClass = ResourceAllocatorParser::class.java,
        resultClass = ResourceAllocation::class.java,
        model = OpenAIModels.GPT4oMini,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            You are an assistant that suggests educational activities based on a list of available resources.
            Given a list of resources, provide a list of possible activities that can be conducted using these resources.
            Please format your response as follows:
            Available Resources: [Resource1, Resource2, ...]
            Suggested Activities: [Activity1, Activity2, ...]
        """.trimIndent()
    )


    data class TimeBlock(
        @Description("The start time of the activity.")
        val startTime: String? = null,
        @Description("The end time of the activity.")
        val endTime: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            startTime.isNullOrBlank() -> "startTime is required"
            endTime.isNullOrBlank() -> "endTime is required"
            else -> null
        }
    }

    data class Activity(
        @Description("The name of the activity.")
        val name: String? = null,
        @Description("The description of the activity.")
        val description: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            name.isNullOrBlank() -> "name is required"
            else -> null
        }
    }

    data class LessonTimeline(
        @Description("A list of activities.")
        val activities: List<Activity>? = null,
        @Description("A map of activities to their corresponding time blocks.")
        val activityTiming: Map<Activity, TimeBlock>? = null
    ) : ValidatedObject {
        override fun validate() = when {
            activities.isNullOrEmpty() -> "activities are required"
            activityTiming.isNullOrEmpty() -> "activityTiming is required"
            else -> null
        }
    }

    private val timeManagerActor = ParsedActor(
//    parserClass = TimeManagerParser::class.java,
        resultClass = LessonTimeline::class.java,
        model = OpenAIModels.GPT4oMini,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            You are an assistant that creates a timeline for a lesson plan.
            Given a list of activities and their descriptions, along with the total lesson time, 
            organize these activities into a structured timeline that fits within the lesson duration.
        """.trimIndent()
    )


    data class AssessmentMethod(
        @Description("The type of assessment method.")
        val type: String,
        @Description("A brief description of the assessment method.")
        val description: String
    ) : ValidatedObject {
        override fun validate(): String? {
            if (type.isBlank()) return "Assessment method type is required."
            if (description.isBlank()) return "Assessment method description is required."
            return null
        }
    }

    data class AssessmentPlan(
        @Description("The learning objectives to be assessed.")
        val learningObjectives: List<String>,
        @Description("The suggested assessment methods for the objectives.")
        val assessmentMethods: List<AssessmentMethod>
    ) : ValidatedObject {
        override fun validate(): String? {
            if (learningObjectives.isEmpty()) return "At least one learning objective is required."
            if (assessmentMethods.isEmpty()) return "At least one assessment method is required."
            return null
        }
    }

    private val assessmentPlannerActor = ParsedActor(
//    parserClass = AssessmentPlanParser::class.java,
        resultClass = AssessmentPlan::class.java,
        model = OpenAIModels.GPT4oMini,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            You are an assistant specializing in educational assessment. Your task is to recommend assessment methods that align with specific learning objectives. For each learning objective provided, suggest one or more assessment methods that effectively measure student understanding and mastery.
            
            Learning Objectives:
            - Understand the concept of photosynthesis.
            - Be able to solve basic algebraic equations.
            - Describe the significance of the water cycle.
            
            Based on these objectives, what assessment methods would you recommend?
        """.trimIndent()
    )


    private val customizationActor = SimpleActor(
        prompt = """
            You are an automated lesson planner customization tool.
            Provide options for teachers to customize their lesson plans.
            Listen to the teacher's requests and incorporate their preferences into the lesson plan.
        """.trimIndent(),
        model = OpenAIModels.GPT4oMini,
    )


    data class FeedbackAnalysis(
        @Description("The original feedback provided by the user.")
        val feedback: String,
        @Description("A list of suggestions for improvement based on the feedback.")
        val improvementSuggestions: List<String>
    ) : ValidatedObject {
        override fun validate() = when {
            feedback.isBlank() -> "Feedback is required"
            improvementSuggestions.isEmpty() -> "At least one improvement suggestion is required"
            else -> null
        }
    }

    private val feedbackAnalyzerActor = ParsedActor(
//    parserClass = FeedbackParser::class.java,
        resultClass = FeedbackAnalysis::class.java,
        model = OpenAIModels.GPT4oMini,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            You are an assistant that analyzes teacher feedback on lesson plans to suggest improvements.
            Analyze the following feedback and provide suggestions for improvement.
        """.trimIndent()
    )

    enum class ActorType {
        CURRICULUM_MAPPER_ACTOR,
        RESOURCE_ALLOCATOR_ACTOR,
        TIME_MANAGER_ACTOR,
        ASSESSMENT_PLANNER_ACTOR,
        CUSTOMIZATION_ACTOR,
        FEEDBACK_ANALYZER_ACTOR,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.CURRICULUM_MAPPER_ACTOR to curriculumMapperActor,
        ActorType.RESOURCE_ALLOCATOR_ACTOR to resourceAllocatorActor,
        ActorType.TIME_MANAGER_ACTOR to timeManagerActor,
        ActorType.ASSESSMENT_PLANNER_ACTOR to assessmentPlannerActor,
        ActorType.CUSTOMIZATION_ACTOR to customizationActor,
        ActorType.FEEDBACK_ANALYZER_ACTOR to feedbackAnalyzerActor,
    )

    companion object {
        val log = LoggerFactory.getLogger(AutomatedLessonPlannerArchitectureActors::class.java)
    }
}
