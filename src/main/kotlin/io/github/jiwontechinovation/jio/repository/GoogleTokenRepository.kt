package io.github.jiwontechinovation.jio.repository

import io.github.jiwontechinovation.jio.domain.GoogleToken
import io.github.jiwontechinovation.jio.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface GoogleTokenRepository : JpaRepository<GoogleToken, Long> {
    fun findByUser(user: User): Optional<GoogleToken>
    fun findByUserId(userId: Long): Optional<GoogleToken>
}
