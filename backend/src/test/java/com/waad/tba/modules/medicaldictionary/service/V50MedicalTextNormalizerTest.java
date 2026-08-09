package com.waad.tba.modules.medicaldictionary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V50MedicalTextNormalizerTest {

    private final V50MedicalTextNormalizer normalizer = new V50MedicalTextNormalizer();

    @Test
    void matchesV50ArabicAndPunctuationNormalization() {
        assertThat(normalizer.normalize("  أشِعّـة/سينية — للعين  "))
                .isEqualTo("اشعه سينيه للعين");
        assertThat(normalizer.normalize("قیصیریة + تخذير"))
                .isEqualTo("قيصيريه التخدير");
    }

    @Test
    void matchesV50KnownEnglishTypoCorrections() {
        assertThat(normalizer.normalize("Sugery & Anasthesia / Ingection"))
                .isEqualTo("surgery and anesthesia injection");
    }

    @Test
    void normalizationIsDeterministicAndNullSafe() {
        assertThat(normalizer.normalize(null)).isEmpty();
        String first = normalizer.normalize("CBC (Complete Blood Count)");
        assertThat(normalizer.normalize(first)).isEqualTo(first);
    }
}
