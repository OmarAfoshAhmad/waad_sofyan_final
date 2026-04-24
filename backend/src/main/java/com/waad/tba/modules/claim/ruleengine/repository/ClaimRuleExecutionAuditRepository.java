package com.waad.tba.modules.claim.ruleengine.repository;

import com.waad.tba.modules.claim.ruleengine.entity.ClaimRuleExecutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRuleExecutionAuditRepository extends JpaRepository<ClaimRuleExecutionAudit, Long> {
}
