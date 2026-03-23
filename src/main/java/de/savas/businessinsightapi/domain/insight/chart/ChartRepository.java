package de.savas.businessinsightapi.domain.insight.chart;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartRepository extends JpaRepository<Chart, Long> {
    Page<Chart> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
