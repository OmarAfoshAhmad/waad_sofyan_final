const toMoney = (value) => {
  const num = Number(value);
  return Number.isFinite(num) ? Number(num.toFixed(2)) : 0;
};

/**
 * Financial preview contract.
 *
 * A missing/failed engine response must never silently become the policy's
 * default coverage. The user can retry, but the company share stays zero until
 * the backend returns an explicit decision.
 */
export function failedCoverageResult(message = 'تعذر حساب التغطية') {
  return {
    coveragePercent: 0,
    requiresPreApproval: false,
    notCovered: true,
    coveragePending: true,
    usageExceeded: false,
    usageDetails: null,
    total: 0,
    byCompany: 0,
    byEmployee: 0,
    refusedAmount: 0,
    priceRefused: 0,
    limitRefused: 0,
    systemRefusedAmount: 0,
    resolvedCategoryId: null,
    rejectionReason: message
  };
}

export function normalizeCoverageResult(result) {
  if (!result || result.coveragePercent == null || result.companyShare == null || result.patientShare == null) {
    return failedCoverageResult('نتيجة محرك التغطية ناقصة — أعد المحاولة قبل الحفظ');
  }

  return {
    coveragePercent: result.notCovered ? 0 : result.coveragePercent,
    requiresPreApproval: !!result.requiresPreApproval,
    notCovered: !!result.notCovered,
    coveragePending: false,
    usageExceeded: !!result.usageDetails?.exceeded,
    usageDetails: result.usageDetails ?? null,
    total: toMoney(result.requestedTotal),
    byCompany: toMoney(result.companyShare),
    byEmployee: toMoney(result.patientShare),
    refusedAmount: toMoney(result.refusedAmount),
    priceRefused: toMoney(result.priceRefused),
    limitRefused: toMoney(result.limitRefused),
    systemRefusedAmount: toMoney(result.systemRefusedAmount),
    resolvedCategoryId: result.resolvedCategoryId ?? null,
    rejectionReason: result.refusalReason || undefined
  };
}
