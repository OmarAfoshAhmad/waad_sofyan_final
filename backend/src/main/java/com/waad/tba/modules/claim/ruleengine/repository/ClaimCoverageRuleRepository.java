package com.waad.tba.modules.claim.ruleengine.repository;

import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimCoverageRuleRepository extends JpaRepository<ClaimCoverageRule, Long> {
    List<ClaimCoverageRule> findByEnabledTrue();
}
