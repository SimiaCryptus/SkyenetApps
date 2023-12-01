package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.core.platform.AuthorizationInterface.OperationType
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
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
        ApplicationServices.authenticationManager = object : AuthenticationInterface {
            override fun getUser(accessToken: String?) = mockUser
            override fun containsUser(value: String) = true
            override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
            override fun logout(accessToken: String, user: User) {}
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


}
