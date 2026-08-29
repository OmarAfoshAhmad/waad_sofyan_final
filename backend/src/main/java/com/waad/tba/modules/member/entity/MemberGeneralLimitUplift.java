package com.waad.tba.modules.member.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An exceptional increase to one member's general ceiling, valid over a dated
 * window and carrying the reason it was granted.
 *
 * The amount is never added to the benefit policy's annual limit. The policy
 * describes what a group is entitled to; this describes an exception made for
 * one person, and the two have to stay separable or nobody can answer "why is
 * this member's ceiling different from their colleague's".
 */
@Entity
@Table(name = "member_general_limit_uplifts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberGeneralLimitUplift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Exclusive. Null means the uplift has no end date yet. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private Source source;

    @Column(name = "requested_by_employer_id")
    private Long requestedByEmployerId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;

    @Column(name = "granted_by_username", length = 100)
    private String grantedByUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "revoked_by_username", length = 100)
    private String revokedByUsername;

    @Column(name = "revoked_reason", columnDefinition = "TEXT")
    private String revokedReason;

    public enum Source {
        /** The member's employer asked for it, and is named. */
        EMPLOYER_REQUEST,
        /** The insurer's own decision about this case. */
        SPECIAL_CONSIDERATION
    }

    /**
     * Ends this uplift from {@code on}, without deleting anything.
     *
     * One rule covers all three cases, because the window is half-open and
     * that is enough to express them:
     *
     *   granted last week, ended today   -> [lastWeek, today)  applied, now stops
     *   granted today, ended today       -> [today, today)     empty: never applied
     *   scheduled for next week, ended   -> [next, next)       empty: never applied
     *
     * The middle case is the one that matters. An uplift entered by mistake
     * and withdrawn the same day must raise nobody's ceiling, not even for the
     * hours in between, and an empty window says exactly that -- while keeping
     * the row, its reason, and the names of both the person who entered it and
     * the person who took it back.
     */
    public void revoke(LocalDate on, String revocationReason, Long userId, String username) {
        this.effectiveTo = on.isBefore(effectiveFrom) ? effectiveFrom : on;
        this.revokedAt = LocalDateTime.now();
        this.revokedByUserId = userId;
        this.revokedByUsername = username;
        this.revokedReason = revocationReason;
    }

    /** Whether this uplift counts towards the ceiling on the given date. */
    public boolean isInForceOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || date.isBefore(effectiveTo));
    }
}
