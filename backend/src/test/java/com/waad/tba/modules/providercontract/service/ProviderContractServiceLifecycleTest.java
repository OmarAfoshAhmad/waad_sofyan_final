package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingScope;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the ACTIVE/SUSPENDED/TERMINATED/DRAFT state-machine rules
 * (ProviderContract#canActivate/canSuspend/canTerminate) and that every
 * transition writes an audit log entry.
 */
@ExtendWith(MockitoExtension.class)
class ProviderContractServiceLifecycleTest {

    @Mock
    private ProviderContractRepository contractRepository;
    @Mock
    private ProviderContractPricingItemRepository pricingItemRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProviderContractService contractService;

    private Provider provider;

    @BeforeEach
    void setUp() {
        provider = Provider.builder().id(10L).name("Test Provider").build();
        lenient().when(contractRepository.save(any(ProviderContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contractRepository.findActiveContractByProvider(anyLong())).thenReturn(Optional.empty());
        lenient().when(contractRepository.hasOverlappingContract(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(false);
    }

    private ProviderContract contractWithStatus(ContractStatus status) {
        return ProviderContract.builder()
                .id(1L).contractCode("C-1").provider(provider)
                .pricingScope(PricingScope.GLOBAL).status(status)
                .startDate(LocalDate.now().minusDays(5)).active(true).build();
    }

    @Test
    void activateDraftContractSucceedsAndWritesAuditLog() {
        ProviderContract draft = contractWithStatus(ContractStatus.DRAFT);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(draft));

        ProviderContractResponseDto result = contractService.activate(1L);

        assertThat(result.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(auditLogService).createAuditLog(org.mockito.ArgumentMatchers.eq("ACTIVATE"),
                org.mockito.ArgumentMatchers.eq("ProviderContract"), org.mockito.ArgumentMatchers.eq(1L),
                any(), any(), any(), any(), any());
    }

    @Test
    void activateFailsWhenAnotherActiveContractExistsForSameProviderAndScope() {
        ProviderContract draft = contractWithStatus(ContractStatus.DRAFT);
        ProviderContract existingActive = contractWithStatus(ContractStatus.ACTIVE);
        existingActive.setId(2L);

        when(contractRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(contractRepository.findActiveContractByProvider(10L)).thenReturn(Optional.of(existingActive));

        assertThatThrownBy(() -> contractService.activate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("يوجد عقد نشط مسبقاً");
    }

    @Test
    void activateFailsForExpiredContract() {
        ProviderContract expired = contractWithStatus(ContractStatus.DRAFT);
        expired.setEndDate(LocalDate.now().minusDays(1));
        when(contractRepository.findById(1L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> contractService.activate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void suspendOnlyAllowedFromActive() {
        ProviderContract draft = contractWithStatus(ContractStatus.DRAFT);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> contractService.suspend(1L, "test reason"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot suspend");
    }

    @Test
    void suspendActiveContractRecordsReasonInNotesAndAuditLog() {
        ProviderContract active = contractWithStatus(ContractStatus.ACTIVE);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(active));

        ProviderContractResponseDto result = contractService.suspend(1L, "شكوى من المستفيد");

        assertThat(result.getStatus()).isEqualTo(ContractStatus.SUSPENDED);
        assertThat(active.getNotes()).contains("شكوى من المستفيد");
        verify(auditLogService).createAuditLog(org.mockito.ArgumentMatchers.eq("SUSPEND"),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void terminateNotAllowedFromDraft() {
        ProviderContract draft = contractWithStatus(ContractStatus.DRAFT);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> contractService.terminate(1L, "reason"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot terminate");
    }

    @Test
    void terminateFromSuspendedSucceeds() {
        ProviderContract suspended = contractWithStatus(ContractStatus.SUSPENDED);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(suspended));

        ProviderContractResponseDto result = contractService.terminate(1L, "انتهاء العلاقة التعاقدية");

        assertThat(result.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        verify(auditLogService).createAuditLog(org.mockito.ArgumentMatchers.eq("TERMINATE"),
                any(), any(), any(), any(), any(), any(), any());
    }
}
