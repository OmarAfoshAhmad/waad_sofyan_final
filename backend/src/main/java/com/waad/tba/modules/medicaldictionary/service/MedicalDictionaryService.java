package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.*;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionaryEntry;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySuggestion;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySynonym;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionaryEntryRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySuggestionRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySynonymRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicalDictionaryService {

    private final MedicalDictionaryEntryRepository entryRepository;
    private final MedicalDictionarySynonymRepository synonymRepository;
    private final MedicalDictionarySuggestionRepository suggestionRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final MedicalDictionaryNormalizer normalizer;

    @Transactional
    public MedicalDictionaryEntryResponse createEntry(MedicalDictionaryEntryRequest request) {
        String normalizedName = normalizer.normalize(request.getCanonicalName());
        if (normalizedName.isBlank()) throw new IllegalArgumentException("اسم الخدمة الموحد مطلوب");
        if (entryRepository.existsByNormalizedCanonicalName(normalizedName)) {
            throw new IllegalArgumentException("يوجد اسم موحد بنفس القيمة بعد التطبيع");
        }

        MedicalCategory category = medicalCategoryRepository.findActiveById(request.getMedicalCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("التصنيف الطبي غير موجود أو غير نشط"));

        DictionaryEntryStatus status = request.getStatus() == null ? DictionaryEntryStatus.DRAFT : request.getStatus();
        MedicalDictionaryEntry entry = MedicalDictionaryEntry.builder()
                .canonicalName(request.getCanonicalName().trim())
                .normalizedCanonicalName(normalizedName)
                .medicalCategory(category)
                .status(status)
                .defaultConfidence(request.getDefaultConfidence() == null ? 80 : request.getDefaultConfidence())
                .notes(request.getNotes())
                .approvedAt(status == DictionaryEntryStatus.APPROVED ? LocalDateTime.now() : null)
                .build();
        return toEntryResponse(entryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public Page<MedicalDictionaryEntryResponse> searchEntries(String query, DictionaryEntryStatus status, Pageable pageable) {
        String q = normalizer.normalize(query);
        return entryRepository.search(q.isBlank() ? null : q, status, pageable).map(this::toEntryResponse);
    }

    @Transactional(readOnly = true)
    public List<MedicalDictionaryMatchResponse> match(String text) {
        String q = normalizer.normalize(text);
        if (q.isBlank()) return List.of();

        List<MedicalDictionaryMatchResponse> synonymMatches = synonymRepository.searchActiveSynonyms(q).stream()
                .map(s -> MedicalDictionaryMatchResponse.builder()
                        .entryId(s.getEntry().getId())
                        .canonicalName(s.getEntry().getCanonicalName())
                        .medicalCategoryId(s.getEntry().getMedicalCategory().getId())
                        .medicalCategoryCode(s.getEntry().getMedicalCategory().getCode())
                        .medicalCategoryName(s.getEntry().getMedicalCategory().getName())
                        .matchedText(s.getSynonym())
                        .matchType("SYNONYM")
                        .confidence(score(q, s.getNormalizedSynonym(), s.getEntry().getDefaultConfidence()))
                        .build())
                .toList();

        List<MedicalDictionaryMatchResponse> entryMatches = entryRepository.search(q, DictionaryEntryStatus.APPROVED, Pageable.ofSize(20))
                .stream()
                .map(e -> MedicalDictionaryMatchResponse.builder()
                        .entryId(e.getId())
                        .canonicalName(e.getCanonicalName())
                        .medicalCategoryId(e.getMedicalCategory().getId())
                        .medicalCategoryCode(e.getMedicalCategory().getCode())
                        .medicalCategoryName(e.getMedicalCategory().getName())
                        .matchedText(e.getCanonicalName())
                        .matchType("CANONICAL")
                        .confidence(score(q, e.getNormalizedCanonicalName(), e.getDefaultConfidence()))
                        .build())
                .toList();

        return java.util.stream.Stream.concat(synonymMatches.stream(), entryMatches.stream())
                .sorted(Comparator.comparing(MedicalDictionaryMatchResponse::getConfidence).reversed())
                .limit(10)
                .toList();
    }

    @Transactional
    public MedicalDictionarySynonymResponse addSynonym(Long entryId, MedicalDictionarySynonymRequest request) {
        MedicalDictionaryEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("سجل القاموس غير موجود"));
        String normalized = normalizer.normalize(request.getSynonym());
        if (normalized.isBlank()) throw new IllegalArgumentException("المرادف مطلوب");
        if (synonymRepository.existsByNormalizedSynonym(normalized)) {
            throw new IllegalArgumentException("هذا المرادف موجود مسبقاً بعد التطبيع");
        }
        MedicalDictionarySynonym synonym = MedicalDictionarySynonym.builder()
                .entry(entry)
                .synonym(request.getSynonym().trim())
                .normalizedSynonym(normalized)
                .synonymType(request.getSynonymType())
                .language(request.getLanguage() == null || request.getLanguage().isBlank() ? "ar" : request.getLanguage().trim())
                .active(true)
                .build();
        return toSynonymResponse(synonymRepository.save(synonym));
    }

    @Transactional
    public MedicalDictionarySynonymResponse toggleSynonym(Long synonymId) {
        MedicalDictionarySynonym synonym = synonymRepository.findById(synonymId)
                .orElseThrow(() -> new IllegalArgumentException("المرادف غير موجود"));
        synonym.setActive(!synonym.isActive());
        return toSynonymResponse(synonymRepository.save(synonym));
    }

    @Transactional
    public MedicalDictionarySuggestionResponse createSuggestion(MedicalDictionarySuggestionRequest request) {
        String normalized = normalizer.normalize(request.getOriginalText());
        if (normalized.isBlank()) throw new IllegalArgumentException("نص الاقتراح مطلوب");

        MedicalDictionaryEntry entry = request.getSuggestedEntryId() == null ? null : entryRepository.findById(request.getSuggestedEntryId())
                .orElseThrow(() -> new IllegalArgumentException("سجل القاموس المقترح غير موجود"));
        MedicalCategory category = request.getSuggestedCategoryId() == null ? null : medicalCategoryRepository.findActiveById(request.getSuggestedCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("التصنيف المقترح غير موجود أو غير نشط"));

        MedicalDictionarySuggestion suggestion = MedicalDictionarySuggestion.builder()
                .originalText(request.getOriginalText().trim())
                .normalizedOriginalText(normalized)
                .suggestedEntry(entry)
                .suggestedCategory(category)
                .source(request.getSource())
                .confidence(request.getConfidence())
                .sourceReference(request.getSourceReference())
                .build();
        return toSuggestionResponse(suggestionRepository.save(suggestion));
    }

    @Transactional(readOnly = true)
    public Page<MedicalDictionarySuggestionResponse> listSuggestions(DictionarySuggestionStatus status, Pageable pageable) {
        if (status == null) return suggestionRepository.findAll(pageable).map(this::toSuggestionResponse);
        return suggestionRepository.findByStatus(status, pageable).map(this::toSuggestionResponse);
    }

    @Transactional
    public MedicalDictionarySuggestionResponse approveSuggestion(Long suggestionId, MedicalDictionarySuggestionReviewRequest request) {
        MedicalDictionarySuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("اقتراح القاموس غير موجود"));
        if (suggestion.getStatus() != DictionarySuggestionStatus.PENDING) {
            throw new IllegalArgumentException("لا يمكن مراجعة اقتراح تمت معالجته سابقاً");
        }

        MedicalDictionaryEntry targetEntry = resolveTargetEntryForApproval(suggestion, request);
        if (request.isApproveAsSynonym()) {
            createSynonymIfMissing(targetEntry, suggestion.getOriginalText());
            suggestion.setStatus(DictionarySuggestionStatus.MERGED);
        } else {
            suggestion.setStatus(DictionarySuggestionStatus.APPROVED);
        }
        suggestion.setSuggestedEntry(targetEntry);
        suggestion.setSuggestedCategory(targetEntry.getMedicalCategory());
        suggestion.setReviewNote(request.getReviewNote());
        suggestion.setReviewedAt(LocalDateTime.now());
        return toSuggestionResponse(suggestionRepository.save(suggestion));
    }

    @Transactional
    public MedicalDictionarySuggestionResponse rejectSuggestion(Long suggestionId, MedicalDictionarySuggestionReviewRequest request) {
        MedicalDictionarySuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("اقتراح القاموس غير موجود"));
        if (suggestion.getStatus() != DictionarySuggestionStatus.PENDING) {
            throw new IllegalArgumentException("لا يمكن مراجعة اقتراح تمت معالجته سابقاً");
        }
        suggestion.setStatus(DictionarySuggestionStatus.REJECTED);
        suggestion.setReviewNote(request.getReviewNote());
        suggestion.setReviewedAt(LocalDateTime.now());
        return toSuggestionResponse(suggestionRepository.save(suggestion));
    }

    private MedicalDictionaryEntry resolveTargetEntryForApproval(MedicalDictionarySuggestion suggestion,
                                                                 MedicalDictionarySuggestionReviewRequest request) {
        if (request.getTargetEntryId() != null) {
            return entryRepository.findById(request.getTargetEntryId())
                    .orElseThrow(() -> new IllegalArgumentException("سجل القاموس المستهدف غير موجود"));
        }
        if (suggestion.getSuggestedEntry() != null) return suggestion.getSuggestedEntry();

        Long categoryId = request.getTargetCategoryId() != null ? request.getTargetCategoryId()
                : suggestion.getSuggestedCategory() == null ? null : suggestion.getSuggestedCategory().getId();
        if (categoryId == null) throw new IllegalArgumentException("يجب تحديد التصنيف أو سجل قاموس مستهدف للاعتماد");

        MedicalCategory category = medicalCategoryRepository.findActiveById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("التصنيف المستهدف غير موجود أو غير نشط"));
        String canonicalName = request.getCanonicalName() == null || request.getCanonicalName().isBlank()
                ? suggestion.getOriginalText()
                : request.getCanonicalName();
        String normalizedName = normalizer.normalize(canonicalName);
        if (entryRepository.existsByNormalizedCanonicalName(normalizedName)) {
            throw new IllegalArgumentException("يوجد سجل قاموس بنفس الاسم الموحد؛ اعتمد الاقتراح كمرادف بدلاً من إنشاء سجل جديد");
        }

        MedicalDictionaryEntry entry = MedicalDictionaryEntry.builder()
                .canonicalName(canonicalName.trim())
                .normalizedCanonicalName(normalizedName)
                .medicalCategory(category)
                .status(DictionaryEntryStatus.APPROVED)
                .defaultConfidence(suggestion.getConfidence() == null ? 80 : suggestion.getConfidence())
                .approvedAt(LocalDateTime.now())
                .notes("تم إنشاؤه من اقتراح: " + suggestion.getSource())
                .build();
        return entryRepository.save(entry);
    }

    private void createSynonymIfMissing(MedicalDictionaryEntry entry, String synonymText) {
        String normalized = normalizer.normalize(synonymText);
        if (normalized.isBlank() || synonymRepository.existsByNormalizedSynonym(normalized)) return;
        MedicalDictionarySynonym synonym = MedicalDictionarySynonym.builder()
                .entry(entry)
                .synonym(synonymText.trim())
                .normalizedSynonym(normalized)
                .synonymType(com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType.COMMON)
                .language("ar")
                .active(true)
                .build();
        synonymRepository.save(synonym);
    }

    private Integer score(String query, String candidate, Integer fallback) {
        if (candidate == null || candidate.isBlank()) return 0;
        if (candidate.equals(query)) return 100;
        if (candidate.contains(query) || query.contains(candidate)) return Math.max(85, fallback == null ? 80 : fallback);
        return fallback == null ? 70 : Math.min(fallback, 75);
    }

    private MedicalDictionaryEntryResponse toEntryResponse(MedicalDictionaryEntry entry) {
        return MedicalDictionaryEntryResponse.builder()
                .id(entry.getId())
                .canonicalName(entry.getCanonicalName())
                .normalizedCanonicalName(entry.getNormalizedCanonicalName())
                .medicalCategoryId(entry.getMedicalCategory().getId())
                .medicalCategoryCode(entry.getMedicalCategory().getCode())
                .medicalCategoryName(entry.getMedicalCategory().getName())
                .status(entry.getStatus())
                .defaultConfidence(entry.getDefaultConfidence())
                .notes(entry.getNotes())
                .synonyms(entry.getSynonyms().stream().map(this::toSynonymResponse).toList())
                .approvedAt(entry.getApprovedAt())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private MedicalDictionarySynonymResponse toSynonymResponse(MedicalDictionarySynonym synonym) {
        return MedicalDictionarySynonymResponse.builder()
                .id(synonym.getId())
                .synonym(synonym.getSynonym())
                .normalizedSynonym(synonym.getNormalizedSynonym())
                .synonymType(synonym.getSynonymType())
                .language(synonym.getLanguage())
                .active(synonym.isActive())
                .usageCount(synonym.getUsageCount())
                .build();
    }

    private MedicalDictionarySuggestionResponse toSuggestionResponse(MedicalDictionarySuggestion suggestion) {
        MedicalDictionaryEntry entry = suggestion.getSuggestedEntry();
        MedicalCategory category = suggestion.getSuggestedCategory();
        return MedicalDictionarySuggestionResponse.builder()
                .id(suggestion.getId())
                .originalText(suggestion.getOriginalText())
                .normalizedOriginalText(suggestion.getNormalizedOriginalText())
                .suggestedEntryId(entry == null ? null : entry.getId())
                .suggestedEntryName(entry == null ? null : entry.getCanonicalName())
                .suggestedCategoryId(category == null ? null : category.getId())
                .suggestedCategoryCode(category == null ? null : category.getCode())
                .suggestedCategoryName(category == null ? null : category.getName())
                .source(suggestion.getSource())
                .status(suggestion.getStatus())
                .confidence(suggestion.getConfidence())
                .sourceReference(suggestion.getSourceReference())
                .reviewNote(suggestion.getReviewNote())
                .createdAt(suggestion.getCreatedAt())
                .reviewedAt(suggestion.getReviewedAt())
                .build();
    }
}

