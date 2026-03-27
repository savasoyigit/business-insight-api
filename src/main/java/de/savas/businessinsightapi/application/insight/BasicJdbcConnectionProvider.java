package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

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
    public JdbcTemplate templateFor(DataSource dataSource) {
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

        JdbcTemplate jdbcTemplate = new JdbcTemplate(jdbcDataSource);
        jdbcTemplate.setQueryTimeout(properties.queryTimeoutSeconds());
        jdbcTemplate.setMaxRows(properties.maxRows());
        return jdbcTemplate;
    }
}
