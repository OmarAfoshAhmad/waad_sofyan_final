import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ClaimEntryReadinessAlert } from '../ClaimEntryReadinessAlert';

const renderGate = (props = {}) =>
  render(
    <ClaimEntryReadinessAlert
      member={null}
      serviceDate=""
      loading={false}
      context={null}
      error={null}
      onRetry={vi.fn()}
      {...props}
    />
  );

describe('ClaimEntryReadinessAlert', () => {
  it('guides the user through the prerequisites in order', () => {
    const { rerender } = renderGate();
    expect(screen.getByText(/اختر المستفيد أولاً/)).toBeInTheDocument();

    rerender(
      <ClaimEntryReadinessAlert member={{ id: 7 }} serviceDate="" loading={false} context={null} error={null} onRetry={vi.fn()} />
    );
    expect(screen.getByText(/حدّد تاريخ الخدمة/)).toBeInTheDocument();

    rerender(
      <ClaimEntryReadinessAlert member={{ id: 7 }} serviceDate="2026-08-20" loading context={null} error={null} onRetry={vi.fn()} />
    );
    expect(screen.getByText(/جارٍ التحقق/)).toBeInTheDocument();
  });

  it('shows one actionable server error rather than two competing messages', () => {
    const onRetry = vi.fn();
    renderGate({
      member: { id: 7 },
      serviceDate: '2026-08-20',
      error: { response: { data: { messageAr: 'الوثيقة منتهية في تاريخ الخدمة' } } },
      onRetry
    });

    expect(screen.getByText('الوثيقة منتهية في تاريخ الخدمة')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'إعادة التحقق' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('names the dated policy and contract when the gate is ready', () => {
    renderGate({
      member: { id: 7 },
      serviceDate: '2026-08-20',
      context: { policyCode: 'POL-7', contractNumber: 'CON-9', serviceDate: '2026-08-20' }
    });

    expect(screen.getByText(/POL-7/)).toBeInTheDocument();
    expect(screen.getByText(/CON-9/)).toBeInTheDocument();
    expect(screen.getByText(/2026-08-20/)).toBeInTheDocument();
  });
});
