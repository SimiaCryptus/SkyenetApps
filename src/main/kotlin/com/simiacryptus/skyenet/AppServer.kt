package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.coding.CodingApp
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.generated.AutomatedLessonPlannerArchitectureApp
import com.simiacryptus.skyenet.apps.beta.IllustratedStorybookApp
import com.simiacryptus.skyenet.apps.generated.LibraryGeneratorApp
import com.simiacryptus.skyenet.apps.generated.PresentationGeneratorApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.core.util.AwsUtil
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain


open class AppServer(
    localName: String, publicName: String, port: Int
) : com.simiacryptus.skyenet.webui.application.ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AppServer(localName = "localhost", "apps.simiacrypt.us", 8081)._main(args)
        }
    }

    //    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName)),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName)),
            ChildWebApp("/illustrated_storybook", IllustratedStorybookApp(domainName = domainName)),
            ChildWebApp("/library_generator", LibraryGeneratorApp(domainName = domainName)),
            ChildWebApp("/presentation_generator", PresentationGeneratorApp(domainName = domainName)),
            ChildWebApp("/lesson_planner", AutomatedLessonPlannerArchitectureApp(domainName = domainName)),
            ChildWebApp("/meta_agent", MetaAgentApp()),
            ChildWebApp("/aws_coder", CodingApp(
                    "AWS Coding Assistant",
                    KotlinInterpreter::class,
                    mapOf(
                            "region" to DefaultAwsRegionProviderChain().getRegion(),
                    ))),
        )
    }

    override fun authenticatedWebsite(): OAuthBase = OAuthPatreon(
        redirectUri = "$domainName/patreonOAuth2callback",
        config = JsonUtil.fromJson(
            AwsUtil.decryptResource("patreon.json.kms", javaClass.classLoader),
            OAuthPatreon.PatreonOAuthInfo::class.java
        )
    )

    override fun setupPlatform() {
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            override fun matches(user: User?, line: String): Boolean {
                if(line == "patreon") {
                    return OAuthPatreon.users[user?.email]?.data?.relationships?.pledges?.data?.isNotEmpty() ?: false
                }
                return super.matches(user, line)
            }
        }

    }
}

