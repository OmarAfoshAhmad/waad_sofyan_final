package com.waad.tba.modules.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ==================== CARD NUMBER GENERATION ====================
 *
 * Format:
 * PRINCIPAL : {EMPLOYER_CODE}-{JOIN_YEAR}-{EMPLOYEE_NUMBER}
 * e.g. JFZ-2025-126565
 *
 * DEPENDENT : {PRINCIPAL_CARD}-{RELATIONSHIP_CODE}{ORDINAL}
 * e.g. JFZ-2025-126565-D1 (first daughter)
 * JFZ-2025-126565-W2 (second wife)
 *
 * Relationship codes come from {@link Member.Relationship#getCardCode()}.
 * The ordinal is 1-based and counts existing dependents of the same type.
 *
 * Year resolution order: joinDate → startDate → current year.
 * Employee-number fallback: if null/blank, an 8-digit random number is
 * generated
 * and retried until unique (no DB sequence required).
 * ================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardNumberGeneratorService {

    private final MemberRepository memberRepository;

    // ----------------------------------------------------------------
    // PRINCIPAL
    // ----------------------------------------------------------------

    /**
     * Generate card number for a PRINCIPAL member.
     *
     * @param member Principal member (employer and dates must already be set)
     * @return Card number string, e.g. "JFZ-2025-126565"
     */
    @Transactional
    public String generateForPrincipal(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }
        if (member.getEmployer() == null || member.getEmployer().getCode() == null) {
            throw new IllegalStateException("Employer with code must be set before generating a card number");
        }

        String employerCode = member.getEmployer().getCode().trim().toUpperCase();
        int year = resolveYear(member);
        String empNum = resolveEmployeeNumber(member);

        String cardNumber = employerCode + "-" + year + "-" + empNum;
        log.info("Generated card number for PRINCIPAL: {}", cardNumber);
        return cardNumber;
    }

    /**
     * Generate a guaranteed-unique card number for a PRINCIPAL member.
     *
     * <ul>
     * <li>If {@code employeeNumber} is set → formula is deterministic.
     * A collision means the employee is already registered → exception.</li>
     * <li>If {@code employeeNumber} is null/blank → an 8-digit random suffix
     * is used and the method retries until a unique card is found.</li>
     * </ul>
     *
     * @param member Principal member (employer must already be set)
     * @return Unique card number
     * @throws IllegalStateException if a duplicate is detected (deterministic path)
     *                               or if uniqueness cannot be achieved (random
     *                               path)
     */
    @Transactional
    public String generateUniqueForPrincipal(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }
        if (member.getEmployer() == null || member.getEmployer().getCode() == null) {
            throw new IllegalStateException("Employer with code must be set before generating a card number");
        }

        boolean hasEmployeeNumber = member.getEmployeeNumber() != null
                && !member.getEmployeeNumber().trim().isEmpty();

        if (hasEmployeeNumber) {
            // Deterministic: collision = same employee already registered
            String cardNumber = generateForPrincipal(member);
            if (memberRepository.existsByCardNumber(cardNumber)) {
                throw new IllegalStateException(
                        "Card number already exists: " + cardNumber +
                                ". This principal may already be registered.");
            }
            return cardNumber;
        }

        // Random fallback: retry until unique
        String employerCode = member.getEmployer().getCode().trim().toUpperCase();
        int year = resolveYear(member);
        final int MAX_ATTEMPTS = 50;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String randomNum = String.format("%08d",
                    ThreadLocalRandom.current().nextInt(1, 100_000_000));
            String cardNumber = employerCode + "-" + year + "-" + randomNum;
            if (!memberRepository.existsByCardNumber(cardNumber)) {
                log.warn("employeeNumber is null for member '{}' – assigned random card number: {}",
                        member.getFullName(), cardNumber);
                return cardNumber;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique card number for '" + member.getFullName() +
                        "' after " + MAX_ATTEMPTS + " attempts. Please assign an employee number.");
    }

    // ----------------------------------------------------------------
    // DEPENDENT
    // ----------------------------------------------------------------

    /**
     * Generate card number for a DEPENDENT member.
     *
     * Format: {PRINCIPAL_CARD}-{RELATIONSHIP_CODE}{ORDINAL}
     * Example: JFZ-2025-126565-D1
     *
     * The ordinal is 1-based and counts current dependents of the same
     * relationship type, so it auto-increments per type (W1, W2, D1, D2…).
     *
     * @param principal    The principal (parent) member
     * @param relationship Relationship of the new dependent (must not be null)
     * @return Card number with relationship suffix
     */
    @Transactional(readOnly = true)
    public String generateForDependent(Member principal, Member.Relationship relationship) {
        if (principal == null) {
            throw new IllegalArgumentException("Principal member cannot be null");
        }
        if (principal.isDependent()) {
            throw new IllegalArgumentException(
                    "Cannot generate dependent card number: the provided member is itself a dependent.");
        }
        if (relationship == null) {
            throw new IllegalArgumentException("Relationship cannot be null for a dependent member");
        }

        String principalCard = principal.getCardNumber();
        if (principalCard == null || principalCard.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Principal member must have a card number before dependents can be created");
        }

        long sameTypeCount = memberRepository.countByParentIdAndRelationship(
                principal.getId(), relationship);
        int ordinal = (int) sameTypeCount + 1;

        String cardNumber = principalCard + "-" + relationship.getCardCode() + ordinal;
        log.info("Generated card number for DEPENDENT ({}) of principal {}: {}",
                relationship, principal.getId(), cardNumber);
        return cardNumber;
    }

    // ----------------------------------------------------------------
    // VALIDATION / UTILITY
    // ----------------------------------------------------------------

    /**
     * Validate that a card number matches the expected format.
     *
     * Principal pattern : CODE-YYYY-EMPNUM (e.g. JFZ-2025-126565)
     * Dependent pattern : CODE-YYYY-EMPNUM-SUFFIX (e.g. JFZ-2025-126565-D1)
     *
     * @param cardNumber Card number to validate
     * @return true if valid
     */
    public boolean isValidCardNumberFormat(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        // At minimum: NON_EMPTY-4DIGITS-NON_EMPTY
        return cardNumber.matches("^[A-Z0-9]+-\\d{4}-.+");
    }

    /**
     * Check whether a card number belongs to a principal (no relationship suffix).
     * A principal card ends with a segment that is purely alphanumeric digits,
     * not a relationship code followed by an ordinal.
     */
    public boolean isPrincipalCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        // Dependent suffixes end with one or more letters followed by digits (e.g. D1,
        // SR2)
        return !cardNumber.matches(".+-[A-Z]+(R?)[0-9]+$");
    }

    /**
     * Extract the principal's card number from a dependent card number.
     * Returns everything before the last "-SUFFIX" segment.
     *
     * Example: "JFZ-2025-126565-D1" → "JFZ-2025-126565"
     */
    public String extractBaseCardNumber(String dependentCardNumber) {
        if (dependentCardNumber == null) {
            return null;
        }
        // Strip last segment if it looks like a relationship suffix (letters+digits)
        int lastHyphen = dependentCardNumber.lastIndexOf('-');
        if (lastHyphen > 0) {
            String suffix = dependentCardNumber.substring(lastHyphen + 1);
            if (suffix.matches("[A-Z]+[0-9]+")) {
                return dependentCardNumber.substring(0, lastHyphen);
            }
        }
        return dependentCardNumber;
    }

    // ----------------------------------------------------------------
    // PRIVATE HELPERS
    // ----------------------------------------------------------------

    private int resolveYear(Member member) {
        if (member.getJoinDate() != null) {
            return member.getJoinDate().getYear();
        }
        if (member.getStartDate() != null) {
            return member.getStartDate().getYear();
        }
        return LocalDate.now().getYear();
    }

    private String resolveEmployeeNumber(Member member) {
        String empNum = member.getEmployeeNumber();
        if (empNum != null && !empNum.trim().isEmpty()) {
            return empNum.trim();
        }
        // Fallback: random 8-digit number (used by generateForPrincipal directly;
        // prefer generateUniqueForPrincipal which retries on collision).
        String generated = String.format("%08d",
                ThreadLocalRandom.current().nextInt(1, 100_000_000));
        log.warn("employeeNumber is null for member '{}' – using random fallback in generateForPrincipal: {}",
                member.getFullName(), generated);
        return generated;
    }
}
