package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization
import com.simiacryptus.skyenet.core.actors.opt.ActorOptimization.Companion.toChatMessage
import com.simiacryptus.skyenet.core.actors.opt.Expectation
import com.simiacryptus.skyenet.core.actors.test.ParsedActorTestBase
import org.junit.jupiter.api.Test

object InitialDesignerActorTest :
    ParsedActorTestBase<MetaAgentActors.AgentDesign>(MetaAgentActors.DesignParser::class.java) {

    @Test
    override fun testRun() = super.testRun()
    override val actor = MetaAgentActors().initialDesigner()
    override val testCases = listOf(
        ActorOptimization.TestCase(
            userMessages = listOf(
                "Design a software project designer",
            ).map { it.toChatMessage() },
            expectations = listOf(
                Expectation.ContainsMatch("""Actors""".toRegex(), critical = false),
                Expectation.VectorMatch("Software Project Designer System Design Document")
            )
        )
    )
}