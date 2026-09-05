import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const entrySource = readFileSync('src/pages/claims/batches/ClaimBatchEntry.jsx', 'utf8');
const lineSource = readFileSync('src/pages/claims/batches/components/ClaimLineRow.jsx', 'utf8');
const headerSource = readFileSync('src/pages/claims/batches/components/ClaimHeaderFields.jsx', 'utf8');

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

  it('blocks rather than merely warns about services from another claim context', () => {
    expect(entrySource).toContain('if (incompatibleContextLines.length > 0)');
    expect(entrySource).toContain('!hasAcceptedCoverageDecision(line)');
    expect(entrySource).toContain('لا يمكن الحفظ: الخدمات في البنود');
    expect(entrySource).not.toContain('وسيتم احتسابها حسب قواعد التغطية المطابقة فقط');
  });

  it('recalculates all draft lines when a service is selected so shared limits are consumed once', () => {
    expect(entrySource).toContain('const currentLines = linesRef.current || lines');
    expect(entrySource).toContain('const currentLine = currentLines[idx] || {}');
    expect(entrySource).toContain(
      'const nextLines = currentLines.map((line, lineIdx) => (lineIdx === idx ? { ...currentLine, ...nextPatch } : line))'
    );
    expect(entrySource).toContain('const committedLines = linesRef.current?.length ? linesRef.current : nextLines');
    expect(entrySource).toContain('refetchAllLinesCoverage(encounterType, committedLines, fullCoverage, claimContextCode)');
    expect(entrySource).not.toContain('refetchAllLinesCoverage(encounterType, nextLines, fullCoverage, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(coverageInput, encounterType, null, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(svc, encounterType, null, claimContextCode)');
    expect(entrySource).not.toContain('fetchCoverage(svc, encounterType);');
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
    const serviceDateState = entrySource.indexOf('const [serviceDate, setServiceDate] = useState(defaultDate);');
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
   * "Add a new service" used to post to /provider/my-contract/pricing, a
   * provider-portal endpoint retired behind @PreAuthorize("denyAll()") on the
   * backend -- every attempt failed for every role, always. The canonical
   * internal path is POST /provider-contracts/{contractId}/pricing, the same
   * ProviderContractPricingItemService the import screens and the contract's
   * own pricing tab already write through, authorized for staff managing the
   * contract rather than a provider acting for itself. It is keyed by the
   * dated contract this screen already resolved, not by providerId alone --
   * a provider can hold more than one contract over time, and the retired
   * endpoint's own guess at "the" active one was part of what made it wrong
   * for this screen even before it was denied outright.
   */
  it('adds a custom service through the contract this screen resolved, not the retired provider-portal path', () => {
    expect(entrySource).not.toContain('/provider/my-contract/pricing');
    expect(entrySource).toContain('/provider-contracts/${entryContext.contractId}/pricing');
    expect(entrySource).toContain("setCustomServiceError('لا يمكن إضافة خدمة قبل التحقق من العقد الفعّال لهذا المستفيد وتاريخ الخدمة')");
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
    expect(entrySource).toContain('pricingItemId: isManualAmount ? null : svc.pricingItemId || null');
    expect(entrySource).toContain('quantity: isManualAmount ? 1 : currentLine.quantity || 1');
  });

  it('submits the entered amount as manualAmount for a manual-amount line, not as a contract unitPrice', () => {
    expect(entrySource).toContain("manualAmount: (l.pricingMode || l.service?.pricingMode) === 'MANUAL_AMOUNT'");
  });

  it('shows an invoice-amount field instead of the contract-bounds-checked price for a manual-amount line', () => {
    expect(lineSource).toContain("const isManualAmount = (line.pricingMode || line.service?.pricingMode) === 'MANUAL_AMOUNT'");
    expect(lineSource).toContain('label="قيمة الفاتورة"');
    expect(lineSource).toContain('disabled={isManualAmount}');
  });
});
