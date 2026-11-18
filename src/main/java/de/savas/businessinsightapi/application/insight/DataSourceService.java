package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceRepository;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import de.savas.businessinsightapi.infrastructure.security.SecretCipher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DataSourceService {

    private static final int MAX_PASSWORD_BYTES = 512;

    private final DataSourceRepository repo;
    private final JdbcConnectionProvider jdbcConnectionProvider;
    private final SecretCipher secretCipher;

    public DataSourceService(DataSourceRepository repo, JdbcConnectionProvider jdbcConnectionProvider, SecretCipher secretCipher) {
        this.repo = repo;
        this.jdbcConnectionProvider = jdbcConnectionProvider;
        this.secretCipher = secretCipher;
    }

    public Page<DataSource> list(Long organizationId, Pageable pageable) {
        return repo.findAllByOrganizationId(organizationId, pageable);
    }

    public DataSource get(Long organizationId, Long id) {
        DataSource ds = repo.findById(id).orElseThrow(() -> new BadRequestException("DataSource not found"));
        if (!ds.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("DataSource not found");
        }
        return ds;
    }

    public DataSource create(Long organizationId,
                             String name,
                             DataSourceType type,
                             String host,
                             Integer port,
                             String databaseName,
                             String username,
                             String secretRef,
                             String password,
                             com.fasterxml.jackson.databind.JsonNode options) {
        validateConnectionFields(host, port, databaseName, username);
        String storedSecret = toStoredSecret(secretRef, password);
        if (storedSecret == null) {
            throw new BadRequestException("secretRef or password is required");
        }
        return repo.save(new DataSource(
                organizationId,
                name,
                type,
                host.trim(),
                port,
                databaseName.trim(),
                username.trim(),
                storedSecret,
                options
        ));
    }

    public DataSource update(Long organizationId,
                             Long id,
                             String name,
                             DataSourceType type,
                             String host,
                             Integer port,
                             String databaseName,
                             String username,
                             String secretRef,
                             String password,
                             com.fasterxml.jackson.databind.JsonNode options,
                             Boolean active) {
        String storedSecret = toStoredSecret(secretRef, password);
        DataSource ds = get(organizationId, id);
        if (name != null) ds.setName(name);
        if (type != null) ds.setType(type);
        if (host != null) ds.setHost(requireText(host, "host"));
        if (port != null) ds.setPort(requirePort(port));
        if (databaseName != null) ds.setDatabaseName(requireText(databaseName, "databaseName"));
        if (username != null) ds.setUsername(requireText(username, "username"));
        if (storedSecret != null) ds.setSecretRef(storedSecret);
        if (options != null) ds.setOptions(options);
        if (active != null) ds.setActive(active);
        return repo.save(ds);
    }

    private void validateConnectionFields(String host, Integer port, String databaseName, String username) {
        requireText(host, "host");
        requirePort(port);
        requireText(databaseName, "databaseName");
        requireText(username, "username");
    }

    /**
     * Turns the request's secret into what is stored: {@code env:VAR} as-is, or a password encrypted with
     * {@link SecretCipher}. Returns null when neither is given (an update then keeps the current secret).
     */
    private String toStoredSecret(String secretRef, String password) {
        boolean hasRef = secretRef != null && !secretRef.isBlank();
        boolean hasPassword = password != null && !password.isEmpty();
        if (hasRef && hasPassword) {
            throw new BadRequestException("Provide either secretRef or password, not both");
        }
        if (hasPassword) {
            if (!secretCipher.isEnabled()) {
                throw new BadRequestException("Encrypted passwords are disabled on this server (APP_SECRETS_KEY is not set); use secretRef=env:VAR_NAME");
            }
            if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
                throw new BadRequestException("password is too long");
            }
            return secretCipher.encrypt(password);
        }
        if (hasRef) {
            String ref = secretRef.trim();
            if (!ref.startsWith("env:") || ref.length() == 4) {
                throw new BadRequestException("secretRef must look like env:VAR_NAME; send a password to store it encrypted");
            }
            return ref;
        }
        return null;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private Integer requirePort(Integer port) {
        if (port == null || port < 1 || port > 65535) {
            throw new BadRequestException("port must be between 1 and 65535");
        }
        return port;
    }
    
    public ConnectionTestResult testConnection(Long organizationId, Long id) {
        DataSource dataSource = get(organizationId, id);
        
        long startTime = System.currentTimeMillis();
        try {
            // Same read-only session setup as real queries, so the test also proves that part works.
            jdbcConnectionProvider.readOnly(dataSource, jdbcTemplate -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            long latencyMs = System.currentTimeMillis() - startTime;
            return new ConnectionTestResult(true, latencyMs, null);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return new ConnectionTestResult(false, latencyMs, e.getMessage());
        }
    }
    
    public record ConnectionTestResult(boolean ok, long latencyMs, String error) {
    }
}
