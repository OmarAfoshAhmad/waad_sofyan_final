package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;

class PreAuthClaimLinkValidatorTest {

    private final PreAuthClaimLinkValidator validator = new PreAuthClaimLinkValidator();
    private final LocalDate serviceDate = LocalDate.of(2025, 8, 12);

    @Test
    void acceptsAnApprovedMatchingAuthorizationWithinItsValidityWindow() {
        assertThatCode(() -> validator.validate(valid(), 7L, 8L, 31L, serviceDate))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnotherMemberProviderPolicyExpiredAndConsumedAuthorization() {
        PreAuthorization anotherMember = valid(); anotherMember.setMemberId(70L);
        PreAuthorization anotherProvider = valid(); anotherProvider.setProviderId(80L);
        PreAuthorization anotherPolicy = valid(); anotherPolicy.setPolicyId(310L);
        PreAuthorization expired = valid(); expired.setExpiryDate(serviceDate.minusDays(1));
        PreAuthorization consumed = valid(); consumed.setStatus(PreAuthStatus.USED);
        assertRejected(anotherMember, "المستفيد");
        assertRejected(anotherProvider, "مقدم الخدمة");
        assertRejected(anotherPolicy, "وثيقة");
        assertRejected(expired, "منتهية");
        assertRejected(consumed, "حالة صالحة");
    }

    private void assertRejected(PreAuthorization preAuth, String message) {
        assertThatThrownBy(() -> validator.validate(preAuth, 7L, 8L, 31L, serviceDate))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(message);
    }

    private PreAuthorization valid() {
        return PreAuthorization.builder().id(1L).active(true).status(PreAuthStatus.APPROVED)
                .memberId(7L).providerId(8L).policyId(31L)
                .requestDate(serviceDate.minusDays(5)).expectedServiceDate(serviceDate)
                .expiryDate(serviceDate.plusDays(10)).build();
    }
}
