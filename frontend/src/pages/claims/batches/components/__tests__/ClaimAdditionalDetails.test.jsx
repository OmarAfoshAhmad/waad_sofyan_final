import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ClaimAdditionalDetails } from '../ClaimAdditionalDetails';

const PRE_AUTHS = [
  { id: 7, number: 'PA-2026-0007', serviceName: 'أشعة مقطعية', approvedAmount: 450 }
];

const props = (overrides = {}) => ({
  complaint: '',
  setComplaint: vi.fn(),
  setIsDirty: vi.fn(),
  preAuthResults: [],
  searchingPreAuth: false,
  preAuthId: '',
  setPreAuthId: vi.fn(),
  doctorName: '',
  setDoctorName: vi.fn(),
  ...overrides
});

// The toggle is matched by its stable prefix rather than its whole label: the
// label deliberately appends what is recorded, and pinning the full string
// would make every future addition to that summary look like a regression.
const toggle = () => screen.getByRole('button', { name: /تفاصيل إضافية/ });

describe('ClaimAdditionalDetails', () => {
  it('keeps optional fields compact until requested', () => {
    render(<ClaimAdditionalDetails {...props()} />);
    expect(toggle()).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens automatically when an existing claim contains clinical details', () => {
    render(<ClaimAdditionalDetails {...props({ complaint: 'ألم مستمر' })} />);
    expect(toggle()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByDisplayValue('ألم مستمر')).toBeInTheDocument();
  });

  // The reason a collapsed section is safe at all: nothing already set can hide
  // inside it. A linked approval commits money, so it must open the section on
  // its own exactly as a recorded complaint does.
  it('opens automatically when a pre-authorization is already linked', () => {
    render(<ClaimAdditionalDetails {...props({ preAuthResults: PRE_AUTHS, preAuthId: 7 })} />);
    expect(toggle()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByDisplayValue(/PA-2026-0007/)).toBeInTheDocument();
  });

  it('names what is recorded on the collapsed toggle', () => {
    render(<ClaimAdditionalDetails {...props({ complaint: 'ألم', preAuthResults: PRE_AUTHS, preAuthId: 7 })} />);
    expect(toggle()).toHaveTextContent('موافقة مسبقة وشكوى');
  });

  // The doctor is recorded on most claims even though the server never required
  // it, so a saved claim must not hide it behind a closed panel either.
  it('opens automatically when a doctor is already recorded', () => {
    render(<ClaimAdditionalDetails {...props({ doctorName: 'د. سالم' })} />);
    expect(toggle()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByDisplayValue('د. سالم')).toBeInTheDocument();
  });

  it('reports the doctor to the parent without demanding one', () => {
    const callbacks = props();
    render(<ClaimAdditionalDetails {...callbacks} />);
    fireEvent.click(toggle());
    const field = screen.getByLabelText('اسم الطبيب المعالج');
    expect(field).not.toBeRequired();
    fireEvent.change(field, { target: { value: 'د. سالم' } });
    expect(callbacks.setDoctorName).toHaveBeenCalledWith('د. سالم');
    expect(callbacks.setIsDirty).toHaveBeenCalledWith(true);
  });

  it('marks the draft dirty when a visible detail is edited', () => {
    const callbacks = props();
    render(<ClaimAdditionalDetails {...callbacks} />);
    fireEvent.click(toggle());
    fireEvent.change(screen.getByLabelText('شكوى المستفيد'), { target: { value: 'ألم' } });
    expect(callbacks.setComplaint).toHaveBeenCalledWith('ألم');
    expect(callbacks.setIsDirty).toHaveBeenCalledWith(true);
  });

  it('reports the chosen pre-authorization to the parent', () => {
    const callbacks = props({ preAuthResults: PRE_AUTHS });
    render(<ClaimAdditionalDetails {...callbacks} />);
    fireEvent.click(toggle());
    // Captured before the listbox opens: once it does, the accessible name
    // matches the input and the rendered option both.
    const picker = screen.getByLabelText('ربط موافقة مسبقة صالحة');
    fireEvent.change(picker, { target: { value: 'PA-2026' } });
    fireEvent.click(screen.getByRole('option', { name: /PA-2026-0007/ }));
    expect(callbacks.setPreAuthId).toHaveBeenCalledWith(7);
    expect(callbacks.setIsDirty).toHaveBeenCalledWith(true);
  });
});
