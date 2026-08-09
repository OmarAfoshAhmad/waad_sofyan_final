package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.V50ClassificationInput;
import com.waad.tba.modules.medicaldictionary.dto.V50ClassificationResult;
import com.waad.tba.modules.medicaldictionary.enums.V50ClassificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic V50 classifier. Decision order is part of the safety contract:
 * exception, contextual alias, exact concept, deterministic rule, fuzzy suggestion.
 * Fuzzy output can never be posted automatically.
 */
@Service
@RequiredArgsConstructor
public class V50MedicalClassificationEngine {

    private final JdbcTemplate jdbc;
    private final V50MedicalTextNormalizer normalizer;

    @Transactional(readOnly = true)
    public V50ClassificationResult classify(V50ClassificationInput input) {
        Release release = activeRelease();
        List<String> names = uniqueNames(input);
        String code = normalizer.normalize(input.serviceCode());
        String facility = normalizer.normalize(input.facilityName());
        String section = normalizer.normalize(String.join(" | ", unique(input.sectionName(), input.sectionNames())));

        for (String name : names) {
            V50ClassificationResult result = exception(release, normalizer.normalize(name), code, facility);
            if (result != null) return result;
        }
        for (String name : names) {
            V50ClassificationResult result = alias(release, normalizer.normalize(name), code, section, facility);
            if (result != null) return result;
        }
        for (String name : names) {
            V50ClassificationResult result = concept(release, name);
            if (result != null) return result;
        }

        V50ClassificationResult rule = deterministicRule(release, String.join(" | ", names), section, input.notes());
        if (rule != null) return rule;

        V50ClassificationResult fuzzy = fuzzySuggestion(release, names);
        if (fuzzy != null) return fuzzy;
        return empty(release, V50ClassificationStatus.REVIEW_REQUIRED, "NO_SAFE_MATCH", BigDecimal.ZERO,
                "لا توجد مطابقة أو قاعدة قطعية آمنة؛ تُرسل الخدمة للمراجعة البشرية.", "UNKNOWN_SERVICE", true, null);
    }

    private Release activeRelease() {
        List<Release> releases = jdbc.query("""
                SELECT id, version FROM medical_dictionary_releases
                WHERE status = 'ACTIVE' ORDER BY activated_at DESC, id DESC
                """, (rs, rowNum) -> new Release(rs.getLong("id"), rs.getString("version")));
        if (releases.size() != 1) {
            throw new IllegalStateException("يجب وجود إصدار قاموس طبي فعّال واحد بالضبط؛ الموجود: " + releases.size());
        }
        return releases.getFirst();
    }

    private V50ClassificationResult exception(Release release, String name, String code, String facility) {
        List<Evidence> rows = new ArrayList<>();
        if (!facility.isBlank() && !code.isBlank()) rows.addAll(exceptionRows(release.id, facility, code, name));
        if (rows.isEmpty() && !facility.isBlank()) rows.addAll(exceptionRows(release.id, facility, "", name));
        if (rows.isEmpty()) rows.addAll(exceptionRows(release.id, "*", "", name));
        if (rows.isEmpty()) return null;
        Evidence row = rows.getFirst();
        V50ClassificationStatus status = switch (row.status) {
            case "SPLIT_REQUIRED" -> V50ClassificationStatus.SPLIT_REQUIRED;
            case "NON_SERVICE_EXCEPTION" -> V50ClassificationStatus.QUARANTINED_NON_SERVICE;
            case "EXCLUDED_COSMETIC" -> V50ClassificationStatus.EXCLUDED_COSMETIC;
            default -> V50ClassificationStatus.REVIEW_REQUIRED;
        };
        return empty(release, status, "KNOWN_EXCEPTION", BigDecimal.ONE, row.reason, row.exceptionType, true, row.id);
    }

    private List<Evidence> exceptionRows(long releaseId, String scope, String code, String name) {
        return jdbc.query("""
                SELECT id, exception_status, exception_type, reason
                  FROM medical_dictionary_exceptions_v2
                 WHERE release_id = ? AND active = TRUE AND scope_key = ?
                   AND provider_code_normalized = ? AND normalized_name = ?
                 ORDER BY id LIMIT 2
                """, (rs, n) -> Evidence.exception(rs.getLong("id"), rs.getString("exception_status"),
                rs.getString("exception_type"), rs.getString("reason")), releaseId, scope, code, name);
    }

