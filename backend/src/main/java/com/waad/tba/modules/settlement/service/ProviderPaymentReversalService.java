package com.waad.tba.modules.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.settlement.dto.ProviderPaymentReversalResultDto;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPayment.Status;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;

import lombok.RequiredArgsConstructor;

/** Atomically neutralises a posted provider payment with one compensating credit. */
@Service
@RequiredArgsConstructor
public class ProviderPaymentReversalService {
    private final ProviderPaymentRepository payments;
    private final ProviderAccountRepository accounts;
    private final AccountTransactionRepository transactions;

    @Transactional
    public ProviderPaymentReversalResultDto reverse(Long paymentId, String reason,
            Long expectedPaymentVersion, Long expectedAccountVersion,
            String actorUsername, Long actorUserId) {
        validateRequest(paymentId, expectedPaymentVersion, expectedAccountVersion, actorUsername);

        ProviderPayment locator = payments.findById(paymentId)
                .orElseThrow(() -> new BusinessRuleException("دفعة مقدم الخدمة غير موجودة: " + paymentId));
        ProviderAccount account = accounts.findByProviderIdForUpdate(locator.getProviderId())
                .orElseThrow(() -> new BusinessRuleException(
                        "لا يوجد حساب مالي لمقدم الخدمة: " + locator.getProviderId()));
        ProviderPayment payment = payments.findByIdWithAllocationsForUpdate(paymentId)
                .orElseThrow(() -> new BusinessRuleException("دفعة مقدم الخدمة غير موجودة: " + paymentId));

        if (!payment.getProviderId().equals(account.getProviderId())) {
            throw new BusinessRuleException("تغيّر مقدم الخدمة أثناء عكس الدفعة؛ حدّث الصفحة وحاول مجدداً");
        }
        if (payment.getStatus() == Status.REVERSED) return replay(payment, account);
        if (payment.getStatus() != Status.POSTED) {
            throw new BusinessRuleException("لا يمكن عكس دفعة غير مُرحّلة؛ الحالة الحالية: " + payment.getStatus());
        }
        validateReason(reason);
        requireVersion("الدفعة", expectedPaymentVersion, payment.getVersion());
        requireVersion("حساب مقدم الخدمة", expectedAccountVersion, account.getVersion());
        if (!account.isActive()) throw new BusinessRuleException("حساب مقدم الخدمة غير نشط ولا يقبل العكس");
        if (account.getTotalPaid().compareTo(payment.getAmount()) < 0) {
            throw new BusinessRuleException(String.format(
                    "لا يمكن عكس هذه الدفعة: المدفوع المسجّل (%s) أقل من مبلغها (%s)، "
                    + "مما يدل على انحراف مالي بين دفتر الحركات وحساب مقدم الخدمة. "
                    + "يلزم تصحيح الحساب بإجراء مالي معتمد قبل إعادة المحاولة.",
                    account.getTotalPaid(), payment.getAmount()));
        }

        BigDecimal balanceBefore = account.getRunningBalance();
        account.reverseProviderPayment(payment.getAmount());
        accounts.saveAndFlush(account);

        String recordedReason = reason.trim();
        AccountTransaction reversal = transactions.saveAndFlush(
                AccountTransaction.createProviderPaymentReversalCredit(account.getId(), payment.getId(),
                        payment.getAmount(), balanceBefore, LocalDate.now(), recordedReason, actorUserId));

        payment.setReversalLedgerTransactionId(reversal.getId());
        payment.setReversedAt(LocalDateTime.now());
        payment.setReversedBy(actorUsername.trim());
        payment.setReversalReason(recordedReason);
        payment.setUpdatedBy(actorUsername.trim());
        payment.setStatus(Status.REVERSED);
        payment = payments.saveAndFlush(payment);
        return result(payment, account, reversal, false);
    }

    private ProviderPaymentReversalResultDto replay(ProviderPayment payment, ProviderAccount account) {
        AccountTransaction reversal = transactions.findById(payment.getReversalLedgerTransactionId())
                .orElseThrow(() -> new BusinessRuleException("قيد عكس الدفعة غير موجود رغم أن حالتها REVERSED"));
        return result(payment, account, reversal, true);
    }

    private ProviderPaymentReversalResultDto result(ProviderPayment payment, ProviderAccount account,
            AccountTransaction reversal, boolean replay) {
        BigDecimal affected = payment.getAllocations().stream()
                .map(a -> a.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return ProviderPaymentReversalResultDto.builder()
                .paymentId(payment.getId()).originalLedgerTransactionId(payment.getLedgerTransactionId())
                .reversalLedgerTransactionId(reversal.getId()).providerAccountId(account.getId())
                .amount(payment.getAmount()).balanceBefore(reversal.getBalanceBefore())
                .balanceAfter(reversal.getBalanceAfter()).currentTotalPaid(account.getTotalPaid())
                .affectedAllocationCount(payment.getAllocations().size())
                .affectedAllocatedAmount(affected).reversedAt(payment.getReversedAt())
                .reversedBy(payment.getReversedBy()).reversalReason(payment.getReversalReason())
                .paymentVersion(payment.getVersion()).accountVersion(account.getVersion())
                .idempotentReplay(replay).build();
    }

    private void requireVersion(String subject, Long expected, Long actual) {
        if (!expected.equals(actual)) {
            throw new BusinessRuleException(subject + " تغيّر منذ المعاينة؛ حدّث الصفحة قبل العكس");
        }
    }

    private void validateRequest(Long paymentId, Long paymentVersion,
            Long accountVersion, String actor) {
        if (paymentId == null) throw new BusinessRuleException("معرّف الدفعة مطلوب");
        if (paymentVersion == null || accountVersion == null) {
            throw new BusinessRuleException("نسخة الدفعة والحساب مطلوبة للعكس الآمن");
        }
        if (actor == null || actor.isBlank()) throw new BusinessRuleException("هوية منفذ العكس مطلوبة");
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("سبب عكس الدفعة مطلوب");
        if (reason.trim().length() > 1000) throw new BusinessRuleException("سبب العكس يتجاوز 1000 حرف");
    }
}
