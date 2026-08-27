import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const read = (relativePath) => fs.readFileSync(path.resolve(here, relativePath), 'utf8');

describe('provider and contract permission wiring', () => {
  it('does not use the unsupported resource/action PermissionGuard API', () => {
    const source = read('../ProvidersList.jsx');

    expect(source).not.toMatch(/<PermissionGuard\s+resource=/);
    expect(source).not.toMatch(/<PermissionGuard[^>]+action=/);
  });

  it('separates provider reading, management, and destructive deletion', () => {
    const source = read('../ProvidersList.jsx');

    expect(source).toContain('requiredPermission="PROVIDER_VIEW"');
    expect(source).toContain('requiredPermission="PROVIDER_MANAGE"');
    expect(source).toContain("requiredPermissions={['PROVIDER_MANAGE', 'DANGER_ZONE_EXECUTE']}");
  });

  it('separates contract management from price-list import', () => {
    const source = read('../../provider-contracts/ProviderContractsList.jsx');

    expect(source).toContain('requiredPermission="CONTRACT_MANAGE"');
    expect(source).toContain('requiredPermission="PRICE_LIST_IMPORT"');
    expect(source).toContain("requiredPermissions={['CONTRACT_MANAGE', 'DANGER_ZONE_EXECUTE']}");
  });

  it('protects contract and price-list routes with effective capabilities', () => {
    const routes = read('../../../routes/MainRoutes.jsx');

    expect(routes).toContain('<PermissionGuard requiredPermission="CONTRACT_VIEW" isRouteGuard>');
    expect(routes).toContain('<PermissionGuard requiredPermission="CONTRACT_MANAGE" isRouteGuard>');
    expect(routes).toContain('<PermissionGuard requiredPermission="PRICE_LIST_IMPORT" isRouteGuard>');
    expect(routes).toContain("requiredPermissions={['PRICE_LIST_IMPORT', 'PRICE_LIST_POST']}");
  });

  it('does not show contract mutation controls to read-only users', () => {
    const source = read('../../provider-contracts/ProviderContractView.jsx');

    expect(source).toContain('<PermissionGuard requiredPermission="CONTRACT_MANAGE">');
    expect(source).toContain('<PermissionGuard requiredPermission="PRICE_LIST_IMPORT">');
  });

  it('filters migrated navigation items using effective permissions', () => {
    const source = read('../../../menu-items/components.jsx');

    expect(source).toContain('effectivePermissions.has(item.requiredPermission)');
    expect(source).toContain("requiredPermission: 'CONTRACT_VIEW'");
    expect(source).toContain("requiredPermission: 'PRICE_LIST_IMPORT'");
    expect(source).toContain("requiredPermissions: ['PRICE_LIST_IMPORT', 'PRICE_LIST_POST']");
  });
});
