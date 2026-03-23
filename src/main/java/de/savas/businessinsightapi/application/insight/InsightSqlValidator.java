package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InsightSqlValidator {

    private static final Pattern PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z][a-zA-Z0-9_]*)");
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|merge|grant|revoke|comment|execute|call)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);

    public ValidationResult validateSql(String sqlText) {
        Set<String> errors = new LinkedHashSet<>();
        String normalized = normalize(sqlText);

        if (normalized.isBlank()) {
            errors.add("sqlText is required");
        } else {
            if (!normalized.startsWith("select ")) {
                errors.add("Only SELECT queries are allowed");
            }
            if (normalized.contains(";")) {
                errors.add("Only a single SELECT statement is allowed");
            }
            if (FORBIDDEN_PATTERN.matcher(normalized).find()) {
                errors.add("DDL/DML keywords are not allowed");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, extractNamedParams(sqlText));
    }

    public void assertValid(String sqlText) {
        ValidationResult result = validateSql(sqlText);
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

    public String applyLimitIfMissing(String sqlText, int maxRows) {
        if (sqlText == null) {
            return null;
        }
        if (LIMIT_PATTERN.matcher(sqlText).find()) {
            return sqlText;
        }
        return sqlText.stripTrailing() + " limit " + maxRows;
    }

    private String normalize(String sqlText) {
        return sqlText == null ? "" : sqlText.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public record ValidationResult(boolean valid, Set<String> errors, Set<String> detectedParams) {
    }
}
