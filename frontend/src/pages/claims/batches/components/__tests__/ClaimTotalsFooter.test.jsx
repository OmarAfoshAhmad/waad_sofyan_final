import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import { ClaimTotalsFooter } from '../ClaimTotalsFooter';

// The footer's write actions are gated on CLAIM_CREATE (the permission the
// server checks on direct-entry/draft/submit). These tests are about the
// financial-read gating of an already-permitted user, so grant it.
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: { role: 'DATA_ENTRY', permissions: ['CLAIM_CREATE'] }, authStatus: 'AUTHENTICATED' })
}));

const theme = createTheme({ cssVariables: true });

function renderFooter(financialDataUnavailable) {
  render(
    <ThemeProvider theme={theme}>
      <ClaimTotalsFooter isClaimRejected={false} handleSave={vi.fn()} saving={false} isDirty
        coveragePending={false} financialDataUnavailable={financialDataUnavailable} hasUncoveredLines={false}
        setIsClaimRejected={vi.fn()} setIsDirty={vi.fn()} setRejectionInput={vi.fn()} openRejectDialog={vi.fn()}
        totals={{ total: 100, refused: 0, company: 80, member: 20 }} theme={theme}
        lines={[{ service: { id: 1 }, rejected: false }]}
        t={(key) => key === 'claimEntry.saveAndAdd' ? 'حفظ وإضافة' : key} visibleColumns={{}} />
    </ThemeProvider>
  );
  return screen.getByRole('button', { name: 'إرسال' });
}

describe('ClaimTotalsFooter financial fail-closed gate', () => {
  it('disables saving when the member financial read is unavailable', () => {
    expect(renderFooter(true)).toBeDisabled();
  });

  it('allows saving after the financial read succeeds', () => {
    expect(renderFooter(false)).toBeEnabled();
  });
});
