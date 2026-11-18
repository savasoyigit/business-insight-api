package de.savas.businessinsightapi.application.organization;

import de.savas.businessinsightapi.application.rbac.RbacAuditService;
import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.organization.Organization;
import de.savas.businessinsightapi.domain.organization.OrganizationRepository;
import de.savas.businessinsightapi.domain.user.User;
import de.savas.businessinsightapi.domain.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class OrganizationAdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RbacAuditService auditService;

    public OrganizationAdminService(OrganizationRepository organizationRepository,
                                    UserRepository userRepository,
                                    RbacAuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Organization> list() {
        return organizationRepository.findAll(Sort.by("id"));
    }

    /** Creates an organization; the creator becomes its first member. */
    @Transactional
    public Organization create(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        Organization saved = organizationRepository.save(new Organization(name.trim()));
        // The creator joins the new organization so they can start working in it (after their next sign-in).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            userRepository.findByUsername(authentication.getName()).ifPresent(creator -> {
                creator.getOrganizationIds().add(saved.getId());
                userRepository.save(creator);
            });
        }
        auditService.log("ORGANIZATION_CREATED", "ORGANIZATION", String.valueOf(saved.getId()), Map.of("name", saved.getName()));
        return saved;
    }

    /** Replaces the user's memberships. Takes effect on the user's next login (memberships live in the JWT). */
    @Transactional
    public User replaceUserOrganizations(Long userId, List<Long> organizationIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        Set<Long> ids = new TreeSet<>(organizationIds);
        if (organizationRepository.findAllById(ids).size() != ids.size()) {
            throw new BadRequestException("One or more organization ids are invalid");
        }

        Set<Long> before = new TreeSet<>(user.getOrganizationIds());
        user.replaceOrganizations(ids);
        User saved = userRepository.save(user);
        auditService.log("USER_ORGANIZATIONS_REPLACED", "USER", String.valueOf(saved.getId()),
                Map.of("before", List.copyOf(before), "after", List.copyOf(ids)));
        return saved;
    }
}
