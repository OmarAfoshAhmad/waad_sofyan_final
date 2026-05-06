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

    private List<MemberSearchDto> searchByCardNumber(String cardNumber) {
        log.debug("Executing card number search for: {}", cardNumber);

        // 1. Try exact match first (Priority 1)
        Optional<Member> exactMatch = memberRepository.findByCardNumber(cardNumber);
        if (exactMatch.isPresent()) {
            return List.of(MemberSearchDto.fromMember(exactMatch.get(), "CARD_NUMBER", 1.0));
        }

        // 2. Try ID exact match (Priority 2)
        if (cardNumber.matches("\\d+")) {
            try {
                Long id = Long.parseLong(cardNumber);
                Optional<Member> idMatch = memberRepository.findById(id);
                if (idMatch.isPresent()) {
                    return List.of(MemberSearchDto.fromMember(idMatch.get(), "DIRECT_ID", 1.0));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // 3. Fallback to partial search (Priority 3)
        // This allows searching for '2025' to find 'JFZ2025...'
        return searchByName(cardNumber);
    }

    /**
     * Search by name - stable pattern match with eager loading
     */
    private List<MemberSearchDto> searchByName(String name) {
        log.info("Executing stable name search for: {}", name);

        // Use the robust search method with JOIN FETCH to prevent 500 errors (LazyInitialization)
        // This method also searches by civilId and cardNumber as fallback
        List<Member> members = memberRepository.search(name);

        if (members.isEmpty()) {
            log.warn("No members found for query: {}", name);
            return List.of();
        }

        // Convert entities to Search DTOs
        List<MemberSearchDto> results = members.stream()
                .limit(20) // Safety limit
                .map(member -> MemberSearchDto.fromMember(member, "NAME_PATTERN", 1.0))
                .collect(Collectors.toList());

        log.info("Found {} members for query: {}", results.size(), name);
        return results;
    }

    /**
     * Detect search type based on query pattern
     */
    private SearchType detectSearchType(String query) {
        // Check for UUID pattern (barcode)
        if (isUUID(query)) {
            return SearchType.BARCODE;
        }

        // Check for card number pattern (Numeric OR Alphanumeric format like WAB-2025-001)
        if (isCardNumberPattern(query)) {
            return SearchType.CARD_NUMBER;
        }

        // Default to name search (fuzzy)
        return SearchType.NAME_FUZZY;
    }

    /**
     * Check if string is a valid UUID
     */
    private boolean isUUID(String str) {
        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return str.matches(uuidPattern);
    }

    /**
     * Check if string matches a card number pattern
     * Supports:
     * 1. Purely numeric (legacy)
     * 2. Modern format: CODE-YEAR-NUMBER (e.g. WAB-2025-12345)
     */
    private boolean isCardNumberPattern(String str) {
        // Pure numeric
        if (str.matches("\\d+")) return true;
        
        // Modern alphanumeric format: [CHARS]-[4 DIGITS]-[ANYTHING]
        // Matches CardNumberGeneratorService.isValidCardNumberFormat pattern
        return str.matches("^[A-Z0-9]+-\\d{4}-.+");
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
