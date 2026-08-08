package com.waad.tba.modules.settlement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.finance.Money;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest.AllocationInput;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Prepares a DRAFT payment — typically the accountant's accepted (or edited)
 * FIFO suggestion from {@link ProviderPaymentAllocationSuggestionService}.
 *
 * Deliberately narrow: this never touches the ledger or the provider account.
 * Allocations need not sum to the payment amount here — the database only
 * requires allocated &lt;= amount at this stage (V137); full allocation is a
 * posting-time concern, not a drafting-time one, since an accountant may
 * legitimately save a draft before deciding how to split the remainder.
 */
@Service
@RequiredArgsConstructor
public class ProviderPaymentDraftService {

    private final ProviderPaymentRepository payments;

    @Transactional
    public ProviderPayment createDraft(CreateProviderPaymentRequest request, String actorUsername) {
        validate(request);

        ProviderPayment draft = ProviderPayment.builder()
                .providerId(request.getProviderId())
                .amount(Money.normalize(request.getAmount()))
                .paymentDate(request.getPaymentDate())
                .paymentMethod(request.getPaymentMethod())
                .referenceNumber(blankToNull(request.getReferenceNumber()))
                .notes(blankToNull(request.getNotes()))
                .idempotencyKey(UUID.randomUUID().toString())
                .status(ProviderPayment.Status.DRAFT)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        for (AllocationInput input : safeList(request.getAllocations())) {
            AllocationMethod method = input.getAllocationMethod() != null
                    ? input.getAllocationMethod() : AllocationMethod.MANUAL;
            if (method == AllocationMethod.MANUAL
                    && (input.getOverrideReason() == null || input.getOverrideReason().isBlank())) {
                throw new BusinessRuleException("سبب التعديل مطلوب عند تعديل التخصيص المقترح يدوياً");
            }
            draft.addAllocation(ProviderPaymentAllocation.builder()
                    .employerId(input.getEmployerId())
                    .targetYear(input.getTargetYear())
                    .targetMonth(input.getTargetMonth())
                    .amount(Money.normalize(input.getAmount()))
                    .outstandingAtAllocation(Money.normalize(input.getOutstandingAtAllocation()))
                    .allocationMethod(method)
                    .overrideReason(blankToNull(input.getOverrideReason()))
                    .createdBy(actorUsername)
                    .build());
        }

        return payments.saveAndFlush(draft);
    }

    private void validate(CreateProviderPaymentRequest request) {
        if (request.getProviderId() == null) throw new BusinessRuleException("معرّف مقدم الخدمة مطلوب");
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BusinessRuleException("مبلغ الدفعة يجب أن يكون موجباً");
        }
        if (request.getPaymentDate() == null) throw new BusinessRuleException("تاريخ الدفعة مطلوب");
        if (request.getPaymentMethod() == null) throw new BusinessRuleException("طريقة الدفع مطلوبة");
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
