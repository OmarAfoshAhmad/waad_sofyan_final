import { useCallback } from 'react';

/**
 * UI projection only. Insurance amounts are authoritative only when returned by
 * the backend coverage engine (coveragePending === false).
 */
export function useCalculationLogic() {
  const recompute = useCallback((line) => {
    if (!line) return line;

    const quantity = Math.max(1, Number.parseInt(line.quantity, 10) || 1);
    const unitPrice = Math.max(0, Number.parseFloat(line.unitPrice) || 0);
    const total = Number((quantity * unitPrice).toFixed(2));

    if (line.coveragePending === false) {
      return { ...line, total };
    }

    return {
      ...line,
      total,
      coveragePercent: 0,
      notCovered: true,
      coveragePending: true,
      byCompany: 0,
      byEmployee: 0,
      refusedAmount: 0,
      priceRefused: 0,
      limitRefused: 0,
      systemRefusedAmount: 0,
      rejectionReason: line.rejectionReason || 'بانتظار قرار محرك التغطية'
    };
  }, []);

  return { recompute };
}
