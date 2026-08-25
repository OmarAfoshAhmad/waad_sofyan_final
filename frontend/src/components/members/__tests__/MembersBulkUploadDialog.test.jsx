import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import MembersBulkUploadDialog from '../MembersBulkUploadDialog';
import { executeImport, previewImport } from 'services/api/unified-members.service';

const enqueueSnackbar = vi.fn();

vi.mock('notistack', () => ({ useSnackbar: () => ({ enqueueSnackbar }) }));
vi.mock('hooks/useAuth', () => ({
  default: () => ({ user: { role: 'DATA_ENTRY' } })
}));
vi.mock('services/api/unified-members.service', () => ({
  downloadTemplate: vi.fn(), previewImport: vi.fn(), executeImport: vi.fn()
}));
vi.mock('components/tba/EmployerFilterSelector', () => ({
  default: ({ onEmployerChange, disabled }) => (
    <button type="button" disabled={disabled} onClick={() => onEmployerChange({ id: 77 })}>
      اختر جهة الاختبار
    </button>
  )
}));

describe('MembersBulkUploadDialog import contract', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    previewImport.mockResolvedValue({
      data: { batchId: 'preview-1', headerRowNumber: 2, totalRows: 3, validRows: 3, invalidRows: 0, canProceed: true }
    });
    executeImport.mockResolvedValue({
      data: { success: true, summary: { totalRows: 3, created: 3, skipped: 0, rejected: 0, failed: 0 } }
    });
  });

  it('requires preview before execute and carries the preview identity into execution', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    render(<MembersBulkUploadDialog open onClose={vi.fn()} onSuccess={onSuccess} />);

    await user.click(screen.getByRole('button', { name: 'اختر جهة الاختبار' }));
    const file = new File(['xlsx'], 'members.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    await user.upload(document.querySelector('input[type="file"]'), file);
    await user.click(screen.getByRole('button', { name: 'معاينة الملف' }));
    await screen.findByText('نتيجة المعاينة قبل التنفيذ');

    expect(previewImport).toHaveBeenCalledWith(file, { employerId: 77, clearOldMembers: false });
    expect(executeImport).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'تأكيد وتنفيذ الاستيراد' }));
    await waitFor(() => expect(executeImport).toHaveBeenCalledWith(file, {
      employerId: 77, batchId: 'preview-1', headerRowNumber: 2, clearOldMembers: false
    }));
    expect(onSuccess).toHaveBeenCalled();
  });

  it('does not execute a preview that contains no valid rows', async () => {
    previewImport.mockResolvedValue({
      data: { batchId: 'preview-bad', totalRows: 2, validRows: 0, invalidRows: 2, canProceed: false }
    });
    const user = userEvent.setup();
    render(<MembersBulkUploadDialog open onClose={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'اختر جهة الاختبار' }));
    await user.upload(document.querySelector('input[type="file"]'), new File(['xlsx'], 'bad.xlsx'));
    await user.click(screen.getByRole('button', { name: 'معاينة الملف' }));

    expect(await screen.findByRole('button', { name: 'تأكيد وتنفيذ الاستيراد' })).toBeDisabled();
    expect(executeImport).not.toHaveBeenCalled();
  });
});
