package de.savas.businessinsightapi.domain.insight.dashboard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {
    Page<Dashboard> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
