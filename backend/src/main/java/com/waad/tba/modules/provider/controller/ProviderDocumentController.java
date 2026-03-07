package com.waad.tba.modules.provider.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.provider.dto.ProviderDocumentDto;
import com.waad.tba.modules.provider.service.ProviderDocumentService;
import com.waad.tba.security.ProviderContextGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Provider Documents Controller
 * 
 * Provides unified access to all provider documents across:
 * - Visits
 * - Pre-Authorizations
 * - Claims
 * 
 * SECURITY: Provider can only access their own documents (via JWT providerId)
 */
@RestController
@RequestMapping("/api/v1/provider/documents")
@RequiredArgsConstructor
@Slf4j
public class ProviderDocumentController {

    private final ProviderDocumentService documentService;
    private final ProviderContextGuard providerContextGuard;

    /**
     * Get all documents for the authenticated provider
     * 
     * GET /api/provider/documents
     * 
     * @param referenceType Optional filter: VISIT | PRE_AUTH | CLAIM
     * @param status Optional filter: REQUIRED | UPLOADED | APPROVED | REJECTED
     * @param fromDate Optional filter: documents from this date
     * @param toDate Optional filter: documents until this date
     * @param page Page number (0-based)
     * @param size Page size
     * @return Page of ProviderDocumentDto
     */
    @GetMapping
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Page<ProviderDocumentDto>>> getProviderDocuments(
            @RequestParam(name = "referenceType", required = false) String referenceType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        
        log.info("[PROVIDER-DOCS] Request: type={}, status={}, from={}, to={}, page={}, size={}",
                referenceType, status, fromDate, toDate, page, size);
        
        // Get provider ID from security context
        Long providerId = providerContextGuard.getRequiredProviderIdStrict();
        log.info("[PROVIDER-DOCS] Resolved providerId={} for user={}", 
                providerId, authentication.getName());
        
        Pageable pageable = PageRequest.of(page, size);
        
        Page<ProviderDocumentDto> documents = documentService.getProviderDocuments(
                providerId, referenceType, status, fromDate, toDate, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
                "تم جلب المستندات بنجاح",
                documents
        ));
    }

    /**
     * Get document statistics for the authenticated provider
     * 
     * GET /api/provider/documents/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<ProviderDocumentService.ProviderDocumentStats>> getDocumentStats(
            Authentication authentication) {
        
        Long providerId = providerContextGuard.getRequiredProviderIdStrict();
        log.info("[PROVIDER-DOCS] Stats request for providerId={}", providerId);
        
        var stats = documentService.getProviderDocumentStats(providerId);
        
        return ResponseEntity.ok(ApiResponse.success(
                "تم جلب الإحصائيات بنجاح",
                stats
        ));
    }

    /**
     * Get reference type options for filter dropdown
     * 
     * GET /api/provider/documents/reference-types
     */
    @GetMapping("/reference-types")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Object>> getReferenceTypes() {
        var types = new Object[] {
            new Object() {
                public final String value = ProviderDocumentDto.REF_TYPE_VISIT;
                public final String label = "زيارة";
            },
            new Object() {
                public final String value = ProviderDocumentDto.REF_TYPE_PRE_AUTH;
                public final String label = "موافقة مسبقة";
            },
            new Object() {
                public final String value = ProviderDocumentDto.REF_TYPE_CLAIM;
                public final String label = "مطالبة";
            }
        };
        
        return ResponseEntity.ok(ApiResponse.success("أنواع المراجع", types));
    }

    /**
     * Get status options for filter dropdown
     * 
     * GET /api/provider/documents/statuses
     */
    @GetMapping("/statuses")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Object>> getStatuses() {
        var statuses = new Object[] {
            new Object() {
                public final String value = ProviderDocumentDto.STATUS_REQUIRED;
                public final String label = "مطلوب";
                public final String color = "warning";
            },
            new Object() {
                public final String value = ProviderDocumentDto.STATUS_UPLOADED;
                public final String label = "مرفوع";
                public final String color = "info";
            },
            new Object() {
                public final String value = ProviderDocumentDto.STATUS_APPROVED;
                public final String label = "مقبول";
                public final String color = "success";
            },
            new Object() {
                public final String value = ProviderDocumentDto.STATUS_REJECTED;
                public final String label = "مرفوض";
                public final String color = "error";
            }
        };
        
        return ResponseEntity.ok(ApiResponse.success("حالات المستندات", statuses));
    }
}
