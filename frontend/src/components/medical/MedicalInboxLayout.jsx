/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 MEDICAL INBOX LAYOUT - Professional Split Screen (Desktop-First)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Architecture:
 * ┌────────────────────────────────────────────────────────────────────┐
 * │ Header: Claim # | Visit # | Member | Provider | Status             │
 * ├─────────────────────────┬──────────────────────────────────────────┤
 * │ LEFT (60%)              │ RIGHT (40%)                              │
 * │ ClaimReviewPanel        │ DocumentsViewer                          │
 * │                         │                                          │
 * │ • Member Card           │ • Documents List                         │
 * │ • Visit Summary         │ • Live Preview                           │
 * │ • Medical Context       │                                          │
 * │ • Financial Snapshot    │                                          │
 * ├─────────────────────────┴──────────────────────────────────────────┤
 * │ Footer Actions: Approve | Reject | Request Docs | Hold             │
 * └────────────────────────────────────────────────────────────────────┘
 *
 * Features:
 * ✓ Desktop-First (min 1280px)
 * ✓ Sticky header & footer
 * ✓ Keyboard shortcuts (A=Approve, R=Reject, ←→=Navigate)
 * ✓ Zero-click waste
 * ✓ Medical-grade focus
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

import { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  Stack,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  Tooltip,
  Kbd
} from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  RequestPage as RequestDocsIcon,
  Pause as HoldIcon,
  ArrowBack as BackIcon,
  Info as InfoIcon
} from '@mui/icons-material';
import ClaimReviewPanel from './ClaimReviewPanel';
import DocumentsViewer from './DocumentsViewer';
import { MEDICAL_THEME } from '../../theme/medical-theme';

