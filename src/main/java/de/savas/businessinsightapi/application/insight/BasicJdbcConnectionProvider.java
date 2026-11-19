package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class BasicJdbcConnectionProvider implements JdbcConnectionProvider {

    private static final Logger log = LoggerFactory.getLogger(BasicJdbcConnectionProvider.class);

    private final DataSourceSecretResolver secretResolver;
    private final InsightExecutionProperties properties;
    private final SqlDialectFactory dialectFactory;
    private final DataSourceConnectionPolicy connectionPolicy;

    public BasicJdbcConnectionProvider(DataSourceSecretResolver secretResolver,
                                      InsightExecutionProperties properties,
                                      SqlDialectFactory dialectFactory,
                                      DataSourceConnectionPolicy connectionPolicy) {
        this.secretResolver = secretResolver;
        this.properties = properties;
        this.dialectFactory = dialectFactory;
        this.connectionPolicy = connectionPolicy;
    }

    @Override
    public <T> T readOnly(DataSource dataSource, ReadOnlyWork<T> work) {
        SqlDialect dialect = dialectFactory.getDialect(dataSource.getType());
        // Before the secret is resolved, so it is never sent to a disallowed address. Rows saved before validation
        // existed are re-checked here too. (The driver resolves the name again; a DNS answer that changes in between
        // is not covered.)
        connectionPolicy.assertAllowedTarget(dataSource.getHost());
        connectionPolicy.validateDatabaseName(dataSource.getDatabaseName());

        DriverManagerDataSource jdbcDataSource = new DriverManagerDataSource();
        jdbcDataSource.setDriverClassName(dialect.getDriverClassName());
        jdbcDataSource.setUrl(dialect.buildJdbcUrl(
                dataSource.getHost(),
                dataSource.getPort(),
                dataSource.getDatabaseName()
        ));
        jdbcDataSource.setConnectionProperties(dialect.connectionProperties(dataSource.getOptions()));
        jdbcDataSource.setUsername(dataSource.getUsername());
        jdbcDataSource.setPassword(secretResolver.resolve(dataSource));

        try (Connection connection = open(jdbcDataSource, dataSource)) {
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

    /** Opens the connection; driver details go to the log, users only see {@link DataSourceConnectionException}'s category. */
    private Connection open(DriverManagerDataSource jdbcDataSource, DataSource dataSource) {
        try {
            return jdbcDataSource.getConnection();
        } catch (SQLException ex) {
            log.warn("Connection to datasource {} (org {}) failed: sqlState={} message={}",
                    dataSource.getId(), dataSource.getOrganizationId(), ex.getSQLState(), ex.getMessage());
            throw DataSourceConnectionException.from(ex);
        }
    }
}
