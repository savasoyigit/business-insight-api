package de.savas.businessinsightapi.api.insight;

import com.fasterxml.jackson.databind.JsonNode;
import de.savas.businessinsightapi.application.insight.QueryExecutionService;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

public class DatasetDtos {

    public record CreateDatasetRequest(
            @NotBlank String name,
            @NotNull Long queryTemplateId,
            JsonNode defaultParams
    ) {
    }

    public record UpdateDatasetRequest(
            String name,
            Long queryTemplateId,
            JsonNode defaultParams
    ) {
    }

    public record CreateSnapshotRequest(
            Map<String, Object> params
    ) {
    }

    public record DatasetResponse(
            Long id,
            String name,
            Long queryTemplateId,
            JsonNode defaultParams,
            String createdBy,
            OffsetDateTime createdAt
    ) {
        public static DatasetResponse from(Dataset dataset) {
            return new DatasetResponse(
                    dataset.getId(),
                    dataset.getName(),
                    dataset.getQueryTemplateId(),
                    dataset.getDefaultParams(),
                    dataset.getCreatedBy(),
                    dataset.getCreatedAt()
            );
        }
    }

    public record DatasetSnapshotResponse(
            Long id,
            Long queryRunId,
            Long datasetId,
            String storageFormat,
            JsonNode data,
            OffsetDateTime createdAt
    ) {
        public static DatasetSnapshotResponse from(DatasetSnapshot snapshot) {
            return new DatasetSnapshotResponse(
                    snapshot.getId(),
                    snapshot.getQueryRunId(),
                    snapshot.getDatasetId(),
                    snapshot.getStorageFormat(),
                    snapshot.getData(),
                    snapshot.getCreatedAt()
            );
        }
    }

    public record CreateSnapshotResponse(
            Long queryRunId,
            String status,
            int rowCount,
            long durationMs,
            String requestedBy,
            Long dataSourceId,
            Long queryTemplateId,
            Long snapshotId
    ) {
        public static CreateSnapshotResponse from(QueryExecutionService.ExecutionResult result) {
            return new CreateSnapshotResponse(
                    result.queryRunId(),
                    result.status().name(),
                    result.rowCount(),
                    result.durationMs(),
                    result.requestedBy(),
                    result.dataSourceId(),
                    result.queryTemplateId(),
                    result.snapshotId()
            );
        }
    }
}
