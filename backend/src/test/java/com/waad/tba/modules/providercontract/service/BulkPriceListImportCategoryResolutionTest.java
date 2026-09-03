package com.waad.tba.modules.providercontract.service;

import com.waad.tba.modules.claimcontext.service.ClaimContextSourceResolver;
import com.waad.tba.modules.claimcontext.service.ClaimContextSourceResolver.Resolution;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The multi-provider bulk import used to classify services by free-text
 * name matching (findFirstByNameAr/En/Name -- first-similar-name-wins, no
 * ranking, no review gate), independently of the approved-alias resolver
 * already unified for the single-contract import. This pins the bulk path
 * onto the same canonical resolver, and guards the free-text lookups from
 * silently returning.
 */
class BulkPriceListImportCategoryResolutionTest {

    private static Method resolveMedicalCategory() throws NoSuchMethodException {
        Method m = BulkPriceListImportService.class.getDeclaredMethod(
                "resolveMedicalCategory", String.class, String.class, String.class, Long.class);
        m.setAccessible(true);
        return m;
    }

    private static BulkPriceListImportService serviceWith(
            MedicalCategoryRepository medicalCategoryRepository,
            ClaimContextSourceResolver resolver) {
        return new BulkPriceListImportService(
                mock(ProviderRepository.class),
                mock(ProviderContractRepository.class),
                mock(ProviderContractPricingItemRepository.class),
                medicalCategoryRepository,
                mock(ProviderContractTermsService.class),
                resolver,
                mock(PlatformTransactionManager.class));
    }

    @Test
    void confidentAliasResolutionAssignsTheApprovedCategory() throws Exception {
        MedicalCategoryRepository medicalCategoryRepository = mock(MedicalCategoryRepository.class);
        ClaimContextSourceResolver resolver = mock(ClaimContextSourceResolver.class);

        MedicalCategory diagnostics = MedicalCategory.builder()
                .id(9L).code("CAT-DIAGNOSTIC").active(true).build();

        when(resolver.resolve(eq("الكشوفات الطبية"), eq(5L)))
                .thenReturn(Optional.of(new Resolution("CTX-OUTPATIENT", "CAT-DIAGNOSTIC", false)));
        when(medicalCategoryRepository.findByCode("CAT-DIAGNOSTIC")).thenReturn(Optional.of(diagnostics));

        BulkPriceListImportService service = serviceWith(medicalCategoryRepository, resolver);
        MedicalCategory result = (MedicalCategory) resolveMedicalCategory().invoke(
                service, null, "العيادات الخارجية", "الكشوفات الطبية", 5L);

        assertThat(result).isEqualTo(diagnostics);
    }

    @Test
    void aResolutionThatRequiresReviewDoesNotSilentlyAssignACategory() throws Exception {
        MedicalCategoryRepository medicalCategoryRepository = mock(MedicalCategoryRepository.class);
        ClaimContextSourceResolver resolver = mock(ClaimContextSourceResolver.class);

        when(resolver.resolve(any(), anyLong()))
                .thenReturn(Optional.of(new Resolution("CTX-OUTPATIENT", "CAT-DIAGNOSTIC", true)));

        BulkPriceListImportService service = serviceWith(medicalCategoryRepository, resolver);
        MedicalCategory result = (MedicalCategory) resolveMedicalCategory().invoke(
                service, null, "تصنيف غامض", null, 5L);

        assertThat(result).isNull();
    }

    @Test
    void noAliasMatchLeavesTheCategoryUnclassifiedRatherThanGuessing() throws Exception {
        MedicalCategoryRepository medicalCategoryRepository = mock(MedicalCategoryRepository.class);
        ClaimContextSourceResolver resolver = mock(ClaimContextSourceResolver.class);

        when(resolver.resolve(any(), anyLong())).thenReturn(Optional.empty());

        BulkPriceListImportService service = serviceWith(medicalCategoryRepository, resolver);
        MedicalCategory result = (MedicalCategory) resolveMedicalCategory().invoke(
                service, null, "لا يوجد تصنيف معتمد بهذا الاسم", null, 5L);

        assertThat(result).isNull();
    }

    @Test
    void doesNotFallBackToFreeTextNameMatching() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/providercontract/service/BulkPriceListImportService.java"));

        assertThat(source)
                .doesNotContain("findFirstByNameAr")
                .doesNotContain("findFirstByNameEn")
                .doesNotContain("findFirstByName(")
                .contains("claimContextSourceResolver.resolve(");
    }
}
