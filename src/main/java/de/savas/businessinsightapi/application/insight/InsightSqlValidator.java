package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InsightSqlValidator {

    private static final Pattern PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z][a-zA-Z0-9_]*)");
    private static final Pattern DYNAMIC_IDENTIFIER_PATTERN = Pattern.compile("(\\$\\{[^}]+}|\\{\\{[^}]+}})");
    
    private final SqlDialectFactory dialectFactory;

    public InsightSqlValidator(SqlDialectFactory dialectFactory) {
        this.dialectFactory = dialectFactory;
    }

    public ValidationResult validateSql(String sqlText, DataSourceType dataSourceType) {
        Set<String> errors = new LinkedHashSet<>();
        
        if (sqlText == null || sqlText.isBlank()) {
            errors.add("sqlText is required");
        } else {
            if (DYNAMIC_IDENTIFIER_PATTERN.matcher(sqlText).find()) {
                errors.add("Dynamic SQL identifiers are not allowed");
            }

            SqlDialect dialect = dialectFactory.getDialect(dataSourceType);
            SqlDialect.ValidationResult dialectValidation = dialect.validateSelectOnly(sqlText);
            
            if (!dialectValidation.valid()) {
                errors.add(dialectValidation.errorMessage());
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, extractNamedParams(sqlText));
    }

    public void assertValid(String sqlText, DataSourceType dataSourceType) {
        ValidationResult result = validateSql(sqlText, dataSourceType);
        if (!result.valid()) {
            throw new BadRequestException(String.join("; ", result.errors()));
        }
    }

    public Set<String> extractNamedParams(String sqlText) {
        Set<String> params = new LinkedHashSet<>();
        if (sqlText == null || sqlText.isBlank()) {
            return params;
        }
        Matcher matcher = PARAM_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    public String applyLimitIfMissing(String sqlText, int maxRows, DataSourceType dataSourceType) {
        if (sqlText == null) {
            return null;
        }
        SqlDialect dialect = dialectFactory.getDialect(dataSourceType);
        return dialect.applyLimit(sqlText, maxRows);
    }

    public record ValidationResult(boolean valid, Set<String> errors, Set<String> detectedParams) {
    }
}
