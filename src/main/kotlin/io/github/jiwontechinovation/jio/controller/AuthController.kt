package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.dto.*
import io.github.jiwontechinovation.jio.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<UserResponse> {
        val user = authService.signup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @PostMapping("/signin")
    fun signin(@Valid @RequestBody request: SigninRequest): ResponseEntity<TokenResponse> {
        val tokens = authService.signin(request)
        return ResponseEntity.ok(tokens)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenResponse> {
        val tokens = authService.refresh(request)
        return ResponseEntity.ok(tokens)
    }

    @PostMapping("/logout")
    fun logout(@RequestBody request: RefreshRequest): ResponseEntity<MessageResponse> {
        authService.logout(request.refreshToken)
        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }

    @GetMapping("/me")
    fun getCurrentUser(@AuthenticationPrincipal user: User): ResponseEntity<UserResponse> {
        val response = authService.getCurrentUser(user)
        return ResponseEntity.ok(response)
    }
}
