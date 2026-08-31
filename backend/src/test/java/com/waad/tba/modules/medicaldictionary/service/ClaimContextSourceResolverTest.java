package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.claimcontext.entity.ClaimContextDefinition;
import com.waad.tba.modules.claimcontext.entity.ClaimContextSourceAlias;
import com.waad.tba.modules.claimcontext.repository.ClaimContextSourceAliasRepository;
import com.waad.tba.modules.claimcontext.service.ClaimContextSourceResolver;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClaimContextSourceResolverTest {
    private final ClaimContextSourceAliasRepository repository = mock(ClaimContextSourceAliasRepository.class);
    private final ClaimContextSourceResolver resolver = new ClaimContextSourceResolver(
            repository, new MedicalDictionaryNormalizer());

    @Test
    void resolvesArabicSpellingThroughTheCanonicalNormalizer() {
        var context = ClaimContextDefinition.builder().code("INPATIENT")
                .nameAr("إيواء").baseEncounterType(EncounterType.INPATIENT).build();
        when(repository.resolveCandidates("ايواء", 41L)).thenReturn(List.of(
                ClaimContextSourceAlias.builder().claimContext(context).build()));

        assertThat(resolver.resolve("  إِيــواء  ", 41L)).get()
                .extracting(ClaimContextSourceResolver.Resolution::claimContextCode)
                .isEqualTo("INPATIENT");
        verify(repository).resolveCandidates("ايواء", 41L);
    }

    @Test
    void resolvesANewDatabaseContextWithoutAddingAnEnumValue() {
        var context = ClaimContextDefinition.builder().code("HOME_CARE")
                .nameAr("رعاية منزلية").baseEncounterType(EncounterType.INPATIENT).build();
        when(repository.resolveCandidates("رعايه منزليه", null)).thenReturn(List.of(
                ClaimContextSourceAlias.builder().claimContext(context)
                        .medicalCategoryCode("CAT-HOME-NURSING").build()));

        assertThat(resolver.resolve("رعاية منزلية", null)).contains(
                new ClaimContextSourceResolver.Resolution("HOME_CARE", "CAT-HOME-NURSING", false));
    }

    @Test
    void leavesAnUnknownSourceUnresolvedInsteadOfGuessing() {
        when(repository.resolveCandidates("تصنيف جديد غير معرف", 7L)).thenReturn(List.of());
        assertThat(resolver.resolve("تصنيف جديد غير معرّف", 7L)).isEmpty();
    }
}
