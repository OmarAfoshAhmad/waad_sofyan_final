package com.waad.tba.modules.member.entity;

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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Which benefit policy applied to a member over which period.
 *
 * Periods are half-open [assignmentStartDate, assignmentEndDate) -- the same
 * convention provider contract price versions use (V156) -- so consecutive
 * assignments meet exactly, with no gap and no overlapping day. Postgres
 * enforces non-overlap directly (V171's gist exclusion constraint); rows are
 * append-only and may only ever be closed, never re-pointed or re-dated.
 *
 * A row whose source is BACKFILL was INFERRED by V171 from the member's
 * single mutable benefit_policy_id pointer, because no assignment history
 * existed before that migration. It answers "as far as we know", not "this
 * was observed".
 */
@Entity
@Table(name = "member_policy_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPolicyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /** Inclusive start of the period this policy applied to the member. */
    @Column(name = "assignment_start_date", nullable = false)
    private LocalDate assignmentStartDate;

    /** EXCLUSIVE end; null means still in force. */
    @Column(name = "assignment_end_date")
    private LocalDate assignmentEndDate;

    @Column(name = "assignment_reason", length = 500)
    private String assignmentReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", length = 30)
    private PolicyAssignmentSource assignmentSource;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "member_full_name", length = 200)
    private String memberFullName;

    @Column(name = "member_card_number", length = 50)
    private String memberCardNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    public boolean coversDate(LocalDate date) {
        if (date.isBefore(assignmentStartDate)) {
            return false;
        }
        return assignmentEndDate == null || date.isBefore(assignmentEndDate);
    }
}
