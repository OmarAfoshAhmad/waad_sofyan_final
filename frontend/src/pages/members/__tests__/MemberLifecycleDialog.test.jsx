import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';

import MemberLifecycleDialog from '../MemberLifecycleDialog';

const theme = createTheme({ cssVariables: true });

function renderDialog(action, onConfirm = vi.fn()) {
  render(
    <ThemeProvider theme={theme}>
      <MemberLifecycleDialog
        open
        action={action}
        member={{ id: 17, fullName: 'أحمد المختبر' }}
        affectedDependents={2}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />
    </ThemeProvider>
  );
  return onConfirm;
}

describe('MemberLifecycleDialog', () => {
  it('requires a non-blank reason before terminating a membership', () => {
    const onConfirm = renderDialog('TERMINATE');
    const confirm = screen.getByRole('button', { name: 'تأكيد إنهاء العضوية' });

    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText('سبب العملية *'), { target: { value: '   ' } });
    expect(confirm).toBeDisabled();

    fireEvent.change(screen.getByLabelText('سبب العملية *'), { target: { value: 'انتهاء التعاقد' } });
    fireEvent.click(confirm);

    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onConfirm).toHaveBeenCalledWith('انتهاء التعاقد');
    expect(screen.getByText(/لن تُحذف المطالبات أو الزيارات/)).toBeInTheDocument();
  });

  it('requires both a reason and the exact confirmation phrase for hard delete', () => {
    const onConfirm = renderDialog('HARD_DELETE');
    const confirm = screen.getByRole('button', { name: 'حذف نهائي' });

    fireEvent.change(screen.getByLabelText('سبب العملية *'), { target: { value: 'سجل أُنشئ بالخطأ' } });
    fireEvent.change(screen.getByLabelText('اكتب: حذف نهائي'), { target: { value: 'حذف' } });
    expect(confirm).toBeDisabled();

    fireEvent.change(screen.getByLabelText('اكتب: حذف نهائي'), { target: { value: 'حذف نهائي' } });
    fireEvent.click(confirm);

    expect(onConfirm).toHaveBeenCalledWith('سجل أُنشئ بالخطأ');
    expect(screen.getByText(/إجراء غير قابل للتراجع/)).toBeInTheDocument();
  });
});
