package de.savas.businessinsightapi.api.admin;

import de.savas.businessinsightapi.application.organization.OrganizationAdminService;
import de.savas.businessinsightapi.application.rbac.RbacAdminService;
import de.savas.businessinsightapi.api.admin.AdminRbacDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rbac")
public class AdminRbacController {

    private final RbacAdminService service;
    private final OrganizationAdminService organizationService;

    public AdminRbacController(RbacAdminService service, OrganizationAdminService organizationService) {
        this.service = service;
        this.organizationService = organizationService;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('RBAC_PERMISSION_MANAGE')")
    public List<PermissionResponse> listPermissions() {
        return service.listPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('RBAC_ROLE_MANAGE')")
    public List<RoleResponse> listRoles() {
        return service.listRoles().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('RBAC_ROLE_MANAGE')")
    public RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(service.createRole(request.name(), request.description()));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('RBAC_ROLE_MANAGE')")
    public RoleResponse updateRoleDescription(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return RoleResponse.from(service.updateRoleDescription(id, request.description()));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('RBAC_ROLE_MANAGE')")
    public RoleResponse replaceRolePermissions(@PathVariable Long id, @Valid @RequestBody ReplaceRolePermissionsRequest request) {
        return RoleResponse.from(service.replaceRolePermissions(id, request.permissionCodes()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('RBAC_USER_ROLE_ASSIGN')")
    public List<UserRoleResponse> listUsers(@RequestParam(required = false) String query) {
        return service.listUsers(query).stream()
                .map(UserRoleResponse::from)
                .toList();
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('RBAC_ROLE_MANAGE')")
    public Page<AuditLogResponse> listAuditLogs(Pageable pageable) {
        return service.listAuditLogs(pageable).map(AuditLogResponse::from);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('RBAC_USER_ROLE_ASSIGN')")
    public UserRoleResponse replaceUserRoles(@PathVariable Long id, @Valid @RequestBody ReplaceUserRolesRequest request) {
        return UserRoleResponse.from(service.replaceUserRoles(id, request.roleNames()));
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAuthority('RBAC_ORG_MANAGE')")
    public List<OrganizationResponse> listOrganizations() {
        return organizationService.list().stream().map(OrganizationResponse::from).toList();
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasAuthority('RBAC_ORG_MANAGE')")
    public OrganizationResponse createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return OrganizationResponse.from(organizationService.create(request.name()));
    }

    @PutMapping("/users/{id}/organizations")
    @PreAuthorize("hasAuthority('RBAC_ORG_MANAGE')")
    public UserRoleResponse replaceUserOrganizations(@PathVariable Long id, @Valid @RequestBody ReplaceUserOrganizationsRequest request) {
        return UserRoleResponse.from(organizationService.replaceUserOrganizations(id, request.organizationIds()));
    }
}
