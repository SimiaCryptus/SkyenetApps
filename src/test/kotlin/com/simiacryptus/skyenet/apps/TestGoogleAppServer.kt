package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.AppServer
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.webui.servlet.OAuthGoogle

object TestGoogleAppServer : AppServer(
    publicName = "localhost",
    localName = "localhost",
    port = 37600,
) {

    @JvmStatic
    fun main(args: Array<String>) {
        super._main(args)
    }

    override fun authenticatedWebsite() = OAuthGoogle(
        redirectUri = "$domainName/oauth2callback",
        applicationName = "Demo",
        key = {
            ApplicationServices.cloud?.decrypt(
                Thread.currentThread().contextClassLoader.getResourceAsStream("client_secret_google_oauth.json.kms")
                    .readBytes()
            )?.byteInputStream()
        }
    )


}
