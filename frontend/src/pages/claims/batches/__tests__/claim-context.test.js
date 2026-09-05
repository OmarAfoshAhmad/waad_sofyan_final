import { describe, expect, it } from 'vitest';
import { getServiceContext, normalizeClaimServiceContext, resolveClaimContextSelection } from '../claim-context.mjs';

describe('claim context', () => {
  it('accepts a future data-driven context code without a frontend release', () => {
    expect(normalizeClaimServiceContext('emergency_dental')).toBe('EMERGENCY_DENTAL');
  });

  it('keeps service encounter as catalog metadata, not as a frontend eligibility filter', () => {
    expect(getServiceContext({ encounterType: 'OUTPATIENT' })).toBe('OUTPATIENT');
    expect(getServiceContext({ encounterType: 'INPATIENT' })).toBe('INPATIENT');
    expect(getServiceContext({ encounterType: 'PREGNANCY_COMPLICATIONS' })).toBe('PREGNANCY_COMPLICATIONS');
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
