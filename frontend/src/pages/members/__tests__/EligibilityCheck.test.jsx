import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import EligibilityCheck from '../EligibilityCheck';
import { checkEligibility } from 'services/api/unified-members.service';

const theme = createTheme({ cssVariables: true });

vi.mock('services/api/unified-members.service', () => ({
  checkEligibility: vi.fn(),
  GENDERS: { MALE: 'MALE', FEMALE: 'FEMALE' }
}));

vi.mock('api/snackbar', () => ({
  openSnackbar: vi.fn()
}));

const BARCODE = 'WAHA-2026-000001';

const basePrincipal = {
  id: 1,
  fullName: 'أحمد علي',
  barcode: BARCODE,
  cardNumber: 'CARD-001',
  eligible: true,
  annualLimit: 50000,
  usedAmount: 8000,
  reservedAmount: 3000,
  // remainingLimit is what may still be committed; actualRemaining is what
  // has actually been consumed. They differ by the held 3,000 on purpose.
  remainingLimit: 39000,
  actualRemaining: 42000
};

async function searchByBarcode() {
  const input = screen.getByLabelText('Barcode');
  fireEvent.change(input, { target: { value: BARCODE } });
  fireEvent.click(screen.getByRole('button', { name: /فحص الأهلية/ }));
}

function renderPage() {
  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <EligibilityCheck />
      </MemoryRouter>
    </ThemeProvider>
  );
}

describe('EligibilityCheck financial-data-failure handling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows a persistent warning and "unavailable" limits, without hiding eligibility, when financialDataAvailable is false', async () => {
    checkEligibility.mockResolvedValue({
      data: {
        principal: basePrincipal,
        dependents: [],
        totalFamilyMembers: 1,
        eligibleMembersCount: 1,
        financialDataAvailable: false,
        financialDataError: 'تعذر جلب بيانات السقف المالي، يرجى إعادة المحاولة'
      }
    });

    renderPage();
    await searchByBarcode();

    // The financial-failure banner is shown with the backend's Arabic message.
    expect(await screen.findByText('تعذر تحميل بيانات السقف المالي')).toBeInTheDocument();
    expect(screen.getByText('تعذر جلب بيانات السقف المالي، يرجى إعادة المحاولة')).toBeInTheDocument();

    // Limit figures must read as unavailable, never as a fabricated zero.
    // Four figures stand alone -- annualLimit, usedAmount, reservedAmount and
    // remainingLimit -- while actualRemaining sits inside its own sentence, so
    // it is asserted by pattern rather than by exact text.
    expect(screen.getAllByText('غير متاح').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByText(/المتبقي محاسبياً\s*غير متاح/)).toBeInTheDocument();

    // Member identity and eligibility succeeded independently and must still render.
    expect(screen.getByText('أحمد علي')).toBeInTheDocument();
    expect(screen.getByText('مؤهل')).toBeInTheDocument();

    // A retry action is offered.
    expect(screen.getByRole('button', { name: /إعادة المحاولة/ })).toBeInTheDocument();
  });

  it('shows the real limit figures and no warning when financialDataAvailable is true', async () => {
    checkEligibility.mockResolvedValue({
      data: {
        principal: basePrincipal,
        dependents: [],
        totalFamilyMembers: 1,
        eligibleMembersCount: 1,
        financialDataAvailable: true
      }
    });

    renderPage();
    await searchByBarcode();

    expect(await screen.findByText('أحمد علي')).toBeInTheDocument();
    expect(screen.queryByText('تعذر تحميل بيانات السقف المالي')).not.toBeInTheDocument();
    expect(screen.queryByText('غير متاح')).not.toBeInTheDocument();
    expect(screen.getByText('50,000 د.ل')).toBeInTheDocument();
    // The headline figure is what may still be committed, not what is left on
    // the consumption axis -- the held 3,000 is unavailable to commit again.
    expect(screen.getByText('39,000 د.ل')).toBeInTheDocument();
    expect(screen.getByText('3,000 د.ل')).toBeInTheDocument();
    expect(screen.getByText(/42,000 د.ل/)).toBeInTheDocument();
  });

  it('renders a missing ceiling figure as unavailable rather than as zero', async () => {
    checkEligibility.mockResolvedValue({
      data: {
        principal: {
          ...basePrincipal,
          annualLimit: null,
          usedAmount: null,
          reservedAmount: null,
          remainingLimit: null,
          actualRemaining: null
        },
        dependents: [],
        totalFamilyMembers: 1,
        eligibleMembersCount: 1,
        // The read succeeded; this member simply has no ceiling in force, so
        // the backend sends nulls. Printing them as 0 د.ل would tell whoever
        // is authorising treatment that the ceiling is spent.
        financialDataAvailable: true
      }
    });

    renderPage();
    await searchByBarcode();

    await screen.findByText('أحمد علي');
    expect(screen.queryByText('0 د.ل')).not.toBeInTheDocument();
    expect(screen.getAllByText('غير متاح').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByText(/المتبقي محاسبياً\s*غير متاح/)).toBeInTheDocument();
  });

  it('retry button re-invokes the eligibility check with the same barcode', async () => {
    checkEligibility.mockResolvedValue({
      data: {
        principal: basePrincipal,
        dependents: [],
        totalFamilyMembers: 1,
        eligibleMembersCount: 1,
        financialDataAvailable: false,
        financialDataError: 'db timeout'
      }
    });

    renderPage();
    await searchByBarcode();
    await screen.findByText('تعذر تحميل بيانات السقف المالي');

    fireEvent.click(screen.getByRole('button', { name: /إعادة المحاولة/ }));

    await waitFor(() => expect(checkEligibility).toHaveBeenCalledTimes(2));
    expect(checkEligibility).toHaveBeenLastCalledWith(BARCODE);
  });
});
