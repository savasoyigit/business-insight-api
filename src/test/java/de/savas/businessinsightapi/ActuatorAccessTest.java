package de.savas.businessinsightapi;

import de.savas.businessinsightapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Registration is open, so being signed in must not be enough to read operational endpoints. */
// Tests disable metrics exporters by default; this one checks the Prometheus endpoint, so it turns them on.
@AutoConfigureMetrics
class ActuatorAccessTest extends IntegrationTestSupport {

    private static final String SCRAPE_TOKEN = "integration-test-scrape-token-0123456789";
    private static final List<String> PROTECTED = List.of("/actuator/metrics", "/actuator/info", "/actuator/prometheus");

    private int status(String path, String authorization) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void healthStaysPublic() throws Exception {
        assertThat(status("/actuator/health", null)).isEqualTo(200);
    }

    @Test
    void anonymousAndUnprivilegedUsersAreRefused() throws Exception {
        var user = createUser(List.of("INSIGHT_DS_READ"), Set.of());
        String token = login(user.getUsername(), USER_PASSWORD);
        for (String path : PROTECTED) {
            assertThat(status(path, null)).as(path).isEqualTo(401);
            assertThat(status(path, "Bearer " + token)).as(path).isEqualTo(403);
        }
    }

    @Test
    void permissionGrantsAccessAndPrometheusWorks() throws Exception {
        var user = createUser(List.of("OPS_METRICS_ACCESS"), Set.of());
        String token = login(user.getUsername(), USER_PASSWORD);
        for (String path : PROTECTED) {
            assertThat(status(path, "Bearer " + token)).as(path).isEqualTo(200);
        }
        var prometheus = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(prometheus).contains("# HELP").contains("jvm_");
    }

    @Test
    void scrapeTokenOpensOnlyPrometheus() throws Exception {
        assertThat(status("/actuator/prometheus", "Bearer " + SCRAPE_TOKEN)).isEqualTo(200);
        assertThat(status("/actuator/metrics", "Bearer " + SCRAPE_TOKEN)).isIn(401, 403);
        assertThat(status("/actuator/prometheus", "Bearer " + SCRAPE_TOKEN + "x")).isEqualTo(401);
        assertThat(status("/actuator/prometheus", SCRAPE_TOKEN)).isEqualTo(401);
    }

    @Test
    void unknownPathsAre404AndWrongMethodsAre405() throws Exception {
        String admin = adminToken();
        var notFound = get("/api/does-not-exist", admin);
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(notFound.body().get("message").asString()).isEqualTo("Not found");
        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me"), admin, null).status())
                .isEqualTo(405);
    }
}
