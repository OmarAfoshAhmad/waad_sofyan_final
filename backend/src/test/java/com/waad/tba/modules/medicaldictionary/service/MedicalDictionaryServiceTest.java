package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.MedicalDictionaryEntryResponse;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionaryEntry;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionaryEntryRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySuggestionRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySynonymRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationItemRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationSessionRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
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

import java.util.List;

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
}
