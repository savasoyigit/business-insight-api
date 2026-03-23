package de.savas.businessinsightapi.domain.insight.dashboard;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dashboard",
        uniqueConstraints = @UniqueConstraint(name = "uk_dashboard_org_name", columnNames = {"organization_id", "name"}))
public class Dashboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Dashboard() {
    }

    public Dashboard(Long organizationId, String name, String createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
}
