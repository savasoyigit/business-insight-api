package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.application.insight.QueryExecutionService;
import jakarta.validation.constraints.NotNull;
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
            Long snapshotId
    ) {}

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
                result.snapshotId()
        );
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('INSIGHT_QUERY_EXECUTE')")
    public ValidateResponse validate(@RequestParam Long orgId, @RequestBody ValidateRequest req) {
        var result = service.validate(orgId, req.queryTemplateId(), req.params());
        return new ValidateResponse(result.valid(), result.errors(), result.detectedParams(), result.allowedParams());
    }
}
