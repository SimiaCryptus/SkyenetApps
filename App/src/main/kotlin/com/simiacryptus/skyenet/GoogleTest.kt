package com.simiacryptus.skyenet

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.security.GeneralSecurityException

object GmailQuickstart {

    private const val APPLICATION_NAME = "Gmail API Java Quickstart"
    private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    private const val TOKENS_DIRECTORY_PATH = "tokens"
    private const val CREDENTIALS_FILE_PATH = "/google-credentials.json"
    private val transport by lazy { GoogleNetHttpTransport.newTrustedTransport() }

    private val SCOPES = listOf(
        GmailScopes.GMAIL_LABELS,
        GmailScopes.GMAIL_READONLY,
        GmailScopes.MAIL_GOOGLE_COM,
    )

    @Throws(IOException::class)
    private fun getCredentials(transport: NetHttpTransport) =
        AuthorizationCodeInstalledApp(
            GoogleAuthorizationCodeFlow.Builder(
                transport,
                JSON_FACTORY,
                GoogleClientSecrets.load(
                    JSON_FACTORY,
                    getCredentialsJsonStream()
                ), SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build(), LocalServerReceiver.Builder().setPort(8888).build()
        ).authorize("user")

    private fun getCredentialsJsonStream() = InputStreamReader(
        GmailQuickstart::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
    )

    @Throws(IOException::class, GeneralSecurityException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val service = getGmailService()
        val user = "me"
        val users = service.users()
        val messageSvc = users.messages()
        val labels = users.labels().list(user).execute().labels
        labels.forEach { println(it) }
        val listRequest = messageSvc.list(user)
        val listMessagesResponse = listRequest.execute()
        val messages = listMessagesResponse.messages
        messages.forEach {
            val message = messageSvc.get(user, it.id).execute()
            message.payload.headers.forEach {
                println(it)
            }
        }
    }

    private fun getGmailService() = Gmail
        .Builder(transport, JSON_FACTORY, getCredentials(transport))
        .setApplicationName(APPLICATION_NAME)
        .build()
}