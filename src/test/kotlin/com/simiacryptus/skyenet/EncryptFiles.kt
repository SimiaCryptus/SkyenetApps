package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.util.AwsUtil
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon

object EncryptFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        AwsUtil.encryptData(
            JsonUtil.toJson(
                OAuthPatreon.PatreonOAuthInfo(
                    name = "apps.simiacrypt.us",
                    clientId = "hvn76GkA9zc7-VN7pyX5BkzKRCP206wf2bjynGESl0faancYN8iqFzxpm9azhBj0",
                    apiVersion = "2",
                    clientSecret = "",
                    creatorAccessToken = "",
                    creatorRefreshToken = ""
                )
            ).encodeToByteArray(),
            """C:\Users\andre\code\SkyenetApps\src\main\resources\patreon.json.kms"""
        )
    }
}

