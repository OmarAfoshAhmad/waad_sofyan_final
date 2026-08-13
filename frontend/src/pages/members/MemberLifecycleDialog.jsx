import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';

const ACTIONS = {
  SUSPEND: { title: 'تعليق المستفيد', color: 'warning', confirm: 'تأكيد التعليق', warning: 'يتوقف الاستحقاق مؤقتاً ويبقى السجل كاملاً.' },
  RESTORE: { title: 'استعادة المستفيد', color: 'success', confirm: 'تأكيد الاستعادة', warning: 'سيُعاد فحص الوثيقة قبل تفعيل الاستحقاق.' },
  TERMINATE: { title: 'إنهاء العضوية', color: 'warning', confirm: 'تأكيد إنهاء العضوية', warning: 'لن تُحذف المطالبات أو الزيارات أو السجلات السابقة.' },
  HARD_DELETE: { title: 'حذف نهائي', color: 'error', confirm: 'حذف نهائي', warning: 'إجراء غير قابل للتراجع، ولا يُسمح به عند وجود أثر مالي أو طبي.' }
};

const MemberLifecycleDialog = ({ open, action, member, affectedDependents = 0, loading = false, onClose, onConfirm }) => {
  const [reason, setReason] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const config = ACTIONS[action] || ACTIONS.SUSPEND;
  const hardDelete = action === 'HARD_DELETE';

  useEffect(() => {
    if (open) { setReason(''); setConfirmation(''); }
  }, [open, action, member?.id]);

  const valid = reason.trim().length > 0 && (!hardDelete || confirmation.trim() === 'حذف نهائي');

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{config.title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>المستفيد: <strong>{member?.fullName || '—'}</strong></DialogContentText>
        {affectedDependents > 0 && action !== 'RESTORE' && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            ستشمل العملية {affectedDependents} من أفراد الأسرة العاملين حالياً. الحالات المستقلة لن تتغير.
          </Alert>
        )}
        <Alert severity={config.color} sx={{ mb: 2 }}>{config.warning}</Alert>
        <TextField autoFocus fullWidth multiline minRows={3} label="سبب العملية *" value={reason}
          onChange={(event) => setReason(event.target.value)} inputProps={{ maxLength: 500 }} />
        {hardDelete && (
          <TextField fullWidth sx={{ mt: 2 }} label="اكتب: حذف نهائي" value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            error={confirmation.length > 0 && confirmation !== 'حذف نهائي'} helperText="تأكيد إضافي مطلوب للحذف المادي" />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>إلغاء</Button>
        <Button color={config.color} variant="contained" disabled={loading || !valid} onClick={() => onConfirm(reason.trim())}>
          {config.confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

MemberLifecycleDialog.propTypes = {
  open: PropTypes.bool.isRequired, action: PropTypes.oneOf(Object.keys(ACTIONS)).isRequired,
  member: PropTypes.object, affectedDependents: PropTypes.number, loading: PropTypes.bool,
  onClose: PropTypes.func.isRequired, onConfirm: PropTypes.func.isRequired
};

export default MemberLifecycleDialog;
