/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🩺 MEDICAL DECISION PANEL - Right Fixed Command Console
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Fixed right panel for medical reviewer decisions
 * Always visible - No scrolling required
 *
 * Features:
 * ✓ Status display
 * ✓ Medical notes textarea
 * ✓ Decision buttons (Approve/Reject/Request Info)
 * ✓ Clear visual hierarchy
 * ✓ Confirmation dialogs
 * ✓ Command console feel
 *
 * @version 1.0
 * @date 2026-02-07
 */

import { useState, useCallback, memo } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Chip,
  Stack,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Alert,
  CircularProgress,
  alpha,
  useTheme
} from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Info as RequestInfoIcon,
  Send as SubmitIcon,
  Pending as PendingIcon
} from '@mui/icons-material';

// ============================================================================
// STATUS CONFIGURATIONS
// ============================================================================

const STATUS_CONFIGS = {
  PENDING: {
    label: 'قيد المراجعة',
    color: 'warning',
    icon: <PendingIcon />
  },
  PENDING_REVIEW: {
    label: 'قيد المراجعة',
    color: 'warning',
    icon: <PendingIcon />
  },
  UNDER_REVIEW: {
    label: 'قيد المراجعة الطبية',
    color: 'info',
    icon: <PendingIcon />
  },
  APPROVED: {
    label: 'موافق عليه',
    color: 'success',
    icon: <ApproveIcon />
  },
  REJECTED: {
    label: 'مرفوض',
    color: 'error',
    icon: <RejectIcon />
  },
  NEEDS_CORRECTION: {
    label: 'يتطلب معلومات إضافية',
    color: 'warning',
    icon: <RequestInfoIcon />
  },
  PENDING_DOCUMENTS: {
    label: 'مطلوب مستندات',
    color: 'warning',
    icon: <RequestInfoIcon />
  }
};

// ============================================================================
// DECISION ACTIONS
// ============================================================================

