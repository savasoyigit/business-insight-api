package de.savas.businessinsightapi.api.insight;

import com.fasterxml.jackson.databind.JsonNode;
import de.savas.businessinsightapi.application.insight.DashboardService;
import de.savas.businessinsightapi.domain.insight.dashboard.Dashboard;
import de.savas.businessinsightapi.domain.insight.dashboard.DashboardWidget;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public class DashboardDtos {

    public record DashboardWidgetRequest(
            Long chartId,
            JsonNode layout
    ) {
        public DashboardService.DashboardWidgetInput toInput() {
            return new DashboardService.DashboardWidgetInput(chartId, layout);
        }
    }

    public record CreateDashboardRequest(
            @NotBlank String name,
            List<DashboardWidgetRequest> widgets
    ) {
    }

    public record UpdateDashboardRequest(
            String name,
            List<DashboardWidgetRequest> widgets
    ) {
    }

    public record DashboardWidgetResponse(
            Long id,
            Long chartId,
            JsonNode layout,
            OffsetDateTime createdAt
    ) {
        public static DashboardWidgetResponse from(DashboardWidget widget) {
            return new DashboardWidgetResponse(
                    widget.getId(),
                    widget.getChartId(),
                    widget.getLayout(),
                    widget.getCreatedAt()
            );
        }
    }

    public record DashboardResponse(
            Long id,
            String name,
            String createdBy,
            OffsetDateTime createdAt,
            List<DashboardWidgetResponse> widgets
    ) {
        public static DashboardResponse from(DashboardService.DashboardDetails details) {
            Dashboard dashboard = details.dashboard();
            return new DashboardResponse(
                    dashboard.getId(),
                    dashboard.getName(),
                    dashboard.getCreatedBy(),
                    dashboard.getCreatedAt(),
                    details.widgets().stream().map(DashboardWidgetResponse::from).toList()
            );
        }
    }
}