    private V50ClassificationResult alias(Release release, String name, String code, String section, String facility) {
        if (!facility.isBlank() && !code.isBlank()) {
            V50ClassificationResult result = uniqueAlias(release, aliasRows(release.id, """
                    a.scope_key = ? AND a.provider_code_normalized = ? AND a.normalized_name = ?
                    """, facility, code, name), "EXACT_PROVIDER_CODE_SERVICE",
                    "مطابقة V50: المرفق + كود الخدمة + اسم الخدمة.");
            if (result != null) return result;

            List<AliasEvidence> codeRows = aliasRows(release.id,
                    "a.scope_key = ? AND a.provider_code_normalized = ?", facility, code);
            long distinctNames = codeRows.stream().map(AliasEvidence::normalizedName).distinct().count();
            if (distinctNames == 1) {
                result = uniqueAlias(release, codeRows, "EXACT_PROVIDER_CODE_UNIQUE",
                        "كود المرفق فريد ومربوط بهدف V50 واحد.");
                if (result != null) return result;
            }
        }
        if (!facility.isBlank()) {
            List<AliasEvidence> rows = aliasRows(release.id, """
                    a.scope_key = ? AND a.normalized_name = ? AND a.provider_code_normalized = ''
                    AND (a.section_normalized = '' OR a.section_normalized = ?)
                    """, facility, name, section);
            V50ClassificationResult result = uniqueAlias(release, rows, "EXACT_PROVIDER_ALIAS",
                    "مطابقة حرفية مع مرادف V50 خاص بالمرفق.");
            if (result != null) return result;
        }
        V50ClassificationResult global = uniqueAlias(release,
                aliasRows(release.id, "a.scope_key = '*' AND a.normalized_name = ?", name),
                "EXACT_GLOBAL_ALIAS", "مطابقة حرفية مع مرادف عالمي معتمد في V50.");
        if (global != null) return global;

        V50ClassificationResult shared = uniqueAlias(release,
                aliasRows(release.id, "a.normalized_name = ?", name),
                "EXACT_SHARED_ALIAS", "الاسم الحرفي متفق على هدف واحد عبر المرافق.");
        if (shared != null) return shared;

        String tokenKey = normalizer.tokenSetKey(name);
        if (tokenKey.split(" ").length >= 3) {
            if (!facility.isBlank()) {
                V50ClassificationResult providerToken = uniqueAlias(release,
                        aliasRows(release.id, "a.scope_key = ? AND a.token_key = ?", facility, tokenKey),
                        "TOKEN_SET_PROVIDER_ALIAS", "تطابق الكلمات الجوهرية مع اختلاف ترتيبها داخل المرفق.");
                if (providerToken != null) return providerToken;
            }
            return uniqueAlias(release,
                    aliasRows(release.id, "a.scope_key = '*' AND a.token_key = ?", tokenKey),
                    "TOKEN_SET_GLOBAL_ALIAS", "تطابق الكلمات الجوهرية مع اختلاف ترتيبها.");
        }
        return null;
    }

