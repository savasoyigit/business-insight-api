package de.savas.businessinsightapi.infrastructure.security;

import de.savas.businessinsightapi.common.error.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tenant isolation for every organization-scoped endpoint ({@code /api/insight/**}).
 * The {@code orgId} request parameter must be present and the caller must be a member of that organization
 * (memberships travel in the JWT {@code orgs} claim). Enforced centrally so new endpoints cannot forget it.
 */
@Component
public class OrganizationAccessInterceptor implements HandlerInterceptor {

    public static final String ORG_PARAM = "orgId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String raw = request.getParameter(ORG_PARAM);
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("orgId is required");
        }

        long orgId;
        try {
            orgId = Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("orgId must be a number");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiPrincipal principal)
                || !principal.isMemberOf(orgId)) {
            throw new AccessDeniedException("Not a member of organization " + orgId);
        }
        return true;
    }
}
