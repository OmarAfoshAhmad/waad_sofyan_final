import { alpha, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import { WarningAmber as WarningIcon } from '@mui/icons-material';

/**
 * Generic "are you sure?" confirmation dialog used across ClaimBatchEntry
 * (e.g. deleting a line). Extracted from ClaimBatchEntry.jsx.
 */
export function ActionConfirmDialog({ actionConfirm, onClose }) {
  const severity = actionConfirm.severity || 'error';
  return (
    <Dialog open={actionConfirm.open} onClose={onClose}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, color: `${severity}.main` }}>
        <WarningIcon color={severity} />
        {actionConfirm.title}
      </DialogTitle>
      <DialogContent>
        <Typography>{actionConfirm.message}</Typography>
      </DialogContent>
      <DialogActions sx={{ p: 2, bgcolor: (theme) => alpha(theme.palette[severity].main, 0.06) }}>
        <Button onClick={onClose} color="inherit">
          تراجع
        </Button>
        <Button
          onClick={() => {
            actionConfirm.onConfirm();
            onClose();
          }}
          variant="contained"
          color={severity}
        >
          متابعة العملية
        </Button>
      </DialogActions>
    </Dialog>
  );
}
