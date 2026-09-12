import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import menuItems, { filterMenuItemsByRole } from '../components';

const flatten = (items) => items.flatMap((item) => [item, ...(item.children ? flatten(item.children) : [])]);
const ids = (items) => new Set(flatten(items).map((item) => item.id));
const leaves = flatten(menuItems).filter((item) => item.url);

/**
 * Every permission a menu item requires must be one the server defines. The
 * enum is read as text so a renamed or removed SystemPermission fails here
 * before it ships a menu that can never be satisfied.
 */
const serverPermissions = (() => {
  const source = fs.readFileSync(
    path.resolve(__dirname, '../../../../backend/src/main/java/com/waad/tba/modules/rbac/permission/SystemPermission.java'),
    'utf8'
  );
  return new Set([...source.matchAll(/^\s{4}([A-Z][A-Z0-9_]+)\s*\(/gm)].map((m) => m[1]));
})();

/**
 * The menu used to be decided by a frozen role -> resource map while the
 * server decided by role defaults ± per-user overrides. A permission granted
 * in RBAC changed nothing on screen; one revoked left the entry in place.
 * For every item whose server endpoint checks a permission, visibility now
 * follows user.permissions from /session/me and nothing else.
 */
describe('menu follows the server permission set', () => {
  it('declares only permissions the server actually defines', () => {
    const declared = leaves.flatMap((item) => [
      ...(item.requiredPermission ? [item.requiredPermission] : []),
      ...(item.requiredPermissions || [])
    ]);
    expect(declared.length).toBeGreaterThan(10);
    expect(declared.filter((p) => !serverPermissions.has(p))).toEqual([]);
  });

  it('reveals a permission-gated screen to a role that gained it by override', () => {
    // DATA_ENTRY's ROLE_RESOURCE_ACCESS row lists no settlement resource.
    const visible = ids(filterMenuItemsByRole(menuItems, 'DATA_ENTRY', false, true, ['MEMBER_VIEW', 'SETTLEMENT_VIEW']));
    expect(visible.has('provider-accounts')).toBe(true);
    expect(visible.has('payments-management')).toBe(true);
  });

  it('hides a permission-gated screen from a role that lost it by override', () => {
    // ACCOUNTANT's ROLE_RESOURCE_ACCESS row lists settlements and provider_accounts.
    const visible = ids(filterMenuItemsByRole(menuItems, 'ACCOUNTANT', false, true, ['FINANCIAL_REPORT_VIEW']));
    expect(visible.has('provider-accounts')).toBe(false);
    expect(visible.has('provider-payments')).toBe(false);
  });

  it('does not let the role map override a missing permission, even for SUPER_ADMIN', () => {
    const visible = ids(filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', false, true, []));
    expect(visible.has('members-list')).toBe(false);
    expect(visible.has('users-management')).toBe(false);
  });

  it('requires both permissions where the server requires both', () => {
    const one = ids(filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', false, true, ['SYSTEM_SETTINGS_VIEW']));
    const both = ids(filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', false, true, ['SYSTEM_SETTINGS_VIEW', 'DANGER_ZONE_EXECUTE']));
    expect(one.has('kinship-mismatch')).toBe(true);
    expect(one.has('member-duplicates')).toBe(false);
    expect(both.has('member-duplicates')).toBe(true);
  });

  it('leaves role-gated screens on the role map until their endpoints migrate', () => {
    // GET /benefit-policies is hasAnyRole(...) on the server: no permission to derive.
    const accountant = ids(filterMenuItemsByRole(menuItems, 'ACCOUNTANT', false, true, []));
    const dataEntry = ids(filterMenuItemsByRole(menuItems, 'DATA_ENTRY', false, true, ['BENEFIT_POLICY_VIEW']));
    expect(accountant.has('benefit-policies')).toBe(false); // narrower than server by product choice
    expect(dataEntry.has('benefit-policies')).toBe(false); // a permission cannot open a role-fenced screen
    expect(ids(filterMenuItemsByRole(menuItems, 'EMPLOYER_ADMIN', false, true, [])).has('benefit-policies')).toBe(true);
  });
});
