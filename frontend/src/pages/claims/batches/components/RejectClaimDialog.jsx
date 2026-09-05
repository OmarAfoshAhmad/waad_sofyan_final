import {
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Radio,
  RadioGroup,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  AddCircleOutline as AddReasonIcon,
  Cancel as CancelIcon,
  Check as CheckIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  ExpandMore as ExpandMoreIcon
} from '@mui/icons-material';

/**
 * Rejects an entire claim, or a single line, with a reason picked from (or
 * added to) the shared rejection-reasons list. Also manages that list
 * in-place (add/edit/delete). Extracted from ClaimBatchEntry.jsx — this is
 * the largest of its 5 inline dialogs.
 */
export function RejectClaimDialog({
  open,
  onClose,
  rejectType,
  rejectIdx,
  lines,
  rejectionMode,
  onRejectionModeChange,
  manualRefusedAmountInput,
  onManualRefusedAmountChange,
  rejectionReasons,
  rejectionInput,
  onRejectionInputChange,
  isSavingNewReason,
  onSaveNewReason,
  editingReasonId,
  editingReasonText,
  onEditingReasonTextChange,
  onStartEditReason,
  onSaveEditedReason,
  onCancelEditReason,
  isDeletingReasonId,
  onDeleteReason,
  showReasonsList,
  onToggleReasonsList,
  onConfirm
}) {
  const currentLine = rejectIdx != null ? lines[rejectIdx] : null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '0.375rem' } }}>
      <DialogTitle sx={{ fontWeight: 700, color: 'error.main', pb: 1 }}>
        {rejectType === 'claim' ? 'رفض المطالبة — تحديد السبب' : 'رفض البند — تحديد السبب'}
      </DialogTitle>
      <DialogContent sx={{ pt: '0.75rem !important' }}>
        {rejectType === 'line' && (
          <Box sx={{ mb: 2 }}>
            <RadioGroup
              row
              value={rejectionMode}
              onChange={(e) => onRejectionModeChange(e.target.value)}
            >
              <FormControlLabel
                value="full"
                control={<Radio size="small" color="error" />}
                label={<Typography sx={{ fontSize: '0.85rem', fontWeight: 600 }}>رفض كلي (التزام الشركة كاملاً)</Typography>}
              />
              <FormControlLabel
                value="partial"
                control={<Radio size="small" color="warning" />}
                label={<Typography sx={{ fontSize: '0.85rem', fontWeight: 600 }}>رفض جزئي (تحديد مبلغ)</Typography>}
              />
            </RadioGroup>

            {rejectionMode === 'partial' && (
              <TextField
                fullWidth
                size="small"
                type="number"
                label={`مبلغ الرفض من التزام الشركة (الحد الأقصى: ${(currentLine?.byCompany ?? 0).toFixed(2)} د.ل)`}
                value={manualRefusedAmountInput}
                onChange={(e) => onManualRefusedAmountChange(e.target.value)}
                inputProps={{ min: 0.01, max: currentLine?.byCompany ?? 0, step: 0.01 }}
                helperText="يُطبَّق على التزام الشركة فقط — التزام المستفيد لا يتأثر"
                error={parseFloat(manualRefusedAmountInput) > (currentLine?.byCompany ?? 0)}
                sx={{ mt: 1.5 }}
                autoFocus
              />
            )}
          </Box>
        )}

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          اختر سبباً من القائمة أو اكتب سبباً جديداً
        </Typography>
        <Autocomplete
          freeSolo
          options={rejectionReasons.map((r) => r.reasonText)}
          value={rejectionInput}
          onChange={(_, val) => onRejectionInputChange(val || '')}
          onInputChange={(_, val) => onRejectionInputChange(val)}
          renderInput={(params) => (
            <TextField
              {...params}
              autoFocus={rejectType === 'claim' || rejectionMode === 'full'}
              fullWidth
              size="small"
              label="سبب الرفض"
              placeholder="اختر أو اكتب سبباً..."
              error={!rejectionInput?.trim()}
            />
          )}
          noOptionsText="لا توجد أسباب — اكتب سبباً جديداً"
        />
        {rejectionInput?.trim() && !rejectionReasons.some((r) => r.reasonText === rejectionInput.trim()) && (
          <Box sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="caption" color="text.secondary">
              سبب جديد — يمكنك حفظه في القائمة:
            </Typography>
            <Button
              size="small"
              startIcon={isSavingNewReason ? <CircularProgress size={12} /> : <AddReasonIcon sx={{ fontSize: '0.9rem' }} />}
              onClick={onSaveNewReason}
              disabled={isSavingNewReason}
              sx={{ fontSize: '0.75rem', textTransform: 'none' }}
            >
              حفظ في القائمة
            </Button>
          </Box>
        )}

        <Box sx={{ mt: 2, borderTop: '1px solid', borderColor: 'divider', pt: 1.5 }}>
          <Button
            size="small"
            endIcon={<ExpandMoreIcon sx={{ fontSize: '0.9rem', transform: showReasonsList ? 'rotate(180deg)' : 'none', transition: '0.2s' }} />}
            onClick={onToggleReasonsList}
            sx={{ fontSize: '0.75rem', textTransform: 'none', color: 'text.secondary', p: 0 }}
          >
            إدارة قائمة الأسباب المحفوظة ({rejectionReasons.length})
          </Button>
          {showReasonsList && (
            <Box sx={{ mt: 1, maxHeight: '13rem', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {rejectionReasons.map((r) => (
                <Box
                  key={r.id}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5,
                    px: 1,
                    py: 0.4,
                    borderRadius: '0.25rem',
                    bgcolor: editingReasonId === r.id ? 'action.selected' : 'action.hover'
                  }}
                >
                  {editingReasonId === r.id ? (
                    <>
                      <TextField
                        size="small"
                        variant="standard"
                        fullWidth
                        value={editingReasonText}
                        onChange={(e) => onEditingReasonTextChange(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') onSaveEditedReason();
                          if (e.key === 'Escape') onCancelEditReason();
                        }}
                        autoFocus
                        inputProps={{ style: { fontSize: '0.8rem', textAlign: 'right' } }}
                      />
                      <Tooltip title="حفظ التعديل" arrow>
                        <IconButton size="small" color="success" onClick={onSaveEditedReason}>
                          <CheckIcon sx={{ fontSize: '0.9rem' }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="إلغاء" arrow>
                        <IconButton size="small" onClick={onCancelEditReason}>
                          <CancelIcon sx={{ fontSize: '0.9rem' }} />
                        </IconButton>
                      </Tooltip>
                    </>
                  ) : (
                    <>
                      <Typography
                        variant="caption"
                        sx={{ flexGrow: 1, fontSize: '0.8rem', cursor: 'pointer' }}
                        onClick={() => onRejectionInputChange(r.reasonText)}
                      >
                        {r.reasonText}
                      </Typography>
                      <Tooltip title="تعديل" arrow>
                        <IconButton size="small" onClick={() => onStartEditReason(r)}>
                          <EditIcon sx={{ fontSize: '0.85rem', color: 'text.secondary' }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="حذف" arrow>
                        <IconButton size="small" color="error" disabled={isDeletingReasonId === r.id} onClick={() => onDeleteReason(r.id)}>
                          {isDeletingReasonId === r.id ? <CircularProgress size={12} /> : <DeleteIcon sx={{ fontSize: '0.85rem' }} />}
                        </IconButton>
                      </Tooltip>
                    </>
                  )}
                </Box>
              ))}
              {rejectionReasons.length === 0 && (
                <Typography variant="caption" color="text.disabled" sx={{ px: 1 }}>
                  لا توجد أسباب محفوظة
                </Typography>
              )}
            </Box>
          )}
        </Box>
      </DialogContent>
      <DialogActions sx={{ p: '1.0rem', gap: 1 }}>
        <Button onClick={onClose} color="inherit">
          إلغاء
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          color={rejectionMode === 'partial' ? 'warning' : 'error'}
          disabled={
            !rejectionInput?.trim() ||
            (rejectionMode === 'partial' &&
              rejectType === 'line' &&
              (!manualRefusedAmountInput ||
                parseFloat(manualRefusedAmountInput) <= 0 ||
                parseFloat(manualRefusedAmountInput) > (currentLine?.byCompany ?? 0) + 0.001))
          }
        >
          {rejectionMode === 'partial' && rejectType === 'line' ? 'تأكيد الرفض الجزئي' : 'تأكيد الرفض'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
