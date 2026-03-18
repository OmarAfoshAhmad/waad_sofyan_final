package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.employer.entity.Employer;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Medical benefits policy — one ACTIVE per employer at any given date. */
@Entity
@Table(name = "benefit_policies", indexes = {
        @Index(name = "idx_benefit_policy_employer", columnList = "employer_id"),
        @Index(name = "idx_benefit_policy_status", columnList = "status"),
        @Index(name = "idx_benefit_policy_dates", columnList = "start_date, end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BenefitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Policy name is required")
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Size(max = 50)
    @Column(length = 50)
    private String policyCode;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotNull(message = "Employer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull(message = "Annual limit is required")
    @DecimalMin(value = "0.00")
    @Column(name = "annual_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal annualLimit;

    @NotNull(message = "Default coverage percent is required")
    @Min(0)
    @Max(100)
    @Column(name = "default_coverage_percent", nullable = false)
    @Builder.Default
    private Integer defaultCoveragePercent = 80;

    @DecimalMin(value = "0.00")
    @Column(name = "per_member_limit", precision = 15, scale = 2)
    private BigDecimal perMemberLimit;

    @DecimalMin(value = "0.00")
    @Column(name = "per_family_limit", precision = 15, scale = 2)
    private BigDecimal perFamilyLimit;

    /** Amount member pays out-of-pocket before coverage begins */
    @DecimalMin(value = "0.00")
    @Column(name = "annual_deductible", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal annualDeductible = BigDecimal.ZERO;

    /** Max patient pays (deductible + co-pay) before 100% coverage */
    @DecimalMin(value = "0.00")
    @Column(name = "out_of_pocket_max", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal outOfPocketMax = BigDecimal.ZERO;

    /** Policy-level default; BenefitPolicyRule can override per category */
    @Min(0)
    @Column(name = "default_waiting_period_days")
    @Builder.Default
    private Integer defaultWaitingPeriodDays = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BenefitPolicyStatus status = BenefitPolicyStatus.DRAFT;

    @Column(name = "covered_members_count")
    @Builder.Default
    private Integer coveredMembersCount = 0;

    @Size(max = 1000)
    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "benefitPolicy", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<BenefitPolicyRule> rules = new ArrayList<>();

    public boolean isEffective() {
        if (status != BenefitPolicyStatus.ACTIVE) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (status != BenefitPolicyStatus.ACTIVE) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !endDate.isBefore(otherStart) && !startDate.isAfter(otherEnd);
    }

    public void activate() {
        this.status = BenefitPolicyStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = BenefitPolicyStatus.EXPIRED;
    }

    public void suspend() {
        this.status = BenefitPolicyStatus.SUSPENDED;
    }

    public void addRule(BenefitPolicyRule rule) {
        rules.add(rule);
        rule.setBenefitPolicy(this);
    }

    public void removeRule(BenefitPolicyRule rule) {
        rules.remove(rule);
        rule.setBenefitPolicy(null);
    }

    public List<BenefitPolicyRule> getActiveRules() {
        return rules.stream()
                .filter(BenefitPolicyRule::isActive)
                .toList();
    }

    public int getActiveRulesCount() {
        return (int) rules.stream()
                .filter(BenefitPolicyRule::isActive)
                .count();
    }

    public enum BenefitPolicyStatus {
        DRAFT, ACTIVE, EXPIRED, SUSPENDED, CANCELLED
    }
}
