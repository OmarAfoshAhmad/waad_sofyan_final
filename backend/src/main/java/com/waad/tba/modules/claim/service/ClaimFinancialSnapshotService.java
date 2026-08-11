package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialInvariantGuard;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialAdjudicationService;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialTotals;
import com.waad.tba.modules.claim.service.finance.ClaimLimitSnapshotFactory;
import com.waad.tba.modules.benefitpolicy.service.ClaimLimitSnapshotService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Canonical finalizer for claim money -- COMMIT-AND-VALIDATE ONLY (finance-00
 * step 4). Every financial number on the claim (approvedAmount,
 * netProviderAmount, patientCoPay, refusedAmount, companyDiscountAmount) was
 * already computed correctly per line by ClaimFinancialAdjudicationService and
 * aggregated by ClaimMapper.calculateClaimTotals before this method ever
 * runs. This method must never recompute any of them.
 *
 * Historically it did recompute them, through an independent deductible-based
 * engine (CostCalculationService/AtomicFinancialService) that does not
 * correspond to any real policy -- silently discarding the contract's
 * discount-before/after-rejection timing and substituting a different
 * patient-responsibility number. finance-00 characterization tests
 * (ClaimCopayDiscountRejectionOrderingIntegrationTest) proved the exact
 * wrong numbers that produced.
 *
 * Two protections from that old path are preserved here, not dropped:
 *   1. The member row lock (now acquired directly, not via
 *      AtomicFinancialService) -- prevents two concurrent claims for the
 *      same member from both reading a stale "used" total and jointly
 *      exceeding the annual policy ceiling.
 *   2. The annual-limit check via BenefitPolicyCoverageService.validateAmountLimits
 *      -- validated against the claim's own limit-consumption total
 *      (ClaimFinancialTotals.sumLimitConsumption), the same WAAD-FIN-1.0 S4
 *      axis the engine itself already capped each line to; never
 *      approvedAmount, which is a different, smaller number by construction.
 */
@Service
@RequiredArgsConstructor
public class ClaimFinancialSnapshotService {

    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final ClaimFinancialInvariantGuard claimFinancialInvariantGuard;
    private final ClaimFinancialAdjudicationService financialAdjudicationService;
    private final ClaimLimitSnapshotFactory limitSnapshotFactory;
    private final ClaimLimitSnapshotService limitSnapshotService;
    private final MemberRepository memberRepository;

    @Transactional
    public BigDecimal finalizeSnapshot(Claim claim) {
        if (claim.getMember() != null && claim.getMember().getId() != null) {
            // Lock the member row so no concurrent claim for the same member can
            // commit between this read and this transaction's own commit --
            // otherwise two claims each individually within the annual ceiling
            // could jointly exceed it.
            Member lockedMember = memberRepository.findByIdWithLock(claim.getMember().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member", "id", claim.getMember().getId()));

            // The draft calculation is only a preview. Approval must resolve the
            // balances again after locking this member, then derive every claim total
            // from those freshly adjudicated canonical line results.
            var adjudication = financialAdjudicationService.adjudicate(claim);
            ClaimFinancialTotals.aggregate(claim);

            // GUARD 2 (finance-00 step 3): the approval gate. This is the LAST
            // point before the caller flips the claim's status to APPROVED, and
            // it re-checks the same identities GUARD 1 checked right after
            // aggregation. If anything between aggregation and here rewrote the
            // numbers, this is what catches it -- fail closed, not a warning.
            claimFinancialInvariantGuard.assertConsistent(claim);

            if (lockedMember.getBenefitPolicy() != null) {
                // excludeClaimId = claim.getId(): this claim may already exist as a row
                // (e.g. the direct-entry path saves it before finalizeSnapshot runs), so
                // the "previously used" aggregation must not count this claim's own
                // amount against itself.
                //
                // WAAD-FIN-1.0 S4: what the ceiling tracks is limit consumption, not
                // approvedAmount -- ClaimFinancialTotals.sumLimitConsumption(claim) is
                // the same axis validateAmountLimits' own "previously consumed" read
                // now uses (BenefitPolicyCoverageService.getLimitConsumedForYear).
                // Comparing the two on the same axis is what fixed the member window's
                // "remaining limit" figure disagreeing with what the engine itself
                // already capped each line to.
                benefitPolicyCoverageService.validateAmountLimits(
                        lockedMember, lockedMember.getBenefitPolicy(), ClaimFinancialTotals.sumLimitConsumption(claim),
                        claim.getServiceDate(), claim.getId());
            }

            // Append-only explanation rows are written before APPROVED and in the
            // same transaction as the later bucket/provider ledger listeners.
            limitSnapshotService.saveAll(limitSnapshotFactory.build(claim, adjudication));
        } else {
            claimFinancialInvariantGuard.assertConsistent(claim);
        }

        claim.markCoverageSynced();
        return claim.getApprovedAmount();
    }
}
