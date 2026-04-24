package com.waad.tba.modules.claim.ruleengine.entity;

import com.waad.tba.modules.claim.ruleengine.model.RuleGroup;
import com.waad.tba.modules.claim.ruleengine.model.RuleStatus;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_rule_execution_audit", indexes = {
        @Index(name = "idx_claim_rule_exec_audit_correlation", columnList = "correlation_id"),
        @Index(name = "idx_claim_rule_exec_audit_claim_time", columnList = "claim_id, executed_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRuleExecutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", nullable = false, length = 80)
    private String correlationId;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 60)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_group", nullable = false, length = 60)
    private RuleGroup ruleGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private RuleStatus decision;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "before_context", columnDefinition = "jsonb", nullable = false)
    private String beforeContext;

    @Column(name = "after_context", columnDefinition = "jsonb", nullable = false)
    private String afterContext;

    @Column(name = "delta_changes", columnDefinition = "jsonb", nullable = false)
    private String deltaChanges;

    @Column(name = "execution_time_ms", precision = 12, scale = 3, nullable = false)
    private BigDecimal executionTimeMs;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = LocalDateTime.now();
        }
    }
}
