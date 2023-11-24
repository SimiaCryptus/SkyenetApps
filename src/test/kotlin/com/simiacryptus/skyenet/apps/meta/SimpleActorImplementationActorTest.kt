package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization.Companion.toChatMessage
import com.simiacryptus.skyenet.core.actors.test.CodingActorTestBase
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

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
                "To design a system that uses GPT actors to construct a model of a creative process for generating comic books, we need to consider the various stages of comic book creation, such as concept development, scripting, storyboarding, illustration, and dialogue writing. Here's how the system could be structured using different types of actors:\n\n### Actors\n\n#### 1. Concept Actor (Parsed Actor)\n- **Purpose**: To generate a high-level concept or theme for the comic book based on user input.\n- **Input**: User prompt describing desired theme or genre.\n- **Output**: A typed object containing a structured concept, including genre, setting, and basic plot elements.\n- **Logic**: The actor takes the user prompt, generates a natural-language response with a concept, and then parses this into a structured format.\n\n#### 2. Plot Actor (Parsed Actor)\n- **Purpose**: To create a detailed plot outline that follows the high-level concept.\n- **Input**: Typed object from the Concept Actor.\n- **Output**: A typed object containing a detailed plot outline with story arcs and key events.\n- **Logic**: This actor develops a more granular plot structure, turning the high-level concept into a step-by-step outline.\n\n#### 3. Character Actor (Parsed Actor)\n- **Purpose**: To design characters that fit into the comic book's plot and setting.\n- **Input**: Typed object from the Plot Actor.\n- **Output**: A typed object with character profiles, including names, roles, appearances, and personalities.\n- **Logic**: Based on the plot outline, this actor creates characters, ensuring they are relevant to the story and contribute to the plot.\n\n#### 4. Script Actor (Simple Actor)\n- **Purpose**: To write the dialogue and captions for the comic book.\n- **Input**: Typed objects from the Plot Actor and Character Actor.\n- **Output**: A script with dialogue, captions, and panel descriptions.\n- **Logic**: This actor crafts the text for the comic book, including character dialogue and narrative captions, based on the plot and characters.\n\n#### 5. Storyboard Actor (Image Actor)\n- **Purpose**: To create a visual storyboard that outlines each panel of the comic book.\n- **Input**: Script from the Script Actor.\n- **Output**: A series of images representing the storyboard.\n- **Logic**: The actor interprets the script and generates a sequence of images that represent the storyboard for the comic book.\n\n#### 6. Illustration Actor (Image Actor)\n- **Purpose**: To generate the final illustrations for each panel of the comic book.\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels with illustrations.\n- **Logic**: This actor takes the storyboard images and refines them into detailed illustrations suitable for the final comic book.\n\n### Logical Flow\n\n#### Step 1: Concept Development\n- **Input**: User prompt with desired theme or genre.\n- **Output**: High-level concept for the comic book.\n- **Actors Used**: Concept Actor.\n\n#### Step 2: Plot Outlining\n- **Input**: High-level concept from the Concept Actor.\n- **Output**: Detailed plot outline.\n- **Actors Used**: Plot Actor.\n\n#### Step 3: Character Design\n- **Input**: Detailed plot outline from the Plot Actor.\n- **Output**: Character profiles.\n- **Actors Used**: Character Actor.\n\n#### Step 4: Script Writing\n- **Input**: Detailed plot outline and character profiles.\n- **Output**: Script with dialogue and captions.\n- **Actors Used**: Script Actor.\n\n#### Step 5: Storyboarding\n- **Input**: Script from the Script Actor.\n- **Output**: Visual storyboard.\n- **Actors Used**: Storyboard Actor.\n\n#### Step 6: Illustration\n- **Input**: Storyboard images from the Storyboard Actor.\n- **Output**: Finalized comic book panels.\n- **Actors Used**: Illustration Actor.\n\nEach actor in the system would be designed to handle a specific part of the comic book creation process, with the output of one actor serving as the input for the next. The actors would work in a sequential manner, with the possibility of feedback loops for revisions. For example, the Script Actor might send the script back to the Character Actor for adjustments if new dialogue ideas suggest character changes. The system would be iterative, allowing for refinement at each stage to ensure a cohesive and polished final product.".trimIndent().trim(),
                "Implement `fun scriptActor() : SimpleActor`",
            ).map { it.toChatMessage() },
            expectations = listOf(
            )
        )
    )
}