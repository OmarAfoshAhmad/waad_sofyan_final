package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationSession;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceListClassificationSessionRepository extends JpaRepository<PriceListClassificationSession, Long> {
    Page<PriceListClassificationSession> findByStatus(PriceListSessionStatus status, Pageable pageable);

    @Query("""
            SELECT session
            FROM PriceListClassificationSession session
            WHERE (:status IS NULL OR session.status = :status)
              AND (:query IS NULL
                   OR LOWER(COALESCE(session.sessionName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(session.originalFileName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(session.providerName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(session.contractCode, '')) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<PriceListClassificationSession> searchSummaries(
            @Param("status") PriceListSessionStatus status,
            @Param("query") String query,
            Pageable pageable);

    java.util.Optional<PriceListClassificationSession> findFirstBySourceFingerprintOrderByUpdatedAtDesc(String sourceFingerprint);
}
