package de.savas.businessinsightapi.infrastructure.security;

import java.security.Principal;
import java.util.Set;

/**
 * The authenticated caller, built from the JWT. Implements {@link Principal} so
 * {@code Authentication#getName()} keeps returning the username.
 */
public record ApiPrincipal(String username, Set<Long> organizationIds) implements Principal {

    public ApiPrincipal {
        organizationIds = Set.copyOf(organizationIds);
    }

    @Override
    public String getName() {
        return username;
    }

    public boolean isMemberOf(Long organizationId) {
        return organizationId != null && organizationIds.contains(organizationId);
    }

    @Override
    public String toString() {
        return username;
    }
}
