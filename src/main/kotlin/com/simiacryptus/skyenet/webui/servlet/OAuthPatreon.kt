package com.simiacryptus.skyenet.webui.servlet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationManager
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.webui.servlet.OAuthGoogle.Companion.urlDecode
import jakarta.servlet.*
import jakarta.servlet.http.*
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.Method
import org.apache.hc.core5.http.ContentType
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.net.URI
import java.util.concurrent.TimeUnit

open class OAuthPatreon(
    redirectUri: String,
    val config: PatreonOAuthInfo,
) : OAuthBase(redirectUri) {

    override fun configure(context: WebAppContext, addFilter: Boolean): WebAppContext {
        context.addServlet(ServletHolder("patreonLogin", LoginServlet()), "/login")
        context.addServlet(ServletHolder("patreonLogin", LoginServlet()), "/patreonLogin")
        context.addServlet(ServletHolder("patreonOAuth2callback", CallbackServlet()), "/patreonOAuth2callback")
        if (addFilter) context.addFilter(FilterHolder(SessionIdFilter({ request ->
            setOf("/patreonLogin", "/patreonOAuth2callback").none { request.requestURI.startsWith(it) }
        }, "/patreonLogin")), "/*", EnumSet.of(DispatcherType.REQUEST))
        return context
    }

    private val patreonAuthorizationUrl = "https://www.patreon.com/oauth2/authorize"
    private val patreonTokenUrl = "https://www.patreon.com/api/oauth2/token"
    private val patreonUserUrl = "https://www.patreon.com/api/oauth2/v2/identity?include=memberships&fields[user]=email,full_name,image_url"

    private inner class LoginServlet : HttpServlet() {
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            val redirect = req.getParameter("redirect") ?: ""
            val state = URLEncoder.encode(redirect, StandardCharsets.UTF_8.toString())
            val authorizationUrl = "$patreonAuthorizationUrl?response_type=code&client_id=${config.clientId}&redirect_uri=$redirectUri&state=$state"
            resp.sendRedirect(authorizationUrl)
        }
    }

    private inner class CallbackServlet : HttpServlet() {
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            val code = req.getParameter("code")
            if (code != null) {
                val tokenResponse = Request.create(Method.POST, URI(patreonTokenUrl))
                    .bodyString(
                        "code=$code&grant_type=authorization_code&client_id=${config.clientId}&client_secret=${config.clientSecret}&redirect_uri=$redirectUri",
                        ContentType.APPLICATION_FORM_URLENCODED
                    ).execute().returnContent().asString()
                val accessToken = extractAccessToken(tokenResponse) // Implement this function to parse the JSON response
                val userInfoResponse = Request.create(Method.GET, URI(patreonUserUrl))
                    .addHeader("Authorization", "Bearer $accessToken")
                    .execute().returnContent().asString()
                val user = parseUserInfo(userInfoResponse)
                ApplicationServices.authenticationManager.putUser(accessToken = accessToken, user = user)
                log.info("User $user logged in with session $accessToken")
                val sessionCookie = Cookie(AuthenticationManager.AUTH_COOKIE, accessToken)
                sessionCookie.path = "/"
                sessionCookie.isHttpOnly = true
                sessionCookie.secure = true
                sessionCookie.maxAge = TimeUnit.HOURS.toSeconds(1).toInt()
                sessionCookie.comment = "Authentication Session ID"
                resp.addCookie(sessionCookie)
                val redirect = req.getParameter("state")?.urlDecode()
                resp.sendRedirect(redirect ?: "/")
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Authorization code not found")
            }
        }
    }

    data class TokenResponse(
        val access_token: String,
        // ... add other fields from the token response if necessary
    )

    data class PatreonUserResponse(
        val data: UserData
    )

    data class UserData(
        val id: String,
        val attributes: UserAttributes
    )

    data class UserAttributes(
        val email: String,
        val full_name: String,
        val image_url: String
        // ... add other user attributes if necessary
    )

    private fun extractAccessToken(tokenResponse: String?): String {
        if (tokenResponse == null) throw RuntimeException("Token response is null")
        log.info("Token response: $tokenResponse")
        val tokenData = JsonUtil.fromJson<TokenResponse>(tokenResponse, TokenResponse::class.java)
        return tokenData.access_token
    }

    private fun parseUserInfo(userInfoResponse: String?): User {
        if (userInfoResponse == null) throw RuntimeException("User info response is null")
        log.info("User info response: $userInfoResponse")
        val userData = JsonUtil.fromJson<PatreonUserResponse>(userInfoResponse, PatreonUserResponse::class.java).data
        val attributes = userData.attributes
        return User(
            email = attributes.email,
            id = userData.id,
            name = attributes.full_name,
            picture = attributes.image_url
        )
    }

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(OAuthPatreon::class.java)
    }

    data class PatreonOAuthInfo(
        val name: String,
        val clientId: String,
        val apiVersion: String,
        val clientSecret: String,
        val creatorAccessToken: String,
        val creatorRefreshToken: String
    )
}

