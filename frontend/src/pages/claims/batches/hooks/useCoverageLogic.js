import { useCallback } from 'react';
import claimsService from 'services/api/claims.service';

export function useCoverageLogic({ 
    policyId, 
    policyInfo, 
    member, 
    applyBenefits, 
    rootCategories, 
    primaryCategoryCode,
    recompute,
    currentClaimId,
    serviceYear,
    fullCoverage,
    onCoverageError
}) {
    const toMoney = (value) => {
        const num = Number(value);
        return Number.isFinite(num) ? Number(num.toFixed(2)) : 0;
    };

    const toInt = (value, fallback = 0) => {
        const num = Number.parseInt(value, 10);
        return Number.isFinite(num) ? num : fallback;
    };

    const normalizeEngineResult = (result, fallbackPercent) => {
        if (!result) {
            return {
                coveragePercent: fallbackPercent,
                requiresPreApproval: false,
                notCovered: false,
                usageExceeded: false,
                usageDetails: null,
                total: 0,
                byCompany: 0,
                byEmployee: 0,
                refusedAmount: 0,
                rejectionReason: ''
            };
        }

        return {
            coveragePercent: result.notCovered ? 0 : (result.coveragePercent ?? fallbackPercent),
            requiresPreApproval: !!result.requiresPreApproval,
            notCovered: !!result.notCovered,
            usageExceeded: !!result.usageDetails?.exceeded,
            usageDetails: result.usageDetails ?? null,
            total: toMoney(result.requestedTotal),
            byCompany: toMoney(result.companyShare),
            byEmployee: toMoney(result.patientShare),
            refusedAmount: toMoney(result.refusedAmount),
            rejectionReason: result.refusalReason || ''
        };
    };

    const buildEngineLineInput = (line, idx, contextCatId = null) => {
        const serviceOwnCategoryId = line?.service?.categoryId
            ?? line?.service?.medicalCategoryId
            ?? line?.service?.medicalCategory?.id
            ?? null;

        return {
            lineId: line?.id || `line_${idx}`,
            serviceId: line?.service?.medicalServiceId || 0,
            pricingItemId: line?.service?.pricingItemId || null,
            quantity: Math.max(1, toInt(line?.quantity, 1)),
            enteredUnitPrice: toMoney(line?.unitPrice),
            contractPrice: toMoney(line?.contractPrice),
            categoryId: contextCatId ?? serviceOwnCategoryId,
            serviceCategoryId: serviceOwnCategoryId,
            rejected: !!line?.rejected,
            manualRefusedAmount: toMoney(line?.manualRefusedAmount)
        };
    };

    const fetchCoverage = useCallback(async (service, categoryCodeOverride, lineId = null) => {
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

            const payload = {
                policyId,
                memberId: member?.id || null,
                serviceYear: serviceYear || null,
                excludeClaimId: currentClaimId || null,
                fullCoverage: fullCoverage || categoryCodeOverride === 'FULL_COVERAGE',
                lines: [{
                    lineId: lineId || 'single',
                    serviceId: sid,
                    pricingItemId: service?.pricingItemId || null,
                    quantity: 1,
                    enteredUnitPrice: toMoney(service?.contractPrice),
                    contractPrice: toMoney(service?.contractPrice),
                    categoryId,
                    serviceCategoryId: serviceOwnCategoryId,
                    rejected: false,
                    manualRefusedAmount: 0
                }]
            };

            const bulkResults = await claimsService.calculateCoverageBulk(payload);
            if (bulkResults && bulkResults.length > 0) {
                return normalizeEngineResult(bulkResults[0], fallbackPercent);
            }
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        } catch (err) {
            console.error('[fetchCoverage] error:', err);
            onCoverageError?.('تعذر حساب التغطية للخدمة المختارة. سيتم استخدام التغطية الافتراضية مؤقتاً.');
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        }
    }, [policyId, policyInfo?.defaultCoveragePercent, applyBenefits, member?.id, rootCategories, currentClaimId, serviceYear, fullCoverage, onCoverageError]);

    const refetchAllLinesCoverage = useCallback(async (newCategoryCode, currentLines) => {
        if (!policyId || !member?.id) return currentLines.map((l, i) => recompute(l, i, currentLines));
        const catCode = newCategoryCode !== undefined ? newCategoryCode : primaryCategoryCode;

        const linesToCheck = currentLines.filter(l => l.service);
        if (linesToCheck.length === 0) return currentLines.map((l, i) => recompute(l, i, currentLines));

        let contextCatId = null;
        if (catCode) {
            const cat = rootCategories?.find(c => c.code === catCode);
            if (cat) contextCatId = cat.id;
        }

        const payload = {
            policyId,
            memberId: member.id,
            serviceYear: serviceYear || null,
            excludeClaimId: currentClaimId || null,
            fullCoverage: fullCoverage || catCode === 'FULL_COVERAGE',
            lines: linesToCheck.map((line, idx) => buildEngineLineInput(line, idx, contextCatId))
        };

        try {
            const bulkResults = await claimsService.calculateCoverageBulk(payload);
            
            const updated = currentLines.map((line, idx) => {
                if (!line.service) return line;
                const lineId = line.id || `line_${idx}`;
                const cov = bulkResults.find(b => b.lineId === lineId);
                
                if (cov) {
                    const normalized = normalizeEngineResult(cov, policyInfo?.defaultCoveragePercent ?? 100);
                     return { 
                         ...line, 
                         ...normalized
                     };
                }
                return line;
            });
            
            return updated.map((line, i) => recompute(line, i, updated));
        } catch (err) {
            console.error('[refetchAllLinesCoverage] bulk error:', err);
            onCoverageError?.('فشل تحديث تغطية جميع البنود. يرجى المحاولة مرة أخرى.');
            return currentLines.map((l, i) => recompute(l, i, currentLines));
        }
    }, [policyId, member?.id, primaryCategoryCode, rootCategories, serviceYear, currentClaimId, recompute, fullCoverage, policyInfo?.defaultCoveragePercent, onCoverageError]);

    return {
        fetchCoverage,
        refetchAllLinesCoverage
    };
}
