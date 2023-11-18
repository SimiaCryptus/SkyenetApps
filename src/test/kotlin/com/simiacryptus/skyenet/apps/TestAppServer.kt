package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.ApplicationDirectory
import com.simiacryptus.skyenet.actors.CodingActor
import com.simiacryptus.skyenet.actors.ParsedActor
import com.simiacryptus.skyenet.actors.SimpleActor
import com.simiacryptus.skyenet.apps.TestAppServer.port
import com.simiacryptus.skyenet.apps.debate.DebateApp
import com.simiacryptus.skyenet.apps.meta.MetaAgentApp
import com.simiacryptus.skyenet.apps.outline.OutlineApp
import com.simiacryptus.skyenet.apps.roblox.AdminCommandCoder
import com.simiacryptus.skyenet.apps.roblox.BehaviorScriptCoder
import com.simiacryptus.skyenet.config.ApplicationServices
import com.simiacryptus.skyenet.config.AuthenticationManager
import com.simiacryptus.skyenet.config.AuthorizationManager
import com.simiacryptus.skyenet.heart.GroovyInterpreter
import com.simiacryptus.skyenet.heart.KotlinInterpreter
import com.simiacryptus.skyenet.heart.ScalaLocalInterpreter
import com.simiacryptus.skyenet.test.*
import java.util.function.Function
import kotlin.math.roundToInt
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
