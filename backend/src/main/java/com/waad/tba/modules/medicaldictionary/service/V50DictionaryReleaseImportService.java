package com.waad.tba.modules.medicaldictionary.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.medicaldictionary.dto.MedicalDictionaryReleaseResponse;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class V50DictionaryReleaseImportService {

    static final String VERSION = "V50";
    static final String SEED_SHA256 = "DB8A70887FC16E8CD8BFADC1637F4E65D5977B84AA65F6A5C4A3507ACFA40E3D";
    static final int CATEGORY_COUNT = 46;
    static final int CONCEPT_COUNT = 20_399;
    static final int ALIAS_COUNT = 97_977;
    static final int EXCEPTION_COUNT = 600;
    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final V50MedicalTextNormalizer normalizer;

    @Transactional
    public MedicalDictionaryReleaseResponse importAndActivate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("ملف seed الخاص بقاموس V50 مطلوب");
        String sha = sha256(file);
        if (!SEED_SHA256.equalsIgnoreCase(sha)) {
            throw new IllegalArgumentException("بصمة ملف V50 غير مطابقة للمصدر المعتمد: " + sha);
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM medical_dictionary_releases WHERE version = ? OR source_sha256 = ?", Integer.class, VERSION, sha);
        if (existing != null && existing > 0) throw new IllegalStateException("إصدار V50 مستورد مسبقاً؛ لا يمكن إنشاء نسخة مكررة");

        Long actorId = authorizationService.getCurrentUser() == null ? null : authorizationService.getCurrentUser().getId();
        Long releaseId = jdbc.queryForObject("""
                INSERT INTO medical_dictionary_releases(version, source_filename, source_sha256, status, created_by)
                VALUES (?, ?, ?, 'STAGED', ?) RETURNING id
                """, Long.class, VERSION, safeFilename(file.getOriginalFilename()), sha, actorId);

        Counts counts = parseSeed(file, releaseId);
        assertExpected(counts);
        assertPersisted(releaseId, counts);

        jdbc.update("UPDATE medical_dictionary_releases SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        jdbc.update("""
                UPDATE medical_dictionary_releases
                   SET status = 'ACTIVE', category_count = ?, concept_count = ?, alias_count = ?, exception_count = ?,
                       validation_report = jsonb_build_object('foreignKeys', 0, 'countsMatch', true, 'sha256', ?),
                       validated_by = ?, activated_by = ?, validated_at = CURRENT_TIMESTAMP, activated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, counts.categories, counts.concepts, counts.aliases, counts.exceptions, sha, actorId, actorId, releaseId);

        LocalDateTime activatedAt = jdbc.queryForObject("SELECT activated_at FROM medical_dictionary_releases WHERE id = ?", LocalDateTime.class, releaseId);
        return MedicalDictionaryReleaseResponse.builder()
                .id(releaseId).version(VERSION).sourceFilename(safeFilename(file.getOriginalFilename()))
                .sourceSha256(sha).status("ACTIVE").categoryCount(counts.categories)
                .conceptCount(counts.concepts).aliasCount(counts.aliases).exceptionCount(counts.exceptions)
                .activatedAt(activatedAt).build();
    }

    private Counts parseSeed(MultipartFile file, Long releaseId) {
        Counts counts = new Counts();
        String parsedVersion = null;
        try (InputStream input = file.getInputStream(); JsonParser parser = objectMapper.getFactory().createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException("بنية ملف V50 غير صحيحة");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                if ("version".equals(field)) {
                    parsedVersion = parser.getValueAsString();
                } else if ("categories".equals(field)) {
                    counts.categories = readArray(parser, rows -> insertCategories(releaseId, rows));
                } else if ("core".equals(field)) {
                    counts.concepts = readArray(parser, rows -> insertConcepts(releaseId, rows));
                } else if ("aliases".equals(field)) {
                    counts.aliases = readArray(parser, rows -> insertAliases(releaseId, rows));
                } else if ("exceptions".equals(field)) {
                    counts.exceptions = readArray(parser, rows -> insertExceptions(releaseId, rows));
                } else {
                    parser.skipChildren();
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalArgumentException("تعذر قراءة ملف V50: " + e.getMessage(), e);
        }
        if (!VERSION.equals(parsedVersion)) throw new IllegalArgumentException("الإصدار داخل الملف ليس V50: " + parsedVersion);
        return counts;
    }

    @FunctionalInterface private interface BatchWriter { void write(List<Map<String, Object>> rows); }

    @SuppressWarnings("unchecked")
    private int readArray(JsonParser parser, BatchWriter writer) throws Exception {
        if (parser.currentToken() != JsonToken.START_ARRAY) throw new IllegalArgumentException("مصفوفة V50 غير صحيحة");
        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
        int count = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(parser.readValueAs(Map.class));
            count++;
            if (batch.size() == BATCH_SIZE) { writer.write(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) writer.write(batch);
        return count;
    }

    private void insertCategories(Long releaseId, List<Map<String, Object>> rows) {
        jdbc.batchUpdate("INSERT INTO medical_dictionary_release_categories(release_id, category_code, name_ar, active, sort_order) VALUES (?, ?, ?, ?, ?)",
                rows, rows.size(), (ps, r) -> { ps.setLong(1, releaseId); ps.setString(2, text(r, "Code")); ps.setString(3, text(r, "Name")); ps.setBoolean(4, !"INACTIVE".equalsIgnoreCase(text(r, "Status"))); ps.setInt(5, 0); });
    }

    private void insertConcepts(Long releaseId, List<Map<String, Object>> rows) {
        jdbc.batchUpdate("""
                INSERT INTO medical_dictionary_concepts_v2(release_id, concept_code, dictionary_type, name_ar, name_en,
                abbreviation, normalized_ar, normalized_en, token_key, specialty, procedure_type, category_code,
                parent_context, context_rule, confidence, production_status, auto_approved, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, rows.size(), (ps, r) -> {
            String ar = text(r, "Name_AR"), en = text(r, "Name_EN"), cat = text(r, "Legacy_CAT");
            ps.setLong(1, releaseId); ps.setString(2, text(r, "Atomic_Code")); ps.setString(3, text(r, "Group"));
            ps.setString(4, ar); ps.setString(5, en); ps.setString(6, ""); ps.setString(7, normalizer.normalize(ar));
            ps.setString(8, normalizer.normalize(en)); ps.setString(9, tokenKey(ar + " " + en)); ps.setString(10, text(r, "Group"));
            ps.setString(11, text(r, "Subtype")); ps.setString(12, cat); ps.setString(13, "CAT-SURGERY".equals(cat) ? "CONTEXT_REQUIRED" : "");
            ps.setString(14, text(r, "Context_Rule")); ps.setBigDecimal(15, BigDecimal.ONE); ps.setString(16, defaultText(r, "Production_Status", "ACTIVE"));
            ps.setBoolean(17, true); ps.setString(18, text(r, "Notes"));
        });
    }

    private void insertAliases(Long releaseId, List<Map<String, Object>> rows) {
        jdbc.batchUpdate("""
                INSERT INTO medical_dictionary_aliases_v2(release_id, alias_code, raw_name, translation_ar, normalized_name,
                token_key, facility_name, scope_key, provider_code_normalized, section_normalized, match_scope, match_priority,
                concept_code, mapping_level, category_code, specialty, procedure_type, parent_context, confidence,
                production_status, auto_approved, source, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, rows.size(), (ps, r) -> {
            String facility = text(r, "Provider_Name"), concept = nullable(r, "Atomic_Code"), cat = text(r, "Official_CAT");
            String raw = text(r, "Original_Service"), normalized = defaultText(r, "Normalized_Service", normalizer.normalize(raw));
            ps.setLong(1, releaseId); ps.setString(2, text(r, "Alias_ID")); ps.setString(3, raw); ps.setString(4, text(r, "Atomic_Name_AR"));
            ps.setString(5, normalized); ps.setString(6, tokenKey(normalized)); ps.setString(7, facility);
            ps.setString(8, facility.isBlank() ? "*" : normalizer.normalize(facility)); ps.setString(9, text(r, "Provider_Code_Normalized"));
            ps.setString(10, text(r, "Section_Normalized")); ps.setString(11, defaultText(r, "Match_Scope", facility.isBlank() ? "GLOBAL" : "PROVIDER_SERVICE"));
            ps.setInt(12, integer(r, "Match_Priority", 3)); ps.setString(13, concept); ps.setString(14, concept == null ? "CATEGORY_ONLY" : "MASTER_ID");
            ps.setString(15, cat); ps.setString(16, text(r, "Specialty")); ps.setString(17, text(r, "Subtype"));
            ps.setString(18, "CAT-SURGERY".equals(cat) ? "CONTEXT_REQUIRED" : ""); ps.setBigDecimal(19, decimal(r, "Confidence", new BigDecimal("0.98")));
            ps.setString(20, defaultText(r, "Status", "READY")); ps.setBoolean(21, "YES".equalsIgnoreCase(text(r, "Auto_Approve")));
            ps.setString(22, text(r, "Source")); ps.setString(23, text(r, "Notes"));
        });
    }

    private void insertExceptions(Long releaseId, List<Map<String, Object>> rows) {
        jdbc.batchUpdate("""
                INSERT INTO medical_dictionary_exceptions_v2(release_id, exception_code, facility_name, scope_key,
                provider_code_normalized, raw_name, normalized_name, exception_status, exception_type, routing_action,
                reason, exclude_from_precision, exclude_from_clean_denominator, source, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, rows.size(), (ps, r) -> {
            String facility = text(r, "Provider_Name"), raw = text(r, "Alias_Original");
            ps.setLong(1, releaseId); ps.setString(2, text(r, "Exception_ID")); ps.setString(3, facility);
            ps.setString(4, facility.isBlank() ? "*" : normalizer.normalize(facility)); ps.setString(5, text(r, "Provider_Code_Normalized"));
            ps.setString(6, raw); ps.setString(7, defaultText(r, "Alias_Normalized", normalizer.normalize(raw)));
            ps.setString(8, text(r, "Exception_Status")); ps.setString(9, text(r, "Exception_Type")); ps.setString(10, defaultText(r, "Routing_Action", "HUMAN_REVIEW"));
            ps.setString(11, defaultText(r, "Reason", "استثناء V50")); ps.setBoolean(12, yes(r, "Exclude_From_Precision", true));
            ps.setBoolean(13, yes(r, "Exclude_From_Clean_Denominator", false)); ps.setString(14, text(r, "Source")); ps.setBoolean(15, yes(r, "Active", true));
        });
    }

    private void assertExpected(Counts c) {
        if (c.categories != CATEGORY_COUNT || c.concepts != CONCEPT_COUNT || c.aliases != ALIAS_COUNT || c.exceptions != EXCEPTION_COUNT)
            throw new IllegalArgumentException("أعداد V50 غير مطابقة: " + c);
    }

    private void assertPersisted(Long id, Counts expected) {
        Counts actual = new Counts();
        actual.categories = count("medical_dictionary_release_categories", id);
        actual.concepts = count("medical_dictionary_concepts_v2", id);
        actual.aliases = count("medical_dictionary_aliases_v2", id);
        actual.exceptions = count("medical_dictionary_exceptions_v2", id);
        if (!actual.equals(expected)) throw new IllegalStateException("فشل تحقق V50 بعد الإدخال: " + actual);
    }

    private int count(String table, Long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE release_id = ?", Integer.class, id); }
    private String sha256(MultipartFile file) { try (InputStream in = file.getInputStream()) { MessageDigest d = MessageDigest.getInstance("SHA-256"); in.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), d)); return HexFormat.of().withUpperCase().formatHex(d.digest()); } catch (Exception e) { throw new IllegalArgumentException("تعذر حساب بصمة الملف", e); } }
    private String safeFilename(String name) { if (name == null) return "v50-seed.json"; return name.replace("\\", "/").substring(name.replace("\\", "/").lastIndexOf('/') + 1); }
    private String text(Map<String, Object> r, String k) { return String.valueOf(r.getOrDefault(k, "")).trim(); }
    private String nullable(Map<String, Object> r, String k) { String v = text(r, k); return v.isBlank() ? null : v; }
    private String defaultText(Map<String, Object> r, String k, String fallback) { String v = text(r, k); return v.isBlank() ? fallback : v; }
    private int integer(Map<String, Object> r, String k, int fallback) { try { return new BigDecimal(text(r, k)).intValueExact(); } catch (Exception ignored) { return fallback; } }
    private BigDecimal decimal(Map<String, Object> r, String k, BigDecimal fallback) { try { return new BigDecimal(text(r, k)); } catch (Exception ignored) { return fallback; } }
    private boolean yes(Map<String, Object> r, String k, boolean fallback) { String v = text(r, k); return v.isBlank() ? fallback : "YES".equalsIgnoreCase(v) || "TRUE".equalsIgnoreCase(v) || "1".equals(v); }
    private String tokenKey(String value) { return java.util.Arrays.stream(normalizer.normalize(value).split(" ")).filter(v -> v.length() > 1).distinct().sorted().reduce((a,b) -> a + " " + b).orElse(""); }

    private static final class Counts {
        int categories, concepts, aliases, exceptions;
        @Override public boolean equals(Object o) { return o instanceof Counts c && categories == c.categories && concepts == c.concepts && aliases == c.aliases && exceptions == c.exceptions; }
        @Override public int hashCode() { return java.util.Objects.hash(categories, concepts, aliases, exceptions); }
        @Override public String toString() { return "categories=" + categories + ", concepts=" + concepts + ", aliases=" + aliases + ", exceptions=" + exceptions; }
    }
}
