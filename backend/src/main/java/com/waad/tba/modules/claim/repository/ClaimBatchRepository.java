package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.ClaimBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimBatchRepository extends JpaRepository<ClaimBatch, Long> {

    Optional<ClaimBatch> findByProviderIdAndEmployerIdAndBatchYearAndBatchMonth(
        Long providerId, Long employerId, Integer batchYear, Integer batchMonth
    );

    List<ClaimBatch> findByBatchYearAndBatchMonth(Integer batchYear, Integer batchMonth);

    List<ClaimBatch> findByStatus(ClaimBatch.ClaimBatchStatus status);

    long countByEmployerIdAndBatchYearAndBatchMonth(Long employerId, Integer batchYear, Integer batchMonth);

    List<ClaimBatch> findByEmployerIdAndBatchYearAndBatchMonth(Long employerId, Integer batchYear, Integer batchMonth);

    List<ClaimBatch> findByProviderIdAndBatchYearAndBatchMonth(Long providerId, Integer batchYear, Integer batchMonth);

    @Query("""
        SELECT b FROM ClaimBatch b
        WHERE b.providerId = :providerId
          AND b.employerId = :employerId
          AND b.batchYear = :batchYear
          AND b.batchMonth = :batchMonth
        """)
    List<ClaimBatch> searchByProviderAndEmployerAndPeriod(
        @Param("providerId") Long providerId,
        @Param("employerId") Long employerId,
        @Param("batchYear") Integer batchYear,
        @Param("batchMonth") Integer batchMonth
    );

    boolean existsByProviderIdAndEmployerIdAndBatchYearAndBatchMonth(
        Long providerId, Long employerId, Integer batchYear, Integer batchMonth
    );

    /**
     * Bulk-closes all OPEN batches from months prior to the current month.
     * More efficient than loading all entities into memory.
     */
    @Modifying
    @Query("""
        UPDATE ClaimBatch b
        SET b.status = :newStatus, b.closedAt = :closedAt
        WHERE b.status = :currentStatus
          AND (b.batchYear < :currentYear
               OR (b.batchYear = :currentYear AND b.batchMonth < :currentMonth))
        """)
    int closeExpiredBatches(
        @Param("currentStatus") ClaimBatch.ClaimBatchStatus currentStatus,
        @Param("newStatus") ClaimBatch.ClaimBatchStatus newStatus,
        @Param("currentYear") int currentYear,
        @Param("currentMonth") int currentMonth,
        @Param("closedAt") LocalDateTime closedAt
    );
}
