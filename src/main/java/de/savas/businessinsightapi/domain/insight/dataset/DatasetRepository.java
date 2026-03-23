package de.savas.businessinsightapi.domain.insight.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    Page<Dataset> findAllByOrganizationId(Long organizationId, Pageable pageable);
}
