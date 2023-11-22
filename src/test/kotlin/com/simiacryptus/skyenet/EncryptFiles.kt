package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.core.util.AwsUtil

object EncryptFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        AwsUtil.encryptFile(
            "E:\\backup\\winhome\\openai.key",
            "C:\\Users\\andre\\code\\AwsAgent\\src\\main\\resources\\openai.key.kms"
        )
    }
}