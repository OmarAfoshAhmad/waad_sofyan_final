import { Alert, Button } from '@mui/material';

const apiMessage = (error) =>
  error?.response?.data?.messageAr ||
  error?.response?.data?.message ||
  error?.message;

/**
 * Reports only the failure of the dated policy/contract/balance gate.
 *
 * Everything else this component used to say has an owner elsewhere on the
 * screen. "Pick a member, then a service date" repeats two required fields that
 * already carry their own markers and their own submit-time errors. The success
 * line -- policy, contract, and the date they were verified on -- is already in
 * the page subtitle, permanently and in its proper place; saying it a second
 * time in a banner underneath is the same fact twice.
 *
 * A failure has no other owner: it stops the claim, and the retry belongs with
 * it. That is what stays.
 */
export const ClaimEntryReadinessAlert = ({ member, serviceDate, loading, context, error, onRetry }) => {
  if (!member?.id || !serviceDate || loading) {
    return null;
  }
  if (!error && context) {
    return null;
  }
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
};
