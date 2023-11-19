package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.platform.ApplicationServices
import com.simiacryptus.skyenet.platform.AuthenticationManager
import com.simiacryptus.skyenet.platform.AuthorizationManager
import kotlin.random.Random

object TestAppServer : AppServer(
    publicName = "localhost",
    localName = "localhost",
    port = Random.nextInt(1024, 65535),
) {
    @JvmStatic
    fun main(args: Array<String>) {
        AppServer(localName = "localhost","localhost", 8081).init(false)
        val mockUser = AuthenticationManager.UserInfo(
            "1",
            "user@mock.test",
            "Test User",
            ""
        )
        ApplicationServices.authenticationManager = object : AuthenticationManager() {
            override fun getUser(sessionId: String?) = mockUser
            override fun containsKey(value: String) = true
            override fun setUser(sessionId: String, userInfo: UserInfo) = throw UnsupportedOperationException()
        }
        ApplicationServices.authorizationManager = object : AuthorizationManager() {
            override fun isAuthorized(
                applicationClass: Class<*>?,
                user: String?,
                operationType: OperationType
            ): Boolean = true
        }
        super._main(args)
    }
}
