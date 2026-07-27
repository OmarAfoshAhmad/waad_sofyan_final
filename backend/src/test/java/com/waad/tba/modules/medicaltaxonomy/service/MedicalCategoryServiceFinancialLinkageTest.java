package com.waad.tba.modules.medicaltaxonomy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a CRITICAL finding from the classifications-module
 * closure pass: delete()/hardDelete()/bulkDelete() had NO check at all for
 * whether a category was still referenced by a medical service, a benefit
 * policy coverage rule, or a provider contract pricing item — deleting one
 * silently orphaned live financial/coverage configuration. The counts must
 * not filter by active=true, since an inactive-but-still-referenced row is
 * still a real financial link.
 */
@ExtendWith(MockitoExtension.class)
class MedicalCategoryServiceFinancialLinkageTest {

    @Mock private MedicalCategoryRepository categoryRepository;
    @Mock private MedicalServiceRepository serviceRepository;
    @Mock private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Mock private ProviderContractPricingItemRepository pricingItemRepository;

    @InjectMocks
    private MedicalCategoryService service;

    private MedicalCategory category;

    @BeforeEach
    void setUp() {
        category = new MedicalCategory();
        category.setId(7L);
        lenient().when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        lenient().when(categoryRepository.existsById(7L)).thenReturn(true);
    }

    @Test
    void deleteRejectedWhenCategoryHasLinkedMedicalService() {
        when(serviceRepository.countByCategoryId(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(BusinessRuleException.class);

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hardDeleteRejectedWhenCategoryHasLinkedCoverageRule() {
        when(serviceRepository.countByCategoryId(7L)).thenReturn(0L);
        when(benefitPolicyRuleRepository.countByMedicalCategoryId(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.hardDelete(7L)).isInstanceOf(BusinessRuleException.class);

        verify(categoryRepository, never()).deleteById(7L);
    }

    @Test
    void hardDeleteRejectedWhenCategoryHasLinkedPricingItem() {
        when(serviceRepository.countByCategoryId(7L)).thenReturn(0L);
        when(benefitPolicyRuleRepository.countByMedicalCategoryId(7L)).thenReturn(0L);
        when(pricingItemRepository.countByMedicalCategoryId(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.hardDelete(7L)).isInstanceOf(BusinessRuleException.class);

        verify(categoryRepository, never()).deleteById(7L);
    }

    @Test
    void deleteSucceedsWhenCategoryHasNoFinancialLinks() {
        when(serviceRepository.countByCategoryId(7L)).thenReturn(0L);
        when(benefitPolicyRuleRepository.countByMedicalCategoryId(7L)).thenReturn(0L);
        when(pricingItemRepository.countByMedicalCategoryId(7L)).thenReturn(0L);
        when(categoryRepository.save(category)).thenReturn(category);

        service.delete(7L);

        verify(categoryRepository).save(category);
    }

    @Test
    void bulkDeleteRejectsWholeBatchIfAnyCategoryIsLinked() {
        MedicalCategory linked = new MedicalCategory();
        linked.setId(8L);
        when(categoryRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(category, linked));
        when(serviceRepository.countByCategoryId(7L)).thenReturn(0L);
        when(benefitPolicyRuleRepository.countByMedicalCategoryId(7L)).thenReturn(0L);
        when(pricingItemRepository.countByMedicalCategoryId(7L)).thenReturn(0L);
        when(serviceRepository.countByCategoryId(8L)).thenReturn(2L);

        assertThatThrownBy(() -> service.bulkDelete(List.of(7L, 8L))).isInstanceOf(BusinessRuleException.class);

        verify(categoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
