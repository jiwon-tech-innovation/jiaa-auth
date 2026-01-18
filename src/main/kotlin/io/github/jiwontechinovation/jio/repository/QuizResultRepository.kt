package io.github.jiwontechinovation.jio.repository

import io.github.jiwontechinovation.jio.domain.QuizResult
import io.github.jiwontechinovation.jio.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface QuizResultRepository : JpaRepository<QuizResult, UUID> {

    fun findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
        user: User,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<QuizResult>

    fun findByUserOrderByCreatedAtDesc(user: User): List<QuizResult>
}
