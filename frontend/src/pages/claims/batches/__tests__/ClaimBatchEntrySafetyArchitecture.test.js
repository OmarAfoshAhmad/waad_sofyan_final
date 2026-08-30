import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const entrySource = readFileSync('src/pages/claims/batches/ClaimBatchEntry.jsx', 'utf8');
const lineSource = readFileSync('src/pages/claims/batches/components/ClaimLineRow.jsx', 'utf8');

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

  it('offers only server-qualified pre-authorizations for the dated claim context', () => {
    expect(entrySource).toContain('claimsService.getEligiblePreAuthorizations');
    expect(entrySource).not.toContain('preApprovalsService.search');
  });
});
