package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.api.insight.ChartDtos.*;
import de.savas.businessinsightapi.application.insight.ChartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/insight/charts")
public class ChartController {

    private final ChartService service;

    public ChartController(ChartService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INSIGHT_CHART_READ')")
    public Page<ChartResponse> list(@RequestParam Long orgId, Pageable pageable) {
        return service.list(orgId, pageable).map(ChartResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_CHART_READ')")
    public ChartResponse get(@RequestParam Long orgId, @PathVariable Long id) {
        return ChartResponse.from(service.get(orgId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INSIGHT_CHART_CREATE')")
    public ChartResponse create(@RequestParam Long orgId, @Valid @RequestBody CreateChartRequest req) {
        return ChartResponse.from(service.create(orgId, req.name(), req.type(), req.datasetId(), req.config()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_CHART_UPDATE')")
    public ChartResponse update(@RequestParam Long orgId, @PathVariable Long id, @Valid @RequestBody UpdateChartRequest req) {
        return ChartResponse.from(service.update(orgId, id, req.name(), req.type(), req.datasetId(), req.config()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INSIGHT_CHART_DELETE')")
    public void delete(@RequestParam Long orgId, @PathVariable Long id) {
        service.delete(orgId, id);
    }

    @GetMapping("/{id}/render")
    @PreAuthorize("hasAuthority('INSIGHT_CHART_READ')")
    public RenderChartResponse render(@RequestParam Long orgId,
                                      @PathVariable Long id,
                                      @RequestParam @NotNull Long snapshotId) {
        return RenderChartResponse.from(service.render(orgId, id, snapshotId));
    }
}
