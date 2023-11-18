package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.actors.opt.Expectation
import com.simiacryptus.skyenet.apps.meta.MetaActors.Companion.parsedActorDesigner
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.system.exitProcess

object ParsedActorImplementationActorOptTest {

    private val log = LoggerFactory.getLogger(ParsedActorImplementationActorOptTest::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            ActorOptimization(
                OpenAIClient(
                    logLevel = Level.DEBUG
                )
            ).runGeneticGenerations(
                populationSize = 1,
                generations = 1,
                selectionSize = 1,
                actorFactory = { CodingActor(
                    interpreterClass = KotlinInterpreter::class,
                    details = it,
                ) },
                resultMapper = { it.getCode() },
                prompts = listOf(
                    parsedActorDesigner().details!!,
                ),
                testCases = listOf(
                    ActorOptimization.TestCase(
                        userMessages = listOf(
                            "Design a software project designer",
                            "# Software Project Designer System Design Document\n\n## Overview\n\nThe Software Project Designer is a sophisticated system that leverages GPT-based \"actors\" to assist users in designing and planning software projects. The system is composed of various actors, each with a specific role in processing user input, generating project design elements, and compiling these into a coherent project plan.\n\n## Actors\n\n### 1. Requirement Gathering Actor (Simple Actor)\n**Purpose**: To interact with the user to gather initial project requirements and expectations.\n**Usage**: The user inputs their project ideas, goals, and constraints. The actor processes this information and responds with clarifying questions or confirms understanding of the requirements.\n\n### 2. Design Proposal Actor (Parsed Actor)\n**Purpose**: To create an initial design proposal based on the requirements gathered by the Requirement Gathering Actor.\n**Usage**: This actor takes the confirmed requirements and generates a high-level design proposal. The response is then parsed into a Kotlin data class representing the project's proposed architecture, technology stack, and design patterns.\n\n### 3. Task Breakdown Actor (Script Actor)\n**Purpose**: To break down the design proposal into actionable tasks and generate a project plan with estimates.\n**Usage**: Using environment definitions and a scripting system, this actor translates the design proposal into a detailed project plan with tasks, milestones, and timelines. The script can be executed to update or refine the project plan as needed.\n\n## Logical Flow\n\n### Step 1: Requirement Gathering\n- The user interacts with the Requirement Gathering Actor by submitting their project idea and requirements.\n- The actor asks follow-up questions to ensure all necessary details are captured.\n- The user's responses are processed, and a summary of requirements is generated.\n\n### Step 2: Design Proposal Generation\n- The summarized requirements are passed to the Design Proposal Actor.\n- The actor generates a text response outlining a high-level design proposal.\n- A second pass uses GPT3.5_Turbo to parse the text into a predefined Kotlin data class, which includes structured information about the proposed design.\n\n### Step 3: Task Breakdown and Project Planning\n- The structured design proposal is passed to the Task Breakdown Actor.\n- The actor uses predefined symbols/functions and a pluggable script compilation system to generate a detailed project plan.\n- The generated script includes tasks, dependencies, milestones, and estimates. It provides an \"execute\" method for project managers to simulate and adjust the plan.\n\n### Step 4: Iteration and Refinement\n- The project plan can be iterated upon by revisiting any of the previous actors.\n- The Requirement Gathering Actor can update requirements based on new insights.\n- The Design Proposal Actor can adjust the design based on updated requirements or constraints.\n- The Task Breakdown Actor can refine the project plan to accommodate changes in the design or project scope.\n\n### Step 5: Finalization and Output\n- Once the project plan is finalized, the system outputs a comprehensive document.\n- This document includes the project requirements, design proposal, and detailed project plan with tasks and timelines.\n- Optional UI elements can be provided for users to interact with the project plan, make adjustments, and track progress.\n\n## Additional Considerations\n\n- The system should include error handling and validation at each stage to ensure the integrity of the information being processed.\n- The actors should be designed to handle specific scopes of work to maintain efficiency and prevent cognitive overload.\n- The system should be modular to allow for easy updates and maintenance of individual actors without affecting the entire system.\n- Integration with existing project management tools and platforms should be considered to enhance usability and adoption.\n\n## Conclusion\n\nThe Software Project Designer system is designed to streamline the process of software project planning. By utilizing specialized GPT-based actors, the system can efficiently transform user inputs into a structured and actionable project plan. This document outlines the initial design and logical flow of the system, which can be further refined and expanded upon to meet specific user needs and industry standards.",
                            "Implement DesignProposalActor",
                        ),
                        expectations = listOf(
                            Expectation.VectorMatch("Great, what kind of book are you looking for?")
                        )
                    )
                ),
            )
        } catch (e: Throwable) {
            log.error("Error", e)
        } finally {
            exitProcess(0)
        }
    }

}