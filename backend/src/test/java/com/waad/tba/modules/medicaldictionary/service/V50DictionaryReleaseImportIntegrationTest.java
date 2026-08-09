package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.medicaldictionary.dto.MedicalDictionaryReleaseResponse;
import com.waad.tba.modules.medicaldictionary.dto.V50ClassificationInput;
import com.waad.tba.modules.medicaldictionary.enums.V50ClassificationStatus;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "waad.v50.seed", matches = ".+")
class V50DictionaryReleaseImportIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private V50DictionaryReleaseImportService service;
    @Autowired private V50MedicalClassificationEngine classificationEngine;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void importsFullVerifiedSeedAtomicallyAndMakesActiveReleaseImmutable() throws Exception {
        Path seed = Path.of(System.getProperty("waad.v50.seed"));
        try (InputStream input = Files.newInputStream(seed)) {
            MedicalDictionaryReleaseResponse result = service.importAndActivate(
                    new MockMultipartFile("file", seed.getFileName().toString(), "application/json", input));

            assertThat(result.getVersion()).isEqualTo("V50");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getCategoryCount()).isEqualTo(46);
            assertThat(result.getConceptCount()).isEqualTo(20_399);
            assertThat(result.getAliasCount()).isEqualTo(97_977);
            assertThat(result.getExceptionCount()).isEqualTo(600);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medical_dictionary_releases WHERE status = 'ACTIVE'", Integer.class)).isOne();

            var exactProviderCode = classificationEngine.classify(new V50ClassificationInput(
                    "ACTH", "ACTH", List.of(), "ACTH", "مختبر", List.of(), "", "المختبر الطبي الأول"));
            assertThat(exactProviderCode.status()).isEqualTo(V50ClassificationStatus.AUTO_APPROVED);
            assertThat(exactProviderCode.categoryCode()).isEqualTo("CAT-LAB");
            assertThat(exactProviderCode.conceptCode()).isEqualTo("WAC-V50-FA504DBB9B5638");
            assertThat(exactProviderCode.dictionaryVersion()).isEqualTo("V50");
            assertThat(exactProviderCode.evidenceId()).isNotNull();

            var nonService = classificationEngine.classify(new V50ClassificationInput(
                    "فتح ملف", null, List.of(), null, null, List.of(), null, null));
            assertThat(nonService.status()).isEqualTo(V50ClassificationStatus.QUARANTINED_NON_SERVICE);
            assertThat(nonService.mayPostToContract()).isFalse();

            var cosmetic = classificationEngine.classify(new V50ClassificationInput(
                    "تبييض الأسنان", null, List.of(), null, null, List.of(), null, null));
            assertThat(cosmetic.status()).isEqualTo(V50ClassificationStatus.EXCLUDED_COSMETIC);
            assertThat(cosmetic.mayPostToContract()).isFalse();

            var unknown = classificationEngine.classify(new V50ClassificationInput(
                    "اسم طبي مبهم غير موجود 987654", null, List.of(), null, null, List.of(), null, null));
            assertThat(unknown.status()).isIn(V50ClassificationStatus.REVIEW_REQUIRED, V50ClassificationStatus.STRONG_SUGGESTION);
            assertThat(unknown.mayPostToContract()).isFalse();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE medical_dictionary_aliases_v2 SET raw_name = 'tampered' WHERE release_id = ? AND id = (SELECT min(id) FROM medical_dictionary_aliases_v2 WHERE release_id = ?)",
                    result.getId(), result.getId()))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immutable");
        }
    }
}
