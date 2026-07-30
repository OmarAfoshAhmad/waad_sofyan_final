package com.waad.tba.modules.medicaldictionary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalDictionaryNormalizerTest {

    private final MedicalDictionaryNormalizer normalizer = new MedicalDictionaryNormalizer();

    @Test
    void normalize_handlesArabicVariantsAndSpacing() {
        assertThat(normalizer.normalize("أشعة   بالرنين المغناطيسي"))
                .isEqualTo("اشعه بالرنين المغناطيسي");
        assertThat(normalizer.normalize("إختبار CBC - دم"))
                .isEqualTo("اختبار cbc دم");
        assertThat(normalizer.normalize("نظّارة طبيّة"))
                .isEqualTo("نظاره طبيه");
    }
}
