import { Alert, Button } from '@mui/material';

const apiMessage = (error) =>
  error?.response?.data?.messageAr ||
  error?.response?.data?.message ||
  error?.message;

/** One truthful readiness message for the dated policy/contract/balance gate. */
export const ClaimEntryReadinessAlert = ({ member, serviceDate, loading, context, error, onRetry }) => {
  if (!member?.id) {
    return <Alert severity="info">اختر المستفيد أولاً، ثم حدّد تاريخ الخدمة للتحقق من الوثيقة والعقد والسقف.</Alert>;
  }
  if (!serviceDate) {
    return <Alert severity="warning">حدّد تاريخ الخدمة؛ صلاحية الوثيقة والعقد والأسعار تُحسم في هذا التاريخ.</Alert>;
  }
  if (loading) {
    return <Alert severity="info">جارٍ التحقق من جهة العمل والوثيقة والعقد والرصيد في تاريخ الخدمة…</Alert>;
  }
  if (error || !context) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={onRetry}>
            إعادة التحقق
          </Button>
        }
      >
        {apiMessage(error) || 'لا توجد وثيقة وعقد صالحان للمستفيد في تاريخ الخدمة المحدد.'}
      </Alert>
    );
  }
  return (
    <Alert severity="success">
      تم التحقق: الوثيقة {context.policyCode || context.policyName || 'المؤرخة'} والعقد {context.contractNumber || 'الساري'} صالحان
      بتاريخ {context.serviceDate}.
    </Alert>
  );
};
