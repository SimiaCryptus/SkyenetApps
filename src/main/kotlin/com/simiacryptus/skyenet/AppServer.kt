package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.AwsAgent.AwsSkyenetCodingSessionServer
import com.simiacryptus.skyenet.body.AuthenticatedWebsite
import com.simiacryptus.skyenet.body.WebSocketServer
import com.simiacryptus.skyenet.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.util.AwsUtil.decryptResource
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import java.awt.Desktop
import java.net.URI

object AppServer {

    @JvmStatic
    fun main(args: Array<String>) {
        OpenAIClient.keyTxt = decryptResource("openai.key.kms")
        val isServer = args.contains("--server")
        val localName = "localhost"
        val port = 8081
        val domainName = if (isServer) "https://apps.simiacrypt.us" else "http://$localName:$port"

        val authentication = AuthenticatedWebsite(
            redirectUri = "$domainName/oauth2callback",
            applicationName = "Demo",
            key = { decryptResource("client_secret_google_oauth.json.kms").byteInputStream() }
        )

        val server = start(
            port,
            authentication.configure(
                newWebAppContext(
                    "/",
                    Resource.newResource(javaClass.classLoader.getResource("welcome"))
                ), false
            ),
            authentication.configure(
                newWebAppContext(
                    "/awsagent", AwsSkyenetCodingSessionServer(
                        baseURL = "$domainName/awsagent/",
                        oauthConfig = null
                    )
                )
            ),
            newWebAppContext(
                "/storygen", StoryGenerator(
                    applicationName = "StoryGenerator",
                    baseURL = "$domainName/storygen/"
                )
            ),
            newWebAppContext(
                "/news", NewsStoryGenerator(
                    applicationName = "NewsStoryGenerator",
                    baseURL = "$domainName/news/"
                )
            ),
            newWebAppContext(
                "/cookbook", CookbookGenerator(
                    applicationName = "CookbookGenerator",
                    baseURL = "$domainName/cookbook/"
                )
            ),
            newWebAppContext(
                "/science", SkyenetScienceBook(
                    applicationName = "ScienceBookGenerator",
                    baseURL = "$domainName/science/"
                )
            ),
            newWebAppContext(
                "/software", SoftwareProjectGenerator(
                    applicationName = "SoftwareProjectGenerator",
                    baseURL = "$domainName/software/"
                )
            ),
            newWebAppContext(
                "/roblox_cmd", AdminCommandCoder(
                    applicationName = "AdminCommandCoder",
                    baseURL = "$domainName/roblox_cmd/"
                )
            ),
            newWebAppContext(
                "/roblox_script", BehaviorScriptCoder(
                    applicationName = "BehaviorScriptCoder",
                    baseURL = "$domainName/roblox_script/"
                )
            ),
            newWebAppContext(
                "/storyiterator", StoryIterator(
                    applicationName = "StoryIterator",
                    baseURL = "$domainName/storyiterator/"
                )
            )
        )
        try {
            Desktop.getDesktop().browse(URI("$domainName/"))
        } catch (e: Throwable) {
            // Ignore
        }
        server.join()
    }

    fun start(
        port: Int,
        vararg webAppContexts: WebAppContext
    ): Server {
        val contexts = ContextHandlerCollection()
        contexts.handlers = webAppContexts
        val server = Server(port)
        server.handler = contexts
        server.start()
        return server
    }

    fun newWebAppContext(path: String, server: WebSocketServer): WebAppContext {
        val webAppContext = newWebAppContext(path, server.baseResource)
        server.configure(webAppContext)
        return webAppContext
    }

    fun newWebAppContext(path: String, baseResource: Resource?): WebAppContext {
        val awsagentContext = WebAppContext()
        JettyWebSocketServletContainerInitializer.configure(awsagentContext, null)
        awsagentContext.baseResource = baseResource
        awsagentContext.contextPath = path
        awsagentContext.welcomeFiles = arrayOf("index.html")
        return awsagentContext
    }

}