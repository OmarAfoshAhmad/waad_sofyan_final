package com.waad.tba.modules.medicaldictionary.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Exact Java counterpart of the V50 TypeScript normalizer. */
@Component
public class V50MedicalTextNormalizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "of", "and", "or", "with", "for", "in", "to", "a", "an",
            "على", "من", "في", "مع", "او", "إلى", "الى", "و", "عن", "لـ", "ل",
            "under", "including", "per", "day", "one");
    private static final Pattern PAREN_ABBREVIATION = Pattern.compile("\\(([A-Z][A-Z0-9+\\-/]{1,11})\\)");
    private static final Pattern WORD_ABBREVIATION = Pattern.compile("\\b([A-Z][A-Z0-9+\\-/]{1,9})\\b");
    private static final Set<String> IGNORED_ABBREVIATIONS = Set.of("AND", "WITH", "THE", "FOR", "UNDER", "PER", "ONE", "DAY");

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
                .replaceAll("(?i)\\b(?:conguntival|conjuctival)\\b", "conjunctival")
                .replaceAll("التخذير|تخذير", "التخدير")
                .replace("اشعة صينية", "اشعة سينية")
                .replace("منضار", "منظار")
                .replace("قيصيرية", "قيصرية");

        return text.replaceAll("[\\\"'`´“”‘’]", " ")
                .replaceAll("[\\\\/_|+(),:;.\\-–—\\[\\]{}%]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public Set<String> significantTokens(String value) {
        if (value == null) return Set.of();
        return Arrays.stream(normalize(value).split(" "))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String tokenSetKey(String value) {
        return significantTokens(value).stream().sorted().collect(Collectors.joining(" "));
    }

    public double tokenSimilarity(String first, String second) {
        Set<String> left = significantTokens(first);
        Set<String> right = significantTokens(second);
        if (left.isEmpty() || right.isEmpty()) return 0d;
        long intersection = left.stream().filter(right::contains).count();
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return (double) intersection / union.size();
    }

    public String extractAbbreviation(String... values) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String value : values) {
            String text = value == null ? "" : value;
            collect(PAREN_ABBREVIATION.matcher(text), candidates);
            Matcher words = WORD_ABBREVIATION.matcher(text);
            while (words.find()) {
                String candidate = words.group(1);
                if (!IGNORED_ABBREVIATIONS.contains(candidate)) candidates.add(candidate);
            }
        }
        return candidates.stream().max(Comparator.comparingInt(String::length)).orElse("");
    }

    private void collect(Matcher matcher, Set<String> target) {
        while (matcher.find()) target.add(matcher.group(1));
    }
}
