package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.springframework.stereotype.Component;

@Component
public class SqlDialectFactory {
    
    public SqlDialect getDialect(DataSourceType type) {
        return switch (type) {
            case POSTGRES -> new PostgresDialect();
            case MSSQL -> new MssqlDialect();
        };
    }
}
