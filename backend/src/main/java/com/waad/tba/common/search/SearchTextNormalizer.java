package com.waad.tba.common.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical text preparation for user-facing search.
 *
 * Keep this deterministic and side-effect free. The database migration defines
 * the matching waad_search_normalize(text) function so expensive normalization
 * can be backed by expression indexes instead of ad-hoc per-screen rules.
 */
public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ٱ', 'ا')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .replace('ة', 'ه')
                .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
                .replaceAll("\\s+", " ");

        if (normalized.startsWith("ال") && normalized.length() > 3) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }
}
