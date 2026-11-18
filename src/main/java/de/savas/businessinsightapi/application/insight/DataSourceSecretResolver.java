package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.infrastructure.security.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * Resolves a datasource password from its secretRef:
 * {@code env:VAR} reads an environment variable, {@code enc:v1:...} is decrypted with {@link SecretCipher}.
 */
@Component
public class DataSourceSecretResolver {

    private final SecretCipher cipher;

    public DataSourceSecretResolver(SecretCipher cipher) {
        this.cipher = cipher;
    }

    public String resolve(DataSource dataSource) {
        String secretRef = dataSource.getSecretRef();
        if (secretRef == null || secretRef.isBlank()) {
            throw new BadRequestException("DataSource secretRef is required");
        }
        if (SecretCipher.isEncrypted(secretRef)) {
            try {
                return cipher.decrypt(secretRef);
            } catch (IllegalStateException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        }
        if (!secretRef.startsWith("env:")) {
            throw new BadRequestException("Unsupported secretRef; use env:VAR_NAME or send a password");
        }

        String envKey = secretRef.substring(4);
        String secret = System.getenv(envKey);
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Missing datasource secret in environment: " + envKey);
        }
        return secret;
    }
}
