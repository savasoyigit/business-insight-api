package de.savas.businessinsightapi.api.insight;

import com.fasterxml.jackson.databind.JsonNode;
import de.savas.businessinsightapi.application.insight.ChartService;
import de.savas.businessinsightapi.domain.insight.chart.Chart;
import de.savas.businessinsightapi.domain.insight.chart.ChartType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class ChartDtos {

    public record CreateChartRequest(
            @NotBlank String name,
            @NotNull ChartType type,
            @NotNull Long datasetId,
            JsonNode config
    ) {
    }

    public record UpdateChartRequest(
            String name,
            ChartType type,
            Long datasetId,
            JsonNode config
    ) {
    }

    public record ChartResponse(
            Long id,
            String name,
            ChartType type,
            Long datasetId,
            JsonNode config,
            String createdBy,
            OffsetDateTime createdAt
    ) {
        public static ChartResponse from(Chart chart) {
            return new ChartResponse(
                    chart.getId(),
                    chart.getName(),
                    chart.getType(),
                    chart.getDatasetId(),
                    chart.getConfig(),
                    chart.getCreatedBy(),
                    chart.getCreatedAt()
            );
        }
    }

    public record RenderChartResponse(
            Long chartId,
            String type,
            Long snapshotId,
            List<Map<String, Object>> points
    ) {
        public static RenderChartResponse from(ChartService.RenderResult result) {
            return new RenderChartResponse(
                    result.chartId(),
                    result.type().name(),
                    result.snapshotId(),
                    result.points()
            );
        }
    }
}
