package com.waad.tba.modules.preauthorization.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * What an approval decided, and on what basis -- written once and never
 * edited, so a conversion months later settles on this basis rather than on
 * whatever the configuration says by then.
 */
@Entity
@Table(name = "preauth_decision_snapshots")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreauthDecisionSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "preauth_id", nullable = false) private Long preauthId;
    @Column(name = "calculation_version", nullable = false) private Integer calculationVersion;
    @Column(name = "member_id", nullable = false) private Long memberId;

    @Column(name = "member_policy_assignment_id") private Long memberPolicyAssignmentId;
    @Column(name = "policy_id", nullable = false) private Long policyId;
    @Column(name = "structure_revision") private Integer structureRevision;

    @Column(name = "expected_service_date", nullable = false) private LocalDate expectedServiceDate;

    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(name = "provider_contract_id") private Long providerContractId;
    @Column(name = "contract_terms_id") private Long contractTermsId;
    @Column(name = "discount_percent", precision = 5, scale = 2) private BigDecimal discountPercent;
    @Column(name = "discount_before_rejection") private Boolean discountBeforeRejection;

    @Column(name = "requested_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedTotal;
    @Column(name = "settlement_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementTotal;
    @Column(name = "authorized_service_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal authorizedServiceTotal;
    @Column(name = "provider_discount_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal providerDiscountTotal = BigDecimal.ZERO;
    @Column(name = "limit_excess_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal limitExcessTotal = BigDecimal.ZERO;
    @Column(name = "limit_capped", nullable = false)
    @Builder.Default private boolean limitCapped = false;
    @Column(name = "rejected_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal rejectedTotal = BigDecimal.ZERO;
    @Column(name = "patient_share_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal patientShareTotal = BigDecimal.ZERO;
    @Column(name = "company_share_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal companyShareTotal = BigDecimal.ZERO;

    @Column(name = "decision_status", nullable = false, length = 25) private String decisionStatus;
    @Column(name = "coverage_outcome", nullable = false, length = 20) private String coverageOutcome;

    @Column(name = "decided_by", nullable = false, length = 150) private String decidedBy;
    @Column(name = "decided_at", nullable = false) private LocalDateTime decidedAt;
    @Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (decidedAt == null) decidedAt = LocalDateTime.now();
    }
}
