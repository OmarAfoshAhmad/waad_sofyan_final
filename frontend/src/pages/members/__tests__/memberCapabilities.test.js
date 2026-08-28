import { describe, expect, it } from 'vitest';
import { getMemberCapabilities, MEMBER_CAPABILITY_PERMISSIONS } from '../memberCapabilities';

describe('member action capabilities', () => {
  // Derived, not typed out. A hand-written list fell behind twice -- once for
  // MEMBER_LIMIT_VIEW and once for MEMBER_LIMIT_LIST_VIEW -- and each time the
  // failure looked like the new capability was broken rather than the fixture
  // being stale.
  const allPermissions = Object.values(MEMBER_CAPABILITY_PERMISSIONS);

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
