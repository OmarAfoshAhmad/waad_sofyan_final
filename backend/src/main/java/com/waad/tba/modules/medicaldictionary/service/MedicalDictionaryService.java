package com.waad.tba.modules.medicaldictionary.service;

import com.waad.tba.modules.medicaldictionary.dto.*;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionaryEntry;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySuggestion;
import com.waad.tba.modules.medicaldictionary.entity.MedicalDictionarySynonym;
import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationItem;
import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationSession;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListItemStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionaryEntryRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySuggestionRepository;
import com.waad.tba.modules.medicaldictionary.repository.MedicalDictionarySynonymRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationItemRepository;
import com.waad.tba.modules.medicaldictionary.repository.PriceListClassificationSessionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogWriteRequest;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.enums.ClassificationStatus;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalDictionaryService {

    private final MedicalDictionaryEntryRepository entryRepository;
    private final MedicalDictionarySynonymRepository synonymRepository;
    private final MedicalDictionarySuggestionRepository suggestionRepository;
    private final PriceListClassificationSessionRepository priceListSessionRepository;
    private final PriceListClassificationItemRepository priceListItemRepository;
    private final MedicalCategoryRepository medicalCategoryRepository;
    private final ProviderContractRepository providerContractRepository;
    private final ProviderContractPricingItemRepository providerContractPricingItemRepository;
    private final MedicalAuditLogService medicalAuditLogService;
    private final MedicalDictionaryNormalizer normalizer;
    private final V50MedicalClassificationEngine v50ClassificationEngine;
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
                .map(row -> classifyPriceListRow(row, request.getProviderName(), normalizedCounts))
                .toList();

        int highConfidence = (int) items.stream().filter(item -> "AUTO_APPROVED".equals(item.getStatus())).count();
        int needsReview = (int) items.stream().filter(item -> Set.of(
                "STRONG_SUGGESTION", "REVIEW_REQUIRED", "SPLIT_REQUIRED").contains(item.getStatus())).count();
        int unknown = (int) items.stream().filter(item -> Set.of(
                "QUARANTINED_NON_SERVICE", "EXCLUDED_COSMETIC").contains(item.getStatus())).count();
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

    @Transactional
    public PriceListSessionResponse savePriceListSession(PriceListSessionSaveRequest request) {
        var currentUser = authorizationService.getCurrentUser();
        Long actorId = currentUser == null ? null : currentUser.getId();

        String sourceFingerprint = calculatePriceListFingerprint(request);
        PriceListClassificationSession session = resolveSessionForSave(request, sourceFingerprint);

        session.setSessionName(request.getSessionName().trim());
        session.setOriginalFileName(blankToNull(request.getOriginalFileName()));
        session.setSourceFingerprint(sourceFingerprint);
        session.setProviderId(request.getProviderId());
        session.setProviderName(blankToNull(request.getProviderName()));
        session.setContractId(request.getContractId());
        session.setContractCode(blankToNull(request.getContractCode()));
        session.setNotes(blankToNull(request.getNotes()));
        session.setUpdatedBy(actorId);
        if (session.getId() == null) {
            session.setCreatedBy(actorId);
            session.setItems(new ArrayList<>());
        }

        session.getItems().clear();
        for (PriceListSessionSaveRequest.Item itemRequest : request.getItems()) {
            PriceListClassificationItem item = toPriceListItem(session, itemRequest);
            session.getItems().add(item);
        }
        recalculateSessionSummary(session);

        PriceListClassificationSession saved = priceListSessionRepository.save(session);
        return toPriceListSessionResponse(saved, true);
    }

    private PriceListClassificationSession resolveSessionForSave(PriceListSessionSaveRequest request, String sourceFingerprint) {
        if (request.getSessionId() != null) {
            return priceListSessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new IllegalArgumentException("جلسة تنظيم قائمة الأسعار غير موجودة"));
        }
        return priceListSessionRepository
                .findFirstBySourceFingerprintAndStatusNotOrderByUpdatedAtDesc(sourceFingerprint, PriceListSessionStatus.POSTED_TO_CONTRACT)
                .orElseGet(PriceListClassificationSession::new);
    }

    private String calculatePriceListFingerprint(PriceListSessionSaveRequest request) {
        StringBuilder signature = new StringBuilder();
        signature.append("file=").append(blankToNull(request.getOriginalFileName())).append('|')
                .append("providerId=").append(request.getProviderId()).append('|')
                .append("providerName=").append(normalizer.normalize(request.getProviderName())).append('|')
                .append("contractId=").append(request.getContractId()).append('|')
                .append("contractCode=").append(normalizer.normalize(request.getContractCode())).append('|');

        List<PriceListSessionSaveRequest.Item> items = request.getItems() == null ? List.of() : request.getItems();
        items.stream()
                .sorted(Comparator
                        .comparing((PriceListSessionSaveRequest.Item item) -> normalizer.normalize(item.getServiceCode()))
                        .thenComparing(item -> normalizer.normalize(item.getServiceName()))
                        .thenComparing(item -> String.valueOf(item.getMinPrice()))
                        .thenComparing(item -> String.valueOf(item.getMaxPrice())))
                .forEach(item -> signature
                        .append(normalizer.normalize(item.getServiceCode())).append('~')
                        .append(normalizer.normalize(item.getServiceName())).append('~')
                        .append(item.getMinPrice()).append('~')
                        .append(item.getMaxPrice()).append('~')
                        .append(item.getMedicalCategoryId()).append(';'));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signature.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("تعذر حساب بصمة قائمة الأسعار", e);
        }
    }

    @Transactional
    public Page<PriceListSessionSummaryResponse> listPriceListSessions(PriceListSessionStatus status, Pageable pageable) {
        Page<PriceListClassificationSession> page = status == null
                ? priceListSessionRepository.findAll(pageable)
                : priceListSessionRepository.findByStatus(status, pageable);
        return page.map(session -> toPriceListSessionSummaryResponse(syncPostedPriceLinks(session)));
    }

    @Transactional
    public PriceListSessionResponse getPriceListSession(Long sessionId) {
        PriceListClassificationSession session = priceListSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("جلسة تنظيم قائمة الأسعار غير موجودة"));
        return toPriceListSessionResponse(syncPostedPriceLinks(session), true);
    }

    @Transactional
    public void deletePriceListSession(Long sessionId) {
        PriceListClassificationSession session = priceListSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("القائمة المصنفة غير موجودة"));
        syncPostedPriceLinks(session);
        if (session.getStatus() == PriceListSessionStatus.POSTED_TO_CONTRACT || session.getPostedCount() != null && session.getPostedCount() > 0) {
            throw new IllegalArgumentException("لا يمكن حذف قائمة تم ترحيلها لعقد مقدم خدمة. يمكن مراجعة أثرها من العقد وسجل التدقيق.");
        }

        medicalAuditLogService.record(AuditLogWriteRequest.builder()
                .entityType(EntityType.PRICE_LIST)
                .entityId(String.valueOf(session.getId()))
                .action(AuditAction.DELETED)
                .source(AuditSource.USER)
                .reason("حذف قائمة أسعار مصنفة غير مرحلة")
                .beforeState(Map.of(
                        "sessionName", session.getSessionName(),
                        "originalFileName", session.getOriginalFileName(),
                        "status", session.getStatus() == null ? "" : session.getStatus().name(),
                        "totalRows", session.getTotalRows()))
                .build());
        priceListSessionRepository.delete(session);
    }

    @Transactional
    public PriceListSessionDiffResponse diffPriceListSessionWithContract(Long sessionId, PriceListSessionPostRequest request) {
        PriceListClassificationSession session = priceListSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("جلسة تنظيم قائمة الأسعار غير موجودة"));
        session = syncPostedPriceLinks(session);

        ProviderContract contract = providerContractRepository.findById(request.getContractId())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("عقد مقدم الخدمة غير موجود أو غير نشط"));

        List<PriceListClassificationItem> items = priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(session.getId());
        List<PriceListSessionDiffResponse.ItemDiff> diffs = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;
        int identicalCount = 0;
        int rejectedCount = 0;

        LocalDate effectiveFrom = resolveAndValidateEffectiveFrom(contract, request.getEffectiveFrom());
        for (PriceListClassificationItem item : items) {
            PriceListSessionDiffResponse.ItemDiff diff = buildPriceListDiffItem(
                    contract, item, request.isOnlyReviewedItems(), effectiveFrom);
            diffs.add(diff);
            switch (diff.getAction()) {
                case "CREATE" -> createCount++;
                case "UPDATE" -> updateCount++;
                case "IDENTICAL" -> identicalCount++;
                default -> rejectedCount++;
            }
        }

        return PriceListSessionDiffResponse.builder()
                .sessionId(session.getId())
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .total(items.size())
                .createCount(createCount)
                .updateCount(updateCount)
                .identicalCount(identicalCount)
                .rejectedCount(rejectedCount)
                .hasChanges(createCount > 0 || updateCount > 0)
                .items(diffs)
                .build();
    }

    @Transactional
    public PriceListSessionPostResponse postPriceListSessionToContract(Long sessionId, PriceListSessionPostRequest request) {
        PriceListClassificationSession session = priceListSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("جلسة تنظيم قائمة الأسعار غير موجودة"));
        session = syncPostedPriceLinks(session);

        ProviderContract contract = providerContractRepository.findById(request.getContractId())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("عقد مقدم الخدمة غير موجود أو غير نشط"));

        LocalDate effectiveFrom = resolveAndValidateEffectiveFrom(contract, request.getEffectiveFrom());

        var currentUser = authorizationService.getCurrentUser();
        Long actorId = currentUser == null ? null : currentUser.getId();
        List<PriceListClassificationItem> items = priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(sessionId);
        List<PriceListSessionPostResponse.ItemResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int superseded = 0;
        int skipped = 0;
        int rejected = 0;

        for (PriceListClassificationItem item : items) {
            String validationError = validatePostableItem(item, request.isOnlyReviewedItems());
            if (validationError != null) {
                rejected++;
                results.add(postResult(item, "REJECTED", validationError, null));
                continue;
            }

            MedicalCategory category = medicalCategoryRepository.findActiveById(item.getMedicalCategoryId())
                    .orElse(null);
            if (category == null) {
                rejected++;
                results.add(postResult(item, "REJECTED", "التصنيف الطبي غير موجود أو غير نشط", null));
                continue;
            }

            if (item.getPostedPricingItemId() != null || item.getStatus() == PriceListItemStatus.POSTED_TO_CONTRACT) {
                Optional<ProviderContractPricingItem> postedPricingItem = findOwnedActivePostedPricingItem(item, contract.getId());
                if (postedPricingItem.isPresent()) {
                    if (pricingItemMatches(postedPricingItem.get(), item, category)) {
                        skipped++;
                        results.add(postResult(item, "IDENTICAL", "لا يوجد تغيير؛ السعر والتصنيف مطابقان لقائمة التنظيم", postedPricingItem.get().getId()));
                        continue;
                    }
                    applyPricingItemValues(postedPricingItem.get(), contract, category, item, actorId);
                    providerContractPricingItemRepository.save(postedPricingItem.get());
                    updated++;
                    results.add(postResult(item, "UPDATED", "تم تحديث سعر العقد من نسخة قائمة التنظيم", postedPricingItem.get().getId()));
                    continue;
                }
                rejected++;
                results.add(postResult(item, "REJECTED", "البند مرتبط بسعر عقد غير موجود أو لا يخص العقد المختار", item.getPostedPricingItemId()));
                continue;
            }

            Optional<ProviderContractPricingItem> effectiveExisting = findExistingPrice(
                    contract.getId(), item, effectiveFrom);
            if (effectiveExisting.isPresent() && pricingItemMatches(effectiveExisting.get(), item, category)) {
                ProviderContractPricingItem current = effectiveExisting.get();
                item.setStatus(PriceListItemStatus.POSTED_TO_CONTRACT);
                item.setPostedPricingItemId(current.getId());
                item.setPostedAt(LocalDateTime.now());
                priceListItemRepository.save(item);
                skipped++;
                results.add(postResult(item, "IDENTICAL", "لا يوجد تغيير؛ السعر والتصنيف مطابقان لقائمة التنظيم", current.getId()));
                continue;
            }
            if (effectiveExisting.isPresent() && !request.isReplaceEffectivePrices()) {
                rejected++;
                results.add(postResult(item, "REJECTED", "يوجد سعر فعال لنفس كود/اسم الخدمة", effectiveExisting.get().getId()));
                continue;
            }

            if (effectiveExisting.isPresent()) {
                ProviderContractPricingItem existing = effectiveExisting.get();
                if (existing.getEffectiveFrom() == null || existing.getEffectiveFrom().isBefore(effectiveFrom)) {
                    // Price periods are half-open [from, to): the new version owns
                    // effectiveFrom and the previous version ends immediately before it.
                    existing.setEffectiveTo(effectiveFrom);
                    existing.setUpdatedBy(actorId == null ? null : actorId.toString());
                    providerContractPricingItemRepository.save(existing);
                    superseded++;
                } else if (existing.getEffectiveFrom().isEqual(effectiveFrom)) {
                    existing.setActive(false);
                    existing.setUpdatedBy(actorId == null ? null : actorId.toString());
                    providerContractPricingItemRepository.save(existing);
                    superseded++;
                }
            }

            ProviderContractPricingItem pricingItem = buildPricingItem(contract, category, item, effectiveFrom, actorId);
            ProviderContractPricingItem savedPricingItem = providerContractPricingItemRepository.save(pricingItem);

            item.setStatus(PriceListItemStatus.POSTED_TO_CONTRACT);
            item.setPostedPricingItemId(savedPricingItem.getId());
            item.setPostedAt(LocalDateTime.now());
            priceListItemRepository.save(item);

            created++;
            results.add(postResult(item, "CREATED", "تم إنشاء سعر عقد جديد", savedPricingItem.getId()));
        }

        session.setContractId(contract.getId());
        session.setContractCode(contract.getContractCode());
        session.setProviderId(contract.getProvider() == null ? session.getProviderId() : contract.getProvider().getId());
        session.setProviderName(contract.getProvider() == null ? session.getProviderName() : contract.getProvider().getName());
        recalculateSessionSummary(session);
        PriceListClassificationSession savedSession = priceListSessionRepository.save(session);

        medicalAuditLogService.record(AuditLogWriteRequest.builder()
                .entityType(EntityType.PRICE_LIST)
                .entityId(String.valueOf(savedSession.getId()))
                .action(AuditAction.IMPORTED)
                .source(AuditSource.USER)
                .reason("ترحيل جلسة تنظيم قائمة الأسعار إلى عقد مقدم خدمة")
                .afterState(Map.of(
                        "contractId", contract.getId(),
                        "contractCode", contract.getContractCode(),
                        "created", created,
                        "updated", updated,
                        "superseded", superseded,
                        "skipped", skipped,
                        "rejected", rejected,
                        "effectiveFrom", effectiveFrom.toString()))
                .build());

        return PriceListSessionPostResponse.builder()
                .sessionId(savedSession.getId())
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .created(created)
                .updated(updated)
                .superseded(superseded)
                .skipped(skipped)
                .rejected(rejected)
                .results(results)
                .session(toPriceListSessionResponse(savedSession, true))
                .build();
    }

    private LocalDate resolveAndValidateEffectiveFrom(ProviderContract contract, LocalDate requestedEffectiveFrom) {
        LocalDate effectiveFrom = requestedEffectiveFrom != null
                ? requestedEffectiveFrom
                : contract.getStartDate();
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("تاريخ بداية تطبيق قائمة الأسعار مطلوب لأن العقد لا يملك تاريخ بداية");
        }
        if (contract.getStartDate() != null && effectiveFrom.isBefore(contract.getStartDate())) {
            throw new IllegalArgumentException("تاريخ تطبيق القائمة لا يمكن أن يسبق تاريخ بداية العقد");
        }
        if (contract.getEndDate() != null && effectiveFrom.isAfter(contract.getEndDate())) {
            throw new IllegalArgumentException("تاريخ تطبيق القائمة لا يمكن أن يكون بعد نهاية العقد");
        }
        return effectiveFrom;
    }

    private String validatePostableItem(PriceListClassificationItem item, boolean onlyReviewedItems) {
        if (item.getProviderServiceName() == null || item.getProviderServiceName().isBlank()) {
            return "اسم خدمة المرفق مطلوب";
        }
        if (item.getMedicalCategoryId() == null) {
            return "لا يمكن الترحيل دون تصنيف طبي موحد";
        }
        if (resolveContractPrice(item) == null) {
            return "لا يمكن الترحيل دون سعر تعاقدي";
        }
        if (item.getStatus() != PriceListItemStatus.AUTO_APPROVED
                && item.getStatus() != PriceListItemStatus.MANUALLY_REVIEWED
                && item.getStatus() != PriceListItemStatus.READY_TO_POST) {
            return switch (item.getStatus()) {
                case STRONG_SUGGESTION -> "النتيجة اقتراح فقط وتحتاج اعتماداً بشرياً قبل الترحيل";
                case REVIEW_REQUIRED, NEEDS_REVIEW, UNKNOWN -> "البند يحتاج مراجعة قبل الترحيل";
                case SPLIT_REQUIRED -> "البند يجمع أكثر من خدمة ويجب تقسيمه قبل الترحيل";
                case QUARANTINED_NON_SERVICE -> "السطر ليس خدمة طبية ولا يجوز ترحيله إلى العقد";
                case EXCLUDED_COSMETIC -> "الخدمة تجميلية مستبعدة ولا يجوز ترحيلها دون قرار يدوي صريح";
                case HIGH_CONFIDENCE -> "هذا تصنيف قديم غير موثق بإصدار V50؛ أعد تصنيف السطر قبل الترحيل";
                default -> "حالة البند لا تسمح بالترحيل";
            };
        }
        if (item.getStatus() == PriceListItemStatus.AUTO_APPROVED
                && (item.getDictionaryReleaseId() == null || item.getDictionaryVersion() == null
                || item.getClassificationMethod() == null || item.getClassificationEvidenceId() == null)) {
            return "التصنيف الآلي لا يحمل دليل V50 كاملاً؛ أعد تصنيف السطر";
        }
        if (item.getStatus() == PriceListItemStatus.MANUALLY_REVIEWED
                && (item.getManualReviewNote() == null || item.getManualReviewNote().isBlank())) {
            return "سبب الاعتماد اليدوي مطلوب قبل الترحيل";
        }
        return null;
    }

    private Optional<ProviderContractPricingItem> findExistingPrice(Long contractId,
                                                                    PriceListClassificationItem item,
                                                                    LocalDate effectiveDate) {
        String serviceCode = blankToNull(item.getProviderServiceCode());
        if (serviceCode != null) {
            Optional<ProviderContractPricingItem> byCode = providerContractPricingItemRepository
                    .findEffectiveInContractByCode(contractId, serviceCode, effectiveDate);
            if (byCode.isPresent()) return byCode;
        }
        return providerContractPricingItemRepository.findEffectiveInContractByName(
                contractId, item.getProviderServiceName(), effectiveDate);
    }

    private Optional<ProviderContractPricingItem> findOwnedActivePostedPricingItem(PriceListClassificationItem item, Long contractId) {
        Long postedPricingItemId = item.getPostedPricingItemId();
        if (postedPricingItemId == null) return Optional.empty();
        return providerContractPricingItemRepository.findById(postedPricingItemId)
                .filter(price -> Boolean.TRUE.equals(price.getActive()))
                .filter(price -> price.getContract() != null && price.getContract().getId() != null)
                .filter(price -> price.getContract().getId().equals(contractId));
    }

    private PriceListSessionDiffResponse.ItemDiff buildPriceListDiffItem(ProviderContract contract,
                                                                         PriceListClassificationItem item,
                                                                         boolean onlyReviewedItems,
                                                                         LocalDate effectiveDate) {
        String validationError = validatePostableItem(item, onlyReviewedItems);
        if (validationError != null) {
            return priceListDiff(item, "REJECTED", validationError, null, null);
        }

        MedicalCategory category = medicalCategoryRepository.findActiveById(item.getMedicalCategoryId()).orElse(null);
        if (category == null) {
            return priceListDiff(item, "REJECTED", "التصنيف الطبي غير موجود أو غير نشط", null, null);
        }

        Optional<ProviderContractPricingItem> postedPricingItem = findOwnedActivePostedPricingItem(item, contract.getId());
        if (item.getPostedPricingItemId() != null && postedPricingItem.isEmpty()) {
            return priceListDiff(item, "REJECTED", "البند مرتبط بسعر عقد غير موجود أو لا يخص العقد المختار", null, category);
        }

        Optional<ProviderContractPricingItem> existing = postedPricingItem.isPresent()
                ? postedPricingItem
                : findExistingPrice(contract.getId(), item, effectiveDate);

        if (existing.isEmpty()) {
            return priceListDiff(item, "CREATE", "خدمة جديدة ستضاف إلى عقد مقدم الخدمة", null, category);
        }

        ProviderContractPricingItem current = existing.get();
        if (pricingItemMatches(current, item, category)) {
            return priceListDiff(item, "IDENTICAL", "لا يوجد تغيير؛ السعر والتصنيف مطابقان لقائمة التنظيم", current, category);
        }

        return priceListDiff(item, "UPDATE", "سيتم تحديث سعر أو تصنيف الخدمة في عقد مقدم الخدمة", current, category);
    }

    private boolean pricingItemMatches(ProviderContractPricingItem current,
                                       PriceListClassificationItem item,
                                       MedicalCategory category) {
        return sameAmount(current.getContractPrice(), resolveContractPrice(item))
                && sameAmount(current.getMaxContractPrice(), resolveMaxContractPrice(item))
                && sameText(current.getServiceName(), item.getProviderServiceName())
                && sameText(current.getServiceCode(), blankToNull(item.getProviderServiceCode()))
                && current.getMedicalCategory() != null
                && current.getMedicalCategory().getId() != null
                && current.getMedicalCategory().getId().equals(category.getId());
    }

    private PriceListSessionDiffResponse.ItemDiff priceListDiff(PriceListClassificationItem item,
                                                                String action,
                                                                String message,
                                                                ProviderContractPricingItem current,
                                                                MedicalCategory newCategory) {
        return PriceListSessionDiffResponse.ItemDiff.builder()
                .itemId(item.getId())
                .rowNumber(item.getRowNumber())
                .serviceCode(item.getProviderServiceCode())
                .serviceName(item.getProviderServiceName())
                .action(action)
                .message(message)
                .pricingItemId(current == null ? item.getPostedPricingItemId() : current.getId())
                .currentMinPrice(current == null ? null : current.getContractPrice())
                .currentMaxPrice(current == null ? null : current.getMaxContractPrice())
                .newMinPrice(resolveContractPrice(item))
                .newMaxPrice(resolveMaxContractPrice(item))
                .currentCategoryCode(current == null || current.getMedicalCategory() == null ? null : current.getMedicalCategory().getCode())
                .newCategoryCode(newCategory == null ? item.getMedicalCategoryCode() : newCategory.getCode())
                .build();
    }

    private ProviderContractPricingItem buildPricingItem(ProviderContract contract,
                                                         MedicalCategory category,
                                                         PriceListClassificationItem item,
                                                         LocalDate effectiveFrom,
                                                         Long actorId) {
        BigDecimal minPrice = resolveContractPrice(item);
        BigDecimal maxPrice = item.getMaxPrice() != null && item.getMaxPrice().compareTo(minPrice) >= 0
                ? item.getMaxPrice()
                : null;
        Integer confidence = item.getConfidence() == null ? 0 : item.getConfidence();

        return ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceName(item.getProviderServiceName())
                .serviceCode(blankToNull(item.getProviderServiceCode()))
                .medicalCategory(category)
                .categoryName(categoryName(category))
                .basePrice(minPrice)
                .contractPrice(minPrice)
                .maxContractPrice(maxPrice)
                .currency(contract.getCurrency() == null || contract.getCurrency().isBlank() ? "LYD" : contract.getCurrency())
                .effectiveFrom(effectiveFrom)
                .effectiveTo(null)
                .active(true)
                .classificationStatus(item.getStatus() == PriceListItemStatus.MANUALLY_REVIEWED
                        ? ClassificationStatus.MANUAL
                        : ClassificationStatus.AUTO)
                .confidenceLevel(confidence >= 85 ? ConfidenceLevel.HIGH : (confidence >= 60 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW))
                .classificationSource("MEDICAL_DICTIONARY_PRICE_LIST")
                .approvedBy(actorId)
                .approvedAt(LocalDateTime.now())
                .createdBy(actorId == null ? null : actorId.toString())
                .updatedBy(actorId == null ? null : actorId.toString())
                .notes(buildPricingNotes(item))
                .build();
    }

    private void applyPricingItemValues(ProviderContractPricingItem pricingItem,
                                        ProviderContract contract,
                                        MedicalCategory category,
                                        PriceListClassificationItem item,
                                        Long actorId) {
        BigDecimal minPrice = resolveContractPrice(item);
        BigDecimal maxPrice = item.getMaxPrice() != null && item.getMaxPrice().compareTo(minPrice) >= 0
                ? item.getMaxPrice()
                : null;
        Integer confidence = item.getConfidence() == null ? 0 : item.getConfidence();

        pricingItem.setServiceName(item.getProviderServiceName());
        pricingItem.setServiceCode(blankToNull(item.getProviderServiceCode()));
        pricingItem.setMedicalCategory(category);
        pricingItem.setCategoryName(categoryName(category));
        pricingItem.setBasePrice(minPrice);
        pricingItem.setContractPrice(minPrice);
        pricingItem.setMaxContractPrice(maxPrice);
        pricingItem.setCurrency(contract.getCurrency() == null || contract.getCurrency().isBlank() ? "LYD" : contract.getCurrency());
        pricingItem.setClassificationStatus(item.getStatus() == PriceListItemStatus.MANUALLY_REVIEWED
                ? ClassificationStatus.MANUAL
                : ClassificationStatus.AUTO);
        pricingItem.setConfidenceLevel(confidence >= 85 ? ConfidenceLevel.HIGH : (confidence >= 60 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW));
        pricingItem.setClassificationSource("MEDICAL_DICTIONARY_PRICE_LIST");
        pricingItem.setApprovedBy(actorId);
        pricingItem.setApprovedAt(LocalDateTime.now());
        pricingItem.setUpdatedBy(actorId == null ? null : actorId.toString());
        pricingItem.setNotes(buildPricingNotes(item));
    }

    private BigDecimal resolveContractPrice(PriceListClassificationItem item) {
        if (item.getMinPrice() != null) return item.getMinPrice();
        return item.getPrice();
    }

    private BigDecimal resolveMaxContractPrice(PriceListClassificationItem item) {
        BigDecimal minPrice = resolveContractPrice(item);
        if (minPrice == null) return null;
        return item.getMaxPrice() != null && item.getMaxPrice().compareTo(minPrice) >= 0 ? item.getMaxPrice() : null;
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return left.compareTo(right) == 0;
    }

    private boolean sameText(String left, String right) {
        String normalizedLeft = blankToNull(left);
        String normalizedRight = blankToNull(right);
        if (normalizedLeft == null && normalizedRight == null) return true;
        if (normalizedLeft == null || normalizedRight == null) return false;
        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private String buildPricingNotes(PriceListClassificationItem item) {
        List<String> notes = new ArrayList<>();
        notes.add("مصدر السعر: جلسة تنظيم قوائم الأسعار بالقاموس الطبي");
        if (item.getCanonicalName() != null && !item.getCanonicalName().isBlank()) {
            notes.add("الاسم الموحد: " + item.getCanonicalName());
        }
        if (item.getPriceLabel() != null && !item.getPriceLabel().isBlank()) {
            notes.add("السعر الأصلي: " + item.getPriceLabel());
        }
        if (Boolean.TRUE.equals(item.getMergedDuplicate())) {
            notes.add("مدمج من " + item.getMergedSourceCount() + " أسطر مصدر");
        }
        if (item.getMergeNotes() != null && !item.getMergeNotes().isBlank()) {
            notes.add(item.getMergeNotes());
        }
        if (item.getManualReviewNote() != null && !item.getManualReviewNote().isBlank()) {
            notes.add("ملاحظة المراجعة: " + item.getManualReviewNote());
        }
        return String.join(" | ", notes);
    }

    private PriceListSessionPostResponse.ItemResult postResult(PriceListClassificationItem item,
                                                               String result,
                                                               String message,
                                                               Long pricingItemId) {
        return PriceListSessionPostResponse.ItemResult.builder()
                .itemId(item.getId())
                .rowNumber(item.getRowNumber())
                .serviceCode(item.getProviderServiceCode())
                .serviceName(item.getProviderServiceName())
                .result(result)
                .message(message)
                .pricingItemId(pricingItemId)
                .build();
    }

    private PriceListClassificationItem toPriceListItem(PriceListClassificationSession session, PriceListSessionSaveRequest.Item request) {
        PriceListItemStatus status = resolveItemStatus(request.getStatus(), request.getMedicalCategoryId(), request.getCanonicalName());
        boolean manual = status == PriceListItemStatus.MANUALLY_REVIEWED;
        V50ClassificationResult classified = manual ? null : v50ClassificationEngine.classify(new V50ClassificationInput(
                request.getServiceName(), null, List.of(), request.getServiceCode(), request.getSourceSheet(),
                List.of(), request.getMergeNotes(), session.getProviderName()));
        MedicalCategory resolvedCategory = classified == null || classified.categoryCode().isBlank()
                ? null : medicalCategoryRepository.findActiveByCode(classified.categoryCode()).orElse(null);
        PriceListItemStatus persistedStatus = manual ? status : PriceListItemStatus.valueOf(classified.status().name());
        return PriceListClassificationItem.builder()
                .session(session)
                .rowNumber(request.getRowNumber())
                .sourceSheet(blankToNull(request.getSourceSheet()))
                .providerServiceCode(blankToNull(request.getServiceCode()))
                .providerServiceName(request.getServiceName().trim())
                .canonicalName(manual ? blankToNull(request.getCanonicalName()) : blankToNull(classified.unifiedAr().isBlank() ? classified.unifiedEn() : classified.unifiedAr()))
                .dictionaryEntryId(manual ? request.getDictionaryEntryId() : null)
                .medicalCategoryId(manual ? request.getMedicalCategoryId() : resolvedCategory == null ? null : resolvedCategory.getId())
                .medicalCategoryCode(manual ? blankToNull(request.getMedicalCategoryCode()) : blankToNull(classified.categoryCode()))
                .medicalCategoryName(manual ? blankToNull(request.getMedicalCategoryName()) : blankToNull(classified.categoryName()))
                .confidence(manual ? request.getConfidence() : classified.confidence().movePointRight(2).intValue())
                .dictionaryReleaseId(manual ? request.getDictionaryReleaseId() : classified.releaseId())
                .dictionaryVersion(manual ? blankToNull(request.getDictionaryVersion()) : classified.dictionaryVersion())
                .dictionaryConceptCode(manual ? blankToNull(request.getDictionaryConceptCode()) : blankToNull(classified.conceptCode()))
                .classificationMethod(manual ? "MANUAL_REVIEW" : classified.matchMethod())
                .classificationReason(manual ? blankToNull(request.getManualReviewNote()) : classified.reason())
                .classificationExceptionType(manual ? blankToNull(request.getClassificationExceptionType()) : blankToNull(classified.exceptionType()))
                .classificationEvidenceId(manual ? request.getClassificationEvidenceId() : classified.evidenceId())
                .classificationExcludePrecision(manual ? Boolean.TRUE : classified.excludeFromPrecision())
                .status(persistedStatus)
                .price(request.getPrice())
                .minPrice(request.getMinPrice() != null ? request.getMinPrice() : request.getPrice())
                .maxPrice(request.getMaxPrice())
                .priceLabel(blankToNull(request.getPriceLabel()))
                .duplicateName(Boolean.TRUE.equals(request.getDuplicateName()))
                .mergedDuplicate(Boolean.TRUE.equals(request.getMergedDuplicate()))
                .mergedSourceCount(request.getMergedSourceCount() == null || request.getMergedSourceCount() < 1 ? 1 : request.getMergedSourceCount())
                .mergeNotes(blankToNull(request.getMergeNotes()))
                .manualReviewNote(blankToNull(request.getManualReviewNote()))
                .build();
    }

    private PriceListItemStatus resolveItemStatus(String status, Long categoryId, String canonicalName) {
        if (status != null && !status.isBlank()) {
            try {
                return PriceListItemStatus.valueOf(status.trim());
            } catch (IllegalArgumentException ignored) {
                // fall through to legacy status mapping
            }
            return switch (status.trim()) {
                case "HIGH_CONFIDENCE" -> PriceListItemStatus.HIGH_CONFIDENCE;
                case "NEEDS_REVIEW" -> PriceListItemStatus.NEEDS_REVIEW;
                case "MANUAL", "MANUALLY_REVIEWED" -> PriceListItemStatus.MANUALLY_REVIEWED;
                default -> PriceListItemStatus.UNKNOWN;
            };
        }
        if (categoryId != null && canonicalName != null && !canonicalName.isBlank()) {
            return PriceListItemStatus.MANUALLY_REVIEWED;
        }
        return PriceListItemStatus.UNKNOWN;
    }

    private void recalculateSessionSummary(PriceListClassificationSession session) {
        List<PriceListClassificationItem> items = session.getItems() == null ? List.of() : session.getItems();
        int high = 0;
        int review = 0;
        int unknown = 0;
        int duplicate = 0;
        int ranged = 0;
        int posted = 0;

        for (PriceListClassificationItem item : items) {
            PriceListItemStatus status = item.getStatus();
            if (status == PriceListItemStatus.AUTO_APPROVED || status == PriceListItemStatus.HIGH_CONFIDENCE || status == PriceListItemStatus.MANUALLY_REVIEWED || status == PriceListItemStatus.READY_TO_POST) {
                high++;
            } else if (status == PriceListItemStatus.NEEDS_REVIEW || status == PriceListItemStatus.REVIEW_REQUIRED
                    || status == PriceListItemStatus.STRONG_SUGGESTION || status == PriceListItemStatus.SPLIT_REQUIRED) {
                review++;
            } else if (status == PriceListItemStatus.UNKNOWN || status == PriceListItemStatus.QUARANTINED_NON_SERVICE
                    || status == PriceListItemStatus.EXCLUDED_COSMETIC) {
                unknown++;
            }
            if (Boolean.TRUE.equals(item.getDuplicateName()) || Boolean.TRUE.equals(item.getMergedDuplicate())) duplicate++;
            if (item.getMinPrice() != null && item.getMaxPrice() != null && item.getMaxPrice().compareTo(item.getMinPrice()) > 0) ranged++;
            if (item.getPostedPricingItemId() != null || status == PriceListItemStatus.POSTED_TO_CONTRACT) posted++;
        }

        session.setTotalRows(items.size());
        session.setHighConfidenceCount(high);
        session.setNeedsReviewCount(review);
        session.setUnknownCount(unknown);
        session.setDuplicateCount(duplicate);
        session.setRangedPriceCount(ranged);
        session.setPostedCount(posted);
        if (posted > 0 && posted == items.size()) {
            session.setStatus(PriceListSessionStatus.POSTED_TO_CONTRACT);
        } else if (unknown > 0 || review > 0) {
            session.setStatus(PriceListSessionStatus.NEEDS_REVIEW);
        } else {
            session.setStatus(PriceListSessionStatus.READY_TO_POST);
        }
    }

    private PriceListClassificationSession syncPostedPriceLinks(PriceListClassificationSession session) {
        List<PriceListClassificationItem> items = priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(session.getId());
        boolean changed = false;

        for (PriceListClassificationItem item : items) {
            Long postedPricingItemId = item.getPostedPricingItemId();
            boolean markedAsPosted = item.getStatus() == PriceListItemStatus.POSTED_TO_CONTRACT;
            boolean postedPriceStillExists = postedPricingItemId != null && providerContractPricingItemRepository.existsByIdAndActiveTrue(postedPricingItemId);

            if ((postedPricingItemId != null || markedAsPosted) && !postedPriceStillExists) {
                item.setPostedPricingItemId(null);
                item.setPostedAt(null);
                if (item.getMedicalCategoryId() != null && resolveContractPrice(item) != null) {
                    item.setStatus(item.getConfidence() != null && item.getConfidence() >= 85
                            ? PriceListItemStatus.HIGH_CONFIDENCE
                            : PriceListItemStatus.MANUALLY_REVIEWED);
                } else if (item.getMedicalCategoryId() != null) {
                    item.setStatus(PriceListItemStatus.NEEDS_REVIEW);
                } else {
                    item.setStatus(PriceListItemStatus.UNKNOWN);
                }
                changed = true;
            }
        }

        if (changed) {
            session.setItems(items);
            recalculateSessionSummary(session);
            priceListItemRepository.saveAll(items);
            return priceListSessionRepository.save(session);
        }

        return session;
    }

    private PriceListSessionResponse toPriceListSessionResponse(PriceListClassificationSession session, boolean includeItems) {
        List<PriceListSessionResponse.Item> items = includeItems
                ? priceListItemRepository.findBySession_IdOrderByRowNumberAscIdAsc(session.getId()).stream().map(this::toPriceListItemResponse).toList()
                : List.of();
        return PriceListSessionResponse.builder()
                .id(session.getId())
                .sessionName(session.getSessionName())
                .originalFileName(session.getOriginalFileName())
                .providerId(session.getProviderId())
                .providerName(session.getProviderName())
                .contractId(session.getContractId())
                .contractCode(session.getContractCode())
                .status(session.getStatus())
                .summary(toPriceListSessionSummary(session))
                .notes(session.getNotes())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .items(items)
                .build();
    }

    private PriceListSessionSummaryResponse toPriceListSessionSummaryResponse(PriceListClassificationSession session) {
        return PriceListSessionSummaryResponse.builder()
                .id(session.getId())
                .sessionName(session.getSessionName())
                .originalFileName(session.getOriginalFileName())
                .providerId(session.getProviderId())
                .providerName(session.getProviderName())
                .contractId(session.getContractId())
                .contractCode(session.getContractCode())
                .status(session.getStatus())
                .totalRows(session.getTotalRows())
                .highConfidence(session.getHighConfidenceCount())
                .needsReview(session.getNeedsReviewCount())
                .unknown(session.getUnknownCount())
                .duplicates(session.getDuplicateCount())
                .rangedPrices(session.getRangedPriceCount())
                .posted(session.getPostedCount())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private PriceListSessionResponse.Summary toPriceListSessionSummary(PriceListClassificationSession session) {
        return PriceListSessionResponse.Summary.builder()
                .totalRows(session.getTotalRows())
                .highConfidence(session.getHighConfidenceCount())
                .needsReview(session.getNeedsReviewCount())
                .unknown(session.getUnknownCount())
                .duplicates(session.getDuplicateCount())
                .rangedPrices(session.getRangedPriceCount())
                .posted(session.getPostedCount())
                .build();
    }

    private PriceListSessionResponse.Item toPriceListItemResponse(PriceListClassificationItem item) {
        return PriceListSessionResponse.Item.builder()
                .id(item.getId())
                .rowNumber(item.getRowNumber())
                .sourceSheet(item.getSourceSheet())
                .serviceCode(item.getProviderServiceCode())
                .serviceName(item.getProviderServiceName())
                .canonicalName(item.getCanonicalName())
                .dictionaryEntryId(item.getDictionaryEntryId())
                .medicalCategoryId(item.getMedicalCategoryId())
                .medicalCategoryCode(item.getMedicalCategoryCode())
                .medicalCategoryName(item.getMedicalCategoryName())
                .confidence(item.getConfidence())
                .dictionaryReleaseId(item.getDictionaryReleaseId())
                .dictionaryVersion(item.getDictionaryVersion())
                .dictionaryConceptCode(item.getDictionaryConceptCode())
                .classificationMethod(item.getClassificationMethod())
                .classificationReason(item.getClassificationReason())
                .classificationExceptionType(item.getClassificationExceptionType())
                .classificationEvidenceId(item.getClassificationEvidenceId())
                .classificationExcludePrecision(item.getClassificationExcludePrecision())
                .status(item.getStatus())
                .price(item.getPrice())
                .minPrice(item.getMinPrice())
                .maxPrice(item.getMaxPrice())
                .priceLabel(item.getPriceLabel())
                .duplicateName(item.getDuplicateName())
                .mergedDuplicate(item.getMergedDuplicate())
                .mergedSourceCount(item.getMergedSourceCount())
                .mergeNotes(item.getMergeNotes())
                .manualReviewNote(item.getManualReviewNote())
                .postedPricingItemId(item.getPostedPricingItemId())
                .postedAt(item.getPostedAt())
                .build();
    }

    private PriceListClassificationResponse.Item classifyPriceListRow(PriceListClassificationRequest.Row row,
                                                                       String requestProviderName,
                                                                       Map<String, Long> normalizedCounts) {
        String normalized = normalizer.normalize(row.getServiceName());
        V50ClassificationResult result = v50ClassificationEngine.classify(new V50ClassificationInput(
                row.getServiceName(), row.getSecondaryName(), row.getAlternateNames(), row.getServiceCode(),
                row.getSectionName(), row.getSectionNames(), row.getNotes(),
                row.getFacilityName() == null || row.getFacilityName().isBlank() ? requestProviderName : row.getFacilityName()));
        String status = result.status().name();
        MedicalCategory category = result.categoryCode().isBlank()
                ? null : medicalCategoryRepository.findActiveByCode(result.categoryCode()).orElse(null);
        MedicalDictionaryMatchResponse bestMatch = category == null ? null : MedicalDictionaryMatchResponse.builder()
                .entryId(null)
                .canonicalName(result.unifiedAr().isBlank() ? result.unifiedEn() : result.unifiedAr())
                .medicalCategoryId(category.getId())
                .medicalCategoryCode(category.getCode())
                .medicalCategoryName(categoryName(category))
                .matchedText(row.getServiceName())
                .matchType(result.matchMethod())
                .confidence(result.confidence().movePointRight(2).intValue())
                .build();

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
                .bestMatch(bestMatch)
                .matches(bestMatch == null ? List.of() : List.of(bestMatch))
                .duplicateName(normalizedCounts.getOrDefault(normalized, 0L) > 1)
                .dictionaryReleaseId(result.releaseId())
                .dictionaryVersion(result.dictionaryVersion())
                .conceptCode(result.conceptCode())
                .categoryCode(result.categoryCode())
                .categoryName(result.categoryName())
                .canonicalName(result.unifiedAr().isBlank() ? result.unifiedEn() : result.unifiedAr())
                .matchMethod(result.matchMethod())
                .confidenceValue(result.confidence())
                .reason(result.reason())
                .exceptionType(result.exceptionType())
                .excludeFromPrecision(result.excludeFromPrecision())
                .evidenceId(result.evidenceId())
                .postable(result.mayPostToContract())
                .build();
    }

    private String resolveClassificationStatus(MedicalDictionaryMatchResponse best) {
        if (best == null) return "UNKNOWN";
        if (best.getConfidence() != null && best.getConfidence() >= 85) return "HIGH_CONFIDENCE";
        return "NEEDS_REVIEW";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "AUTO_APPROVED" -> "معتمد آلياً بواسطة V50";
            case "STRONG_SUGGESTION" -> "اقتراح قوي يحتاج اعتماداً بشرياً";
            case "REVIEW_REQUIRED" -> "يحتاج مراجعة بشرية";
            case "SPLIT_REQUIRED" -> "يجب تقسيم السطر إلى خدمات";
            case "QUARANTINED_NON_SERVICE" -> "ليس خدمة طبية";
            case "EXCLUDED_COSMETIC" -> "خدمة تجميلية مستبعدة";
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

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

