package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.actors.opt.Expectation
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

object InitialDesignerActorOptTest {

    private val log = LoggerFactory.getLogger(InitialDesignerActorOptTest::class.java)

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
                actorFactory = { ParsedActor(
                    MetaActors.DesignParser::class.java,
                    prompt = it,
                ) },
                resultMapper = { JsonUtil.toJson(it.getObj()) },
                prompts = listOf(
                    """
                    |You are a software design assistant.
                    |
                    |Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
                    |
                    |There are three types of actors:
                    |1. Simple actors contain a system directive and can process a list of user messages into a response
                    |2. Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
                    |3. Script actors use a multi-stage process that combines an environment definition of predefined symbols/functions and a pluggable script compilation system using Scala, Kotlin, or Groovy. The actor will return a valid script with a convenient "execute" method. This can provide both simple function calling responses and complex code generation.
                    |
                    |Combined with traditional programming and existing jvm libraries, and also vector embeddings/databases using gpt models, we form complete "actor applications" or "agents". Optional UI elements exist to provide interactivity appropriate to a web application.
                    |
                    |As an example, consider an "Idea Mapping" agent containing 3 actors:
                    |1. Initial author actor to answer a user query and parse the initial response essay into an outline
                    |2. Expansion actor to expand the initial essay based on each parsed outline section
                    |3. Final actor to generate a final essay based on the expanded outline, after tree manipulations such as substitution and partitioning
                    |
                    |The result of this example is the ability to generate much longer, more insightful explorations of concepts via queries to ChatGPT.
                    |
                    |Some important design principles:
                    |1. ChatGPT has an optimal token window of still around 4k to "logic" although it now can support 128k of input tokens.
                    |2. The logic of each individual actor is specialized and focused on a single task. This both conserves the cognitive space of the model, and allows the system to be more easily understood and debugged.
                    |
                    |Respond to the user's idea with a detailed technical document for a proposed system design including:
                    |1. the actors used, including a description of the actor's purpose and how it is used
                    |2. a step-by-step outline of the logical flow of the system
                    |
                    |""".trimMargin().trim(),
                ),
                testCases = listOf(
                    ActorOptimization.TestCase(
                        userMessages = listOf(
                            "Design a software project designer",
                        ),
                        expectations = listOf(
                            Expectation.ContainsMatch("""Actors""".toRegex(), critical = false),
                            Expectation.VectorMatch("Software Project Designer System Design Document")
                        )
                    )
                ),
            )
        } catch (e: Throwable) {
            log.error("Error", e)
        } finally {
            System.exit(0)
        }
    }

}