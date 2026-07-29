import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';

/**
 * Prompts the user to restore an in-progress claim draft (server or local
 * backup) when one is found on page load. Extracted from ClaimBatchEntry.jsx.
 */
export function RecoveryDialog({ recoveryDialog, onRestoreServer, onRestoreLocal, onDismiss }) {
  return (
    <Dialog open={recoveryDialog.open} onClose={onDismiss} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>استرجاع المسودة</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 1 }}>
          تم العثور على بيانات محفوظة لهذه الدفعة. هل تريد استكمال الإدخال من المسودة؟
        </Typography>
        {recoveryDialog.serverDraft?.data && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            توجد مسودة محفوظة على الخادم.
          </Typography>
        )}
        {recoveryDialog.localDraft?.data && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            توجد نسخة احتياطية على هذا الجهاز.
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        {recoveryDialog.serverDraft?.data && (
          <Button onClick={onRestoreServer} variant="contained">
            استكمال من المسودة
          </Button>
        )}
        {recoveryDialog.localDraft?.data && (
          <Button onClick={onRestoreLocal} variant="outlined">
            استرجاع من الجهاز
          </Button>
        )}
        <Button onClick={onDismiss} color="inherit">
          تجاهل
        </Button>
      </DialogActions>
    </Dialog>
  );
}
