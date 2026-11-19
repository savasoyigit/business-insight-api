package de.savas.businessinsightapi.application.insight;

import java.sql.SQLException;

/**
 * A connection to a datasource could not be opened. The message is safe to show to users: it names a broad
 * category only. Driver messages tell "connection refused" from "timed out" from "unknown host", which turns
 * connection tests into a scanner for whatever the server can reach; they are logged instead.
 */
public class DataSourceConnectionException extends IllegalStateException {

    public DataSourceConnectionException(String publicMessage, Throwable cause) {
        super(publicMessage, cause);
    }

    static DataSourceConnectionException from(SQLException ex) {
        String state = ex.getSQLState() == null ? "" : ex.getSQLState();
        String message;
        if (state.startsWith("28")) {
            message = "Authentication failed for the database user";
        } else if (state.equals("3D000")) {
            message = "The database does not exist"; // Postgres reports this only after authentication succeeded
        } else {
            message = "Could not connect to the database server";
        }
        return new DataSourceConnectionException(message, ex);
    }
}
