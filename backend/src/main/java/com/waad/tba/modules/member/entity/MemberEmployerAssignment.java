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

/** Immutable historical fact: which employer owned a member on a date. */
@Entity
@Table(name = "member_employer_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberEmployerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    @Column(name = "assignment_start_date", nullable = false)
    private LocalDate assignmentStartDate;

    /** Exclusive end; null means currently open. */
    @Column(name = "assignment_end_date")
    private LocalDate assignmentEndDate;

    @Column(name = "assignment_reason", nullable = false, length = 500)
    private String assignmentReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, length = 30)
    private EmployerAssignmentSource assignmentSource;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "member_full_name", length = 200)
    private String memberFullName;

    @Column(name = "member_card_number", length = 50)
    private String memberCardNumber;

    @Column(name = "employer_name", length = 255)
    private String employerName;

    @Column(name = "employer_code", length = 100)
    private String employerCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
