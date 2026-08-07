package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.finance.Money;
import com.waad.tba.modules.settlement.dto.ProviderAccountAdjustmentResultDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderAccountReconciliationAudit;
import com.waad.tba.modules.settlement.repository.ProviderAccountReconciliationAuditRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * The sanctioned way to correct a provider account whose recorded paid total
 * disagrees with the ledger.
 *
 * This is the exit the reversal guard depends on. That guard refuses to reverse
 * a payment when {@code totalPaid < payment.amount}, because doing so would
 * drive the total negative — but without a correction path the operator would be
 * permanently blocked, which is the failure mode this project has already hit
 * once (a guard whose message named procedures that did not exist).
 *
 * Deliberately narrow: it only closes a measured ledger-vs-account gap, never
 * invents an amount, never touches totalApproved. The correction is recorded in
 * {@link ProviderAccountReconciliationAudit} — NOT {@code account_transactions}.
 * The amount here is, by construction, exactly the drift reconciliation measured
 * against the ledger; writing it back into that same ledger as an ordinary entry
 * would make the next reconciliation see the correction itself as new drift of
 * the same size. The audit table exists precisely so this correction can never
 * be summed into ledgerNet, or mistaken for a provider payment.
 */
@Service
@RequiredArgsConstructor
public class ProviderAccountAdjustmentService {

    private final ProviderAccountRepository accounts;
    private final ProviderAccountReconciliationAuditRepository audits;
    private final ProviderAccountReconciliationService reconciliation;

    /**
     * Aligns the account's paid total with the ledger.
     *
     * The amount is not supplied by the caller: it is the drift the
     * reconciliation itself measured. Letting a caller pass an arbitrary figure
     * would turn an audited correction into a free-form balance edit.
     */
    @Transactional
    public ProviderAccountAdjustmentResultDto alignPaidTotalWithLedger(Long providerId, String reason,
            Long expectedAccountVersion, String actorUsername, Long actorUserId) {

        validateRequest(providerId, reason, expectedAccountVersion, actorUsername);

        ProviderAccount account = accounts.findByProviderIdForUpdate(providerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "لا يوجد حساب مالي لمقدم الخدمة: " + providerId));

        if (!expectedAccountVersion.equals(account.getVersion())) {
            throw new BusinessRuleException(
                    "حساب مقدم الخدمة تغيّر منذ المطابقة؛ أعد فحص الأرقام قبل التسوية");
        }

        // Measured after the lock, so the correction reflects the state actually
        // being written, not what a stale preview reported.
        ProviderReconciliationDto before = reconciliation.reconcile(providerId);
        BigDecimal drift = Money.normalize(before.getLedgerVsAccountDrift());

        if (drift.signum() == 0) {
            throw new BusinessRuleException(
                    "لا يوجد انحراف بين الدفتر وحساب مقدم الخدمة؛ لا حاجة إلى تسوية");
        }

        BigDecimal totalPaidBefore = account.getTotalPaid();
        BigDecimal runningBalanceBefore = account.getRunningBalance();

        account.applyPaidTotalCorrection(drift);
        accounts.saveAndFlush(account);

        ProviderAccountReconciliationAudit audit = audits.saveAndFlush(ProviderAccountReconciliationAudit.builder()
                .providerAccountId(account.getId())
                .providerId(providerId)
                .adjustmentAmount(drift)
                .totalPaidBefore(totalPaidBefore)
                .totalPaidAfter(account.getTotalPaid())
                .runningBalanceBefore(runningBalanceBefore)
                .runningBalanceAfter(account.getRunningBalance())
                .ledgerVsAccountDriftBefore(drift)
                .reason(reason.trim())
                .performedBy(actorUsername.trim())
                .performedByUserId(actorUserId)
                .build());

        ProviderReconciliationDto after = reconciliation.reconcile(providerId);

        return ProviderAccountAdjustmentResultDto.builder()
                .providerId(providerId)
                .providerAccountId(account.getId())
                .reconciliationAuditId(audit.getId())
                .adjustmentAmount(drift)
                .totalPaidBefore(totalPaidBefore)
                .totalPaidAfter(account.getTotalPaid())
                .runningBalanceBefore(runningBalanceBefore)
                .runningBalanceAfter(account.getRunningBalance())
                .ledgerVsAccountDriftBefore(drift)
                .ledgerVsAccountDriftAfter(Money.normalize(after.getLedgerVsAccountDrift()))
                .reason(reason.trim())
                .performedBy(actorUsername.trim())
                .performedAt(LocalDateTime.now())
                .build();
    }

    private void validateRequest(Long providerId, String reason, Long accountVersion, String actor) {
        if (providerId == null) throw new BusinessRuleException("معرّف مقدم الخدمة مطلوب");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب التسوية المالية مطلوب ولا يمكن تركه فارغاً");
        }
        if (reason.trim().length() > 500) throw new BusinessRuleException("سبب التسوية يتجاوز 500 حرف");
        if (accountVersion == null) throw new BusinessRuleException("نسخة الحساب مطلوبة للتسوية الآمنة");
        if (actor == null || actor.isBlank()) throw new BusinessRuleException("هوية منفذ التسوية مطلوبة");
    }
}
