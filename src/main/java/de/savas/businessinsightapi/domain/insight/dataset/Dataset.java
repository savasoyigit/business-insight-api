package de.savas.businessinsightapi.domain.insight.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dataset",
        uniqueConstraints = @UniqueConstraint(name = "uk_dataset_org_name", columnNames = {"organization_id", "name"}))
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "query_template_id", nullable = false)
    private Long queryTemplateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_params", nullable = false, columnDefinition = "jsonb")
    private JsonNode defaultParams;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Dataset() {
    }

    public Dataset(Long organizationId, String name, Long queryTemplateId, JsonNode defaultParams, String createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.queryTemplateId = queryTemplateId;
        this.defaultParams = defaultParams;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public Long getQueryTemplateId() { return queryTemplateId; }
    public JsonNode getDefaultParams() { return defaultParams; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setQueryTemplateId(Long queryTemplateId) { this.queryTemplateId = queryTemplateId; }
    public void setDefaultParams(JsonNode defaultParams) { this.defaultParams = defaultParams; }
}
