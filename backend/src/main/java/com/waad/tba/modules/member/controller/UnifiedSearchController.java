package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.MemberSearchDto;
import com.waad.tba.modules.member.service.UnifiedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Unified Search Controller - Phase 3: Barcode/QR Support
 * 
 * Provides a single endpoint for all member search types:
 * - Card Number (numeric) - Phase 1
 * - Name (fuzzy Arabic/English) - Phase 2
 * - Barcode/QR (UUID) - Phase 3
 * 
 * Auto-detects search type based on query pattern.
 * 
 * @author TBA System
 * @version 3.0
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Member Search", description = "Unified member search API - supports Card/Name/Barcode")
public class UnifiedSearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * Unified search endpoint - auto-detects search type
     * 
     * Query pattern detection:
     * - UUID (8-4-4-4-12) → Barcode search
     * - Numeric only → Card number search
     * - Text → Fuzzy name search (min 3 chars)
     * 
     * @param query Search query (card number, name, or barcode/QR UUID)
     * @return List of matching members (1 for exact match, multiple for fuzzy)
     */
    @GetMapping("/unified-search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Unified member search",
        description = "Search members by card number, name (fuzzy), or barcode/QR. " +
                      "Auto-detects search type:\n" +
                      "- UUID pattern → Barcode search\n" +
                      "- Numeric → Card number search\n" +
                      "- Text → Fuzzy name search (min 3 chars)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Search completed successfully (may return empty list if no matches)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid query (empty or too short for name search)"
        )
    })
    public ResponseEntity<ApiResponse<List<MemberSearchDto>>> search(
            @Parameter(
                description = "Search query - card number (numeric), name (text, min 3 chars), or barcode (UUID)",
                example = "1234567890",
                required = true
            )
            @RequestParam(name = "query") String query
    ) {
        log.info("Unified search request received - query: {}", query);

        // Validate query
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty search query received");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<MemberSearchDto>>builder()
                            .status("error")
                            .message("Search query cannot be empty")
                            .data(List.of())
                            .timestamp(java.time.LocalDateTime.now())
                            .build());
        }

        // Execute unified search
        List<MemberSearchDto> results = unifiedSearchService.search(query.trim());

        // Build response
        String message = buildResponseMessage(results, query);
        
        return ResponseEntity.ok(
                ApiResponse.<List<MemberSearchDto>>builder()
                        .status("success")
                        .message(message)
                        .data(results)
                        .timestamp(java.time.LocalDateTime.now())
                        .build()
        );
    }

    /**
     * Get member by ID - for detailed view after search
     * 
     * @param id Member ID
     * @return Member details
     */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get member details by ID",
        description = "Retrieve complete member information after search selection"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Member found"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Member not found"
        )
    })
    public ResponseEntity<ApiResponse<MemberSearchDto>> getMemberDetails(
            @Parameter(description = "Member ID", example = "12345", required = true)
            @PathVariable Long id
    ) {
        log.info("Member details request - ID: {}", id);

        return unifiedSearchService.getMemberById(id)
                .map(member -> ResponseEntity.ok(
                        ApiResponse.<MemberSearchDto>builder()
                                .status("success")
                                .message("Member found")
                                .data(member)
                                .timestamp(java.time.LocalDateTime.now())
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.<MemberSearchDto>builder()
                                .status("error")
                                .message("Member not found with ID: " + id)
                                .data(null)
                                .timestamp(java.time.LocalDateTime.now())
                                .build()));
    }

    /**
     * Build appropriate response message based on results
     */
    private String buildResponseMessage(List<MemberSearchDto> results, String query) {
        if (results.isEmpty()) {
            return "No members found for query: " + query;
        }

        if (results.size() == 1) {
            String searchType = results.get(0).getSearchType();
            return switch (searchType) {
                case "BARCODE" -> "Member found by barcode";
                case "CARD_NUMBER" -> "Member found by card number";
                case "NAME_FUZZY" -> "Member found by name";
                default -> "Member found";
            };
        }

        return String.format("Found %d members matching query", results.size());
    }
}
