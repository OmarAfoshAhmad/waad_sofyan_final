package com.waad.tba.modules.claim.service;

import java.time.LocalDate;
import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;

/** Fail-closed rules for attaching one approved reservation to one claim. */
@Component
public class PreAuthClaimLinkValidator {

    private static final EnumSet<PreAuthStatus> USABLE = EnumSet.of(
            PreAuthStatus.APPROVED, PreAuthStatus.PARTIALLY_APPROVED,
            PreAuthStatus.ACKNOWLEDGED);

    public void validate(PreAuthorization preAuth, Long memberId, Long providerId,
            Long policyId, LocalDate serviceDate) {
        if (!Boolean.TRUE.equals(preAuth.getActive()) || !USABLE.contains(preAuth.getStatus())) {
            throw new BusinessRuleException("الموافقة المسبقة ليست في حالة صالحة للاستخدام في مطالبة");
        }
        if (!memberId.equals(preAuth.getMemberId())) {
            throw new BusinessRuleException("الموافقة المسبقة لا تخص المستفيد المحدد");
        }
        if (!providerId.equals(preAuth.getProviderId())) {
            throw new BusinessRuleException("الموافقة المسبقة لا تخص مقدم الخدمة المحدد");
        }
        if (preAuth.getPolicyId() != null && !preAuth.getPolicyId().equals(policyId)) {
            throw new BusinessRuleException("وثيقة الموافقة المسبقة لا تطابق وثيقة المستفيد في تاريخ الخدمة");
        }
        if (preAuth.getRequestDate() != null && serviceDate.isBefore(preAuth.getRequestDate())) {
            throw new BusinessRuleException("تاريخ الخدمة يسبق تاريخ طلب الموافقة المسبقة");
        }
        if (preAuth.getExpiryDate() != null && serviceDate.isAfter(preAuth.getExpiryDate())) {
            throw new BusinessRuleException("الموافقة المسبقة منتهية في تاريخ الخدمة المحدد");
        }
        if (preAuth.getExpectedServiceDate() == null) {
            throw new BusinessRuleException("الموافقة المسبقة لا تحمل تاريخ خدمة متوقعاً موثوقاً");
        }
    }
}
