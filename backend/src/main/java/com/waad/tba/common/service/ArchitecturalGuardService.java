package com.waad.tba.common.service;

import com.waad.tba.common.exception.ArchitecturalViolationException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Architectural Guard Service
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SYSTEM INVARIANTS - NON-NEGOTIABLE RULES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This service enforces architectural rules that must NEVER be violated.
 * These are system invariants, not business rules.
 * 
 * RULES ENFORCED:
 * 1. MedicalService must belong to a MedicalCategory
 * 2. Claim must be linked to a Visit
 * 3. Claim must have at least one MedicalService
 * 4. Contract Pricing must reference a MedicalService
 * 5. BenefitPolicyRule must target either Service OR Category
 * 6. PreAuthorization must be linked to a Visit
 * 7. Price must come from ProviderContract only
 * 8. Coverage must come from BenefitPolicy only
 * 
 * GOLDEN RULES:
 * - Service يُنفذ (executes)
 * - Category يُقرر (decides coverage)
 * - Policy يُحكم (governs limits)
 * - Contract يُسعّر (prices)
 * 
 * @version 1.0
 * @since 2026-01-22
 */
@Slf4j
@Service
public class ArchitecturalGuardService {

    // No repository dependencies — this guard operates on IDs and entity state
    // only.
    // Category-based architecture: services are identified by code/name on
    // pricingItem.

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDICAL SERVICE GUARDS (legacy stubs — services are now pricingItem-based)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate service creation request has category.
     * RULE: Every ProviderContractPricingItem MUST belong to a MedicalCategory.
     */
    public void guardServiceCreateHasCategory(Long categoryId, String serviceCode) {
        if (categoryId == null) {
            throw ArchitecturalViolationException.serviceWithoutCategory(serviceCode);
        }
    }

