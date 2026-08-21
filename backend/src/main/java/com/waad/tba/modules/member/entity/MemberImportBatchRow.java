package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
 * The link {@code member_import_logs} never had: which specific member a
 * batch created or updated, and (for an update) what its mutable fields
 * held before the import wrote them. This is what makes a rollback possible
 * -- without it, an import batch's effect on any one member is unrecoverable.
 */
@Entity
@Table(name = "member_import_batch_rows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberImportBatchRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_log_id", nullable = false)
    private Long importLogId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Action action;

    /**
     * NULL for a CREATED row (there is no "before"). For an UPDATED row,
     * the JSON of {@link com.waad.tba.modules.member.dto.MemberImportFieldSnapshot}
     * captured immediately before the import applied its own values.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_snapshot", columnDefinition = "jsonb")
    private String previousSnapshot;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    public enum Action {
        CREATED,
        UPDATED
    }
}
