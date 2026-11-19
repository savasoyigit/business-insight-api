package de.savas.businessinsightapi.application.insight;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on what a datasource may point at. Datasource fields are written by organization users, so they decide
 * which host the server connects to and which secret it sends there; these settings keep that inside safe bounds.
 */
@ConfigurationProperties(prefix = "app.insight.connection")
public record DataSourceConnectionProperties(
        /**
         * {@code secretRef: env:VAR} may only name environment variables with this prefix, so a datasource cannot
         * read (and send to its host) the server's own secrets such as JWT_SECRET. Never empty.
         */
        String envSecretPrefix,
        /** Allow loopback hosts (localhost, 127.0.0.0/8, ::1). Off in production: the server's own services live there. */
        Boolean allowLoopback,
        /** Allow private networks (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7), where company databases usually are. */
        Boolean allowPrivateNetworks
) {
    public static final String DEFAULT_ENV_SECRET_PREFIX = "DS_SECRET_";

    public DataSourceConnectionProperties {
        if (envSecretPrefix == null || envSecretPrefix.isBlank()) envSecretPrefix = DEFAULT_ENV_SECRET_PREFIX;
        if (allowLoopback == null) allowLoopback = false;
        if (allowPrivateNetworks == null) allowPrivateNetworks = true;
    }

    public static DataSourceConnectionProperties defaults() {
        return new DataSourceConnectionProperties(null, null, null);
    }
}
