package com.waad.tba.modules.settlement.service;

import static com.waad.tba.common.finance.Money.ZERO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.finance.Money;
import com.waad.tba.modules.settlement.dto.ProviderPaymentPostResultDto;
import com.waad.tba.modules.settlement.entity.AccountTransaction;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPayment.Status;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentAllocationRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository.OutstandingPeriod;

import lombok.RequiredArgsConstructor;

/** Atomically posts one provider transfer and exactly one ledger debit. */
@Service
@RequiredArgsConstructor
public class ProviderPaymentPostingService {
    private final ProviderPaymentRepository payments;
    private final ProviderPaymentAllocationRepository allocations;
    private final ProviderAccountRepository accounts;
    private final AccountTransactionRepository transactions;

    /**
     * Lock order is deliberately fixed: account -> payment -> allocations.
     * expected versions bind posting to the preview/edit state seen by the user.
     */
    @Transactional
    public ProviderPaymentPostResultDto post(Long paymentId, String idempotencyKey,
            Long expectedPaymentVersion, Long expectedAccountVersion,
            String actorUsername, Long actorUserId) {
        validateRequest(paymentId, idempotencyKey, expectedPaymentVersion,
                expectedAccountVersion, actorUsername);

        ProviderPayment locator = payments.findById(paymentId)
                .orElseThrow(() -> new BusinessRuleException("دفعة مقدم الخدمة غير موجودة: " + paymentId));
        ProviderAccount account = accounts.findByProviderIdForUpdate(locator.getProviderId())
                .orElseThrow(() -> new BusinessRuleException(
                        "لا يوجد حساب مالي لمقدم الخدمة: " + locator.getProviderId()));
        ProviderPayment payment = payments.findByIdWithAllocationsForUpdate(paymentId)
                .orElseThrow(() -> new BusinessRuleException("دفعة مقدم الخدمة غير موجودة: " + paymentId));

        if (!payment.getProviderId().equals(account.getProviderId())) {
            throw new BusinessRuleException("تغيّر مقدم الخدمة أثناء ترحيل الدفعة؛ أعد فتحها وحاول مجدداً");
        }
        if (payment.getStatus() == Status.POSTED) {
            if (idempotencyKey.equals(payment.getIdempotencyKey())) {
                return replay(payment, account);
            }
            throw new BusinessRuleException("الدفعة مُرحّلة مسبقاً بمفتاح طلب مختلف");
        }
        if (payment.getStatus() != Status.DRAFT) {
            throw new BusinessRuleException("لا يمكن ترحيل دفعة في حالة " + payment.getStatus());
        }
        requireVersion("الدفعة", expectedPaymentVersion, payment.getVersion());
        requireVersion("حساب مقدم الخدمة", expectedAccountVersion, account.getVersion());
        requireActive(account);
        bindIdempotency(payment, idempotencyKey);

        revalidateAndRefreshSnapshots(payment);
        allocations.saveAllAndFlush(payment.getAllocations());

        BigDecimal balanceBefore = account.getRunningBalance();
        account.recordProviderPayment(payment.getAmount());
        accounts.saveAndFlush(account);

        AccountTransaction ledger = transactions.saveAndFlush(
                AccountTransaction.createProviderPaymentDebit(account.getId(), payment.getId(),
                        payment.getAmount(), balanceBefore, payment.getPaymentDate(), actorUserId));

        LocalDateTime postedAt = LocalDateTime.now();
        payment.setLedgerTransactionId(ledger.getId());
        payment.setPostedAt(postedAt);
        payment.setPostedBy(actorUsername.trim());
        payment.setUpdatedBy(actorUsername.trim());
        payment.setStatus(Status.POSTED);
        payment = payments.saveAndFlush(payment);

        return result(payment, account, ledger, false);
    }

    private void revalidateAndRefreshSnapshots(ProviderPayment payment) {
        Map<Target, BigDecimal> outstanding = new HashMap<>();
        for (OutstandingPeriod row : payments.findOutstandingPeriodsForAllocation(
                payment.getProviderId(), payment.getPaymentDate())) {
            outstanding.put(new Target(row.getEmployerId(), row.getTargetYear(), row.getTargetMonth()),
                    Money.normalize(row.getOutstandingAmount()));
        }

        BigDecimal allocated = ZERO;
        for (ProviderPaymentAllocation allocation : payment.getAllocations()) {
            validateAllocation(allocation);
            Target target = new Target(allocation.getEmployerId(),
                    allocation.getTargetYear(), allocation.getTargetMonth());
            BigDecimal available = outstanding.getOrDefault(target, ZERO);
            BigDecimal amount = Money.normalize(allocation.getAmount());
            if (amount.compareTo(available) > 0) {
                throw new BusinessRuleException(String.format(
                        "التخصيص لجهة العمل %d عن %04d-%02d (%s) يتجاوز المستحق الحالي (%s)",
                        target.employerId(), target.year(), target.month(), amount, available));
            }
            allocation.setAmount(amount);
            allocation.setOutstandingAtAllocation(available);
            allocated = allocated.add(amount);
        }
        if (allocated.compareTo(payment.getAmount()) > 0) {
            throw new BusinessRuleException("مجموع التخصيصات يتجاوز مبلغ الدفعة");
        }
    }

