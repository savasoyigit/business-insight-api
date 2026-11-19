package de.savas.businessinsightapi.application.insight;

public interface SqlDialect {
    
    /**
     * Apply row limit to SQL query if not already present
     */
    String applyLimit(String sqlText, int maxRows);
    
    /**
     * Validate that SQL is SELECT-only and doesn't contain forbidden keywords
     */
    ValidationResult validateSelectOnly(String sqlText);
    
    /**
     * Get the JDBC driver class name for this dialect
     */
    String getDriverClassName();
    
    /**
     * Build the JDBC URL from connection parameters. Callers pass values validated by
     * {@link DataSourceConnectionPolicy}; driver settings never go into the URL but into
     * {@link #connectionProperties}, so the URL holds nothing a user could extend.
     */
    String buildJdbcUrl(String host, int port, String databaseName);

    /**
     * Fixed driver settings for every connection. Only the switches listed by the dialect are read from the
     * datasource's options; anything else in options is ignored.
     */
    default java.util.Properties connectionProperties(com.fasterxml.jackson.databind.JsonNode options) {
        return new java.util.Properties();
    }
    
    /**
     * Statements run at the start of every query transaction to make it read-only and bounded.
     * They run right after the transaction begins, before the template's SQL.
     */
    default java.util.List<String> readOnlySessionStatements(int timeoutSeconds) {
        return java.util.List.of();
    }

    record ValidationResult(boolean valid, String errorMessage) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}
