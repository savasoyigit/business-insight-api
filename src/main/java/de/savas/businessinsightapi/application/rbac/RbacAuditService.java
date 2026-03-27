package de.savas.businessinsightapi.application.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.savas.businessinsightapi.domain.security.RbacAuditLog;
import de.savas.businessinsightapi.domain.security.RbacAuditLogRepository;
import de.savas.businessinsightapi.infrastructure.security.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class RbacAuditService {

    private final RbacAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public RbacAuditService(RbacAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void log(String action, String targetType, String targetId, Map<String, Object> details) {
        JsonNode detailsJson = objectMapper.valueToTree(details == null ? Map.of() : details);
        RbacAuditLog entry = new RbacAuditLog(
                currentUsername(),
                action,
                targetType,
                targetId,
                MDC.get(CorrelationIdFilter.CORRELATION_MDC_KEY),
                detailsJson
        );
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<RbacAuditLog> list(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
