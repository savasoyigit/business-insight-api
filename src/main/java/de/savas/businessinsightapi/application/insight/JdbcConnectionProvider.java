package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public interface JdbcConnectionProvider {

    /**
     * Runs {@code work} on a fresh connection inside a read-only transaction that is always rolled back.
     * Whatever the SQL guardrails miss, the target database refuses writes (where the engine supports it)
     * and nothing is ever committed.
     */
    <T> T readOnly(DataSource dataSource, ReadOnlyWork<T> work);

    @FunctionalInterface
    interface ReadOnlyWork<T> {
        T run(JdbcTemplate jdbcTemplate);
    }
}
