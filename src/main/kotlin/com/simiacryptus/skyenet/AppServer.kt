package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.heart.GroovyInterpreter
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import com.simiacryptus.skyenet.test.*
import java.util.function.Function


open class AppServer(
    localName: String, publicName: String, port: Int
) : ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AppServer(localName = "localhost","apps.simiacrypt.us", 8081)._main(args)
        }
    }

    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName)),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName)),
            ChildWebApp("/test_coding_scala", CodingActorTestApp(CodingActor(ScalaLocalInterpreter::class))),
            ChildWebApp("/test_coding_kotlin", CodingActorTestApp(CodingActor(KotlinInterpreter::class))),
            ChildWebApp("/test_coding_groovy", CodingActorTestApp(CodingActor(GroovyInterpreter::class))),
            ChildWebApp("/test_simple", SimpleActorTestApp(SimpleActor("Translate the user's request into pig latin.", "PigLatin"))),
            ChildWebApp("/test_parsed_joke", ParsedActorTestApp(ParsedActor(JokeParser::class.java, "Tell me a joke"))),
            ChildWebApp("/meta_agent", MetaAgentApp()),
            ChildWebApp("/roblox_cmd", AdminCommandCoder()),
            ChildWebApp("/roblox_script", BehaviorScriptCoder()),
        )}



    data class TestJokeDataStructure(
        val setup: String? = null,
        val punchline: String? = null,
        val type: String? = null,
    )

    interface JokeParser : Function<String, TestJokeDataStructure>

}

