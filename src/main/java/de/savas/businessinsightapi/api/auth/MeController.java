package de.savas.businessinsightapi.api.auth;

import de.savas.businessinsightapi.domain.organization.OrganizationRepository;
import de.savas.businessinsightapi.infrastructure.security.ApiPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** The caller as the API sees them: username, permissions and the organizations they can work in. */
@RestController
public class MeController {

    private final OrganizationRepository organizationRepository;

    public MeController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public record OrganizationRef(Long id, String name) {}

    public record MeResponse(String username, List<String> permissions, List<OrganizationRef> organizations) {}

    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication) {
        Set<Long> orgIds = authentication.getPrincipal() instanceof ApiPrincipal p ? p.organizationIds() : Set.of();
        List<OrganizationRef> organizations = organizationRepository.findAllById(orgIds).stream()
                .sorted(Comparator.comparing(o -> o.getId()))
                .map(o -> new OrganizationRef(o.getId(), o.getName()))
                .toList();
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return new MeResponse(authentication.getName(), permissions, organizations);
    }
}
