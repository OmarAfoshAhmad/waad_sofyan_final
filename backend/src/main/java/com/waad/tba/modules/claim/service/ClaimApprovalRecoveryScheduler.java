package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimApprovalRecoveryScheduler {
    private final ClaimRepository claimRepository;
    private final ClaimApprovalRecoveryWorker worker;

    @Scheduled(fixedDelayString = "${claims.approval-recovery.interval-ms:300000}")
    public void recoverStuckApprovals() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        for (Long claimId : claimRepository.findIdsByStatusAndUpdatedAtBefore(
                ClaimStatus.APPROVAL_IN_PROGRESS, threshold)) {
            try {
                worker.recover(claimId);
            } catch (Exception error) {
                log.error("Failed to recover stuck approval for claim {}", claimId, error);
            }
        }
    }
}
