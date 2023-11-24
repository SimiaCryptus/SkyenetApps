package com.simiacryptus.skyenet.webui.servlet

import com.patreon.PatreonAPI
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
                if (tokenResponse == null) throw RuntimeException("Token response is null")
                log.info("Token response: $tokenResponse")
                val tokenData = JsonUtil.fromJson<TokenResponse>(tokenResponse, TokenResponse::class.java)
                val userInfo = getUserInfo(tokenData.access_token!!)
                log.info("User data: ${JsonUtil.toJson(userInfo)}")
                val user = User(
                    email = userInfo.email!!,
                    name = userInfo.fullName,
                    picture = userInfo.thumbUrl,
                )
                val accessToken = UUID.randomUUID().toString()
                ApplicationServices.authenticationManager.putUser(accessToken = accessToken, user = user)
                log.info("User $user logged in with session $accessToken")
                val sessionCookie = Cookie(AuthenticationManager.AUTH_COOKIE, accessToken)
                sessionCookie.path = "/"
                sessionCookie.isHttpOnly = true
                sessionCookie.secure = true
                sessionCookie.maxAge = TimeUnit.HOURS.toSeconds(1).toInt()
                sessionCookie.comment = "Authentication Session ID"
                resp.addCookie(sessionCookie)
                val redirect = req.getParameter("state")?.urlDecode() ?: "/"
                resp.sendRedirect(redirect)
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Authorization code not found")
            }
        }
    }

    private fun getUserInfo(authorizationToken: String): PatreonUserInfo {
        Request.create(Method.GET, URI("https://www.patreon.com/api/oauth2/api/current_user"))
            .addHeader("authorization", "Bearer $authorizationToken")
            .execute().returnContent().asString().also {
                log.info("User info: $it")
                return JsonUtil.fromJson(it, PatreonUserInfo::class.java)
            }
    }

    data class PatreonUserInfo(
        val fullName: String? = null,
        val discordId: String? = null,
        val twitch: String? = null,
        val vanity: String? = null,
        val email: String? = null,
        val about: String? = null,
        val facebookId: String? = null,
        val imageUrl: String? = null,
        val thumbUrl: String? = null,
        val youtube: String? = null,
        val twitter: String? = null,
        val facebook: String? = null,
        val created: Date? = null,
        val url: String? = null,
        val isEmailVerified: Boolean? = null,
        val likeCount: Int? = null,
        val commentCount: Int? = null,
        val pledges: List<Pledge>? = null
    )

    data class Pledge(
        val amountCents: Int? = null,
        val createdAt: String? = null,
        val declinedSince: String? = null,
        val patronPaysFees: Boolean? = null,
        val pledgeCapCents: Int? = null,
        val totalHistoricalAmountCents: Int? = null,
        val isPaused: Boolean? = null,
        val hasShippingAddress: Boolean? = null,
        val creator: User? = null,
        val patron: User? = null,
        val reward: Reward? = null,
    )
    data class Reward(
        val amount_cents: Int? = null,
        val created_at: String? = null,
        val description: String? = null,
        val remaining: Float? = null,
        val requires_shipping: Boolean? = null,
        val url: String? = null,
        val user_limit: Int? = null,
        val edited_at: String? = null,
        val patron_count: Int? = null,
        val published: Boolean? = null,
        val published_at: String? = null,
        val image_url: String? = null,
        val discord_role_ids: List<String>? = null,
        val title: String? = null,
        val unpublished_at: String? = null,
    )

    data class TokenResponse(
        val access_token: String? = null,
        val expires_in: Long? = null,
        val token_type: String? = null,
        val scope: String? = null,
        val refresh_token: String? = null,
        val version: String? = null,
    )
    data class PatreonOAuthInfo(
        val name: String? = null,
        val clientId: String? = null,
        val apiVersion: String? = null,
        val clientSecret: String? = null,
        val creatorAccessToken: String? = null,
        val creatorRefreshToken: String? = null
    )

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(OAuthPatreon::class.java)
    }

}

