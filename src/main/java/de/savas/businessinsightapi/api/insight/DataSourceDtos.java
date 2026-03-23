package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
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
            @NotBlank String secretRef,
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
            String secretRef,
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
                    ds.getSecretRef(),
                    ds.getOptions(),
                    ds.isActive()
            );
        }
    }
}
