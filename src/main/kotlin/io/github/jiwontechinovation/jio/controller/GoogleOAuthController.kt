package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.Role
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.repository.GoogleTokenRepository
import io.github.jiwontechinovation.jio.repository.UserRepository
import io.github.jiwontechinovation.jio.security.JwtTokenProvider
import io.github.jiwontechinovation.jio.service.GoogleOAuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth/google")
class GoogleOAuthController(
    private val googleOAuthService: GoogleOAuthService,
    private val userRepository: UserRepository,
    private val googleTokenRepository: GoogleTokenRepository,
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
    fun handleCallback(@RequestParam code: String): ResponseEntity<Map<String, Any>> {
        // 1. Exchange code for tokens
        val tokens = googleOAuthService.exchangeCodeForTokens(code)
        val accessToken = tokens["access_token"] as? String
            ?: throw IllegalStateException("No access_token received from Google")
        val refreshToken = tokens["refresh_token"] as? String
        val expiresIn = (tokens["expires_in"] as? Number)?.toInt()

        // 2. Get user info from Google
        val userInfo = googleOAuthService.getUserInfo(accessToken)
        val email = userInfo["email"] as? String
            ?: throw IllegalStateException("No email received from Google")
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

        return ResponseEntity.ok(mapOf(
            "accessToken" to appAccessToken,
            "refreshToken" to appRefreshToken,
            "expiresIn" to jwtProvider.getRefreshExpirationMs(),
            "email" to email
        ))
    }

    /**
     * Get Google OAuth connection status for current user
     */
    @GetMapping("/status")
    fun getStatus(@AuthenticationPrincipal user: User): ResponseEntity<Map<String, Any>> {
        val tokenOpt = googleTokenRepository.findByUser(user)
        val connected = tokenOpt.isPresent
        
        val emailValue: Any? = if (connected) user.email else null
        
        return ResponseEntity.ok(mapOf<String, Any>(
            "connected" to connected,
            "email" to (emailValue as? Any ?: "")
        ))
    }

    /**
     * Get Google access token for current user (for Calendar API usage)
     */
    @GetMapping("/token")
    fun getGoogleToken(@AuthenticationPrincipal user: User): ResponseEntity<Map<String, Any>> {
        val accessToken = googleOAuthService.getAccessToken(user.id ?: throw IllegalStateException("User ID is null"))
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Google token not found"))

        return ResponseEntity.ok(mapOf(
            "accessToken" to accessToken,
            "email" to user.email
        ))
    }
}
