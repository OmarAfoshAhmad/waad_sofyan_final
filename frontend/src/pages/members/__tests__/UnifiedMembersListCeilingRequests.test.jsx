import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import UnifiedMembersList from '../UnifiedMembersList';
import { searchMembers, getLimitsOverview, getLimitDetail } from 'services/api/unified-members.service';
import useAuth from 'hooks/useAuth';

/**
 * The rule this file exists for: the cost of the ceiling column belongs to the
 * page, not to its rows.
 *
 * A per-row hook is the obvious way to build this column and it is invisible
 * in review -- each row looks like it is fetching only what it needs. It only
 * shows up as thirty requests where there should be one, at exactly the page
 * sizes people use.
 */

const theme = createTheme({ cssVariables: true });

vi.mock('services/api/unified-members.service', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    searchMembers: vi.fn(),
    getLimitsOverview: vi.fn(),
    getLimitDetail: vi.fn(),
    countMembers: vi.fn().mockResolvedValue({ data: 0 }),
    exportMembers: vi.fn(),
    exportReimportableMembers: vi.fn(),
    terminateMembership: vi.fn(),
    bulkDeleteMembers: vi.fn(),
    reinstateTerminatedMember: vi.fn(),
    hardDeleteMember: vi.fn(),
    toggleMemberActive: vi.fn()
  };
});

vi.mock('utils/axios', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { data: [] } }) }
}));

vi.mock('hooks/useAuth', () => ({ default: vi.fn() }));

vi.mock('notistack', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSnackbar: () => ({ enqueueSnackbar: vi.fn() }) };
});

const PAGE_SIZE = 30;

const member = (id) => ({
  id,
  fullName: `عضو ${id}`,
  cardNumber: `CARD-${id}`,
  status: 'ACTIVE',
  employerName: 'جهة',
  relationship: 'PRINCIPAL',
  birthDate: '1990-01-01'
});

const ceiling = (id) => [
  id,
  {
    asOfDate: '2026-08-28',
    readAt: '2026-08-28T10:00:00',
    mode: 'FOUND',
    policyId: 7,
    limit: 60000,
    committed: 10000,
    reserved: 5000,
    actualRemaining: 50000,
    reservableAvailable: 45000,
    utilizationPercent: 16.7,
    alertStatus: 'NORMAL'
  }
];

function renderList() {
  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <UnifiedMembersList />
      </MemoryRouter>
    </ThemeProvider>
  );
}

describe('members list ceiling requests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuth.mockReturnValue({ user: { permissions: ['MEMBER_VIEW', 'MEMBER_LIMIT_VIEW'] } });

    const members = Array.from({ length: PAGE_SIZE }, (_, i) => member(i + 1));
    searchMembers.mockResolvedValue({
      data: { content: members, totalElements: PAGE_SIZE }
    });
    getLimitsOverview.mockResolvedValue(Object.fromEntries(members.map((m) => ceiling(m.id))));
  });

  it('reads thirty members ceilings in one request, with every id in it', async () => {
    renderList();

    await waitFor(() => expect(getLimitsOverview).toHaveBeenCalledTimes(1));

    const [ids] = getLimitsOverview.mock.calls[0];
    expect(ids).toHaveLength(PAGE_SIZE);
    expect(ids).toEqual(expect.arrayContaining([1, PAGE_SIZE]));

    // And the figures reached the rows, so "one request" is not passing
    // because the column quietly rendered nothing.
    await waitFor(() => expect(screen.getAllByText('45,000 د.ل')).toHaveLength(PAGE_SIZE));
  });

  it('asks for bucket detail only when a drawer is opened, and only for that member', async () => {
    getLimitDetail.mockResolvedValue({
      memberId: 1,
      asOfDate: '2026-08-28',
      readAt: '2026-08-28T10:05:00',
      general: ceiling(1)[1],
      buckets: []
    });

    renderList();
    await waitFor(() => expect(getLimitsOverview).toHaveBeenCalledTimes(1));

    // Rendering a page of thirty must not have fetched any detail.
    expect(getLimitDetail).not.toHaveBeenCalled();

    await userEvent.click(screen.getAllByText('45,000 د.ل')[0]);

    await waitFor(() => expect(getLimitDetail).toHaveBeenCalledTimes(1));
    expect(getLimitDetail).toHaveBeenCalledWith(1);
  });

  it('leaves the rows standing when the ceiling read fails', async () => {
    getLimitsOverview.mockRejectedValue(new Error('boom'));

    renderList();

    await waitFor(() => expect(getLimitsOverview).toHaveBeenCalledTimes(1));

    // The members loaded fine; hiding them because a balance read failed
    // would be a worse answer than showing them without balances.
    await waitFor(() => expect(screen.getByText('عضو 1')).toBeInTheDocument());
    expect(screen.getAllByText('غير متاح').length).toBeGreaterThan(0);
  });

  it('drops the column when the server says this account may never read ceilings', async () => {
    const forbidden = new Error('forbidden');
    forbidden.response = { status: 403 };
    getLimitsOverview.mockRejectedValue(forbidden);

    renderList();

    await waitFor(() => expect(getLimitsOverview).toHaveBeenCalledTimes(1));

    // MEMBER_LIMIT_VIEW is held by roles the backend still refuses for a bulk
    // read, so the permission bit alone would leave a column reading
    // "unavailable" on every row forever -- a system fault to look at, not a
    // permission.
    await waitFor(() => expect(screen.queryByText('المتاح لالتزام جديد')).not.toBeInTheDocument());
    expect(screen.getByText('عضو 1')).toBeInTheDocument();
  });

  it('keeps the column for a transient failure, because that one is worth retrying', async () => {
    getLimitsOverview.mockRejectedValue(new Error('network'));

    renderList();

    await waitFor(() => expect(getLimitsOverview).toHaveBeenCalledTimes(1));

    expect(screen.getByText('المتاح لالتزام جديد')).toBeInTheDocument();
    expect(screen.getAllByText('غير متاح').length).toBeGreaterThan(0);
  });

  it('hides the column entirely without the limit permission', async () => {
    useAuth.mockReturnValue({ user: { permissions: ['MEMBER_VIEW'] } });

    renderList();

    await waitFor(() => expect(searchMembers).toHaveBeenCalled());
    expect(screen.queryByText('المتاح لالتزام جديد')).not.toBeInTheDocument();
    expect(getLimitsOverview).not.toHaveBeenCalled();
  });
});
