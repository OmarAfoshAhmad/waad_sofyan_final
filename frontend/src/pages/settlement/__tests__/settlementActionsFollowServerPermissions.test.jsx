import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

import PaymentDetailsModal from '../components/PaymentDetailsModal';

let currentUser;
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: currentUser, authStatus: 'AUTHENTICATED' })
}));
vi.mock('services/api/payments.service', () => ({
  default: {
    getPaymentRecords: vi.fn().mockResolvedValue([
      { id: 11, paymentDate: '2026-09-01', amount: 500, paymentMethod: 'BANK_TRANSFER', referenceNumber: 'TRX-1', notes: '' }
    ]),
    deletePayment: vi.fn()
  }
}));

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const read = (rel) => fs.readFileSync(path.resolve(here, rel), 'utf8');

const summary = { employerId: 1, providerId: 2, targetYear: 2026, targetMonth: 9, totalAmount: 1000, paidAmount: 500, remainingAmount: 500 };

const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <PaymentDetailsModal open summary={summary} onClose={() => {}} onPaymentChanged={() => {}} />
    </QueryClientProvider>
  );
};

/**
 * Settlement writes are SETTLEMENT_MANAGE on the server; balance recalculation
 * additionally needs DANGER_ZONE_EXECUTE (both, not either). The pages used
 * to wrap themselves in PermissionGuard props the component does not read
 * (requiredRole, resource/action) -- naming roles that do not exist -- which
 * rendered everything for every signed-in user.
 */
describe('settlement actions follow server permissions', () => {
  beforeEach(() => {
    currentUser = { role: 'FINANCE_VIEWER', permissions: ['SETTLEMENT_VIEW'] };
  });

  describe('payment details modal (POST|PUT|DELETE /payments -> SETTLEMENT_MANAGE)', () => {
    it('shows the records but no add/edit/cancel controls without SETTLEMENT_MANAGE', async () => {
      mount();
      await waitFor(() => expect(screen.getByText('TRX-1')).toBeInTheDocument());
      expect(screen.queryByRole('button', { name: /إضافة دفعة جديدة/ })).toBeNull();
      expect(screen.queryByLabelText('تعديل')).toBeNull();
      expect(screen.queryByLabelText('إلغاء')).toBeNull();
    });

    it('shows them with SETTLEMENT_MANAGE, whatever the role', async () => {
      currentUser = { role: 'DATA_ENTRY', permissions: ['SETTLEMENT_VIEW', 'SETTLEMENT_MANAGE'] };
      mount();
      await waitFor(() => expect(screen.getByText('TRX-1')).toBeInTheDocument());
      expect(screen.getByRole('button', { name: /إضافة دفعة جديدة/ })).toBeInTheDocument();
      expect(screen.getByLabelText('تعديل')).toBeInTheDocument();
      expect(screen.getByLabelText('إلغاء')).toBeInTheDocument();
    });
  });

  describe('wiring', () => {
    it('no settlement page wraps itself in props PermissionGuard does not read', () => {
      const dirs = ['..', '../components', '../reconciliation'];
      const offenders = [];
      for (const dir of dirs) {
        const abs = path.resolve(here, dir);
        for (const name of fs.readdirSync(abs)) {
          if (!name.endsWith('.jsx') || name.includes('.test.')) continue;
          const code = fs.readFileSync(path.join(abs, name), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
          if (/<PermissionGuard[^>]*(requiredRole|resource=|action=|allowedRoles)/.test(code)) offenders.push(`${dir}/${name}`);
        }
      }
      expect(offenders).toEqual([]);
    });

    it('recalculation requires SETTLEMENT_MANAGE and DANGER_ZONE_EXECUTE together', () => {
      const list = read('../ProviderPaymentsList.jsx');
      const view = read('../ProviderAccountView.jsx');
      const both = /requiredPermissions=\{\['SETTLEMENT_MANAGE', 'DANGER_ZONE_EXECUTE'\]\}/g;
      expect((list.match(both) || []).length).toBe(2); // repair all + repair row
      expect((view.match(both) || []).length).toBe(1); // recalculate balance
      expect(list).not.toMatch(/requireAll=\{false\}|requireAllPermissions/);
    });

    it('paying an installment is SETTLEMENT_MANAGE', () => {
      const view = read('../ProviderAccountView.jsx');
      expect(view).toMatch(/<PermissionGuard requiredPermission="SETTLEMENT_MANAGE">[\s\S]{0,500}setIsPaymentModalOpen\(true\)/);
    });

    it('reconciliation writes need the posting flag AND SETTLEMENT_MANAGE', () => {
      const drawer = read('../reconciliation/ProviderPaymentDetailDrawer.jsx');
      expect(drawer).toMatch(/PROVIDER_PAYMENT_POSTING_ENABLED\)\s*&&\s*\(user\?\.permissions \|\| \[\]\)\.includes\('SETTLEMENT_MANAGE'\)/);
    });
  });
});
