package de.savas.businessinsightapi.domain.insight.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface DatasetSnapshotRepository extends JpaRepository<DatasetSnapshot, Long> {
    Optional<DatasetSnapshot> findTopByOrganizationIdAndQueryRunId(Long organizationId, Long queryRunId);
    Page<DatasetSnapshot> findAllByOrganizationIdAndDatasetIdOrderByCreatedAtDesc(Long organizationId, Long datasetId, Pageable pageable);
    Optional<DatasetSnapshot> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Keeps the newest {@code keep} snapshots of a dataset and deletes the rest. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(value = """
            delete from dataset_snapshot
             where organization_id = :orgId and dataset_id = :datasetId
               and id not in (select id from dataset_snapshot
                               where organization_id = :orgId and dataset_id = :datasetId
                               order by id desc limit :keep)
            """, nativeQuery = true)
    int deleteAllButNewest(@org.springframework.data.repository.query.Param("orgId") Long orgId,
                           @org.springframework.data.repository.query.Param("datasetId") Long datasetId,
                           @org.springframework.data.repository.query.Param("keep") int keep);
}
