package com.waad.tba.modules.medicaldictionary.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.medicaldictionary.dto.*;
import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import com.waad.tba.modules.medicaldictionary.service.MedicalDictionaryService;
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

@RestController
@RequestMapping("/api/v1/medical-dictionary")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MedicalDictionaryController {

    private final MedicalDictionaryService service;

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

    @PatchMapping("/synonyms/{synonymId}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<MedicalDictionarySynonymResponse>> toggleSynonym(@PathVariable Long synonymId) {
        return ResponseEntity.ok(ApiResponse.success("تم تحديث حالة المرادف", service.toggleSynonym(synonymId)));
    }

    @GetMapping("/match")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<MedicalDictionaryMatchResponse>>> match(@RequestParam String text) {
        return ResponseEntity.ok(ApiResponse.success(service.match(text)));
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
}
