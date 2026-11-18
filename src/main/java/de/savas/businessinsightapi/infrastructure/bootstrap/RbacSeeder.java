package de.savas.businessinsightapi.infrastructure.bootstrap;

import de.savas.businessinsightapi.domain.organization.Organization;
import de.savas.businessinsightapi.domain.organization.OrganizationRepository;
import de.savas.businessinsightapi.domain.security.Permission;
import de.savas.businessinsightapi.domain.security.PermissionRepository;
import de.savas.businessinsightapi.domain.security.Role;
import de.savas.businessinsightapi.domain.security.RoleRepository;
import de.savas.businessinsightapi.domain.user.User;
import de.savas.businessinsightapi.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RbacSeeder implements ApplicationRunner {

    static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe_DevOnly_123456!";
    private static final Logger log = LoggerFactory.getLogger(RbacSeeder.class);

    private final PermissionRepository permissionRepo;
    private final RoleRepository roleRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final OrganizationRepository organizationRepo;
    private final boolean allowDefaultAdminPassword;

    public RbacSeeder(PermissionRepository permissionRepo, RoleRepository roleRepo,
                      UserRepository userRepo, PasswordEncoder encoder, OrganizationRepository organizationRepo,
                      @Value("${app.security.allow-default-admin-password:true}") boolean allowDefaultAdminPassword) {
        this.allowDefaultAdminPassword = allowDefaultAdminPassword;
        this.permissionRepo = permissionRepo;
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.organizationRepo = organizationRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Permissions (idempotent)
        var perms = List.of(
                perm("INSIGHT_DS_READ", "Read data sources"),
                perm("INSIGHT_DS_CREATE", "Create data sources"),
                perm("INSIGHT_DS_UPDATE", "Update data sources"),
                perm("INSIGHT_DS_DELETE", "Delete data sources"),

                perm("INSIGHT_QT_READ", "Read query templates"),
                perm("INSIGHT_QT_CREATE", "Create query templates"),
                perm("INSIGHT_QT_UPDATE", "Update query templates"),
                perm("INSIGHT_QT_DELETE", "Delete query templates"),

                perm("INSIGHT_QUERY_EXECUTE", "Execute query templates"),

                perm("INSIGHT_DATASET_READ", "Read datasets"),
                perm("INSIGHT_DATASET_CREATE", "Create datasets"),
                perm("INSIGHT_DATASET_UPDATE", "Update datasets"),
                perm("INSIGHT_DATASET_DELETE", "Delete datasets"),

                perm("INSIGHT_CHART_READ", "Read charts"),
                perm("INSIGHT_CHART_CREATE", "Create charts"),
                perm("INSIGHT_CHART_UPDATE", "Update charts"),
                perm("INSIGHT_CHART_DELETE", "Delete charts"),

                perm("INSIGHT_DASHBOARD_READ", "Read dashboards"),
                perm("INSIGHT_DASHBOARD_CREATE", "Create dashboards"),
                perm("INSIGHT_DASHBOARD_UPDATE", "Update dashboards"),
                perm("INSIGHT_DASHBOARD_DELETE", "Delete dashboards"),

                perm("RBAC_ROLE_MANAGE", "Manage roles"),
                perm("RBAC_PERMISSION_MANAGE", "Manage permissions"),
                perm("RBAC_USER_ROLE_ASSIGN", "Assign roles to users"),
                perm("RBAC_ORG_MANAGE", "Manage organizations and memberships")
        );

        perms.forEach(p ->
                permissionRepo.findByCode(p.code).orElseGet(() ->
                        permissionRepo.save(new Permission(p.code, p.desc))
                )
        );

        // ADMIN role
// 1️⃣ ADMIN role ensure
        Role admin = roleRepo.findByName("ADMIN")
                .orElseGet(() -> roleRepo.save(
                        new Role("ADMIN", "System administrator")
                ));

// 2️⃣ Permission sync
        admin.getPermissions().clear();
        permissionRepo.findAll().forEach(admin::addPermission);
        admin = roleRepo.save(admin);

// 3️⃣ Admin user ensure
        String adminUsername = env("ADMIN_USERNAME", "admin");
        String adminEmail    = env("ADMIN_EMAIL", "admin@local.dev");
        String adminPassword = env("ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);

        Optional<User> existing = userRepo.findByUsername(adminUsername);

        Long defaultOrgId = organizationRepo.findFirstByOrderByIdAsc()
                .orElseGet(() -> organizationRepo.save(new Organization("Default Org")))
                .getId();

        if (existing.isEmpty()) {
            // sadece yoksa oluştur
            User u = new User(adminEmail, adminUsername, encoder.encode(adminPassword));
            u.getRoles().add(admin);
            u.getOrganizationIds().add(defaultOrgId);
            userRepo.save(u);
            log.info("Admin created: username={}", adminUsername);
        } else {
            // varsa: kesinlikle insert yok
            log.info("Admin exists, skipping create: username={}", adminUsername);
        }

        // Catches both a fresh admin seeded with the default and an older admin whose password was never changed.
        User adminUser = userRepo.findByUsername(adminUsername).orElseThrow();
        if (encoder.matches(DEFAULT_ADMIN_PASSWORD, adminUser.getPasswordHash())) {
            if (!allowDefaultAdminPassword) {
                throw new IllegalStateException("Admin user '" + adminUsername + "' still uses the default password. "
                        + "Set ADMIN_PASSWORD before the first start, or change the password, then restart.");
            }
            log.warn("Admin user '{}' uses the default development password. Never run like this in production "
                    + "(the prod profile refuses to start).", adminUsername);
        }
    }

    private SeedPerm perm(String code, String desc) { return new SeedPerm(code, desc); }

    private String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static class SeedPerm {
        final String code;
        final String desc;
        SeedPerm(String code, String desc) { this.code = code; this.desc = desc; }
    }
}
