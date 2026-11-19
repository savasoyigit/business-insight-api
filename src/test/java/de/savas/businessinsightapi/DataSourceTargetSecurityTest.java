package de.savas.businessinsightapi;

import de.savas.businessinsightapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Datasource fields are written by organization users. They must not be able to read the server's own secrets,
 * carry a stored password to another server, or inject driver settings into the JDBC URL.
 */
class DataSourceTargetSecurityTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private long org;

    @BeforeEach
    void setUp() throws Exception {
        String admin = adminToken();
        org = createOrganization(admin);
        token = adminToken();
    }

    private String q(String path) {
        return path + (path.contains("?") ? "&" : "?") + "orgId=" + org;
    }

    private Map<String, Object> dataSource(String secretRef) {
        Map<String, Object> body = new HashMap<>(Map.of("name", unique("ds"), "type", "POSTGRES", "host", "db.example.com",
                "port", 5432, "databaseName", "reporting", "username", "reader"));
        body.put("secretRef", secretRef);
        return body;
    }

    private long create() throws Exception {
        var r = post(q("/api/insight/data-sources"), token, dataSource("env:DS_SECRET_REPORTING"));
        assertThat(r.status()).as(r.text()).isEqualTo(200);
        return r.body().get("id").asLong();
    }

    @Test
    void secretRefCanOnlyNamePrefixedVariables() throws Exception {
        for (String ref : List.of("env:JWT_SECRET", "env:APP_SECRETS_KEY", "env:PATH", "env:DS_SECRET_", "env:ds_secret_x",
                "env:DS_SECRET_A-B", "env:XDS_SECRET_A")) {
            var r = post(q("/api/insight/data-sources"), token, dataSource(ref));
            assertThat(r.status()).as(ref).isEqualTo(400);
            assertThat(r.text()).contains("DS_SECRET_");
        }
        long id = create();
        assertThat(put(q("/api/insight/data-sources/" + id), token, Map.of("secretRef", "env:JWT_SECRET")).status()).isEqualTo(400);
    }

    @Test
    void storedRowsWithForbiddenSecretRefAreRefusedAtConnectTime() throws Exception {
        long id = create();
        // A row saved before the prefix rule existed.
        jdbc.update("update data_source set host = 'localhost', secret_ref = 'env:PATH' where id = ?", id);

        var test = post(q("/api/insight/data-sources/" + id + "/test"), token, null);
        assertThat(test.body().get("ok").asBoolean()).isFalse();
        assertThat(test.body().get("error").asString()).contains("DS_SECRET_");
        assertThat(test.text()).doesNotContain(System.getenv("PATH"));
    }

    @Test
    void missingVariableIsNotNamedInTheError() throws Exception {
        long id = create();
        jdbc.update("update data_source set host = 'localhost', secret_ref = 'env:DS_SECRET_SURELY_NOT_SET_42' where id = ?", id);

        var test = post(q("/api/insight/data-sources/" + id + "/test"), token, null);
        assertThat(test.body().get("ok").asBoolean()).isFalse();
        assertThat(test.body().get("error").asString())
                .isEqualTo("Datasource secret is not available on this server")
                .doesNotContain("SURELY_NOT_SET");
    }

    @Test
    void changingTheTargetRequiresTheSecretAgain() throws Exception {
        long id = create();
        String url = q("/api/insight/data-sources/" + id);

        for (Map<String, Object> change : List.<Map<String, Object>>of(
                Map.of("host", "attacker.example.net"),
                Map.of("port", 6543),
                Map.of("databaseName", "other"),
                Map.of("type", "MSSQL"))) {
            var r = put(url, token, change);
            assertThat(r.status()).as(change.toString()).isEqualTo(400);
            assertThat(r.text()).contains("requires the password or secretRef again");
        }
        assertThat(get(url, token).body().get("host").asString()).isEqualTo("db.example.com");

        // Same target sent again (as the edit form does) or other fields only: no new secret needed.
        assertThat(put(url, token, Map.of("host", "DB.example.com ", "port", 5432, "databaseName", "reporting",
                "type", "POSTGRES", "name", unique("renamed"))).status()).isEqualTo(200);
        assertThat(put(url, token, Map.of("username", "other_reader", "active", false)).status()).isEqualTo(200);

        // With a new secret the target may change.
        var moved = put(url, token, Map.of("host", "db2.example.com", "secretRef", "env:DS_SECRET_OTHER"));
        assertThat(moved.status()).as(moved.text()).isEqualTo(200);
        assertThat(moved.body().get("host").asString()).isEqualTo("db2.example.com");
        assertThat(moved.body().get("secretRef").asString()).isEqualTo("env:DS_SECRET_OTHER");
    }

    @Test
    void hostAndDatabaseNameCannotCarryUrlSyntax() throws Exception {
        for (String db : List.of("db?socketFactory=org.example.Evil", "db&sslfactory=x", "db;encrypt=false",
                "db/other", "db name", "{db}", "")) {
            Map<String, Object> body = dataSource("env:DS_SECRET_X");
            body.put("databaseName", db);
            assertThat(post(q("/api/insight/data-sources"), token, body).status()).as(db).isEqualTo(400);
        }
        for (String host : List.of("evil.example.com/x?a=b", "host;databaseName=x", "host:5432", "user@host",
                "-host", "[::1]", "169.254.169.254", "0.0.0.0", "224.0.0.1")) {
            Map<String, Object> body = dataSource("env:DS_SECRET_X");
            body.put("host", host);
            assertThat(post(q("/api/insight/data-sources"), token, body).status()).as(host).isEqualTo(400);
        }
        long id = create();
        assertThat(put(q("/api/insight/data-sources/" + id), token,
                Map.of("databaseName", "db?socketFactory=x", "secretRef", "env:DS_SECRET_X")).status()).isEqualTo(400);
    }

    @Test
    void storedRowsWithInjectedFieldsAreRefusedAtConnectTime() throws Exception {
        long id = create();
        jdbc.update("update data_source set host = 'localhost', database_name = 'db?socketFactory=x' where id = ?", id);
        var test = post(q("/api/insight/data-sources/" + id + "/test"), token, null);
        assertThat(test.body().get("ok").asBoolean()).isFalse();
        assertThat(test.body().get("error").asString()).contains("databaseName");

        jdbc.update("update data_source set host = '169.254.169.254', database_name = 'db' where id = ?", id);
        test = post(q("/api/insight/data-sources/" + id + "/test"), token, null);
        assertThat(test.body().get("ok").asBoolean()).isFalse();
        assertThat(test.body().get("error").asString()).contains("not allowed");
    }

    @Test
    void connectionFailuresOnlyNameABroadCategory() throws Exception {
        long id = create();
        String url = q("/api/insight/data-sources/" + id + "/test");
        String expected = "Could not connect to the database server";

        // Unknown name and closed port used to give different driver messages: a scanner for internal hosts and ports.
        jdbc.update("update data_source set host = 'no-such-host.invalid', secret_ref = 'env:PATH_IS_NOT_USED' where id = ?", id);
        jdbc.update("update data_source set secret_ref = 'env:DS_SECRET_X' where id = ?", id);
        assertThat(post(url, token, null).body().get("error").asString()).isEqualTo(expected);

        jdbc.update("update data_source set host = 'localhost', port = 1 where id = ?", id);
        String closedPort = post(url, token, null).body().get("error").asString();
        // DS_SECRET_X is not set, so the secret check may answer first; either way no driver detail comes through.
        assertThat(closedPort).isIn(expected, "Datasource secret is not available on this server");
        assertThat(closedPort).doesNotContainIgnoringCase("refused").doesNotContain("localhost");
    }

    @Test
    void queryExecutionHidesDriverDetailsToo() throws Exception {
        assumeTrue(System.getenv("DS_SECRET_TEST_DB") != null, "DS_SECRET_TEST_DB not set");
        var ds = post(q("/api/insight/data-sources"), token, Map.of("name", unique("closed"), "type", "POSTGRES",
                "host", "localhost", "port", 1, "databaseName", "businessinsight_test", "username", "app",
                "secretRef", "env:DS_SECRET_TEST_DB"));
        long dsId = ds.body().get("id").asLong();
        long qtId = post(q("/api/insight/query-templates"), token, Map.of("dataSourceId", dsId, "name", unique("q"),
                "sqlText", "select 1 as x")).body().get("id").asLong();

        var exec = post(q("/api/insight/query-execution/execute"), token, Map.of("queryTemplateId", qtId, "params", Map.of()));
        assertThat(exec.status()).isEqualTo(400);
        assertThat(exec.body().get("message").asString())
                .contains("Could not connect to the database server")
                .doesNotContainIgnoringCase("refused");
    }

    @Test
    void missingDatabaseIsReportedAfterAuthentication() throws Exception {
        assumeTrue(System.getenv("DS_SECRET_TEST_DB") != null, "DS_SECRET_TEST_DB not set");
        var ds = post(q("/api/insight/data-sources"), token, Map.of("name", unique("nodb"), "type", "POSTGRES",
                "host", "localhost", "port", 5432, "databaseName", "no_such_db_42", "username", "app",
                "secretRef", "env:DS_SECRET_TEST_DB"));
        var test = post(q("/api/insight/data-sources/" + ds.body().get("id").asLong() + "/test"), token, null);
        assertThat(test.body().get("error").asString()).isEqualTo("The database does not exist");
    }

    @Test
    void forbiddenFunctionsAreRefusedOnSaveAndOnExecution() throws Exception {
        long dsId = create();
        var refused = post(q("/api/insight/query-templates"), token, Map.of("dataSourceId", dsId, "name", unique("bad"),
                "sqlText", "select pg_read_file('/etc/passwd') as f"));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.text()).contains("pg_read_file() is not allowed");

        // A template saved before the rule existed.
        long qtId = post(q("/api/insight/query-templates"), token, Map.of("dataSourceId", dsId, "name", unique("old"),
                "sqlText", "select 1 as x")).body().get("id").asLong();
        jdbc.update("update query_template set sql_text = 'select pg_read_file(''/etc/passwd'') as f' where id = ?", qtId);
        var exec = post(q("/api/insight/query-execution/execute"), token, Map.of("queryTemplateId", qtId, "params", Map.of()));
        assertThat(exec.status()).isEqualTo(400);
        assertThat(exec.text()).contains("pg_read_file() is not allowed");
    }
}
