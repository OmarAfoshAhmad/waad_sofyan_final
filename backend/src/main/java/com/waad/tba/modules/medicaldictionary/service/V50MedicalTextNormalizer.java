package com.waad.tba.modules.medicaldictionary.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/** Exact Java counterpart of the V50 TypeScript normalizer. */
@Component
public class V50MedicalTextNormalizer {

    public String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("_x0009_", " ")
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\u0617-\\u061A\\u064B-\\u0652\\u0670\\u06D6-\\u06ED]", "")
                .replaceAll("[أإآٱ]", "ا")
                .replaceAll("[ىی]", "ي")
                .replace('ئ', 'ي')
                .replace('ؤ', 'و')
                .replace('ة', 'ه')
                .replace('ک', 'ك')
                .replace("ـ", "")
                .replace("ﻻ", "لا")
                .replace("&", " and ");

        text = text
                .replaceAll("(?i)\\bana?sthesia\\b", "anesthesia")
                .replaceAll("(?i)\\bphysiothrapy\\b", "physiotherapy")
                .replaceAll("(?i)\\bdebrident\\b", "debridement")
                .replaceAll("(?i)\\b(?:sugery|surery)\\b", "surgery")
                .replaceAll("(?i)\\barthrograply\\b", "arthrography")
                .replaceAll("(?i)\\bingection\\b", "injection")
                .replaceAll("التخذير|تخذير", "التخدير")
                .replace("اشعة صينية", "اشعة سينية")
                .replace("منضار", "منظار")
                .replace("قيصيرية", "قيصرية");

        return text.replaceAll("[\\\"'`´“”‘’]", " ")
                .replaceAll("[\\\\/_|+(),:;.\\-–—\\[\\]{}%]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
