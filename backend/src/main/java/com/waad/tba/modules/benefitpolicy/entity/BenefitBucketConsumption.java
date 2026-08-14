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

    /**
     * Why a compensating movement was posted. Mandatory on every REVERSED
     * row (V173) so the ledger says what happened rather than only that
     * something did.
     */
    public enum ReversalReason {
        PREAUTH_RELEASE, PREAUTH_EXPIRY, PREAUTH_CANCELLATION,
        CLAIM_REVERSAL, CLAIM_CORRECTION
    }

    /**
     * WHERE a movement came from, kept separate from {@code status}, which is
     * WHAT KIND of movement it is (the entry type). The two used to be
     * conflated -- every row was implicitly a claim consumption, so an
     * imported opening balance had to masquerade as a claim and a
     * pre-authorization hold could not be expressed at all.
     */
    public enum SourceType { CLAIM, PREAUTH, OPENING_IMPORT, ADJUSTMENT }
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
    @Enumerated(EnumType.STRING) @Column(name = "reversal_reason", length = 30) private ReversalReason reversalReason;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default private SourceType sourceType = SourceType.CLAIM;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "committed_at") private LocalDateTime committedAt;
    @Column(name = "reversed_at") private LocalDateTime reversedAt;
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}
