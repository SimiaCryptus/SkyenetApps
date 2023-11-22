package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.core.actors.opt.Expectation
import com.simiacryptus.skyenet.core.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.junit.jupiter.api.Test

object ParsedActorImplementationActorTest : CodingActorTestBase() {

    @Test
    override fun testRun() = super.testRun()
    override val interpreterClass get() = KotlinInterpreter::class
    override val actor = MetaActors().parsedActorDesigner()
    override val testCases = listOf(
        ActorOptimization.TestCase(
            userMessages = listOf(
                "Design a software project designer",
                """# Software Project Designer System Design Document
                
                ## Overview
                
                The Software Project Designer is a sophisticated system that leverages GPT-based "actors" to assist users in designing and planning software projects. The system is composed of various actors, each with a specific role in processing user input, generating project design elements, and compiling these into a coherent project plan.
                
                ## Actors
                
                ### 1. Requirement Gathering Actor (Simple Actor)
                **Purpose**: To interact with the user to gather initial project requirements and expectations.
                **Usage**: The user inputs their project ideas, goals, and constraints. The actor processes this information and responds with clarifying questions or confirms understanding of the requirements.
                
                ### 2. Design Proposal Actor (Parsed Actor)
                **Purpose**: To create an initial design proposal based on the requirements gathered by the Requirement Gathering Actor.
                **Usage**: This actor takes the confirmed requirements and generates a high-level design proposal. The response is then parsed into a Kotlin data class representing the project's proposed architecture, technology stack, and design patterns.
                
                ### 3. Task Breakdown Actor (Script Actor)
                **Purpose**: To break down the design proposal into actionable tasks and generate a project plan with estimates.
                **Usage**: Using environment definitions and a scripting system, this actor translates the design proposal into a detailed project plan with tasks, milestones, and timelines. The script can be executed to update or refine the project plan as needed.
                
                ## Logical Flow
                
                ### Step 1: Requirement Gathering
                - The user interacts with the Requirement Gathering Actor by submitting their project idea and requirements.
                - The actor asks follow-up questions to ensure all necessary details are captured.
                - The user's responses are processed, and a summary of requirements is generated.
                
                ### Step 2: Design Proposal Generation
                - The summarized requirements are passed to the Design Proposal Actor.
                - The actor generates a text response outlining a high-level design proposal.
                - A second pass uses GPT3.5_Turbo to parse the text into a predefined Kotlin data class, which includes structured information about the proposed design.
                
                ### Step 3: Task Breakdown and Project Planning
                - The structured design proposal is passed to the Task Breakdown Actor.
                - The actor uses predefined symbols/functions and a pluggable script compilation system to generate a detailed project plan.
                - The generated script includes tasks, dependencies, milestones, and estimates. It provides an "execute" method for project managers to simulate and adjust the plan.
                
                ### Step 4: Iteration and Refinement
                - The project plan can be iterated upon by revisiting any of the previous actors.
                - The Requirement Gathering Actor can update requirements based on new insights.
                - The Design Proposal Actor can adjust the design based on updated requirements or constraints.
                - The Task Breakdown Actor can refine the project plan to accommodate changes in the design or project scope.
                
                ### Step 5: Finalization and Output
                - Once the project plan is finalized, the system outputs a comprehensive document.
                - This document includes the project requirements, design proposal, and detailed project plan with tasks and timelines.
                - Optional UI elements can be provided for users to interact with the project plan, make adjustments, and track progress.
                
                ## Additional Considerations
                
                - The system should include error handling and validation at each stage to ensure the integrity of the information being processed.
                - The actors should be designed to handle specific scopes of work to maintain efficiency and prevent cognitive overload.
                - The system should be modular to allow for easy updates and maintenance of individual actors without affecting the entire system.
                - Integration with existing project management tools and platforms should be considered to enhance usability and adoption.
                
                ## Conclusion
                
                The Software Project Designer system is designed to streamline the process of software project planning. By utilizing specialized GPT-based actors, the system can efficiently transform user inputs into a structured and actionable project plan. This document outlines the initial design and logical flow of the system, which can be further refined and expanded upon to meet specific user needs and industry standards.
                """.trimIndent(),
                "Implement DesignProposalActor",
            ),
            expectations = listOf(
                Expectation.VectorMatch("Great, what kind of book are you looking for?")
            )
        )
    )
}