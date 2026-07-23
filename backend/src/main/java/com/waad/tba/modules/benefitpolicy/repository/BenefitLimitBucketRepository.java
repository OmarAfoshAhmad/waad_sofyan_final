package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface BenefitLimitBucketRepository extends JpaRepository<BenefitLimitBucket, Long> {
    List<BenefitLimitBucket> findByPolicyIdOrderByCode(Long policyId);
    Optional<BenefitLimitBucket> findByPolicyIdAndCode(Long policyId, String code);
    Optional<BenefitLimitBucket> findByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    Optional<BenefitLimitBucket> findByPolicyIdAndNameArIgnoreCase(Long policyId, String nameAr);
    boolean existsByPolicyIdAndCodeIgnoreCase(Long policyId, String code);
    boolean existsByBenefitGroupId(Long benefitGroupId);
    List<BenefitLimitBucket> findByBenefitGroupId(Long benefitGroupId);
    List<BenefitLimitBucket> findByParentBucketId(Long parentBucketId);
    long countByPolicyId(Long policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BenefitLimitBucket b where b.id = :id")
    Optional<BenefitLimitBucket> findByIdForUpdate(@Param("id") Long id);
}
