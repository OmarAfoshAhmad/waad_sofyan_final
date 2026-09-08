import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const entrySource = readFileSync('src/pages/claims/batches/ClaimBatchEntry.jsx', 'utf8');
const lineSource = readFileSync('src/pages/claims/batches/components/ClaimLineRow.jsx', 'utf8');
const footerSource = readFileSync('src/pages/claims/batches/components/ClaimTotalsFooter.jsx', 'utf8');
const headerSource = readFileSync('src/pages/claims/batches/components/ClaimHeaderFields.jsx', 'utf8');
const customServiceDialogSource = readFileSync('src/pages/claims/batches/components/CustomServiceDialog.jsx', 'utf8');
const detailSource = readFileSync('src/pages/claims/batches/ClaimBatchDetail.jsx', 'utf8');
const authSource = readFileSync('src/contexts/AuthContext.jsx', 'utf8');

describe('claim batch entry safety boundary', () => {
  it('loads dated contract services with the selected member identity, never an undefined alias', () => {
    expect(entrySource).toContain('memberId: member.id');
    expect(entrySource).toContain('providerId,');
    expect(entrySource).toContain('employerId,');
    expect(entrySource).not.toContain('selectedMemberId');
    expect(entrySource).not.toContain('const generalOptions =');
    expect(entrySource).toContain('q: debouncedServiceSearch || undefined');
    expect(lineSource).toContain('onServiceSearchChange?.(value)');
  });

  it('does not let the browser construct an approved claim', () => {
    expect(entrySource).not.toMatch(/status:\s*effectivelyRejected\s*\?\s*['"]REJECTED['"]\s*:\s*['"]APPROVED['"]/);
    expect(entrySource).toContain("saveMode === 'draft' ? 'DRAFT' : 'SUBMITTED'");
    expect(entrySource).not.toContain("status: effectivelyRejected ? 'REJECTED' : null");
    expect(footerSource).toContain("handleSave(false, 'draft')");
    expect(footerSource).toContain("handleSave(true, 'submit')");
    expect(footerSource).toContain('حفظ كمسودة');
    expect(footerSource).toContain('إرسال');
  });

  it('does not let a closed claim fail late with a backend status error', () => {
    expect(entrySource).toContain("!['DRAFT', 'NEEDS_CORRECTION'].includes(editingClaim.status)");
    expect(entrySource).toContain('ولا يمكن تعديلها من شاشة الإدخال');
  });

  it('never labels a claim with refused money as partially approved in batch details', () => {
    expect(detailSource).not.toContain('معتمدة جزئ');
    expect(detailSource).toContain('getDisplayRefused(claim)');
    expect(detailSource).toContain("label: 'مرفوضة'");
  });

  it('shows provider refusal balance after beneficiary payment in batch details', () => {
    expect(detailSource).toContain('providerRefusalBalance');
    expect(detailSource).toContain('beneficiaryPaidTowardRefusal');
    expect(detailSource).toContain("label: 'على مقدم الخدمة'");
    expect(detailSource).toContain('getReviewerDisplayStatus(claim)');
  });

  it('keeps provider contract discount out of the medical reviewer batch table', () => {
    expect(detailSource).not.toContain('المستحق للمقدم');
    expect(detailSource).not.toContain("id: 'discountPercent'");
    expect(detailSource).not.toContain('getDiscountPercent');
    expect(detailSource).not.toContain("{ id: 'dueAfterRefused'");
    expect(detailSource).not.toContain("label: 'المعتمد'");
    expect(detailSource).toContain("{ id: 'beneficiaryPaid', label: 'مدفوع المستفيد'");
    expect(detailSource).toContain("{ id: 'covered', label: 'التزام الشركة'");
    expect(detailSource).toContain('getInsurerCommitment(claim).toFixed(2)');
  });

  it('does not show noisy draft conflict synchronization snackbars', () => {
    expect(entrySource).not.toContain('تمت مزامنة المسودة بعد تعارض بسيط');
  });

  it('submits only an explicit manual refusal and never reposts the calculated aggregate refusal', () => {
    expect(entrySource).toContain('manualRefusedAmount: isClaimRejected ? 0 : parseFloat(l.manualRefusedAmount) || 0');
    expect(entrySource).not.toContain('refusedAmount: parseFloat(l.refusedAmount)');
  });

  it('does not guess the first day of the batch as the service date', () => {
    expect(entrySource).toContain("const defaultDate = '';");
    expect(entrySource).not.toMatch(/const defaultDate\s*=\s*useMemo/);
  });

  it('keeps diagnosis required, doctor optional, and offers a spacious service-entry mode', () => {
    expect(entrySource).toContain("if (!diagnosis?.trim()) missingFields.push('التشخيص الطبي')");
    expect(entrySource).not.toMatch(/if\s*\(!doctorName/);
    expect(entrySource).toContain('توسيع مساحة إدخال البنود');
    expect(entrySource).toContain('<Collapse in={headerExpanded}');
  });

  it('does not reject a service date just because it differs from the accounting batch month', () => {
    expect(entrySource).not.toContain('لا يتبع لشهر الدفعة الحالي');
    expect(entrySource).not.toContain('d.getMonth() + 1 !== month');
    expect(entrySource).not.toContain('d.getFullYear() !== year');
  });

  it('does not display a policy default as a calculated line coverage', () => {
    expect(lineSource).not.toContain('policyInfo?.defaultCoveragePercent ?? 100');
    expect(lineSource).toContain('بانتظار الحساب');
  });

  it('does not mix the undated current balance into dated claim entry', () => {
    expect(entrySource).not.toContain('unifiedMembersService.getFinancialSummary');
    expect(entrySource).toContain('reservableAvailable: entryContext.reservableAvailable');
  });

  it('creates the visit and claim through one atomic backend command', () => {
    expect(entrySource).toContain('claimsService.createDirectEntry');
    expect(entrySource).not.toContain('visitsService.create');
    expect(entrySource).not.toContain('visitsService.remove');
  });

  it('persists and reuses one direct-entry command key across response loss and draft recovery', () => {
    expect(entrySource).toContain('directEntryKey');
    expect(entrySource).toContain('setDirectEntryKey(payload.directEntryKey || newDirectEntryKey())');
    expect(entrySource).toContain('claimsService.createDirectEntry(parseInt(employerId), claimData, directEntryKey)');
  });

  it('offers only server-qualified pre-authorizations for the dated claim context', () => {
    expect(entrySource).toContain('entryContext?.eligiblePreAuthorizations');
    expect(entrySource).not.toContain('claimsService.getEligiblePreAuthorizations');
    expect(entrySource).not.toContain('preApprovalsService.search');
  });

  it('clears a dated pre-authorization when its member or service date changes', () => {
    const memberChange = headerSource.match(/value=\{member\}[\s\S]*?onChange=\{\(_, v\) => \{([\s\S]*?)setMember\(v\)/)?.[1];
    const dateChange = headerSource.match(
      /value=\{serviceDate \? dayjs\(serviceDate\) : null\}[\s\S]*?onChange=\{\(value\) => \{([\s\S]*?)setServiceDate/
    )?.[1];

    expect(memberChange).toContain("setPreAuthId('')");
    expect(dateChange).toContain("setPreAuthId('')");
  });

  it('does not use the selected claim context as a frontend service filter', () => {
    expect(entrySource).toContain('السياق المالي يُطبّق على المطالبة كاملة');
    expect(entrySource).not.toContain('incompatibleContextLines');
    expect(entrySource).not.toContain('لا تتوافق مع سياق المطالبة الحالي');
    expect(entrySource).not.toContain('isServiceAllowedForClaimContext');
  });

  it('recalculates all draft lines when a service is selected so shared limits are consumed once', () => {
    expect(entrySource).toContain('const currentLines = linesRef.current || lines');
    expect(entrySource).toContain('const currentLine = currentLines[idx] || {}');
    expect(entrySource).toContain(
      'const nextLines = currentLines.map((line, lineIdx) => (lineIdx === idx ? { ...currentLine, ...nextPatch } : line))'
    );
    expect(entrySource).not.toContain('const committedLines = linesRef.current?.length ? linesRef.current : nextLines');
    expect(entrySource).toContain('refetchAllLinesCoverage(encounterType, nextLines, fullCoverage, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(coverageInput, encounterType, null, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(svc, encounterType, null, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(svc, encounterType);');
    expect(entrySource).toContain('refetchCoverageOnEditRef.current(encounterType, fullCoverage, claimContextCode)');
    expect(entrySource).not.toContain('refetchCoverageOnEditRef.current(encounterType, fullCoverage)');
    expect(entrySource).not.toContain('refetchCoverageOnEditRef.current(encounterType)');
  });

  it('does not invent a one-occurrence usage message when a quantity-based times limit rejects the line', () => {
    expect(lineSource).toContain('تعذّر قبول البند لأن عدد المرات المطلوبة يتجاوز الحد');
    expect(lineSource).not.toContain('(line.usageDetails.totalUsedCount || 0) + 1');
  });

  it('labels a fully refused financial amount as full rather than partial', () => {
    expect(lineSource).toContain("effectiveFinancialRefusal >= lineTotal ? 'رفض مالي كامل' : 'رفض جزئي'");
    expect(lineSource).not.toContain('خصم/رفض جزئي');
  });

  it('does not fetch employer details or a second approval context that this screen does not consume', () => {
    expect(entrySource).not.toContain('employersService.getById');
    expect(entrySource).not.toContain("['eligible-claim-preauths'");
  });

  it('debounces and cancels dated coverage checks and does not render a persistent readiness banner', () => {
    expect(entrySource).toContain('setTimeout(() => setDebouncedServiceDate(serviceDate), 450)');
    expect(entrySource).toContain('queryFn: ({ signal }) =>');
    expect(entrySource).toContain('signal');
    expect(entrySource).toContain('preventDuplicate: true');
    expect(entrySource).not.toContain('<ClaimEntryReadinessAlert');
  });

  it('declares serviceDate before any effect reads it during the first render', () => {
    const serviceDateState = entrySource.indexOf('const [serviceDate, setServiceDate] = useState(initialServiceDate || defaultDate);');
    const serviceDateDebounce = entrySource.indexOf('setTimeout(() => setDebouncedServiceDate(serviceDate), 450)');

    expect(serviceDateState).toBeGreaterThan(-1);
    expect(serviceDateDebounce).toBeGreaterThan(-1);
    expect(serviceDateState).toBeLessThan(serviceDateDebounce);
  });

  it('explains when a valid dated contract has no effective service prices', () => {
    expect(entrySource).toContain('noEffectiveContractServicesForDate');
    expect(entrySource).toContain('لا توجد أسعار خدمات فعالة في العقد بتاريخ الخدمة');
  });

  /**
   * Adding a missing service from claim entry must attach it to the active
   * provider contract so it appears for that provider later, but without an
   * enforceable fixed price. The clerk enters a unit price per claim and
   * quantity remains editable.
   */
  it('adds a custom service to the active provider contract with open per-claim pricing', () => {
    expect(entrySource).toContain('لا يوجد عقد مقدم خدمة فعّال لإضافة هذه الخدمة إليه بتاريخ المطالبة');
    expect(entrySource).toContain('/provider-contracts/${entryContext.contractId}/pricing');
    expect(entrySource).not.toContain("axiosClient.post('/provider-standard-services', payload)");
    expect(entrySource).toContain("pricingMode: 'CLAIM_UNIT_PRICE'");
    expect(entrySource).toContain('basePrice: 0');
    expect(entrySource).toContain('contractPrice: 0');
    expect(entrySource).toContain('pricingItemId: createdService.id');
  });

  /**
   * The cache key invalidated after adding a custom service must be a real
   * prefix of the key the search query itself uses -- otherwise the clerk who
   * just added a service and searches again is shown stale results and does
   * not see it. 'contracted-services' matched nothing this file ever queries
   * with; the real key starts with 'claim-entry-contract-services'.
   */
  it('invalidates the query key the service search actually uses', () => {
    expect(entrySource).toContain("queryKey: ['claim-entry-contract-services'");
    expect(entrySource).not.toContain("queryKey: ['contracted-services'");
  });

  /**
   * A standard (pharmacy/optics-style) service has no contract price list --
   * selecting one must fix quantity at 1, clear any pricingItemId, and send
   * the entered amount as manualAmount rather than trusting the generic
   * unitPrice field a contract-priced line also uses.
   */
  it('locks quantity to 1 and clears the pricing item when a manual-amount service is selected', () => {
    expect(entrySource).toContain("const isManualAmount = svc.pricingMode === 'MANUAL_AMOUNT'");
    expect(entrySource).toContain("const isClaimUnitPrice = svc.pricingMode === 'CLAIM_UNIT_PRICE'");
    expect(entrySource).toContain('pricingItemId: isManualAmount ? null : svc.pricingItemId || null');
    expect(entrySource).toContain('quantity: isManualAmount ? 1 : currentLine.quantity || 1');
  });

  it('does not reinterpret a manual-amount service id as a provider pricing item id', () => {
    expect(entrySource).toContain("const isManualAmount = s.pricingMode === 'MANUAL_AMOUNT'");
    expect(entrySource).toContain('medicalServiceId: s.medicalServiceId ?? s.serviceId ?? (isManualAmount ? s.id : null)');
    expect(entrySource).toContain('pricingItemId: isManualAmount ? null : (s.pricingItemId ?? s.id)');
  });

  it('submits the entered amount as manualAmount for a manual-amount line, not as a contract unitPrice', () => {
    expect(entrySource).toContain("const isManualAmountLine = (l.pricingMode || l.service?.pricingMode) === 'MANUAL_AMOUNT'");
    expect(entrySource).toContain("const isClaimUnitPriceLine = (l.pricingMode || l.service?.pricingMode) === 'CLAIM_UNIT_PRICE'");
    expect(entrySource).toContain('manualAmount: isManualAmountLine ? parseFloat(l.unitPrice) || 0 : null');
    expect(entrySource).toContain('medicalServiceId: l.medicalServiceId || l.service?.medicalServiceId || l.service?.serviceId || null');
    expect(entrySource).toContain('pricingItemId: isManualAmountLine ? null :');
  });

  it('calculates coverage for claim-created price-free unit services with editable quantity', () => {
    expect(entrySource).toContain('await handleServiceChange(selectedLineIndex, newServiceObject)');
    expect(entrySource).toContain("pricingMode: 'CLAIM_UNIT_PRICE'");
    expect(entrySource).toContain('isClaimUnitPrice ? (svc.price || currentLine.unitPrice || 0) : price');
    expect(entrySource).toContain('if (!isFreeText && policyId && member?.id)');
    expect(entrySource).toContain('refetchAllLinesCoverage(encounterType, nextLines, fullCoverage, claimContextCode)');
  });

  it('does not block an invoice/manual line as uncovered before the invoice amount is entered', () => {
    expect(entrySource).toContain('const hasAmountForCoverage = Number(line.unitPrice || 0) > 0 && Number(line.quantity || 0) > 0');
    expect(entrySource).toContain('hasAmountForCoverage &&');
    expect(lineSource).toContain('hasAmountForCoverage');
  });

  it('shows an invoice-amount field instead of the contract-bounds-checked price for a manual-amount line', () => {
    expect(lineSource).toContain("const isManualAmount = (line.pricingMode || line.service?.pricingMode) === 'MANUAL_AMOUNT'");
    expect(lineSource).toContain('label="قيمة الفاتورة"');
    expect(lineSource).toContain('disabled={isManualAmount}');
  });

  it('shows Arabic claim context terms in the general-service dialog, not technical codes', () => {
    expect(customServiceDialogSource).toContain("OUTPATIENT: 'عيادات خارجية'");
    expect(customServiceDialogSource).toContain("INPATIENT: 'إيواء'");
    expect(customServiceDialogSource).toContain("MATERNITY: 'ولادة'");
    expect(customServiceDialogSource).toContain('claimContextLabel(claimContextCode)');
    expect(customServiceDialogSource).toContain('لن يُحفظ كسعر ثابت');
  });

  it('does not expose generated catalog codes in the visible service label', () => {
    expect(entrySource).toContain('GENERATED_SERVICE_CODE_PATTERN = /^(PL-|SYS-)/i');
    expect(entrySource).toContain('buildServiceDisplayLabel({ code, name })');
    expect(entrySource).toContain('buildServiceDisplayLabel({ code: finalServiceCode, name: payload.serviceName })');
    expect(entrySource).not.toContain("label: `${code ? '[' + code + '] ' : ''}${name}`");
    expect(entrySource).not.toContain("label: `[${finalServiceCode}] ${payload.nameAr}`");
  });

  it('keeps service search matching the facility code even when the visible label is cleaned', () => {
    expect(lineSource).toContain('opt.serviceCode || opt.code ||');
    expect(lineSource).toContain('opt.serviceName || opt.name ||');
  });

  it('blocks saving when beneficiary payment exceeds copay plus refusal', () => {
    expect(entrySource).toContain('beneficiarySettlement.excessPayment > 0');
    expect(entrySource).toContain('المبلغ المدفوع من المستفيد أكبر من التزامه والمبلغ المرفوض');
    expect(footerSource).toContain('Boolean(saveDisabledReason)');
  });

  it('keeps the browser inactivity session aligned with the 24-hour backend session and clears claim drafts on logout', () => {
    expect(authSource).toContain('const TIMEOUT_MS = 24 * 60 * 60 * 1000');
    expect(authSource).toContain("key.startsWith('claim-draft:')");
    expect(authSource).toContain('clearClaimDraftStorage();');
  });
});
