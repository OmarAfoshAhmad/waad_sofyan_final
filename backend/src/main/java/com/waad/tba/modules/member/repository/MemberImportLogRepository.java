package com.waad.tba.modules.member.repository;

import java.time.LocalDateTime;
import java.util.List;
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
    
    /**
     * Find by batch ID
     */
    Optional<MemberImportLog> findByImportBatchId(String importBatchId);

    /**
     * Idempotency lookup: has this exact file already been fully imported for
     * this employer? employerId may be null (file-level employer column, no
     * single selected employer) -- Spring Data's derived query treats a null
     * parameter as "IS NULL", which correctly matches the partial unique
     * index's semantics from V167.
     */
    Optional<MemberImportLog> findByEmployerIdAndFileHashAndStatus(
            Long employerId, String fileHash, ImportStatus status);
    
    /**
     * Find by user with pagination
     */
    Page<MemberImportLog> findByImportedByUserId(Long userId, Pageable pageable);
    
    /**
     * Find by company scope with pagination
     */
    Page<MemberImportLog> findByCompanyScopeId(Long companyScopeId, Pageable pageable);
    
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
