package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BenefitRuleBucketRepository extends JpaRepository<BenefitRuleBucket, Long> {
    List<BenefitRuleBucket> findByRuleIdOrderByConsumptionOrder(Long ruleId);
    Optional<BenefitRuleBucket> findByRuleIdAndBucketId(Long ruleId, Long bucketId);
    List<BenefitRuleBucket> findByRuleBenefitPolicyIdOrderByConsumptionOrder(Long policyId);
    boolean existsByBucketId(Long bucketId);
    List<BenefitRuleBucket> findByBucketId(Long bucketId);
    long countByRuleBenefitPolicyId(Long policyId);
    void deleteByRuleIdAndBucketId(Long ruleId, Long bucketId);
}
