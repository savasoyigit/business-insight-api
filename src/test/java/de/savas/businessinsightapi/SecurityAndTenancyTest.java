package de.savas.businessinsightapi;

import de.savas.businessinsightapi.domain.user.User;
import de.savas.businessinsightapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAndTenancyTest extends IntegrationTestSupport {

    private static final List<String> DS_PERMS = List.of("INSIGHT_DS_READ", "INSIGHT_DS_CREATE");

    private Map<String, Object> dataSource(String name) {
        return Map.of("name", name, "type", "POSTGRES", "host", "localhost", "port", 5432,
                "databaseName", "db", "username", "reader", "secretRef", "env:NOT_USED");
    }

    @Test
    void requestsWithoutValidTokenGet401() throws Exception {
        assertThat(get("/api/insight/charts?orgId=1", null).status()).isEqualTo(401);
        assertThat(get("/api/insight/charts?orgId=1", "not-a-jwt").status()).isEqualTo(401);

        var badLogin = post("/api/auth/login", null, Map.of("username", ADMIN_USER, "password", "wrong-password"));
        assertThat(badLogin.status()).isEqualTo(401);
        assertThat(badLogin.body().get("message").asString()).isEqualTo("Invalid credentials");
    }

    @Test
    void missingPermissionGets403NotServerError() throws Exception {
        String admin = adminToken();
        long org = createOrganization(admin);
        User noRoles = createUser(List.of(), Set.of(org));
        String token = login(noRoles.getUsername(), USER_PASSWORD);

        var r = get("/api/insight/charts?orgId=" + org, token);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body().get("message").asString()).isEqualTo("Access denied");
        assertThat(get("/api/admin/rbac/roles", token).status()).isEqualTo(403);
    }

    @Test
    void usersOnlyReachOrganizationsTheyBelongTo() throws Exception {
        String admin = adminToken();
        long orgA = createOrganization(admin);
        long orgB = createOrganization(admin);
        String adminFresh = adminToken(); // memberships are read from the token

        var secret = post("/api/insight/data-sources?orgId=" + orgB, adminFresh, dataSource("org B secret"));
        assertThat(secret.status()).isEqualTo(200);
        long secretId = secret.body().get("id").asLong();

        User alice = createUser(DS_PERMS, Set.of(orgA));
        String token = login(alice.getUsername(), USER_PASSWORD);

        assertThat(get("/api/insight/data-sources?orgId=" + orgA, token).status()).isEqualTo(200);
        assertThat(get("/api/insight/data-sources?orgId=" + orgB, token).status()).isEqualTo(403);
        assertThat(get("/api/insight/data-sources/" + secretId + "?orgId=" + orgB, token).status()).isEqualTo(403);
        assertThat(post("/api/insight/data-sources?orgId=" + orgB, token, dataSource("sneaky")).status()).isEqualTo(403);
        // Asking for B's record through her own org must not find it either.
        assertThat(get("/api/insight/data-sources/" + secretId + "?orgId=" + orgA, token).status()).isEqualTo(400);
    }

    @Test
    void orgIdMustBePresentAndNumeric() throws Exception {
        String admin = adminToken();
        assertThat(get("/api/insight/data-sources", admin).status()).isEqualTo(400);
        var r = get("/api/insight/data-sources?orgId=abc", admin);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().get("message").asString()).isEqualTo("orgId must be a number");
    }

    @Test
    void adminManagesMembershipsWithAuditTrail() throws Exception {
        String admin = adminToken();
        long org = createOrganization(admin);
        User bob = createUser(DS_PERMS, Set.of());
        String before = login(bob.getUsername(), USER_PASSWORD);
        assertThat(get("/api/insight/data-sources?orgId=" + org, before).status()).isEqualTo(403);
        assertThat(get("/api/me", before).body().get("organizations").size()).isZero();

        var invalid = put("/api/admin/rbac/users/" + bob.getId() + "/organizations", admin, Map.of("organizationIds", List.of(org, 999_999_999L)));
        assertThat(invalid.status()).isEqualTo(400);

        var assigned = put("/api/admin/rbac/users/" + bob.getId() + "/organizations", admin, Map.of("organizationIds", List.of(org)));
        assertThat(assigned.status()).isEqualTo(200);
        assertThat(assigned.body().get("organizationIds").get(0).asLong()).isEqualTo(org);

        String after = login(bob.getUsername(), USER_PASSWORD);
        assertThat(get("/api/insight/data-sources?orgId=" + org, after).status()).isEqualTo(200);
        var me = get("/api/me", after).body();
        assertThat(me.get("username").asString()).isEqualTo(bob.getUsername());
        assertThat(me.get("organizations").get(0).get("id").asLong()).isEqualTo(org);

        var audit = get("/api/admin/rbac/audit-logs?size=50&sort=id,desc", admin).body().get("content");
        boolean logged = false;
        for (var entry : audit) {
            if (entry.get("action").asString().equals("USER_ORGANIZATIONS_REPLACED")
                    && entry.get("targetId").asString().equals(String.valueOf(bob.getId()))) {
                logged = true;
            }
        }
        assertThat(logged).as("membership change is audited").isTrue();
    }

    @Test
    void conflictsAndBadBodiesAreClientErrors() throws Exception {
        String admin = adminToken();
        long org = createOrganization(admin);
        String token = adminToken();

        assertThat(post("/api/insight/data-sources?orgId=" + org, token, dataSource("dup")).status()).isEqualTo(200);
        assertThat(post("/api/insight/data-sources?orgId=" + org, token, dataSource("dup")).status()).isEqualTo(409);
        assertThat(post("/api/insight/data-sources?orgId=" + org, token, "{\"name\": ").status()).isEqualTo(400);
    }
}
