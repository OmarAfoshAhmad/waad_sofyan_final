package com.waad.tba.modules.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest.AllocationInput;
import com.waad.tba.modules.settlement.dto.ProviderPaymentDto;
import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;
import com.waad.tba.modules.settlement.service.ProviderPaymentDraftService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Regression test for a bug the live smoke test caught but the mocked
 * ProviderPaymentControllerGateTest could not: GET /provider-payments/by-provider/{id}
 * threw LazyInitializationException because
 * {@code findByProviderIdOrderByPaymentDateDesc} returned entities whose
 * Hibernate session had already closed by the time the controller mapped
 * {@code payment.getAllocations()} into the DTO. A mocked repository can
 * never reproduce this — it takes a real session boundary, hence no
 * @Transactional on this test class (matching the convention already used by
 * every other Phase 2-9 integration test that guards lazy-loading behavior).
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentControllerIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ProviderPaymentController controller;
    @Autowired ProviderPaymentDraftService draftService;
    @Autowired ProviderRepository providers;
    @Autowired EmployerRepository employers;
    @MockitoBean PermissionGuard permissionGuard;

    @BeforeEach
    void allowSettlementViewForThisLazyLoadingRegressionTest() {
        when(permissionGuard.has("SETTLEMENT_VIEW")).thenReturn(true);
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTLEMENT_VIEW")
    void listByProviderReadsAllocationsWithoutLazyInitializationException() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long providerId = providers.save(Provider.builder().name("Regression Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("REG-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        Long employerId = employers.save(Employer.builder().name("Regression Co " + suffix)
                .code("REG-" + suffix).active(true).build()).getId();

        CreateProviderPaymentRequest request = new CreateProviderPaymentRequest();
        request.setProviderId(providerId);
        request.setAmount(new BigDecimal("75.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        AllocationInput allocation = new AllocationInput();
        allocation.setEmployerId(employerId);
        allocation.setTargetYear(2026);
        allocation.setTargetMonth(6);
        allocation.setAmount(new BigDecimal("75.00"));
        allocation.setOutstandingAtAllocation(new BigDecimal("75.00"));
        allocation.setAllocationMethod(AllocationMethod.AUTO_FIFO);
        request.setAllocations(java.util.List.of(allocation));

        ProviderPayment draft = draftService.createDraft(request, "tester");
        assertThat(draft.getAllocations()).hasSize(1); // sanity: the draft really has an allocation to read back

        // The bug only reproduces through the real controller entry point — a
        // fresh call with no ambient transaction/session from the test itself.
        assertThatCode(() -> controller.listByProvider(providerId)).doesNotThrowAnyException();

        ApiResponse<java.util.List<ProviderPaymentDto>> body = controller.listByProvider(providerId).getBody();
        assertThat(body).isNotNull();
        java.util.List<ProviderPaymentDto> paymentsList = body.getData();
        assertThat(paymentsList).hasSize(1);
        assertThat(paymentsList.get(0).getAllocations()).hasSize(1);
        assertThat(paymentsList.get(0).getAllocations().get(0).getAmount()).isEqualByComparingTo("75.00");
    }
}
