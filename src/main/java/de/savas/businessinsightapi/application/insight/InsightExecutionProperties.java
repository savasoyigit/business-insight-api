package de.savas.businessinsightapi.application.insight;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.insight.execution")
public record InsightExecutionProperties(
        int maxRows,
        int snapshotRows,
        int queryTimeoutSeconds
) {
}
