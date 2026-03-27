package de.savas.businessinsightapi.domain.insight.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface DatasetSnapshotRepository extends JpaRepository<DatasetSnapshot, Long> {
    Optional<DatasetSnapshot> findTopByOrganizationIdAndQueryRunId(Long organizationId, Long queryRunId);
    Page<DatasetSnapshot> findAllByOrganizationIdAndDatasetIdOrderByCreatedAtDesc(Long organizationId, Long datasetId, Pageable pageable);
    Optional<DatasetSnapshot> findByIdAndOrganizationId(Long id, Long organizationId);
}
