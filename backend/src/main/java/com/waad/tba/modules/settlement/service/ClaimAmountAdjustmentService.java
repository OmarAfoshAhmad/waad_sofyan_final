package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.AccountTransaction.ReferenceType;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.event.ClaimAmountAdjustedEvent;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a change to an already-approved claim's amount to the provider's
 * account.
 *
 * Replaces a prior handler that called {@code ProviderAccount.debit()} when the
 * approved amount decreased — that raised {@code totalPaid} as if a payment had
 * occurred, when no money moved at all. The company's liability simply changed,
 * so only {@code totalApproved} and {@code runningBalance} move here; see
 * {@link ProviderAccount#adjustApprovedAmount}.
 *
 * Idempotency is keyed by (claimId, claimVersion), not claimId alone, because a
 * claim can legitimately be adjusted more than once. The account row is locked
 * BEFORE the idempotency check so two adjustments to the same account — whether
 * a genuine second edit or a redelivery of the same event — serialize through
 * the same lock rather than racing each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAmountAdjustmentService {

    private final ProviderAccountRepository accounts;
    private final AccountTransactionRepository transactions;

    @EventListener
    @Transactional
    public void onClaimAmountAdjusted(ClaimAmountAdjustedEvent event) {
        BigDecimal delta = event.getDeltaAmount();
        if (delta.signum() == 0) {
            return; // No financial change
        }

        ProviderAccount account = accounts.findByProviderIdForUpdate(event.getProviderId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Provider account not found for provider: " + event.getProviderId()));

        // Under the account lock: a concurrent redelivery of the same
        // (claimId, claimVersion) — or a second genuine adjustment racing it —
        // is now serialized behind this check, not just an unenforced app-level
        // convention. The unique index in V141 is the backstop if this is ever
        // bypassed.
        if (transactions.existsByReferenceTypeAndReferenceIdAndReferenceVersion(
                ReferenceType.CLAIM_AMOUNT_ADJUSTMENT, event.getClaimId(), event.getClaimVersion())) {
            log.info("Claim amount adjustment already recorded for claim {} version {} — skipping replay",
                    event.getClaimId(), event.getClaimVersion());
            return;
        }

        BigDecimal balanceBefore = account.getRunningBalance();
        account.adjustApprovedAmount(delta);
        accounts.saveAndFlush(account);

        transactions.saveAndFlush(AccountTransaction.createClaimAmountAdjustment(
                account.getId(), event.getClaimId(), event.getClaimVersion(), delta, balanceBefore,
                event.getUserId()));

        log.info("Claim {} amount adjustment applied: delta={}, provider={}, newApproved={}, newBalance={}",
                event.getClaimId(), delta, event.getProviderId(),
                account.getTotalApproved(), account.getRunningBalance());
    }
}
