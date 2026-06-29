import React, { useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';
import dayjs from 'dayjs';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Grid,
  FormControlLabel,
  Checkbox,
  Alert,
  IconButton,
  Typography
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import useAuth from 'hooks/useAuth';
import paymentsService from 'services/api/payments.service';

const PAYMENT_METHODS = [
  { value: 'CASH', label: 'نقدي' },
  { value: 'BANK_TRANSFER', label: 'تحويل مصرفي' },
  { value: 'CHECK', label: 'صك' },
  { value: 'OTHER', label: 'غير ذلك' }
];

const PaymentFormModal = ({ open, onClose, payment, summary, onSuccess }) => {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const isEdit = !!payment;

  const { control, handleSubmit, watch, formState: { errors, isSubmitting }, reset } = useForm({
    defaultValues: {
      amount: '',
      paymentDate: dayjs(),
      paymentMethod: 'CASH',
      referenceNumber: '',
      notes: '',
      overrideLimit: false,
      reason: ''
    }
  });

  const watchOverride = watch('overrideLimit');

  useEffect(() => {
    if (payment) {
      reset({
        amount: payment.amount,
        paymentDate: dayjs(payment.paymentDate),
        paymentMethod: payment.paymentMethod,
        referenceNumber: payment.referenceNumber || '',
        notes: payment.notes || '',
        overrideLimit: false,
        reason: ''
      });
    }
  }, [payment, reset]);

  const onSubmit = async (data) => {
    try {
      const payload = {
        employerId: summary.employerId,
        providerId: summary.providerId,
        targetYear: summary.targetYear,
        targetMonth: summary.targetMonth,
        amount: Number(data.amount),
        paymentDate: data.paymentDate.format('YYYY-MM-DD'),
        paymentMethod: data.paymentMethod,
        referenceNumber: data.referenceNumber,
        notes: data.notes,
        overrideLimit: data.overrideLimit,
        reason: data.reason
      };

      if (isEdit) {
        await paymentsService.updatePayment(payment.id, payload);
      } else {
        await paymentsService.addPayment(payload);
      }

      onSuccess();
      onClose();
    } catch (err) {
      alert(err?.response?.data?.message || 'حدث خطأ أثناء حفظ الدفعة');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">{isEdit ? 'تعديل الدفعة' : 'إضافة دفعة جديدة'}</Typography>
        <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
      </DialogTitle>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent dividers>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="amount"
                control={control}
                rules={{ required: 'المبلغ مطلوب', min: { value: 0.01, message: 'المبلغ غير صالح' } }}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="المبلغ"
                    type="number"
                    fullWidth
                    size="small"
                    error={!!errors.amount}
                    helperText={errors.amount?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="paymentDate"
                control={control}
                rules={{ required: 'التاريخ مطلوب' }}
                render={({ field }) => (
                  <DatePicker
                    {...field}
                    label="تاريخ الدفع"
                    format="DD/MM/YYYY"
                    slotProps={{ textField: { size: 'small', fullWidth: true, error: !!errors.paymentDate } }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="paymentMethod"
                control={control}
                rules={{ required: 'طريقة الدفع مطلوبة' }}
                render={({ field }) => (
                  <TextField {...field} select label="طريقة الدفع" fullWidth size="small" error={!!errors.paymentMethod}>
                    {PAYMENT_METHODS.map((method) => (
                      <MenuItem key={method.value} value={method.value}>{method.label}</MenuItem>
                    ))}
                  </TextField>
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="referenceNumber"
                control={control}
                render={({ field }) => (
                  <TextField {...field} label="رقم المرجع / الإيصال" fullWidth size="small" />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="notes"
                control={control}
                render={({ field }) => (
                  <TextField {...field} label="ملاحظات" fullWidth multiline rows={2} size="small" />
                )}
              />
            </Grid>

            {isSuperAdmin && (
              <Grid item xs={12}>
                <Controller
                  name="overrideLimit"
                  control={control}
                  render={({ field }) => (
                    <FormControlLabel
                      control={<Checkbox {...field} checked={field.value} />}
                      label="تجاوز الحد المسموح (دفع مبلغ أكبر من المتبقي)"
                    />
                  )}
                />
              </Grid>
            )}

            {(isEdit || watchOverride) && (
              <Grid item xs={12}>
                <Alert severity="warning" sx={{ mb: 1 }}>السبب إلزامي في حالة التعديل أو التجاوز</Alert>
                <Controller
                  name="reason"
                  control={control}
                  rules={{ required: (isEdit || watchOverride) ? 'السبب إلزامي' : false }}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="سبب التعديل / التجاوز"
                      fullWidth
                      size="small"
                      error={!!errors.reason}
                      helperText={errors.reason?.message}
                    />
                  )}
                />
              </Grid>
            )}
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} color="inherit">إلغاء</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {isSubmitting ? 'جاري الحفظ...' : 'حفظ'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default PaymentFormModal;