const DECISION_ACTIONS = {
  APPROVE: 'approve',
  REJECT: 'reject',
  REQUEST_INFO: 'request_info'
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

/**
 * Medical Decision Panel
 *
 * Right-side fixed panel for reviewer actions
 */
const MedicalDecisionPanel = ({
  status,
  notes = '',
  onNotesChange,
  onApprove,
  onReject,
  onRequestInfo,
  loading = false,
  disabled = false,
  canApprove = true,
  canReject = true,
  canRequestInfo = true,
  width = 360,
  height = 'calc(100vh - 180px)',
  approveLabel = 'الموافقة',
  rejectLabel = 'الرفض',
  requestInfoLabel = 'طلب معلومات إضافية',
  notesPlaceholder = 'الملاحظات الطبية...',
  notesLabel = 'ملاحظات المراجع الطبي',
  confirmApprove = true,
  confirmReject = true,
  confirmRequestInfo = false
}) => {
  const theme = useTheme();

  // Dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pendingAction, setPendingAction] = useState(null);

  // Get status configuration
  const statusConfig = STATUS_CONFIGS[status] || STATUS_CONFIGS.PENDING;

  // Handle decision action
  const handleAction = useCallback(
    (action) => {
      const needsConfirmation =
        (action === DECISION_ACTIONS.APPROVE && confirmApprove) ||
        (action === DECISION_ACTIONS.REJECT && confirmReject) ||
        (action === DECISION_ACTIONS.REQUEST_INFO && confirmRequestInfo);

      if (needsConfirmation) {
        setPendingAction(action);
        setDialogOpen(true);
      } else {
        executeAction(action);
      }
    },
    [confirmApprove, confirmReject, confirmRequestInfo]
  );

  // Execute action
  const executeAction = useCallback(
    (action) => {
      switch (action) {
        case DECISION_ACTIONS.APPROVE:
          onApprove?.(notes);
          break;
        case DECISION_ACTIONS.REJECT:
          onReject?.(notes);
          break;
        case DECISION_ACTIONS.REQUEST_INFO:
          onRequestInfo?.(notes);
          break;
        default:
          break;
      }
      setDialogOpen(false);
      setPendingAction(null);
    },
    [notes, onApprove, onReject, onRequestInfo]
  );

  // Cancel dialog
  const handleCancel = useCallback(() => {
    setDialogOpen(false);
    setPendingAction(null);
  }, []);

  // Get confirmation dialog content
  const getDialogContent = () => {
    switch (pendingAction) {
      case DECISION_ACTIONS.APPROVE:
        return {
          title: 'تأكيد الموافقة',
          message: 'هل أنت متأكد من الموافقة على هذا الطلب؟',
          confirmText: approveLabel,
          confirmColor: 'success'
        };
      case DECISION_ACTIONS.REJECT:
        return {
          title: 'تأكيد الرفض',
          message: 'هل أنت متأكد من رفض هذا الطلب؟',
          confirmText: rejectLabel,
          confirmColor: 'error'
        };
      case DECISION_ACTIONS.REQUEST_INFO:
        return {
          title: 'طلب معلومات إضافية',
          message: 'سيتم إرجاع الطلب لمقدم الخدمة لإضافة المعلومات المطلوبة.',
          confirmText: requestInfoLabel,
          confirmColor: 'warning'
        };
      default:
        return {
          title: '',
          message: '',
          confirmText: 'تأكيد',
          confirmColor: 'primary'
        };
    }
  };

  const dialogContent = getDialogContent();

  return (
    <>
      <Paper
        elevation={2}
        sx={{
          width,
          height,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          borderRadius: '0.25rem',
          border: 1,
          borderColor: 'divider'
        }}
      >
        {/* Header */}
        <Box
          sx={{
            px: '1.0rem',
            py: '0.75rem',
            bgcolor: theme.palette.mode === 'dark' ? 'grey.900' : 'grey.50',
            borderBottom: 1,
            borderColor: 'divider'
          }}
        >
          <Typography variant="subtitle2" fontWeight={600}>
            لوحة القرار الطبي
          </Typography>
        </Box>

        {/* Status */}
        <Box sx={{ p: '1.0rem', borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
            الحالة الحالية
          </Typography>
          <Chip icon={statusConfig.icon} label={statusConfig.label} color={statusConfig.color} sx={{ fontWeight: 500 }} />
        </Box>

        {/* Notes */}
        <Box sx={{ p: '1.0rem', flex: 1, overflow: 'auto' }}>
          <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
            {notesLabel}
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={8}
            placeholder={notesPlaceholder}
            value={notes}
            onChange={(e) => onNotesChange?.(e.target.value)}
            disabled={disabled || loading}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: 'background.paper'
              }
            }}
          />
          <Typography variant="caption" color="text.disabled" sx={{ mt: 1, display: 'block' }}>
            يمكنك إضافة ملاحظات طبية توضح سبب القرار
          </Typography>
        </Box>

        <Divider />

        {/* Decision Buttons */}
        <Box sx={{ p: '1.0rem' }}>
          <Stack spacing={1.5}>
            {/* Approve */}
            {canApprove && (
              <Button
                fullWidth
                variant="contained"
                color="success"
                size="large"
                startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <ApproveIcon />}
                onClick={() => handleAction(DECISION_ACTIONS.APPROVE)}
                disabled={disabled || loading}
                sx={{
                  fontWeight: 600,
                  py: '0.75rem',
                  boxShadow: 2,
                  '&:hover': {
                    boxShadow: 4
                  }
                }}
              >
                {approveLabel}
              </Button>
            )}

            {/* Reject */}
            {canReject && (
              <Button
                fullWidth
                variant="contained"
                color="error"
                size="large"
                startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <RejectIcon />}
                onClick={() => handleAction(DECISION_ACTIONS.REJECT)}
                disabled={disabled || loading}
                sx={{
                  fontWeight: 600,
                  py: '0.75rem',
                  boxShadow: 2,
                  '&:hover': {
                    boxShadow: 4
                  }
                }}
              >
                {rejectLabel}
              </Button>
            )}

            {/* Request Info */}
            {canRequestInfo && (
              <Button
                fullWidth
                variant="outlined"
                color="warning"
                size="large"
                startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <RequestInfoIcon />}
                onClick={() => handleAction(DECISION_ACTIONS.REQUEST_INFO)}
                disabled={disabled || loading}
                sx={{
                  fontWeight: 600,
                  py: '0.75rem'
                }}
              >
                {requestInfoLabel}
              </Button>
            )}
          </Stack>

          {/* Helper text */}
          <Alert severity="info" sx={{ mt: '1.0rem' }} icon={false}>
            <Typography variant="caption">جميع القرارات يتم حفظها تلقائياً ولا يمكن التراجع عنها</Typography>
          </Alert>
        </Box>
      </Paper>

      {/* Confirmation Dialog */}
      <Dialog open={dialogOpen} onClose={handleCancel} maxWidth="xs" fullWidth>
        <DialogTitle>{dialogContent.title}</DialogTitle>
        <DialogContent>
          <DialogContentText>{dialogContent.message}</DialogContentText>
          {notes && (
            <Alert severity="info" sx={{ mt: '1.0rem' }}>
              <Typography variant="caption" fontWeight={600}>
                الملاحظات:
              </Typography>
              <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
                {notes}
              </Typography>
            </Alert>
          )}
        </DialogContent>
        <DialogActions sx={{ px: '1.5rem', pb: '1.0rem' }}>
          <Button onClick={handleCancel} disabled={loading}>
            إلغاء
          </Button>
          <Button
            onClick={() => executeAction(pendingAction)}
            color={dialogContent.confirmColor}
            variant="contained"
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <SubmitIcon />}
          >
            {dialogContent.confirmText}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

MedicalDecisionPanel.propTypes = {
  /** Current status */
  status: PropTypes.string.isRequired,
  /** Medical notes value */
  notes: PropTypes.string,
  /** Notes change handler */
  onNotesChange: PropTypes.func,
  /** Approve handler */
  onApprove: PropTypes.func,
  /** Reject handler */
  onReject: PropTypes.func,
  /** Request info handler */
  onRequestInfo: PropTypes.func,
  /** Loading state */
  loading: PropTypes.bool,
  /** Disabled state */
  disabled: PropTypes.bool,
  /** Can approve */
  canApprove: PropTypes.bool,
  /** Can reject */
  canReject: PropTypes.bool,
  /** Can request info */
  canRequestInfo: PropTypes.bool,
  /** Panel width */
  width: PropTypes.number,
  /** Panel height */
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  /** Approve button label */
  approveLabel: PropTypes.string,
  /** Reject button label */
  rejectLabel: PropTypes.string,
  /** Request info button label */
  requestInfoLabel: PropTypes.string,
  /** Notes placeholder */
  notesPlaceholder: PropTypes.string,
  /** Notes label */
  notesLabel: PropTypes.string,
  /** Confirm approve action */
  confirmApprove: PropTypes.bool,
  /** Confirm reject action */
  confirmReject: PropTypes.bool,
  /** Confirm request info action */
  confirmRequestInfo: PropTypes.bool
};

export default memo(MedicalDecisionPanel);


