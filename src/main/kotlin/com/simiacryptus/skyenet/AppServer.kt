package com.simiacryptus.skyenet

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.apps.coding.SimpleCodingApp
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession


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

    val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
    override val childWebApps by lazy {

        listOf(
            ChildWebApp("/meta_agent", MetaAgentApp()),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName)),
            ChildWebApp("/spark_coder", SimpleCodingApp("Spark Coding Assistant", CodingActor(
                ScalaLocalInterpreter::class, symbols = mapOf(
                    "sc" to SparkContext.getOrCreate(sparkConf),
                    "spark" to SparkSession.builder().config(sparkConf).getOrCreate(),
                )
            ))),
            ChildWebApp("/aws_coder", SimpleCodingApp("AWS Coding Assistant", CodingActor(
                KotlinInterpreter::class, symbols = mapOf(
                    // Region
                    "region" to DefaultAwsRegionProviderChain().getRegion(),
                    // AWSCredentialsProvider
                    "credentials" to DefaultAWSCredentialsProviderChain.getInstance(),
                )
            ))),
            ChildWebApp("/debate_mapper", DebateApp(domainName = domainName)),

            // Legacy for the kids
            ChildWebApp("/roblox_cmd", AdminCommandCoder()),
            ChildWebApp("/roblox_script", BehaviorScriptCoder()),
        )}

}

