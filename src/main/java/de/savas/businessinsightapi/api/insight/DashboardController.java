package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.api.insight.DashboardDtos.*;
import de.savas.businessinsightapi.application.insight.DashboardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insight/dashboards")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INSIGHT_DASHBOARD_READ')")
    public Page<DashboardResponse> list(@RequestParam Long orgId, Pageable pageable) {
        return service.list(orgId, pageable).map(dashboard -> DashboardResponse.from(service.get(orgId, dashboard.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DASHBOARD_READ')")
    public DashboardResponse get(@RequestParam Long orgId, @PathVariable Long id) {
        return DashboardResponse.from(service.get(orgId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INSIGHT_DASHBOARD_CREATE')")
    public DashboardResponse create(@RequestParam Long orgId, @Valid @RequestBody CreateDashboardRequest req) {
        return DashboardResponse.from(service.create(orgId, req.name(), toInputs(req.widgets())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DASHBOARD_UPDATE')")
    public DashboardResponse update(@RequestParam Long orgId, @PathVariable Long id, @Valid @RequestBody UpdateDashboardRequest req) {
        return DashboardResponse.from(service.update(orgId, id, req.name(), toInputs(req.widgets())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INSIGHT_DASHBOARD_DELETE')")
    public void delete(@RequestParam Long orgId, @PathVariable Long id) {
        service.delete(orgId, id);
    }

    private List<DashboardService.DashboardWidgetInput> toInputs(List<DashboardWidgetRequest> widgets) {
        return widgets == null ? null : widgets.stream().map(DashboardWidgetRequest::toInput).toList();
    }
}
