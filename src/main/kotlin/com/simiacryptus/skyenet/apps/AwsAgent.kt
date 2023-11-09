@file:Suppress("unused")

package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.util.AwsUtil.decryptResource
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.Heart
import com.simiacryptus.skyenet.body.SessionServerUtil.asJava
import com.simiacryptus.skyenet.body.SkyenetCodingSessionServer
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import com.simiacryptus.util.describe.AbbrevWhitelistYamlDescriber
import org.eclipse.jetty.server.Server
import java.io.File
import java.util.Map

object AwsAgent {

    // Function to Say Hello World
    class AwsClients(val defaultRegion: Regions) {
        fun s3() = AmazonS3ClientBuilder.standard().withRegion(defaultRegion).build()
        fun ec2() = AmazonEC2ClientBuilder.standard().withRegion(defaultRegion).build()
        fun rds() = AmazonRDSClientBuilder.standard().withRegion(defaultRegion).build()
        fun cloudwatch() = AmazonCloudWatchClientBuilder.standard().withRegion(defaultRegion).build()
        fun route53() =
            com.amazonaws.services.route53.AmazonRoute53ClientBuilder.standard().withRegion(defaultRegion).build()

        fun emr() = com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder.standard()
            .withRegion(defaultRegion).build()

        fun lambda() = com.amazonaws.services.lambda.AWSLambdaClientBuilder.standard().withRegion(defaultRegion).build()
    }

    class HttpUtil {
        fun client() = org.apache.http.impl.client.HttpClients.createDefault()
    }

    val oauthConfig: File by lazy {
        writeToTempFile(
            decryptResource("client_secret_google_oauth.json.kms"),
            "client_secret_google_oauth.json"
        )
    }

    fun start(baseURL: String, port: Int): Server {
        // Load /client_secret_google_oauth.json.kms from classpath, decrypt it, and print it out
        return codingSessionServer(baseURL).start(port)
    }

    open class AwsSkyenetCodingSessionServer(
        oauthConfig: File? = AwsAgent.oauthConfig,
    )  : SkyenetCodingSessionServer(
        applicationName = "AwsAgent",
        typeDescriber = AbbrevWhitelistYamlDescriber("com.simiacryptus", "com.github.simiacryptus"),
        oauthConfig = oauthConfig?.absolutePath,
        model = OpenAIClient.Models.GPT4,
        apiKey = OpenAIClient.keyTxt
    ) {
        override fun hands() = mapOf(
            "aws" to AwsClients(Regions.US_EAST_1) as Object,
            "client" to HttpUtil() as Object,
        ).asJava

        override fun toString(e: Throwable): String {
            return e.message ?: e.toString()
        }

        //            override fun heart(hands: java.util.Map<String, Object>): Heart = GroovyInterpreter(hands)
        override fun heart(hands: Map<String, Object>): Heart =
            ScalaLocalInterpreter::class.java.getConstructor(Map::class.java).newInstance(hands)
    }

    fun codingSessionServer(baseURL: String) = AwsSkyenetCodingSessionServer()

    fun writeToTempFile(text: String, filename: String): File {
        val tempFile = File.createTempFile(filename, ".tmp")
        tempFile.deleteOnExit()
        tempFile.writeText(text)
        return tempFile
    }


}
