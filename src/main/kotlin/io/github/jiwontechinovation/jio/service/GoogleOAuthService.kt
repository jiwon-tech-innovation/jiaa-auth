package io.github.jiwontechinovation.jio.service

import io.github.jiwontechinovation.jio.config.GoogleOAuthConfig
import io.github.jiwontechinovation.jio.domain.GoogleToken
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.repository.GoogleTokenRepository
import io.github.jiwontechinovation.jio.repository.UserRepository
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.time.LocalDateTime

@Service
class GoogleOAuthService(
    private val googleConfig: GoogleOAuthConfig,
    private val googleTokenRepository: GoogleTokenRepository,
    private val userRepository: UserRepository
) {
    private val restTemplate = RestTemplate()

    init {
        // Validate Google OAuth configuration on startup
        validateConfig()
    }

    /**
     * Validate that required Google OAuth configuration is present
     */
    private fun validateConfig() {
        if (googleConfig.clientId.isBlank() || googleConfig.clientId == "your-google-client-id") {
            throw IllegalStateException(
                """
                Google OAuth client_id is not configured!
                
                Please set the GOOGLE_CLIENT_ID environment variable or add it to your .env file.
                
                Steps to fix:
                1. Go to https://console.cloud.google.com/apis/credentials
                2. Create OAuth 2.0 Client ID credentials
                3. Set GOOGLE_CLIENT_ID in your .env file or docker-compose.yml
                4. Also set GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI
                
                Example .env file:
                GOOGLE_CLIENT_ID=your-actual-client-id.apps.googleusercontent.com
                GOOGLE_CLIENT_SECRET=your-actual-client-secret
                GOOGLE_REDIRECT_URI=http://localhost:8082/api/auth/google/callback
                """.trimIndent()
            )
        }
        if (googleConfig.clientSecret.isBlank() || googleConfig.clientSecret == "your-google-client-secret") {
            throw IllegalStateException(
                "Google OAuth client_secret is not configured! Please set GOOGLE_CLIENT_SECRET environment variable."
            )
        }
        if (googleConfig.redirectUri.isBlank()) {
            throw IllegalStateException(
                "Google OAuth redirect_uri is not configured! Please set GOOGLE_REDIRECT_URI environment variable."
            )
        }
    }

    /**
     * Build the Google OAuth URL for the client to open
     */
    fun buildAuthUrl(): String {
        // Double-check at runtime (in case config changed)
        if (googleConfig.clientId.isBlank()) {
            throw IllegalStateException(
                "Google OAuth client_id is missing. Please configure GOOGLE_CLIENT_ID environment variable."
            )
        }

        val params = mapOf(
            "client_id" to googleConfig.clientId,
            "redirect_uri" to googleConfig.redirectUri,
            "response_type" to "code",
            "scope" to googleConfig.scopes,
            "access_type" to "offline",  // Get refresh token
            "prompt" to "consent"  // Force consent to get refresh token
        )
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?$queryString"
    }

    /**
     * Exchange authorization code for tokens
     */
    fun exchangeCodeForTokens(code: String): Map<String, Any> {
        if (googleConfig.clientId.isBlank()) {
            throw IllegalStateException(
                "Google OAuth client_id is missing. Please configure GOOGLE_CLIENT_ID environment variable."
            )
        }
        if (googleConfig.clientSecret.isBlank()) {
            throw IllegalStateException(
                "Google OAuth client_secret is missing. Please configure GOOGLE_CLIENT_SECRET environment variable."
            )
        }

        val url = "https://oauth2.googleapis.com/token"

        val body = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", googleConfig.clientId)
            add("client_secret", googleConfig.clientSecret)
            add("redirect_uri", googleConfig.redirectUri)
            add("grant_type", "authorization_code")
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val request = HttpEntity(body, headers)
        
        try {
            val response = restTemplate.postForEntity(url, request, Map::class.java)
            
            @Suppress("UNCHECKED_CAST")
            val responseBody = response.body as Map<String, Any>?
            
            if (responseBody == null) {
                throw RuntimeException("Failed to get tokens from Google: Empty response")
            }
            
            // Check for error in response
            if (responseBody.containsKey("error")) {
                val error = responseBody["error"] as? String ?: "unknown_error"
                val errorDescription = responseBody["error_description"] as? String ?: ""
                throw RuntimeException(
                    "Google OAuth error: $error${if (errorDescription.isNotEmpty()) " - $errorDescription" else ""}"
                )
            }
            
            return responseBody
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            val errorBody = try {
                e.responseBodyAsString
            } catch (ex: Exception) {
                "Unknown error"
            }
            throw RuntimeException(
                "Failed to exchange code for tokens from Google: ${e.statusCode} - $errorBody",
                e
            )
        }
    }

    /**
     * Get user info from Google (email) using access token
     */
    fun getUserInfo(accessToken: String): Map<String, Any> {
        val url = "https://www.googleapis.com/oauth2/v2/userinfo"
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
        val request = HttpEntity<Any>(headers)
        val response = restTemplate.exchange(url, HttpMethod.GET, request, Map::class.java)

        @Suppress("UNCHECKED_CAST")
        return response.body as Map<String, Any>? ?: throw RuntimeException("Failed to get user info")
    }

    /**
     * Save or update Google tokens for a user
     */
    fun saveTokens(user: User, accessToken: String, refreshToken: String?, expiresInSeconds: Int?) {
        val expiresAt = expiresInSeconds?.let { LocalDateTime.now().plusSeconds(it.toLong()) }

        val existingToken = googleTokenRepository.findByUser(user)
        if (existingToken.isPresent) {
            val token = existingToken.get()
            token.accessToken = accessToken
            if (refreshToken != null) token.refreshToken = refreshToken
            token.expiresAt = expiresAt
            token.updatedAt = LocalDateTime.now()
            googleTokenRepository.save(token)
        } else {
            googleTokenRepository.save(GoogleToken(
                user = user,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt
            ))
        }
    }

    /**
     * Get access token for a user (refresh if expired)
     */
    fun getAccessToken(userId: Long): String? {
        val tokenOpt = googleTokenRepository.findByUserId(userId)
        if (tokenOpt.isEmpty) return null

        val token = tokenOpt.get()

        // Check if expired
        if (token.expiresAt != null && LocalDateTime.now().isAfter(token.expiresAt)) {
            // Refresh the token
            val refreshToken = token.refreshToken ?: return null
            val newTokens = refreshAccessToken(refreshToken)

            token.accessToken = newTokens["access_token"] as String
            (newTokens["expires_in"] as? Int)?.let {
                token.expiresAt = LocalDateTime.now().plusSeconds(it.toLong())
            }
            token.updatedAt = LocalDateTime.now()
            googleTokenRepository.save(token)
        }

        return token.accessToken
    }

    /**
     * Refresh access token using refresh token
     */
    private fun refreshAccessToken(refreshToken: String): Map<String, Any> {
        if (googleConfig.clientId.isBlank() || googleConfig.clientSecret.isBlank()) {
            throw IllegalStateException(
                "Google OAuth credentials are missing. Please configure GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET."
            )
        }

        val url = "https://oauth2.googleapis.com/token"

        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_id", googleConfig.clientId)
            add("client_secret", googleConfig.clientSecret)
            add("refresh_token", refreshToken)
            add("grant_type", "refresh_token")
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val request = HttpEntity(body, headers)
        
        try {
            val response = restTemplate.postForEntity(url, request, Map::class.java)
            
            @Suppress("UNCHECKED_CAST")
            val responseBody = response.body as Map<String, Any>?
            
            if (responseBody == null) {
                throw RuntimeException("Failed to refresh token from Google: Empty response")
            }
            
            // Check for error in response
            if (responseBody.containsKey("error")) {
                val error = responseBody["error"] as? String ?: "unknown_error"
                val errorDescription = responseBody["error_description"] as? String ?: ""
                throw RuntimeException(
                    "Google OAuth refresh error: $error${if (errorDescription.isNotEmpty()) " - $errorDescription" else ""}"
                )
            }
            
            return responseBody
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            val errorBody = try {
                e.responseBodyAsString
            } catch (ex: Exception) {
                "Unknown error"
            }
            throw RuntimeException(
                "Failed to refresh token from Google: ${e.statusCode} - $errorBody",
                e
            )
        }
    }
}
