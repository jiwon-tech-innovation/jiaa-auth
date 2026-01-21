package io.github.jiwontechinovation.jio.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val token: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
