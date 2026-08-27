import { describe, expect, it } from 'vitest';
import { getMemberCapabilities } from '../memberCapabilities';

describe('member action capabilities', () => {
  const allPermissions = [
    'MEMBER_CREATE', 'MEMBER_EDIT_IDENTITY', 'MEMBER_CHANGE_STATUS',
    'MEMBER_TRANSFER_EMPLOYER', 'MEMBER_REINSTATE_TERMINATED', 'MEMBER_HARD_DELETE',
    'MEMBER_IMPORT', 'MEMBER_EXPORT'
  ];

  it('derives every operation from the effective permission snapshot', () => {
    expect(Object.values(getMemberCapabilities({ permissions: allPermissions }))).not.toContain(false);
  });

  it('does not grant operations from a role name alone', () => {
    expect(Object.values(getMemberCapabilities({ role: 'SUPER_ADMIN' }))).not.toContain(true);
  });

  it('honours explicit revocation even when the legacy role would have allowed the action', () => {
    const capabilities = getMemberCapabilities({
      role: 'EMPLOYER_ADMIN',
      permissions: ['MEMBER_CREATE', 'MEMBER_EDIT_IDENTITY', 'MEMBER_EXPORT']
    });
    expect(capabilities.lifecycle).toBe(false);
    expect(capabilities.bulkTerminate).toBe(false);
    expect(capabilities.export).toBe(true);
  });

  it('allows a delegated permission independently of the role template', () => {
    const capabilities = getMemberCapabilities({ role: 'DATA_ENTRY', permissions: ['MEMBER_CHANGE_STATUS'] });
    expect(capabilities.lifecycle).toBe(true);
    expect(capabilities.bulkTerminate).toBe(true);
    expect(capabilities.create).toBe(false);
  });
});
