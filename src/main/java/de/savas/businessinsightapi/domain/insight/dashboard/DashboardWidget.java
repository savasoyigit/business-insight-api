package de.savas.businessinsightapi.domain.insight.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dashboard_widget")
public class DashboardWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dashboard_id", nullable = false)
    private Long dashboardId;

    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode layout;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DashboardWidget() {
    }

    public DashboardWidget(Long dashboardId, Long chartId, JsonNode layout) {
        this.dashboardId = dashboardId;
        this.chartId = chartId;
        this.layout = layout;
    }

    public Long getId() { return id; }
    public Long getDashboardId() { return dashboardId; }
    public Long getChartId() { return chartId; }
    public JsonNode getLayout() { return layout; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setChartId(Long chartId) { this.chartId = chartId; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
}
