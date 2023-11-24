package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization.Companion.toChatMessage
import com.simiacryptus.skyenet.core.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

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
                """
                To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to consider the various stages of comic book creation, such as concept development, scripting, storyboarding, illustration, and dialogue writing. Here's how the system could be structured using different types of actors:
                
                ### Actors
                
                #### 1. Concept Actor (Parsed Actor)
                - **Purpose**: To generate a high-level concept or theme for the comic book based on user input.
                - **Input**: User prompt describing desired theme or genre.
                - **Output**: A typed object containing a structured concept, including genre, setting, and basic plot elements.
                - **Logic**: The actor takes the user prompt, generates a natural-language response with a concept, and then parses this into a structured format.
                
                #### 2. Plot Actor (Parsed Actor)
                - **Purpose**: To create a detailed plot outline that follows the high-level concept.
                - **Input**: Typed object from the Concept Actor.
                - **Output**: A typed object containing a detailed plot outline with story arcs and key events.
                - **Logic**: This actor develops a more granular plot structure, turning the high-level concept into a step-by-step outline.
                
                #### 3. Character Actor (Parsed Actor)
                - **Purpose**: To design characters that fit into the comic book's plot and setting.
                - **Input**: Typed object from the Plot Actor.
                - **Output**: A typed object with character profiles, including names, roles, appearances, and personalities.
                - **Logic**: Based on the plot outline, this actor creates characters, ensuring they are relevant to the story and contribute to the plot.
                
                #### 4. Script Actor (Simple Actor)
                - **Purpose**: To write the dialogue and captions for the comic book.
                - **Input**: Typed objects from the Plot Actor and Character Actor.
                - **Output**: A script with dialogue, captions, and panel descriptions.
                - **Logic**: This actor crafts the text for the comic book, including character dialogue and narrative captions, based on the plot and characters.
                
                #### 5. Storyboard Actor (Image Actor)
                - **Purpose**: To create a visual storyboard that outlines each panel of the comic book.
                - **Input**: Script from the Script Actor.
                - **Output**: A series of images representing the storyboard.
                - **Logic**: The actor interprets the script and generates a sequence of images that represent the storyboard for the comic book.
                
                #### 6. Illustration Actor (Image Actor)
                - **Purpose**: To generate the final illustrations for each panel of the comic book.
                - **Input**: Storyboard images from the Storyboard Actor.
                - **Output**: Finalized comic book panels with illustrations.
                - **Logic**: This actor takes the storyboard images and refines them into detailed illustrations suitable for the final comic book.
                
                ### Logical Flow
                
                #### Step 1: Concept Development
                - **Input**: User prompt with desired theme or genre.
                - **Output**: High-level concept for the comic book.
                - **Actors Used**: Concept Actor.
                
                #### Step 2: Plot Outlining
                - **Input**: High-level concept from the Concept Actor.
                - **Output**: Detailed plot outline.
                - **Actors Used**: Plot Actor.
                
                #### Step 3: Character Design
                - **Input**: Detailed plot outline from the Plot Actor.
                - **Output**: Character profiles.
                - **Actors Used**: Character Actor.
                
                #### Step 4: Script Writing
                - **Input**: Detailed plot outline and character profiles.
                - **Output**: Script with dialogue and captions.
                - **Actors Used**: Script Actor.
                
                #### Step 5: Storyboarding
                - **Input**: Script from the Script Actor.
                - **Output**: Visual storyboard.
                - **Actors Used**: Storyboard Actor.
                
                #### Step 6: Illustration
                - **Input**: Storyboard images from the Storyboard Actor.
                - **Output**: Finalized comic book panels.
                - **Actors Used**: Illustration Actor.
                
                Each actor in the system would be designed to handle a specific part of the comic book creation process, with the output of one actor serving as the input for the next. The actors would work in a sequential manner, with the possibility of feedback loops for revisions. For example, the Script Actor might send the script back to the Character Actor for adjustments if new dialogue ideas suggest character changes. The system would be iterative, allowing for refinement at each stage to ensure a cohesive and polished final product.
                """.trimIndent().trim(),
                "Implement `fun storyboardActor() : ImageActor`",
            ).map { it.toChatMessage() },
            expectations = listOf(
            )
        )
    )
}