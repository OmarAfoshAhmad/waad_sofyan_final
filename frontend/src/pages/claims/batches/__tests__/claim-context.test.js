import { describe, expect, it } from 'vitest';
import { isServiceAllowedForClaimContext, normalizeClaimServiceContext, resolveClaimContextSelection } from '../claim-context.mjs';

describe('claim context', () => {
  it('accepts a future data-driven context code without a frontend release', () => {
    expect(normalizeClaimServiceContext('emergency_dental')).toBe('EMERGENCY_DENTAL');
  });

  it('shows every contracted item when outpatient is cleared to ANY', () => {
    for (const encounterType of ['OUTPATIENT', 'INPATIENT', 'MATERNITY', 'PREGNANCY_COMPLICATIONS', 'ANY']) {
      expect(isServiceAllowedForClaimContext({ encounterType }, 'ANY')).toBe(true);
    }
  });

  it('treats pharmacy as a benefit classification, not a claim-header context', () => {
    expect(isServiceAllowedForClaimContext({ encounterType: 'OUTPATIENT' }, 'OUTPATIENT')).toBe(true);
    expect(isServiceAllowedForClaimContext({ encounterType: 'INPATIENT' }, 'OUTPATIENT')).toBe(false);
  });

  it.each([
    ['OUTPATIENT', 'OUTPATIENT', false],
    ['INPATIENT', 'INPATIENT', false],
    ['FULL_COVERAGE', 'ANY', true],
    ['MATERNITY', 'INPATIENT', false],
    ['PREGNANCY_COMPLICATIONS', 'INPATIENT', false]
  ])('maps %s to its engine context without sending the business code as EncounterType', (code, base, fullCoverage) => {
    const contexts = [
      { code, baseEncounterType: base, nameAr: code }
    ];
    expect(resolveClaimContextSelection(contexts, code)).toEqual({
      claimContextCode: code,
      encounterType: base,
      fullCoverage
    });
  });
});
