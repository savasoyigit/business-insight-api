package de.savas.businessinsightapi.domain.insight.run;

public enum QueryRunStatus {
    /** Accepted for asynchronous execution, waiting for a worker. */
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED
}
