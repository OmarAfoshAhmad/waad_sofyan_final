package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.ClaimPendingService;
import com.waad.tba.modules.claim.entity.PendingServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimPendingServiceRepository extends JpaRepository<ClaimPendingService, Long> {
    Optional<ClaimPendingService> findByIdAndClaimId(Long id, Long claimId);
    List<ClaimPendingService> findByClaimIdOrderByIdAsc(Long claimId);
    boolean existsByClaimIdAndStatusIn(Long claimId, Collection<PendingServiceStatus> statuses);
}
