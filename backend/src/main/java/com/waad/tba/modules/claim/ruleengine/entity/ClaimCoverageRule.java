package com.waad.tba.modules.claim.ruleengine.entity;

import com.waad.tba.modules.claim.ruleengine.model.RuleGroup;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "claim_coverage_rules", indexes = {
        @Index(name = "idx_claim_coverage_rules_active_group_priority", columnList = "enabled, rule_group, priority")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCoverageRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 60)
    private RuleType type;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_group", nullable = false, length = 60)
    private RuleGroup ruleGroup;

    @Column(name = "dependency_rules", columnDefinition = "jsonb", nullable = false)
    private String dependencyRules;

    @Column(name = "configuration", columnDefinition = "jsonb", nullable = false)
    private String configuration;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.dependencyRules == null) {
            this.dependencyRules = "[]";
        }
        if (this.configuration == null) {
            this.configuration = "{}";
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
