package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.actors.opt.Expectation
import com.simiacryptus.skyenet.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import org.junit.jupiter.api.Test

object FlowStepDesignerActorTest : CodingActorTestBase() {

    @Test
    override fun testRun() = super.testRun()
//    @Test
    override fun testOptimize() = super.testOptimize()
    override val interpreterClass get() = KotlinInterpreter::class
    override val actor = MetaActors().flowStepDesigner()
    override val testCases = listOf(
        ActorOptimization.TestCase(
            userMessages = listOf(
                "Design a software project designer",
                """# Software Project Designer System Design Document
                
                ## Introduction
                
                The Software Project Designer is a system designed to assist users in creating a structured plan for software development projects. It leverages GPT actors to interactively guide the user through the process of defining project requirements, architecture, tasks, and milestones. The system is composed of various specialized actors that collaborate to generate a comprehensive project design document.
                
                ## System Overview
                
                The system is structured around a community of GPT actors, each with a specific role in the project design process. These actors are:
                
                1. **Requirements Actor**: Captures and organizes user requirements.
                2. **Architecture Actor**: Suggests software architecture based on requirements.
                3. **Task Breakdown Actor**: Breaks down architecture into actionable tasks.
                4. **Milestone Planner Actor**: Organizes tasks into milestones and timelines.
                5. **Documentation Actor**: Compiles all information into a project design document.
                
                ## Actors Description
                
                ### 1. Requirements Actor (Parsed Actor)
                
                - **Purpose**: To interact with the user to gather and categorize software project requirements.
                - **Usage**: The actor prompts the user for requirements, which are then parsed into a structured format suitable for further processing by other actors.
                
                ### 2. Architecture Actor (Script Actor)
                
                - **Purpose**: To propose a software architecture that aligns with the captured requirements.
                - **Usage**: Based on the input from the Requirements Actor, this actor uses predefined symbols and functions to generate an architectural blueprint script, which can be executed to visualize the proposed architecture.
                
                ### 3. Task Breakdown Actor (Parsed Actor)
                
                - **Purpose**: To decompose the proposed architecture into a set of actionable development tasks.
                - **Usage**: This actor parses the architecture script into a list of tasks, categorizing them by development phase, technology stack, and estimated effort.
                
                ### 4. Milestone Planner Actor (Script Actor)
                
                - **Purpose**: To organize tasks into milestones and create a project timeline.
                - **Usage**: The actor takes the task list and applies project management principles to sequence tasks, define milestones, and estimate timelines using a script that can be executed to produce a Gantt chart or similar visual timeline.
                
                ### 5. Documentation Actor (Simple Actor)
                
                - **Purpose**: To compile all the information generated by the previous actors into a cohesive and comprehensive project design document.
                - **Usage**: The actor processes the outputs from all other actors and formats them into a document, complete with an introduction, table of contents, sections for requirements, architecture, task breakdown, milestones, and appendices if needed.
                
                ## Logical Flow of the System
                
                1. **User Interaction**: The user initiates the process by interacting with the Requirements Actor to input project requirements.
                2. **Requirements Parsing**: The Requirements Actor processes the user input and parses it into a structured format.
                3. **Architecture Generation**: The parsed requirements are passed to the Architecture Actor, which generates a software architecture proposal script.
                4. **Task Breakdown**: The Architecture Actor's output is fed into the Task Breakdown Actor, which creates a detailed list of development tasks.
                5. **Milestone Planning**: The list of tasks is provided to the Milestone Planner Actor, which organizes them into milestones and timelines.
                6. **Documentation Compilation**: All the generated data from the previous steps is compiled by the Documentation Actor into a final project design document.
                7. **Review and Iteration**: The user reviews the document and may interact with any of the actors again to refine the project design.
                8. **Finalization**: Once the user is satisfied, the final project design document is generated and can be exported or shared.
                
                ## Conclusion
                
                The Software Project Designer system is an innovative approach to streamline the project planning process. By leveraging specialized GPT actors, the system facilitates an interactive and iterative design experience, resulting in a well-structured and detailed project plan. This document serves as a blueprint for the development of the Software Project Designer system.
                """.trimIndent(),

                // This should use the assistant message role
                """
                import com.simiacryptus.openai.OpenAIClient
                import com.simiacryptus.skyenet.actors.SimpleActor
                
                class RequirementsActor(api: OpenAIClient) : SimpleActor(
                    prompt = "Please provide the requirements for your software project:",
                    api = api
                ) {
                    override fun processResponse(response: String): String {
                        // Parse the user's response and categorize the requirements
                        val parsedRequirements = parseRequirements(response)
                
                        // Convert the parsed requirements into a formatted string
                        val formattedRequirements = formatRequirements(parsedRequirements)
                
                        // Return the formatted requirements as the actor's response
                        return formattedRequirements
                    }
                
                    private fun parseRequirements(response: String): List<String> {
                        // Implement your logic to parse the user's response and extract the requirements
                        // You can use regular expressions or any other parsing technique
                
                        // For example, you can split the response by newlines to get individual requirements
                        return response.split("\n")
                    }
                
                    private fun formatRequirements(requirements: List<String>): String {
                        // Implement your logic to format the parsed requirements into a readable format
                        // You can use string concatenation or any other formatting technique
                
                        // For example, you can join the requirements with bullet points
                        return requirements.joinToString(prefix = "- ", separator = "\n- ")
                    }
                }
                
                val api = OpenAIClient()
                val requirementsActor = RequirementsActor(api)
              
                """.trimIndent(),

                // Taken from parsed task list
                "Implement User Interaction"
            )
            ,
            expectations = listOf(
                Expectation.ContainsMatch("""search\(.*?\)""".toRegex(), critical = false),
                Expectation.VectorMatch("Great, what kind of book are you looking for?")
            )
        )
    )
}