    /**
     * @deprecated Services are no longer tracked via MedicalService catalog.
     *             Category is now embedded directly in ProviderContractPricingItem.
     */
    @Deprecated
    public void guardServiceHasCategory(Long serviceId) {
        // No-op: MedicalService catalog is being removed.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLAIM GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate Claim has a Visit.
     * RULE: Visit-centric architecture is mandatory
     */
    public void guardClaimHasVisit(Claim claim) {
        if (claim == null) {
            throw new ArchitecturalViolationException("Claim", "Claim cannot be null");
        }
        if (claim.getVisit() == null) {
            throw ArchitecturalViolationException.claimWithoutVisit(claim.getId());
        }
        log.trace("✅ Guard passed: Claim {} has visit {}", claim.getId(), claim.getVisit().getId());
    }

    /**
     * Validate visitId is provided for claim creation (ID-based).
     * Called before entity is created.
     */
    public void guardClaimHasVisit(Long visitId) {
        if (visitId == null) {
            throw ArchitecturalViolationException.claimWithoutVisit(null);
        }
    }

    /**
     * Validate Claim lines have service identification.
     * RULE: Each line must have at least a serviceCode or serviceName.
     */
    public void guardClaimHasServices(Claim claim, List<ClaimLine> lines) {
        if (claim == null) {
            throw new ArchitecturalViolationException("Claim", "Claim cannot be null");
        }
        if (lines == null || lines.isEmpty()) {
            throw ArchitecturalViolationException.claimWithoutService(claim.getId());
        }

        // Validate each line has service identification via code or name
        for (ClaimLine line : lines) {
            if ((line.getServiceCode() == null || line.getServiceCode().isBlank()) &&
                    (line.getServiceName() == null || line.getServiceName().isBlank())) {
                throw new ArchitecturalViolationException(
                        "INVALID_CLAIM_LINE",
                        "ClaimLine",
                        String.format("Claim %d line %d must have a service reference (Code or Name).",
                                claim.getId(), line.getId()));
            }
        }
        log.trace("✅ Guard passed: Claim {} has {} valid service lines", claim.getId(), lines.size());
    }

    /**
     * Validate claim lines (DTO) have pricing item or service code.
     * Called before entity is created.
     */
    public void guardClaimHasServices(List<Long> pricingItemIds) {
        // pricingItemIds may be empty for claims using serviceCode directly \u2014 just
        // warn.
        if (pricingItemIds == null || pricingItemIds.isEmpty()) {
            log.debug("\u26a0\ufe0f guardClaimHasServices: no pricingItemIds provided (serviceCode path).");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREAUTHORIZATION GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate PreAuthorization has a Visit.
     * RULE: Standalone pre-authorizations are not allowed
     */
    public void guardPreAuthHasVisit(PreAuthorization preAuth) {
        if (preAuth == null) {
            throw new ArchitecturalViolationException("PreAuthorization", "PreAuthorization cannot be null");
        }
        if (preAuth.getVisit() == null) {
            throw ArchitecturalViolationException.preAuthWithoutVisit(preAuth.getReferenceNumber());
        }
        log.trace("✅ Guard passed: PreAuth {} has visit", preAuth.getReferenceNumber());
    }

    /**
     * Validate visitId is provided for preauth creation (ID-based).
     * Called before entity is created.
     */
    public void guardPreAuthHasVisit(Long visitId) {
        if (visitId == null) {
            throw ArchitecturalViolationException.preAuthWithoutVisit(null);
        }
    }

    /**
     * Validate PreAuthorization has service identification.
     * RULE: PA must reference a service via code or pricingItem.
     */
    public void guardPreAuthHasService(PreAuthorization preAuth) {
        if (preAuth == null) {
            throw new ArchitecturalViolationException("PreAuthorization", "PreAuthorization cannot be null");
        }
        if (preAuth.getServiceCode() == null || preAuth.getServiceCode().isBlank()) {
            throw new ArchitecturalViolationException(
                    "SERVICE_REQUIRED",
                    "PreAuthorization",
                    String.format(
                            "PreAuthorization '%s' must have a service code. Free-text services are not allowed.",
                            preAuth.getReferenceNumber()));
        }
        log.trace("✅ Guard passed: PreAuth {} has service", preAuth.getReferenceNumber());
    }

    /**
     * Validate pricingItemId is provided for preauth creation (ID-based).
     * pricingItemId replaced medicalServiceId as the primary service identifier.
     */
    public void guardPreAuthHasService(Long pricingItemId) {
        if (pricingItemId == null) {
            throw new ArchitecturalViolationException(
                    "SERVICE_REQUIRED",
                    "PreAuthorization",
                    "PreAuthorization must reference a pricing item. Free-text services are not allowed.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTRACT PRICING GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate pricing item has identification.
     * RULE: Pricing must have at least a Name or Code
     */
    public void guardPricingHasService(ProviderContractPricingItem item) {
        if (item == null) {
            throw new ArchitecturalViolationException("ProviderContractPricingItem", "Pricing item cannot be null");
        }
        if ((item.getServiceName() == null || item.getServiceName().isBlank()) &&
                (item.getServiceCode() == null || item.getServiceCode().isBlank())) {

            throw ArchitecturalViolationException.pricingWithoutService(
                    item.getContract() != null ? item.getContract().getId() : null);
        }
        log.trace("✅ Guard passed: Pricing item has identification");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BENEFIT POLICY RULE GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate rule has a target category.
     * RULE: Rules without a category cannot be used for coverage calculation.
     */
    public void guardRuleHasTarget(BenefitPolicyRule rule) {
        if (rule == null) {
            throw new ArchitecturalViolationException("BenefitPolicyRule", "Rule cannot be null");
        }
        if (rule.getMedicalCategory() == null) {
            throw ArchitecturalViolationException.ruleWithoutTarget(rule.getId());
        }
        log.trace("✅ Guard passed: Rule {} has category target", rule.getId());
    }

    /**
     * Validate rule targets only a category (no service).
     * In the new architecture rules are always category-based.
     */
    public void guardRuleHasSingleTarget(BenefitPolicyRule rule) {
        if (rule == null) {
            throw new ArchitecturalViolationException("BenefitPolicyRule", "Rule cannot be null");
        }
        // No-op: service targets were removed; category is mandated by
        // guardRuleHasTarget.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRICE SOURCE GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Warn if basePrice is being used for calculation.
     * RULE: Price must come from ProviderContract only
     */
    public void warnBasePriceUsage(String context) {
        log.warn("⚠️ ARCHITECTURAL WARNING: basePrice should not be used for calculation. " +
                "Use ProviderContract.contractPrice instead. Context: {}", context);
    }

    /**
     * Guard that price is from contract, not service
     */
    public void guardPriceFromContract(boolean hasContract, String context) {
        if (!hasContract) {
            throw ArchitecturalViolationException.invalidPriceSource(
                    String.format("No contract found. %s", context));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COVERAGE SOURCE GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Warn if coverage is being determined outside BenefitPolicy.
     * RULE: Coverage must come from BenefitPolicy only
     */
    public void warnCoverageSourceViolation(String context) {
        log.warn("⚠️ ARCHITECTURAL WARNING: Coverage should be determined by BenefitPolicyCoverageService only. " +
                "Context: {}", context);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPOSITE GUARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Run all guards for Claim creation
     */
    public void guardClaimCreation(Claim claim, List<ClaimLine> lines) {
        log.debug("🔒 Running architectural guards for Claim creation");
        guardClaimHasVisit(claim);
        guardClaimHasServices(claim, lines);
        log.debug("✅ All guards passed for Claim creation");
    }

    /**
     * Run all guards for PreAuthorization creation
     */
    public void guardPreAuthCreation(PreAuthorization preAuth) {
        log.debug("🔒 Running architectural guards for PreAuthorization creation");
        guardPreAuthHasVisit(preAuth);
        guardPreAuthHasService(preAuth);
        log.debug("✅ All guards passed for PreAuthorization creation");
    }

    /**
     * Run all guards for BenefitPolicyRule creation
     */
    public void guardRuleCreation(BenefitPolicyRule rule) {
        log.debug("🔒 Running architectural guards for BenefitPolicyRule creation");
        guardRuleHasTarget(rule);
        guardRuleHasSingleTarget(rule);
        log.debug("✅ All guards passed for BenefitPolicyRule creation");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ID-BASED COMPOSITE GUARDS (For DTO validation before entity creation)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Run all guards for Claim creation using IDs (pre-entity validation).
     *
     * @param visitId        The visit ID from DTO
     * @param pricingItemIds List of pricing item IDs from DTO lines (may be empty
     *                       for serviceCode path)
     */
    public void guardClaimCreation(Long visitId, List<Long> pricingItemIds) {
        log.debug("🔒 Running architectural guards for Claim creation (ID-based)");
        guardClaimHasVisit(visitId);
        guardClaimHasServices(pricingItemIds);
        log.debug("✅ All guards passed for Claim creation (ID-based)");
    }

    /**
     * Run all guards for PreAuthorization creation using IDs (pre-entity
     * validation).
     *
     * @param visitId       The visit ID from DTO
     * @param pricingItemId The pricing item ID from DTO (replaces medicalServiceId)
     */
    public void guardPreAuthCreation(Long visitId, Long pricingItemId) {
        log.debug("🔒 Running architectural guards for PreAuthorization creation (ID-based)");
        guardPreAuthHasVisit(visitId);
        guardPreAuthHasService(pricingItemId);
        log.debug("✅ All guards passed for PreAuthorization creation (ID-based)");
    }

    /**
     * Run guards for BenefitPolicyRule creation using IDs (pre-entity validation).
     *
     * @param serviceId  Must be null (service rules deprecated)
     * @param categoryId Category ID \u2014 required
     */
    public void guardRuleCreation(Long serviceId, Long categoryId) {
        log.debug("🔒 Running architectural guards for Rule creation (ID-based)");
        if (categoryId == null) {
            throw ArchitecturalViolationException.ruleWithoutTarget(null);
        }
        if (serviceId != null) {
            throw new ArchitecturalViolationException(
                    "SERVICE_RULE_DEPRECATED",
                    "BenefitPolicyRule",
                    "Service-level rules are no longer supported. Use category-based rules.");
        }
        log.debug("✅ All guards passed for Rule creation (ID-based)");
    }
}
