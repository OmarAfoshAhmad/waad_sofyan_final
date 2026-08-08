import { useState } from 'react';
import dayjs from 'dayjs';

import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  TextField,
  MenuItem,
  Alert,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Typography,
  CircularProgress
} from '@mui/material';

import { formatCurrency } from 'utils/currency-formatter';
import { providerPaymentsV2Service } from 'services/api/settlement.service';

const PAYMENT_METHODS = [
  { value: 'BANK_TRANSFER', label: 'تحويل مصرفي' },
  { value: 'CASH', label: 'نقدي' },
  { value: 'CHECK', label: 'صك' },
  { value: 'OTHER', label: 'غير ذلك' }
];

const initialForm = {
  amount: '',
  paymentDate: dayjs().format('YYYY-MM-DD'),
  paymentMethod: 'BANK_TRANSFER',
  referenceNumber: '',
  notes: ''
};

export default function NewProviderPaymentDialog({ providerId, open, onClose, onCreated }) {
  const [form, setForm] = useState(initialForm);
  const [suggestion, setSuggestion] = useState(null);
  const [loadingSuggestion, setLoadingSuggestion] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const reset = () => {
    setForm(initialForm);
    setSuggestion(null);
    setError('');
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const previewSuggestion = async () => {
    setError('');
    if (!form.amount || Number(form.amount) <= 0) {
      setError('أدخل مبلغاً موجباً أولاً');
      return;
    }
    setLoadingSuggestion(true);
    try {
      const result = await providerPaymentsV2Service.suggest(providerId, form.amount, form.paymentDate);
      setSuggestion(result);
    } catch (e) {
      setError(e?.message || 'تعذّر جلب اقتراح التوزيع');
    } finally {
      setLoadingSuggestion(false);
    }
  };

  const save = async () => {
    if (!suggestion) {
      setError('يجب معاينة اقتراح التوزيع FIFO قبل الحفظ');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = {
        providerId,
        amount: form.amount,
        paymentDate: form.paymentDate,
        paymentMethod: form.paymentMethod,
        referenceNumber: form.referenceNumber || undefined,
        notes: form.notes || undefined,
        allocations: (suggestion.allocations || []).map((a) => ({
          employerId: a.employerId,
          targetYear: a.targetYear,
          targetMonth: a.targetMonth,
          amount: a.suggestedAmount,
          outstandingAtAllocation: a.outstandingAtAllocation,
          allocationMethod: a.allocationMethod
        }))
      };
      await providerPaymentsV2Service.createDraft(payload);
      reset();
      onCreated?.();
    } catch (e) {
      setError(e?.message || 'تعذّر إنشاء الدفعة');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>دفعة جديدة لمقدم الخدمة</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="المبلغ"
            type="number"
            value={form.amount}
            onChange={(e) => {
              setForm((f) => ({ ...f, amount: e.target.value }));
              setSuggestion(null);
            }}
            fullWidth
            required
          />
          <TextField
            label="تاريخ الدفعة"
            type="date"
            value={form.paymentDate}
            onChange={(e) => {
              setForm((f) => ({ ...f, paymentDate: e.target.value }));
              setSuggestion(null);
            }}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
          <TextField
            select
            label="طريقة الدفع"
            value={form.paymentMethod}
            onChange={(e) => setForm((f) => ({ ...f, paymentMethod: e.target.value }))}
            fullWidth
          >
            {PAYMENT_METHODS.map((m) => (
              <MenuItem key={m.value} value={m.value}>
                {m.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="المرجع البنكي (اختياري)"
            value={form.referenceNumber}
            onChange={(e) => setForm((f) => ({ ...f, referenceNumber: e.target.value }))}
            fullWidth
          />
          <TextField
            label="ملاحظات (اختياري)"
            value={form.notes}
            onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
          />

          {error && <Alert severity="error">{error}</Alert>}

          <Button variant="outlined" onClick={previewSuggestion} disabled={loadingSuggestion}>
            {loadingSuggestion ? <CircularProgress size={20} /> : 'معاينة اقتراح التوزيع (FIFO)'}
          </Button>

          {suggestion && (
            <Stack spacing={1}>
              <Typography variant="subtitle2">التوزيع المقترح — الأقدم فالأحدث</Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>جهة العمل</TableCell>
                    <TableCell>الفترة</TableCell>
                    <TableCell align="right">المستحق</TableCell>
                    <TableCell align="right">المقترح</TableCell>
                    <TableCell>الطريقة</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(suggestion.allocations || []).map((a, idx) => (
                    <TableRow key={idx}>
                      <TableCell>{a.employerId}</TableCell>
                      <TableCell>
                        {a.targetYear}/{a.targetMonth}
                      </TableCell>
                      <TableCell align="right">{formatCurrency(a.outstandingAtAllocation)}</TableCell>
                      <TableCell align="right">{formatCurrency(a.suggestedAmount)}</TableCell>
                      <TableCell>{a.allocationMethod}</TableCell>
                    </TableRow>
                  ))}
                  {(suggestion.allocations || []).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5}>
                        <Typography variant="caption" color="text.secondary">
                          لا يوجد مستحق قابل للتوزيع
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              {Number(suggestion.unallocatedAmount) > 0 && (
                <Alert severity="info">
                  مبلغ غير مخصَّص من هذه الدفعة: {formatCurrency(suggestion.unallocatedAmount)} — سيبقى معلّقاً على الدفعة حتى يُخصَّص
                  لاحقاً أو يتوفر مستحق جديد.
                </Alert>
              )}
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>إلغاء</Button>
        <Button variant="contained" onClick={save} disabled={saving || !suggestion}>
          {saving ? '...جارٍ الحفظ' : 'حفظ كمسودة'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
