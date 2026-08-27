import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import PermissionGuard from '../PermissionGuard';

let currentUser;
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: currentUser, authStatus: 'AUTHENTICATED' })
}));

describe('PermissionGuard capability mode', () => {
  beforeEach(() => {
    currentUser = { role: 'DATA_ENTRY', permissions: [] };
  });

  it('renders only when the effective permission snapshot grants the capability', () => {
    currentUser.permissions = ['EMPLOYER_MANAGE'];
    render(
      <PermissionGuard requiredPermission="EMPLOYER_MANAGE">
        <span>allowed</span>
      </PermissionGuard>
    );
    expect(screen.getByText('allowed')).toBeInTheDocument();
  });

  it('hides a capability revoked from the effective snapshot', () => {
    render(
      <PermissionGuard requiredPermission="EMPLOYER_MANAGE">
        <span>forbidden</span>
      </PermissionGuard>
    );
    expect(screen.queryByText('forbidden')).not.toBeInTheDocument();
  });

  it('does not bypass an explicit revocation merely because the role is super admin', () => {
    currentUser = { role: 'SUPER_ADMIN', permissions: [] };
    render(
      <PermissionGuard requiredPermission="EMPLOYER_MANAGE">
        <span>forbidden</span>
      </PermissionGuard>
    );
    expect(screen.queryByText('forbidden')).not.toBeInTheDocument();
  });
});
