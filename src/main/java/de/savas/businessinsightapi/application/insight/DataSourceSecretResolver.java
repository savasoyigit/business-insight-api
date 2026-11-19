package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.infrastructure.security.SecretCipher;
import org.springframework.stereotype.Component;

/**
 * Resolves a datasource password from its secretRef:
 * {@code env:DS_SECRET_...} reads an environment variable with the allowed prefix
 * (see {@link DataSourceConnectionProperties#envSecretPrefix()}), {@code enc:v1:...} is decrypted with {@link SecretCipher}.
 */
@Component
public class DataSourceSecretResolver {

    private final SecretCipher cipher;
    private final DataSourceConnectionPolicy policy;

    public DataSourceSecretResolver(SecretCipher cipher, DataSourceConnectionPolicy policy) {
        this.cipher = cipher;
        this.policy = policy;
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

        // Checked again here, not only when saved: rows written before the prefix rule must not reach other variables.
        String envKey = policy.validateEnvSecretRef(secretRef).substring(4);
        String secret = System.getenv(envKey);
        if (secret == null || secret.isBlank()) {
            // Deliberately does not name the variable or say whether it exists.
            throw new BadRequestException("Datasource secret is not available on this server");
        }
        return secret;
    }
}
