package com.simiacryptus.skyenet
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.apps.code.*
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookApp
import com.simiacryptus.skyenet.apps.general.OutlineApp
import com.simiacryptus.skyenet.apps.general.VocabularyApp
import com.simiacryptus.skyenet.apps.general.WebDevApp
import com.simiacryptus.skyenet.apps.generated.*
import com.simiacryptus.skyenet.apps.hybrid.IncrementalCodeGenApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.premium.DebateApp
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerApp
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.ApplicationServices.authorizationManager
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.model.ApplicationServicesConfig
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.platform.DatabaseServices
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import java.io.File


open class AppServer(
    localName: String, publicName: String, port: Int
) : ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {

    companion object {
      private val log = LoggerFactory.getLogger(AppServer::class.java)
        @JvmStatic
        fun main(args: Array<String>) {
            AppServer(localName = "localhost", "apps.simiacrypt.us", 8081)._main(args)
        }
    }
    open val api2 = OpenAIClient()

    //    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/illustrated_storybook", IllustratedStorybookApp(domainName = domainName), "IllustratedStorybook.png"),
            ChildWebApp("/incremental_codegen", IncrementalCodeGenApp(domainName = domainName), null),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName, api2 = api2), "outline.png"),
            ChildWebApp("/meta_agent", MetaAgentApp(), "MetaAgent.png"),
            ChildWebApp("/creative_writing", CreativeWritingAssistantApp("/creative_writing"), "CreativeWriting.png"),
            ChildWebApp("/debate", DebateApp(api2 = api2), "Debate.png"),
            ChildWebApp("/presentation", PresentationDesignerApp(), "PresentationDesigner.png"),
            ChildWebApp("/vocabulary", VocabularyApp(), "Vocabulary.png"),
            ChildWebApp("/testgenerator", TestGeneratorApp(), null),
            ChildWebApp("/lesson_planner", AutomatedLessonPlannerArchitectureApp(domainName = domainName), null),
            ChildWebApp("/aws", AwsCodingApp(), "awscoding.png"),
            ChildWebApp("/bash", BashCodingApp(), "bashcoding.png"),
            ChildWebApp("/powershell", PowershellCodingApp(), "powershell.png"),
            ChildWebApp("/webdev", WebDevApp(api2 = api2), "webdev.png"),
            ChildWebApp("/jdbc", JDBCCodingApp(), "JDBCCoding.png"),
            ChildWebApp("/science", ScientificAnalysisApplicationApp(), "coding.png"),
            ChildWebApp("/recombant_chain_of_thought", RecombantChainOfThoughtApp(), "coding.png"),
            ChildWebApp("/library_generator", LibraryGeneratorApp(), "coding.png"),
        )
    }

    override fun authenticatedWebsite(): OAuthBase {
        val encryptedData = javaClass.classLoader.getResourceAsStream("patreon.json.kms")?.readAllBytes()
            ?: throw RuntimeException("Unable to load resource: ${"patreon.json.kms"}")
        return OAuthPatreon(
            redirectUri = "$domainName/patreonOAuth2callback",
            config = JsonUtil.fromJson(
                ApplicationServices.cloud!!.decrypt(encryptedData),
                OAuthPatreon.PatreonOAuthInfo::class.java
            )
        )
    }

    override fun setupPlatform() {
        ApplicationServicesConfig.dataStorageRoot = File(".skyenet").absoluteFile
        authorizationManager = object : AuthorizationManager() {
            override fun matches(user: User?, line: String): Boolean {
                if (line == "patreon") {
                    return OAuthPatreon.users[user?.email]?.data?.relationships?.pledges?.data?.isNotEmpty() ?: false
                }
                return super.matches(user, line)
            }
        }
        val jdbc = System.getProperties()["db.url"]
        if (jdbc != null) DatabaseServices(
            jdbcUrl = jdbc as String,
            username = System.getProperties()["db.user"] as String,
            password = { getPassword() }
        ).register()

    }

    private fun fetchPlaintextSecret(secretArn: String, region: Region) =
        SecretsManagerClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build().getSecretValue(
                GetSecretValueRequest.builder()
                    .secretId(secretArn)
                    .build()
            ).secretString()

    private fun getPassword() = (System.getProperties()["db.password"] as String).run {
        when {
            startsWith("arn:aws:secretsmanager:us-east-1:") -> {
                val plaintextSecret: String = fetchPlaintextSecret(this, Region.US_EAST_1)
                val secretJson = JsonUtil.fromJson<Map<String, String>>(plaintextSecret, Map::class.java)
                secretJson["password"] as String
            }
            // Literal password
            else -> this
        }
    }
}