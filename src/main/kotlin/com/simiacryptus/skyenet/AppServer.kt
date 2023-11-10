package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.heart.GroovyInterpreter
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import com.simiacryptus.skyenet.servers.AppServerBase
import com.simiacryptus.skyenet.servers.CodingActorTestApp
import com.simiacryptus.skyenet.servers.ReadOnlyApp


object AppServer : AppServerBase() {

    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/idea_mapper_ro", ReadOnlyApp("IdeaMapper")),
            ChildWebApp("/debate_mapper_ro", ReadOnlyApp("DebateMapper")),
            ChildWebApp("/test_coding_scala", CodingActorTestApp(CodingActor(ScalaLocalInterpreter::class)), isAuthenticated = true),
            ChildWebApp("/test_coding_kotlin", CodingActorTestApp(CodingActor(KotlinInterpreter::class)), isAuthenticated = true),
            ChildWebApp("/test_coding_groovy", CodingActorTestApp(CodingActor(GroovyInterpreter::class)), isAuthenticated = true),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName), isAuthenticated = true),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName), isAuthenticated = true),
            ChildWebApp("/meta_agent", MetaAgentApp(domainName = domainName), isAuthenticated = true),
            ChildWebApp("/roblox_cmd", AdminCommandCoder()),
            ChildWebApp("/roblox_script", BehaviorScriptCoder()),
        )}

    @JvmStatic
    fun main(args: Array<String>) {
        super._main(args)
    }
}

