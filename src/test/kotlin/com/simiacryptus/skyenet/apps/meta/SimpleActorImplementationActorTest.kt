package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.Heart
import com.simiacryptus.skyenet.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.actors.opt.Expectation
import com.simiacryptus.skyenet.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.actors.test.ParsedActorTestBase
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

object SimpleActorImplementationActorTest : CodingActorTestBase() {

    @Test
    override fun testRun() = super.testRun()
//    @Test
    override fun testOptimize() = super.testOptimize()
    override val actor = MetaActors.simpleActorDesigner()
    override val interpreterClass = KotlinInterpreter::class
    @Language("Markdown")
    override val testCases = listOf(
        ActorOptimization.TestCase(
            userMessages = listOf(
                "Design a software project designer",
                """
                # Software Project Generator System Design Document
                
                ## Introduction
                
                The Software Project Generator is a system designed to automate the creation of software project scaffolding, tailored to the user's specifications. This system leverages a community of GPT "actors" to interpret user requirements, generate project structure, and provide code templates. The system aims to streamline the initial setup process of software development, allowing developers to focus on implementing business logic and features.
                
                ## System Actors
                
                ### 1. Requirement Interpreter Actor (Simple Actor)
                
                **Purpose**: This actor's primary function is to process a list of user messages detailing project requirements and convert them into a structured format that can be understood by subsequent actors.
                
                **Usage**: Users input their project requirements in natural language. The Requirement Interpreter Actor processes these inputs and generates a response that outlines the understood requirements and any clarifications needed.
                
                ### 2. Project Structure Generator Actor (Parsed Actor)
                
                **Purpose**: This actor takes the structured requirements from the Requirement Interpreter Actor and generates a high-level outline of the project structure, including directory layout and necessary configuration files.
                
                **Usage**: Once the requirements are confirmed, this actor parses the response into a predefined Kotlin data class representing the project structure. It then uses GPT3.5_Turbo to generate a textual representation of the project structure, which is further parsed into a more detailed and actionable format.
                
                ### 3. Code Template Producer Actor (Script Actor)
                
                **Purpose**: This actor uses the project structure outline to generate code templates and boilerplate for the project. It can produce scripts in Scala, Kotlin, or Groovy, depending on the user's choice.
                
                **Usage**: The actor takes the detailed project structure and utilizes an environment definition with predefined symbols/functions to generate the necessary code templates. The result includes a valid script with an "execute" method that can be used to create the project files and directories.
                
                ## Logical Flow of the System
                
                1. **User Input**: The user interacts with the system, providing a description of the desired software project, including language, frameworks, and any specific patterns or features.
                
                2. **Requirement Interpretation**:
                    - The Requirement Interpreter Actor receives the user's input.
                    - It processes the input and generates a structured outline of the requirements.
                    - The actor may request additional information or clarification from the user if necessary.
                
                3. **Project Structure Generation**:
                    - The structured requirements are passed to the Project Structure Generator Actor.
                    - This actor generates a high-level project structure outline, including directories and configuration files.
                    - The textual representation of the project structure is parsed into a Kotlin data class for further processing.
                
                4. **Code Template Production**:
                    - The detailed project structure is provided to the Code Template Producer Actor.
                    - The actor uses the structure to generate code templates and boilerplate scripts.
                    - The generated scripts are tailored to the user's specifications and include an "execute" method.
                
                5. **Project Scaffolding Execution**:
                    - The generated scripts are executed, creating the project's directory structure and populating it with the initial code templates and configuration files.
                    - The system may provide additional instructions or scripts to run the project or integrate with version control systems.
                
                6. **Review and Delivery**:
                    - The generated project is presented to the user for review.
                    - The user can request changes or further customization, which are processed by looping back to the appropriate actor.
                    - Once approved, the project is packaged and delivered to the user, ready for development.
                
                ## Optional UI Elements
                
                - A web-based interface allows users to input requirements and view the generated project structure and code templates.
                - Interactive elements enable users to tweak the generated output before finalizing the project scaffolding.
                
                ## Conclusion
                
                The Software Project Generator system is designed to be a flexible and efficient tool for setting up new software projects. By leveraging the capabilities of GPT actors and existing JVM libraries, the system can cater to a wide range of project requirements and streamline the initial development process.
                """.trimIndent(),
                "Implement RequirementInterpreterActor",
            ),
            expectations = listOf(
                Expectation.ContainsMatch("""`search\('.*?'\)`""".toRegex(), critical = false),
                Expectation.ContainsMatch("""search\(.*?\)""".toRegex(), critical = false),
                Expectation.VectorMatch("Great, what kind of book are you looking for?")
            )
        )
    )
}