import { useCallback, useEffect, useRef } from 'react';
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
    const isDev = typeof import.meta !== 'undefined' && import.meta.env?.DEV;
    const singleRequestIdRef = useRef(0);
    const bulkRequestIdRef = useRef(0);
    const singleAbortRef = useRef(null);
    const bulkAbortRef = useRef(null);
    const diagnosticsRef = useRef({
        singleStarted: 0,
        singleAborted: 0,
        singleStaleIgnored: 0,
        bulkStarted: 0,
        bulkAborted: 0,
        bulkStaleIgnored: 0
    });

    const debugLog = useCallback((event, extra = {}) => {
        if (!isDev) return;
        // eslint-disable-next-line no-console
        console.debug('[coverage-race-guard]', event, {
            ...diagnosticsRef.current,
            ...extra
        });
    }, [isDev]);

    useEffect(() => {
        return () => {
            singleAbortRef.current?.abort();
            bulkAbortRef.current?.abort();
        };
    }, []);

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
            rejectionReason: result.refusalReason || undefined
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
                    quantity: Math.max(1, toInt(service?.quantity, 1)),
                    enteredUnitPrice: toMoney(service?.contractPrice),
                    contractPrice: toMoney(service?.contractPrice),
                    categoryId,
                    serviceCategoryId: serviceOwnCategoryId,
                    rejected: false,
                    manualRefusedAmount: 0
                }]
            };

            const requestId = ++singleRequestIdRef.current;
            diagnosticsRef.current.singleStarted += 1;
            singleAbortRef.current?.abort();
            const controller = new AbortController();
            singleAbortRef.current = controller;
            debugLog('single:start', { requestId });

            const bulkResults = await claimsService.calculateCoverageBulk(payload, {
                signal: controller.signal
            });

            if (requestId !== singleRequestIdRef.current) {
                diagnosticsRef.current.singleStaleIgnored += 1;
                debugLog('single:stale-ignored', { requestId, latest: singleRequestIdRef.current });
                return { __stale: true };
            }

            if (bulkResults && bulkResults.length > 0) {
                return normalizeEngineResult(bulkResults[0], fallbackPercent);
            }
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        } catch (err) {
            const isCanceled = err?.name === 'CanceledError'
                || err?.name === 'AbortError'
                || err?.originalError?.name === 'CanceledError'
                || err?.message === 'canceled';

            if (isCanceled) {
                diagnosticsRef.current.singleAborted += 1;
                debugLog('single:aborted', { latest: singleRequestIdRef.current });
                return { __stale: true };
            }
            console.error('[fetchCoverage] error:', err);
            onCoverageError?.('تعذر حساب التغطية للخدمة المختارة. سيتم استخدام التغطية الافتراضية مؤقتاً.');
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        }
    }, [policyId, policyInfo?.defaultCoveragePercent, applyBenefits, member?.id, rootCategories, currentClaimId, serviceYear, fullCoverage, onCoverageError]);

    const refetchAllLinesCoverage = useCallback(async (newCategoryCode, currentLines, newFullCoverage) => {
        if (!policyId || !member?.id) return currentLines.map((l, i) => recompute(l, i, currentLines));

        const catCode = newCategoryCode !== undefined ? newCategoryCode : primaryCategoryCode;
        const isFull = newFullCoverage !== undefined ? newFullCoverage : fullCoverage;

        const linesToCheck = currentLines.filter(l => l.service);
        if (linesToCheck.length === 0) return currentLines.map((l, i) => recompute(l, i, currentLines));

        let contextCatId = null;
        if (catCode && catCode !== 'FULL_COVERAGE') {
            const cat = rootCategories?.find(c => c.code === catCode);
            if (cat) contextCatId = cat.id;
        }

        const payload = {
            policyId,
            memberId: member.id,
            serviceYear: serviceYear || null,
            excludeClaimId: currentClaimId || null,
            fullCoverage: isFull || catCode === 'FULL_COVERAGE',
            lines: linesToCheck.map((line, idx) => buildEngineLineInput(line, idx, contextCatId))
        };

        try {
            const requestId = ++bulkRequestIdRef.current;
            diagnosticsRef.current.bulkStarted += 1;
            bulkAbortRef.current?.abort();
            const controller = new AbortController();
            bulkAbortRef.current = controller;
            debugLog('bulk:start', { requestId, lines: linesToCheck.length });

            const bulkResults = await claimsService.calculateCoverageBulk(payload, {
                signal: controller.signal
            });

            if (requestId !== bulkRequestIdRef.current) {
                diagnosticsRef.current.bulkStaleIgnored += 1;
                debugLog('bulk:stale-ignored', { requestId, latest: bulkRequestIdRef.current });
                return null;
            }

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
            const isCanceled = err?.name === 'CanceledError'
                || err?.name === 'AbortError'
                || err?.originalError?.name === 'CanceledError'
                || err?.message === 'canceled';

            if (isCanceled) {
                diagnosticsRef.current.bulkAborted += 1;
                debugLog('bulk:aborted', { latest: bulkRequestIdRef.current });
                return null;
            }
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
