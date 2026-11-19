package de.savas.businessinsightapi.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Access to actuator endpoints other than health: users with {@link #PERMISSION}, or, for the Prometheus endpoint
 * only, a scraper presenting the static {@code METRICS_SCRAPE_TOKEN} as a bearer token (JWTs expire too quickly
 * for a scrape config). Without a configured token, only the permission grants access.
 */
@Component
public class MetricsAccessManager {

    public static final String PERMISSION = "OPS_METRICS_ACCESS";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final byte[] scrapeToken;

    public MetricsAccessManager(@Value("${app.metrics.scrape-token:}") String scrapeToken) {
        String token = scrapeToken == null ? "" : scrapeToken.trim();
        if (!token.isEmpty() && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException("METRICS_SCRAPE_TOKEN must be at least " + MIN_TOKEN_LENGTH + " characters");
        }
        this.scrapeToken = token.isEmpty() ? null : ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    /** For /actuator/prometheus: the scrape token or the permission. */
    public AuthorizationManager<RequestAuthorizationContext> scrapeOrPermission() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
                return new AuthorizationDecision(hasScrapeToken(context.getRequest()) || hasPermission(authentication.get()));
            }
        };
    }

    private boolean hasScrapeToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return scrapeToken != null && header != null
                && MessageDigest.isEqual(scrapeToken, header.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasPermission(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(PERMISSION::equals);
    }
}
