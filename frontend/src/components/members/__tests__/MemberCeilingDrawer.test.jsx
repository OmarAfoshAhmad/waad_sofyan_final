import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import MemberCeilingDrawer from '../MemberCeilingDrawer';
import { getLimitDetail } from 'services/api/unified-members.service';

vi.mock('services/api/unified-members.service', () => ({
  getLimitDetail: vi.fn()
}));

const theme = createTheme({ cssVariables: true });

const general = {
  asOfDate: '2026-08-28',
  readAt: '2026-08-28T22:15:04',
  mode: 'FOUND',
  policyId: 1,
  limit: 60000,
  committed: 10000,
  reserved: 5000,
  actualRemaining: 50000,
  reservableAvailable: 45000,
  utilizationPercent: 16.7,
  alertStatus: 'NORMAL'
};

const dental = {
  bucketId: 1,
  code: 'UAT-CEIL-DENTAL',
  name: 'أسنان',
  limit: 5000,
  committed: 1200,
  reserved: 800,
  actualRemaining: 3800,
  reservableAvailable: 3000,
  timesLimit: null
};

/** Limits occurrences, not money: every monetary figure is null by design. */
const visits = {
  bucketId: 2,
  code: 'UAT-CEIL-VISITS',
  name: 'زيارات',
  limit: null,
  committed: 0,
  reserved: 0,
  actualRemaining: null,
  reservableAvailable: null,
  timesLimit: 12
};

function renderDrawer() {
  render(
    <ThemeProvider theme={theme}>
      <MemberCeilingDrawer
        open
        member={{ id: 2, fullName: 'UAT-CEIL محجوز', cardNumber: 'UAT-CEIL-RESERVED' }}
        initialSummary={general}
        onClose={() => {}}
      />
    </ThemeProvider>
  );
}

describe('MemberCeilingDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getLimitDetail.mockResolvedValue({
      memberId: 2,
      asOfDate: '2026-08-28',
      readAt: '2026-08-28T22:15:04',
      general,
      buckets: [dental, visits]
    });
  });

  it('separates what the ledger holds from what is derived off it', async () => {
    renderDrawer();
    await waitFor(() => expect(getLimitDetail).toHaveBeenCalled());

    // The three readings.
    expect(screen.getByText('السقف السنوي')).toBeInTheDocument();
    expect(screen.getByText('المستهلك الفعلي')).toBeInTheDocument();
    expect(screen.getByText('المحجوز بموافقات مسبقة')).toBeInTheDocument();

    // And the two conclusions, under their own heading. Reading conclusions
    // in one list with their inputs invites adding a figure to something it
    // was already subtracted from.
    expect(screen.getByText('المحسوب منها')).toBeInTheDocument();
    expect(screen.getByText('المتبقي محاسبياً')).toBeInTheDocument();
    // Asserted by its own explanation: the label alone also names the
    // buckets table's column, and a query that matches two places proves
    // neither of them.
    expect(screen.getByText(/المتبقي محاسبياً ناقص المحجوز/)).toBeInTheDocument();
    expect(screen.getByText(/السقف ناقص المستهلك/)).toBeInTheDocument();
  });

  it('keeps the two remaining figures apart and correct', async () => {
    renderDrawer();
    await waitFor(() => expect(getLimitDetail).toHaveBeenCalled());

    expect(screen.getByText('50,000 د.ل')).toBeInTheDocument();
    expect(screen.getByText('45,000 د.ل')).toBeInTheDocument();
  });

  it('stacks a bucket held amount over what is left, in one column', async () => {
    renderDrawer();
    await waitFor(() => expect(getLimitDetail).toHaveBeenCalled());

    const row = screen.getByText('أسنان').closest('tr');
    expect(within(row).getByText('800 د.ل')).toBeInTheDocument();
    expect(within(row).getByText(/متبقٍ\s*3,800 د.ل/)).toBeInTheDocument();
    expect(within(row).getByText('3,000 د.ل')).toBeInTheDocument();
  });

  it('says what a count-only bucket limits instead of five unavailable cells', async () => {
    renderDrawer();
    await waitFor(() => expect(getLimitDetail).toHaveBeenCalled());

    const row = screen.getByText('زيارات').closest('tr');
    // A row of "غير متاح" reads as a broken record rather than as a benefit
    // measured in visits, which is what this one is.
    expect(within(row).getByText(/يحدّ عدد المرات \(12\)/)).toBeInTheDocument();
    expect(within(row).queryByText('غير متاح')).not.toBeInTheDocument();
  });

  it('never adds bucket consumption into the general ceiling', async () => {
    renderDrawer();
    await waitFor(() => expect(getLimitDetail).toHaveBeenCalled());

    // 10,000 general and 1,200 dental stay apart: one claim line can map to
    // several buckets, so summing counts the same money once per category.
    expect(screen.getByText('10,000 د.ل')).toBeInTheDocument();
    expect(screen.getByText('أرصدة مستقلة عن السقف العام ولا تُجمع معه')).toBeInTheDocument();
  });
});