    private List<AliasEvidence> aliasRows(long releaseId, String predicate, Object... args) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(releaseId);
        parameters.addAll(List.of(args));
        return jdbc.query("""
                SELECT a.id, a.normalized_name, a.concept_code, a.category_code, c.name_ar AS category_name,
                       COALESCE(m.name_ar, a.translation_ar, '') AS name_ar, COALESCE(m.name_en, '') AS name_en,
                       COALESCE(m.abbreviation, '') AS abbreviation, COALESCE(a.specialty, '') AS specialty,
                       COALESCE(a.procedure_type, '') AS procedure_type, COALESCE(a.parent_context, '') AS parent_context,
                       a.confidence
                  FROM medical_dictionary_aliases_v2 a
                  JOIN medical_dictionary_release_categories c
                    ON c.release_id = a.release_id AND c.category_code = a.category_code AND c.active = TRUE
                  LEFT JOIN medical_dictionary_concepts_v2 m
                    ON m.release_id = a.release_id AND m.concept_code = a.concept_code
                 WHERE a.release_id = ? AND a.auto_approved = TRUE AND a.production_status = 'READY' AND
                """ + predicate + " ORDER BY a.match_priority, a.confidence DESC, a.id LIMIT 100",
                (rs, n) -> new AliasEvidence(rs.getLong("id"), rs.getString("normalized_name"),
                        rs.getString("concept_code"), rs.getString("category_code"), rs.getString("category_name"),
                        rs.getString("name_ar"), rs.getString("name_en"), rs.getString("abbreviation"),
                        rs.getString("specialty"), rs.getString("procedure_type"), rs.getString("parent_context"),
                        rs.getBigDecimal("confidence")), parameters.toArray());
    }

    private V50ClassificationResult uniqueAlias(Release release, List<AliasEvidence> rows, String method, String reason) {
        if (rows.isEmpty()) return null;
        Set<String> targets = new LinkedHashSet<>();
        for (AliasEvidence row : rows) targets.add(row.categoryCode + "::" + (row.conceptCode == null ? "" : row.conceptCode));
        if (targets.size() != 1) return null;
        AliasEvidence row = rows.getFirst();
        return new V50ClassificationResult(release.id, release.version, row.conceptCode, row.categoryCode,
                row.categoryName, row.nameAr, row.nameEn, row.abbreviation, row.specialty, row.procedureType,
                "CAT-SURGERY".equals(row.categoryCode) ? "CONTEXT_REQUIRED" : row.parentContext,
                method, row.confidence, V50ClassificationStatus.AUTO_APPROVED, reason, "", false, row.id);
    }

    private V50ClassificationResult concept(Release release, String rawName) {
        String normalized = normalizer.normalize(rawName);
        String abbreviation = normalizer.extractAbbreviation(rawName).toUpperCase();
        String tokenKey = normalizer.tokenSetKey(normalized);
        List<ConceptEvidence> rows = jdbc.query("""
                SELECT m.id, m.concept_code, m.category_code, c.name_ar AS category_name, m.name_ar, m.name_en,
                       m.abbreviation, m.specialty, m.procedure_type, m.parent_context, m.confidence,
                       CASE WHEN m.normalized_ar = ? OR m.normalized_en = ? THEN 0
                            WHEN ? <> '' AND UPPER(m.abbreviation) = ? THEN 1 ELSE 2 END AS rank
                  FROM medical_dictionary_concepts_v2 m
                  JOIN medical_dictionary_release_categories c
                    ON c.release_id = m.release_id AND c.category_code = m.category_code AND c.active = TRUE
                 WHERE m.release_id = ? AND m.auto_approved = TRUE AND m.production_status = 'ACTIVE'
                   AND (m.normalized_ar = ? OR m.normalized_en = ?
                        OR (? <> '' AND UPPER(m.abbreviation) = ?) OR (? <> '' AND m.token_key = ?))
                 ORDER BY rank, m.id LIMIT 20
                """, (rs, n) -> new ConceptEvidence(rs.getLong("id"), rs.getString("concept_code"),
                rs.getString("category_code"), rs.getString("category_name"), rs.getString("name_ar"),
                rs.getString("name_en"), rs.getString("abbreviation"), rs.getString("specialty"),
                rs.getString("procedure_type"), rs.getString("parent_context"), rs.getBigDecimal("confidence"),
                rs.getInt("rank")), normalized, normalized, abbreviation, abbreviation, release.id,
                normalized, normalized, abbreviation, abbreviation, tokenKey, tokenKey);
        if (rows.isEmpty()) return null;
        int bestRank = rows.getFirst().rank;
        List<ConceptEvidence> best = rows.stream().filter(row -> row.rank == bestRank).toList();
        if (best.stream().map(row -> row.categoryCode + "::" + row.conceptCode).distinct().count() != 1) return null;
        ConceptEvidence row = best.getFirst();
        String method = bestRank == 1 ? "EXACT_ABBREVIATION" : bestRank == 2 ? "TOKEN_SET_CONCEPT" : "EXACT_MASTER";
        return new V50ClassificationResult(release.id, release.version, row.conceptCode, row.categoryCode,
                row.categoryName, blank(row.nameAr), blank(row.nameEn), blank(row.abbreviation), blank(row.specialty),
                blank(row.procedureType), "CAT-SURGERY".equals(row.categoryCode) ? "CONTEXT_REQUIRED" : blank(row.parentContext),
                method, row.confidence, V50ClassificationStatus.AUTO_APPROVED,
                "مطابقة مباشرة مع مفهوم WAC معتمد في V50.", "", false, row.id);
    }

    private V50ClassificationResult deterministicRule(Release release, String names, String section, String notes) {
        String raw = names == null ? "" : names;
        String name = normalizer.normalize(raw);
        String combined = name + " " + section + " " + normalizer.normalize(notes);
        if (name.length() < 2 || raw.trim().matches("^[.\\-_]+$"))
            return empty(release, V50ClassificationStatus.QUARANTINED_NON_SERVICE, "INVALID_ROW", BigDecimal.ONE,
                    "السطر لا يحتوي اسماً طبياً صالحاً.", "INVALID_ROW", true, null);
        if (has(name, "عموله", "commission", "فتح ملف", "medical file", "registration", "اجراء خروج", "وجبات", "مصاريف ادار", "extra film", "طباعه فيلم"))
            return empty(release, V50ClassificationStatus.QUARANTINED_NON_SERVICE, "NON_SERVICE_RULE", new BigDecimal("0.99"),
                    "صف إداري أو رسم ملحق أو غير خدمي.", "NON_SERVICE_OR_ADMIN", true, null);
        if (has(name, "ثقب الاذن", "ear piercing", "شفط الدهون", "liposuction", "شد البطن", "abdominoplasty", "زراعه الشعر", "ازاله الشعر بالليزر", "تجميل الانف", "rhinoplasty", "filler", "botox cosmetic", "تبييض الاسنان", "hollywood smile"))
            return empty(release, V50ClassificationStatus.EXCLUDED_COSMETIC, "COSMETIC_RULE", new BigDecimal("0.99"),
                    "خدمة تجميلية أو اختيارية صريحة.", "COSMETIC", true, null);
        if (has(combined, "bone marrow aspiration", "bone marrow biopsy", "trephine biopsy", "عينة من نخاع", "bronchoalveolar lavage"))
            return empty(release, V50ClassificationStatus.REVIEW_REQUIRED, "SPECIMEN_PROCEDURE_GATE", BigDecimal.ONE,
                    "قد يكون السطر إجراء أخذ عينة وليس تحليلاً صرفاً؛ يحتاج مراجعة.", "SPECIMEN_COLLECTION_OR_PROCEDURE", true, null);
        if (Set.of("على حسب الحاله", "حسب الحاله", "open code", "service", "procedure", "package", "joint", "local", "profile", "صوره", "p g", "u s", "s c", "drugs").contains(name))
            return empty(release, V50ClassificationStatus.REVIEW_REQUIRED, "GENERIC_LABEL_GATE", BigDecimal.ONE,
                    "المسمى عام أو اختصار غير كافٍ ولا يجوز تصنيفه من القسم وحده.", "INSUFFICIENT_DESCRIPTION", true, null);

        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("CAT-ROOM", new Rule("ROOM_RULE", "إقامة أو غرفة واضحة.", "اقامه", "غرفه", "سرير", "room", "ward"));
        rules.put("CAT-DRUG-GENERAL", new Rule("DRUG_RULE", "اسم دواء أو جرعة أو شكل صيدلاني واضح.", "tablet", "capsule", "syrup", "ointment", "دواء", " vial", " amp"));
        rules.put("CAT-DME", new Rule("DME_RULE", "جهاز أو معدة طبية مستقلة.", "ventilator", "cpap", "bipap", "تنفس صناعي", "air mattress", "infusion pump"));
        rules.put("CAT-LAB", new Rule("LAB_RULE", "تحليل أو فحص مخبري واضح.", "مختبر", "تحاليل", "laboratory", "cbc", "hba1c", "troponin", "culture", "تحليل", "urine", "stool", "pcr"));
        rules.put("CAT-IMG-ADV", new Rule("ADVANCED_IMAGING_RULE", "تصوير طبي متقدم.", "mri", "magnetic resonance", "ct scan", "computed tomography", "cbct", "رنين مغناطيسي", "اشعه مقطعيه"));
        rules.put("CAT-IMG-DIAG", new Rule("IMAGING_RULE", "تصوير تشخيصي واضح.", "x ray", "xray", "اشعه سينيه", "ultrasound", "سونار", "doppler", "mammography"));
        rules.put("CAT-DIALYSIS", new Rule("DIALYSIS_RULE", "غسيل كلوي صريح.", "dialysis", "hemodialysis", "غسيل كلوي", "غسيل الكلى"));
        rules.put("CAT-ONCOLOGY", new Rule("ONCOLOGY_RULE", "خدمة علاج أورام.", "chemotherapy", "كيماوي", "radiotherapy", "علاج اورام"));
        rules.put("CAT-PHYSIO", new Rule("PHYSIO_RULE", "خدمة علاج طبيعي.", "physiotherapy", "physical therapy", "علاج طبيعي", "tecar", "shock wave"));
        rules.put("CAT-SPEECH-THERAPY", new Rule("SPEECH_RULE", "جلسة علاج نطق أو تخاطب.", "speech therapy", "علاج نطق", "تخاطب"));
        rules.put("CAT-CARDIO-CHECKUP", new Rule("CARDIO_CHECKUP_RULE", "فحص أو تخطيط قلبي واضح.", "ecg", "ekg", "holter", "echo", "تخطيط قلب", "ايكو"));
        rules.put("CAT-AMBULANCE", new Rule("AMBULANCE_RULE", "خدمة إسعاف محلي.", "سياره اسعاف", "ambulance"));
        rules.put("CAT-PRACT-FEE", new Rule("PRACTITIONER_RULE", "رسوم ممارس أو استشارة.", "استشاره", "consultation", "مرور طبيب", "كشف طبي", "مراجعه طبيه"));
        rules.put("CAT-ANESTHESIA", new Rule("ANESTHESIA_RULE", "خدمة تخدير مستقلة.", "anesthesia", "anaesthesia", "تخدير"));
        rules.put("CAT-ENDOSCOPY", new Rule("ENDOSCOPY_RULE", "منظار تشخيصي أو علاجي.", "endoscopy", "gastroscopy", "colonoscopy", "bronchoscopy", "arthroscopy", "منظار"));
        rules.put("CAT-SURGERY", new Rule("SURGERY_RULE", "إجراء جراحي؛ يحدد OP/IP من السياق التشغيلي.", "surgery", "operation", "استئصال", "اصلاح", "ختان", "debridement", "excision", "resection", "repair", "hysterectomy", "appendectomy"));
        for (Map.Entry<String, Rule> entry : rules.entrySet()) {
            if (has(combined, entry.getValue().signals))
                return ruleResult(release, entry.getKey(), entry.getValue());
        }
        return null;
    }

    private V50ClassificationResult ruleResult(Release release, String category, Rule rule) {
        List<ConceptEvidence> concepts = jdbc.query("""
                SELECT m.id, m.concept_code, m.category_code, c.name_ar, m.name_ar, m.name_en, m.abbreviation,
                       m.specialty, m.procedure_type, m.parent_context, m.confidence, 0 AS rank
                  FROM medical_dictionary_concepts_v2 m
                  JOIN medical_dictionary_release_categories c ON c.release_id=m.release_id AND c.category_code=m.category_code
                 WHERE m.release_id=? AND m.category_code=? AND m.auto_approved=TRUE AND m.production_status='ACTIVE'
                 ORDER BY CASE WHEN m.concept_code LIKE '%-OTHER' OR m.concept_code LIKE '%-GENERAL' THEN 0 ELSE 1 END, m.id LIMIT 1
                """, (rs, n) -> new ConceptEvidence(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                rs.getBigDecimal(11), 0), release.id, category);
        if (concepts.isEmpty()) return null;
        ConceptEvidence c = concepts.getFirst();
        return new V50ClassificationResult(release.id, release.version, c.conceptCode, category, c.categoryName,
                blank(c.nameAr), blank(c.nameEn), blank(c.abbreviation), blank(c.specialty), blank(c.procedureType),
                "CAT-SURGERY".equals(category) ? "CONTEXT_REQUIRED" : blank(c.parentContext), rule.method,
                new BigDecimal("0.95"), V50ClassificationStatus.AUTO_APPROVED, rule.reason, "", false, c.id);
    }

    private V50ClassificationResult fuzzySuggestion(Release release, List<String> names) {
        Candidate winner = null;
        for (String name : names) {
            List<String> tokens = normalizer.significantTokens(name).stream().filter(t -> t.length() > 2).limit(3).toList();
            if (tokens.isEmpty()) continue;
            String pattern = "%" + tokens.getFirst() + "%";
            List<AliasEvidence> candidates = aliasRows(release.id, "a.normalized_name LIKE ?", pattern);
            List<Candidate> scored = candidates.stream()
                    .map(row -> new Candidate(row, normalizer.tokenSimilarity(name, row.normalizedName)))
                    .sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
            if (scored.isEmpty()) continue;
            Candidate best = scored.getFirst();
            double second = scored.size() > 1 ? scored.get(1).score : 0d;
            if (best.score < 0.62d || best.score - second < 0.08d) continue;
            if (winner == null || best.score > winner.score) winner = best;
        }
        if (winner == null) return null;
        AliasEvidence row = winner.row;
        BigDecimal confidence = BigDecimal.valueOf(Math.min(0.89d, 0.55d + winner.score * 0.4d)).setScale(4, RoundingMode.HALF_UP);
        return new V50ClassificationResult(release.id, release.version, row.conceptCode, row.categoryCode, row.categoryName,
                row.nameAr, row.nameEn, row.abbreviation, row.specialty, row.procedureType, row.parentContext,
                "FUZZY_SUGGESTION", confidence, V50ClassificationStatus.STRONG_SUGGESTION,
                "اقتراح قريب من «" + row.normalizedName + "»؛ لا يعتمد تلقائياً.", "FUZZY_CANDIDATE", true, row.id);
    }

    private List<String> uniqueNames(V50ClassificationInput input) {
        return unique(input.rawName(), combine(input.secondaryName(), input.alternateNames(), inlineEnglish(input.notes())));
    }

    private List<String> combine(String first, List<String> others, String last) {
        List<String> values = new ArrayList<>();
        values.add(first);
        values.addAll(others);
        values.add(last);
        return values;
    }

    private List<String> unique(String first, List<String> rest) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        Map<String, String> values = new LinkedHashMap<>();
        for (String value : all) {
            String text = value == null ? "" : value.trim();
            String normalized = normalizer.normalize(text);
            if (!normalized.isBlank()) values.putIfAbsent(normalized, text);
        }
        return List.copyOf(values.values());
    }

    private String inlineEnglish(String notes) {
        if (notes == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\|)\\s*EN\\s*:\\s*(.*?)(?:\\s*\\|\\s*المصدر\\s*:|$)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(notes);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private boolean has(String text, String... signals) {
        for (String signal : signals) if (text.contains(normalizer.normalize(signal))) return true;
        return false;
    }

    private V50ClassificationResult empty(Release release, V50ClassificationStatus status, String method,
                                           BigDecimal confidence, String reason, String type, boolean excluded, Long evidenceId) {
        return new V50ClassificationResult(release.id, release.version, "", "", "", "", "", "", "", "", "",
                method, confidence, status, reason, type, excluded, evidenceId);
    }

    private String blank(String value) { return value == null ? "" : value; }

    private record Release(long id, String version) {}
    private record Evidence(long id, String status, String exceptionType, String reason) {
        static Evidence exception(long id, String status, String type, String reason) { return new Evidence(id, status, type, reason); }
    }
    private record AliasEvidence(long id, String normalizedName, String conceptCode, String categoryCode,
                                 String categoryName, String nameAr, String nameEn, String abbreviation,
                                 String specialty, String procedureType, String parentContext, BigDecimal confidence) {}
    private record ConceptEvidence(long id, String conceptCode, String categoryCode, String categoryName,
                                   String nameAr, String nameEn, String abbreviation, String specialty,
                                   String procedureType, String parentContext, BigDecimal confidence, int rank) {}
    private record Candidate(AliasEvidence row, double score) {}
    private record Rule(String method, String reason, String... signals) {}
}
