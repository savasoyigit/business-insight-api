package de.savas.businessinsightapi.support;

import de.savas.businessinsightapi.domain.security.PermissionRepository;
import de.savas.businessinsightapi.domain.security.Role;
import de.savas.businessinsightapi.domain.security.RoleRepository;
import de.savas.businessinsightapi.domain.user.User;
import de.savas.businessinsightapi.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base for API integration tests: full Spring context, real security filter chain, real PostgreSQL
 * (see application-test.yml). Tests isolate themselves by creating their own organizations and users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "ChangeMe_DevOnly_123456!";
    protected static final String USER_PASSWORD = "Integration-Test-Pass-42";

    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected PermissionRepository permissionRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- HTTP helpers ----------

    protected Response call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString();
        JsonNode node = text.isBlank() ? null : tryParse(text);
        return new Response(result.getResponse().getStatus(), node, text);
    }

    protected Response get(String url, String token) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token, null);
    }

    protected Response post(String url, String token, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), token, body);
    }

    protected Response put(String url, String token, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url), token, body);
    }

    private JsonNode tryParse(String text) {
        try {
            return json.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    protected String login(String username, String password) throws Exception {
        Response r = post("/api/auth/login", null, java.util.Map.of("username", username, "password", password));
        if (r.status() != 200) {
            throw new AssertionError("login failed for " + username + ": " + r.status() + " " + r.text());
        }
        return r.body().get("accessToken").asString();
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_USER, ADMIN_PASSWORD);
    }

    // ---------- fixtures ----------

    /** Creates an organization through the admin API; the seeded admin, as creator, becomes a member. */
    protected long createOrganization(String adminToken) throws Exception {
        Response r = post("/api/admin/rbac/organizations", adminToken, java.util.Map.of("name", unique("org")));
        if (r.status() != 200) {
            throw new AssertionError("create org failed: " + r.status() + " " + r.text());
        }
        long orgId = r.body().get("id").asLong();
        User admin = userRepository.findByUsername(ADMIN_USER).orElseThrow();
        if (!admin.isMemberOf(orgId)) {
            throw new AssertionError("creator should become a member of the new organization");
        }
        return orgId;
    }

    /** Creates a user with a fresh role holding exactly the given permissions and the given memberships. */
    protected User createUser(Collection<String> permissionCodes, Set<Long> organizationIds) {
        String name = unique("user");
        User user = new User(name + "@it.local", name, passwordEncoder.encode(USER_PASSWORD));
        if (!permissionCodes.isEmpty()) {
            Role role = new Role(unique("ROLE").toUpperCase(), "integration test role");
            permissionRepository.findAllByCodeIn(java.util.List.copyOf(permissionCodes)).forEach(role::addPermission);
            user.getRoles().add(roleRepository.save(role));
        }
        user.getOrganizationIds().addAll(organizationIds);
        return userRepository.save(user);
    }

    public record Response(int status, JsonNode body, String text) {
        @Override
        public String toString() {
            return status + " " + text;
        }
    }
}
