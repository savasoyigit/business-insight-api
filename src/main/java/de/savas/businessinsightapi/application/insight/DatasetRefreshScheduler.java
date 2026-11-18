package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.common.error.TooManyRequestsException;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.dataset.DatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Scheduled dataset refresh. Each dataset may carry a cron expression; due datasets get an asynchronous snapshot.
 * Claiming a due refresh is a compare-and-set on next_refresh_at, so several API instances can run the poller.
 */
@Component
public class DatasetRefreshScheduler {

    public static final String SCHEDULER_USER = "scheduler";
    private static final Logger log = LoggerFactory.getLogger(DatasetRefreshScheduler.class);

    private final DatasetRepository datasetRepository;
    private final QueryRunJobService jobService;
    private final InsightSchedulerProperties properties;

    public DatasetRefreshScheduler(DatasetRepository datasetRepository,
                                   QueryRunJobService jobService,
                                   InsightSchedulerProperties properties) {
        this.datasetRepository = datasetRepository;
        this.jobService = jobService;
        this.properties = properties;
    }

    /**
     * Validates a user-supplied schedule and returns it in normalized form, or null to disable.
     * Accepts standard 5-field cron ("0 6 * * *"), Spring's 6-field form with seconds, and macros like "@daily".
     */
    public String normalize(String cron) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        String trimmed = cron.trim().replaceAll("\\s+", " ");
        CronExpression expression = parse(trimmed);
        ZonedDateTime first = expression.next(ZonedDateTime.now(properties.zoneId()));
        ZonedDateTime second = first == null ? null : expression.next(first);
        if (first == null || second == null) {
            throw new BadRequestException("refreshCron never fires");
        }
        if (Duration.between(first, second).toMinutes() < properties.minIntervalMinutes()) {
            throw new BadRequestException("refreshCron fires too often; the minimum interval is "
                    + properties.minIntervalMinutes() + " minutes");
        }
        return trimmed;
    }

    /** Next fire time after now, or null when the dataset has no schedule. */
    public OffsetDateTime nextFireTime(String normalizedCron) {
        if (normalizedCron == null) {
            return null;
        }
        ZonedDateTime next = parse(normalizedCron).next(ZonedDateTime.now(properties.zoneId()));
        return next == null ? null : next.toOffsetDateTime();
    }

    private CronExpression parse(String cron) {
        String springForm = !cron.startsWith("@") && cron.split(" ").length == 5 ? "0 " + cron : cron;
        try {
            return CronExpression.parse(springForm);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("refreshCron is not a valid cron expression: " + ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.insight.scheduler.poll-interval:30s}", initialDelayString = "${app.insight.scheduler.poll-interval:30s}")
    public void poll() {
        if (properties.enabled()) {
            refreshDueDatasets();
        }
    }

    /** Submits a snapshot for every due dataset this instance manages to claim. Returns how many were submitted. */
    public int refreshDueDatasets() {
        OffsetDateTime now = OffsetDateTime.now();
        int submitted = 0;
        for (Dataset dataset : datasetRepository.findTop50ByRefreshCronIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(now)) {
            OffsetDateTime next = nextFireTime(dataset.getRefreshCron());
            if (datasetRepository.claimRefresh(dataset.getId(), dataset.getNextRefreshAt(), next, now) != 1) {
                continue; // another instance got it
            }
            try {
                jobService.submitSnapshot(dataset.getOrganizationId(), dataset, Map.of(), SCHEDULER_USER);
                submitted++;
            } catch (BadRequestException | TooManyRequestsException ex) {
                // The failed attempt is visible as a FAILED run where applicable; the schedule moves on.
                log.warn("Scheduled refresh of dataset {} not submitted: {}", dataset.getId(), ex.getMessage());
            }
        }
        return submitted;
    }
}
