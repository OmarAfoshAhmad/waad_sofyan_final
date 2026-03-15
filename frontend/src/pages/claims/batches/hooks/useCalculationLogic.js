import { useCallback } from 'react';

export function useCalculationLogic({ applyBenefits, policyInfo }) {
    
    const recompute = useCallback((line, idx = null, currentBatch = null) => {
        if (!line) return line;

        const qty = Math.max(0, parseInt(line.quantity) || 0);
        const enteredPrice = Math.max(0, parseFloat(line.unitPrice) || 0);
        const total = parseFloat((enteredPrice * qty).toFixed(2));

        if (line.rejected) {
            return { ...line, byCompany: 0, byEmployee: 0, total, refusedAmount: total };
        }

        const contractPrice = parseFloat(line.contractPrice || 0);
        const effectivePrice = (contractPrice > 0 && enteredPrice > contractPrice) ? contractPrice : enteredPrice;
        const effectiveTotal = parseFloat((effectivePrice * qty).toFixed(2));
        const priceRefused = Math.max(0, total - effectiveTotal);

        let limitRefused = 0;
        let usageExceeded = false;
        const usage = line.usageDetails;

        let batchUsedTimes = 0;
        let batchUsedAmount = 0;
        let totalUsedCount = 0;
        let totalUsedAmount = 0;

        if (usage && usage.hasLimit) {
            const currentRuleId = usage.ruleId;
            if (currentRuleId && currentBatch && idx !== null) {
                currentBatch.forEach((l, i) => {
                    if (i < idx && !l.rejected && l.service && l.usageDetails?.ruleId === currentRuleId) {
                        // Calculate how many were approved in this previous line
                        const lQty = parseInt(l.quantity) || 0;
                        const lEffectivePrice = Math.min(parseFloat(l.unitPrice || 0), parseFloat(l.contractPrice || 0) || Infinity);
                        const lEffectiveTotal = parseFloat((lEffectivePrice * lQty).toFixed(2));
                        
                        // We need to know how much was refused due to limit in THAT line
                        // Note: refusedAmount in l might include both priceRefused and limitRefused
                        const lPriceRefused = Math.max(0, (parseFloat(l.total) || 0) - lEffectiveTotal);
                        const lLimitRefused = Math.max(0, (parseFloat(l.refusedAmount) || 0) - lPriceRefused);
                        
                        const lApprovedAmount = Math.max(0, lEffectiveTotal - lLimitRefused);
                        const lApprovedQty = lEffectiveTotal > 0 ? (lApprovedAmount / lEffectivePrice) : 0;
                        
                        batchUsedTimes += lApprovedQty;
                        batchUsedAmount += lApprovedAmount;
                    }
                });
            }

            totalUsedCount = (usage?.usedCount || 0) + batchUsedTimes;
            totalUsedAmount = (usage?.usedAmount || 0) + batchUsedAmount;

            if (usage.timesLimit > 0) {
                const remainingQty = Math.max(0, usage.timesLimit - totalUsedCount);
                if (qty > remainingQty) {
                    const refusedQty = qty - remainingQty;
                    // Refuse the price for the exceeded quantity
                    limitRefused = parseFloat((refusedQty * effectivePrice).toFixed(2));
                    usageExceeded = true;
                }
            }
            
            if (usage.amountLimit > 0) {
                // Adjust remaining based on what we already refused from timesLimit (to avoid double deduction if both exist)
                const currentApprovedBeforeAmount = effectiveTotal - limitRefused;
                const remainingAmount = Math.max(0, usage.amountLimit - totalUsedAmount);
                
                if (currentApprovedBeforeAmount > remainingAmount) {
                    const extraRefused = currentApprovedBeforeAmount - remainingAmount;
                    limitRefused += parseFloat(extraRefused.toFixed(2));
                    usageExceeded = true;
                }
            }
        }

        const approvedTotalForCoverage = Math.max(0, effectiveTotal - limitRefused);

        let byCompany, byEmployee;
        const defaultCov = policyInfo?.defaultCoveragePercent ?? 100;
        const cov = (line.coveragePercent !== null && line.coveragePercent !== undefined) ? line.coveragePercent : defaultCov;

        if (applyBenefits) {
            byCompany = parseFloat((approvedTotalForCoverage * cov / 100).toFixed(2));
            // التوزيع من المبلغ الصافي المعتمد (بعد خصم المرفوض) لا من كامل مبلغ العقد
            byEmployee = parseFloat((approvedTotalForCoverage - byCompany).toFixed(2));
        } else {
            byEmployee = Math.max(0, parseFloat(line.byEmployee) || 0);
            byCompany = parseFloat(Math.max(0, approvedTotalForCoverage - byEmployee).toFixed(2));
        }

        return {
            ...line,
            total,
            byCompany,
            byEmployee,
            refusedAmount: parseFloat((priceRefused + limitRefused).toFixed(2)),
            usageExceeded: usageExceeded || (usage && usage.exceeded),
            usageExhausted: limitRefused >= effectiveTotal && effectiveTotal > 0,
            usageDetails: usage ? {
                ...usage,
                totalUsedCount,
                totalUsedAmount,
                remainingAmount: usage.amountLimit > 0 ? Math.max(0, usage.amountLimit - totalUsedAmount - approvedTotalForCoverage) : null
            } : null
        };
    }, [applyBenefits, policyInfo?.defaultCoveragePercent]);

    return { recompute };
}
