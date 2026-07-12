import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Typography,
  Box,
  CircularProgress
} from '@mui/material';
import { bulkMoveMedicalCategories } from 'services/api/medical-categories.service';
import { openSnackbar } from 'api/snackbar';

const BulkMoveMedicalCategoryDialog = ({ open, onClose, selectedIds, parentCategories, onMoveSuccess }) => {
  const [newParentId, setNewParentId] = useState('');
  const [loading, setLoading] = useState(false);

  const handleMove = async () => {
    if (!selectedIds || selectedIds.length === 0) return;
    try {
      setLoading(true);
      // newParentId = '' maps to null for root
      await bulkMoveMedicalCategories(selectedIds, newParentId ? Number(newParentId) : null);
      openSnackbar({ message: 'تم نقل التصنيفات بنجاح', variant: 'alert', alert: { color: 'success', variant: 'filled' } });
      onMoveSuccess();
      onClose();
    } catch (err) {
      console.error('Bulk move error:', err);
      const errMsg = err?.response?.data?.message || 'فشل نقل التصنيفات';
      openSnackbar({ message: errMsg, variant: 'alert', alert: { color: 'error', variant: 'filled' } });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={!loading ? onClose : undefined} maxWidth="xs" fullWidth>
      <DialogTitle>نقل جماعي للتصنيفات</DialogTitle>
      <DialogContent dividers>
        <Box sx={{ mb: 2 }}>
          <Typography variant="body2" color="textSecondary">
            لقد قمت بتحديد <strong>{selectedIds.length}</strong> تصنيف/تصنيفات.
            <br />
            اختر التصنيف الأب الجديد الذي ترغب في نقل هذه التصنيفات إليه.
          </Typography>
        </Box>

        <TextField
          select
          fullWidth
          label="التصنيف الأب الجديد"
          value={newParentId}
          onChange={(e) => setNewParentId(e.target.value)}
          disabled={loading}
          helperText="اختر 'لا يوجد / تصنيف رئيسي' لجعلها تصنيفات رئيسية"
        >
          <MenuItem value="">
            <em>لا يوجد / تصنيف رئيسي</em>
          </MenuItem>
          {parentCategories.map((cat) => (
            <MenuItem key={cat.id} value={cat.id} disabled={selectedIds.includes(cat.id)}>
              {cat.name || cat.code}
            </MenuItem>
          ))}
        </TextField>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          إلغاء
        </Button>
        <Button variant="contained" onClick={handleMove} disabled={loading || selectedIds.length === 0}>
          {loading ? <CircularProgress size={24} color="inherit" /> : 'تأكيد النقل'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BulkMoveMedicalCategoryDialog;
