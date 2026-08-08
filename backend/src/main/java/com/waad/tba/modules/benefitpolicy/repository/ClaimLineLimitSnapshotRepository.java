package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimLineLimitSnapshotRepository extends JpaRepository<ClaimLineLimitSnapshot, Long> {

    List<ClaimLineLimitSnapshot> findByClaimIdOrderByClaimLineIdAscConsumptionOrderAsc(Long claimId);

    List<ClaimLineLimitSnapshot> findByClaimLineIdAndCalculationVersion(Long claimLineId, Integer calculationVersion);
}
