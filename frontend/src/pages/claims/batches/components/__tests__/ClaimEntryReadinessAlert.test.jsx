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
  // Every non-failure state has an owner elsewhere on the screen: the required
  // markers on the two fields, and the page subtitle that names the verified
  // policy and contract permanently. This component repeats none of them.
  it('says nothing while there is no failure to report', () => {
    const { container, rerender } = renderGate();
    expect(container).toBeEmptyDOMElement();

    rerender(
      <ClaimEntryReadinessAlert member={{ id: 7 }} serviceDate="" loading={false} context={null} error={null} onRetry={vi.fn()} />
    );
    expect(container).toBeEmptyDOMElement();

    rerender(
      <ClaimEntryReadinessAlert member={{ id: 7 }} serviceDate="2026-08-20" loading context={null} error={null} onRetry={vi.fn()} />
    );
    expect(container).toBeEmptyDOMElement();

    rerender(
      <ClaimEntryReadinessAlert
        member={{ id: 7 }}
        serviceDate="2026-08-20"
        loading={false}
        context={{ policyCode: 'POL-7', contractNumber: 'CON-9', serviceDate: '2026-08-20' }}
        error={null}
        onRetry={vi.fn()}
      />
    );
    expect(container).toBeEmptyDOMElement();
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

  it('still reports a missing dated policy, which nothing else on the screen does', () => {
    renderGate({ member: { id: 7 }, serviceDate: '2026-08-20', context: null, error: null });
    expect(screen.getByText(/لا توجد وثيقة وعقد صالحان/)).toBeInTheDocument();
  });

});
