package com.simiacryptus.skyenet.apps.coding

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.simiacryptus.jopenai.API
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer

open class CodingApp(
    applicationName: String,
    temperature: Double = 0.1,
) : ApplicationServer(
    applicationName = applicationName,
    temperature = temperature,
) {
    override fun newSession(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        CodingAgent(
            api = api,
            dataStorage = dataStorage,
            session = session,
            user = user,
            ui = ui,
            interpreter = KotlinInterpreter::class,
            symbols = mapOf(
                // Region
                "region" to DefaultAwsRegionProviderChain().getRegion(),
                // AWSCredentialsProvider
                "credentials" to DefaultAWSCredentialsProviderChain.getInstance(),
            ),
        ).start(
            userMessage = userMessage,
        )
    }
}