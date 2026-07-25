package com.waad.tba.modules.settlement.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.settlement.dto.PaymentRequestDto;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.repository.PaymentAuditLogRepository;
import com.waad.tba.modules.settlement.repository.PaymentRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for SECTION_02 HIGH finding #6: PaymentService.addPayment
 * had no idempotency check, so a retried/double-submitted POST with the same
 * reference number created a second PaymentRecord — a real double-payment
 * risk. This was also the first test ever added for the `settlement` module
 * (previously zero coverage on money-moving code).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceIdempotencyTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private PaymentAuditLogRepository paymentAuditLogRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequestDto request;

    @BeforeEach
    void setUp() {
        request = new PaymentRequestDto();
        request.setEmployerId(1L);
        request.setProviderId(251L);
        request.setTargetYear(2026);
        request.setTargetMonth(7);
        request.setAmount(BigDecimal.valueOf(500));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setReferenceNumber("REF-DUP-001");

        // Backs getMonthlySettlementSummaries(): one open summary with enough
        // remaining amount to accept the payment on the happy path.
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        Object[] row = new Object[] { 1L, "Employer A", 251L, "Provider X", 2026, 7, 1000.0 };
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        when(paymentRecordRepository.findAllActivePayments()).thenReturn(List.of());
    }

    @BeforeEach
    void injectPersistenceContext() {
        // entityManager is a @PersistenceContext field, not a constructor
        // param, so Mockito's constructor-based @InjectMocks does not wire it.
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "entityManager", entityManager);
    }

    @Test
    void rejectsSecondPaymentWithSameReferenceNumberForSameProvider() {
        when(paymentRecordRepository.existsByEmployerIdAndProviderIdAndReferenceNumberAndDeletedFalse(
                1L, 251L, "REF-DUP-001")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.addPayment(request, "user1"))
                .isInstanceOf(BusinessRuleException.class);

        verify(paymentRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsPaymentWhenReferenceNumberIsNew() {
        when(paymentRecordRepository.existsByEmployerIdAndProviderIdAndReferenceNumberAndDeletedFalse(
                1L, 251L, "REF-DUP-001")).thenReturn(false);
        when(paymentRecordRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        paymentService.addPayment(request, "user1");

        verify(paymentRecordRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsPaymentWithNoReferenceNumber() {
        request.setReferenceNumber(null);
        when(paymentRecordRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        paymentService.addPayment(request, "user1");

        verify(paymentRecordRepository, never())
                .existsByEmployerIdAndProviderIdAndReferenceNumberAndDeletedFalse(
                        anyLong(), anyLong(), anyString());
        verify(paymentRecordRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
