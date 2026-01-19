package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.Role
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.repository.UserRepository
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
    fun getAuthUrl(): ResponseEntity<Map<String, String>> {
        val url = googleOAuthService.buildAuthUrl()
        return ResponseEntity.ok(mapOf("url" to url))
    }

    /**
     * Handle OAuth callback - exchange code for tokens, create/find user, return JWT
     */
    @GetMapping("/callback")
    fun handleCallback(
        @RequestParam code: String,
        @io.github.jiwontechinovation.jio.security.CurrentUser currentUser: User?
    ): ResponseEntity<Map<String, Any>> {
        // 1. Exchange code for tokens
        val tokens = googleOAuthService.exchangeCodeForTokens(code)
        val accessToken = tokens["access_token"] as? String
            ?: throw IllegalStateException("No access_token received from Google")
        val refreshToken = tokens["refresh_token"] as? String
        val expiresIn = (tokens["expires_in"] as? Number)?.toInt()

        // 2. Get user info from Google
        val userInfo = googleOAuthService.getUserInfo(accessToken)
        val googleEmail = userInfo["email"] as? String
            ?: throw IllegalStateException("No email received from Google")
        val name = userInfo["name"] as? String

        // 3. Resolve user (Link or Login)
        val user = if (currentUser != null) {
            // mode 1: Linking (User is already authenticated)
            currentUser
        } else {
            // mode 2: Login/Signup
            // 2.1 Try to find user who has this Google account linked
            googleOAuthService.findUserByGoogleEmail(googleEmail)
                ?: userRepository.findByEmail(googleEmail).orElseGet {
                    // 2.2 Not linked, not existing email -> Create new user
                    userRepository.save(User(
                        email = googleEmail,
                        name = name,
                        password = passwordEncoder.encode(UUID.randomUUID().toString())!!,
                        role = Role.USER
                    ))
                }
        }

        // 3.1 Update name if missing but available now
        if (user.name == null && name != null) {
            user.name = name
            userRepository.save(user)
        }

        // 4. Save Google tokens (Linking happens here)
        googleOAuthService.saveTokens(user, accessToken, refreshToken, expiresIn, googleEmail)

        // 5. Generate app JWT
        val appAccessToken = jwtProvider.generateAccessToken(user)
        val appRefreshToken = jwtProvider.generateRefreshToken()

        return ResponseEntity.ok(mapOf(
            "accessToken" to appAccessToken,
            "refreshToken" to appRefreshToken,
            "expiresIn" to jwtProvider.getRefreshExpirationMs(),
            "email" to user.email,
            "googleEmail" to googleEmail
        ))
    }

    @GetMapping("/status")
    fun getStatus(@io.github.jiwontechinovation.jio.security.CurrentUser user: User): ResponseEntity<Map<String, Any?>> {
        val googleToken = googleOAuthService.getGoogleToken(user)
        return ResponseEntity.ok(mapOf<String, Any?>(
            "connected" to (googleToken != null),
            "email" to googleToken?.googleEmail
        ))
    }

    /**
     * Get Google access token for the authenticated user (for Calendar API, etc.)
     * Refreshes token if expired
     */
    @GetMapping("/token")
    fun getToken(@io.github.jiwontechinovation.jio.security.CurrentUser user: User): ResponseEntity<Map<String, String>> {
        val accessToken = googleOAuthService.getAccessToken(user.id)
            ?: throw IllegalStateException("Google account not connected")
        return ResponseEntity.ok(mapOf("accessToken" to accessToken))
    }

    @DeleteMapping("/disconnect")
    fun disconnect(@io.github.jiwontechinovation.jio.security.CurrentUser user: User): ResponseEntity<Void> {
        googleOAuthService.disconnect(user)
        return ResponseEntity.ok().build()
    }
}
