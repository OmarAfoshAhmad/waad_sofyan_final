package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.claimcontext.service.ClaimContextSourceResolver;
import com.waad.tba.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class DynamicClaimContextsMigrationIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClaimContextSourceResolver resolver;

    @Test
    void seedsOnlyObservedSelectableContextsAndTheirSourceAliases() {
        assertThat(jdbc.queryForList(
                "select code from claim_contexts where active order by display_order", String.class))
                .containsExactly("OUTPATIENT", "INPATIENT", "FULL_COVERAGE",
                        "MATERNITY", "PREGNANCY_COMPLICATIONS")
                .doesNotContain("PHARMACY");
        assertThat(resolver.resolve("إيواء", null)).get()
                .extracting(ClaimContextSourceResolver.Resolution::claimContextCode)
                .isEqualTo("INPATIENT");
        assertThat(resolver.resolve("أشعة تحاليل رسوم أطباء", null)).get()
                .satisfies(result -> {
                    assertThat(result.claimContextCode()).isEqualTo("OUTPATIENT");
                    assertThat(result.medicalCategoryCode()).isEqualTo("CAT-COV-DIAG-FEES");
                });
        assertThat(resolver.resolve("أسنان تجميلي", null)).get()
                .satisfies(result -> {
                    assertThat(result.claimContextCode()).isEqualTo("OUTPATIENT");
                    assertThat(result.medicalCategoryCode()).isEqualTo("CAT-DENT-COSMETIC");
                });
    }

    @Test
    void acceptsANewContextAndAliasAsDataWithoutAnApplicationChange() {
        jdbc.update("insert into claim_contexts(code,name_ar,base_encounter_type,display_order) values (?,?,?,?)",
                "HOME_CARE", "رعاية منزلية", "INPATIENT", 30);
        jdbc.update("""
                insert into claim_context_source_aliases
                  (source_alias,normalized_alias,claim_context_code,requires_review,active)
                values (?,?,?,?,true)
                """, "رعاية منزلية", "رعايه منزليه", "HOME_CARE", false);

        assertThat(resolver.resolve("رعاية منزلية", null)).contains(
                new ClaimContextSourceResolver.Resolution("HOME_CARE", null, false));
    }
}
