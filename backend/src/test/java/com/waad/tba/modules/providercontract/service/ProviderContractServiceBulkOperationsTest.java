package com.waad.tba.modules.providercontract.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.BulkProviderContractResultDto;
import com.waad.tba.modules.providercontract.dto.BulkProviderContractUpdateDto;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the "never collapse a mixed bulk result into a single generic
 * failure toast" requirement: bulkUpdateContracts and bulkDelete must
 * process every contract independently and report a per-contract result,
 * even when some contracts in the same batch are invalid.
 */
@ExtendWith(MockitoExtension.class)
class ProviderContractServiceBulkOperationsTest {

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

    private ProviderContract activeContract;
    private ProviderContract draftContract;
    private ProviderContract terminatedContract;

    @BeforeEach
    void setUp() {
        Provider provider = Provider.builder().id(10L).name("Test Provider").build();

        activeContract = ProviderContract.builder()
                .id(1L).contractCode("C-1").provider(provider)
                .pricingScope(PricingScope.GLOBAL).status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(10)).active(true).build();

        draftContract = ProviderContract.builder()
                .id(2L).contractCode("C-2").provider(provider)
                .pricingScope(PricingScope.GLOBAL).status(ContractStatus.DRAFT)
                .startDate(LocalDate.now().minusDays(10)).active(true).build();

        terminatedContract = ProviderContract.builder()
                .id(3L).contractCode("C-3").provider(provider)
                .pricingScope(PricingScope.GLOBAL).status(ContractStatus.TERMINATED)
                .startDate(LocalDate.now().minusDays(10)).active(true).build();

        lenient().when(contractRepository.save(any(ProviderContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── bulkUpdateContracts ──────────────────────────────────────────────

    @Test
    void bulkUpdateReportsPerContractSuccessAndFailureInSameBatch() {
        // draftContract -> ACTIVE succeeds; terminatedContract can never be bulk-updated.
        when(contractRepository.findAllById(List.of(2L, 3L)))
                .thenReturn(List.of(draftContract, terminatedContract));

        BulkProviderContractUpdateDto dto = BulkProviderContractUpdateDto.builder()
                .contractIds(List.of(2L, 3L))
                .status(ContractStatus.ACTIVE)
                .updateStatus(true)
                .build();

        BulkProviderContractResultDto result = contractService.bulkUpdateContracts(dto);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getResults())
                .anySatisfy(r -> {
                    assertThat(r.getContractId()).isEqualTo(2L);
                    assertThat(r.isSuccess()).isTrue();
                })
                .anySatisfy(r -> {
                    assertThat(r.getContractId()).isEqualTo(3L);
                    assertThat(r.isSuccess()).isFalse();
                    assertThat(r.getMessage()).contains("ملغى");
                });
    }

    @Test
    void bulkUpdateReportsMissingContractWithoutFailingWholeBatch() {
        when(contractRepository.findAllById(List.of(2L, 999L)))
                .thenReturn(List.of(draftContract));

        BulkProviderContractUpdateDto dto = BulkProviderContractUpdateDto.builder()
                .contractIds(List.of(2L, 999L))
                .status(ContractStatus.ACTIVE)
                .updateStatus(true)
                .build();

        BulkProviderContractResultDto result = contractService.bulkUpdateContracts(dto);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getResults())
                .anySatisfy(r -> {
                    assertThat(r.getContractId()).isEqualTo(999L);
                    assertThat(r.isSuccess()).isFalse();
                    assertThat(r.getMessage()).contains("غير موجود");
                });
    }

    @Test
    void bulkUpdateToActiveFailsWhenAnotherActiveContractAlreadyExistsForSameScope() {
        ProviderContract alreadyActive = ProviderContract.builder()
                .id(99L).contractCode("C-99").provider(draftContract.getProvider())
                .pricingScope(PricingScope.GLOBAL).status(ContractStatus.ACTIVE).active(true).build();

        when(contractRepository.findAllById(List.of(2L))).thenReturn(List.of(draftContract));
        when(contractRepository.findActiveContractByProvider(10L))
                .thenReturn(Optional.of(alreadyActive));

        BulkProviderContractUpdateDto dto = BulkProviderContractUpdateDto.builder()
                .contractIds(List.of(2L))
                .status(ContractStatus.ACTIVE)
                .updateStatus(true)
                .build();

        BulkProviderContractResultDto result = contractService.bulkUpdateContracts(dto);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getResults().get(0).getMessage()).contains("يوجد عقد نشط مسبقاً");
    }

    // ── bulkDelete ───────────────────────────────────────────────────────

    @Test
    void bulkDeleteSkipsActiveContractButDeletesTheRest() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(activeContract));
        when(contractRepository.findById(2L))
                .thenReturn(Optional.of(draftContract), Optional.of(draftContract));

        BulkProviderContractResultDto result = contractService.bulkDelete(List.of(1L, 2L));

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getResults())
                .anySatisfy(r -> {
                    assertThat(r.getContractId()).isEqualTo(1L);
                    assertThat(r.isSuccess()).isFalse();
                    assertThat(r.getMessage()).contains("Cannot delete an active contract");
                })
                .anySatisfy(r -> {
                    assertThat(r.getContractId()).isEqualTo(2L);
                    assertThat(r.isSuccess()).isTrue();
                });
        assertThat(draftContract.getActive()).isFalse();
        assertThat(activeContract.getActive()).isTrue();
    }

    // ── single delete() rule reused by bulkDelete ───────────────────────

    @Test
    void deleteRejectsActiveContract() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(activeContract));

        assertThatThrownBy(() -> contractService.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete an active contract");
    }

    @Test
    void deleteSoftDeletesNonActiveContractAndWritesAuditLog() {
        when(contractRepository.findById(2L)).thenReturn(Optional.of(draftContract));

        contractService.delete(2L);

        assertThat(draftContract.getActive()).isFalse();
        org.mockito.Mockito.verify(auditLogService).createAuditLog(
                org.mockito.ArgumentMatchers.eq("DELETE"),
                org.mockito.ArgumentMatchers.eq("ProviderContract"),
                org.mockito.ArgumentMatchers.eq(2L),
                any(), any(), any(), any(), any());
    }
}
