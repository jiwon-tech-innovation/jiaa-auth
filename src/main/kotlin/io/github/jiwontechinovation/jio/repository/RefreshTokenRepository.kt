package io.github.jiwontechinovation.jio.repository

import io.github.jiwontechinovation.jio.domain.RefreshToken
import io.github.jiwontechinovation.jio.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByToken(token: String): Optional<RefreshToken>
    fun deleteByToken(token: String)
    fun deleteByUser(user: User)
    
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    fun deleteExpiredTokens(now: Instant)
}
