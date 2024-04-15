package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization.Companion.toChatMessage
import com.simiacryptus.skyenet.core.actors.opt.Expectation
import com.simiacryptus.skyenet.core.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationSocketManager
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class MetaAgentActorTests {
    @Nested
    object ParsedActorImplementationActorTest : CodingActorTestBase() {

        @Test
        override fun testRun() = super.testRun()
        override val interpreterClass get() = KotlinInterpreter::class
        override val actor = MetaAgentActors().parsedActorDesigner()
        override val testCases = listOf(
            ActorOptimization.TestCase(
                userMessages = listOf(
                    "Create a comic book generator",
                    "To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to consider the various stages of comic book creation, such as concept development, scripting, storyboarding, illustration, and dialogue writing. Here's how the system could be structured using different types of actors:\n\n### Actors\n\n#### 1. Concept Actor (Parsed Actor)\n- **Purpose**: To generate a high-level concept or theme for the comic book based on user input.\n- **Input**: User prompt describing desired theme or genre.\n- **Output**: A typed object containing a structured concept, including genre, setting, and basic plot elements.\n- **Logic**: The actor takes the user prompt, generates a natural-language response with a concept, and then parses this into a structured format.\n\n#### 2. Plot Actor (Parsed Actor)\n- **Purpose**: To create a detailed plot outline that follows the high-level concept.\n- **Input**: Typed object from the Concept Actor.\n- **Output**: A typed object containing a detailed plot outline with story arcs and key events.\n- **Logic**: This actor develops a more granular plot structure, turning the high-level concept into a step-by-step outline.\n\n#### 3. Character Actor (Parsed Actor)\n- **Purpose**: To design characters that fit into the comic book's plot and setting.\n- **Input**: Typed object from the Plot Actor.\n- **Output**: A typed object with character profiles, including names, roles, appearances, and personalities.\n- **Logic**: Based on the plot outline, this actor creates characters, ensuring they are relevant to the story and contribute to the plot.\n\n#### 4. Script Actor (Simple Actor)\n- **Purpose**: To write the dialogue and captions for the comic book.\n- **Input**: Typed objects from the Plot Actor and Character Actor.\n- **Output**: A script with dialogue, captions, and panel descriptions.\n- **Logic**: This actor crafts the text for the comic book, including character dialogue and narrative captions, based on the plot and characters.\n\n#### 5. Storyboard Actor (Image Actor)\n- **Purpose**: To create a visual storyboard that outlines each panel of the comic book.\n- **Input**: Script from the Script Actor.\n- **Output**: A series of images representing the storyboard.\n- **Logic**: The actor interprets the script and generates a sequence of images that represent the storyboard for the comic book.\n\n#### 6. Illustration Actor (Image Actor)\n- **Purpose**: To generate the final illustrations for each panel of the comic book.\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels with illustrations.\n- **Logic**: This actor takes the storyboard images and refines them into detailed illustrations suitable for the final comic book.\n\n### Logical Flow\n\n#### Step 1: Concept Development\n- **Input**: User prompt with desired theme or genre.\n- **Output**: High-level concept for the comic book.\n- **Actors Used**: Concept Actor.\n\n#### Step 2: Plot Outlining\n- **Input**: High-level concept from the Concept Actor.\n- **Output**: Detailed plot outline.\n- **Actors Used**: Plot Actor.\n\n#### Step 3: Character Design\n- **Input**: Detailed plot outline from the Plot Actor.\n- **Output**: Character profiles.\n- **Actors Used**: Character Actor.\n\n#### Step 4: Script Writing\n- **Input**: Detailed plot outline and character profiles.\n- **Output**: Script with dialogue and captions.\n- **Actors Used**: Script Actor.\n\n#### Step 5: Storyboarding\n- **Input**: Script from the Script Actor.\n- **Output**: Visual storyboard.\n- **Actors Used**: Storyboard Actor.\n\n#### Step 6: Illustration\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels.\n- **Actors Used**: Illustration Actor.\n\nEach actor in the system would be designed to handle a specific part of the comic book creation process, with the output of one actor serving as the input for the next. The actors would work in a sequential manner, with the possibility of feedback loops for revisions. For example, the Script Actor might send the script back to the Character Actor for adjustments if new dialogue ideas suggest character changes. The system would be iterative, allowing for refinement at each stage to ensure a cohesive and polished final product.".trimIndent(),
                    "Implement `fun conceptActor() : ParsedActor`",
                ).map { it.toChatMessage() },
                expectations = listOf(
                )
            )
        )
    }

    @Nested
    object SimpleActorImplementationActorTest : CodingActorTestBase() {

        @Test
        override fun testRun() = super.testRun()

        override val actor = MetaAgentActors().simpleActorDesigner()
        override val interpreterClass = KotlinInterpreter::class

        @Language("Markdown")
        override val testCases = listOf(
            ActorOptimization.TestCase(
                userMessages = listOf(
                    "Create a comic book generator",
                    "To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to consider the various stages of comic book creation, such as concept development, scripting, storyboarding, illustration, and dialogue writing. Here's how the system could be structured using different types of actors:\n\n### Actors\n\n#### 1. Concept Actor (Parsed Actor)\n- **Purpose**: To generate a high-level concept or theme for the comic book based on user input.\n- **Input**: User prompt describing desired theme or genre.\n- **Output**: A typed object containing a structured concept, including genre, setting, and basic plot elements.\n- **Logic**: The actor takes the user prompt, generates a natural-language response with a concept, and then parses this into a structured format.\n\n#### 2. Plot Actor (Parsed Actor)\n- **Purpose**: To create a detailed plot outline that follows the high-level concept.\n- **Input**: Typed object from the Concept Actor.\n- **Output**: A typed object containing a detailed plot outline with story arcs and key events.\n- **Logic**: This actor develops a more granular plot structure, turning the high-level concept into a step-by-step outline.\n\n#### 3. Character Actor (Parsed Actor)\n- **Purpose**: To design characters that fit into the comic book's plot and setting.\n- **Input**: Typed object from the Plot Actor.\n- **Output**: A typed object with character profiles, including names, roles, appearances, and personalities.\n- **Logic**: Based on the plot outline, this actor creates characters, ensuring they are relevant to the story and contribute to the plot.\n\n#### 4. Script Actor (Simple Actor)\n- **Purpose**: To write the dialogue and captions for the comic book.\n- **Input**: Typed objects from the Plot Actor and Character Actor.\n- **Output**: A script with dialogue, captions, and panel descriptions.\n- **Logic**: This actor crafts the text for the comic book, including character dialogue and narrative captions, based on the plot and characters.\n\n#### 5. Storyboard Actor (Image Actor)\n- **Purpose**: To create a visual storyboard that outlines each panel of the comic book.\n- **Input**: Script from the Script Actor.\n- **Output**: A series of images representing the storyboard.\n- **Logic**: The actor interprets the script and generates a sequence of images that represent the storyboard for the comic book.\n\n#### 6. Illustration Actor (Image Actor)\n- **Purpose**: To generate the final illustrations for each panel of the comic book.\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels with illustrations.\n- **Logic**: This actor takes the storyboard images and refines them into detailed illustrations suitable for the final comic book.\n\n### Logical Flow\n\n#### Step 1: Concept Development\n- **Input**: User prompt with desired theme or genre.\n- **Output**: High-level concept for the comic book.\n- **Actors Used**: Concept Actor.\n\n#### Step 2: Plot Outlining\n- **Input**: High-level concept from the Concept Actor.\n- **Output**: Detailed plot outline.\n- **Actors Used**: Plot Actor.\n\n#### Step 3: Character Design\n- **Input**: Detailed plot outline from the Plot Actor.\n- **Output**: Character profiles.\n- **Actors Used**: Character Actor.\n\n#### Step 4: Script Writing\n- **Input**: Detailed plot outline and character profiles.\n- **Output**: Script with dialogue and captions.\n- **Actors Used**: Script Actor.\n\n#### Step 5: Storyboarding\n- **Input**: Script from the Script Actor.\n- **Output**: Visual storyboard.\n- **Actors Used**: Storyboard Actor.\n\n#### Step 6: Illustration\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels.\n- **Actors Used**: Illustration Actor.\n\nEach actor in the system would be designed to handle a specific part of the comic book creation process, with the output of one actor serving as the input for the next. The actors would work in a sequential manner, with the possibility of feedback loops for revisions. For example, the Script Actor might send the script back to the Character Actor for adjustments if new dialogue ideas suggest character changes. The system would be iterative, allowing for refinement at each stage to ensure a cohesive and polished final product.".trimIndent()
                        .trim(),
                    "Implement `fun scriptActor() : SimpleActor`",
                ).map { it.toChatMessage() },
                expectations = listOf(
                )
            )
        )
    }

    @Nested
    open class CodingActorImplementationActorTest : CodingActorTestBase() {

        @Test
        override fun testRun() = super.testRun()

        //    @Test override fun testOptimize() = super.testOptimize()
        override val actor = MetaAgentActors().codingActorDesigner()
        override val interpreterClass get() = KotlinInterpreter::class
        override val testCases = listOf(
            ActorOptimization.TestCase(
                userMessages = listOf(
                    "Design a software project designer",
                    "# Software Project Designer System Design Document\n\n## Overview\n\nThe Software Project Designer is a sophisticated system that leverages GPT-based \"actors\" to assist users in designing and planning software projects. The system is composed of various actors, each with a specific role in processing user input, generating project design elements, and compiling these into a coherent project plan.\n\n## Actors\n\n### 1. Requirement Gathering Actor (Simple Actor)\n**Purpose**: To interact with the user to gather initial project requirements and expectations.\n**Usage**: The user inputs their project ideas, goals, and constraints. The actor processes this information and responds with clarifying questions or confirms understanding of the requirements.\n\n### 2. Design Proposal Actor (Parsed Actor)\n**Purpose**: To create an initial design proposal based on the requirements gathered by the Requirement Gathering Actor.\n**Usage**: This actor takes the confirmed requirements and generates a high-level design proposal. The response is then parsed into a Kotlin data class representing the project's proposed architecture, technology stack, and design patterns.\n\n### 3. Task Breakdown Actor (Script Actor)\n**Purpose**: To break down the design proposal into actionable tasks and generate a project plan with estimates.\n**Usage**: Using environment definitions and a scripting system, this actor translates the design proposal into a detailed project plan with tasks, milestones, and timelines. The script can be executed to update or refine the project plan as needed.\n\n## Logical Flow\n\n### Step 1: Requirement Gathering\n- The user interacts with the Requirement Gathering Actor by submitting their project idea and requirements.\n- The actor asks follow-up questions to ensure all necessary details are captured.\n- The user's responses are processed, and a summary of requirements is generated.\n\n### Step 2: Design Proposal Generation\n- The summarized requirements are passed to the Design Proposal Actor.\n- The actor generates a text response outlining a high-level design proposal.\n- A second pass uses GPT3.5_Turbo to parse the text into a predefined Kotlin data class, which includes structured information about the proposed design.\n\n### Step 3: Task Breakdown and Project Planning\n- The structured design proposal is passed to the Task Breakdown Actor.\n- The actor uses predefined symbols/functions and a pluggable script compilation system to generate a detailed project plan.\n- The generated script includes tasks, dependencies, milestones, and estimates. It provides an \"execute\" method for project managers to simulate and adjust the plan.\n\n### Step 4: Iteration and Refinement\n- The project plan can be iterated upon by revisiting any of the previous actors.\n- The Requirement Gathering Actor can update requirements based on new insights.\n- The Design Proposal Actor can adjust the design based on updated requirements or constraints.\n- The Task Breakdown Actor can refine the project plan to accommodate changes in the design or project scope.\n\n### Step 5: Finalization and Output\n- Once the project plan is finalized, the system outputs a comprehensive document.\n- This document includes the project requirements, design proposal, and detailed project plan with tasks and timelines.\n- Optional UI elements can be provided for users to interact with the project plan, make adjustments, and track progress.\n\n## Additional Considerations\n\n- The system should include error handling and validation at each stage to ensure the integrity of the information being processed.\n- The actors should be designed to handle specific scopes of work to maintain efficiency and prevent cognitive overload.\n- The system should be modular to allow for easy updates and maintenance of individual actors without affecting the entire system.\n- Integration with existing project management tools and platforms should be considered to enhance usability and adoption.\n\n## Conclusion\n\nThe Software Project Designer system is designed to streamline the process of software project planning. By utilizing specialized GPT-based actors, the system can efficiently transform user inputs into a structured and actionable project plan. This document outlines the initial design and logical flow of the system, which can be further refined and expanded upon to meet specific user needs and industry standards.",
                    "Implement DesignProposalActor",
                ).map { it.toChatMessage() },
                expectations = listOf(
                    Expectation.VectorMatch("Great, what kind of book are you looking for?")
                )
            )
        )

    }

    @Nested
    object ImageActorImplementationActorTest : CodingActorTestBase() {

        @Test
        override fun testRun() = super.testRun()

        override val actor = MetaAgentActors().imageActorDesigner()
        override val interpreterClass = KotlinInterpreter::class

        @Language("Markdown")
        override val testCases = listOf(
            ActorOptimization.TestCase(
                userMessages = listOf(
                    "Create a comic book generator",
                    "To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to consider the various stages of comic book creation, such as concept development, scripting, storyboarding, illustration, and dialogue writing. Here's how the system could be structured using different types of actors:\n\n### Actors\n\n#### 1. Concept Actor (Parsed Actor)\n- **Purpose**: To generate a high-level concept or theme for the comic book based on user input.\n- **Input**: User prompt describing desired theme or genre.\n- **Output**: A typed object containing a structured concept, including genre, setting, and basic plot elements.\n- **Logic**: The actor takes the user prompt, generates a natural-language response with a concept, and then parses this into a structured format.\n\n#### 2. Plot Actor (Parsed Actor)\n- **Purpose**: To create a detailed plot outline that follows the high-level concept.\n- **Input**: Typed object from the Concept Actor.\n- **Output**: A typed object containing a detailed plot outline with story arcs and key events.\n- **Logic**: This actor develops a more granular plot structure, turning the high-level concept into a step-by-step outline.\n\n#### 3. Character Actor (Parsed Actor)\n- **Purpose**: To design characters that fit into the comic book's plot and setting.\n- **Input**: Typed object from the Plot Actor.\n- **Output**: A typed object with character profiles, including names, roles, appearances, and personalities.\n- **Logic**: Based on the plot outline, this actor creates characters, ensuring they are relevant to the story and contribute to the plot.\n\n#### 4. Script Actor (Simple Actor)\n- **Purpose**: To write the dialogue and captions for the comic book.\n- **Input**: Typed objects from the Plot Actor and Character Actor.\n- **Output**: A script with dialogue, captions, and panel descriptions.\n- **Logic**: This actor crafts the text for the comic book, including character dialogue and narrative captions, based on the plot and characters.\n\n#### 5. Storyboard Actor (Image Actor)\n- **Purpose**: To create a visual storyboard that outlines each panel of the comic book.\n- **Input**: Script from the Script Actor.\n- **Output**: A series of images representing the storyboard.\n- **Logic**: The actor interprets the script and generates a sequence of images that represent the storyboard for the comic book.\n\n#### 6. Illustration Actor (Image Actor)\n- **Purpose**: To generate the final illustrations for each panel of the comic book.\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels with illustrations.\n- **Logic**: This actor takes the storyboard images and refines them into detailed illustrations suitable for the final comic book.\n\n### Logical Flow\n\n#### Step 1: Concept Development\n- **Input**: User prompt with desired theme or genre.\n- **Output**: High-level concept for the comic book.\n- **Actors Used**: Concept Actor.\n\n#### Step 2: Plot Outlining\n- **Input**: High-level concept from the Concept Actor.\n- **Output**: Detailed plot outline.\n- **Actors Used**: Plot Actor.\n\n#### Step 3: Character Design\n- **Input**: Detailed plot outline from the Plot Actor.\n- **Output**: Character profiles.\n- **Actors Used**: Character Actor.\n\n#### Step 4: Script Writing\n- **Input**: Detailed plot outline and character profiles.\n- **Output**: Script with dialogue and captions.\n- **Actors Used**: Script Actor.\n\n#### Step 5: Storyboarding\n- **Input**: Script from the Script Actor.\n- **Output**: Visual storyboard.\n- **Actors Used**: Storyboard Actor.\n\n#### Step 6: Illustration\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels.\n- **Actors Used**: Illustration Actor.\n\nEach actor in the system would be designed to handle a specific part of the comic book creation process, with the output of one actor serving as the input for the next. The actors would work in a sequential manner, with the possibility of feedback loops for revisions. For example, the Script Actor might send the script back to the Character Actor for adjustments if new dialogue ideas suggest character changes. The system would be iterative, allowing for refinement at each stage to ensure a cohesive and polished final product.".trim(),
                    "Implement `fun storyboardActor() : ImageActor`",
                ).map { it.toChatMessage() },
                expectations = listOf(
                )
            )
        )
    }

    @Nested
    object FlowStepDesignerActorTest : CodingActorTestBase() {

        @Test
        override fun testRun() = super.testRun()
        override val interpreterClass get() = KotlinInterpreter::class
        override val actor = MetaAgentActors(symbols = symbols()).flowStepDesigner()

        private fun symbols(): Map<String, Any> {
            val user = User("user@test")
            val session = StorageInterface.newGlobalID()
            val dataStorage = DataStorage(File("."))
            val socketManager = object : ApplicationSocketManager(session, user, dataStorage, this::class.java) {
                override fun userMessage(
                    session: Session,
                    user: User?,
                    userMessage: String,
                    socketManager: ApplicationSocketManager,
                    api: API
                ) {
                    throw UnsupportedOperationException()
                }

            }
            val ui = ApplicationInterface(socketManager)
            return mapOf(
                "user" to user,
                "session" to session,
                "dataStorage" to dataStorage,
                "ui" to ui,
                "api" to api,
            )
        }

        private val codePrefix =
            "\nimport com.simiacryptus.skyenet.core.actors.SimpleActor\nimport com.simiacryptus.jopenai.models.ChatModels\nimport com.simiacryptus.jopenai.models.OpenAITextModel\n\nfun conceptActor(): SimpleActor {\n    return SimpleActor(\n        prompt = \"\"\"\n        You are a creative assistant specialized in generating comic book concepts. \n        When given a theme or genre, you provide a structured concept that includes the genre, themes, and a basic plot outline.\n        \"\"\".trimIndent(),\n        name = \"ConceptActor\",\n        model = ChatModels.GPT35Turbo,\n        temperature = 0.3\n    )\n}\n".trimIndent()

        override fun answer(messages: Array<ApiModel.ChatMessage>): CodingActor.CodeResult {
            return actor.respond(
                messages = messages,
                input = CodingActor.CodeRequest(
                    messages = messages.map { (it.content?.first()?.text ?: "") to ApiModel.Role.user },
                    codePrefix = codePrefix,
                ),
                api = api
            )
        }

        override val testCases = listOf(
            ActorOptimization.TestCase(
                userMessages = listOf(
                    "Create a comic book generator".toChatMessage(),
                    "To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to define the actors and the logical flow of their interactions. The creative process for a comic book typically involves conceptualization, scripting, storyboarding, illustration, and finalization.\n\n### Actors\n\n#### 1. Concept Actor (Parsed Actor)\n- **Purpose**: To generate a high-level concept for the comic book based on user input.\n- **Input**: User prompt describing the desired theme or genre of the comic book.\n- **Output**: A typed object containing a structured concept, including genre, themes, and a basic plot outline.\n- **Logic**: The actor uses the user prompt to generate a natural language response, which is then parsed into a structured concept object.\n\n#### 2. Script Actor (Parsed Actor)\n- **Purpose**: To create a detailed script for the comic book, including dialogue and scene descriptions.\n- **Input**: Typed object from the Concept Actor.\n- **Output**: A typed object containing a detailed script with dialogue, scene descriptions, and character actions.\n- **Logic**: The actor takes the concept and outlines a script in natural language, which is then parsed into a structured script object.\n\n#### 3. Storyboard Actor (Image Actor)\n- **Purpose**: To translate the script into a visual storyboard.\n- **Input**: Typed object from the Script Actor.\n- **Output**: A series of images representing the storyboard of the comic book.\n- **Logic**: The actor converts script elements into image prompts and uses these to generate a sequence of storyboard images.\n\n#### 4. Illustration Actor (Image Actor)\n- **Purpose**: To create detailed illustrations for each panel of the comic book.\n- **Input**: Storyboard images and typed script object.\n- **Output**: A series of high-resolution images for each panel.\n- **Logic**: The actor refines storyboard images into detailed panel illustrations, guided by the script for accuracy and consistency.\n\n#### 5. Layout Actor (Coding Actor)\n- **Purpose**: To arrange the illustrated panels into comic book pages with appropriate layout and design.\n- **Input**: High-resolution panel images and script details.\n- **Output**: Complete comic book pages with panels, dialogue, and captions in place.\n- **Logic**: The actor generates code to layout the images and text on a page, which is then compiled and validated to ensure it meets design standards.\n\n#### 6. Finalization Actor (Simple Actor)\n- **Purpose**: To apply finishing touches such as color correction, lettering, and effects.\n- **Input**: Complete comic book pages.\n- **Output**: Finalized comic book ready for distribution.\n- **Logic**: The actor takes user preferences for final touches and applies them to the pages, returning the finished product.\n\n### Logical Flow\n\n#### Step 1: Conceptualization\n- **Input**: User prompt with desired theme or genre.\n- **Output**: Concept object with genre, themes, and plot outline.\n- **Actors Used**: Concept Actor.\n\n#### Step 2: Scripting\n- **Input**: Concept object from Step 1.\n- **Output**: Script object with detailed dialogue and scene descriptions.\n- **Actors Used**: Script Actor.\n\n#### Step 3: Storyboarding\n- **Input**: Script object from Step 2.\n- **Output**: Storyboard images.\n- **Actors Used**: Storyboard Actor.\n\n#### Step 4: Illustration\n- **Input**: Storyboard images and script object from previous steps.\n- **Output**: High-resolution panel images.\n- **Actors Used**: Illustration Actor.\n\n#### Step 5: Layout\n- **Input**: High-resolution panel images and script details.\n- **Output**: Comic book pages with panels and text.\n- **Actors Used**: Layout Actor.\n\n#### Step 6: Finalization\n- **Input**: Comic book pages from Step 5.\n- **Output**: Finalized comic book.\n- **Actors Used**: Finalization Actor.\n\nEach step in the process feeds into the next, with the output of one actor becoming the input for another. This ensures a coherent and sequential development of the comic book, with each actor contributing to a specific aspect of the creative process. The system can be designed to allow for user feedback and iterations at various stages to refine the comic book to the user's satisfaction.".trim()
                        .toChatMessage(),
                    codePrefix
                        .toChatMessage(role = ApiModel.Role.assistant),
                    "Implement `fun conceptualization(User prompt with desired theme or genre : String)`".toChatMessage()
                ),
                expectations = listOf(
                )
            )
        )

    }

}