const MedicalInboxLayout = ({
  claim,
  type = 'claim', // 'claim' or 'preauth'
  documents = [],
  onApprove,
  onReject,
  onRequestDocs,
  onHold,
  onBack,
  loading = false,
  canApprove = true,
  canReject = true
}) => {
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [holdDialogOpen, setHoldDialogOpen] = useState(false);
  const [requestDocsDialogOpen, setRequestDocsDialogOpen] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('');
  const [holdReason, setHoldReason] = useState('');
  const [requestedDocs, setRequestedDocs] = useState('');

  const isPreAuth = type === 'preauth';

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyPress = (e) => {
      // Ignore if typing in input
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
        return;
      }

      if (e.key === 'a' || e.key === 'A') {
        if (canApprove) handleApproveClick();
      } else if (e.key === 'r' || e.key === 'R') {
        if (canReject) setRejectDialogOpen(true);
      } else if (e.key === 'h' || e.key === 'H') {
        setHoldDialogOpen(true);
      } else if (e.key === 'd' || e.key === 'D') {
        setRequestDocsDialogOpen(true);
      } else if (e.key === 'Escape') {
        if (rejectDialogOpen) setRejectDialogOpen(false);
        if (holdDialogOpen) setHoldDialogOpen(false);
        if (requestDocsDialogOpen) setRequestDocsDialogOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [canApprove, canReject, rejectDialogOpen, holdDialogOpen, requestDocsDialogOpen]);

  // Handlers
  const handleApproveClick = () => {
    if (onApprove && !loading) {
      onApprove(claim);
    }
  };

  const handleRejectSubmit = () => {
    if (onReject && rejectionReason.trim()) {
      onReject(claim, rejectionReason);
      setRejectDialogOpen(false);
      setRejectionReason('');
    }
  };

  const handleHoldSubmit = () => {
    if (onHold && holdReason.trim()) {
      onHold(claim, holdReason);
      setHoldDialogOpen(false);
      setHoldReason('');
    }
  };

  const handleRequestDocsSubmit = () => {
    if (onRequestDocs && requestedDocs.trim()) {
      onRequestDocs(claim, requestedDocs);
      setRequestDocsDialogOpen(false);
      setRequestedDocs('');
    }
  };

  // Keyboard Hint Component
  const KeyboardHint = ({ keys }) => (
    <Box
      component="span"
      sx={{
        ...MEDICAL_THEME.keyboardHint,
        ml: 1
      }}
    >
      {keys}
    </Box>
  );

  return (
    <Box
      sx={{
        minWidth: MEDICAL_THEME.breakpoints.desktop,
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        background: MEDICAL_THEME.colors.background.default
      }}
    >
      {/* ════════════════════════════════════════════════════════════ */}
      {/* STICKY HEADER */}
      {/* ════════════════════════════════════════════════════════════ */}
      <Paper
        elevation={2}
        sx={{
          ...MEDICAL_THEME.components.header,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}
      >
        <Stack direction="row" spacing={2} alignItems="center">
          {onBack && (
            <IconButton onClick={onBack} sx={{ mr: 1 }}>
              <BackIcon />
            </IconButton>
          )}

          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {isPreAuth ? '🔍 مراجعة موافقة مسبقة' : '🔍 مراجعة مطالبة'}
          </Typography>

          {claim && (
            <>
              <Chip label={`#${claim.claimNumber || claim.preAuthNumber || claim.id}`} color="primary" sx={{ fontWeight: 600 }} />

              {claim.visitNumber && <Chip label={`زيارة #${claim.visitNumber}`} variant="outlined" />}

              <Chip label={claim.memberName || claim.member?.name} icon={<InfoIcon />} />
            </>
          )}
        </Stack>

        {/* Status Chip */}
        {claim && (
          <Chip
            label={claim.status}
            sx={{
              background: MEDICAL_THEME.colors.status.pending.bg,
              color: MEDICAL_THEME.colors.status.pending.text,
              fontWeight: 700,
              fontSize: '0.875rem'
            }}
          />
        )}
      </Paper>

      {/* ════════════════════════════════════════════════════════════ */}
      {/* SPLIT SCREEN CONTENT */}
      {/* ════════════════════════════════════════════════════════════ */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          overflow: 'hidden'
        }}
      >
        {/* LEFT PANEL - Claim Review (60%) */}
        <Box
          sx={{
            width: MEDICAL_THEME.components.splitScreen.leftWidth,
            overflow: 'auto',
            borderRight: `1px solid ${MEDICAL_THEME.colors.border.light}`
          }}
        >
          <ClaimReviewPanel claim={claim} type={type} />
        </Box>

        {/* RIGHT PANEL - Documents Viewer (40%) */}
        <Box
          sx={{
            width: MEDICAL_THEME.components.splitScreen.rightWidth,
            overflow: 'hidden'
          }}
        >
          <DocumentsViewer documents={documents} entityId={claim?.id} entityType={isPreAuth ? 'PRE_AUTH' : 'CLAIM'} />
        </Box>
      </Box>

      {/* ════════════════════════════════════════════════════════════ */}
      {/* STICKY FOOTER - ACTIONS */}
      {/* ════════════════════════════════════════════════════════════ */}
      <Paper
        elevation={3}
        sx={{
          ...MEDICAL_THEME.components.footer,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
      >
        {/* Left: Info */}
        <Typography variant="caption" color="text.secondary">
          استخدم اختصارات لوحة المفاتيح للسرعة
        </Typography>

        {/* Right: Action Buttons */}
        <Stack direction="row" spacing={2}>
          {onRequestDocs && (
            <Button
              variant="outlined"
              startIcon={<RequestDocsIcon />}
              onClick={() => setRequestDocsDialogOpen(true)}
              disabled={loading}
              sx={{ minWidth: '8.75rem' }}
            >
              طلب مستندات
              <KeyboardHint keys="D" />
            </Button>
          )}

          {onHold && (
            <Button
              variant="outlined"
              color="warning"
              startIcon={<HoldIcon />}
              onClick={() => setHoldDialogOpen(true)}
              disabled={loading}
              sx={{ minWidth: '7.5rem' }}
            >
              تعليق
              <KeyboardHint keys="H" />
            </Button>
          )}

          {onReject && canReject && (
            <Button
              variant="outlined"
              color="error"
              startIcon={<RejectIcon />}
              onClick={() => setRejectDialogOpen(true)}
              disabled={loading}
              sx={{ minWidth: '7.5rem' }}
            >
              رفض
              <KeyboardHint keys="R" />
            </Button>
          )}

          {onApprove && canApprove && (
            <Button
              variant="contained"
              color="success"
              startIcon={<ApproveIcon />}
              onClick={handleApproveClick}
              disabled={loading}
              sx={{
                minWidth: '8.75rem',
                fontWeight: 700,
                fontSize: '1rem'
              }}
            >
              موافقة
              <KeyboardHint keys="A" />
            </Button>
          )}
        </Stack>
      </Paper>

      {/* ════════════════════════════════════════════════════════════ */}
      {/* DIALOGS */}
      {/* ════════════════════════════════════════════════════════════ */}

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>رفض {isPreAuth ? 'الموافقة المسبقة' : 'المطالبة'}</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: '1.0rem' }}>
            يرجى تحديد سبب الرفض بوضوح
          </Alert>
          <TextField
            fullWidth
            multiline
            rows={4}
            label="سبب الرفض"
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            placeholder="اكتب السبب التفصيلي للرفض..."
            required
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>إلغاء</Button>
          <Button variant="contained" color="error" onClick={handleRejectSubmit} disabled={!rejectionReason.trim() || loading}>
            تأكيد الرفض
          </Button>
        </DialogActions>
      </Dialog>

      {/* Hold Dialog */}
      <Dialog open={holdDialogOpen} onClose={() => setHoldDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>تعليق المراجعة</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={3}
            label="سبب التعليق"
            value={holdReason}
            onChange={(e) => setHoldReason(e.target.value)}
            placeholder="لماذا تريد تعليق هذه المراجعة؟"
            required
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setHoldDialogOpen(false)}>إلغاء</Button>
          <Button variant="contained" color="warning" onClick={handleHoldSubmit} disabled={!holdReason.trim() || loading}>
            تعليق
          </Button>
        </DialogActions>
      </Dialog>

      {/* Request Documents Dialog */}
      <Dialog open={requestDocsDialogOpen} onClose={() => setRequestDocsDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>طلب مستندات إضافية</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={4}
            label="المستندات المطلوبة"
            value={requestedDocs}
            onChange={(e) => setRequestedDocs(e.target.value)}
            placeholder="حدد المستندات المطلوبة بوضوح..."
            required
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRequestDocsDialogOpen(false)}>إلغاء</Button>
          <Button variant="contained" onClick={handleRequestDocsSubmit} disabled={!requestedDocs.trim() || loading}>
            إرسال الطلب
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MedicalInboxLayout;
