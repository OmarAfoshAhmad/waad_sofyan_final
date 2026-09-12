import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

import KinshipMismatchChecker from '../KinshipMismatchChecker';

let currentUser;
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: currentUser, authStatus: 'AUTHENTICATED' })
}));
vi.mock('utils/axios', () => ({
  default: {
    get: vi.fn().mockResolvedValue({
      data: { data: [{ id: 7, fullName: 'سالم علي', cardNumber: '1001', relationship: 'SON', gender: 'FEMALE', parentName: 'علي' }] }
    }),
    post: vi.fn()
  }
}));

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const read = (rel) => fs.readFileSync(path.resolve(here, rel), 'utf8');

/**
 * Every write on /system-settings/kinship-mismatches is DANGER_ZONE_EXECUTE
 * on the server, while the screen itself opens with SYSTEM_SETTINGS_VIEW. A
 * viewer sees the mismatches; only a holder of the danger-zone permission
 * sees fix / ignore. Role names are not consulted.
 */
describe('danger-zone and price-list actions follow server permissions', () => {
  beforeEach(() => {
    currentUser = { role: 'MEDICAL_REVIEWER', permissions: ['SYSTEM_SETTINGS_VIEW'] };
  });

  describe('kinship mismatch checker (DANGER_ZONE_EXECUTE)', () => {
    it('lists mismatches but draws no fix/ignore controls without the permission', async () => {
      render(<KinshipMismatchChecker />);
      await waitFor(() => expect(screen.getByText('سالم علي')).toBeInTheDocument());
      expect(screen.queryByRole('button', { name: /^إصلاح$/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /إبقاء/ })).toBeNull();
      // the bulk bar appears once a row is selected
      await userEvent.click(screen.getAllByRole('checkbox')[1]);
      expect(screen.queryByRole('button', { name: /إصلاح شامل/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /تجاهل الأخطاء/ })).toBeNull();
    });

    it('draws them with DANGER_ZONE_EXECUTE, whatever the role', async () => {
      currentUser = { role: 'DATA_ENTRY', permissions: ['SYSTEM_SETTINGS_VIEW', 'DANGER_ZONE_EXECUTE'] };
      render(<KinshipMismatchChecker />);
      await waitFor(() => expect(screen.getByText('سالم علي')).toBeInTheDocument());
      expect(screen.getByRole('button', { name: /^إصلاح$/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /إبقاء/ })).toBeInTheDocument();
      await userEvent.click(screen.getAllByRole('checkbox')[1]);
      expect(screen.getByRole('button', { name: /إصلاح شامل/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /تجاهل الأخطاء/ })).toBeInTheDocument();
    });
  });

  describe('wiring', () => {
    it('role template save is ROLE_PERMISSION_MANAGE', () => {
      expect(read('../../rbac/users/RolePermissions.jsx')).toMatch(
        /<PermissionGuard requiredPermission="ROLE_PERMISSION_MANAGE"><Button[\s\S]{0,300}?onClick=\{save\}/
      );
    });

    it('price-list sessions: posting is PRICE_LIST_POST, deleting is PRICE_LIST_IMPORT', () => {
      const src = read('../../price-list-classifier/PriceListSessionsPage.jsx');
      expect((src.match(/<PermissionGuard requiredPermission="PRICE_LIST_POST">/g) || []).length).toBe(2);
      expect((src.match(/<PermissionGuard requiredPermission="PRICE_LIST_IMPORT">/g) || []).length).toBe(2);
      expect(src).toMatch(/requiredPermission="PRICE_LIST_POST">[\s\S]{0,400}openBulkPostDialog/);
      expect(src).toMatch(/requiredPermission="PRICE_LIST_IMPORT">[\s\S]{0,400}openBulkDeleteDialog/);
    });

    it('classifier: posting selected rows to a contract is PRICE_LIST_POST', () => {
      expect(read('../../price-list-classifier/index.jsx')).toMatch(
        /<PermissionGuard requiredPermission="PRICE_LIST_POST">[\s\S]{0,500}postApprovedRowsToSelectedProviderContract/
      );
    });

    it('user management actions are decided by USER_MANAGE from the effective set', () => {
      const src = read('../../rbac/users/UsersList.jsx');
      expect(src).toMatch(/const canManageUsers = permissions\.has\('USER_MANAGE'\)/);
      expect(src).not.toMatch(/requiredRole=|allowedRoles=/);
    });
  });
});
