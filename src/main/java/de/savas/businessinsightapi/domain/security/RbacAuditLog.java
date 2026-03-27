package de.savas.businessinsightapi.domain.security;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rbac_audit_log")
public class RbacAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", nullable = false, length = 120)
    private String actorUsername;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id", length = 120)
    private String targetId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected RbacAuditLog() {
    }

    public RbacAuditLog(String actorUsername, String action, String targetType, String targetId, String correlationId, JsonNode details) {
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.correlationId = correlationId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public JsonNode getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
