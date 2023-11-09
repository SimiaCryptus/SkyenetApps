package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.CodingActorTestApp
import com.simiacryptus.skyenet.apps.AwsAgent.AwsSkyenetCodingSessionServer
import com.simiacryptus.skyenet.actors.SimpleActorTestApp
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.*
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.servers.AppServerBase
import com.simiacryptus.skyenet.servers.ReadOnlyApp


object AppServer : AppServerBase() {

    override val childWebApps by lazy {
        listOf(
            ChildWebApp(
                "/awsagent",
                AwsSkyenetCodingSessionServer(oauthConfig = null),
                isAuthenticated = true
            ),
            ChildWebApp("/storygen", StoryGenerator()),
//        ChildWebApp("/news", NewsStoryGenerator()),
            ChildWebApp("/cookbook", CookbookGenerator()),
            ChildWebApp("/science", SkyenetScienceBook()),
            ChildWebApp("/software", SoftwareProjectGenerator()),
            ChildWebApp("/roblox_cmd", AdminCommandCoder()),
            ChildWebApp("/roblox_script", BehaviorScriptCoder()),
//        ChildWebApp("/storyiterator", StoryIterator()),
//        ChildWebApp("/socratic_analysis", SocraticAnalysis()),
            ChildWebApp("/socratic_markdown", SocraticMarkdown()),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName), isAuthenticated = true),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName), isAuthenticated = true),
            ChildWebApp("/idea_mapper_ro", ReadOnlyApp("IdeaMapper")),
            ChildWebApp("/debate_mapper_ro", ReadOnlyApp("DebateMapper")),
            ChildWebApp("/test_simple", SimpleActorTestApp(SimpleActor("Translate the user's request into pig latin."))),
            ChildWebApp("/test_coding", CodingActorTestApp(CodingActor())),
        )}

    @JvmStatic
    fun main(args: Array<String>) {
        super._main(args)
    }
}

