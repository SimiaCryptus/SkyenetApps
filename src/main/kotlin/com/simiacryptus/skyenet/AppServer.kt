package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.coding.CodingApp
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.core.util.AwsUtil
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon


open class AppServer(
    localName: String, publicName: String, port: Int
) : com.simiacryptus.skyenet.webui.application.ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            AppServer(localName = "localhost","apps.simiacrypt.us", 8081)._main(args)
        }
    }

//    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
    override val childWebApps by lazy {

        listOf(
            ChildWebApp("/meta_agent", MetaAgentApp()),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName)),
//            ChildWebApp("/spark_coder", SimpleCodingApp("Spark Coding Assistant", CodingActor(
//                ScalaLocalInterpreter::class, symbols = mapOf(
//                    "sc" to SparkContext.getOrCreate(sparkConf),
//                    "spark" to SparkSession.builder().config(sparkConf).getOrCreate(),
//                )
//            )
//            )),
            ChildWebApp("/aws_coder", CodingApp("AWS Coding Assistant"            )),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName)),

            // Legacy for the kids
            ChildWebApp("/roblox_cmd", AdminCommandCoder()),
            ChildWebApp("/roblox_script", BehaviorScriptCoder()),
        )}

    override fun authenticatedWebsite(): OAuthBase = OAuthPatreon(
        redirectUri = "$domainName/patreonOAuth2callback",
        config = JsonUtil.fromJson(
            AwsUtil.decryptResource("patreon.json.kms", javaClass.classLoader),
            OAuthPatreon.PatreonOAuthInfo::class.java)
    )
}

