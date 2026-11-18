package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.api.insight.DataSourceDtos.*;
import de.savas.businessinsightapi.application.insight.DataSourceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insight/data-sources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    // MVP: orgId as query param (later from JWT claims/tenant)
    @GetMapping
    @PreAuthorize("hasAuthority('INSIGHT_DS_READ')")
    public Page<DataSourceResponse> list(@RequestParam Long orgId, Pageable pageable) {
        return service.list(orgId, pageable).map(DataSourceResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DS_READ')")
    public DataSourceResponse get(@RequestParam Long orgId, @PathVariable Long id) {
        return DataSourceResponse.from(service.get(orgId, id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INSIGHT_DS_CREATE')")
    public DataSourceResponse create(@RequestParam Long orgId, @Valid @RequestBody CreateDataSourceRequest req) {
        return DataSourceResponse.from(service.create(
                orgId,
                req.name(),
                req.type(),
                req.host(),
                req.port(),
                req.databaseName(),
                req.username(),
                req.secretRef(),
                req.password(),
                req.options()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DS_UPDATE')")
    public DataSourceResponse update(@RequestParam Long orgId, @PathVariable Long id, @Valid @RequestBody UpdateDataSourceRequest req) {
        return DataSourceResponse.from(service.update(
                orgId,
                id,
                req.name(),
                req.type(),
                req.host(),
                req.port(),
                req.databaseName(),
                req.username(),
                req.secretRef(),
                req.password(),
                req.options(),
                req.active()
        ));
    }
    
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('INSIGHT_DS_READ')")
    public ConnectionTestResponse testConnection(@RequestParam Long orgId, @PathVariable Long id) {
        return ConnectionTestResponse.from(service.testConnection(orgId, id));
    }
}
