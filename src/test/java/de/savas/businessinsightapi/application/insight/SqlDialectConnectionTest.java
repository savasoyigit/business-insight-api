package de.savas.businessinsightapi.application.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlDialectConnectionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mssqlEncryptsAndVerifiesTheCertificateByDefault() {
        var dialect = new MssqlDialect();
        assertThat(dialect.buildJdbcUrl("db.example.com", 1433, "ERP"))
                .isEqualTo("jdbc:sqlserver://db.example.com:1433;databaseName=ERP");

        var props = dialect.connectionProperties(null);
        assertThat(props.getProperty("encrypt")).isEqualTo("true");
        assertThat(props.getProperty("trustServerCertificate")).isEqualTo("false");
    }

    @Test
    void mssqlCertificateCheckIsOnlySkippedWhenExplicitlyRequested() throws Exception {
        var dialect = new MssqlDialect();
        assertThat(dialect.connectionProperties(mapper.readTree("{\"trustServerCertificate\": true}"))
                .getProperty("trustServerCertificate")).isEqualTo("true");
        assertThat(dialect.connectionProperties(mapper.readTree("{\"trustServerCertificate\": \"true\"}"))
                .getProperty("trustServerCertificate")).isEqualTo("false");
        // Options cannot turn encryption off or add other driver settings.
        var props = dialect.connectionProperties(mapper.readTree("{\"encrypt\": false, \"socketFactoryClass\": \"x\"}"));
        assertThat(props.getProperty("encrypt")).isEqualTo("true");
        assertThat(props).doesNotContainKey("socketFactoryClass");
    }

    @Test
    void postgresTakesNoSettingsFromOptions() throws Exception {
        var dialect = new PostgresDialect();
        assertThat(dialect.buildJdbcUrl("db.example.com", 5432, "reporting"))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/reporting");
        var props = dialect.connectionProperties(mapper.readTree("{\"socketFactory\": \"x\", \"sslfactory\": \"y\"}"));
        assertThat(props).doesNotContainKeys("socketFactory", "sslfactory");
    }
}
