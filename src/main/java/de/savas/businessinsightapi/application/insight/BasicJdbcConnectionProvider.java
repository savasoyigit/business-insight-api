package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Component
public class BasicJdbcConnectionProvider implements JdbcConnectionProvider {

    private final DataSourceSecretResolver secretResolver;
    private final InsightExecutionProperties properties;

    public BasicJdbcConnectionProvider(DataSourceSecretResolver secretResolver, InsightExecutionProperties properties) {
        this.secretResolver = secretResolver;
        this.properties = properties;
    }

    @Override
    public JdbcTemplate templateFor(DataSource dataSource) {
        DriverManagerDataSource jdbcDataSource = new DriverManagerDataSource();
        jdbcDataSource.setDriverClassName(driverClassName(dataSource.getType()));
        jdbcDataSource.setUrl(jdbcUrl(dataSource));
        jdbcDataSource.setUsername(dataSource.getUsername());
        jdbcDataSource.setPassword(secretResolver.resolve(dataSource));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(jdbcDataSource);
        jdbcTemplate.setQueryTimeout(properties.queryTimeoutSeconds());
        jdbcTemplate.setMaxRows(properties.maxRows());
        return jdbcTemplate;
    }

    private String driverClassName(DataSourceType type) {
        return switch (type) {
            case POSTGRES -> "org.postgresql.Driver";
            case MSSQL -> throw new BadRequestException("MSSQL routing is not supported yet");
        };
    }

    private String jdbcUrl(DataSource dataSource) {
        return switch (dataSource.getType()) {
            case POSTGRES -> "jdbc:postgresql://%s:%d/%s".formatted(
                    dataSource.getHost(),
                    dataSource.getPort(),
                    dataSource.getDatabaseName()
            );
            case MSSQL -> throw new BadRequestException("MSSQL routing is not supported yet");
        };
    }
}
