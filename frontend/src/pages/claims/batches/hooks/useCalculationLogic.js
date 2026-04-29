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

        // Extract inputs
        const qty = Math.max(1, parseInt(line.quantity, 10) || 1);
        const uprice = Math.max(0, parseFloat(line.unitPrice) || 0);
        const calculatedTotal = qty * uprice;

        // Has the user changed the price/qty since the last backend sync?
        // We do a naive calculation for UI responsiveness. 
        // The backend CoverageEngine is the ultimate source of truth on Save!
        const cPrice = parseFloat(line.contractPrice) || 0;
        const effUPrice = (cPrice > 0 && cPrice < uprice) ? cPrice : uprice;
        const effTotal = effUPrice * qty;

        // If line is completely rejected
        if (line.rejected) {
            return {
                ...line,
                coveragePercent,
                total: toMoney(calculatedTotal),
                byCompany: 0,
                byEmployee: 0, // Total rejection means company pays 0. Patient pays 0 or everything? 
                // Usually Refused means it's simply NOT paid.
                refusedAmount: toMoney(calculatedTotal),
                rejectionReason: line.rejectionReason || 'الخدمة مرفوضة',
                usageExceeded: !!line.usageExceeded || !!line.usageDetails?.exceeded,
                usageExhausted: !!line.usageDetails?.exceeded,
                usageDetails: line.usageDetails || null
            };
        }

        // Naive share estimation
        const baseCompany = (effTotal * coveragePercent) / 100.0;
        const mRefused = Math.max(0, parseFloat(line.manualRefusedAmount) || 0);
        let estCompanyShare = Math.max(0, baseCompany - mRefused);
        let estPatientShare = effTotal - baseCompany; // does not pay for manual refusal
        let estRefused = Math.max(0, calculatedTotal - effTotal) + mRefused; // Price diff + manual diff

        // If usage details say exceeded, we don't naively override the backend's exact refusal
        // unless the calculatedTotal is completely different from the line's old total.
        // Only keep backend values if total hasn't changed AND there's no manual change in rejection status
        const oldTotal = parseFloat(line.total) || 0;
        const hasManualRefusal = mRefused > 0;
        const isRejectedStatusChanged = line.rejected !== (parseFloat(line.oldRejected) === 1); // Helper to track change

        if (Math.abs(oldTotal - calculatedTotal) < 0.01 && line.byCompany !== undefined && !hasManualRefusal && !line.rejected) {
            // Keep backend values if total hasn't changed and no manual rejection/status change
            estCompanyShare = parseFloat(line.byCompany) || 0;
            estPatientShare = parseFloat(line.byEmployee) || 0;
            estRefused = parseFloat(line.refusedAmount) || 0;
        }

        return {
            ...line,
            coveragePercent,
            total: toMoney(calculatedTotal),
            byCompany: toMoney(estCompanyShare),
            byEmployee: toMoney(estPatientShare),
            refusedAmount: toMoney(estRefused),
            rejectionReason: line.rejectionReason || '',
            usageExceeded: !!line.usageExceeded || !!line.usageDetails?.exceeded,
            usageExhausted: !!line.usageDetails?.exceeded,
            usageDetails: line.usageDetails || null
        };
    }, [policyInfo?.defaultCoveragePercent]);

    return { recompute };
}
