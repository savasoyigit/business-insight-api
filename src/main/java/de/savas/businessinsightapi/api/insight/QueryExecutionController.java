package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.application.insight.QueryExecutionService;
import de.savas.businessinsightapi.domain.insight.run.QueryRun;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/insight/query-execution")
public class QueryExecutionController {

    private final QueryExecutionService service;

    public QueryExecutionController(QueryExecutionService service) {
        this.service = service;
    }

    public record ExecuteRequest(
            @NotNull Long queryTemplateId,
            Map<String, Object> params,
            Boolean snapshot
    ) {}

    public record ExecuteResponse(
            Long queryRunId,
            String status,
            int rowCount,
            long durationMs,
            String requestedBy,
            Long dataSourceId,
            Long queryTemplateId,
            Long snapshotId,
            String queryHash,
            String errorType
    ) {}

    public record QueryRunResponse(
            Long id,
            String status,
            String requestedBy,
            Long queryTemplateId,
            Long dataSourceId,
            long durationMs,
            Integer rowCount,
            String queryHash,
            String errorType,
            java.time.OffsetDateTime startedAt,
            java.time.OffsetDateTime finishedAt
    ) {
        public static QueryRunResponse from(QueryRun run) {
            return new QueryRunResponse(
                    run.getId(),
                    run.getStatus().name(),
                    run.getRequestedBy(),
                    run.getQueryTemplateId(),
                    run.getDataSourceId(),
                    run.getDurationMs() == null ? 0L : run.getDurationMs(),
                    run.getRowCount(),
                    run.getQueryHash(),
                    run.getErrorType(),
                    run.getStartedAt(),
                    run.getFinishedAt()
            );
        }
    }

    public record ValidateRequest(
            @NotNull Long queryTemplateId,
            Map<String, Object> params
    ) {}

    public record ValidateResponse(
            boolean valid,
            java.util.List<String> errors,
            java.util.List<String> detectedParams,
            java.util.List<String> allowedParams
    ) {}

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('INSIGHT_QUERY_EXECUTE')")
    public ExecuteResponse execute(@RequestParam Long orgId, @RequestBody ExecuteRequest req) {
        boolean takeSnapshot = req.snapshot() != null && req.snapshot();
        var result = service.execute(orgId, req.queryTemplateId(), req.params(), takeSnapshot);
        return new ExecuteResponse(
                result.queryRunId(),
                result.status().name(),
                result.rowCount(),
                result.durationMs(),
                result.requestedBy(),
                result.dataSourceId(),
                result.queryTemplateId(),
                                result.snapshotId(),
                                result.queryHash(),
                                result.errorType()
        );
    }

        @GetMapping("/runs")
        @PreAuthorize("hasAuthority('INSIGHT_QUERY_EXECUTE')")
        public Page<QueryRunResponse> listRuns(@RequestParam Long orgId, Pageable pageable) {
                return service.listRuns(orgId, pageable).map(QueryRunResponse::from);
        }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('INSIGHT_QUERY_EXECUTE')")
    public ValidateResponse validate(@RequestParam Long orgId, @RequestBody ValidateRequest req) {
        var result = service.validate(orgId, req.queryTemplateId(), req.params());
        return new ValidateResponse(result.valid(), result.errors(), result.detectedParams(), result.allowedParams());
    }
}
