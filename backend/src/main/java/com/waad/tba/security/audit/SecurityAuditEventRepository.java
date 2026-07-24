package com.waad.tba.security.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {

    List<SecurityAuditEvent> findByActorId(Long actorId);

    List<SecurityAuditEvent> findByActorIdOrderByEventTimestampDesc(Long actorId);

    List<SecurityAuditEvent> findByCorrelationId(String correlationId);

    List<SecurityAuditEvent> findByActorUsername(String actorUsername);

    Page<SecurityAuditEvent> findByActionType(
            SecurityAuditEvent.AuditActionType actionType,
            Pageable pageable);

    Page<SecurityAuditEvent> findByActorId(Long actorId, Pageable pageable);

    Page<SecurityAuditEvent> findByEventTimestampBetween(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    Page<SecurityAuditEvent> findByResult(
            SecurityAuditEvent.AuditResult result,
            Pageable pageable);
}
