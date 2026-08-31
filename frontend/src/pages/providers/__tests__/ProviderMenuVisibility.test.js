import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import menuItems, { filterMenuItemsByRole } from '../../../menu-items/components';

const flatten = (items) =>
  items.flatMap((item) => [item, ...(item.children ? flatten(item.children) : [])]);

describe('provider contracts menu visibility', () => {
  it('shows provider contracts when the effective session grants CONTRACT_VIEW', () => {
    const visible = filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', false, true, ['CONTRACT_VIEW']);

    expect(flatten(visible).find((item) => item.id === 'provider-contracts')?.url).toBe('/provider-contracts');
  });

  it('honours an explicit CONTRACT_VIEW revocation even for SUPER_ADMIN', () => {
    const visible = filterMenuItemsByRole(menuItems, 'SUPER_ADMIN', false, true, []);

    expect(flatten(visible).some((item) => item.id === 'provider-contracts')).toBe(false);
  });

  it('passes the effective session permissions through the shared sidebar hook', () => {
    const hookSource = fs.readFileSync(path.resolve(__dirname, '../../../hooks/useRBACSidebar.js'), 'utf8');

    expect(hookSource).toMatch(
      /filterMenuItemsByRole\(menuItem,\s*role,\s*flags\.PROVIDER_PORTAL_ENABLED,\s*flags\.BATCH_CLAIMS_ENABLED,\s*user\.permissions\)/
    );
  });
});
