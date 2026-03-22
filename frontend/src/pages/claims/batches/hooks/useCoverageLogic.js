import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import * as benefitPolicyRulesService from 'services/api/benefit-policy-rules.service';
import providerContractsService from 'services/api/provider-contracts.service';
import benefitPoliciesService from 'services/api/benefit-policies.service';

const { 
    checkServiceCoverage, 
    getCoverageForService, 
    checkServiceUsageLimit,
    checkBulkCoverage
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
    serviceYear,
    serviceDate,
    fullCoverage
}) {
    const linesRef = useRef([]);

    // Keep linesRef in sync (passed from parent or managed here)
    // For now, assume we'll use functional updates or passed lines
    
    const fetchCoverage = useCallback(async (service, categoryCodeOverride, lineId = null) => {
        // Full coverage: 100% with no limits, skip backend call
        if (fullCoverage || categoryCodeOverride === 'FULL_COVERAGE') {
            return { coveragePercent: 100, requiresPreApproval: false, notCovered: false, usageExceeded: false, usageDetails: null };
        }

        // Keep single line fetch logic mostly the same for individual changes,
        // but it's largely superseded by bulk.
        const sid = service?.medicalServiceId || 0;
        const serviceOwnCategoryId = service?.categoryId ?? service?.medicalCategoryId ?? service?.medicalCategory?.id ?? null;
        let categoryId = serviceOwnCategoryId;
        const fallbackPercent = policyInfo?.defaultCoveragePercent ?? 100;

        if (!policyId || !applyBenefits)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        if (!sid && !categoryId && !categoryCodeOverride)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        try {
            if (categoryCodeOverride) {
                const cat = rootCategories?.find(c => c.code === categoryCodeOverride);
                if (cat) categoryId = cat.id;
            }

            const contextCatId = categoryId;
            const serviceCatId = serviceOwnCategoryId;

            // USE SINGLE LINE BULK INSTEAD OF 3 APIS
            const payload = {
                memberId: member?.id,
                year: serviceYear || null,
                excludeClaimId: currentClaimId || null,
                lines: [{ id: lineId || "single", serviceId: sid, categoryId: contextCatId, serviceCategoryId: serviceCatId }]
            };

            const bulkResults = await checkBulkCoverage(policyId, payload);
            if (bulkResults && bulkResults.length > 0) {
                return {
                    coveragePercent: bulkResults[0].notCovered ? 0 : (bulkResults[0].coveragePercent ?? fallbackPercent),
                    requiresPreApproval: bulkResults[0].requiresPreApproval ?? false,
                    notCovered: bulkResults[0].notCovered ?? false,
                    usageExceeded: bulkResults[0].usageExceeded ?? false,
                    usageDetails: bulkResults[0].usageDetails ?? null
                };
            }
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        } catch (err) {
            console.error('[fetchCoverage] error:', err);
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        }
    }, [policyId, policyInfo?.defaultCoveragePercent, serviceDate, applyBenefits, member?.id, rootCategories, currentClaimId, serviceYear, fullCoverage]);

    const refetchAllLinesCoverage = useCallback(async (newCategoryCode, currentLines) => {
        if (!policyId || !member?.id) return currentLines.map((l, i) => recompute(l, i, currentLines));
        const catCode = newCategoryCode !== undefined ? newCategoryCode : primaryCategoryCode;

        if (fullCoverage || catCode === 'FULL_COVERAGE') {
             const updated = currentLines.map(line => ({ 
                 ...line, 
                 coveragePercent: 100, 
                 requiresPreApproval: false, 
                 notCovered: false, 
                 usageExceeded: false, 
                 usageDetails: null 
             }));
             return updated.map((line, i) => recompute(line, i, updated));
        }

        const linesToCheck = currentLines.filter(l => l.service);
        if (linesToCheck.length === 0) return currentLines.map((l, i) => recompute(l, i, currentLines));

        let contextCatId = null;
        if (catCode) {
            const cat = rootCategories?.find(c => c.code === catCode);
            if (cat) contextCatId = cat.id;
        }

        const payload = {
            memberId: member.id,
            year: serviceYear || null,
            excludeClaimId: currentClaimId || null,
            lines: linesToCheck.map((l, idx) => {
                const sid = l.service?.medicalServiceId || 0;
                const serviceOwnCategoryId = l.service?.categoryId ?? l.service?.medicalCategoryId ?? l.service?.medicalCategory?.id ?? null;
                
                return {
                    id: l.id || `line_${idx}`,
                    serviceId: sid,
                    categoryId: contextCatId || serviceOwnCategoryId,
                    serviceCategoryId: serviceOwnCategoryId
                };
            })
        };

        try {
            const bulkResults = await checkBulkCoverage(policyId, payload);
            
            const updated = currentLines.map((line, idx) => {
                if (!line.service) return line;
                const lineId = line.id || `line_${idx}`;
                const cov = bulkResults.find(b => b.id === lineId);
                
                if (cov) {
                     return { 
                         ...line, 
                         coveragePercent: cov.notCovered ? 0 : cov.coveragePercent,
                         requiresPreApproval: cov.requiresPreApproval,
                         notCovered: cov.notCovered,
                         usageExceeded: cov.usageExceeded,
                         usageDetails: cov.usageDetails
                     };
                }
                return line;
            });
            
            return updated.map((line, i) => recompute(line, i, updated));
        } catch (err) {
            console.error('[refetchAllLinesCoverage] bulk error:', err);
            return currentLines.map((l, i) => recompute(l, i, currentLines));
        }
    }, [policyId, member?.id, primaryCategoryCode, rootCategories, serviceYear, currentClaimId, recompute, fullCoverage]);

    return {
        fetchCoverage,
        refetchAllLinesCoverage
    };
}
