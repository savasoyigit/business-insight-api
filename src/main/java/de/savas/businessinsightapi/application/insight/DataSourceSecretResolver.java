package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.springframework.stereotype.Component;

@Component
public class DataSourceSecretResolver {

    public String resolve(DataSource dataSource) {
        String secretRef = dataSource.getSecretRef();
        if (secretRef == null || secretRef.isBlank()) {
            throw new BadRequestException("DataSource secretRef is required");
        }
        if (!secretRef.startsWith("env:")) {
            throw new BadRequestException("Only env-based secretRef values are supported");
        }

        String envKey = secretRef.substring(4);
        String secret = System.getenv(envKey);
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Missing datasource secret in environment: " + envKey);
        }
        return secret;
    }
}
