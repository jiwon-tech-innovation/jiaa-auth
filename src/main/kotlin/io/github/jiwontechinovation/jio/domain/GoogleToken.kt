package io.github.jiwontechinovation.jio.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "google_tokens")
data class GoogleToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    var user: User,

    @Column(name = "google_email", unique = true)
    var googleEmail: String? = null,

    @Column(name = "access_token", nullable = false, length = 2048)
    var accessToken: String,

    @Column(name = "refresh_token", length = 2048)
    var refreshToken: String? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
)
