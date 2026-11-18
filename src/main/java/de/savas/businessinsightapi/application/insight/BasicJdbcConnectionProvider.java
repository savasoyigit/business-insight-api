package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class BasicJdbcConnectionProvider implements JdbcConnectionProvider {

    private final DataSourceSecretResolver secretResolver;
    private final InsightExecutionProperties properties;
    private final SqlDialectFactory dialectFactory;

    public BasicJdbcConnectionProvider(DataSourceSecretResolver secretResolver,
                                      InsightExecutionProperties properties,
                                      SqlDialectFactory dialectFactory) {
        this.secretResolver = secretResolver;
        this.properties = properties;
        this.dialectFactory = dialectFactory;
    }

    @Override
    public <T> T readOnly(DataSource dataSource, ReadOnlyWork<T> work) {
        SqlDialect dialect = dialectFactory.getDialect(dataSource.getType());

        DriverManagerDataSource jdbcDataSource = new DriverManagerDataSource();
        jdbcDataSource.setDriverClassName(dialect.getDriverClassName());
        jdbcDataSource.setUrl(dialect.buildJdbcUrl(
                dataSource.getHost(),
                dataSource.getPort(),
                dataSource.getDatabaseName()
        ));
        jdbcDataSource.setUsername(dataSource.getUsername());
        jdbcDataSource.setPassword(secretResolver.resolve(dataSource));

        try (Connection connection = jdbcDataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : dialect.readOnlySessionStatements(properties.queryTimeoutSeconds())) {
                        statement.execute(sql);
                    }
                }

                JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                jdbcTemplate.setQueryTimeout(properties.queryTimeoutSeconds());
                jdbcTemplate.setMaxRows(properties.maxRows());
                return work.run(jdbcTemplate);
            } finally {
                connection.rollback();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
