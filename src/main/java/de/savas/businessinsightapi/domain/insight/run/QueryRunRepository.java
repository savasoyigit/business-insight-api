package de.savas.businessinsightapi.domain.insight.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryRunRepository extends JpaRepository<QueryRun, Long> {
    Page<QueryRun> findAllByOrganizationId(Long organizationId, Pageable pageable);

    java.util.Optional<QueryRun> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * Fails runs left QUEUED/RUNNING by a crash or restart. Only runs older than the cutoff are touched,
     * so runs that another instance is still executing (bounded by the query timeout) are left alone.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("""
            update QueryRun r
               set r.status = de.savas.businessinsightapi.domain.insight.run.QueryRunStatus.FAILED,
                   r.errorType = 'INTERRUPTED',
                   r.errorMessage = 'The server stopped before the run finished',
                   r.finishedAt = :now
             where r.status in (de.savas.businessinsightapi.domain.insight.run.QueryRunStatus.QUEUED,
                                de.savas.businessinsightapi.domain.insight.run.QueryRunStatus.RUNNING)
               and r.startedAt < :cutoff
            """)
    int failStaleRuns(@org.springframework.data.repository.query.Param("cutoff") java.time.OffsetDateTime cutoff,
                      @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
}
