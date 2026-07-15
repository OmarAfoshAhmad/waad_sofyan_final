package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BenefitGroupRepository extends JpaRepository<BenefitGroup, Long> {
    List<BenefitGroup> findByPolicyIdOrderByCode(Long policyId);
    Optional<BenefitGroup> findByPolicyIdAndCode(Long policyId, String code);
    Optional<BenefitGroup> findByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    Optional<BenefitGroup> findByPolicyIdAndNameArIgnoreCase(Long policyId, String nameAr);
    boolean existsByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    long countByPolicyId(Long policyId);
}
