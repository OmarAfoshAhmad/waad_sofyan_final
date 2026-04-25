package com.waad.tba.modules.audit.repository;

import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MedicalAuditLogRepository extends JpaRepository<AuditLog, Long> {

        /**
         * Bulk delete using native SQL — bypasses Hibernate entity lifecycle
         * and @PreRemove hooks entirely. Goes directly to the DB layer.
         * The DB-level DELETE trigger is removed via V54 migration.
         */
        @Modifying
        @Query(value = "DELETE FROM medical_audit_logs WHERE id IN :ids", nativeQuery = true)
        int bulkDeleteByIds(@Param("ids") List<Long> ids);

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
