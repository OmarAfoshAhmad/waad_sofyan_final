package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A member a rollback deliberately did not touch, and why. */
@Entity
@Table(name = "member_import_rollback_skips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MemberImportRollbackSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rollback_id", nullable = false)
    private Long rollbackId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** e.g. "HAS_FINANCIAL_ACTIVITY". */
    @Column(nullable = false, length = 50)
    private String reason;

    @CreatedDate
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
