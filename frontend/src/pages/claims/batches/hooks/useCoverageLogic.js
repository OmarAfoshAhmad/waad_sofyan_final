import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import * as benefitPolicyRulesService from 'services/api/benefit-policy-rules.service';
import providerContractsService from 'services/api/provider-contracts.service';
import benefitPoliciesService from 'services/api/benefit-policies.service';

const { 
    checkServiceCoverage, 
    getCoverageForService, 
    checkServiceUsageLimit 
} = benefitPolicyRulesService;

export function useCoverageLogic({ 
    policyId, 
    policyInfo, 
    member, 
    applyBenefits, 
    rootCategories, 
    primaryCategoryCode,
    setLines,
    recompute,
    currentClaimId,
    serviceYear
}) {
    const linesRef = useRef([]);

    // Keep linesRef in sync (passed from parent or managed here)
    // For now, assume we'll use functional updates or passed lines
    
    const fetchCoverage = useCallback(async (service, categoryCodeOverride) => {
        const sid = service?.medicalServiceId || 0;
        const serviceOwnCategoryId = service?.categoryId ?? service?.medicalCategoryId ?? service?.medicalCategory?.id ?? null;
        let categoryId = serviceOwnCategoryId;
        // Keep the service's own intrinsic category for precise rule matching
        const fallbackPercent = policyInfo?.defaultCoveragePercent ?? 100;

        if (!policyId || !applyBenefits)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        if (!sid && !categoryId && !categoryCodeOverride)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        try {
            // When a context (categoryCodeOverride) is explicitly set by the user, it always
            // takes precedence so that context-level rules (e.g. "إيواء" vs "عيادات خارجية")
            // are resolved correctly even when the service has its own intrinsic category.
            // When no context is provided, fall back to the service's own category.
            if (categoryCodeOverride) {
                const cat = rootCategories?.find(c => c.code === categoryCodeOverride);
                if (cat) categoryId = cat.id;
            }

            // Send both: categoryId (context/override) and serviceOwnCategoryId (intrinsic)
            // so the backend can find the most specific matching rule.
            // e.g. context=CAT-OP (51), serviceOwn=CAT-IP-PHYSIO (201) → finds CAT-OP-PHYSIO
            const contextCatId = categoryId;
            const serviceCatId = serviceOwnCategoryId;

            const [r, fullRule] = await Promise.all([
                checkServiceCoverage(policyId, sid, contextCatId, serviceCatId),
                getCoverageForService(policyId, sid, contextCatId, serviceCatId).catch(() => null)
            ]);

            const timesLimit = r?.timesLimit ?? fullRule?.timesLimit ?? null;
            const amountLimit = r?.amountLimit != null
                ? parseFloat(r.amountLimit)
                : (fullRule?.amountLimit != null ? parseFloat(fullRule.amountLimit) : null);

            const hasLimits = (timesLimit != null) || (amountLimit != null);
            let baseLimitDetails = hasLimits ? {
                hasLimit: true,
                timesLimit,
                amountLimit,
                usedCount: 0,
                usedAmount: 0,
                exceeded: false,
                timesExceeded: false,
                amountExceeded: false
            } : null;

            if (member?.id) {
                // Pass serviceYear so the query matches the batch's year (not current year)
                // e.g. batch for Jan 2025 → serviceDate = 2025-01-01 → year must be 2025
                const usageYear = serviceYear || null;
                // Use the context category (categoryId) for usage limits so they match the active rule context
                const usage = await checkServiceUsageLimit(
                    policyId,
                    sid,
                    member.id,
                    contextCatId,
                    usageYear,
                    currentClaimId,
                    serviceCatId
                );
                if (usage && usage.hasLimit) {
                    baseLimitDetails = { ...baseLimitDetails, ...usage };
                }
            }

            return {
                coveragePercent: r?.coveragePercent ?? fallbackPercent,
                requiresPreApproval: r?.requiresPreApproval ?? false,
                notCovered: r?.covered === false,
                usageExceeded: baseLimitDetails?.exceeded ?? false,
                usageDetails: baseLimitDetails
            };
        } catch (err) {
            console.error('[fetchCoverage] error:', err);
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        }
    }, [policyId, policyInfo?.defaultCoveragePercent, applyBenefits, member?.id, rootCategories, currentClaimId, serviceYear]);

    const refetchAllLinesCoverage = useCallback(async (newCategoryCode, currentLines) => {
        if (!policyId || !member?.id) return;
        const catCode = newCategoryCode !== undefined ? newCategoryCode : primaryCategoryCode;

        const updated = await Promise.all(
            currentLines.map(async (line) => {
                if (!line.service) return line;
                const cov = await fetchCoverage(line.service, catCode);
                return { ...line, ...cov };
            })
        );
        
        return updated.map((line, i) => recompute(line, i, updated));
    }, [policyId, member?.id, primaryCategoryCode, fetchCoverage, recompute]);

    return {
        fetchCoverage,
        refetchAllLinesCoverage
    };
}
