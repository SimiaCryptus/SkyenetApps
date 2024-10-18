package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.core.platform.ApplicationServices
import java.nio.file.Files
import java.nio.file.Paths

object EncryptFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        /*
        * Input: C:\Users\andre\code\GmailUtils\src\main\resources\client_secret_647304571255-5mil7mhisamqjtacvl78u1n4gap2oer6.apps.googleusercontent.com.json
        * Output: C:\Users\andre\code\GmailUtils\src\main\resources\client_secret_google_oauth.json.kms
        * */
        val encryptedData = ApplicationServices.cloud!!.encrypt(
            Files.readAllBytes(
                Paths.get(
                    """C:\Users\andre\code\GmailUtils\src\main\resources\openai.key.json"""
                )
            ),
            "arn:aws:kms:us-east-1:470240306861:key/a1340b89-64e6-480c-a44c-e7bc0c70dcb1"
        ) ?: throw RuntimeException("Unable to encrypt data")
        Files.write(
            Paths.get(
                """C:\Users\andre\code\GmailUtils\src\main\resources\openai.key.json.kms"""
            ), encryptedData.toByteArray()
        )
    }
}

