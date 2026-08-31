import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const entrySource = readFileSync('src/pages/claims/batches/ClaimBatchEntry.jsx', 'utf8');
const lineSource = readFileSync('src/pages/claims/batches/components/ClaimLineRow.jsx', 'utf8');
const headerSource = readFileSync('src/pages/claims/batches/components/ClaimHeaderFields.jsx', 'utf8');

describe('claim batch entry safety boundary', () => {
  it('does not let the browser construct an approved claim', () => {
    expect(entrySource).not.toMatch(/status:\s*effectivelyRejected\s*\?\s*['"]REJECTED['"]\s*:\s*['"]APPROVED['"]/);
  });

  it('does not guess the first day of the batch as the service date', () => {
    expect(entrySource).toContain("const defaultDate = '';");
    expect(entrySource).not.toMatch(/const defaultDate\s*=\s*useMemo/);
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
    const dateChange = headerSource.match(/value=\{serviceDate \? dayjs\(serviceDate\) : null\}[\s\S]*?onChange=\{\(value\) => \{([\s\S]*?)setServiceDate/)?.[1];

    expect(memberChange).toContain("setPreAuthId('')");
    expect(dateChange).toContain("setPreAuthId('')");
  });

  it('blocks rather than merely warns about services from another claim context', () => {
    expect(entrySource).toContain('if (incompatibleContextLines.length > 0)');
    expect(entrySource).toContain('لا يمكن الحفظ: الخدمات في البنود');
    expect(entrySource).not.toContain('وسيتم احتسابها حسب قواعد التغطية المطابقة فقط');
  });

  it('does not fetch employer details or a second approval context that this screen does not consume', () => {
    expect(entrySource).not.toContain('employersService.getById');
    expect(entrySource).not.toContain("['eligible-claim-preauths'");
  });
});
