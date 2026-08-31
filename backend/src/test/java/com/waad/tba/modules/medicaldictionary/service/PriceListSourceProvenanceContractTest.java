package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.PriceListSessionResponse;
import com.waad.tba.modules.medicaldictionary.dto.PriceListSessionSaveRequest;
import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for the price-list provenance that must survive classification.
 *
 * <p>The provider's classification and the resolved encounter context are not
 * coverage authorities. They are evidence used to explain how the canonical
 * medical category was selected. Losing them makes review and audit unable to
 * distinguish source data from a system decision.</p>
 */
class PriceListSourceProvenanceContractTest {

    @Test
    void saveContractCarriesProviderClassificationSeparatelyFromCanonicalCategory() {
        assertThat(PriceListSessionSaveRequest.Item.class)
                .hasDeclaredFields("sourceClassification", "claimContextCode",
                        "medicalCategoryId", "medicalCategoryCode");
    }

    @Test
    void storedClassificationPreservesProviderClassificationAndResolvedEncounterContext() {
        assertThat(PriceListClassificationItem.class)
                .hasDeclaredFields("sourceClassification", "claimContextCode");
    }

    @Test
    void reviewResponseShowsSourceClassificationAndResolvedEncounterContext() {
        assertThat(PriceListSessionResponse.Item.class)
                .hasDeclaredFields("sourceClassification", "claimContextCode");
    }
}
