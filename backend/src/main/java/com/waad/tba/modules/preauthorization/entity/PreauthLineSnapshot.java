package com.waad.tba.modules.preauthorization.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/** The money and the identity of one approved line, copied rather than referenced. */
@Entity
@Table(name = "preauth_line_snapshots")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreauthLineSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "decision_snapshot_id", nullable = false) private Long decisionSnapshotId;
    @Column(name = "preauth_line_id", nullable = false) private Long preauthLineId;

    @Column(name = "provider_service_id") private Long providerServiceId;
    @Column(name = "medical_service_id") private Long medicalServiceId;
    @Column(name = "medical_category_id") private Long medicalCategoryId;
    @Column(name = "benefit_rule_id") private Long benefitRuleId;
    @Column(name = "service_code", length = 50) private String serviceCode;
    @Column(name = "service_name", length = 500) private String serviceName;

    @Column(name = "quantity", nullable = false) private Integer quantity;
    @Column(name = "requested_quantity") private Integer requestedQuantity;
    @Column(name = "approved_quantity") private Integer approvedQuantity;
    @Column(name = "review_decision", length = 20) private String reviewDecision;
    @Column(name = "rejection_reason", length = 1000) private String rejectionReason;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;
    @Column(name = "coverage_percent") private Integer coveragePercent;
    @Column(name = "copay_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal copayAmount = BigDecimal.ZERO;
    @Column(name = "rejected_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal rejectedAmount = BigDecimal.ZERO;
    /** The AUTHORISED service value: requested less explicitly refused. */
    @Column(name = "approved_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal approvedAmount;
    /** What the two parties pay between them. Differs from the above once anything is refused. */
    @Column(name = "settlement_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementAmount;
    @Column(name = "patient_share", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal patientShare = BigDecimal.ZERO;
    @Column(name = "company_share", nullable = false, precision = 15, scale = 2)
    @Builder.Default private BigDecimal companyShare = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
