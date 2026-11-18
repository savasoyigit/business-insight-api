package de.savas.businessinsightapi.domain.insight.dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    Page<Dataset> findAllByOrganizationId(Long organizationId, Pageable pageable);

    java.util.List<Dataset> findTop50ByRefreshCronIsNotNullAndNextRefreshAtLessThanEqualOrderByNextRefreshAtAsc(java.time.OffsetDateTime now);

    /**
     * Claims a due refresh by moving next_refresh_at forward, only if nobody else did first
     * (compare-and-set), so several API instances never refresh the same dataset twice.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("""
            update Dataset d set d.nextRefreshAt = :next, d.lastRefreshAt = :now
             where d.id = :id and d.nextRefreshAt = :expected
            """)
    int claimRefresh(@org.springframework.data.repository.query.Param("id") Long id,
                     @org.springframework.data.repository.query.Param("expected") java.time.OffsetDateTime expected,
                     @org.springframework.data.repository.query.Param("next") java.time.OffsetDateTime next,
                     @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
}
