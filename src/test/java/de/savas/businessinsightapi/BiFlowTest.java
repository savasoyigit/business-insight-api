package de.savas.businessinsightapi;

import de.savas.businessinsightapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The whole BI pipeline over HTTP: DataSource -> QueryTemplate -> Dataset -> Snapshot -> Chart -> Dashboard.
 * The "external" database is the test database itself; its password is resolved through {@code env:TEST_DB_PASSWORD},
 * so the test is skipped when that variable is not set.
 */
class BiFlowTest extends IntegrationTestSupport {

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

    @Test
    void jsonObjectFieldsRoundTrip() throws Exception {
        var ds = post(q("/api/insight/data-sources"), token, Map.of(
                "name", "json", "type", "POSTGRES", "host", "h", "port", 5432, "databaseName", "d",
                "username", "u", "secretRef", "env:X", "options", Map.of("ssl", true, "nested", Map.of("a", List.of(1, 2)))));
        assertThat(ds.status()).isEqualTo(200);
        assertThat(ds.body().get("options").get("nested").get("a").get(1).asInt()).isEqualTo(2);
    }

    @Test
    void guardrailsRejectNonSelectSql() throws Exception {
        long dsId = post(q("/api/insight/data-sources"), token, Map.of(
                "name", "guard", "type", "POSTGRES", "host", "h", "port", 5432, "databaseName", "d",
                "username", "u", "secretRef", "env:X")).body().get("id").asLong();

        for (String sql : List.of("delete from it_sales", "select 1; drop table it_sales", "select * from ${table}",
                "select * into it_copy from it_sales", "select * from it_sales for update")) {
            var r = post(q("/api/insight/query-templates"), token, Map.of("dataSourceId", dsId, "name", unique("bad"), "sqlText", sql));
            assertThat(r.status()).as(sql).isEqualTo(400);
        }
    }

