package com.waad.tba.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextNormalizerTest {

    @Test
    void normalizesArabicHamzaTaMarbutaAndWhitespace() {
        assertThat(SearchTextNormalizer.normalize("  آلشَّرِكَة   الليبية  "))
                .isEqualTo("شركه الليبيه");
    }

    @Test
    void normalizesEnglishCaseWithoutDroppingDigitsOrCodes() {
        assertThat(SearchTextNormalizer.normalize(" POL-2026-017 "))
                .isEqualTo("pol-2026-017");
    }

    @Test
    void nullBecomesEmptySearch() {
        assertThat(SearchTextNormalizer.normalize(null)).isEmpty();
    }
}
