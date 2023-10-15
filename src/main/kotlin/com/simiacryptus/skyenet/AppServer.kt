package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.skyenet.AwsAgent.AwsSkyenetCodingSessionServer
import com.simiacryptus.skyenet.body.AuthenticatedWebsite
import com.simiacryptus.skyenet.body.WebSocketServer
import com.simiacryptus.skyenet.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.util.AwsUtil.decryptResource
import jakarta.servlet.Servlet
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import java.awt.Desktop
import java.net.URI

object AppServer {

    data class ChildWebApp(
        val path: String,
        val server: WebSocketServer,
        val isAuthenticated: Boolean = false
    )

    val localName = "localhost"
    val port = 8081
    fun domainName(isServer: Boolean) = if (isServer) "https://apps.simiacrypt.us" else "http://$localName:$port"
    var domainName: String = ""

    val childWebApps = listOf(
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
        ChildWebApp("/socratic_markdown", SocraticMarkdown())
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val isServer = args.contains("--server")
        domainName = domainName(isServer)
        OpenAIClient.keyTxt = decryptResource("openai.key.kms")

        val authentication = AuthenticatedWebsite(
            redirectUri = "$domainName/oauth2callback",
            applicationName = "Demo",
            key = { decryptResource("client_secret_google_oauth.json.kms").byteInputStream() }
        )

        val server = start(
            port,
            *(arrayOf(authentication.configure(
                newWebAppContext(
                    "/",
                    Resource.newResource(javaClass.classLoader.getResource("welcome")),
                    object : HttpServlet() {
                        override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
                            resp?.contentType = "text/html"
                            resp?.writer?.write("""
                                <!DOCTYPE html>
                                <html lang="en">
                                <head>
                                    <meta charset="UTF-8">
                                    <title>SimiaCryptus Skyenet Apps</title>
                                    <link href="chat.css" rel="stylesheet"/>
                                    <link rel="icon" type="image/png" href="favicon.png"/>
                                </head>
                                <body>
                
                                <div id="toolbar">
                                    ${
                                        childWebApps.joinToString("<br/>") {
                                            """<a href="${it.path}">${it.server.applicationName}</a>"""
                                        }
                                    }
                                </div>
                
                                </body>
                                </html>
                            """.trimIndent())
                        }
                    }
                ), false
            )) + childWebApps.map {
                if (it.isAuthenticated) authentication.configure(
                    newWebAppContext(
                        it.path,
                        it.server
                    )
                ) else newWebAppContext(it.path, it.server)
            })
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
        server.configure(webAppContext, baseUrl = "$domainName/$path")
        return webAppContext
    }

    fun newWebAppContext(path: String, baseResource: Resource?, indexServlet: Servlet? = null): WebAppContext {
        val context = WebAppContext()
        JettyWebSocketServletContainerInitializer.configure(context, null)
        context.baseResource = baseResource
        context.contextPath = path
        context.welcomeFiles = arrayOf("index.html")
        if (indexServlet != null) {
            context.addServlet(ServletHolder("index", indexServlet), "/index.html")
            context.addServlet(ServletHolder("index", indexServlet), "/")
        }
        return context
    }

}