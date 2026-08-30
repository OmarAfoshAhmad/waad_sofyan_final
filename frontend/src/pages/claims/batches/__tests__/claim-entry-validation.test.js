import { describe, expect, it } from 'vitest';

import { invalidQuantityLineNumbers, isValidClaimQuantity } from '../claim-entry-validation';

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
