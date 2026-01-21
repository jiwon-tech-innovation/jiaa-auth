package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.Role
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.repository.UserRepository
import io.github.jiwontechinovation.jio.security.CurrentUser
import io.github.jiwontechinovation.jio.security.JwtTokenProvider
import io.github.jiwontechinovation.jio.service.GoogleOAuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth/google")
class GoogleOAuthController(
    private val googleOAuthService: GoogleOAuthService,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder
) {
    /**
     * Get the OAuth URL for client to open
     */
    @GetMapping("/url")
    fun getAuthUrl(): ResponseEntity<Map<String, Any>> {
        return try {
            val url = googleOAuthService.buildAuthUrl()
            ResponseEntity.ok<Map<String, Any>>(mapOf<String, Any>(
                "url" to url
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "Google OAuth not configured",
                "message" to (e.message ?: ""),
                "details" to "Please configure GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, and GOOGLE_REDIRECT_URI environment variables."
            ))
        } catch (e: Exception) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "Failed to build OAuth URL",
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    /**
     * Get Google access token for the current user (for Calendar API, etc.)
     */
    @GetMapping("/token")
    fun getGoogleToken(@CurrentUser user: User): ResponseEntity<Map<String, Any>> {
        return try {
            val accessToken = googleOAuthService.getAccessToken(user.id)
                ?: return ResponseEntity.status(404).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "token_not_found",
                    "message" to "No Google token found for user. Please complete OAuth flow first.",
                    "userId" to user.id,
                    "email" to (user.email ?: "")
                ))

            ResponseEntity.ok<Map<String, Any>>(mapOf<String, Any>(
                "accessToken" to accessToken
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "configuration_error",
                "message" to (e.message ?: ""),
                "details" to "Google OAuth is not properly configured."
            ))
        } catch (e: RuntimeException) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "token_retrieval_failed",
                "message" to (e.message ?: ""),
                "details" to "Failed to retrieve or refresh Google access token."
            ))
        } catch (e: Exception) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "internal_error",
                "message" to (e.message ?: "Unknown error occurred"),
                "type" to e.javaClass.simpleName
            ))
        }
    }

    /**
     * Handle OAuth callback - exchange code for tokens, create/find user, return JWT
     */
    @GetMapping("/callback")
    fun handleCallback(@RequestParam code: String?): ResponseEntity<Map<String, Any>> {
        return try {
            // Validate code parameter
            if (code.isNullOrBlank()) {
                return ResponseEntity.status(400).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "invalid_request",
                    "error_description" to "Missing required parameter: code",
                    "message" to "Authorization code is required. Please ensure you're redirected from Google OAuth."
                ))
            }

            // 1. Exchange code for tokens
            val tokens = try {
                googleOAuthService.exchangeCodeForTokens(code)
            } catch (e: IllegalStateException) {
                return ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "configuration_error",
                    "message" to (e.message ?: ""),
                    "details" to "Google OAuth is not properly configured. Please check GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET."
                ))
            } catch (e: RuntimeException) {
                return ResponseEntity.status(400).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "token_exchange_failed",
                    "message" to (e.message ?: ""),
                    "details" to "Failed to exchange authorization code for tokens. The code may be invalid or expired."
                ))
            }

            val accessToken = tokens["access_token"] as? String
                ?: return ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "invalid_response",
                    "message" to "No access_token received from Google",
                    "response" to tokens
                ))

            val refreshToken = tokens["refresh_token"] as? String
            val expiresIn = (tokens["expires_in"] as? Number)?.toInt()

            // 2. Get user info from Google
            val userInfo = try {
                googleOAuthService.getUserInfo(accessToken)
            } catch (e: RuntimeException) {
                return ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "user_info_failed",
                    "message" to (e.message ?: ""),
                    "details" to "Failed to retrieve user information from Google."
                ))
            }

            val email = userInfo["email"] as? String
                ?: return ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                    "error" to "invalid_response",
                    "message" to "No email received from Google",
                    "response" to userInfo
                ))

            val name = userInfo["name"] as? String

            // 3. Find or create user
            val user = userRepository.findByEmail(email).orElseGet {
                userRepository.save(User(
                    email = email,
                    name = name,
                    password = passwordEncoder.encode(UUID.randomUUID().toString())!!, // Random password for OAuth users
                    role = Role.USER
                ))
            }

            // 3.1 Update name if missing but available now
            if (user.name == null && name != null) {
                user.name = name
                userRepository.save(user)
            }

            // 4. Save Google tokens
            googleOAuthService.saveTokens(user, accessToken, refreshToken, expiresIn)

            // 5. Generate app JWT
            val appAccessToken = jwtProvider.generateAccessToken(user)
            val appRefreshToken = jwtProvider.generateRefreshToken()

            ResponseEntity.ok<Map<String, Any>>(mapOf<String, Any>(
                "accessToken" to appAccessToken,
                "refreshToken" to appRefreshToken,
                "expiresIn" to jwtProvider.getRefreshExpirationMs(),
                "email" to email
            ))
        } catch (e: Exception) {
            ResponseEntity.status(500).body<Map<String, Any>>(mapOf<String, Any>(
                "error" to "internal_error",
                "message" to (e.message ?: "Unknown error occurred during OAuth callback"),
                "type" to e.javaClass.simpleName
            ))
        }
    }
}
