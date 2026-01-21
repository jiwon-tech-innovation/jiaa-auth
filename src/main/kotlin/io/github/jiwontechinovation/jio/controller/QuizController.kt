package io.github.jiwontechinovation.jio.controller

import io.github.jiwontechinovation.jio.domain.QuizResult
import io.github.jiwontechinovation.jio.domain.User
import io.github.jiwontechinovation.jio.repository.QuizResultRepository
import io.github.jiwontechinovation.jio.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/quiz")
class QuizController(
    private val quizResultRepository: QuizResultRepository
) {

    @PostMapping("/submit")
    fun submitQuiz(
        @CurrentUser user: User,
        @RequestBody result: QuizResultDto
    ): ResponseEntity<Map<String, Boolean>> {
        
        // Save to Database
        val quizResult = QuizResult(
            user = user,
            topic = result.topic ?: "Unknown",
            score = result.score,
            maxScore = result.maxScore ?: result.score // Fallback if maxScore not provided
        )
        quizResultRepository.save(quizResult)
        
        println("[QuizController] Saved quiz result: ${quizResult.topic} - ${quizResult.score}/${quizResult.maxScore}")
        
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/daily")
    fun getDailyQuizResults(
        @CurrentUser user: User,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<Map<String, Any>> {
        
        val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
        val startOfDay = targetDate.atStartOfDay()
        val endOfDay = targetDate.plusDays(1).atStartOfDay()

        val results = quizResultRepository.findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            user, startOfDay, endOfDay
        )

        val response = results.map { r ->
            mapOf(
                "topic" to r.topic,
                "score" to r.score,
                "maxScore" to r.maxScore,
                "percentage" to r.percentage,
                "createdAt" to r.createdAt.toString()
            )
        }

        return ResponseEntity.ok(mapOf("success" to true, "data" to response))
    }
}

data class QuizResultDto(
    val topic: String?,
    val score: Int,
    val maxScore: Int?,
    val wrong: List<String>? = null // For legacy compatibility
)
