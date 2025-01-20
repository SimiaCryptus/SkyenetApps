package com.simiacryptus.skyenet
import com.simiacryptus.jopenai.OpenAIClient
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.SwingUtilities
import org.slf4j.LoggerFactory
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
    private var systemTrayManager: SystemTrayManager? = null

    companion object {
      private val log = LoggerFactory.getLogger(AppServer::class.java.name)
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                if (args.isEmpty()) {
                    log.info("No arguments provided - defaulting to server mode with default options")
                    handleServer(arrayOf())
                    return
                }
                when (args[0].lowercase()) {
                    "server" -> handleServer(args.sliceArray(1 until args.size))
                    "help", "-h", "--help" -> printUsage()
                    else -> {
                        log.error("Unknown command: ${args[0]}")
                        printUsage()
                        System.exit(1)
                    }
                }
            } catch (e: Exception) {
                log.error("Fatal error: ${e.message}", e)
                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(Thread {
                    log.info("Shutting down server...")
                    server?.stopServer()
                })
                System.exit(1)
            }
        }
        private var server: AppServer? = null
        private fun handleServer(args: Array<String>) {
            log.info("Parsing server options...")
            val options = parseServerOptions(args)
            log.info("Configuring server with options: port=${options.port}, host=${options.host}, publicName=${options.publicName}")
            server = AppServer(
                localName = options.host,
                publicName = options.publicName, 
                port = options.port
            )
            server?.initSystemTray()
            server?._main(args)
        }
        private fun printUsage() {
            println("""
                SkyenetApps Server
                Usage:
                  skyenet <command> [options]
                Commands:
                  server     Start the server
                  help      Show this help message
                For server options:
                  skyenet server --help
            """.trimIndent())
        }
    private data class ServerOptions(
        val port: Int = 8081,
        val host: String = "localhost", 
        val publicName: String = "apps.simiacrypt.us"
    )
    private fun parseServerOptions(args: Array<String>): ServerOptions {
        var port = 8081
        var host = "localhost"
        var publicName = "apps.simiacrypt.us"
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--port" -> {
                    if (i + 1 < args.size) {
                        log.debug("Setting port to: ${args[i + 1]}")
                        port = args[++i].toIntOrNull() ?: run {
                            log.error("Invalid port number: ${args[i]}")
                            System.exit(1)
                        }
                    }
                }
                "--host" -> if (i + 1 < args.size) host = args[++i]
                "--public-name" -> if (i + 1 < args.size) publicName = args[++i]
                else -> {
                    log.error("Unknown server option: ${args[i]}")
                    throw IllegalArgumentException("Unknown server option: ${args[i]}")
                }}
            i++
        }
        log.debug("Server options parsed successfully")
        return ServerOptions(port, host, publicName)
    }
    }
    private fun initSystemTray() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }
        systemTrayManager = SystemTrayManager(port, localName) {
            log.info("Exit requested from system tray")
            stopServer()
            System.exit(0)
        }
        systemTrayManager?.initialize()
    }
    fun stopServer() {
        systemTrayManager?.remove()
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