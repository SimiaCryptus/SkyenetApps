package com.simiacryptus.skyenet.apps.generated

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ClientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.function.Function

open class LibraryGeneratorApp(
  applicationName: String = "Software Libary Assistant",
  domainName: String,
) : ApplicationServer(
  applicationName = applicationName,
  path = "/library_generator",
) {

  data class Settings(
    val model: ChatModels = ChatModels.GPT35Turbo,
    val temperature: Double = 0.1,
    val budget : Double = 2.0,
  )
  override val settingsClass: Class<*> get() = Settings::class.java
  @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    try {
      val settings = getSettings<Settings>(session, user)
      (api as ClientManager.MonitoredClient).budget = settings?.budget ?: 2.0
      LibraryGeneratorAgent(
        user = user,
        session = session,
        dataStorage = dataStorage,
        api = api,
        ui = ui,
        model = settings?.model ?: ChatModels.GPT35Turbo,
        temperature = settings?.temperature ?: 0.3,
      ).pseudocodeBreakdown(userMessage)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(LibraryGeneratorApp::class.java)
  }

}



open class LibraryGeneratorAgent(
  user: User?,
  session: Session,
  dataStorage: StorageInterface,
  val ui: ApplicationInterface,
  val api: API,
  model: ChatModels = ChatModels.GPT35Turbo,
  temperature: Double = 0.3,
) : ActorSystem<LibraryGeneratorActors.ActorType>(LibraryGeneratorActors(
  ui = ui,
  api = api,
  model = model,
  temperature = temperature,
).actorMap, dataStorage, user, session) {

  @Suppress("UNCHECKED_CAST")
  private val requirementInterpreter by lazy { getActor(LibraryGeneratorActors.ActorType.REQUIREMENT_INTERPRETER) as ParsedActor<LibraryGeneratorActors.InterpretationResult> }
  private val structureDesigner by lazy { getActor(LibraryGeneratorActors.ActorType.STRUCTURE_DESIGNER) as ParsedActor<LibraryGeneratorActors.DataStructureDesign> }
  private val functionArchitect by lazy { getActor(LibraryGeneratorActors.ActorType.FUNCTION_ARCHITECT) as ParsedActor<LibraryGeneratorActors.FunctionOutline> }
  private val codeSynthesizer by lazy { getActor(LibraryGeneratorActors.ActorType.CODE_SYNTHESIZER) as CodingActor }
  private val documentationComposer by lazy { getActor(LibraryGeneratorActors.ActorType.DOCUMENTATION_COMPOSER) as SimpleActor }
  private val testCaseCreator by lazy { getActor(LibraryGeneratorActors.ActorType.TEST_CASE_CREATOR) as ParsedActor<LibraryGeneratorActors.TestCase> }
  private val qualityAssessor by lazy { getActor(LibraryGeneratorActors.ActorType.QUALITY_ASSESSOR) as ParsedActor<LibraryGeneratorActors.QualityReview> }
  private val outputFormatter by lazy { getActor(LibraryGeneratorActors.ActorType.OUTPUT_FORMATTER) as SimpleActor }

  /**
   * Breaks down the user prompt into a pseudocode representation of the required software library.
   *
   * @param user_prompt The user prompt describing the software library requirements.
   */
  fun pseudocodeBreakdown(user_prompt: String) {
    val task = ui.newTask()
    try {
      task.echo(user_prompt)

      // Extract requirements from the user prompt
      val requirements = extractRequirements(user_prompt)
      task.add("Requirements extracted successfully.")

      // Initialize lists to hold the designs, PseudocodeBreakdownActors.code snippets, documentation, and test cases
      val dataStructuresDesigns = mutableListOf<LibraryGeneratorActors.DataStructureDesign>()
      val functionOutlines = mutableListOf<LibraryGeneratorActors.FunctionOutline>()
      val codeSnippets = mutableListOf<CodeSnippet>()
      val documentations = mutableListOf<Documentation>()
      val testCases = mutableListOf<LibraryGeneratorActors.TestCase>()

      // Design data structures
      for (structureName in requirements.structures) {
        val dataStructureDesign = designDataStructure(structureName)
        dataStructuresDesigns.add(dataStructureDesign)
        val codeSnippet = synthesizeCodeForDataStructure(Structure(dataStructureDesign.name!!, dataStructureDesign.fields!!.map { Field(it.name, it.type) }))
        codeSnippets.add(CodeSnippet(codeSnippet))
        val documentation = composeDocumentation(CodeSnippet(codeSnippet))
        documentations.add(Documentation(documentation))
        testCases.addAll(createTestCases(CodeSnippet(codeSnippet)))
      }

      // Outline functions
      for (functionName in requirements.functions) {
        val functionOutline = outlineFunction(functionName)
        functionOutlines.add(functionOutline)
        val codeSnippet = synthesizeCodeForFunction(functionOutline)
        codeSnippets.add(CodeSnippet(codeSnippet))
        val documentation = composeDocumentation(CodeSnippet(codeSnippet))
        documentations.add(Documentation(documentation))
        testCases.addAll(createTestCases(CodeSnippet(codeSnippet)))
      }

      // Assess the quality of the generated artifacts
      val qualityReview = assessQuality(codeSnippets, documentations, testCases)
      if (!qualityReview.satisfactory) {
        // If the quality is not satisfactory, log the feedback and throw an exception
        task.add("Quality assessment failed. Feedback: ${qualityReview.feedback?.joinToString("\n")}")
        throw Exception("Quality assessment failed.")
      }

      // Format the output
      val formattedOutput = formatOutput(codeSnippets, documentations, testCases)
      task.add("Formatted output:\n$formattedOutput")

      // Save the formatted output
      saveOutput(formattedOutput)

      task.complete("Pseudocode breakdown complete.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  /**
   * Outlines a function based on the given name.
   *
   * @param functionName The name of the function to outline.
   * @return The outlined function.
   */
  private fun outlineFunction(functionName: String): LibraryGeneratorActors.FunctionOutline {
    val task = ui.newTask()
    try {
      task.header("Outlining Function")
      task.add("Outlining the function for: $functionName")

      // Use the functionArchitect actor to outline the function
      val outlineResult = functionArchitect.answer(listOf(functionName), api = api)

      // Log the results
      task.add("Outlined function: ${outlineResult.text}")

      task.complete("Function outline complete.")
      return outlineResult.obj
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  /**
   * Synthesizes PseudocodeBreakdownActors.code for a given function outline.
   *
   * @param functionOutline The outline of the function to synthesize PseudocodeBreakdownActors.code for.
   * @return The synthesized PseudocodeBreakdownActors.code.
   */
  private fun synthesizeCodeForFunction(functionOutline: LibraryGeneratorActors.FunctionOutline): String {
    val task = ui.newTask()
    try {
      task.header("Synthesizing Code for Function")
      task.add("Generating Kotlin PseudocodeBreakdownActors.code for the function: ${functionOutline.name}")

      // Construct the PseudocodeBreakdownActors.code request for the PseudocodeBreakdownActors.code synthesizer
      val codeRequest = CodingActor.CodeRequest(
        messages = listOf(
          "Generate a Kotlin function with the following signature: ${functionOutline.name}(${functionOutline.parameters!!.joinToString { "${it.name}: ${it.type}" }}): ${functionOutline.returnType}" to ApiModel.Role.user
        )
      )

      // Use the codeSynthesizer actor to generate the PseudocodeBreakdownActors.code
      val codeResult = codeSynthesizer.answer(codeRequest, api = api)

      // Log the generated PseudocodeBreakdownActors.code
      task.add("Generated Kotlin Code:\n${codeResult.code}")

      // Optionally, you can execute the generated PseudocodeBreakdownActors.code and log the result
      val executionResult: CodingActor.ExecutionResult = codeResult.result
      task.add("Execution Log: ${executionResult.resultOutput}")
      task.add("Execution Result: ${executionResult.resultValue}")

      task.complete("Code synthesis for function complete.")
      return codeResult.code
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun formatOutput(
    code_snippets: List<CodeSnippet>,
    documentation: List<Documentation>,
    test_cases: List<LibraryGeneratorActors.TestCase>
  ): String {
    val task = ui.newTask()
    try {
      task.header("Formatting Output")
      task.add("Formatting the generated PseudocodeBreakdownActors.code, documentation, and test cases for presentation.")

      // Combine all the PseudocodeBreakdownActors.code snippets, documentation, and test cases into a single PseudocodeBreakdownActors.string
      val combinedContent = buildString {
        append("Code Snippets:\n")
        code_snippets.forEach { snippet ->
          append(snippet.code)
          append("\n\n")
        }
        append("Documentation:\n")
        documentation.forEach { doc ->
          append(doc.content)
          append("\n\n")
        }
        append("Test Cases:\n")
        test_cases.forEach { testCase ->
          append("Description: ${testCase.description}\n")
          append("Input: ${testCase.input}\n")
          append("Expected Output: ${testCase.expectedOutput}\n\n")
        }
      }

      // Use the outputFormatter actor to format the combined content
      val formattedOutput = outputFormatter.answer(listOf(combinedContent), api = api)

      // Log the formatted output
      task.add("Formatted Output:\n$formattedOutput")

      task.complete("Output formatting complete.")
      return formattedOutput
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun createTestCases(code_snippet: CodeSnippet): List<LibraryGeneratorActors.TestCase> {
    val task = ui.newTask()
    try {
      task.header("Creating Test Cases")
      task.add("Generating test cases for the provided PseudocodeBreakdownActors.code snippet.")

      // Use the testCaseCreator actor to generate test cases for the PseudocodeBreakdownActors.code snippet
      val testCasesText = testCaseCreator.answer(listOf(code_snippet.code), api = api)

      // Log the generated test cases
      task.add("Generated Test Cases:\n${testCasesText.text}")

      // Assuming the testCasesText.obj is a list of PseudocodeBreakdownActors.TestCase objects
      val testCases = testCasesText.obj

      task.complete("Test case creation complete.")
      return listOf(testCases)
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun extractRequirements(prompt: String): LibraryGeneratorActors.InterpretationResult {
    val task = ui.newTask()
    try {
      task.header("Extracting Requirements")
      task.add("Interpreting the user prompt to identify required data structures and functions.")

      // Use the requirementInterpreter actor to interpret the prompt
      val interpretationResult = requirementInterpreter.answer(listOf(prompt), api = api)

      // Log the results
      task.add("Identified data structures: ${interpretationResult.obj.structures.joinToString()}")
      task.add("Identified functions: ${interpretationResult.obj.functions.joinToString()}")

      task.complete("Requirements extraction complete.")
      return interpretationResult.obj
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  // Assuming the Structure and Field classes are defined as follows:
  data class Structure(
    val name: String,
    val fields: List<Field>
  )

  data class Field(
    val name: String,
    val type: String
  )

  // Implement the function to synthesize PseudocodeBreakdownActors.code for a given data structure
  private fun synthesizeCodeForDataStructure(data_structure: Structure): String {
    val task = ui.newTask()
    try {
      task.header("Synthesizing Code for Data Structure")
      task.add("Generating Kotlin PseudocodeBreakdownActors.code for the data structure: ${data_structure.name}")

      // Construct the PseudocodeBreakdownActors.code request for the PseudocodeBreakdownActors.code synthesizer
      val codeRequest = CodingActor.CodeRequest(
        messages = listOf(
          "Generate a Kotlin data class for the following structure: `data class ${data_structure.name}(${data_structure.fields.joinToString { "${it.name}: ${it.type}" }})`" to ApiModel.Role.user
        )
      )

      // Use the codeSynthesizer actor to generate the PseudocodeBreakdownActors.code
      val codeResult = codeSynthesizer.answer(codeRequest, api = api)

      // Log the generated PseudocodeBreakdownActors.code
      task.add("Generated Kotlin Code:\n${codeResult.code}")

      // Optionally, you can execute the generated PseudocodeBreakdownActors.code and log the result
      val executionResult: CodingActor.ExecutionResult = codeResult.result
      task.add("Execution Log: ${executionResult.resultOutput}")
      task.add("Execution Result: ${executionResult.resultValue}")

      task.complete("Code synthesis for data structure complete.")
      return codeResult.code
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  data class CodeSnippet(
    val code: String
  )

  private fun composeDocumentation(code_snippet: CodeSnippet): String {
    val task = ui.newTask()
    try {
      task.header("Composing Documentation")
      task.add("Creating documentation for the provided PseudocodeBreakdownActors.code snippet.")

      // Use the documentationComposer actor to create documentation for the PseudocodeBreakdownActors.code snippet
      val documentation = documentationComposer.answer(listOf(code_snippet.code), api = api)

      // Log the generated documentation
      task.add("Generated Documentation:\n$documentation")

      task.complete("Documentation composition complete.")
      return documentation
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  data class Documentation(
    val content: String
  )

  private fun assessQuality(
    code_snippets: List<CodeSnippet>,
    documentation: List<Documentation>,
    test_cases: List<LibraryGeneratorActors.TestCase>
  ): LibraryGeneratorActors.QualityReview {
    val task = ui.newTask()
    try {
      task.header("Assessing Quality")
      task.add("Reviewing the quality of the generated PseudocodeBreakdownActors.code, documentation, and test cases.")

      // Prepare the content for quality assessment
      val assessmentContent = code_snippets.map { it.code } +
          documentation.map { it.content } +
          test_cases.map { "Description: ${it.description}, Input: ${it.input}, Expected Output: ${it.expectedOutput}" }

      // Use the qualityAssessor actor to assess the quality
      val qualityReview = qualityAssessor.answer(assessmentContent, api = api)

      // Log the quality review results
      task.add("Quality Assessment Result: ${if (qualityReview.obj.satisfactory) "Satisfactory" else "Not Satisfactory"}")
      qualityReview.obj.feedback?.let { feedback ->
        task.add("Feedback for improvement:\n${feedback.joinToString(separator = "\n")}")
      }

      task.complete("Quality assessment complete.")
      return qualityReview.obj
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }


  /**
   * Saves the formatted output to a file.
   *
   * @param formatted_output The formatted output to be saved.
   */
  private fun saveOutput(formatted_output: String) {
    val task = ui.newTask()
    try {
      task.header("Saving Output")
      task.add("Saving the formatted output to a file.")

      // Define the path and file name for the output file
      val outputPath = Paths.get("output")
      val fileName = "formatted_output_${System.currentTimeMillis()}.txt"
      val outputFile = outputPath.resolve(fileName)

      // Ensure the output directory exists
      Files.createDirectories(outputPath)

      // Write the formatted output to the file
      Files.write(outputFile, formatted_output.toByteArray(), StandardOpenOption.CREATE_NEW)

      task.add("Formatted output has been successfully saved to: $outputFile")
      task.complete("Output saving complete.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun designDataStructure(structure: String): LibraryGeneratorActors.DataStructureDesign {
    val task = ui.newTask()
    try {
      task.header("Designing Data Structure")
      task.add("Designing the data structure for: $structure")

      // Use the structureDesigner actor to design the data structure
      val designResult = structureDesigner.answer(listOf(structure), api = api)

      // Log the results
      task.add("Designed data structure: ${designResult.text}")

      task.complete("Data structure design complete.")
      return designResult.obj
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(LibraryGeneratorAgent::class.java)

  }
}



class LibraryGeneratorActors(
  val ui: ApplicationInterface,
  val api: API,
  val model: ChatModels = ChatModels.GPT4Turbo,
  val temperature: Double = 0.3,
) {


  data class InterpretationResult(
    @Description("The list of identified data structures.")
    val structures: List<String>,
    @Description("The list of identified functions.")
    val functions: List<String>
  ) : ValidatedObject {
    override fun validate() = when {
      structures.isEmpty() && functions.isEmpty() -> "At least one structure or function is required"
      else -> null
    }
  }

  interface RequirementInterpreter : Function<String, InterpretationResult> {
    @Description("Interpret the user's requirements for data structures and functions.")
    override fun apply(text: String): InterpretationResult
  }

  private val requirementInterpreter = ParsedActor<InterpretationResult>(
    parserClass = RequirementInterpreter::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that interprets software requirements. Analyze the following description and extract the required data structures and functions.
        """.trimIndent()
  )


  data class DataStructureDesign(
    @Description("The name of the data structure.")
    val name: String? = null,
    @Description("The list of fields within the data structure.")
    val fields: List<Field>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      name.isNullOrBlank() -> "Data structure name is required."
      fields.isNullOrEmpty() -> "Data structure must have at least one field."
      else -> null
    }
  }

  data class Field(
    @Description("The name of the field.")
    val name: String,
    @Description("The type of the field.")
    val type: String
  )

  interface StructureDesignerParser : Function<String, DataStructureDesign> {
    @Description("Parse the text response into a data structure design.")
    override fun apply(text: String): DataStructureDesign
  }

  private val structureDesigner = ParsedActor<DataStructureDesign>(
    parserClass = StructureDesignerParser::class.java,
    prompt = """
            You are an AI that designs data structures based on specified requirements. Given a description, create a data structure with appropriate fields and types.
        """.trimIndent(),
    model = ChatModels.GPT35Turbo,
    temperature = 0.3
  )


  data class FunctionOutline(
    @Description("The name of the function")
    val name: String? = null,
    @Description("The list of parameters for the function")
    val parameters: List<Parameter>? = null,
    @Description("The return type of the function")
    val returnType: String? = null
  ) : ValidatedObject {
    override fun validate() = when {
      name.isNullOrBlank() -> "Function name is required"
      parameters == null || parameters.isEmpty() -> "Function parameters are required"
      returnType.isNullOrBlank() -> "Function return type is required"
      else -> null
    }
  }

  data class Parameter(
    @Description("The name of the parameter")
    val name: String,
    @Description("The type of the parameter")
    val type: String
  )

  interface FunctionOutlineParser : Function<String, FunctionOutline> {
    @Description("Parse the text response into a FunctionOutline data structure.")
    override fun apply(text: String): FunctionOutline
  }

  private val functionArchitect = ParsedActor<FunctionOutline>(
    parserClass = FunctionOutlineParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an AI that outlines software functions. Given a description, you will provide the function name, parameters, and return type.
            """.trimIndent()
  )


  private val codeSynthesizer = CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf(
      "ui" to ui,
      "api" to api,
    ),
    details = """
            You are a code synthesizer.
            
            Your task is to generate Kotlin code based on the provided data structures and functions.
            You have access to a set of predefined symbols and functions that you can use in your code generation process.
            
            Defined symbols:
            * ui: ApplicationInterface
            * api: API
            * pool: ThreadPoolExecutor
            
            Expected code structure:
            * Functions should be well-defined with appropriate parameters and return types.
            * Data structures should be represented as Kotlin classes or data classes with properties.
            * Use the provided symbols to interact with the system and perform tasks.
        """.trimIndent()
  )


  private val documentationComposer = SimpleActor(
    prompt = """
            You are a documentation composer.
            Your task is to create clear and concise documentation for the provided code snippets.
            The documentation should explain the purpose of the code, how it works, and how to use it.
            Include descriptions of any classes, methods, parameters, and return values.
        """.trimIndent()
  )


  interface TestCaseParser : Function<String, TestCase> {
    @Description("Parse the text into a test case data structure.")
    override fun apply(text: String): TestCase
  }

  data class TestCase(
    @Description("A brief description of the test case.")
    val description: String? = null,
    @Description("The input for the test case.")
    val input: String? = null,
    @Description("The expected output for the test case.")
    val expectedOutput: String? = null
  ) : ValidatedObject {
    override fun validate(): String? = when {
      description.isNullOrBlank() -> "Description is required."
      input.isNullOrBlank() -> "Input is required."
      expectedOutput.isNullOrBlank() -> "Expected output is required."
      else -> null
    }
  }

  private val testCaseCreator = ParsedActor<TestCase>(
    parserClass = TestCaseParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
    You are an assistant that creates detailed test cases for software functions. Given a description of a function and its expected behavior, generate a test case with the following structure: a brief description, the input for the test case, and the expected output.
    """.trimMargin().trim()
  )


  data class QualityReview(
    @Description("Indicates if the quality of the generated code, documentation, and test cases is satisfactory.")
    val satisfactory: Boolean,
    @Description("A list of feedback points to improve the generated artifacts.")
    val feedback: List<String>? = null
  ) : ValidatedObject {
    override fun validate() = when {
      satisfactory && feedback != null && feedback.isNotEmpty() -> "Feedback should be empty if the quality is satisfactory."
      !satisfactory && (feedback == null || feedback.isEmpty()) -> "Feedback is required if the quality is not satisfactory."
      else -> null
    }
  }

  interface QualityReviewParser : Function<String, QualityReview> {
    @Description("Parse the text response into a QualityReview data structure.")
    override fun apply(text: String): QualityReview
  }

  private val qualityAssessor = ParsedActor<QualityReview>(
    parserClass = QualityReviewParser::class.java,
    model = ChatModels.GPT35Turbo,
    prompt = """
            You are an assistant that reviews the quality of generated code, documentation, and test cases.
            Assess the quality based on best practices, correctness, and completeness.
            If the quality is not satisfactory, provide specific feedback for improvement.
        """.trimIndent()
  )


  private val outputFormatter = SimpleActor(
    prompt = """
            You are an output formatter. Your job is to take the generated code, documentation, and test cases and format them neatly for presentation. Ensure that the code is properly indented and commented, the documentation is clear and concise, and the test cases are well-organized and easy to understand.
        """.trimIndent()
  )

  enum class ActorType {
    REQUIREMENT_INTERPRETER,
    STRUCTURE_DESIGNER,
    FUNCTION_ARCHITECT,
    CODE_SYNTHESIZER,
    DOCUMENTATION_COMPOSER,
    TEST_CASE_CREATOR,
    QUALITY_ASSESSOR,
    OUTPUT_FORMATTER,
  }

  val actorMap: Map<ActorType, BaseActor<out Any,out Any>> = mapOf(
    ActorType.REQUIREMENT_INTERPRETER to requirementInterpreter,
    ActorType.STRUCTURE_DESIGNER to structureDesigner,
    ActorType.FUNCTION_ARCHITECT to functionArchitect,
    ActorType.CODE_SYNTHESIZER to codeSynthesizer,
    ActorType.DOCUMENTATION_COMPOSER to documentationComposer,
    ActorType.TEST_CASE_CREATOR to testCaseCreator,
    ActorType.QUALITY_ASSESSOR to qualityAssessor,
    ActorType.OUTPUT_FORMATTER to outputFormatter,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(LibraryGeneratorActors::class.java)
  }
}
