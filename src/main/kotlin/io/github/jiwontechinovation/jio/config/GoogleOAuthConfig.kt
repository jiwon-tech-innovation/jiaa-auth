package io.github.jiwontechinovation.jio.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "google")
data class GoogleOAuthConfig(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = "",
    var scopes: String = ""
)
