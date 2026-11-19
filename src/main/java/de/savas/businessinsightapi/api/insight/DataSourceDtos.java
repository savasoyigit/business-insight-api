package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import de.savas.businessinsightapi.infrastructure.security.SecretCipher;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DataSourceDtos {

    public record CreateDataSourceRequest(
            @NotBlank String name,
            @NotNull DataSourceType type,
            @NotBlank String host,
            @NotNull Integer port,
            @NotBlank String databaseName,
            @NotBlank String username,
            /** {@code env:DS_SECRET_NAME}; alternatively send {@code password} to store it encrypted. */
            String secretRef,
            /** Write-only: encrypted at rest, never returned. */
            String password,
            com.fasterxml.jackson.databind.JsonNode options
    ) {}

    public record UpdateDataSourceRequest(
            String name,
            DataSourceType type,
            String host,
            Integer port,
            String databaseName,
            String username,
            String secretRef,
            String password,
            com.fasterxml.jackson.databind.JsonNode options,
            Boolean active
    ) {}

    public record DataSourceResponse(
            Long id,
            String name,
            DataSourceType type,
            String host,
            Integer port,
            String databaseName,
            String username,
            /** {@code env:DS_SECRET_NAME}, or null when the password is stored encrypted. */
            String secretRef,
            /** ENV or ENCRYPTED. */
            String secretType,
            com.fasterxml.jackson.databind.JsonNode options,
            boolean active
    ) {
        public static DataSourceResponse from(DataSource ds) {
            return new DataSourceResponse(
                    ds.getId(),
                    ds.getName(),
                    ds.getType(),
                    ds.getHost(),
                    ds.getPort(),
                    ds.getDatabaseName(),
                    ds.getUsername(),
                    SecretCipher.isEncrypted(ds.getSecretRef()) ? null : ds.getSecretRef(),
                    SecretCipher.isEncrypted(ds.getSecretRef()) ? "ENCRYPTED" : "ENV",
                    ds.getOptions(),
                    ds.isActive()
            );
        }
    }
    
    public record ConnectionTestResponse(
            boolean ok,
            long latencyMs,
            String error
    ) {
        public static ConnectionTestResponse from(de.savas.businessinsightapi.application.insight.DataSourceService.ConnectionTestResult result) {
            return new ConnectionTestResponse(
                    result.ok(),
                    result.latencyMs(),
                    result.error()
            );
        }
    }
}
