package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefit_bucket_consumptions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BenefitBucketConsumption {
    public enum Status { RESERVED, COMMITTED, REVERSED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id") private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_line_id") private ClaimLine claimLine;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "policy_id") private BenefitPolicy policy;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bucket_id") private BenefitLimitBucket bucket;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end") private LocalDate periodEnd;
    @Column(name = "approved_amount", nullable = false, precision = 15, scale = 2) private BigDecimal approvedAmount;
    @Column(name = "times_consumed", nullable = false) private Integer timesConsumed;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "calculation_version", nullable = false) private Integer calculationVersion;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180) private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reversal_of_id") private BenefitBucketConsumption reversalOf;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "committed_at") private LocalDateTime committedAt;
    @Column(name = "reversed_at") private LocalDateTime reversedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
