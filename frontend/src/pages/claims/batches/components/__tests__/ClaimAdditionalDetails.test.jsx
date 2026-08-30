import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ClaimAdditionalDetails } from '../ClaimAdditionalDetails';

const props = (overrides = {}) => ({
  complaint: '',
  setComplaint: vi.fn(),
  setIsDirty: vi.fn(),
  ...overrides
});

describe('ClaimAdditionalDetails', () => {
  it('keeps optional fields compact until requested', () => {
    render(<ClaimAdditionalDetails {...props()} />);
    expect(screen.getByRole('button', { name: /شكوى المستفيد/ })).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens automatically when an existing claim contains clinical details', () => {
    render(<ClaimAdditionalDetails {...props({ complaint: 'ألم مستمر' })} />);
    expect(screen.getByRole('button', { name: /شكوى المستفيد/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByDisplayValue('ألم مستمر')).toBeInTheDocument();
  });

  it('marks the draft dirty when a visible detail is edited', () => {
    const callbacks = props();
    render(<ClaimAdditionalDetails {...callbacks} />);
    fireEvent.click(screen.getByRole('button', { name: /شكوى المستفيد/ }));
    fireEvent.change(screen.getByLabelText('شكوى المستفيد'), { target: { value: 'ألم' } });
    expect(callbacks.setComplaint).toHaveBeenCalledWith('ألم');
    expect(callbacks.setIsDirty).toHaveBeenCalledWith(true);
  });
});
