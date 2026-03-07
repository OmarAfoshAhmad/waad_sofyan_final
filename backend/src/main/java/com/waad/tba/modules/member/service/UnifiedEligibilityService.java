package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.EligibilityResultDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.exception.InvalidEligibilityInputException;
import com.waad.tba.modules.member.exception.MemberNotFoundException;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Unified Eligibility Service
 * Deterministic eligibility verification with automatic input detection
 * 
 * Supported Methods:
 * - Card Number (digits only)
 * - Barcode (WAD-YYYY-NNNNNNNN format)
 * 
 * NOT Supported:
 * - Name search (removed by architectural decision)
 * - Fuzzy search
 * - Multiple results
 * 
 * @version 2.0 - Refactored for deterministic behavior
 * @since 2026-01-10
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UnifiedEligibilityService {

    private final MemberRepository memberRepository;

    // Barcode pattern: WAD-YYYY-NNNNNNNN
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^WAD-\\d{4}-\\d{8}$");
    
    // Card number pattern: digits only
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    /**
     * Check member eligibility with automatic input detection
     * 
     * Auto-Detection Rules:
     * 1. WAD-YYYY-NNNNNNNN → Barcode search
     * 2. Digits only → Card Number search
     * 3. Other → InvalidEligibilityInputException
     * 
     * @param query Search query (card number or barcode)
     * @return EligibilityResultDto with complete eligibility information
     * @throws InvalidEligibilityInputException if input format is invalid
     * @throws MemberNotFoundException if member not found
     */
    public EligibilityResultDto checkEligibility(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new InvalidEligibilityInputException("Query cannot be empty");
        }

        String trimmedQuery = query.trim();
        log.info("📥 [ELIGIBILITY-CHECK] Query received (length: {})", trimmedQuery.length());

        // Auto-detect input type
        if (BARCODE_PATTERN.matcher(trimmedQuery).matches()) {
            log.info("🔍 [BARCODE-DETECTED] Format: WAD-YYYY-NNNNNNNN");
            return checkByBarcode(trimmedQuery);
        } 
        else if (CARD_NUMBER_PATTERN.matcher(trimmedQuery).matches()) {
            log.info("🔍 [CARD-NUMBER-DETECTED] Format: Digits only");
            return checkByCardNumber(trimmedQuery);
        } 
        else {
            log.warn("⚠️ [INVALID-FORMAT] Query does not match Card Number or Barcode pattern");
            throw new InvalidEligibilityInputException(
                "Invalid input format. Expected: Card Number (digits) or Barcode (WAD-YYYY-NNNNNNNN)"
            );
        }
    }

    /**
     * Check eligibility by barcode (exact match)
     */
    private EligibilityResultDto checkByBarcode(String barcode) {
        log.debug("Searching by barcode: {}", barcode);

        Member member = memberRepository.findByBarcode(barcode)
                .orElseThrow(() -> {
                    log.warn("⚠️ [NOT-FOUND] No member with barcode: {}", barcode);
                    return new MemberNotFoundException("Member not found with barcode: " + barcode);
                });
        log.info("✅ [FOUND] Member ID: {}, Name: {}", member.getId(), member.getFullName());

        return buildEligibilityResult(member);
    }

    /**
     * Check eligibility by card number (exact match)
     */
    private EligibilityResultDto checkByCardNumber(String cardNumber) {
        log.debug("Searching by card number: {}", cardNumber);

        Member member = memberRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> {
                    log.warn("⚠️ [NOT-FOUND] No member with card number: {}", cardNumber);
                    return new MemberNotFoundException("Member not found with card number: " + cardNumber);
                });
        log.info("✅ [FOUND] Member ID: {}, Name: {}", member.getId(), member.getFullName());

        return buildEligibilityResult(member);
    }

    /**
     * Build eligibility result DTO from Member entity
     */
    private EligibilityResultDto buildEligibilityResult(Member member) {
        // Determine eligibility decision based on member status
        String statusValue = member.getStatus() != null ? member.getStatus().name() : "UNKNOWN";
        EligibilityResultDto.EligibilityDecision decision = 
            "ACTIVE".equals(statusValue) ? 
            EligibilityResultDto.EligibilityDecision.ELIGIBLE : 
            EligibilityResultDto.EligibilityDecision.NOT_ELIGIBLE;

        // Log eligibility decision
        log.info("🎯 [ELIGIBILITY-DECISION] Member ID: {}, Status: {}, Decision: {}", 
            member.getId(), statusValue, decision);

        return EligibilityResultDto.builder()
            .memberId(member.getId())
            .fullName(member.getFullName())
            .cardNumber(member.getCardNumber())
            .barcode(member.getBarcode())
            .dependent(member.getParent() != null) // Check if this is a dependent
            .primaryMemberId(member.getParent() != null ? member.getParent().getId() : null)
            .memberStatus(statusValue)
            .cardStatus(member.getCardStatus() != null ? member.getCardStatus().name() : "UNKNOWN")
            .eligibilityDecision(decision)
            .build();
    }
}
