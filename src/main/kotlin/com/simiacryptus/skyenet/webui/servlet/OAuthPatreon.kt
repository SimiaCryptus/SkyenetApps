package com.simiacryptus.skyenet.webui.servlet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.AuthenticationInterface
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
                val attributes = userInfo.data?.attributes
                val user = User(
                    email = attributes?.email!!,
                    name = attributes.full_name,
                    picture = attributes.image_url,
                )
                val accessToken = UUID.randomUUID().toString()
                ApplicationServices.authenticationManager.putUser(accessToken = accessToken, user = user)
                log.info("User $user logged in with session $accessToken")
                val sessionCookie = Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
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

    data class PatreonOAuthInfo(
        val name: String? = null,
        val clientId: String? = null,
        val apiVersion: String? = null,
        val clientSecret: String? = null,
        val creatorAccessToken: String? = null,
        val creatorRefreshToken: String? = null
    )

    data class TokenResponse(
        val access_token: String,
        val expires_in: Long,
        val token_type: String,
        val scope: String,
        val refresh_token: String,
        val version: String,
    )
    data class PatreonUserInfo(
        val data: Data? = null,
        val included: List<Included>? = null,
        val links: Links? = null
    )

    data class Data(
        val attributes: Attributes? = null,
        val id: String? = null,
        val relationships: Relationships? = null,
        val type: String? = null
    )

    data class Attributes(
        val about: String? = null,
        val age_verification_status: String? = null,
        val apple_id: String? = null,
        val can_see_nsfw: Boolean? = null,
        val created: String? = null,
        val current_user_block_status: String? = null,
        val default_country_code: String? = null,
        val discord_id: String? = null,
        val email: String? = null,
        val facebook: String? = null,
        val facebook_id: String? = null,
        val first_name: String? = null,
        val full_name: String? = null,
        val gender: Int? = null,
        val google_id: String? = null,
        val has_password: Boolean? = null,
        val image_url: String? = null,
        val is_deleted: Boolean? = null,
        val is_email_verified: Boolean? = null,
        val is_nuked: Boolean? = null,
        val is_suspended: Boolean? = null,
        val last_name: String? = null,
        val patron_currency: String? = null,
        val social_connections: SocialConnections? = null,
        val thumb_url: String? = null,
        val twitch: String? = null,
        val twitter: String? = null,
        val url: String? = null,
        val vanity: String? = null,
        val youtube: String? = null
    )

    data class SocialConnections(
        val discord: String? = null,
        val facebook: String? = null,
        val google: String? = null,
        val instagram: String? = null,
        val reddit: String? = null,
        val spotify: String? = null,
        val spotify_open_access: String? = null,
        val twitch: String? = null,
        val twitter: String? = null,
        val vimeo: String? = null,
        val youtube: String? = null
    )

    data class Relationships(
        val campaign: CampaignRelationship? = null,
        val pledges: PledgesRelationship? = null
    )

    data class CampaignRelationship(
        val data: CampaignData? = null,
        val links: CampaignLinks? = null
    )

    data class CampaignData(
        val id: String? = null,
        val type: String? = null
    )

    data class CampaignLinks(
        val related: String? = null
    )

    data class PledgesRelationship(
        val data: List<PledgeData>? = null
    )

    data class PledgeData(
        val id: String? = null,
        val type: String? = null
    )

    data class Included(
        val attributes: IncludedAttributes? = null,
        val id: String? = null,
        val relationships: IncludedRelationships? = null,
        val type: String? = null
    )

    data class IncludedAttributes(
        // Define all the fields you need for IncludedAttributes
        // Example:
        val amount: Int? = null,
        val amount_cents: Int? = null,
        val created_at: String? = null,
        // ... Add other fields as per your JSON structure
    )

    data class IncludedRelationships(
        // Define all the fields you need for IncludedRelationships
        // Example:
        val campaign: CampaignRelationship? = null,
        val creator: CreatorRelationship? = null
        // ... Add other fields as per your JSON structure
    )

    data class CreatorRelationship(
        val data: CreatorData? = null,
        val links: CreatorLinks? = null
    )

    data class CreatorData(
        val id: String? = null,
        val type: String? = null
    )

    data class CreatorLinks(
        val related: String? = null
    )

    data class Links(
        val self: String? = null
    )

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(OAuthPatreon::class.java)
    }

}

