package de.savas.businessinsightapi.domain.insight.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetSnapshotRepository extends JpaRepository<DatasetSnapshot, Long> {
    Optional<DatasetSnapshot> findTopByOrganizationIdAndQueryRunId(Long organizationId, Long queryRunId);
    List<DatasetSnapshot> findAllByOrganizationIdAndDatasetIdOrderByCreatedAtDesc(Long organizationId, Long datasetId);
    Optional<DatasetSnapshot> findByIdAndOrganizationId(Long id, Long organizationId);
}
