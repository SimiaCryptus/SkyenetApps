package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.util.function.Function


open class PresentationGeneratorApp(
  applicationName: String = "Presentation Generator",
  domainName: String,
) : ApplicationServer(
  applicationName = applicationName,
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT35Turbo,
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
      PresentationGeneratorAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).revisedArchitectureAndPseudocode(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(PresentationGeneratorApp::class.java)
  }

}

open class PresentationGeneratorAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<PresentationGeneratorActors.ActorType>(
  PresentationGeneratorActors(
    model = model,
    temperature = temperature,
  ).actorMap, dataStorage, user, session
) {

  @Suppress("UNCHECKED_CAST")
  private val contentActor by lazy { getActor(PresentationGeneratorActors.ActorType.CONTENT_ACTOR) as ParsedActor<PresentationGeneratorActors.PresentationContent> }
  @Suppress("UNCHECKED_CAST")
  private val designActor by lazy { getActor(PresentationGeneratorActors.ActorType.DESIGN_ACTOR) as ParsedActor<PresentationGeneratorActors.PresentationDesign> }
  private val imageActor by lazy { getActor(PresentationGeneratorActors.ActorType.IMAGE_ACTOR) as ImageActor }
  @Suppress("UNCHECKED_CAST")
  private val speakerNotesActor by lazy { getActor(PresentationGeneratorActors.ActorType.SPEAKER_NOTES_ACTOR) as ParsedActor<PresentationGeneratorActors.SpeakerNotes> }
  @Suppress("UNCHECKED_CAST")
  private val optimizationActor by lazy { getActor(PresentationGeneratorActors.ActorType.OPTIMIZATION_ACTOR) as ParsedActor<PresentationGeneratorActors.OptimizedPresentation> }
  @Suppress("UNCHECKED_CAST")
  private val feedbackActor by lazy { getActor(PresentationGeneratorActors.ActorType.FEEDBACK_ACTOR) as ParsedActor<PresentationGeneratorActors.RefinedPresentation> }

  // Implement the revised architecture and pseudocode logic
  fun revisedArchitectureAndPseudocode(userInput: String) {
    val task = ui.newTask()
    try {
      task.header("Starting Presentation Generation Process")

      // Step 1: Spawn threads for each actor to process the input in parallel
      val contentFuture = pool.submit<ParsedResponse<PresentationGeneratorActors.PresentationContent>> {
        contentActor.answer(listOf(userInput), api = api)
      }
      val designFuture = pool.submit<ParsedResponse<PresentationGeneratorActors.PresentationDesign>> {
        designActor.answer(listOf(userInput), api = api)
      }
      val imageFuture = pool.submit<ImageResponse> {
        imageActor.answer(listOf(userInput), api = api)
      }
      val notesFuture = pool.submit<ParsedResponse<PresentationGeneratorActors.SpeakerNotes>> {
        speakerNotesActor.answer(listOf(userInput), api = api)
      }

      // Step 2: Collect results from each actor
      val slidesContent = contentFuture.get().obj
      val slidesDesign = designFuture.get().obj
      val slidesImages = imageFuture.get().image
      val speakerNotes = notesFuture.get().obj

      // Step 3: Merge content, design, and images into presentation slides
      val presentationSlides = mergeContentDesignImages(slidesContent, slidesDesign, slidesImages)

      // Step 4: Optimize the presentation slides and speaker notes
      val optimizationResponse = optimizationActor.answer(
        listOf(
          "Optimize the presentation slides and speaker notes.",
          presentationSlides.toString(),
          speakerNotes.toString()
        ), api = api
      )
      val optimizedSlides = optimizationResponse.obj.optimizedContent
      val optimizedNotes = optimizationResponse.obj.optimizedNotes

      // Step 5: Display the optimized presentation and speaker notes
      task.add("Optimized Slides: $optimizedSlides")
      task.add("Optimized Speaker Notes: $optimizedNotes")

      // Step 6: Store the presentation
      // Placeholder for storing the presentation (implementation depends on the storage system)
      // storageSystem.storePresentation(optimizedSlides, optimizedNotes)

      // Step 7: Display the presentation to the user
      // Placeholder for displaying the presentation (implementation depends on the web interface)
      // webInterface.displayPresentation(optimizedSlides, optimizedNotes)

      task.complete("Presentation generation complete.")
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
  }
//
//  // Helper function to merge content, design, and images into presentation slide
//  // Call the revised architecture and pseudocode function with user input
//  //revisedArchitectureAndPseudocode("User input for presentation generation")
//
//  // Define the function to initialize and manage system components
//  fun systemComponents() {
//    // Initialize the web interface
//    val webInterface = ui // Assuming 'ui' is already provided as part of the system context
//
//    // Initialize the storage system
//    // Placeholder for storage system initialization
//    // In a real-world scenario, this would involve setting up connections to a database or file storage service
//    val storageSystem = object {
//      fun storePresentation(content: RevisedArchitectureAndPseudocodeActors.PresentationContent, design: RevisedArchitectureAndPseudocodeActors.PresentationDesign, notes: RevisedArchitectureAndPseudocodeActors.SpeakerNotes) {
//        // Implement the logic to store the presentation components
//        // This is a placeholder for demonstration purposes
//      }
//
//      fun retrievePresentation(presentationId: String): Triple<RevisedArchitectureAndPseudocodeActors.PresentationContent, RevisedArchitectureAndPseudocodeActors.PresentationDesign, RevisedArchitectureAndPseudocodeActors.SpeakerNotes>? {
//        // Implement the logic to retrieve the presentation components by ID
//        // This is a placeholder for demonstration purposes
//        return null // Return null if the presentation is not found
//      }
//    }
//
//    // Initialize the threading manager
//    // In a real-world scenario, this would involve setting up a thread pool or task scheduler
//    val threadingManager = pool // 'pool' is already provided as part of the system context
//
//    // Initialize the communication layer
//    // Placeholder for communication layer initialization
//    // In a real-world scenario, this would involve setting up web sockets, REST APIs, or other communication protocols
//    val communicationLayer = object {
//      fun sendMessage(message: String) {
//        // Implement the logic to send a message to the user
//        // This is a placeholder for demonstration purposes
//      }
//    }
//
//    // Define the logic to generate and manage the presentation
//    fun generatePresentation(userInput: String) {
//      val task = webInterface.newTask()
//      try {
//        task.header("Generating Presentation")
//
//        // Execute the content actor
//        val contentFuture = threadingManager.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.PresentationContent>> {
//          contentActor.answer(listOf(userInput), api = api)
//        }
//
//        // Execute the design actor
//        val designFuture = threadingManager.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.PresentationDesign>> {
//          designActor.answer(listOf(userInput), api = api)
//        }
//
//        // Execute the RevisedArchitectureAndPseudocodeActors.image actor
//        val imageFuture = threadingManager.submit<ImageResponse> {
//          imageActor.answer(listOf(userInput), api = api)
//        }
//
//        // Execute the speaker notes actor
//        val notesFuture = threadingManager.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.SpeakerNotes>> {
//          speakerNotesActor.answer(listOf(userInput), api = api)
//        }
//
//        // Wait for all futures to complete
//        val content = contentFuture.get()
//        val design = designFuture.get()
//        val images = imageFuture.get()
//        val notes = notesFuture.get()
//
//        // Display the content, design, and images
//        task.add("Content: ${content.text}")
//        task.add("Design: ${design.text}")
//        task.image(images.image)
//
//        // Optimize the presentation
//        val optimizedPresentation = optimizationActor.answer(listOf("Please optimize the presentation.", content.text, design.text), api = api)
//        task.add("Optimized Presentation: ${optimizedPresentation.text}")
//
//        // Get user feedback and refine the presentation if necessary
//        task.add(webInterface.textInput { feedback ->
//          val refinedPresentation = feedbackActor.answer(listOf("Please refine the presentation based on the following feedback.", feedback), api = api)
//          task.add("Refined Presentation: ${refinedPresentation.text}")
//        })
//
//        task.complete("Presentation generation complete.")
//      } catch (e: Throwable) {
//        task.error(e)
//        throw e
//      }
//    }
//
//  }
//
//  // Define the overall logic flow function
//  fun overallLogicFlow() {
//    val task = ui.newTask()
//    try {
//      task.header("Starting Presentation Generation Process")
//
//      // Step 1: Get user input for the content of the presentation
//      task.add(ui.textInput { userInput ->
//        // Step 2: Spawn threads for each actor to process the input in parallel
//        val contentFuture = pool.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.PresentationContent>> {
//          contentActor.answer(listOf(userInput), api = api)
//        }
//        val designFuture = pool.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.PresentationDesign>> {
//          designActor.answer(listOf(userInput), api = api)
//        }
//        val imageFuture = pool.submit<ImageResponse> {
//          imageActor.answer(listOf(userInput), api = api)
//        }
//        val notesFuture = pool.submit<ParsedResponse<RevisedArchitectureAndPseudocodeActors.SpeakerNotes>> {
//          speakerNotesActor.answer(listOf(userInput), api = api)
//        }
//
//        // Step 3: Collect results from each actor
//        val slidesContent = contentFuture.get().obj
//        val slidesDesign = designFuture.get().obj
//        val slidesImages = imageFuture.get().image
//        val speakerNotes = notesFuture.get().obj
//
//        // Step 4: Merge content, design, and images into presentation slides
//        val presentationSlides = mergeContentDesignImages(slidesContent, slidesDesign, slidesImages)
//
//        // Step 5: Optimize the presentation slides and speaker notes
//        val optimizationResponse = optimizationActor.answer(listOf("Optimize the presentation slides and speaker notes.", presentationSlides.toString(), speakerNotes.toString()), api = api)
//        val optimizedSlides = optimizationResponse.obj.optimizedContent
//        val optimizedNotes = optimizationResponse.obj.optimizedNotes
//
//        // Step 6: Display the optimized presentation and speaker notes
//        task.add("Optimized Slides: $optimizedSlides")
//        task.add("Optimized Speaker Notes: $optimizedNotes")
//
//        // Step 7: Store the presentation
//        // Placeholder for storing the presentation (implementation depends on the storage system)
//        // storageSystem.storePresentation(optimizedSlides, optimizedNotes)
//
//        // Step 8: Display the presentation to the user
//        // Placeholder for displaying the presentation (implementation depends on the web interface)
//        // webInterface.displayPresentation(optimizedSlides, optimizedNotes)
//
//        task.complete("Presentation generation complete.")
//      })
//    } catch (e: Throwable) {
//      task.error(e)
//      throw e
//    }
//  }

  // Helper function to merge content, design, and images into presentation slides
  private fun mergeContentDesignImages(
    content: PresentationGeneratorActors.PresentationContent,
    design: PresentationGeneratorActors.PresentationDesign,
    image: BufferedImage
  ): PresentationGeneratorActors.PresentationContent {
    // Placeholder for merging logic (implementation depends on the presentation format)
    // This function should integrate the content, design, and images into a cohesive set of slides
    // For now, we'll return the content as is
    return content
  }


  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(PresentationGeneratorAgent::class.java)
  }
}


