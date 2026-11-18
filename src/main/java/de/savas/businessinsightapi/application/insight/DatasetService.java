package de.savas.businessinsightapi.application.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.dataset.DatasetRepository;
import de.savas.businessinsightapi.domain.insight.query.QueryTemplateRepository;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshot;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepo;
    private final QueryTemplateRepository queryTemplateRepo;
    private final DatasetSnapshotRepository snapshotRepo;
    private final QueryExecutionService queryExecutionService;
    private final QueryRunJobService jobService;
    private final DatasetRefreshScheduler refreshScheduler;

    public DatasetService(DatasetRepository datasetRepo,
                          QueryTemplateRepository queryTemplateRepo,
                          DatasetSnapshotRepository snapshotRepo,
                          QueryExecutionService queryExecutionService,
                          QueryRunJobService jobService,
                          DatasetRefreshScheduler refreshScheduler) {
        this.datasetRepo = datasetRepo;
        this.queryTemplateRepo = queryTemplateRepo;
        this.snapshotRepo = snapshotRepo;
        this.queryExecutionService = queryExecutionService;
        this.jobService = jobService;
        this.refreshScheduler = refreshScheduler;
    }

    public Page<Dataset> list(Long organizationId, Pageable pageable) {
        return datasetRepo.findAllByOrganizationId(organizationId, pageable);
    }

    public Dataset get(Long organizationId, Long id) {
        Dataset dataset = datasetRepo.findById(id).orElseThrow(() -> new BadRequestException("Dataset not found"));
        if (!dataset.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("Dataset not found");
        }
        return dataset;
    }

    public Dataset create(Long organizationId, String name, Long queryTemplateId, JsonNode defaultParams, String refreshCron) {
        ensureQueryTemplateBelongsToOrg(organizationId, queryTemplateId);
        Dataset dataset = new Dataset(
                organizationId,
                requireName(name),
                queryTemplateId,
                normalizeDefaultParams(defaultParams),
                currentUsername()
        );
        applySchedule(dataset, refreshCron);
        return datasetRepo.save(dataset);
    }

    /** {@code refreshCron}: null leaves the schedule unchanged, blank removes it. */
    public Dataset update(Long organizationId, Long id, String name, Long queryTemplateId, JsonNode defaultParams, String refreshCron) {
        Dataset dataset = get(organizationId, id);
        if (name != null) {
            dataset.setName(requireName(name));
        }
        if (queryTemplateId != null) {
            ensureQueryTemplateBelongsToOrg(organizationId, queryTemplateId);
            dataset.setQueryTemplateId(queryTemplateId);
        }
        if (defaultParams != null) {
            dataset.setDefaultParams(normalizeDefaultParams(defaultParams));
        }
        if (refreshCron != null) {
            applySchedule(dataset, refreshCron);
        }
        return datasetRepo.save(dataset);
    }

    private void applySchedule(Dataset dataset, String refreshCron) {
        String normalized = refreshScheduler.normalize(refreshCron);
        dataset.setRefreshSchedule(normalized, refreshScheduler.nextFireTime(normalized));
    }

    public void delete(Long organizationId, Long id) {
        Dataset dataset = get(organizationId, id);
        datasetRepo.delete(dataset);
    }

    public Page<DatasetSnapshot> listSnapshots(Long organizationId, Long datasetId, Pageable pageable) {
        get(organizationId, datasetId);
        return snapshotRepo.findAllByOrganizationIdAndDatasetIdOrderByCreatedAtDesc(organizationId, datasetId, pageable);
    }

    public QueryExecutionService.ExecutionResult createSnapshot(Long organizationId, Long datasetId, Map<String, Object> params) {
        Dataset dataset = get(organizationId, datasetId);
        return queryExecutionService.executeForDataset(organizationId, dataset, params == null ? Map.of() : params);
    }

    /** Queues a snapshot; poll the returned run for the result. */
    public QueryRunJobService.SubmittedRun submitSnapshot(Long organizationId, Long datasetId, Map<String, Object> params) {
        Dataset dataset = get(organizationId, datasetId);
        return jobService.submitSnapshot(organizationId, dataset, params == null ? Map.of() : params, currentUsername());
    }

    private void ensureQueryTemplateBelongsToOrg(Long organizationId, Long queryTemplateId) {
        var queryTemplate = queryTemplateRepo.findById(queryTemplateId)
                .orElseThrow(() -> new BadRequestException("QueryTemplate not found"));
        if (!queryTemplate.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("QueryTemplate not found");
        }
    }

    private JsonNode normalizeDefaultParams(JsonNode defaultParams) {
        if (defaultParams == null || defaultParams.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!defaultParams.isObject()) {
            throw new BadRequestException("defaultParams must be a JSON object");
        }
        return defaultParams;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        return name.trim();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
