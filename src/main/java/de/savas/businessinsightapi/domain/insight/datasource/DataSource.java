package de.savas.businessinsightapi.domain.insight.datasource;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "data_source",
        uniqueConstraints = @UniqueConstraint(name = "uk_data_source_org_name", columnNames = {"organization_id", "name"})
)
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DataSourceType type;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 255)
    private String databaseName;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "secret_ref", nullable = false, length = 255)
    private String secretRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private com.fasterxml.jackson.databind.JsonNode options;

    @Column(name="is_active", nullable = false)
    private boolean active = true;

    @Column(name="created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name="updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DataSource() {}

    public DataSource(Long organizationId,
                      String name,
                      DataSourceType type,
                      String host,
                      Integer port,
                      String databaseName,
                      String username,
                      String secretRef,
                      com.fasterxml.jackson.databind.JsonNode options) {
        this.organizationId = organizationId;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.secretRef = secretRef;
        this.options = options;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public DataSourceType getType() { return type; }
    public String getHost() { return host; }
    public Integer getPort() { return port; }
    public String getDatabaseName() { return databaseName; }
    public String getUsername() { return username; }
    public String getSecretRef() { return secretRef; }
    public com.fasterxml.jackson.databind.JsonNode getOptions() { return options; }
    public boolean isActive() { return active; }

    public void setName(String name) { this.name = name; }
    public void setType(DataSourceType type) { this.type = type; }
    public void setHost(String host) { this.host = host; }
    public void setPort(Integer port) { this.port = port; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public void setUsername(String username) { this.username = username; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public void setOptions(com.fasterxml.jackson.databind.JsonNode options) { this.options = options; }
    public void setActive(boolean active) { this.active = active; }
}
