package de.savas.businessinsightapi.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.hardening")
public record SecurityHardeningProperties(
        int loginRateLimitPerMinute,
        int maxFailedLoginAttempts,
        int lockoutMinutes,
        int minPasswordLength
) {
}
