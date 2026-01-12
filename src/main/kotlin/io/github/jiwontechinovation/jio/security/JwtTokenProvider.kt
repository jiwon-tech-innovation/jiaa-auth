package io.github.jiwontechinovation.jio.security

import io.github.jiwontechinovation.jio.domain.User
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}")
    private val jwtSecret: String,
    
    @Value("\${jwt.access-expiration}")
    private val accessExpirationMs: Long,
    
    @Value("\${jwt.refresh-expiration}")
    private val refreshExpirationMs: Long
) {
    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)
    
    private val key: SecretKey by lazy {
        val keyBytes = if (jwtSecret.length < 64) {
            // Pad key if too short (for development)
            jwtSecret.padEnd(64, 'x').toByteArray()
        } else {
            jwtSecret.toByteArray()
        }
        Keys.hmacShaKeyFor(keyBytes)
    }
    
    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + accessExpirationMs)
        
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }
    
    fun generateRefreshToken(): String {
        return UUID.randomUUID().toString()
    }
    
    fun getRefreshExpirationMs(): Long = refreshExpirationMs
    
    fun getUserIdFromToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        
        return claims.subject.toLong()
    }
    
    fun getEmailFromToken(token: String): String {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        
        return claims["email"] as String
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (ex: SecurityException) {
            logger.error("Invalid JWT signature: ${ex.message}")
            false
        } catch (ex: MalformedJwtException) {
            logger.error("Invalid JWT token: ${ex.message}")
            false
        } catch (ex: ExpiredJwtException) {
            logger.error("Expired JWT token: ${ex.message}")
            false
        } catch (ex: UnsupportedJwtException) {
            logger.error("Unsupported JWT token: ${ex.message}")
            false
        } catch (ex: IllegalArgumentException) {
            logger.error("JWT claims string is empty: ${ex.message}")
            false
        }
    }
}
