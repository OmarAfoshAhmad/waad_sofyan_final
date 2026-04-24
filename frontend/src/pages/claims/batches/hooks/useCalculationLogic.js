import { useCallback } from 'react';

export function useCalculationLogic({ policyInfo }) {

    const toMoney = (value) => {
        const num = Number(value);
        return Number.isFinite(num) ? Number(num.toFixed(2)) : 0;
    };
    
    const recompute = useCallback((line, idx = null, currentBatch = null) => {
        if (!line) return line;
        const defaultCov = policyInfo?.defaultCoveragePercent ?? 100;
        const coveragePercent = (line.coveragePercent !== null && line.coveragePercent !== undefined)
            ? line.coveragePercent
            : defaultCov;

        return {
            ...line,
            coveragePercent,
            total: toMoney(line.total),
            byCompany: toMoney(line.byCompany),
            byEmployee: toMoney(line.byEmployee),
            refusedAmount: toMoney(line.refusedAmount),
            rejectionReason: line.rejectionReason || '',
            usageExceeded: !!line.usageExceeded || !!line.usageDetails?.exceeded,
            usageExhausted: !!line.usageDetails?.exceeded,
            usageDetails: line.usageDetails || null
        };
    }, [policyInfo?.defaultCoveragePercent]);

    return { recompute };
}
