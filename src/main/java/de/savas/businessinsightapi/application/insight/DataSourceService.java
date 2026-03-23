package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceRepository;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DataSourceService {

    private final DataSourceRepository repo;

    public DataSourceService(DataSourceRepository repo) {
        this.repo = repo;
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
                             com.fasterxml.jackson.databind.JsonNode options) {
        validateConnectionFields(host, port, databaseName, username, secretRef);
        return repo.save(new DataSource(
                organizationId,
                name,
                type,
                host.trim(),
                port,
                databaseName.trim(),
                username.trim(),
                secretRef.trim(),
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
                             com.fasterxml.jackson.databind.JsonNode options,
                             Boolean active) {
        DataSource ds = get(organizationId, id);
        if (name != null) ds.setName(name);
        if (type != null) ds.setType(type);
        if (host != null) ds.setHost(requireText(host, "host"));
        if (port != null) ds.setPort(requirePort(port));
        if (databaseName != null) ds.setDatabaseName(requireText(databaseName, "databaseName"));
        if (username != null) ds.setUsername(requireText(username, "username"));
        if (secretRef != null) ds.setSecretRef(requireText(secretRef, "secretRef"));
        if (options != null) ds.setOptions(options);
        if (active != null) ds.setActive(active);
        return repo.save(ds);
    }

    private void validateConnectionFields(String host, Integer port, String databaseName, String username, String secretRef) {
        requireText(host, "host");
        requirePort(port);
        requireText(databaseName, "databaseName");
        requireText(username, "username");
        requireText(secretRef, "secretRef");
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
}
