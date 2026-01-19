package io.github.jiwontechinovation.jio.repository

import io.github.jiwontechinovation.jio.domain.GoogleToken
import io.github.jiwontechinovation.jio.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface GoogleTokenRepository : JpaRepository<GoogleToken, UUID> {
    fun findByUser(user: User): Optional<GoogleToken>
    
    @Query("SELECT gt FROM GoogleToken gt WHERE gt.user.id = :userId")
    fun findByUserId(@Param("userId") userId: UUID): Optional<GoogleToken>
    
    fun findByGoogleEmail(googleEmail: String): Optional<GoogleToken>
}
