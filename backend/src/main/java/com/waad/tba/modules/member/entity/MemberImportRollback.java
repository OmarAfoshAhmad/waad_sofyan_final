package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One attempt to undo an import batch. Append-only, like every other
 * batched operation in this system: a reason and an actor are mandatory,
 * and a batch may have at most one COMPLETED row here -- enforced by a
 * partial unique index in the database, not just this service.
 */
@Entity
@Table(name = "member_import_rollbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberImportRollback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_log_id", nullable = false)
    private Long importLogId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 120)
    private String performedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Builder.Default
    @Column(name = "reverted_created_count", nullable = false)
    private Integer revertedCreatedCount = 0;

    @Builder.Default
    @Column(name = "reverted_updated_count", nullable = false)
    private Integer revertedUpdatedCount = 0;

    @Builder.Default
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    public enum Status {
        COMPLETED,
        FAILED
    }
}
