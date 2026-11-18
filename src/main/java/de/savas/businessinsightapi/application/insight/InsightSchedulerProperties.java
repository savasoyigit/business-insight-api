package de.savas.businessinsightapi.application.insight;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/** Scheduled dataset refresh. */
@ConfigurationProperties(prefix = "app.insight.scheduler")
public record InsightSchedulerProperties(
        /** Turns the background poller off (e.g. in tests); schedules are still stored and validated. */
        boolean enabled,
        /** How often due datasets are looked for. */
        Duration pollInterval,
        /** Time zone cron expressions are evaluated in. */
        String zone,
        /** Smallest allowed gap between two scheduled refreshes. */
        int minIntervalMinutes
) {
    public InsightSchedulerProperties {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) pollInterval = Duration.ofSeconds(30);
        if (zone == null || zone.isBlank()) zone = "UTC";
        if (minIntervalMinutes <= 0) minIntervalMinutes = 5;
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
