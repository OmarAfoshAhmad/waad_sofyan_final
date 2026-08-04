package com.waad.tba.modules.maintenancehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per maintenance action (issue created/reopened, assigned, resolved, ignored;
 * a backup or reconciliation run). Optionally linked to the {@link SystemIssue} it
 * concerns, giving each issue a full, queryable timeline — the unified maintenance
 * log the plan calls for, distinct from the business-entity-oriented audit trail.
 */
@Entity
@Table(name = "maintenance_operations_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "operation_type", nullable = false, length = 60)
    private String operationType;

    @Column(name = "performed_by", nullable = false, length = 150)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "details_ar", length = 2000)
    private String detailsAr;
}
