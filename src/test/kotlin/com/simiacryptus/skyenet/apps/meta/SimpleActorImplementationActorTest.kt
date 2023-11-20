package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.actors.opt.Expectation
import com.simiacryptus.skyenet.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

object SimpleActorImplementationActorTest : CodingActorTestBase() {

    @Test
    override fun testRun() = super.testRun()

    //    @Test
    override fun testOptimize() = super.testOptimize()
    override val actor = MetaActors().simpleActorDesigner()
    override val interpreterClass = KotlinInterpreter::class

    @Language("Markdown")
    override val testCases = listOf(
        ActorOptimization.TestCase(
            userMessages = listOf(
                "Create a software project generator",
                """
                # Software Project Generator System Design Document
                
                ## Overview
                
                The Software Project Generator is a system designed to assist users in creating the scaffolding for new software projects across various programming languages and frameworks. The system employs GPT-based "actors" to interact with the user, gather requirements, and generate the necessary code and documentation. This document outlines the system design, including the actors used and the logical flow of the system.
                
                ## Actors
                
                ### 1. Requirement Collector Actor (Simple Actor)
                
                **Purpose:** This actor's role is to interact with the user to collect the requirements for the new software project. It asks questions about the project's scope, desired programming language, frameworks, and any specific libraries or tools the user wants to include.
                
                **Usage:** The actor processes user messages and generates a response that guides the user through the requirement collection process. It ensures all necessary information is gathered to create the project scaffold.
                
                ### 2. Project Template Selector Actor (Parsed Actor)
                
                **Purpose:** Based on the requirements collected by the Requirement Collector Actor, the Project Template Selector Actor suggests appropriate project templates and configurations.
                
                **Usage:** The actor first responds to queries like a simple actor, then uses GPT3.5_Turbo to parse the text response into a predefined Kotlin data class representing project templates. This structured data is used for further processing and decision-making.
                
                ### 3. Code Generator Actor (Script Actor)
                
                **Purpose:** This actor takes the selected project template and user requirements to generate the project scaffold, including directory structure, base code files, build scripts, and initial documentation.
                
                **Usage:** The actor combines an environment definition with a pluggable script compilation system using Scala, Kotlin, or Groovy. It returns a valid script with an "execute" method that, when run, creates the project scaffold.
                
                ## Logical Flow
                
                ### Step 1: Requirement Collection
                - The user initiates the process by expressing the desire to create a new software project.
                - The Requirement Collector Actor engages the user in a conversation to gather all necessary project requirements.
                - The actor uses a system directive to ensure the conversation stays on track and covers all aspects needed for project generation.
                
                ### Step 2: Template Selection
                - Once the requirements are collected, the Project Template Selector Actor takes over.
                - The actor suggests several project templates that match the user's requirements.
                - The user selects a preferred template, and the actor parses the selection into a data class for further processing.
                
                ### Step 3: Project Generation
                - The Code Generator Actor receives the selected template and user requirements.
                - It uses the environment definition and script compilation system to generate the project scaffold.
                - The actor creates a script that, when executed, will set up the project's directory structure, base code files, build scripts, and initial documentation.
                
                ### Step 4: Project Delivery
                - The generated script is delivered to the user with instructions on how to execute it.
                - The user runs the script on their local machine or a designated environment to create the new software project.
                - The system may provide additional support or instructions for the next steps, such as version control setup or integration with development tools.
                
                ### Step 5: Iteration and Feedback
                - The user reviews the generated project scaffold and provides feedback.
                - If adjustments are needed, the user can return to any of the previous steps to refine the requirements or select a different template.
                - The system iterates through the process until the user is satisfied with the generated project.
                
                ## Optional UI Elements
                
                To enhance user experience, the system may include a web-based UI that allows users to:
                - Input requirements through forms and selections rather than text-based conversation.
                - Browse and select project templates visually.
                - Receive and execute the generated script directly within the UI.
                
                ## Conclusion
                
                The Software Project Generator system leverages specialized GPT actors to streamline the process of setting up new software projects. By guiding the user through requirement collection, template selection, and code generation, the system simplifies the project initiation phase and allows for quick and customized project scaffolding.
                """.trimIndent().trim(),
                "Implement `fun requirementCollectorActor`",
            ),
            expectations = listOf(
            )
        )
    )
}