package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public interface JdbcConnectionProvider {
    JdbcTemplate templateFor(DataSource dataSource);
}
