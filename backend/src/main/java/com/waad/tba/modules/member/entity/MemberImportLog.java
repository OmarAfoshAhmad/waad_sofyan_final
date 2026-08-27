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
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Audit log for bulk member imports.
 * 
 * Tracks:
 * - Who imported (user)
 * - When (timestamps)
 * - What file
 * - How many records (created/updated/errors)
 */
@Entity
@Table(name = "member_import_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Import batch ID is required")
    @Column(name = "import_batch_id", unique = true, nullable = false, length = 64)
    private String importBatchId;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * SHA-256 of the uploaded file's bytes alone (metadata / diagnostics).
     * Uniqueness is NOT enforced on this column directly -- see
     * importScopeHash, which is what the file_hash feeds into.
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "employer_id")
    private Long employerId;

    /**
     * Fingerprint of every input that changes an import's outcome: fileHash
     * + employerId + benefitPolicyId + resolvedHeaderRowNumber +
     * clearOldMembers. A partial unique index on this column (status =
     * 'COMPLETED') is what actually enforces idempotency -- see V168 for why
     * V167's original fileHash + employerId key was insufficient (NULL
     * employerId, different benefit policy/header row/clearOldMembers all
     * change the outcome and must NOT be folded into "the same import").
     */
    @Column(name = "import_scope_hash", length = 64)
    private String importScopeHash;

    // Statistics
    @Builder.Default
    @Column(name = "total_rows")
    private Integer totalRows = 0;

    @Builder.Default
    @Column(name = "created_count")
    private Integer createdCount = 0;

    @Builder.Default
    @Column(name = "updated_count")
    private Integer updatedCount = 0;

    @Builder.Default
    @Column(name = "skipped_count")
    private Integer skippedCount = 0;

    @Builder.Default
    @Column(name = "error_count")
    private Integer errorCount = 0;

    // Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Processing timestamps
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    // Security context
    @Column(name = "imported_by_user_id")
    private Long importedByUserId;

    @Column(name = "imported_by_username", length = 100)
    private String importedByUsername;

    @Column(name = "company_scope_id")
    private Long companyScopeId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Import status enum
     */
    public enum ImportStatus {
        PENDING,        // File uploaded, waiting to process
        VALIDATING,     // Validating rows
        PROCESSING,     // Creating/updating members
        COMPLETED,      // All done successfully
        PARTIAL,        // Completed with some errors
        FAILED          // Import failed completely
    }

    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        if (totalRows == null || totalRows == 0) return 0;
        int successful = (createdCount != null ? createdCount : 0) + 
                        (updatedCount != null ? updatedCount : 0);
        return (double) successful / totalRows * 100;
    }

    /**
     * Mark as started
     */
    public void markStarted() {
        this.status = ImportStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Mark as completed
     */
    public void markCompleted() {
        this.completedAt = LocalDateTime.now();
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
        
        if (errorCount != null && errorCount > 0) {
            this.status = createdCount > 0 || updatedCount > 0 ? ImportStatus.PARTIAL : ImportStatus.FAILED;
        } else {
            this.status = ImportStatus.COMPLETED;
        }
    }

    /**
     * Mark as failed
     */
    public void markFailed(String message) {
        this.status = ImportStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = LocalDateTime.now();
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Increment created count
     */
    public void incrementCreated() {
        this.createdCount = (this.createdCount != null ? this.createdCount : 0) + 1;
    }

    /**
     * Increment updated count
     */
    public void incrementUpdated() {
        this.updatedCount = (this.updatedCount != null ? this.updatedCount : 0) + 1;
    }

    /**
     * Increment error count
     */
    public void incrementError() {
        this.errorCount = (this.errorCount != null ? this.errorCount : 0) + 1;
    }

    /**
     * Increment skipped count
     */
    public void incrementSkipped() {
        this.skippedCount = (this.skippedCount != null ? this.skippedCount : 0) + 1;
    }
}
