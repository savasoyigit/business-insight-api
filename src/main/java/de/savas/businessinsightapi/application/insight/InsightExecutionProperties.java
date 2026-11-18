package de.savas.businessinsightapi.application.insight;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.insight.execution")
public record InsightExecutionProperties(
        int maxRows,
        int snapshotRows,
        int queryTimeoutSeconds,
        /** Worker threads for asynchronous runs. */
        int asyncThreads,
        /** Runs that may wait for a worker; beyond that, submissions get 429. */
        int asyncQueueCapacity,
        /** Snapshots kept per dataset (oldest are deleted); 0 keeps all. */
        int snapshotRetention
) {
    public InsightExecutionProperties {
        if (asyncThreads <= 0) asyncThreads = 4;
        if (asyncQueueCapacity <= 0) asyncQueueCapacity = 100;
        if (snapshotRetention < 0) snapshotRetention = 0;
    }
}
