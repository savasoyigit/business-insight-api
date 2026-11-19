package de.savas.businessinsightapi.application.insight;

import java.util.Locale;
import java.util.regex.Pattern;

public class PostgresDialect implements SqlDialect {
    
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|merge|grant|revoke|comment|execute|call)\\b",
            Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public String applyLimit(String sqlText, int maxRows) {
        if (sqlText == null) {
            return null;
        }
        if (LIMIT_PATTERN.matcher(sqlText).find()) {
            return sqlText;
        }
        return sqlText.stripTrailing() + " LIMIT " + maxRows;
    }
    
    @Override
    public ValidationResult validateSelectOnly(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return ValidationResult.error("SQL text is required");
        }
        
        String normalized = sqlText.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        
        if (!normalized.startsWith("select ")) {
            return ValidationResult.error("Only SELECT queries are allowed");
        }
        
        if (normalized.contains(";")) {
            return ValidationResult.error("Only a single SELECT statement is allowed");
        }
        
        if (FORBIDDEN_PATTERN.matcher(normalized).find()) {
            return ValidationResult.error("DDL/DML keywords are not allowed");
        }
        
        return ValidationResult.ok();
    }
    
    /** The server enforces both: writes fail with "cannot execute ... in a read-only transaction". */
    @Override
    public java.util.List<String> readOnlySessionStatements(int timeoutSeconds) {
        return java.util.List.of(
                "SET TRANSACTION READ ONLY",
                "SET LOCAL statement_timeout = " + Math.max(1, timeoutSeconds) * 1000L
        );
    }

    @Override
    public String getDriverClassName() {
        return "org.postgresql.Driver";
    }
    
    @Override
    public String buildJdbcUrl(String host, int port, String databaseName) {
        return "jdbc:postgresql://%s:%d/%s".formatted(host, port, databaseName);
    }

    @Override
    public java.util.Properties connectionProperties(com.fasterxml.jackson.databind.JsonNode options) {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("connectTimeout", "10");
        props.setProperty("ApplicationName", "business-insight-api");
        return props;
    }
}
