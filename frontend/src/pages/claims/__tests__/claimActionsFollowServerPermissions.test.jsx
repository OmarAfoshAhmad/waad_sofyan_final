import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

import { ClaimTotalsFooter } from '../batches/components/ClaimTotalsFooter';

let currentUser;
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: currentUser, authStatus: 'AUTHENTICATED' })
}));

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const read = (rel) => fs.readFileSync(path.resolve(here, rel), 'utf8');

/**
 * Sensitive actions are drawn only for a user whose effective permission set
 * (user.permissions from /session/me) satisfies what the server checks on the
 * endpoint the button calls -- docs/security/ACTION_GUARD_DERIVATION.md. Role
 * names are never consulted: the lists this replaced named INSURANCE_ADMIN,
 * a role that does not exist, and could not see per-user overrides.
 */
describe('claim actions follow server permissions', () => {
  const theme = createTheme();
  const footerProps = () => ({
    isClaimRejected: false,
    handleSave: vi.fn(),
    saving: false,
    isDirty: true,
    coveragePending: false,
    financialDataUnavailable: false,
    hasUncoveredLines: false,
    setIsClaimRejected: vi.fn(),
    setIsDirty: vi.fn(),
    setRejectionInput: vi.fn(),
    openRejectDialog: vi.fn(),
    totals: { total: 100, company: 80, employee: 20, refused: 0 },
    beneficiarySettlement: { paid: 0, finalBeneficiaryShare: 20, providerRefusedBalance: 0, excessPayment: 0 },
    onBeneficiaryPaidAmountChange: vi.fn(),
    theme,
    lines: [{ id: 1, total: 100 }],
    t: (k) => k,
    visibleColumns: { companyShare: true, patientShare: true, refused: true }
  });

  beforeEach(() => {
    currentUser = { role: 'MEDICAL_REVIEWER', permissions: [] };
  });

  describe('claim entry footer (POST /claims/direct-entry, /claims/draft -> CLAIM_CREATE)', () => {
    it('hides submit, draft and reject without CLAIM_CREATE', () => {
      render(<ThemeProvider theme={theme}><ClaimTotalsFooter {...footerProps()} /></ThemeProvider>);
      expect(screen.queryByRole('button', { name: /إرسال/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /حفظ كمسودة/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /رفض المطالبة/ })).toBeNull();
    });

    it('shows them with CLAIM_CREATE, whatever the role', () => {
      currentUser = { role: 'PROVIDER_STAFF', permissions: ['CLAIM_CREATE'] };
      render(<ThemeProvider theme={theme}><ClaimTotalsFooter {...footerProps()} /></ThemeProvider>);
      expect(screen.getByRole('button', { name: /إرسال/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /حفظ كمسودة/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /رفض المطالبة/ })).toBeInTheDocument();
    });
  });

  /**
   * The review and batch-detail pages are too data-heavy to mount here; the
   * wiring is asserted as text, the same way the provider and member modules
   * are guarded. Each action names the permission its endpoint checks.
   */
  describe('wiring', () => {
    it('medical review: reject/request-info/pause/resume are CLAIM_REVIEW, approve is CLAIM_APPROVE', () => {
      const src = read('../ClaimViewMedicalReview.jsx');
      expect(src).toMatch(/const canFinalizeApproval = effectivePermissions\.has\('CLAIM_APPROVE'\)/);
      expect((src.match(/<PermissionGuard requiredPermission="CLAIM_REVIEW">/g) || []).length).toBe(2);
      expect(src).not.toMatch(/\[\s*'SUPER_ADMIN',\s*'INSURANCE_MANAGER',\s*'MEDICAL_REVIEW_HEAD'\s*\]/);
      expect(src).not.toMatch(/normalizedCurrentUserRole/);
    });

    it('batch detail: flags come from the effective set, one per endpoint', () => {
      const src = read('../batches/ClaimBatchDetail.jsx');
      expect(src).toMatch(/const canSuspend = effectivePermissions\.has\('CLAIM_REVERSE'\)/);
      expect(src).toMatch(/const canDelete = effectivePermissions\.has\('CLAIM_CREATE'\)/);
      expect(src).toMatch(/const canRestore = effectivePermissions\.has\('CLAIM_REVIEW'\)/);
      expect(src).toMatch(/const canHardDelete = effectivePermissions\.has\('DANGER_ZONE_EXECUTE'\)/);
      expect(src).toMatch(/\{canRestore && \(/);
      expect(src).not.toMatch(/currentUserRole/);
      expect(src).not.toMatch(/INSURANCE_ADMIN/);
    });

    it('pre-auth inbox: start-review is PREAUTH_REVIEW, reject-all is PREAUTH_APPROVE', () => {
      const src = read('../../pre-approvals/PreApprovalsInbox.jsx');
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_REVIEW">[\s\S]{0,400}handleStartReview/);
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_APPROVE">[\s\S]{0,400}handleOpenReject/);
    });

    it('pre-auth review: line decisions and request-info are PREAUTH_REVIEW, finalize and reject-all are PREAUTH_APPROVE', () => {
      const src = read('../../pre-approvals/PreAuthReviewPage.jsx');
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_REVIEW">[\s\S]{0,600}handleStartReview/);
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_REVIEW">[\s\S]{0,900}openLineDecisionModal/);
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_APPROVE">[\s\S]{0,700}finalize_confirm[\s\S]{0,900}reject_all/);
      expect(src).toMatch(/<PermissionGuard requiredPermission="PREAUTH_REVIEW">[\s\S]{0,700}request_info/);
    });

    it('no page in the claims or pre-approvals modules decides an action by role name', () => {
      const dirs = ['..', '../batches', '../../pre-approvals'];
      const offenders = [];
      for (const dir of dirs) {
        const abs = path.resolve(here, dir);
        for (const name of fs.readdirSync(abs)) {
          if (!name.endsWith('.jsx') || name.includes('.test.')) continue;
          const code = fs.readFileSync(path.join(abs, name), 'utf8')
            .replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
          if (/requiredRole=|allowedRoles=/.test(code)) offenders.push(`${dir}/${name}`);
        }
      }
      expect(offenders).toEqual([]);
    });
  });
});
