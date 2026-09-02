package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BenefitGroupRepository extends JpaRepository<BenefitGroup, Long> {
    List<BenefitGroup> findByPolicyIdOrderByCode(Long policyId);
    Optional<BenefitGroup> findByPolicyIdAndCode(Long policyId, String code);
    Optional<BenefitGroup> findByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    Optional<BenefitGroup> findByPolicyIdAndNameArIgnoreCase(Long policyId, String nameAr);
    @Query("select g from BenefitGroup g where g.policy.id = :policyId and lower(trim(g.nameAr)) = lower(trim(:nameAr))")
    Optional<BenefitGroup> findByPolicyIdAndNormalizedNameAr(@Param("policyId") Long policyId, @Param("nameAr") String nameAr);
    boolean existsByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    long countByPolicyId(Long policyId);
}
