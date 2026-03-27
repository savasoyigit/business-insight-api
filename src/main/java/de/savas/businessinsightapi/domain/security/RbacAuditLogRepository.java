package de.savas.businessinsightapi.domain.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RbacAuditLogRepository extends JpaRepository<RbacAuditLog, Long> {
}
