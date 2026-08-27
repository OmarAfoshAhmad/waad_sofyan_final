import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import { ClaimTotalsFooter } from '../ClaimTotalsFooter';

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
  return screen.getByRole('button', { name: 'حفظ وإضافة' });
}

describe('ClaimTotalsFooter financial fail-closed gate', () => {
  it('disables saving when the member financial read is unavailable', () => {
    expect(renderFooter(true)).toBeDisabled();
  });

  it('allows saving after the financial read succeeds', () => {
    expect(renderFooter(false)).toBeEnabled();
  });
});