    @Test
    void fullPipelineRendersChartsAndDashboards() throws Exception {
        assumeTrue(System.getenv("TEST_DB_PASSWORD") != null, "TEST_DB_PASSWORD not set; skipping live execution test");

        jdbc.execute("create table if not exists it_sales(order_date date, region text, amount numeric)");
        jdbc.execute("truncate it_sales");
        jdbc.update("insert into it_sales values ('2026-01-05','Berlin',10),('2026-01-20','Berlin',5),"
                + "('2026-01-07','Munich',7),('2026-02-02','Berlin',3)");

        var ds = post(q("/api/insight/data-sources"), token, liveDataSource("it-db"));
        assertThat(ds.status()).isEqualTo(200);
        long dsId = ds.body().get("id").asLong();

        var test = post(q("/api/insight/data-sources/" + dsId + "/test"), token, null);
        assertThat(test.body().get("ok").asBoolean()).as(test.text()).isTrue();

        var qt = post(q("/api/insight/query-templates"), token, Map.of(
                "dataSourceId", dsId, "name", "sales by region",
                "sqlText", "select to_char(order_date, 'YYYY-MM') as month, region, amount from it_sales where order_date >= :startDate",
                "parameters", List.of(Map.of("name", "startDate", "type", "date", "required", true))));
        assertThat(qt.status()).as(qt.text()).isEqualTo(200);
        long qtId = qt.body().get("id").asLong();

        var validation = post(q("/api/insight/query-execution/validate"), token, Map.of("queryTemplateId", qtId, "params", Map.of("startDate", "2026-01-01")));
        assertThat(validation.body().get("valid").asBoolean()).as(validation.text()).isTrue();

        var exec = post(q("/api/insight/query-execution/execute"), token, Map.of("queryTemplateId", qtId, "params", Map.of("startDate", "2026-01-01")));
        assertThat(exec.body().get("status").asString()).as(exec.text()).isEqualTo("SUCCESS");
        assertThat(exec.body().get("rowCount").asInt()).isEqualTo(4);

        var dataset = post(q("/api/insight/datasets"), token, Map.of("name", "sales", "queryTemplateId", qtId, "defaultParams", Map.of("startDate", "2026-01-01")));
        assertThat(dataset.status()).as(dataset.text()).isEqualTo(201);
        long datasetId = dataset.body().get("id").asLong();

        var snapshot = post(q("/api/insight/datasets/" + datasetId + "/snapshots"), token, null);
        assertThat(snapshot.status()).as(snapshot.text()).isEqualTo(201);
        long snapshotId = snapshot.body().get("snapshotId").asLong();

        var chart = post(q("/api/insight/charts"), token, Map.of("name", "revenue", "type", "BAR", "datasetId", datasetId,
                "config", Map.of("xField", "region", "yField", "amount", "aggregation", "sum")));
        assertThat(chart.status()).as(chart.text()).isEqualTo(201);
        long chartId = chart.body().get("id").asLong();
        assertThat(chart.body().get("config").get("xField").asString()).isEqualTo("region");

        JsonNode points = get(q("/api/insight/charts/" + chartId + "/render?snapshotId=" + snapshotId), token).body().get("points");
        Map<String, Double> byRegion = new java.util.HashMap<>();
        points.forEach(p -> byRegion.put(p.get("x").asString(), p.get("value").asDouble()));
        assertThat(byRegion).containsEntry("Berlin", 18.0).containsEntry("Munich", 7.0);

        var dashboard = post(q("/api/insight/dashboards"), token, Map.of("name", "overview",
                "widgets", List.of(Map.of("chartId", chartId, "layout", Map.of("x", 0, "y", 0, "w", 6, "h", 4)))));
        assertThat(dashboard.status()).as(dashboard.text()).isEqualTo(201);
        long dashboardId = dashboard.body().get("id").asLong();
        JsonNode widget = get(q("/api/insight/dashboards/" + dashboardId), token).body().get("widgets").get(0);
        assertThat(widget.get("layout").get("w").asInt()).isEqualTo(6);

        var runs = get(q("/api/insight/query-execution/runs?size=10"), token);
        assertThat(runs.body().get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    /** A SELECT that writes (nextval) passes the SQL checks; the read-only transaction must still stop it. */
    @Test
    void databaseRefusesWritesThatPassTheSqlChecks() throws Exception {
        assumeTrue(System.getenv("TEST_DB_PASSWORD") != null, "TEST_DB_PASSWORD not set; skipping live execution test");
        jdbc.execute("create sequence if not exists it_seq");
        long before = jdbc.queryForObject("select last_value from it_seq", Long.class);

        long dsId = post(q("/api/insight/data-sources"), token, liveDataSource("readonly-check")).body().get("id").asLong();
        var qt = post(q("/api/insight/query-templates"), token, Map.of(
                "dataSourceId", dsId, "name", "sneaky write", "sqlText", "select nextval('it_seq') as n"));
        assertThat(qt.status()).as("passes the SQL checks").isEqualTo(200);

        var exec = post(q("/api/insight/query-execution/execute"), token, Map.of("queryTemplateId", qt.body().get("id").asLong(), "params", Map.of()));
        assertThat(exec.status()).isEqualTo(400);
        assertThat(exec.body().get("message").asString()).contains("read-only transaction");
        assertThat(jdbc.queryForObject("select last_value from it_seq", Long.class)).isEqualTo(before);
    }

    @Test
    void passwordsAreStoredEncryptedAndNeverReturned() throws Exception {
        String password = env("TEST_DB_PASSWORD", "app");
        Map<String, Object> body = new java.util.HashMap<>(liveDataSource("encrypted"));
        body.remove("secretRef");
        body.put("password", password);

        var created = post(q("/api/insight/data-sources"), token, body);
        assertThat(created.status()).as(created.text()).isEqualTo(200);
        assertThat(created.body().get("secretType").asString()).isEqualTo("ENCRYPTED");
        assertThat(created.body().get("secretRef").isNull()).isTrue();
        long id = created.body().get("id").asLong();

        String stored = jdbc.queryForObject("select secret_ref from data_source where id = ?", String.class, id);
        assertThat(stored).startsWith("enc:v1:").doesNotContain(password);

        String listed = get(q("/api/insight/data-sources?size=100"), token).text();
        assertThat(listed).doesNotContain(stored).doesNotContain("enc:v1:");

        if (System.getenv("TEST_DB_PASSWORD") != null) {
            var test = post(q("/api/insight/data-sources/" + id + "/test"), token, null);
            assertThat(test.body().get("ok").asBoolean()).as("connects with the decrypted password: " + test.text()).isTrue();
        }

        // switching back to an environment reference
        var updated = put(q("/api/insight/data-sources/" + id), token, Map.of("secretRef", "env:TEST_DB_PASSWORD"));
        assertThat(updated.body().get("secretType").asString()).isEqualTo("ENV");
    }

    @Test
    void secretInputIsValidated() throws Exception {
        Map<String, Object> both = new java.util.HashMap<>(liveDataSource("both"));
        both.put("password", "x");
        assertThat(post(q("/api/insight/data-sources"), token, both).status()).isEqualTo(400);

        Map<String, Object> none = new java.util.HashMap<>(liveDataSource("none"));
        none.remove("secretRef");
        assertThat(post(q("/api/insight/data-sources"), token, none).status()).isEqualTo(400);

        Map<String, Object> injected = new java.util.HashMap<>(liveDataSource("injected"));
        injected.put("secretRef", "enc:v1:AAAA");
        assertThat(post(q("/api/insight/data-sources"), token, injected).status()).isEqualTo(400);
    }

    private Map<String, Object> liveDataSource(String name) {
        return Map.of(
                "name", name, "type", "POSTGRES",
                "host", env("TEST_DB_HOST", "localhost"), "port", Integer.parseInt(env("TEST_DB_PORT", "5432")),
                "databaseName", env("TEST_DB_NAME", "businessinsight_test"), "username", env("TEST_DB_USER", "app"),
                "secretRef", "env:TEST_DB_PASSWORD");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
