package de.savas.businessinsightapi.api.insight;

import de.savas.businessinsightapi.api.insight.DatasetDtos.*;
import de.savas.businessinsightapi.application.insight.DatasetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insight/datasets")
public class DatasetController {

    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_READ')")
    public Page<DatasetResponse> list(@RequestParam Long orgId, Pageable pageable) {
        return service.list(orgId, pageable).map(DatasetResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_READ')")
    public DatasetResponse get(@RequestParam Long orgId, @PathVariable Long id) {
        return DatasetResponse.from(service.get(orgId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_CREATE')")
    public DatasetResponse create(@RequestParam Long orgId, @Valid @RequestBody CreateDatasetRequest req) {
        return DatasetResponse.from(service.create(orgId, req.name(), req.queryTemplateId(), req.defaultParams()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_UPDATE')")
    public DatasetResponse update(@RequestParam Long orgId, @PathVariable Long id, @Valid @RequestBody UpdateDatasetRequest req) {
        return DatasetResponse.from(service.update(orgId, id, req.name(), req.queryTemplateId(), req.defaultParams()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_DELETE')")
    public void delete(@RequestParam Long orgId, @PathVariable Long id) {
        service.delete(orgId, id);
    }

    @GetMapping("/{id}/snapshots")
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_READ')")
    public List<DatasetSnapshotResponse> listSnapshots(@RequestParam Long orgId, @PathVariable Long id) {
        return service.listSnapshots(orgId, id).stream().map(DatasetSnapshotResponse::from).toList();
    }

    @PostMapping("/{id}/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INSIGHT_DATASET_CREATE')")
    public CreateSnapshotResponse createSnapshot(@RequestParam Long orgId, @PathVariable Long id, @RequestBody(required = false) CreateSnapshotRequest req) {
        var result = service.createSnapshot(orgId, id, req == null ? null : req.params());
        return CreateSnapshotResponse.from(result);
    }
}
