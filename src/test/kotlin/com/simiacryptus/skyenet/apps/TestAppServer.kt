package com.simiacryptus.skyenet.apps

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationManager
import com.simiacryptus.skyenet.core.platform.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.util.AwsUtil
import com.simiacryptus.skyenet.core.util.AwsUtil.decryptResource
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon
import kotlin.random.Random

object TestAppServer : AppServer(
    publicName = "localhost",
    localName = "localhost",
    port = Random.nextInt(1024, 65535),
) {
    @JvmStatic
    fun main(args: Array<String>) {
        AppServer(localName = "localhost","localhost", 8081).init(false)
        val mockUser = User(
            "1",
            "user@mock.test",
            "Test User",
            ""
        )
        ApplicationServices.authenticationManager = object : AuthenticationManager() {
            override fun getUser(accessToken: String?) = mockUser
            override fun containsUser(value: String) = true
            override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
        }
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: User?,
                operationType: OperationType
            ): Boolean = true
        }
        super._main(args)
    }


    override fun authenticatedWebsite(): OAuthBase = OAuthPatreon(
        redirectUri = "$domainName/oauth2callback",
        config = JsonUtil.fromJson(
            decryptResource("patreon.json.kms", javaClass.classLoader),
            OAuthPatreon.PatreonOAuthInfo::class.java)
    )
}
