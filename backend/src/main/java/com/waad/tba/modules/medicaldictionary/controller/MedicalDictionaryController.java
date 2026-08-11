package com.waad.tba.modules.medicaldictionary.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaldictionary.dto.*;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import com.waad.tba.modules.medicaldictionary.service.MedicalDictionaryService;
import com.waad.tba.modules.medicaldictionary.service.V50DictionaryReleaseImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/medical-dictionary")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MedicalDictionaryController {

    private final MedicalDictionaryService service;
    private final V50DictionaryReleaseImportService v50ReleaseImportService;

    @PostMapping(value = "/releases/v50/import-and-activate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MedicalDictionaryReleaseResponse>> importAndActivateV50(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "تم التحقق من قاموس V50 وتفعيله بالكامل",
                v50ReleaseImportService.importAndActivate(file)));
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Page<MedicalDictionaryEntryResponse>>> searchEntries(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) DictionaryEntryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ResponseEntity.ok(ApiResponse.success(service.searchEntries(query, status, pageable)));
    }

    @PostMapping("/entries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionaryEntryResponse>> createEntry(@Valid @RequestBody MedicalDictionaryEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("تم إنشاء سجل القاموس الطبي", service.createEntry(request)));
    }

    @PostMapping("/entries/{entryId}/synonyms")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionarySynonymResponse>> addSynonym(
            @PathVariable Long entryId,
            @Valid @RequestBody MedicalDictionarySynonymRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("تمت إضافة المرادف", service.addSynonym(entryId, request)));
    }

    @GetMapping("/entries/{entryId}/synonyms")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Page<MedicalDictionarySynonymResponse>>> listSynonyms(
            @PathVariable Long entryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.ASC, "synonym"));
        return ResponseEntity.ok(ApiResponse.success(service.listSynonyms(entryId, pageable)));
    }

    @PatchMapping("/synonyms/{synonymId}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionarySynonymResponse>> toggleSynonym(@PathVariable Long synonymId) {
        return ResponseEntity.ok(ApiResponse.success("تم تحديث حالة المرادف", service.toggleSynonym(synonymId)));
    }

    @GetMapping("/synonyms/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Page<MedicalDictionarySynonymSearchResponse>>> searchSynonyms(
            @RequestParam String query,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "usageCount"));
        return ResponseEntity.ok(ApiResponse.success(service.searchSynonyms(query, activeOnly, pageable)));
    }

    @GetMapping("/match")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<MedicalDictionaryMatchResponse>>> match(@RequestParam String text) {
        return ResponseEntity.ok(ApiResponse.success(service.match(text)));
    }

    @PostMapping("/price-lists/classify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PriceListClassificationResponse>> classifyPriceList(
            @Valid @RequestBody PriceListClassificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.classifyPriceList(request)));
    }

    @GetMapping("/price-lists/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Page<PriceListSessionSummaryResponse>>> listPriceListSessions(
            @RequestParam(required = false) PriceListSessionStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 25), Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ResponseEntity.ok(ApiResponse.success(service.listPriceListSessions(status, query, pageable)));
    }

    @GetMapping("/price-lists/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PriceListSessionResponse>> getPriceListSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(service.getPriceListSession(sessionId)));
    }

    @DeleteMapping("/price-lists/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Void>> deletePriceListSession(@PathVariable Long sessionId) {
        service.deletePriceListSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("تم حذف القائمة المصنفة غير المرحلة بنجاح", null));
    }

    @PostMapping("/price-lists/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PriceListSessionResponse>> savePriceListSession(
            @Valid @RequestBody PriceListSessionSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("تم حفظ جلسة تنظيم قائمة الأسعار", service.savePriceListSession(request)));
    }

    @PostMapping("/price-lists/sessions/{sessionId}/post-to-contract")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PriceListSessionPostResponse>> postPriceListSessionToContract(
            @PathVariable Long sessionId,
            @Valid @RequestBody PriceListSessionPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "تم ترحيل قائمة الأسعار إلى عقد مقدم الخدمة",
                service.postPriceListSessionToContract(sessionId, request)));
    }

    @PostMapping("/price-lists/sessions/{sessionId}/diff-contract")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PriceListSessionDiffResponse>> diffPriceListSessionWithContract(
            @PathVariable Long sessionId,
            @Valid @RequestBody PriceListSessionPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "تم إنشاء تقرير فروقات قائمة الأسعار مع عقد مقدم الخدمة",
                service.diffPriceListSessionWithContract(sessionId, request)));
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<Page<MedicalDictionarySuggestionResponse>>> listSuggestions(
            @RequestParam(required = false) DictionarySuggestionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(service.listSuggestions(status, pageable)));
    }

    @PostMapping("/suggestions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<MedicalDictionarySuggestionResponse>> createSuggestion(@Valid @RequestBody MedicalDictionarySuggestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("تم تسجيل اقتراح تعلم للقاموس الطبي", service.createSuggestion(request)));
    }
    @PostMapping("/suggestions/{suggestionId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionarySuggestionResponse>> approveSuggestion(
            @PathVariable Long suggestionId,
            @Valid @RequestBody MedicalDictionarySuggestionReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("تم اعتماد اقتراح القاموس الطبي", service.approveSuggestion(suggestionId, request)));
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionarySuggestionResponse>> rejectSuggestion(
            @PathVariable Long suggestionId,
            @Valid @RequestBody MedicalDictionarySuggestionReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("تم رفض اقتراح القاموس الطبي", service.rejectSuggestion(suggestionId, request)));
    }
}