    private void validateAllocation(ProviderPaymentAllocation allocation) {
        if (allocation.getEmployerId() == null || allocation.getTargetYear() == null
                || allocation.getTargetMonth() == null || allocation.getAmount() == null
                || allocation.getAmount().signum() <= 0 || !Money.isExact(allocation.getAmount())) {
            throw new BusinessRuleException("تخصيص الدفعة يحتوي بيانات أو مبلغاً غير صالح");
        }
        if (allocation.getAllocationMethod() == ProviderPaymentAllocation.AllocationMethod.MANUAL
                && (allocation.getOverrideReason() == null || allocation.getOverrideReason().isBlank())) {
            throw new BusinessRuleException("التخصيص اليدوي يتطلب سبباً واضحاً");
        }
    }

    private void bindIdempotency(ProviderPayment payment, String key) {
        ProviderPayment existing = payments.findByIdempotencyKey(key).orElse(null);
        if (existing != null && !existing.getId().equals(payment.getId())) {
            throw new BusinessRuleException("مفتاح منع التكرار مستخدم لدفعة أخرى");
        }
        if (payment.getIdempotencyKey() != null && !payment.getIdempotencyKey().equals(key)) {
            throw new BusinessRuleException("مفتاح الطلب لا يطابق مفتاح المسودة");
        }
        payment.setIdempotencyKey(key);
    }

    private ProviderPaymentPostResultDto replay(ProviderPayment payment, ProviderAccount account) {
        AccountTransaction ledger = transactions.findById(payment.getLedgerTransactionId())
                .orElseThrow(() -> new BusinessRuleException("قيد دفتر الدفعة المُرحّلة غير موجود"));
        return result(payment, account, ledger, true);
    }

    private ProviderPaymentPostResultDto result(ProviderPayment payment, ProviderAccount account,
            AccountTransaction ledger, boolean replay) {
        return ProviderPaymentPostResultDto.builder()
                .paymentId(payment.getId()).ledgerTransactionId(ledger.getId())
                .providerAccountId(account.getId()).amount(payment.getAmount())
                .allocatedAmount(payment.getAllocatedAmount())
                .unallocatedAmount(payment.getUnallocatedAmount())
                .balanceBefore(ledger.getBalanceBefore()).balanceAfter(ledger.getBalanceAfter())
                .postedAt(payment.getPostedAt()).postedBy(payment.getPostedBy())
                .paymentVersion(payment.getVersion()).accountVersion(account.getVersion())
                .idempotentReplay(replay).build();
    }

    private void requireVersion(String subject, Long expected, Long actual) {
        if (!expected.equals(actual)) {
            throw new BusinessRuleException(subject + " تغيّر منذ المعاينة؛ حدّث الصفحة وراجع الأرقام قبل الترحيل");
        }
    }

    private void requireActive(ProviderAccount account) {
        if (!account.isActive()) {
            throw new BusinessRuleException("حساب مقدم الخدمة غير نشط ولا يقبل دفعات");
        }
    }

    private void validateRequest(Long paymentId, String key, Long paymentVersion,
            Long accountVersion, String actor) {
        if (paymentId == null) throw new BusinessRuleException("معرّف الدفعة مطلوب");
        if (key == null || key.isBlank()) throw new BusinessRuleException("مفتاح منع تكرار الطلب مطلوب");
        if (key.trim().length() > 120) throw new BusinessRuleException("مفتاح منع التكرار يتجاوز 120 حرفاً");
        if (paymentVersion == null || accountVersion == null) {
            throw new BusinessRuleException("نسخة الدفعة والحساب مطلوبة للترحيل الآمن");
        }
        if (actor == null || actor.isBlank()) throw new BusinessRuleException("هوية منفذ الترحيل مطلوبة");
    }

    private record Target(Long employerId, int year, int month) {}
}
