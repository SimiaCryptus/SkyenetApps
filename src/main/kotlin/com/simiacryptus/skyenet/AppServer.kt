package com.simiacryptus.skyenet
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.apps.code.*
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookApp
import com.simiacryptus.skyenet.apps.general.OutlineApp
import com.simiacryptus.skyenet.apps.general.VocabularyApp
import com.simiacryptus.skyenet.apps.hybrid.IncrementalCodeGenApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.premium.DebateApp
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerApp
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.model.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import java.awt.SystemTray
import java.io.BufferedWriter
import java.net.ServerSocket


open class AppServer(
    localName: String, publicName: String, port: Int
) : ApplicationDirectory(
    localName = localName, publicName = publicName, port = port
) {
    private var systemTrayManager: SystemTrayManager? = null
    private var socketServer: ServerSocket? = null
    private var socketThread: Thread? = null

    companion object {
      private val log = LoggerFactory.getLogger(AppServer::class.java.name)
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                if (args.isEmpty()) {
                    log.info("No arguments provided - defaulting to server mode with default options")
                    handleServer()
                    return
                }
                when (args[0].lowercase()) {
                    "server" -> handleServer(*args.sliceArray(1 until args.size))
                    "help", "-h", "--help" -> printUsage()
                    "daemon" -> {
                        // For compatibility: allow launching as a daemon
                        handleServer(*args.sliceArray(1 until args.size))
                    }
                    else -> {
                        handleServer()
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
        private fun handleServer(vararg args: String) {
            log.info("Parsing server options...")
            val options = parseServerOptions(*args)
            log.info("Configuring server with options: port=${options.port}, host=${options.host}, publicName=${options.publicName}")
            server = AppServer(
                localName = options.host,
                publicName = options.publicName, 
                port = options.port
            )
            server?.initSystemTray()
            server?.startSocketServer(options.port)
            server?._main(*args)
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
        val port: Int = 7681,
        val host: String = "localhost", 
        val publicName: String = "apps.simiacrypt.us"
    )

        private fun parseServerOptions(vararg args: String): ServerOptions {
        var port = 7681
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
                            throw IllegalArgumentException("Invalid port number: ${args[i]}")
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
        systemTrayManager = SystemTrayManager(
            port = port,
            host = localName,
            apps = childWebApps,
            onExit = {
            log.info("Exit requested from system tray")
            stopServer()
            System.exit(0)
            })
        systemTrayManager?.initialize()
    }
    fun stopServer() {
        systemTrayManager?.remove()
        stopSocketServer()
    }
    open val api2 = OpenAIClient()

    override fun authenticatedWebsite() = object : OAuthBase("") {
        override fun configure(context: WebAppContext, addFilter: Boolean) = context
    }

    override fun setupPlatform() {
        super.setupPlatform()
        val mockUser = User(
            "1",
            "user@mock.test",
            "Test User",
            ""
        )
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = mockUser
            override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
            override fun logout(accessToken: String, user: User) {}
        }
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: User?,
                operationType: AuthorizationInterface.OperationType
            ): Boolean = true
        }
    }

    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/illustrated_storybook", IllustratedStorybookApp(domainName = domainName), "IllustratedStorybook.png"),
            ChildWebApp("/incremental_codegen", IncrementalCodeGenApp(domainName = domainName), null),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName, api2 = api2), "outline.png"),
            ChildWebApp("/meta_agent", MetaAgentApp(), "MetaAgent.png"),
            ChildWebApp("/debate", DebateApp(api2 = api2), "Debate.png"),
            ChildWebApp("/presentation", PresentationDesignerApp(), "PresentationDesigner.png"),
            ChildWebApp("/vocabulary", VocabularyApp(), "Vocabulary.png"),
            ChildWebApp("/aws", AwsCodingApp(), "awscoding.png"),
            ChildWebApp("/bash", BashCodingApp(), "bashcoding.png"),
            ChildWebApp("/powershell", PowershellCodingApp(), "powershell.png"),
            ChildWebApp("/jdbc", JDBCCodingApp(), "JDBCCoding.png"),
            ChildWebApp("/library_generator", LibraryGeneratorApp(), "coding.png"),
        )
    }
    
    protected open fun onMessage(line: String?) {
        log.info("Received command from DaemonClient: $line")
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
    
    /**
     * Start a simple socket server to listen for commands from DaemonClient.
     * Responds with a simple acknowledgment for now.
     */
    private fun startSocketServer(port: Int) {
        if (socketServer != null) {
            log.warn("Socket server already started on port $port")
            return
        }
        socketThread = Thread {
            try {
                socketServer = ServerSocket(port)
                log.info("Socket server started on port $port")
                while (!socketServer!!.isClosed) {
                    val client = try {
                        socketServer!!.accept()
                    } catch (e: java.io.IOException) {
                        log.info("Socket server stopped accepting connections: ${e.message}")
                        break
                    }
                    Thread {
                        var output: BufferedWriter? = null
                        try {
                            val input = client.getInputStream().bufferedReader()
                            output = client.getOutputStream().bufferedWriter()
                            val line = input.readLine()
                            if (line != null) {
                                onMessage(line)
                                output.write("OK: $line\n")
                            } else {
                                output.write("ERROR: No command received\n")
                            }
                        } catch (e: Exception) {
                            output?.write("ERROR: ${(e.message ?: e.toString()).replace('\n', ' ')}\n")
                            log.error("Socket handler error: ${e.message}", e)
                        } finally {
                            output?.flush()
                            try {
                                client.close()
                            } catch (_: Exception) {
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                log.error("Socket server error: ${e.message}", e)
            } finally {
                try {
                    socketServer?.close()
                } catch (_: Exception) {
                }
                socketServer = null
                log.info("Socket server thread exiting")
            }
        }
        socketThread?.isDaemon = true
        socketThread?.name = "AppServer-SocketThread"
        socketThread?.start()
    }
    
    private fun stopSocketServer() {
        try {
            socketServer?.close()
        } catch (_: Exception) {
        }
        socketThread?.interrupt()
        socketServer = null
        socketThread = null
    }
}