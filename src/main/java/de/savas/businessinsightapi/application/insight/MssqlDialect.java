package de.savas.businessinsightapi.application.insight;

import java.util.Locale;
import java.util.regex.Pattern;

public class MssqlDialect implements SqlDialect {
    
    private static final Pattern TOP_PATTERN = Pattern.compile("\\bselect\\s+top\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFSET_FETCH_PATTERN = Pattern.compile("\\boffset\\s+\\d+\\s+rows\\s+fetch", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|merge|grant|revoke|comment|execute|call|exec)\\b",
            Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public String applyLimit(String sqlText, int maxRows) {
        if (sqlText == null) {
            return null;
        }
        
        // Check if already has TOP or OFFSET/FETCH
        if (TOP_PATTERN.matcher(sqlText).find() || OFFSET_FETCH_PATTERN.matcher(sqlText).find()) {
            return sqlText;
        }
        
        // Apply TOP clause by inserting after SELECT
        return sqlText.trim().replaceFirst(
                "(?i)(select\\s+)",
                "$1TOP " + maxRows + " "
        );
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
    
    /**
     * SQL Server has no read-only transaction mode; Connection#setReadOnly is only a hint there.
     * Writes are still never committed (the transaction is always rolled back), and the JDBC query timeout applies.
     * Use a login with read-only permissions (e.g. db_datareader) for real protection.
     */
    @Override
    public java.util.List<String> readOnlySessionStatements(int timeoutSeconds) {
        return java.util.List.of("SET LOCK_TIMEOUT " + Math.max(1, timeoutSeconds) * 1000L);
    }

    @Override
    public String getDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }
    
    @Override
    public String buildJdbcUrl(String host, int port, String databaseName) {
        return "jdbc:sqlserver://%s:%d;databaseName=%s".formatted(host, port, databaseName);
    }

    /**
     * Connections are always encrypted and the server certificate is verified. A server without a trusted
     * certificate can be allowed per datasource with {@code options: {"trustServerCertificate": true}}; the traffic
     * stays encrypted, but the server's identity is then not checked.
     */
    @Override
    public java.util.Properties connectionProperties(com.fasterxml.jackson.databind.JsonNode options) {
        boolean trustServerCertificate = options != null
                && options.path("trustServerCertificate").isBoolean()
                && options.path("trustServerCertificate").booleanValue();
        java.util.Properties props = new java.util.Properties();
        props.setProperty("encrypt", "true");
        props.setProperty("trustServerCertificate", String.valueOf(trustServerCertificate));
        props.setProperty("loginTimeout", "10");
        props.setProperty("applicationName", "business-insight-api");
        return props;
    }
}
