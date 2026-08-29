package com.waad.tba.modules.member.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportLog.ImportStatus;

@Repository
public interface MemberImportLogRepository extends JpaRepository<MemberImportLog, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from MemberImportLog l where l.id = :id")
    java.util.Optional<MemberImportLog> findByIdForRollback(
            @org.springframework.data.repository.query.Param("id") Long id);
    
    /**
     * Find by batch ID
     */
    Optional<MemberImportLog> findByImportBatchId(String importBatchId);

    /**
     * Idempotency lookup: has this exact logical import (same file bytes AND
     * the same employer/benefitPolicy/headerRow/clearOldMembers choices --
     * see MemberImportLog.importScopeHash) already completed?
     */
    Optional<MemberImportLog> findByImportScopeHashAndStatus(String importScopeHash, ImportStatus status);
    
    /**
     * Find by user with pagination
     */
    Page<MemberImportLog> findByImportedByUserId(Long userId, Pageable pageable);
    
    /**
     * Find by company scope with pagination
     */
    Page<MemberImportLog> findByCompanyScopeId(Long companyScopeId, Pageable pageable);

    /**
     * The four filters the history screen offers, applied identically on both
     * the global and the employer-scoped read.
     *
     * They are written twice -- once in JPQL here, once inside the native
     * scoped query below -- because the scope predicate needs a JSONB
     * subquery that JPQL cannot express. Two spellings of one filter set is a
     * duplication worth watching, so MemberImportLogFilterIntegrationTest
     * runs the same cases through both and fails if they ever disagree.
     *
     * `search` matches the file name, the batch id and who ran it: those are
     * the three things someone has in hand when they come here to trace a
     * particular import.
     */
    @Query("""
            select l from MemberImportLog l
            where (cast(:status as String) is null or l.status = :status)
              and (cast(:searchPattern as String) is null
                   or lower(l.fileName) like :searchPattern
                   or lower(l.importBatchId) like :searchPattern
                   or lower(l.importedByUsername) like :searchPattern)
              and (cast(:fromDate as LocalDateTime) is null or l.createdAt >= :fromDate)
              and (cast(:toDate as LocalDateTime) is null or l.createdAt < :toDate)
            """)
    Page<MemberImportLog> findFiltered(
            @Param("status") ImportStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query(value = """
            select l.* from member_import_logs l
            where exists (select 1 from member_import_batch_rows r where r.import_log_id = l.id)
              and not exists (
                  select 1 from member_import_batch_rows r
                  where r.import_log_id = l.id
                    and ((r.imported_snapshot ->> 'employerId') is null
                         or cast(r.imported_snapshot ->> 'employerId' as bigint) not in (:employerIds))
              )
              and (cast(:status as text) is null or l.status = cast(:status as text))
              and (cast(:searchPattern as text) is null
                   or lower(l.file_name) like cast(:searchPattern as text)
                   or lower(l.import_batch_id) like cast(:searchPattern as text)
                   or lower(l.imported_by_username) like cast(:searchPattern as text))
              and (cast(:fromDate as timestamp) is null or l.created_at >= cast(:fromDate as timestamp))
              and (cast(:toDate as timestamp) is null or l.created_at < cast(:toDate as timestamp))
            order by l.created_at desc
            """,
            countQuery = """
            select count(*) from member_import_logs l
            where exists (select 1 from member_import_batch_rows r where r.import_log_id = l.id)
              and not exists (
                  select 1 from member_import_batch_rows r
                  where r.import_log_id = l.id
                    and ((r.imported_snapshot ->> 'employerId') is null
                         or cast(r.imported_snapshot ->> 'employerId' as bigint) not in (:employerIds))
              )
              and (cast(:status as text) is null or l.status = cast(:status as text))
              and (cast(:searchPattern as text) is null
                   or lower(l.file_name) like cast(:searchPattern as text)
                   or lower(l.import_batch_id) like cast(:searchPattern as text)
                   or lower(l.imported_by_username) like cast(:searchPattern as text))
              and (cast(:fromDate as timestamp) is null or l.created_at >= cast(:fromDate as timestamp))
              and (cast(:toDate as timestamp) is null or l.created_at < cast(:toDate as timestamp))
            """, nativeQuery = true)
    Page<MemberImportLog> findVisibleToEmployers(@Param("employerIds") Collection<Long> employerIds,
            @Param("status") String status,
            @Param("searchPattern") String searchPattern,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);
    
    /**
     * Find by status
     */
    List<MemberImportLog> findByStatus(ImportStatus status);
    
    /**
     * Find recent imports by user
     */
    @Query("SELECT l FROM MemberImportLog l WHERE l.importedByUserId = :userId " +
           "ORDER BY l.createdAt DESC")
    List<MemberImportLog> findRecentByUser(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Find imports in date range
     */
    @Query("SELECT l FROM MemberImportLog l WHERE l.createdAt BETWEEN :fromDate AND :toDate " +
           "ORDER BY l.createdAt DESC")
    List<MemberImportLog> findByDateRange(
            @Param("fromDate") LocalDateTime fromDate, 
            @Param("toDate") LocalDateTime toDate);
    
    /**
     * Count imports by status
     */
    long countByStatus(ImportStatus status);
    
    /**
     * Sum total created records
     */
    @Query("SELECT COALESCE(SUM(l.createdCount), 0) FROM MemberImportLog l WHERE l.status = 'COMPLETED'")
    long sumTotalCreated();
    
    /**
     * Sum total updated records
     */
    @Query("SELECT COALESCE(SUM(l.updatedCount), 0) FROM MemberImportLog l WHERE l.status = 'COMPLETED'")
    long sumTotalUpdated();
    
    /**
     * Find pending imports (older than X minutes - for cleanup)
     */
    @Query("SELECT l FROM MemberImportLog l WHERE l.status = 'PENDING' " +
           "AND l.createdAt < :cutoff")
    List<MemberImportLog> findStaleImports(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Get import statistics for dashboard
     */
    @Query("SELECT new map(" +
           "COALESCE(SUM(l.totalRows), 0) as totalRows, " +
           "COALESCE(SUM(l.createdCount), 0) as created, " +
           "COALESCE(SUM(l.updatedCount), 0) as updated, " +
           "COALESCE(SUM(l.errorCount), 0) as errors, " +
           "COUNT(l) as importCount) " +
           "FROM MemberImportLog l WHERE l.createdAt >= :since")
    java.util.Map<String, Object> getStatsSince(@Param("since") LocalDateTime since);
}
