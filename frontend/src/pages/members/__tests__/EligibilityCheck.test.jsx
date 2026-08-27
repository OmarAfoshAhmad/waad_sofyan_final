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
  remainingLimit: 42000
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
    const unavailableValues = screen.getAllByText('غير متاح');
    expect(unavailableValues.length).toBeGreaterThanOrEqual(3); // annualLimit, usedAmount, remainingLimit

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
    expect(screen.getByText('42,000 د.ل')).toBeInTheDocument();
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
