import { describe, expect, it } from 'vitest';
import { getMemberCapabilities } from '../memberCapabilities';

describe('member action capabilities', () => {
  it('gives SUPER_ADMIN every member operation', () => {
    expect(Object.values(getMemberCapabilities({ userType: 'SUPER_ADMIN' }))).not.toContain(false);
  });

  it('allows EMPLOYER_ADMIN scoped administration but not import or hard delete', () => {
    expect(getMemberCapabilities({ role: 'EMPLOYER_ADMIN' })).toEqual({
      create: true, edit: true, lifecycle: true, hardDelete: false,
      bulkTerminate: true, import: false, export: true
    });
  });

  it('limits DATA_ENTRY to identity entry and import', () => {
    expect(getMemberCapabilities({ roles: [{ name: 'DATA_ENTRY' }] })).toEqual({
      create: true, edit: true, lifecycle: false, hardDelete: false,
      bulkTerminate: false, import: true, export: false
    });
  });

  it.each(['PROVIDER_STAFF', 'MEDICAL_REVIEWER', 'ACCOUNTANT', ''])('keeps %s read-only', (role) => {
    expect(Object.values(getMemberCapabilities({ userType: role }))).not.toContain(true);
  });
});
