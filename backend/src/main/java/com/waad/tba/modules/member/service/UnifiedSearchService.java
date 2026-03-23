package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberAutocompleteDto;
import com.waad.tba.modules.member.dto.MemberSearchDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Unified Search Service - Phase 3: Barcode/QR Support
 * 
 * Provides intelligent member search with automatic query type detection:
 * 1. Card Number Search (Phase 1) - Numeric exact match with indexed lookup
 * 2. Fuzzy Name Search (Phase 2) - Arabic intelligent search with pg_trgm
 * 3. Barcode/QR Search (Phase 3) - UUID exact match for QR scanning
 * 
 * Search Type Detection Logic:
 * - UUID Pattern (8-4-4-4-12 format) → BARCODE search
 * - Numeric only → CARD_NUMBER search
 * - Text (Arabic/English) → NAME_FUZZY search
 * 
 * Performance Targets:
 * - Card Number: <100ms (B-tree index)
 * - Barcode: <50ms (unique constraint + index)
 * - Name: <150ms (GIN trigram index)
 * 
 * @author TBA System
 * @version 3.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnifiedSearchService {

    private final MemberRepository memberRepository;
    private final NameSearchService nameSearchService;

    /**
     * Main unified search method - auto-detects search type
     * 
     * @param query Search query (card number, name, or barcode)
     * @return List of matching members (single for exact match, multiple for fuzzy)
     */
    public List<MemberSearchDto> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty search query received");
            return List.of();
        }

        String trimmedQuery = query.trim();
        log.info("Unified search initiated for query: {}", trimmedQuery);

        // Detect search type
        SearchType searchType = detectSearchType(trimmedQuery);
        log.debug("Detected search type: {}", searchType);

        // Execute appropriate search
        switch (searchType) {
            case BARCODE:
                return searchByBarcode(trimmedQuery);

            case CARD_NUMBER:
                return searchByCardNumber(trimmedQuery);

            case NAME_FUZZY:
                return searchByName(trimmedQuery);

            default:
                log.error("Unknown search type: {}", searchType);
                return List.of();
        }
    }

    /**
     * Search by barcode (UUID) - exact match
     * Performance: <50ms (indexed unique constraint)
     */
    private List<MemberSearchDto> searchByBarcode(String barcode) {
        log.info("Executing barcode search for: {}", barcode);

        Member member = memberRepository.findByBarcode(barcode)
                .orElse(null);

        if (member == null) {
            log.warn("No member found with barcode: {}", barcode);
            return List.of();
        }
        MemberSearchDto dto = MemberSearchDto.fromMember(member, "BARCODE", null);

        log.info("Found member by barcode: {} (ID: {})", member.getFullName(), member.getId());
        return List.of(dto);
    }

    /**
     * Search by card number - exact match
     * Performance: <100ms (B-tree index from Phase 1)
     */
    private List<MemberSearchDto> searchByCardNumber(String cardNumber) {
        log.debug("Executing card number search");

        Member member = memberRepository.findByCardNumber(cardNumber)
                .orElse(null);

        if (member == null) {
            log.debug("No member found with provided card number");
            return List.of();
        }
        MemberSearchDto dto = MemberSearchDto.fromMember(member, "CARD_NUMBER", null);

        log.debug("Found member by card number (ID: {})", member.getId());
        return List.of(dto);
    }

    /**
     * Search by name - fuzzy match with Arabic support
     * Performance: <150ms (GIN trigram index from Phase 2)
     */
    private List<MemberSearchDto> searchByName(String name) {
        log.info("Executing fuzzy name search for: {}", name);

        // Minimum 3 characters required for fuzzy search
        if (name.length() < 3) {
            log.warn("Name search requires at least 3 characters, got: {}", name.length());
            return List.of();
        }

        // Use Phase 2 fuzzy search service
        List<MemberAutocompleteDto> autocompleteResults = nameSearchService.searchMembersByName(name);

        if (autocompleteResults.isEmpty()) {
            log.warn("No members found for name: {}", name);
            return List.of();
        }

        // Convert autocomplete DTOs to full search DTOs
        List<MemberSearchDto> results = autocompleteResults.stream()
                .map(auto -> {
                    // Fetch full member details
                    return memberRepository.findById(auto.getMemberId())
                            .map(member -> MemberSearchDto.fromMember(member, "NAME_FUZZY", auto.getSimilarity()))
                            .orElse(null);
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        log.info("Found {} members for name search: {}", results.size(), name);
        return results;
    }

    /**
     * Detect search type based on query pattern
     */
    private SearchType detectSearchType(String query) {
        // Check for UUID pattern (barcode)
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        if (isUUID(query)) {
            return SearchType.BARCODE;
        }

        // Check for numeric (card number)
        if (isNumeric(query)) {
            return SearchType.CARD_NUMBER;
        }

        // Default to name search (fuzzy)
        return SearchType.NAME_FUZZY;
    }

    /**
     * Check if string is a valid UUID
     */
    private boolean isUUID(String str) {
        // UUID regex pattern: 8-4-4-4-12 hexadecimal characters
        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return str.matches(uuidPattern);
    }

    /**
     * Check if string contains only digits
     */
    private boolean isNumeric(String str) {
        return str.matches("\\d+");
    }

    /**
     * Search type enumeration
     */
    private enum SearchType {
        BARCODE, // UUID exact match
        CARD_NUMBER, // Numeric exact match
        NAME_FUZZY // Arabic/English fuzzy match
    }

    /**
     * Get member by ID with full details
     * Used after search to get complete member info
     */
    public Optional<MemberSearchDto> getMemberById(Long id) {
        log.info("Fetching member by ID: {}", id);

        return memberRepository.findById(id)
                .map(member -> MemberSearchDto.fromMember(member, "DIRECT_ID", null));
    }
}
