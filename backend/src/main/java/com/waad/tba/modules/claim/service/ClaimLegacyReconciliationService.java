package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit, admin-triggered repair for legacy claims saved before this fix existed
 * (when the direct-entry path could persist an APPROVED claim with a zero amount, or
 * skip the benefit-ledger commit entirely). Never runs automatically — must be invoked
 * via the admin endpoint. Each claim is processed independently so one failure does not
 * abort the batch; failures are collected and reported, never silently swallowed.
 *
 * Case A: APPROVED claim with no qualifying amount (never actually consumed anything) —
 * moved to NEEDS_CORRECTION (a valid, non-financial state per ClaimStateMachine) so it
 * stops being a phantom "approved with 0" row and a human can review/resubmit it.
 *
 * Case B: APPROVED claim with a genuine positive company share and a bucket-backed
 * line, but no COMMITTED ledger entry — reconciled via the existing, idempotent
 * BenefitBucketLedgerService.reconcileApprovedClaim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimLegacyReconciliationService {

    private final ClaimRepository claimRepository;
    private final ClaimStateMachine claimStateMachine;
    private final BenefitBucketLedgerService benefitBucketLedgerService;
    private final AuthorizationService authorizationService;

    @Getter
    @Builder
    public static class ReconciliationReport {
        private final int scanned;
        private final int zeroApprovedFixed;
        private final int positiveLedgered;
        private final List<FailedClaim> failed;
    }

    public record FailedClaim(Long claimId, String reason) {
    }

    @Transactional
    public ReconciliationReport reconcileLegacyClaims() {
        User currentUser = authorizationService.getCurrentUser();
        List<Long> zeroApprovedIds = claimRepository.findLegacyZeroApprovedClaimIds();
        List<Long> unledgeredPositiveIds = claimRepository.findLegacyUnledgeredPositiveClaimIds();

        int zeroFixed = 0;
        int positiveLedgered = 0;
        List<FailedClaim> failed = new ArrayList<>();

        for (Long claimId : zeroApprovedIds) {
            try {
                Claim claim = claimRepository.findById(claimId).orElseThrow();
                claim.setReviewerComment(
                        "إصلاح بيانات قديمة: مطالبة APPROVED بلا مبلغ مؤهل — أُعيدت لحالة تحتاج تصحيح/مراجعة");
                claimStateMachine.transition(claim, ClaimStatus.NEEDS_CORRECTION, currentUser);
                claimRepository.save(claim);
                zeroFixed++;
                log.info("🔧 [LEGACY-REPAIR] Claim {} (zero-approved) moved APPROVED -> NEEDS_CORRECTION", claimId);
            } catch (Exception e) {
                log.error("❌ [LEGACY-REPAIR] Failed to fix zero-approved claim {}: {}", claimId, e.getMessage());
                failed.add(new FailedClaim(claimId, "zero-approved: " + e.getMessage()));
            }
        }

        for (Long claimId : unledgeredPositiveIds) {
            try {
                int createdEntries = benefitBucketLedgerService.reconcileApprovedClaim(claimId);
                positiveLedgered++;
                log.info("🔧 [LEGACY-REPAIR] Claim {} ledger reconciled ({} entries created)", claimId, createdEntries);
            } catch (Exception e) {
                log.error("❌ [LEGACY-REPAIR] Failed to reconcile ledger for claim {}: {}", claimId, e.getMessage());
                failed.add(new FailedClaim(claimId, "unledgered-positive: " + e.getMessage()));
            }
        }

        int scanned = zeroApprovedIds.size() + unledgeredPositiveIds.size();
        log.info("✅ [LEGACY-REPAIR] Done: scanned={}, zeroApprovedFixed={}, positiveLedgered={}, failed={}",
                scanned, zeroFixed, positiveLedgered, failed.size());

        return ReconciliationReport.builder()
                .scanned(scanned)
                .zeroApprovedFixed(zeroFixed)
                .positiveLedgered(positiveLedgered)
                .failed(failed)
                .build();
    }
}
