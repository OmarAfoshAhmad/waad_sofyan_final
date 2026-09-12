import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import menuItems, { filterMenuItemsByRole } from '../components';

const SRC = path.resolve(__dirname, '../..');
const flatten = (items) => items.flatMap((item) => [item, ...(item.children ? flatten(item.children) : [])]);
const leaves = flatten(menuItems).filter((item) => item.url);
const ids = (items) => new Set(flatten(items).map((item) => item.id));

/**
 * Menu visibility is derived from the server (decision 2026-09-12, option A
 * in docs/security/MENU_PERMISSION_DERIVATION.md). These tests guard the
 * three rules that make that true, so a later edit cannot quietly restore a
 * second source of truth in the client.
 */

/**
 * Screens whose primary endpoint is permission-gated on the server. Kept as
 * a literal list rather than read from the menu, so removing a
 * requiredPermissions line from one of them fails here instead of silently
 * handing the item back to the role map.
 */
const PERMISSION_GATED_SCREENS = [
  'members-list', 'employers-list', 'providers-list', 'provider-contracts',
  'claims-batches', 'preauth-inbox', 'claims-report',
  'provider-accounts', 'provider-payments', 'payments-management', 'provider-payment-reconciliation',
  'users-management', 'medical-dictionary', 'price-list-classifier', 'price-list-sessions',
  'kinship-mismatch', 'member-duplicates'
];

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (/\.(jsx?|mjs)$/.test(entry.name) && !full.includes(`${path.sep}__tests__${path.sep}`)) out.push(full);
  }
  return out;
};

describe('menu visibility architecture', () => {
  it('every server-permission-gated screen declares its permissions in the menu', () => {
    const missing = PERMISSION_GATED_SCREENS.filter((id) => {
      const item = leaves.find((leaf) => leaf.id === id);
      return !item || !(item.requiredPermission || (item.requiredPermissions && item.requiredPermissions.length));
    });
    expect(missing).toEqual([]);
  });

  it('the role map is consulted by the menu filter only, nowhere else', () => {
    const consumers = walk(SRC)
      .filter((file) => !file.endsWith(path.join('config', 'roleAccessMap.js')))
      .filter((file) => {
        const code = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
        return /ROLE_RESOURCE_ACCESS|roleAccessMap/.test(code);
      })
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(consumers).toEqual(['menu-items/components.jsx']);
  });

  it('the role map never decides an item that declares permissions', () => {
    // SUPER_ADMIN is '*' in the role map. With an empty permission set every
    // permission-declared screen must still be absent.
    const visible = ids(filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', true, true, []));
    const leaked = PERMISSION_GATED_SCREENS.filter((id) => visible.has(id));
    expect(leaked).toEqual([]);
  });

  it('a container is shown iff at least one child is shown', () => {
    // settlements/provider_accounts are not in DATA_ENTRY's role row; the
    // permission alone must open the child and, with it, its container.
    const withPermission = filterMenuItemsByRole(menuItems, 'DATA_ENTRY', false, true, ['SETTLEMENT_VIEW']);
    const without = filterMenuItemsByRole(menuItems, 'DATA_ENTRY', false, true, []);
    const containersOf = (items) => flatten(items).filter((i) => i.type === 'group' || i.type === 'collapse');

    expect(ids(withPermission).has('settlement')).toBe(true);
    expect(ids(without).has('settlement')).toBe(false);
    // and no surviving container is empty
    containersOf(withPermission).forEach((c) => expect(c.children?.length ?? 0).toBeGreaterThan(0));
    containersOf(without).forEach((c) => expect(c.children?.length ?? 0).toBeGreaterThan(0));
  });
});
