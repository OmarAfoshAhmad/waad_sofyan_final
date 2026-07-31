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
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalDictionaryService {

    private final MedicalDictionaryEntryRepository entryRepository;
    private final MedicalDictionarySynonymRepository synonymRepository;
    private final MedicalDictionarySuggestionRepository suggestionRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final MedicalDictionaryNormalizer normalizer;
    private final AuthorizationService authorizationService;

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
        Page<MedicalDictionaryEntry> entries;
        if (q.isBlank() && status == null) {
            entries = entryRepository.findAll(pageable);
        } else if (q.isBlank()) {
            entries = entryRepository.findByStatus(status, pageable);
        } else if (status == null) {
            entries = entryRepository.searchByText(q, pageable);
        } else {
            entries = entryRepository.searchByTextAndStatus(q, status, pageable);
        }
        return entries.map(this::toEntrySummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<MedicalDictionarySynonymResponse> listSynonyms(Long entryId, Pageable pageable) {
        if (!entryRepository.existsById(entryId)) {
            throw new IllegalArgumentException("سجل القاموس غير موجود");
        }
        return synonymRepository.findByEntry_Id(entryId, pageable).map(this::toSynonymResponse);
    }

    @Transactional(readOnly = true)
    public Page<MedicalDictionarySynonymSearchResponse> searchSynonyms(String query, boolean activeOnly, Pageable pageable) {
        String q = normalizer.normalize(query);
        if (q.isBlank()) return Page.empty(pageable);
        return synonymRepository.searchSynonyms(q, activeOnly, pageable).map(this::toSynonymSearchResponse);
    }

    @Transactional(readOnly = true)
    public List<MedicalDictionaryMatchResponse> match(String text) {
        String q = normalizer.normalize(text);
        if (q.isBlank()) return List.of();

        List<MedicalDictionaryMatchResponse> synonymMatches = synonymRepository.searchActiveSynonyms(q).stream()
                .filter(s -> s.getEntry() != null && s.getEntry().getMedicalCategory() != null)
                .map(s -> MedicalDictionaryMatchResponse.builder()
                        .entryId(s.getEntry().getId())
                        .canonicalName(s.getEntry().getCanonicalName())
                        .medicalCategoryId(categoryId(s.getEntry().getMedicalCategory()))
                        .medicalCategoryCode(categoryCode(s.getEntry().getMedicalCategory()))
                        .medicalCategoryName(categoryName(s.getEntry().getMedicalCategory()))
                        .matchedText(s.getSynonym())
                        .matchType("SYNONYM")
                        .confidence(score(q, s.getNormalizedSynonym(), s.getEntry().getDefaultConfidence()))
                        .build())
                .toList();

        List<MedicalDictionaryMatchResponse> entryMatches = entryRepository.searchByTextAndStatus(q, DictionaryEntryStatus.APPROVED, Pageable.ofSize(20))
                .stream()
                .filter(e -> e.getMedicalCategory() != null)
                .map(e -> MedicalDictionaryMatchResponse.builder()
                        .entryId(e.getId())
                        .canonicalName(e.getCanonicalName())
                        .medicalCategoryId(categoryId(e.getMedicalCategory()))
                        .medicalCategoryCode(categoryCode(e.getMedicalCategory()))
                        .medicalCategoryName(categoryName(e.getMedicalCategory()))
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

    @Transactional(readOnly = true)
    public PriceListClassificationResponse classifyPriceList(PriceListClassificationRequest request) {
        List<PriceListClassificationRequest.Row> rows = request.getRows() == null ? List.of() : request.getRows();

        Map<String, Long> normalizedCounts = rows.stream()
                .map(row -> normalizer.normalize(row.getServiceName()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<PriceListClassificationResponse.Item> items = rows.stream()
                .map(row -> classifyPriceListRow(row, normalizedCounts))
                .toList();

        int highConfidence = (int) items.stream().filter(item -> "HIGH_CONFIDENCE".equals(item.getStatus())).count();
        int needsReview = (int) items.stream().filter(item -> "NEEDS_REVIEW".equals(item.getStatus())).count();
        int unknown = (int) items.stream().filter(item -> "UNKNOWN".equals(item.getStatus())).count();
        int duplicates = (int) items.stream().filter(PriceListClassificationResponse.Item::isDuplicateName).count();

        return PriceListClassificationResponse.builder()
                .summary(PriceListClassificationResponse.Summary.builder()
                        .total(items.size())
                        .highConfidence(highConfidence)
                        .needsReview(needsReview)
                        .unknown(unknown)
                        .duplicateNames(duplicates)
                        .build())
                .items(items)
                .build();
    }

    private PriceListClassificationResponse.Item classifyPriceListRow(PriceListClassificationRequest.Row row,
                                                                       Map<String, Long> normalizedCounts) {
        String normalized = normalizer.normalize(row.getServiceName());
        List<MedicalDictionaryMatchResponse> matches = match(row.getServiceName());
        MedicalDictionaryMatchResponse best = matches.isEmpty() ? null : matches.get(0);
        String status = resolveClassificationStatus(best);

        return PriceListClassificationResponse.Item.builder()
                .rowNumber(row.getRowNumber())
                .sourceSheet(row.getSourceSheet())
                .serviceCode(row.getServiceCode())
                .serviceName(row.getServiceName())
                .price(row.getPrice())
                .minPrice(row.getMinPrice() != null ? row.getMinPrice() : row.getPrice())
                .maxPrice(row.getMaxPrice() != null ? row.getMaxPrice() : (row.getMinPrice() != null ? row.getMinPrice() : row.getPrice()))
                .priceLabel(row.getPriceLabel())
                .status(status)
                .statusLabel(statusLabel(status))
                .bestMatch(best)
                .matches(matches)
                .duplicateName(normalizedCounts.getOrDefault(normalized, 0L) > 1)
                .build();
    }

    private String resolveClassificationStatus(MedicalDictionaryMatchResponse best) {
        if (best == null) return "UNKNOWN";
        if (best.getConfidence() != null && best.getConfidence() >= 85) return "HIGH_CONFIDENCE";
        return "NEEDS_REVIEW";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "HIGH_CONFIDENCE" -> "مطابق بثقة عالية";
            case "NEEDS_REVIEW" -> "يحتاج مراجعة";
            default -> "غير معروف";
        };
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
        var currentUser = authorizationService.getCurrentUser();
        if (synonym.isActive()) {
            synonym.setLifecycleStatus(synonym.getLockedAt() != null ? "LOCKED" : "REVIEWER_APPROVED");
            synonym.setDisabledBy(null);
            synonym.setDisabledAt(null);
        } else {
            synonym.setLifecycleStatus("DISABLED");
            synonym.setDisabledBy(currentUser == null ? null : currentUser.getId());
            synonym.setDisabledAt(LocalDateTime.now());
        }
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

        if (category != null) {
            var existingPending = suggestionRepository.findFirstByNormalizedOriginalTextAndSuggestedCategory_IdAndStatus(
                    normalized,
                    category.getId(),
                    DictionarySuggestionStatus.PENDING);
            if (existingPending.isPresent()) {
                MedicalDictionarySuggestion existing = existingPending.get();
                if (entry != null) existing.setSuggestedEntry(entry);
                existing.setSource(request.getSource());
                existing.setConfidence(maxConfidence(existing.getConfidence(), request.getConfidence()));
                existing.setSourceReference(request.getSourceReference());
                return toSuggestionResponse(suggestionRepository.save(existing));
            }
        }

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

    private Integer maxConfidence(Integer current, Integer incoming) {
        if (current == null) return incoming;
        if (incoming == null) return current;
        return Math.max(current, incoming);
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
            createSynonymIfMissing(targetEntry, suggestion.getOriginalText(), suggestion);
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
        Optional<MedicalDictionaryEntry> existingCanonical = entryRepository.findFirstByNormalizedCanonicalName(normalizedName);
        if (existingCanonical.isPresent()) {
            return existingCanonical.get();
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

    private void createSynonymIfMissing(MedicalDictionaryEntry entry, String synonymText, MedicalDictionarySuggestion suggestion) {
        String normalized = normalizer.normalize(synonymText);
        if (normalized.isBlank() || synonymRepository.existsByNormalizedSynonym(normalized)) return;
        var currentUser = authorizationService.getCurrentUser();
        MedicalDictionarySynonym synonym = MedicalDictionarySynonym.builder()
                .entry(entry)
                .synonym(synonymText.trim())
                .normalizedSynonym(normalized)
                .synonymType(com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType.COMMON)
                .language("ar")
                .active(true)
                .lifecycleStatus("REVIEWER_APPROVED")
                .learnedFromSource(suggestion == null || suggestion.getSource() == null ? "CLAIM_REVIEW" : suggestion.getSource().name())
                .sourceReference(suggestion == null ? null : suggestion.getSourceReference())
                .approvedBy(currentUser == null ? null : currentUser.getId())
                .approvedAt(LocalDateTime.now())
                .governanceNote("تعلم مراقب من تعديل/اعتماد مراجع")
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
        MedicalCategory category = entry.getMedicalCategory();
        return MedicalDictionaryEntryResponse.builder()
                .id(entry.getId())
                .canonicalName(entry.getCanonicalName())
                .normalizedCanonicalName(entry.getNormalizedCanonicalName())
                .medicalCategoryId(categoryId(category))
                .medicalCategoryCode(categoryCode(category))
                .medicalCategoryName(categoryName(category))
                .status(entry.getStatus())
                .defaultConfidence(entry.getDefaultConfidence())
                .notes(entry.getNotes())
                .synonymCount(entry.getSynonyms() == null ? 0 : entry.getSynonyms().size())
                .synonyms(entry.getSynonyms() == null ? List.of() : entry.getSynonyms().stream().map(this::toSynonymResponse).toList())
                .approvedAt(entry.getApprovedAt())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private MedicalDictionaryEntryResponse toEntrySummaryResponse(MedicalDictionaryEntry entry) {
        MedicalCategory category = entry.getMedicalCategory();
        return MedicalDictionaryEntryResponse.builder()
                .id(entry.getId())
                .canonicalName(entry.getCanonicalName())
                .normalizedCanonicalName(entry.getNormalizedCanonicalName())
                .medicalCategoryId(categoryId(category))
                .medicalCategoryCode(categoryCode(category))
                .medicalCategoryName(categoryName(category))
                .status(entry.getStatus())
                .defaultConfidence(entry.getDefaultConfidence())
                .notes(entry.getNotes())
                .synonymCount(synonymRepository.countByEntry_Id(entry.getId()))
                .synonyms(List.of())
                .approvedAt(entry.getApprovedAt())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private MedicalDictionarySynonymResponse toSynonymResponse(MedicalDictionarySynonym synonym) {
        return MedicalDictionarySynonymResponse.builder()
                .id(synonym.getId())
                .entryId(synonym.getEntry().getId())
                .synonym(synonym.getSynonym())
                .normalizedSynonym(synonym.getNormalizedSynonym())
                .synonymType(synonym.getSynonymType())
                .language(synonym.getLanguage())
                .active(synonym.isActive())
                .usageCount(synonym.getUsageCount())
                .lifecycleStatus(synonym.getLifecycleStatus())
                .learnedFromSource(synonym.getLearnedFromSource())
                .sourceReference(synonym.getSourceReference())
                .approvedBy(synonym.getApprovedBy())
                .approvedAt(synonym.getApprovedAt())
                .lockedBy(synonym.getLockedBy())
                .lockedAt(synonym.getLockedAt())
                .disabledBy(synonym.getDisabledBy())
                .disabledAt(synonym.getDisabledAt())
                .governanceNote(synonym.getGovernanceNote())
                .build();
    }

    private MedicalDictionarySynonymSearchResponse toSynonymSearchResponse(MedicalDictionarySynonym synonym) {
        MedicalDictionaryEntry entry = synonym.getEntry();
        MedicalCategory category = entry == null ? null : entry.getMedicalCategory();
        return MedicalDictionarySynonymSearchResponse.builder()
                .synonymId(synonym.getId())
                .synonym(synonym.getSynonym())
                .normalizedSynonym(synonym.getNormalizedSynonym())
                .synonymType(synonym.getSynonymType())
                .language(synonym.getLanguage())
                .active(synonym.isActive())
                .usageCount(synonym.getUsageCount())
                .entryId(entry == null ? null : entry.getId())
                .canonicalName(entry == null ? null : entry.getCanonicalName())
                .medicalCategoryId(categoryId(category))
                .medicalCategoryCode(categoryCode(category))
                .medicalCategoryName(categoryName(category))
                .lifecycleStatus(synonym.getLifecycleStatus())
                .learnedFromSource(synonym.getLearnedFromSource())
                .approvedBy(synonym.getApprovedBy())
                .approvedAt(synonym.getApprovedAt())
                .disabledBy(synonym.getDisabledBy())
                .disabledAt(synonym.getDisabledAt())
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
                .suggestedCategoryName(categoryName(category))
                .source(suggestion.getSource())
                .status(suggestion.getStatus())
                .confidence(suggestion.getConfidence())
                .sourceReference(suggestion.getSourceReference())
                .reviewNote(suggestion.getReviewNote())
                .createdAt(suggestion.getCreatedAt())
                .reviewedAt(suggestion.getReviewedAt())
                .build();
    }

    private Long categoryId(MedicalCategory category) {
        return category == null ? null : category.getId();
    }

    private String categoryCode(MedicalCategory category) {
        return category == null ? null : category.getCode();
    }

    private String categoryName(MedicalCategory category) {
        if (category == null) return "غير محدد";
        if (category.getName() != null && !category.getName().isBlank()) return category.getName();
        if (category.getNameAr() != null && !category.getNameAr().isBlank()) return category.getNameAr();
        if (category.getCode() != null && !category.getCode().isBlank()) return category.getCode();
        return "غير محدد";
    }
}

