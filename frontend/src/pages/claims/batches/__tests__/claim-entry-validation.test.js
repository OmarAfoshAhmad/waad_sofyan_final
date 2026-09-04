import { describe, expect, it } from 'vitest';

import { invalidQuantityLineNumbers, isValidClaimQuantity, lineAcceptedAmount } from '../claim-entry-validation';

describe('claim entry quantities', () => {
  it('accepts only positive whole quantities', () => {
    expect(isValidClaimQuantity(1)).toBe(true);
    expect(isValidClaimQuantity('3')).toBe(true);
    expect(isValidClaimQuantity(0)).toBe(false);
    expect(isValidClaimQuantity('')).toBe(false);
    expect(isValidClaimQuantity('1.5')).toBe(false);
    expect(isValidClaimQuantity(-1)).toBe(false);
  });

  it('names only selected service rows whose quantities are invalid', () => {
    expect(
      invalidQuantityLineNumbers([
        { serviceName: 'أ', quantity: 0 },
        { quantity: '' },
        { service: { id: 2 }, quantity: 2 },
        { serviceName: 'د', quantity: 1.5 }
      ])
    ).toEqual([1, 4]);
  });
});

describe('lineAcceptedAmount', () => {
  it('is the full total when nothing was refused', () => {
    expect(lineAcceptedAmount({ total: 300, refusedAmount: 0 })).toBe(300);
  });

  it('subtracts refusedAmount when it is the larger reading', () => {
    expect(lineAcceptedAmount({ total: 300, refusedAmount: 120, priceRefused: 0, limitRefused: 0 })).toBe(180);
  });

  it('subtracts priceRefused + limitRefused when that combined figure is larger than refusedAmount', () => {
    // Mirrors ClaimLineRow's own "effectiveFinancialRefusal" reading -- the
    // two figures can disagree (e.g. refusedAmount stale mid-recalculation),
    // and the larger one is always the honest one to subtract.
    expect(lineAcceptedAmount({ total: 300, refusedAmount: 50, priceRefused: 80, limitRefused: 40 })).toBe(180);
  });

  it('never goes negative when refused exceeds total', () => {
    expect(lineAcceptedAmount({ total: 100, refusedAmount: 150 })).toBe(0);
  });

  it('treats a fully rejected line (refusedAmount === total) as zero accepted', () => {
    expect(lineAcceptedAmount({ total: 300, refusedAmount: 300 })).toBe(0);
  });

  it('handles missing/undefined fields as zero, not NaN', () => {
    expect(lineAcceptedAmount({})).toBe(0);
    expect(lineAcceptedAmount({ total: 250 })).toBe(250);
  });
});
