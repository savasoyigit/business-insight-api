package de.savas.businessinsightapi;

import de.savas.businessinsightapi.application.insight.DatasetRefreshScheduler;
import de.savas.businessinsightapi.application.insight.QueryRunJobService;
import de.savas.businessinsightapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** M3: asynchronous runs, coalescing, recovery, scheduled refresh and snapshot retention (retention = 3 in tests). */
class AsyncAndScheduleTest extends IntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatasetRefreshScheduler scheduler;
    @Autowired private QueryRunJobService jobService;

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

    private void requireLiveDb() {
        assumeTrue(System.getenv("DS_SECRET_TEST_DB") != null, "DS_SECRET_TEST_DB not set; skipping live execution test");
    }

    private long dataSource(String host, int port) throws Exception {
        var r = post(q("/api/insight/data-sources"), token, Map.of(
                "name", unique("ds"), "type", "POSTGRES", "host", host, "port", port,
                "databaseName", env("TEST_DB_NAME", "businessinsight_test"), "username", env("TEST_DB_USER", "app"),
                "secretRef", "env:DS_SECRET_TEST_DB"));
        assertThat(r.status()).as(r.text()).isEqualTo(200);
        return r.body().get("id").asLong();
    }

    private long liveDataSource() throws Exception {
        return dataSource(env("TEST_DB_HOST", "localhost"), Integer.parseInt(env("TEST_DB_PORT", "5432")));
    }

    private long template(long dsId, String sql, List<Map<String, Object>> parameters) throws Exception {
        var r = post(q("/api/insight/query-templates"), token, Map.of("dataSourceId", dsId, "name", unique("qt"), "sqlText", sql, "parameters", parameters));
        assertThat(r.status()).as(r.text()).isEqualTo(200);
        return r.body().get("id").asLong();
    }

    private JsonNode awaitRun(long runId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode run = get(q("/api/insight/query-execution/runs/" + runId), token).body();
            String status = run.get("status").asString();
            if (status.equals("SUCCESS") || status.equals("FAILED")) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run " + runId + " did not finish in time");
    }

    @Test
    void asyncRunIsQueuedThenCompletesWithSnapshot() throws Exception {
        requireLiveDb();
        long qt = template(liveDataSource(), "select n from generate_series(1, :upto) as n",
                List.of(Map.of("name", "upto", "type", "int", "required", true)));

        var submitted = post(q("/api/insight/query-execution/jobs"), token, Map.of("queryTemplateId", qt, "params", Map.of("upto", 7), "snapshot", true));
        assertThat(submitted.status()).as(submitted.text()).isEqualTo(202);
        assertThat(submitted.body().get("status").asString()).isEqualTo("QUEUED");

        JsonNode run = awaitRun(submitted.body().get("queryRunId").asLong());
        assertThat(run.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(run.get("rowCount").asInt()).isEqualTo(7);
        assertThat(run.get("snapshotId").isNull()).isFalse();
    }

    @Test
    void asyncValidationErrorsAreImmediate400s() throws Exception {
        long qt = template(dataSource("localhost", 5432), "select :x as x", List.of(Map.of("name", "x", "type", "int", "required", true)));
        var r = post(q("/api/insight/query-execution/jobs"), token, Map.of("queryTemplateId", qt, "params", Map.of()));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void asyncFailuresAreRecordedOnTheRun() throws Exception {
        requireLiveDb();
        long qt = template(dataSource("localhost", 1), "select 1 as one", List.of()); // nothing listens on port 1
        var submitted = post(q("/api/insight/query-execution/jobs"), token, Map.of("queryTemplateId", qt, "params", Map.of()));
        JsonNode run = awaitRun(submitted.body().get("queryRunId").asLong());
        assertThat(run.get("status").asString()).isEqualTo("FAILED");
        assertThat(run.get("errorType").asString()).isNotBlank();
    }

    @Test
    void identicalInFlightSubmissionsShareOneRun() throws Exception {
        requireLiveDb();
        long qt = template(liveDataSource(), "select pg_sleep(1) is null as slept", List.of());
        Map<String, Object> body = Map.of("queryTemplateId", qt, "params", Map.of());

        var first = post(q("/api/insight/query-execution/jobs"), token, body);
        var second = post(q("/api/insight/query-execution/jobs"), token, body);
        assertThat(second.body().get("queryRunId").asLong()).isEqualTo(first.body().get("queryRunId").asLong());
        assertThat(second.body().get("coalesced").asBoolean()).isTrue();

        assertThat(awaitRun(first.body().get("queryRunId").asLong()).get("status").asString()).isEqualTo("SUCCESS");
        var third = post(q("/api/insight/query-execution/jobs"), token, body);
        assertThat(third.body().get("queryRunId").asLong()).as("a finished run is not reused").isNotEqualTo(first.body().get("queryRunId").asLong());
        awaitRun(third.body().get("queryRunId").asLong());
    }

    @Test
    void runsAreScopedToTheirOrganization() throws Exception {
        requireLiveDb();
        long qt = template(liveDataSource(), "select 1 as one", List.of());
        long runId = post(q("/api/insight/query-execution/jobs"), token, Map.of("queryTemplateId", qt, "params", Map.of())).body().get("queryRunId").asLong();
        awaitRun(runId);

        String admin = adminToken();
        long otherOrg = createOrganization(admin);
        String fresh = adminToken();
        assertThat(get("/api/insight/query-execution/runs/" + runId + "?orgId=" + otherOrg, fresh).status()).isEqualTo(400);
    }

    @Test
    void stuckRunsAreMarkedInterrupted() {
        Long runId = jdbc.queryForObject("""
                insert into query_run(organization_id, query_template_id, data_source_id, status, requested_by, started_at)
                values (?, (select id from query_template limit 1), (select id from data_source limit 1), 'RUNNING', 'it', now() - interval '1 hour')
                returning id""", Long.class, org);
        assumeTrue(runId != null);

        assertThat(jobService.recoverStaleRuns()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status || '/' || error_type from query_run where id = ?", String.class, runId))
                .isEqualTo("FAILED/INTERRUPTED");
    }

    @Test
    void refreshSchedulesAreValidated() throws Exception {
        long qt = template(dataSource("localhost", 5432), "select 1 as one", List.of());
        String url = q("/api/insight/datasets");

        assertThat(post(url, token, Map.of("name", unique("d"), "queryTemplateId", qt, "refreshCron", "not a cron")).status()).isEqualTo(400);
        var tooOften = post(url, token, Map.of("name", unique("d"), "queryTemplateId", qt, "refreshCron", "* * * * *"));
        assertThat(tooOften.status()).isEqualTo(400);
        assertThat(tooOften.body().get("message").asString()).contains("minimum interval");

        var daily = post(url, token, Map.of("name", unique("d"), "queryTemplateId", qt, "refreshCron", "0 6 * * *"));
        assertThat(daily.status()).as(daily.text()).isEqualTo(201);
        assertThat(daily.body().get("refreshCron").asString()).isEqualTo("0 6 * * *");
        assertThat(OffsetDateTime.parse(daily.body().get("nextRefreshAt").asString())).isAfter(OffsetDateTime.now());

        long id = daily.body().get("id").asLong();
        var cleared = put(q("/api/insight/datasets/" + id), token, Map.of("refreshCron", ""));
        assertThat(cleared.body().get("refreshCron").isNull()).isTrue();
        assertThat(cleared.body().get("nextRefreshAt").isNull()).isTrue();
        var macro = put(q("/api/insight/datasets/" + id), token, Map.of("refreshCron", "@hourly"));
        assertThat(macro.status()).isEqualTo(200);
    }

    @Test
    void dueDatasetsAreRefreshedOnceAndRescheduled() throws Exception {
        requireLiveDb();
        long qt = template(liveDataSource(), "select 42 as answer", List.of());
        var dataset = post(q("/api/insight/datasets"), token, Map.of("name", unique("d"), "queryTemplateId", qt, "refreshCron", "@hourly"));
        long id = dataset.body().get("id").asLong();
        jdbc.update("update dataset set next_refresh_at = now() - interval '1 minute' where id = ?", id);

        assertThat(scheduler.refreshDueDatasets()).isGreaterThanOrEqualTo(1);
        assertThat(scheduler.refreshDueDatasets()).as("already claimed; not due again").isZero();

        long deadline = System.currentTimeMillis() + 15_000;
        JsonNode snapshots;
        do {
            Thread.sleep(100);
            snapshots = get(q("/api/insight/datasets/" + id + "/snapshots"), token).body().get("content");
        } while (snapshots.isEmpty() && System.currentTimeMillis() < deadline);
        assertThat(snapshots.size()).isEqualTo(1);
        assertThat(snapshots.get(0).get("data").get(0).get("answer").asInt()).isEqualTo(42);

        JsonNode refreshed = get(q("/api/insight/datasets/" + id), token).body();
        assertThat(OffsetDateTime.parse(refreshed.get("nextRefreshAt").asString())).isAfter(OffsetDateTime.now());
        assertThat(refreshed.get("lastRefreshAt").isNull()).isFalse();
        assertThat(jdbc.queryForObject("select requested_by from query_run where id = ?", String.class,
                snapshots.get(0).get("queryRunId").asLong())).isEqualTo(DatasetRefreshScheduler.SCHEDULER_USER);
    }

    @Test
    void onlyTheNewestSnapshotsAreKept() throws Exception {
        requireLiveDb();
        long qt = template(liveDataSource(), "select 1 as one", List.of());
        long id = post(q("/api/insight/datasets"), token, Map.of("name", unique("d"), "queryTemplateId", qt)).body().get("id").asLong();
        long last = 0;
        for (int i = 0; i < 5; i++) {
            last = post(q("/api/insight/datasets/" + id + "/snapshots"), token, null).body().get("snapshotId").asLong();
        }
        JsonNode page = get(q("/api/insight/datasets/" + id + "/snapshots"), token).body();
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        assertThat(page.get("content").get(0).get("id").asLong()).isEqualTo(last);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
