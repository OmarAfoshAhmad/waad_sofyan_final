package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.MedicalDictionaryEntryResponse;
import com.waad.tba.modules.medicaldictionary.dto.PriceListSessionDiffResponse;
import com.waad.tba.modules.medicaldictionary.dto.PriceListSessionPostRequest;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionaryEntry;
import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationItem;
import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationSession;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListItemStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionaryEntryRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySuggestionRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySynonymRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationItemRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationSessionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalDictionaryServiceTest {

    @Mock
    private MedicalDictionaryEntryRepository entryRepository;
    @Mock
    private MedicalDictionarySynonymRepository synonymRepository;
    @Mock
    private MedicalDictionarySuggestionRepository suggestionRepository;
    @Mock
    private PriceListClassificationSessionRepository priceListSessionRepository;
    @Mock
    private PriceListClassificationItemRepository priceListItemRepository;
    @Mock
    private MedicalCategoryRepository medicalCategoryRepository;
    @Mock
    private ProviderContractRepository providerContractRepository;
    @Mock
    private ProviderContractPricingItemRepository providerContractPricingItemRepository;
    @Mock
    private MedicalAuditLogService medicalAuditLogService;
    @Mock
    private AuthorizationService authorizationService;

    @Test
    void searchEntries_doesNotFailWhenLegacyEntryHasNoCategory() {
        MedicalDictionaryEntry legacyEntry = MedicalDictionaryEntry.builder()
                .id(10L)
                .canonicalName("خدمة قديمة بلا تصنيف")
                .normalizedCanonicalName("خدمه قديمه بلا تصنيف")
                .medicalCategory(null)
                .status(DictionaryEntryStatus.APPROVED)
                .defaultConfidence(80)
                .build();

        when(entryRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(legacyEntry)));
        when(synonymRepository.countByEntry_Id(10L)).thenReturn(0L);

        MedicalDictionaryService service = new MedicalDictionaryService(
                entryRepository,
                synonymRepository,
                suggestionRepository,
                priceListSessionRepository,
                priceListItemRepository,
                medicalCategoryRepository,
                providerContractRepository,
                providerContractPricingItemRepository,
                medicalAuditLogService,
                new MedicalDictionaryNormalizer(),
                authorizationService
        );

        Page<MedicalDictionaryEntryResponse> result = service.searchEntries(null, null, Pageable.ofSize(25));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMedicalCategoryId()).isNull();
        assertThat(result.getContent().get(0).getMedicalCategoryCode()).isNull();
        assertThat(result.getContent().get(0).getMedicalCategoryName()).isEqualTo("غير محدد");
    }

    @Test
    void diffPriceListSessionWithContract_reportsIdenticalWhenPriceAndCategoryMatch() {
        MedicalDictionaryService service = newService();
        PriceListClassificationSession session = priceListSession();
        MedicalCategory category = category();
        ProviderContract contract = contract();
        PriceListClassificationItem item = priceListItem("كشف طبي", new BigDecimal("100.00"));
        ProviderContractPricingItem existingPrice = pricingItem(contract, category, "كشف طبي", new BigDecimal("100.00"));

        when(priceListSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(providerContractRepository.findById(200L)).thenReturn(Optional.of(contract));
        when(priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(100L)).thenReturn(List.of(item));
        when(medicalCategoryRepository.findActiveById(10L)).thenReturn(Optional.of(category));
        when(providerContractPricingItemRepository.findByContractIdAndServiceNameActiveTrue(200L, "كشف طبي"))
                .thenReturn(Optional.of(existingPrice));

        PriceListSessionDiffResponse diff = service.diffPriceListSessionWithContract(100L, postRequest());

        assertThat(diff.isHasChanges()).isFalse();
        assertThat(diff.getIdenticalCount()).isEqualTo(1);
        assertThat(diff.getUpdateCount()).isZero();
        assertThat(diff.getCreateCount()).isZero();
        assertThat(diff.getItems().get(0).getAction()).isEqualTo("IDENTICAL");
    }

    @Test
    void diffPriceListSessionWithContract_reportsUpdateWhenPriceChanged() {
        MedicalDictionaryService service = newService();
        PriceListClassificationSession session = priceListSession();
        MedicalCategory category = category();
        ProviderContract contract = contract();
        PriceListClassificationItem item = priceListItem("كشف طبي", new BigDecimal("150.00"));
        ProviderContractPricingItem existingPrice = pricingItem(contract, category, "كشف طبي", new BigDecimal("100.00"));

        when(priceListSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(providerContractRepository.findById(200L)).thenReturn(Optional.of(contract));
        when(priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(100L)).thenReturn(List.of(item));
        when(medicalCategoryRepository.findActiveById(10L)).thenReturn(Optional.of(category));
        when(providerContractPricingItemRepository.findByContractIdAndServiceNameActiveTrue(200L, "كشف طبي"))
                .thenReturn(Optional.of(existingPrice));

        PriceListSessionDiffResponse diff = service.diffPriceListSessionWithContract(100L, postRequest());

        assertThat(diff.isHasChanges()).isTrue();
        assertThat(diff.getUpdateCount()).isEqualTo(1);
        assertThat(diff.getIdenticalCount()).isZero();
        assertThat(diff.getItems().get(0).getAction()).isEqualTo("UPDATE");
        assertThat(diff.getItems().get(0).getCurrentMinPrice()).isEqualByComparingTo("100.00");
        assertThat(diff.getItems().get(0).getNewMinPrice()).isEqualByComparingTo("150.00");
    }

    private MedicalDictionaryService newService() {
        return new MedicalDictionaryService(
                entryRepository,
                synonymRepository,
                suggestionRepository,
                priceListSessionRepository,
                priceListItemRepository,
                medicalCategoryRepository,
                providerContractRepository,
                providerContractPricingItemRepository,
                medicalAuditLogService,
                new MedicalDictionaryNormalizer(),
                authorizationService
        );
    }

    private PriceListSessionPostRequest postRequest() {
        PriceListSessionPostRequest request = new PriceListSessionPostRequest();
        request.setContractId(200L);
        request.setOnlyReviewedItems(true);
        request.setReplaceEffectivePrices(true);
        return request;
    }

    private PriceListClassificationSession priceListSession() {
        return PriceListClassificationSession.builder()
                .id(100L)
                .sessionName("قائمة اختبار")
                .status(PriceListSessionStatus.READY_TO_POST)
                .build();
    }

    private PriceListClassificationItem priceListItem(String serviceName, BigDecimal price) {
        return PriceListClassificationItem.builder()
                .id(1L)
                .rowNumber(1)
                .providerServiceName(serviceName)
                .medicalCategoryId(10L)
                .medicalCategoryCode("CAT-DIAGNOSTIC")
                .medicalCategoryName("الكشوفات الطبية")
                .canonicalName(serviceName)
                .confidence(95)
                .status(PriceListItemStatus.HIGH_CONFIDENCE)
                .minPrice(price)
                .build();
    }

    private MedicalCategory category() {
        return MedicalCategory.builder()
                .id(10L)
                .code("CAT-DIAGNOSTIC")
                .name("الكشوفات الطبية")
                .nameAr("الكشوفات الطبية")
                .active(true)
                .build();
    }

    private ProviderContract contract() {
        return ProviderContract.builder()
                .id(200L)
                .contractCode("PC-001")
                .provider(Provider.builder().id(5L).name("مرفق اختبار").licenseNumber("LIC-1").build())
                .status(ProviderContract.ContractStatus.ACTIVE)
                .active(true)
                .currency("LYD")
                .build();
    }

    private ProviderContractPricingItem pricingItem(ProviderContract contract, MedicalCategory category, String serviceName, BigDecimal price) {
        return ProviderContractPricingItem.builder()
                .id(300L)
                .contract(contract)
                .serviceName(serviceName)
                .medicalCategory(category)
                .contractPrice(price)
                .basePrice(price)
                .active(true)
                .build();
    }
}
