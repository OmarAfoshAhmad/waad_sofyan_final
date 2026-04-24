package com.waad.tba.modules.systemadmin.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.systemadmin.entity.AuditLog;

/**
 * Repository for Audit Log Entity
 * Phase 2 - System Administration
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs by user ID (paginated)
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    /**
     * Find audit logs by entity type and ID
     */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType,
            Long entityId,
            Pageable pageable);

    /**
     * Find audit logs by action type
     */
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    /**
     * Find audit logs within time range
     */
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    /**
     * Get distinct action types
     */
    @Query("SELECT DISTINCT a.action FROM SystemAuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    /**
     * Get distinct entity types
     */
    @Query("SELECT DISTINCT a.entityType FROM SystemAuditLog a WHERE a.entityType IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();

    /**
     * Count logs by user
     */
    Long countByUserId(Long userId);

    /**
     * Count logs by action
     */
    Long countByAction(String action);
}
