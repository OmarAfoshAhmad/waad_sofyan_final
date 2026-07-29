import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';

/** Confirms cancelling/deleting a claim from the batch. Extracted from ClaimBatchEntry.jsx. */
export function ConfirmDeleteClaimDialog({ confirmDeleteId, onCancel, onConfirm }) {
  return (
    <Dialog open={!!confirmDeleteId} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 600, color: 'error.main' }}>تأكيد إلغاء المطالبة</DialogTitle>
      <DialogContent>
        <Typography>هل أنت متأكد من رغبتك في إلغاء المطالبة رقم #{confirmDeleteId}؟</Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          سيتم استرجاع الأموال لسقف العضو تلقائياً.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ p: '1.0rem' }}>
        <Button onClick={onCancel} color="inherit">
          تراجع
        </Button>
        <Button onClick={onConfirm} variant="contained" color="error">
          تأكيد الإلغاء
        </Button>
      </DialogActions>
    </Dialog>
  );
}
