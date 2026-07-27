package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a HIGH financial-correctness finding from the
 * reports-module closure pass: when the caller doesn't pass an explicit
 * status filter (which is what the report UI does by default —
 * ProviderSettlementReport.jsx sends `statuses: undefined` until the user
 * picks one), resolveStatuses() previously defaulted to EVERY claim status
 * including SUBMITTED/UNDER_REVIEW/NEEDS_CORRECTION/REJECTED. Since the
 * totals loop sums every claim returned with no per-status split, a
 * provider's "amount owed" figure silently included claims that were still
 * pending review or had been rejected outright. The default must only
 * include claims that represent real payable money: APPROVED/BATCHED/SETTLED.
 */
@ExtendWith(MockitoExtension.class)
class ProviderSettlementReportServiceDefaultStatusTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ProviderContractRepository contractRepository;

    @InjectMocks
    private ProviderSettlementReportService service;

    @BeforeEach
    void setUp() {
        Provider provider = Provider.builder().id(251L).name("Test Hospital").build();
        when(providerRepository.findById(251L)).thenReturn(Optional.of(provider));
        when(claimRepository.findForSettlementReport(anyLong(), any(), anyList(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultStatusFilterExcludesPendingAndRejectedClaims() {
        service.generateReport(251L, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, null, null, null);

        ArgumentCaptor<List<ClaimStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(claimRepository).findForSettlementReport(
                anyLong(), any(), statusesCaptor.capture(), any(), any());

        List<ClaimStatus> effectiveStatuses = statusesCaptor.getValue();
        assertThat(effectiveStatuses)
                .containsExactlyInAnyOrder(ClaimStatus.APPROVED, ClaimStatus.BATCHED, ClaimStatus.SETTLED)
                .doesNotContain(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                        ClaimStatus.NEEDS_CORRECTION, ClaimStatus.REJECTED, ClaimStatus.APPROVAL_IN_PROGRESS);
    }
}
