package com.waad.tba.modules.member.controller;

import com.waad.tba.modules.member.dto.MemberAutocompleteDto;
import com.waad.tba.modules.member.service.NameSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * Controller for Phase 2: Arabic Fuzzy Name Search + Autocomplete
 * Provides fast, intelligent name-based search for unified search experience
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Name Search", description = "Fuzzy name search and autocomplete endpoints")
public class NameSearchController {

    private final NameSearchService nameSearchService;

    /**
     * Search members by name with autocomplete suggestions
     * Phase 2: Fuzzy Arabic Name Search
     * Uses pg_trgm for typo-tolerant matching
     * Performance: < 150ms with GIN index
     *
     * @param query Search query (minimum 3 characters)
     * @return List of autocomplete suggestions, ranked by relevance
     */
    @Operation(
            summary = "Search members by name (autocomplete)",
            description = "Fuzzy search for member names with autocomplete suggestions. " +
                         "Supports Arabic text with typo tolerance. Minimum 3 characters required."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Autocomplete suggestions returned (may be empty)",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = MemberAutocompleteDto.class))
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid query (too short or empty)"
            )
    })
    @GetMapping("/autocomplete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    public ResponseEntity<List<MemberAutocompleteDto>> searchByName(
            @Parameter(
                    description = "Search query (Arabic or English name, minimum 3 characters)",
                    required = true,
                    example = "احمد"
            )
            @RequestParam(name = "query") String query
    ) {
        log.info("Name search request received: {}", query);

        // Validate minimum length
        if (query == null || query.trim().length() < 3) {
            log.warn("Search query too short: {}", query);
            return ResponseEntity.ok(List.of()); // Return empty list for autocomplete
        }

        // Perform fuzzy search
        List<MemberAutocompleteDto> suggestions = nameSearchService.searchMembersByName(query);

        log.info("Found {} autocomplete suggestions for: {}", suggestions.size(), query);

        return ResponseEntity.ok(suggestions);
    }
}
