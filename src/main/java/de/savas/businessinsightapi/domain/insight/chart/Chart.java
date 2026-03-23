package de.savas.businessinsightapi.domain.insight.chart;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chart",
        uniqueConstraints = @UniqueConstraint(name = "uk_chart_org_name", columnNames = {"organization_id", "name"}))
public class Chart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChartType type;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode config;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Chart() {
    }

    public Chart(Long organizationId, String name, ChartType type, Long datasetId, JsonNode config, String createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.type = type;
        this.datasetId = datasetId;
        this.config = config;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public ChartType getType() { return type; }
    public Long getDatasetId() { return datasetId; }
    public JsonNode getConfig() { return config; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setType(ChartType type) { this.type = type; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public void setConfig(JsonNode config) { this.config = config; }
}
