package io.github.jiwontechinovation.jio.service

import io.github.jiwontechinovation.jio.domain.RefreshToken
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.dto.*
import io.github.jiwontechinovation.jio.repository.RefreshTokenRepository
import io.github.jiwontechinovation.jio.repository.UserRepository
import io.github.jiwontechinovation.jio.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun signup(request: SignupRequest): UserResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }
        
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password)!!
        )
        
        val savedUser = userRepository.save(user)
        
        return UserResponse(
            id = savedUser.id,
            email = savedUser.email,
            role = savedUser.role.name
        )
    }

    @Transactional
    fun signin(request: SigninRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalArgumentException("Invalid email or password") }
        
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }
        
        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val refreshTokenValue = jwtTokenProvider.generateRefreshToken()
        
        // Save refresh token
        val refreshToken = RefreshToken(
            token = refreshTokenValue,
            user = user,
            expiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMs())
        )
        refreshTokenRepository.save(refreshToken)
        
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshTokenValue,
            expiresIn = 900 // 15 minutes in seconds
        )
    }

    @Transactional
    fun refresh(request: RefreshRequest): TokenResponse {
        val storedToken = refreshTokenRepository.findByToken(request.refreshToken)
            .orElseThrow { IllegalArgumentException("Invalid refresh token") }
        
        if (storedToken.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken)
            throw IllegalArgumentException("Refresh token expired")
        }
        
        val user = storedToken.user
        val newAccessToken = jwtTokenProvider.generateAccessToken(user)
        val newRefreshTokenValue = jwtTokenProvider.generateRefreshToken()
        
        // Rotate refresh token (delete old, create new)
        refreshTokenRepository.delete(storedToken)
        val newRefreshToken = RefreshToken(
            token = newRefreshTokenValue,
            user = user,
            expiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshExpirationMs())
        )
        refreshTokenRepository.save(newRefreshToken)
        
        return TokenResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshTokenValue,
            expiresIn = 900
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenRepository.deleteByToken(refreshToken)
    }
    
    fun getCurrentUser(user: User): UserResponse {
        return UserResponse(
            id = user.id,
            email = user.email,
            role = user.role.name
        )
    }

    @Transactional
    fun updatePassword(user: User, newPassword: String) {
        user.password = passwordEncoder.encode(newPassword)!!
        userRepository.save(user)
    }
}
