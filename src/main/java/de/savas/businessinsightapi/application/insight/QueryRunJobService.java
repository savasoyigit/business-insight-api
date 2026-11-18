package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.application.insight.QueryExecutionService.PreparedRun;
import de.savas.businessinsightapi.common.error.TooManyRequestsException;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.run.QueryRun;
import de.savas.businessinsightapi.domain.insight.run.QueryRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Asynchronous query runs: validate and record the run in the request thread, execute it on a bounded worker pool,
 * and let clients poll the run. Identical submissions that are still queued or running share one run.
 */
@Service
public class QueryRunJobService {

    private static final Logger log = LoggerFactory.getLogger(QueryRunJobService.class);

    private final QueryExecutionService executionService;
    private final QueryRunRepository runRepository;
    private final ThreadPoolTaskExecutor executor;
    private final InsightExecutionProperties properties;

    /** In-flight runs by submission key (per instance). */
    private final ConcurrentMap<String, Long> inFlight = new ConcurrentHashMap<>();

    public QueryRunJobService(QueryExecutionService executionService,
                              QueryRunRepository runRepository,
                              @Qualifier("queryRunExecutor") ThreadPoolTaskExecutor executor,
                              InsightExecutionProperties properties) {
        this.executionService = executionService;
        this.runRepository = runRepository;
        this.executor = executor;
        this.properties = properties;
    }

    public SubmittedRun submit(Long orgId, Long queryTemplateId, Map<String, Object> params, boolean snapshot, String requestedBy) {
        String key = key(orgId, queryTemplateId, null, snapshot, params);
        return submitOnce(key, () -> executionService.prepare(orgId, queryTemplateId, params, snapshot, null, requestedBy, true));
    }

    public SubmittedRun submitSnapshot(Long orgId, Dataset dataset, Map<String, Object> params, String requestedBy) {
        String key = key(orgId, dataset.getQueryTemplateId(), dataset.getId(), true, params);
        return submitOnce(key, () -> executionService.prepareForDataset(orgId, dataset, params, requestedBy, true));
    }

    private SubmittedRun submitOnce(String key, java.util.function.Supplier<PreparedRun> preparer) {
        synchronized (inFlight) {
            Long existing = inFlight.get(key);
            if (existing != null) {
                QueryRun run = runRepository.findById(existing).orElse(null);
                if (run != null && !run.isFinished()) {
                    return new SubmittedRun(run.getId(), run.getStatus().name(), true);
                }
                inFlight.remove(key);
            }

            PreparedRun prepared = preparer.get();
            inFlight.put(key, prepared.runId());
            try {
                executor.execute(() -> work(key, prepared));
            } catch (TaskRejectedException ex) {
                inFlight.remove(key);
                executionService.abandon(prepared, "QUEUE_FULL", "Too many queued runs");
                throw new TooManyRequestsException("Too many queued query runs; try again shortly");
            }
            return new SubmittedRun(prepared.runId(), "QUEUED", false);
        }
    }

    private void work(String key, PreparedRun prepared) {
        try {
            executionService.runPrepared(prepared, false);
        } catch (RuntimeException ex) {
            // runPrepared records query failures itself; this guards against anything unexpected.
            log.error("Async run {} failed unexpectedly", prepared.runId(), ex);
            executionService.abandon(prepared, "INTERNAL", "Unexpected error while running the query");
        } finally {
            inFlight.remove(key, prepared.runId());
        }
    }

    /** Fails runs stuck in QUEUED/RUNNING after a crash; safe with several instances (see the repository query). */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public int recoverStaleRuns() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusSeconds(properties.queryTimeoutSeconds() * 2L + 60);
        int failed = runRepository.failStaleRuns(cutoff, now);
        if (failed > 0) {
            log.warn("Marked {} stale query run(s) as INTERRUPTED", failed);
        }
        return failed;
    }

    private static String key(Long orgId, Long queryTemplateId, Long datasetId, boolean snapshot, Map<String, Object> params) {
        return orgId + "|" + queryTemplateId + "|" + datasetId + "|" + snapshot + "|"
                + (params == null ? "{}" : new TreeMap<>(params).toString());
    }

    /** {@code coalesced} is true when an identical run was already queued or running and is returned instead. */
    public record SubmittedRun(Long queryRunId, String status, boolean coalesced) {
    }
}
