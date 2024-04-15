package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.webui.servlet.OAuthPatreon
import java.nio.file.Files
import java.nio.file.Paths

object EncryptFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        val encryptedData = ApplicationServices.cloud!!.encrypt(
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
            "arn:aws:kms:us-east-1:470240306861:key/a1340b89-64e6-480c-a44c-e7bc0c70dcb1"
        ) ?: throw RuntimeException("Unable to encrypt data")
        Files.write(
            Paths.get(
                """C:\Users\andre\code\SkyenetApps\src\main\resources\patreon.json.kms"""
            ), encryptedData.toByteArray()
        )
    }
}

