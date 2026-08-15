package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberAutocompleteDto;
import com.waad.tba.modules.member.dto.MemberSearchDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.modules.member.security.MemberScopeFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    private static final int MIN_TEXT_SEARCH_LENGTH = 3;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final MemberRepository memberRepository;
    private final MemberQueryAccessPolicy queryAccessPolicy;

    /**
     * Main unified search method - auto-detects search type
     * 
     * @param query Search query (card number, name, or barcode)
     * @return List of matching members (single for exact match, multiple for fuzzy)
     */
    public List<MemberSearchDto> search(String query, Long employerId) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty search query received");
            return List.of();
        }

        AuthorizedMemberScope scope = queryAccessPolicy.requireListing(MemberOperation.SEARCH, employerId);

        String trimmedQuery = query.trim();
        log.info("Unified search initiated for query: {}, requestedEmployerId: {}", trimmedQuery, employerId);

        // Detect search type
        SearchType searchType = detectSearchType(trimmedQuery);
        log.debug("Detected search type: {}", searchType);

        // Execute appropriate search
        switch (searchType) {
            case BARCODE:
                return searchByBarcode(trimmedQuery, scope);

            case CARD_NUMBER:
                return searchByCardNumber(trimmedQuery, scope);

            case NAME_FUZZY:
                return searchByName(trimmedQuery, scope);

            default:
                log.error("Unknown search type: {}", searchType);
                return List.of();
        }
    }

    /**
     * Search by barcode (UUID) - exact match
     * Performance: <50ms (indexed unique constraint)
     */
    private List<MemberSearchDto> searchByBarcode(String barcode, AuthorizedMemberScope scope) {
        log.info("Executing barcode search for: {}", barcode);

        Member member = memberRepository.findByBarcode(barcode)
                .orElse(null);

        if (member == null) {
            log.warn("No member found with barcode: {}", barcode);
            return List.of();
        }
        
        requireMemberAccess(scope, member);
        
        MemberSearchDto dto = MemberSearchDto.fromMember(member, "BARCODE", null);

        log.info("Found member by barcode: {} (ID: {})", member.getFullName(), member.getId());
        return List.of(dto);
    }

    private List<MemberSearchDto> searchByCardNumber(String cardNumber, AuthorizedMemberScope scope) {
        log.debug("Executing card number search for: {}", cardNumber);

        // 1. Try exact match first (Priority 1)
        Optional<Member> exactMatch = memberRepository.findByCardNumberWithDetails(cardNumber);
        if (exactMatch.isPresent()) {
            Member m = exactMatch.get();
            requireMemberAccess(scope, m);
            return List.of(MemberSearchDto.fromMember(m, "CARD_NUMBER", 1.0));
        }

        // 2. Try ID exact match (Priority 2)
        if (cardNumber.matches("\\d+")) {
            try {
                Long id = Long.parseLong(cardNumber);
                Optional<Member> idMatch = memberRepository.findById(id);
                if (idMatch.isPresent()) {
                    Member m = idMatch.get();
                    requireMemberAccess(scope, m);
                    return List.of(MemberSearchDto.fromMember(m, "DIRECT_ID", 1.0));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // 3. Fallback to partial search (Priority 3)
        // This allows searching for '2025' to find 'JFZ2025...'
        return searchByName(cardNumber, scope);
    }

    /**
     * Search by name - stable pattern match with eager loading
     */
    private List<MemberSearchDto> searchByName(String name, AuthorizedMemberScope scope) {
        log.info("Executing scoped name search for: {}", name);

        if (name == null || name.trim().length() < MIN_TEXT_SEARCH_LENGTH) {
            log.info("Skipping member text search shorter than {} characters", MIN_TEXT_SEARCH_LENGTH);
            return List.of();
        }

        String pattern = "%" + name.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Specification<Member> specification = (root, query, builder) -> builder.and(
                MemberScopeFilter.toPredicate(scope, root.get("employer").get("id"), builder),
                builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern),
                        builder.like(builder.lower(root.get("nationalNumber")), pattern),
                        builder.like(builder.lower(root.get("barcode")), pattern),
                        builder.like(builder.lower(root.get("cardNumber")), pattern)));

        List<Member> members = memberRepository.findAll(
                specification,
                PageRequest.of(0, MAX_SEARCH_RESULTS, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();

        if (members.isEmpty()) {
            log.warn("No members found for query: {}", name);
            return List.of();
        }

        // Convert entities to Search DTOs
        List<MemberSearchDto> results = members.stream()
                .map(member -> MemberSearchDto.fromMember(member, "NAME_PATTERN", 1.0))
                .toList();

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
                .map(member -> {
                    Long employerId = memberEmployerId(member);
                    queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerId);
                    return MemberSearchDto.fromMember(member, "DIRECT_ID", null);
                });
    }

    private void requireMemberAccess(AuthorizedMemberScope scope, Member member) {
        Long employerId = memberEmployerId(member);
        if (!scope.covers(employerId)) {
            // Use the policy's standard denial and audit shape rather than
            // disguising an out-of-scope exact match as "not found".
            queryAccessPolicy.requireMember(MemberOperation.SEARCH, employerId);
        }
    }

    private Long memberEmployerId(Member member) {
        return member.getEmployer() == null ? null : member.getEmployer().getId();
    }
}
