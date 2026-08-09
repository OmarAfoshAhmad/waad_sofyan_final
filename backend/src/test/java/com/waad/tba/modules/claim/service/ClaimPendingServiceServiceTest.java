package com.waad.tba.modules.claim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.dto.PendingServiceCreateRequest;
import com.waad.tba.modules.claim.dto.PendingServiceDecisionRequest;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimPendingService;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.PendingServiceStatus;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimPendingServiceRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.medicaldictionary.dto.V50ClassificationResult;
import com.waad.tba.modules.medicaldictionary.enums.V50ClassificationStatus;
import com.waad.tba.modules.medicaldictionary.service.V50MedicalClassificationEngine;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimPendingServiceServiceTest {

    @Mock ClaimRepository claimRepository;
    @Mock ClaimPendingServiceRepository pendingRepository;
    @Mock ClaimMapper claimMapper;
    @Mock MedicalCategoryRepository categoryRepository;
    @Mock ProviderContractRepository contractRepository;
    @Mock ProviderContractPricingItemRepository pricingRepository;
    @Mock V50MedicalClassificationEngine classifier;
    @Mock AuthorizationService authorizationService;
    @Mock JdbcTemplate jdbc;
    @Mock ObjectMapper objectMapper;

    @InjectMocks ClaimPendingServiceService service;

    @Test
    void createAddsAProvisionalLineAndRecalculatesWithoutPostingApproval() {
        Claim claim = Claim.builder().id(10L).providerId(20L).providerName("Provider")
                .status(ClaimStatus.UNDER_REVIEW).build();
        User reviewer = User.builder().id(30L).userType("MEDICAL_REVIEWER").build();
        PendingServiceCreateRequest request = new PendingServiceCreateRequest();
        request.setServiceCode("NEW-1");
        request.setServiceName("خدمة جديدة");
        request.setProposedCategoryId(40L);
        request.setProposedUnitPrice(new BigDecimal("125.00"));

        when(claimRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(categoryRepository.findActiveById(40L)).thenReturn(Optional.of(MedicalCategory.builder().id(40L).build()));
        when(authorizationService.getCurrentUser()).thenReturn(reviewer);
        when(classifier.classify(any())).thenReturn(classification());
        when(pendingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ClaimPendingService pending = invocation.getArgument(0);
            pending.setId(50L);
            return pending;
        });

        var response = service.create(10L, request);

        assertThat(response.status()).isEqualTo(PendingServiceStatus.PRELIMINARY);
        assertThat(claim.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getPendingServiceId()).isEqualTo(50L);
            assertThat(line.getRequestedUnitPrice()).isEqualByComparingTo("125.00");
        });
        verify(claimMapper).recalculateForApproval(claim);
        verify(claimRepository).save(claim);
    }

    @Test
    void ordinaryReviewerCannotDecideEvenWhenServiceIsCalledDirectly() {
        Claim claim = Claim.builder().id(10L).providerId(20L).status(ClaimStatus.UNDER_REVIEW).build();
        ClaimPendingService pending = ClaimPendingService.builder()
                .id(50L).claim(claim).providerId(20L).status(PendingServiceStatus.PRELIMINARY)
                .proposedServiceName("خدمة").proposedCategoryId(40L)
                .proposedUnitPrice(new BigDecimal("100.00")).build();
        PendingServiceDecisionRequest request = new PendingServiceDecisionRequest();
        request.setDecision(PendingServiceStatus.APPROVED_CLAIM_ONLY);
        request.setReason("معتمد طبياً");

        when(claimRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(pendingRepository.findByIdAndClaimId(50L, 10L)).thenReturn(Optional.of(pending));
        when(authorizationService.getCurrentUser()).thenReturn(
                User.builder().id(30L).userType("MEDICAL_REVIEWER").build());

        assertThatThrownBy(() -> service.decide(10L, 50L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("اعتماد الخدمة مسموح فقط");
    }

    private V50ClassificationResult classification() {
        return new V50ClassificationResult(
                1L, "V50", "CONCEPT-1", "CAT-1", "Category",
                "خدمة جديدة", "New service", null, null, null, null,
                "EXACT", BigDecimal.ONE, V50ClassificationStatus.AUTO_APPROVED,
                "exact", null, false, 99L);
    }
}
