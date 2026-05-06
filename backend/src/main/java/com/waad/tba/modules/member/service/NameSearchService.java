package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberAutocompleteDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Phase 2: Arabic Fuzzy Name Search + Autocomplete
 * Provides intelligent name-based search with typo tolerance and ranking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NameSearchService {

    private final MemberRepository memberRepository;

    /**
     * Search members by name with fuzzy matching and autocomplete
     * Uses pg_trgm for Arabic text similarity
     * Minimum 3 characters required for performance
     *
     * @param query Search query (Arabic or English text)
     * @return List of autocomplete suggestions, ranked by relevance
     */
    @Transactional(readOnly = true)
    public List<MemberAutocompleteDto> searchMembersByName(String query) {
        // Validate input
        if (query == null || query.trim().isEmpty()) {
            log.debug("Empty search query received");
            return new ArrayList<>();
        }

        String trimmedQuery = query.trim();

        // Enforce minimum length (performance optimization)
        if (trimmedQuery.length() < 3) {
            log.debug("Search query too short: {} (minimum 3 characters)", trimmedQuery);
            return new ArrayList<>();
        }

        log.debug("Searching members by name: {}", trimmedQuery);

        try {
            // Normalize Arabic text (remove diacritics, normalize characters)
            String normalizedQuery = normalizeArabicText(trimmedQuery);
    
            // Execute fuzzy search with pg_trgm similarity
            List<Object[]> results = memberRepository.searchByNameFuzzy(normalizedQuery);
    
            // Convert to DTO
            List<MemberAutocompleteDto> suggestions = results.stream()
                    .map(this::mapToAutocompleteDto)
                    .collect(Collectors.toList());
    
            log.debug("Found {} fuzzy autocomplete suggestions for query: {}", suggestions.size(), trimmedQuery);
            return suggestions;
        } catch (Exception e) {
            log.warn("Fuzzy search failed (possible missing pg_trgm extension), falling back to pattern search: {}", e.getMessage());
            // Fallback to simple pattern search
            return searchMembersByNamePattern(trimmedQuery);
        }
    }

    /**
     * Normalize Arabic text for better search results
     * Handles common variations in Arabic writing
     *
     * @param text Original text
     * @return Normalized text
     */
    private String normalizeArabicText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text;

        // Normalize Alef variations (أ، إ، آ → ا)
        normalized = normalized.replace('أ', 'ا');
        normalized = normalized.replace('إ', 'ا');
        normalized = normalized.replace('آ', 'ا');

        // Normalize Taa Marbouta (ة → ه)
        normalized = normalized.replace('ة', 'ه');

        // Normalize Yaa variations (ى → ي)
        normalized = normalized.replace('ى', 'ي');

        // Remove diacritics (harakat)
        // Arabic diacritics: ً ٌ ٍ َ ُ ِ ّ ْ
        normalized = normalized.replaceAll("[\u064B-\u0652]", "");

        // Trim whitespace
        normalized = normalized.trim();

        // Normalize multiple spaces to single space
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    /**
     * Map database result row to MemberAutocompleteDto
     *
     * @param row Database row [id, full_name, card_number, similarity]
     * @return MemberAutocompleteDto
     */
    private MemberAutocompleteDto mapToAutocompleteDto(Object[] row) {
        Long id = ((Number) row[0]).longValue();
        String fullName = (String) row[1];
        String cardNumber = (String) row[2];
        Double similarity = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

        return MemberAutocompleteDto.builder()
                .memberId(id)
                .fullName(fullName)
                .cardNumber(cardNumber)
                .similarity(similarity)
                .build();
    }

    /**
     * Search members by name pattern (simple LIKE search)
     * Fallback method for non-fuzzy searches
     *
     * @param query Search query
     * @return List of matching members
     */
    @Transactional(readOnly = true)
    public List<MemberAutocompleteDto> searchMembersByNamePattern(String query) {
        if (query == null || query.trim().isEmpty() || query.trim().length() < 3) {
            return new ArrayList<>();
        }

        String pattern = "%" + query.trim() + "%";
        List<Member> members = memberRepository.searchByNamePattern(pattern);

        return members.stream()
                .limit(10)
                .map(member -> MemberAutocompleteDto.builder()
                        .memberId(member.getId())
                        .fullName(member.getFullName())
                        .cardNumber(member.getCardNumber())
                        .similarity(1.0) // Pattern match = exact relevance
                        .build())
                .collect(Collectors.toList());
    }
}