class PresentationGeneratorActors(
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  // Define the data class to represent the content of a presentation slide
  data class SlideContent(
    val title: String?,
    val bulletPoints: List<String>?,
    val additionalText: String?
  ) : ValidatedObject {
    override fun validate() = when {
      title.isNullOrBlank() -> "Title is required"
      bulletPoints == null || bulletPoints.isEmpty() -> "At least one bullet point is required"
      else -> null
    }
  }

  // Define the data class to represent the content of the entire presentation
  data class PresentationContent(
    val slides: List<SlideContent>
  ) : ValidatedObject {
    override fun validate() = when {
      slides.isEmpty() -> "At least one slide content is required"
      else -> null
    }
  }

  // Define the parser interface
  interface PresentationContentParser : Function<String, PresentationContent> {
    override fun apply(text: String): PresentationContent
  }

  // Instantiate the contentActor using the ParsedActor constructor
  val contentActor = ParsedActor<PresentationContent>(
    parserClass = PresentationContentParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an AI that generates content for presentation slides. Create engaging and informative slides based on the following topics provided by the user.
        """.trimIndent()
  )


  data class SlideDesign(
    @Description("The layout of the slide")
    val layout: String? = null,
    @Description("The color scheme of the slide")
    val colorScheme: String? = null,
    @Description("Design elements for the slide")
    val designElements: List<String>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      layout.isNullOrBlank() -> "layout is required"
      colorScheme.isNullOrBlank() -> "colorScheme is required"
      designElements.isNullOrEmpty() -> "designElements are required"
      else -> null
    }
  }

  data class PresentationDesign(
    @Description("A list of slide designs")
    val slideDesigns: List<SlideDesign>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      slideDesigns.isNullOrEmpty() -> "slideDesigns are required"
      else -> null
    }
  }

  interface DesignParser : Function<String, PresentationDesign> {
    @Description("Parse the text response into a PresentationDesign data structure.")
    override fun apply(text: String): PresentationDesign
  }

  val designActor = ParsedActor<PresentationDesign>(
    parserClass = DesignParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that creates design suggestions for presentation slides.
            Generate a design for each slide including layout, color scheme, and any additional design elements.
            """.trimIndent()
  )


  val imageActor = ImageActor(
    prompt = "Create a visually appealing image that complements the content of the presentation slide.",
    name = "Presentation Image Generator",
    imageModel = ImageModels.DallE2,
    temperature = 0.7,
    width = 1024,
    height = 768
  )


  // Define the data class to hold the speaker notes.
  data class SpeakerNotes(
    val notesPerSlide: Map<Int, String>
  ) : ValidatedObject {
    override fun validate(): String? {
      if (notesPerSlide.isEmpty()) return "Speaker notes cannot be empty"
      if (notesPerSlide.any { it.value.isBlank() }) return "Speaker notes cannot contain blank entries"
      return null
    }
  }

  // Define the parser interface.
  interface SpeakerNotesParser : Function<String, SpeakerNotes> {
    override fun apply(text: String): SpeakerNotes {
      // Implement the parsing logic here. This is a placeholder.
      // The actual implementation should parse the text and create a map of slide numbers to notes.
      val notesMap = text.lines()
        .filter { it.isNotBlank() }
        .mapIndexed { index, note -> index to note.trim() }
        .toMap()

      return SpeakerNotes(notesMap)
    }
  }

  // Instantiate the ParsedActor for speaker notes.
  val speakerNotesActor = ParsedActor<SpeakerNotes>(
    parserClass = SpeakerNotesParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that generates speaker notes for presentation slides.
            Your task is to create concise, clear, and helpful notes that a speaker can refer to while presenting each slide.
            Below are the contents of the slides, please provide the corresponding speaker notes.
            
            Slide 1: [Content of Slide 1]
            
            Slide 2: [Content of Slide 2]
            
            Slide 3: [Content of Slide 3]
            
            ...
            
            Please provide the speaker notes for each slide:
        """.trimIndent()
  )


  data class OptimizedPresentation(
    @Description("The optimized content of the presentation.")
    val optimizedContent: String, // This should be replaced with the actual content structure

    @Description("The optimized design of the presentation.")
    val optimizedDesign: String, // This should be replaced with the actual design structure

    @Description("The optimized speaker notes of the presentation.")
    val optimizedNotes: String // This should be replaced with the actual notes structure
  ) : ValidatedObject {
    override fun validate(): String? {
      // Add validation logic if necessary
      return null
    }
  }

  interface OptimizationParser : Function<String, OptimizedPresentation> {
    @Description("Parse the text into an optimized presentation structure.")
    override fun apply(text: String): OptimizedPresentation
  }

  val optimizationActor = ParsedActor<OptimizedPresentation>(
    parserClass = OptimizationParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an optimization assistant. Your task is to optimize presentation slides and speaker notes for clarity, impact, and aesthetics. Given the content and design of the presentation slides along with the speaker notes, provide an optimized version of the presentation.
        """.trimIndent()
  )

  // Define the data class to hold the refined presentation details
  data class RefinedPresentation(
    @Description("The refined content of the presentation")
    val refinedContent: PresentationContent? = null,

    @Description("The refined design of the presentation")
    val refinedDesign: PresentationDesign? = null,

    @Description("The refined speaker notes of the presentation")
    val refinedNotes: SpeakerNotes? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      // Add validation logic if necessary
      return null
    }
  }

  // Define the parser interface
  interface FeedbackParser : Function<String, RefinedPresentation> {
    @Description("Parse the text into a refined presentation data structure.")
    override fun apply(text: String): RefinedPresentation
  }

  // Instantiate the feedbackActor
  val feedbackActor = ParsedActor<RefinedPresentation>(
    parserClass = FeedbackParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an AI assistant helping to refine a presentation based on user feedback.
            Given the optimized presentation slides, speaker notes, and user feedback,
            provide a refined version of the presentation content, design, and speaker notes.
        """.trimIndent()
  )

  enum class ActorType {
    CONTENT_ACTOR,
    DESIGN_ACTOR,
    IMAGE_ACTOR,
    SPEAKER_NOTES_ACTOR,
    OPTIMIZATION_ACTOR,
    FEEDBACK_ACTOR,
  }

  val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
    ActorType.CONTENT_ACTOR to contentActor,
    ActorType.DESIGN_ACTOR to designActor,
    ActorType.IMAGE_ACTOR to imageActor,
    ActorType.SPEAKER_NOTES_ACTOR to speakerNotesActor,
    ActorType.OPTIMIZATION_ACTOR to optimizationActor,
    ActorType.FEEDBACK_ACTOR to feedbackActor,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(PresentationGeneratorActors::class.java)
  }
}
