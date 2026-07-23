package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.rbac.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimApprovalRecoveryWorker {
    private final ClaimRepository claimRepository;
    private final ClaimStateMachine claimStateMachine;
    private final ClaimAuditService claimAuditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recover(Long claimId) {
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", "id", claimId));
        if (claim.getStatus() != ClaimStatus.APPROVAL_IN_PROGRESS) return false;

        User systemReviewer = User.builder()
                .username("system-approval-recovery")
                .userType("MEDICAL_REVIEWER")
                .build();
        claim.setReviewerComment("فشل تقني أو انقطاع أثناء الاعتماد — أعيدت المطالبة للمراجعة تلقائياً");
        claimStateMachine.transition(claim, ClaimStatus.UNDER_REVIEW, systemReviewer);
        Claim saved = claimRepository.save(claim);
        claimAuditService.recordStatusChange(saved, ClaimStatus.APPROVAL_IN_PROGRESS,
                systemReviewer, claim.getReviewerComment());
        log.warn("Recovered stuck claim {} from APPROVAL_IN_PROGRESS", claimId);
        return true;
    }
}
