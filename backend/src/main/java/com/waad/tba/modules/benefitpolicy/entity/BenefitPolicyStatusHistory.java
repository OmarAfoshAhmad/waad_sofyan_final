package com.waad.tba.modules.benefitpolicy.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

/**
 * One interval of {@code [validFrom, validTo)} during which a policy held a
 * given status. The dated source of truth for "was this policy ACTIVE on
 * service date X", kept separate from {@code BenefitPolicy.status}, which
 * only answers "is it ACTIVE right now". See V210.
 */
@Entity
@Table(name = "benefit_policy_status_history", indexes = {
        @Index(name = "idx_policy_status_history_policy", columnList = "policy_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BenefitPolicyStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitPolicyStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Null means this is the currently open (still-in-effect) interval. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Half-open: valid_from is included, valid_to is excluded. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || date.isBefore(validTo));
    }
}
