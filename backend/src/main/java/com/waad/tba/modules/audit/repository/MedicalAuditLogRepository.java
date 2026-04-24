package com.waad.tba.modules.audit.repository;

import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface MedicalAuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            EntityType entityType,
            String entityId,
            Pageable pageable);

    Page<AuditLog> findByCorrelationIdOrderByTimestampDesc(String correlationId, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdAndCorrelationIdOrderByTimestampDesc(
            EntityType entityType,
            String entityId,
            String correlationId,
            Pageable pageable);

    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdAndTimestampBetweenOrderByTimestampDesc(
            EntityType entityType,
            String entityId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable);

    Page<AuditLog> findByCorrelationIdAndTimestampBetweenOrderByTimestampDesc(
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdAndCorrelationIdAndTimestampBetweenOrderByTimestampDesc(
            EntityType entityType,
            String entityId,
            String correlationId,
            Instant fromInclusive,
            Instant toExclusive,
            Pageable pageable);
}
