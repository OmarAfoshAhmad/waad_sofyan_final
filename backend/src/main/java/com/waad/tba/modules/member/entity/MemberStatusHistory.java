package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Append-only record of a single member status transition. Immutable at the
 * DB level (V169's triggers reject UPDATE/DELETE) -- this is the actual
 * history; Member's own statusReason/statusSource/statusChangedAt/etc.
 * fields only describe the LAST transition, not the full timeline.
 *
 * Every row is written by MemberStatusTransitionService, in the same
 * transaction as the Member row it describes -- if the transition doesn't
 * commit, neither does its history row (unlike an external audit log, this
 * IS part of what "the transition happened" means).
 */
@Entity
@Table(name = "member_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private Member.MemberStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private Member.MemberStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSource source;

    @Column(name = "transition_id", nullable = false, length = 64)
    private String transitionId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by")
    private Long changedBy;
}
