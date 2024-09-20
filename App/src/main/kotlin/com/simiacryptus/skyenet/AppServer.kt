package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.apps.coding.AwsCodingApp
import com.simiacryptus.skyenet.apps.coding.BashCodingApp
import com.simiacryptus.skyenet.apps.coding.JDBCCodingApp
import com.simiacryptus.skyenet.apps.coding.PowershellCodingApp
import com.simiacryptus.skyenet.apps.general.IllustratedStorybookApp
import com.simiacryptus.skyenet.apps.general.OutlineApp
import com.simiacryptus.skyenet.apps.general.VocabularyApp
import com.simiacryptus.skyenet.apps.general.WebDevApp
import com.simiacryptus.skyenet.apps.generated.AutomatedLessonPlannerArchitectureApp
import com.simiacryptus.skyenet.apps.generated.CreativeWritingAssistantApp
import com.simiacryptus.skyenet.apps.generated.LibraryGeneratorApp
import com.simiacryptus.skyenet.apps.generated.TestGeneratorApp
import com.simiacryptus.skyenet.apps.hybrid.IncrementalCodeGenApp
import com.simiacryptus.skyenet.apps.premium.DebateApp
import com.simiacryptus.skyenet.apps.premium.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerApp
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.ApplicationServices.authorizationManager
import com.simiacryptus.skyenet.core.platform.ApplicationServices.seleniumFactory
import com.simiacryptus.skyenet.core.platform.ApplicationServicesConfig
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.platform.DatabaseServices
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon
import com.simiacryptus.skyenet.webui.servlet.WelcomeServlet
import com.simiacryptus.skyenet.webui.util.Selenium2S3
import org.intellij.lang.annotations.Language
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
        @JvmStatic
        fun main(args: Array<String>) {
            AppServer(localName = "localhost", "apps.simiacrypt.us", 8081)._main(args)
        }
    }

    //    private val sparkConf = SparkConf().setMaster("local[*]").setAppName("Spark Coding Assistant")
    override val childWebApps by lazy {
        listOf(
            ChildWebApp("/illustrated_storybook", IllustratedStorybookApp(domainName = domainName), "IllustratedStorybook.png"),
            ChildWebApp("/incremental_codegen", IncrementalCodeGenApp(domainName = domainName), null),
            ChildWebApp("/idea_mapper", OutlineApp(domainName = domainName), "outline.png"),
            ChildWebApp("/meta_agent", MetaAgentApp(), "MetaAgent.png"),
            ChildWebApp("/creative_writing", CreativeWritingAssistantApp("/creative_writing"), "CreativeWriting.png"),
            ChildWebApp("/debate", DebateApp(domainName = domainName), "Debate.png"),
            ChildWebApp("/presentation", PresentationDesignerApp(), "PresentationDesigner.png"),
            ChildWebApp("/library_generator", LibraryGeneratorApp(domainName = domainName), null),
            ChildWebApp("/vocabulary", VocabularyApp(), "Vocabulary.png"),
            ChildWebApp("/testgenerator", TestGeneratorApp(), null),
            ChildWebApp("/lesson_planner", AutomatedLessonPlannerArchitectureApp(domainName = domainName), null),
            ChildWebApp("/aws", AwsCodingApp(), "awscoding.png"),
            ChildWebApp("/bash", BashCodingApp(), "bashcoding.png"),
            ChildWebApp("/powershell", PowershellCodingApp(), "powershell.png"),
            ChildWebApp("/webdev", WebDevApp(), "webdev.png"),
            ChildWebApp("/jdbc", JDBCCodingApp(), "JDBCCoding.png"),
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
        super.setupPlatform()
        authorizationManager = object : AuthorizationManager() {
            override fun matches(user: User?, line: String): Boolean {
                if (line == "patreon") {
                    return OAuthPatreon.users[user?.email]?.data?.relationships?.pledges?.data?.isNotEmpty() ?: false
                }
                return super.matches(user, line)
            }
        }
        seleniumFactory = { pool, cookies ->
            object : Selenium2S3(
                pool,
                cookies,
            ) {
                override fun saveHTML(html: String, saveRoot: String, filename: String) {
                    var newHTML = html
                    newHTML = newHTML.replace(
                        "</body>", """
                        <style>
                        #footer {
                            position: fixed;
                            bottom: 0;
                            right: 20px;
                            width: 100%;
                            text-align: right;
                            z-index: 1000;
                        }
                        #footer a {
                            color: #4f80a4;
                            text-decoration: none;
                            font-weight: bold;
                        }
                        #footer a:hover {
                            text-decoration: underline;
                        }
                        </style>
                        <footer id="footer">
                            <a href="https://apps.simiacrypt.us/" target="_blank">Powered by Apps.Simiacrypt.us</a>
                        </footer>
                        </body>
                      """.trimIndent()
                    )
                    super.saveHTML(newHTML, saveRoot, filename)
                }
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

    override val welcomeServlet: WelcomeServlet
        get() = object : WelcomeServlet(this@AppServer) {

            val videoHtml = """<div style="width: 30%; float: right; margin: 1em;">
                <video controls width='100%'>
                    <source src="https://share.simiacrypt.us/Apps_Demo_03.mp4" type="video/mp4" />
                </video>
            </div>"""

            @Language("Markdown")
            override val welcomeMarkdown = """
            # Welcome to `apps.simiacrypt.us`!
            $videoHtml
            
            Welcome to the SimiaCryptus App Server! Here you will find a variety of AI applications using LLMs (i.e., ChatGPT)
            
            Users are welcome to browse this server anonymously. No cookies or ads here! 
            As you enjoy our site, be sure to check out our Privacy Policy and Terms of Service in the "about" menu.
            
            If you wish to try out an app, log in and enter your api key under user preferences to get started (details below).
            Premium apps and features available to Patreon supporters.
            
            ## Applications
        """.trimIndent()

            @Language("Markdown")
            override val postAppMarkdown = """
            
            To use this site to create your own content:
            1. Log in. (Upper right corner) We use Patreon for authentication, which in turn can relay Google and Apple accounts.
            2. Access your user settings via the "User"->`Settings` menu. (Upper right corner)
            3. Get an [OpenAI API key](https://platform.openai.com/account/api-keys) and enter it for the `"apiKey"` value. Submit.
            4. Select "New Private Session" for your chosen app to get started. Enjoy!
            
            ## Features
            * **Private Sessions** — By default, all user sessions are private. The public sessions you are seeing are set up by admins.
            * **Usage Reporting** — The ChatGPT API is paid by usage, and some of these jobs can be large. Keep track of how much you are using per session and globally.
            * **Session Configuration** — Many apps have individual settings, which can be configured in the settings menu. This should be done before the initial message is sent.
            * **Session Management** — Various methods allow you to manage sessions, such as cancelling accidents to control costs! (e.g. cancel, delete, files, debug, etc.)
            * **Session Sharing** (Supporter feature) — Your session is copied to the archive server, and you can share the link with anyone.
            * **Premium and Beta Apps** — Supporters can use premium apps and have sneak previews of upcoming apps.
            
            |                        | **Public**&nbsp;&nbsp;    | &nbsp;&nbsp;**Signed In**&nbsp;&nbsp;                         | &nbsp;&nbsp;**Supporter**                         |
            |------------------------|---------------|---------------------------------------|---------------------------------------|
            | **Public Sessions**    | &nbsp;&nbsp;✅ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ |
            | **Private Sessions**   | &nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ |
            | **Sharing / Archival** | &nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ |
            | **Premium Apps**       | &nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ |
            | **Beta Apps**          | &nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;❌ | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✅ |
            
            
        """.trimIndent()

        }
//    override val toolServlet: ToolServlet? get() = null


}