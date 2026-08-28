import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import MemberCeilingCell from '../MemberCeilingCell';

const theme = createTheme({ cssVariables: true });

function renderCell(summary) {
  return render(
    <ThemeProvider theme={theme}>
      <MemberCeilingCell summary={summary} loading={false} />
    </ThemeProvider>
  );
}

const found = (overrides = {}) => ({
  mode: 'FOUND',
  limit: 60000,
  committed: 10000,
  reserved: 5000,
  actualRemaining: 50000,
  reservableAvailable: 45000,
  alertStatus: 'NORMAL',
  ...overrides
});

describe('MemberCeilingCell', () => {
  it('leads with what may still be committed, not with what is left on the consumption axis', () => {
    renderCell(found());

    // 45,000 and not 50,000: the held 5,000 is not available to commit again,
    // and committing it twice is what the hold exists to prevent.
    expect(screen.getByText(/45,000 د.ل/)).toBeInTheDocument();
    expect(screen.queryByText(/50,000 د.ل/)).not.toBeInTheDocument();
    // The ceiling rides on the same line rather than a caption of its own.
    expect(screen.getByText(/من 60,000 د.ل/)).toBeInTheDocument();
  });

  it('names consumed and held separately rather than adding them', () => {
    renderCell(found());

    expect(screen.getByText(/مستهلك\s*10,000\s*·\s*محجوز\s*5,000/)).toBeInTheDocument();
  });

  it('shows an overspend as its own warning and never as a clamped zero', () => {
    renderCell(found({ committed: 65000, actualRemaining: -5000, reservableAvailable: -5000, alertStatus: 'EXCEEDED' }));

    expect(screen.getByText(/تجاوز\s*5,000 د.ل/)).toBeInTheDocument();
    expect(screen.getByText(/-5,000 د.ل/)).toBeInTheDocument();
  });

  it.each([
    ['UNLIMITED', 'بلا سقف'],
    ['NOT_CONFIGURED', 'لا يوجد سقف'],
    ['UNAVAILABLE', 'غير متاح']
  ])('draws no figures for %s', (mode, expectedText) => {
    renderCell({ mode });

    expect(screen.getByText(expectedText)).toBeInTheDocument();
    // Nothing that could be read as a balance.
    expect(screen.queryByText(/د\.ل/)).not.toBeInTheDocument();
  });

  it('treats a missing summary as unavailable rather than as an empty ceiling', () => {
    renderCell(undefined);

    expect(screen.getByText('غير متاح')).toBeInTheDocument();
  });
});
