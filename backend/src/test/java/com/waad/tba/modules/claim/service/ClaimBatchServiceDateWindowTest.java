package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.modules.claim.entity.ClaimBatch;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;

class ClaimBatchServiceDateWindowTest {

    private ClaimBatchService service;

    @BeforeEach
    void setUp() {
        service = new ClaimBatchService(
                mock(ClaimBatchRepository.class),
                mock(ProviderRepository.class),
                mock(EmployerRepository.class),
                mock(SystemSettingsService.class));
    }

    @Test
    void augustServiceDateBelongsToAugustBatchEvenWhenEnteredLater() {
        ClaimBatch august = batch("AUG-2026", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        service.validateServiceDateInsideBatch(august, LocalDate.of(2026, 8, 31));
    }

    @Test
    void augustServiceDateIsRejectedFromSeptemberBatch() {
        ClaimBatch september = batch("SEP-2026", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> service.validateServiceDateInsideBatch(september, LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("خارج فترة الدفعة")
                .hasMessageContaining("افتح دفعة شهر الخدمة الحقيقي");
    }

    private ClaimBatch batch(String code, LocalDate start, LocalDate end) {
        return ClaimBatch.builder()
                .batchCode(code)
                .providerId(10L)
                .employerId(20L)
                .batchYear(start.getYear())
                .batchMonth(start.getMonthValue())
                .periodStart(start)
                .periodEnd(end)
                .status(ClaimBatch.ClaimBatchStatus.OPEN)
                .build();
    }
}
