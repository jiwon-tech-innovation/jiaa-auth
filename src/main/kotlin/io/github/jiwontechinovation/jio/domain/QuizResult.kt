package io.github.jiwontechinovation.jio.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "quiz_results")
data class QuizResult(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val topic: String,

    @Column(nullable = false)
    val score: Int,

    @Column(name = "max_score", nullable = false)
    val maxScore: Int,

    @Column(name = "percentage")
    val percentage: Double = if (maxScore > 0) (score.toDouble() / maxScore) * 100 else 0.0,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
