package com.waad.tba.modules.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Independent audit record for a physical member deletion. Deliberately NOT
 * foreign-keyed to members(id): the whole reason this table exists is to
 * survive the member row it describes ceasing to exist, so it snapshots the
 * identifying details instead of referencing them. Immutable at the DB
 * level (V169's triggers reject UPDATE/DELETE).
 */
@Entity
@Table(name = "member_hard_delete_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberHardDeleteAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "member_full_name", length = 200)
    private String memberFullName;

    @Column(name = "member_card_number", length = 50)
    private String memberCardNumber;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "was_principal", nullable = false)
    private Boolean wasPrincipal;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "performed_by_username", length = 100)
    private String performedByUsername;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;
}
