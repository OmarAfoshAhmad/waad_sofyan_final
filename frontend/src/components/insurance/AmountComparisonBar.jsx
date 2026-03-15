/**
 * AmountComparisonBar Component
 * Visual comparison of Requested vs Approved amounts
 * Shows percentage and financial breakdown
 *
 * Usage:
 * <AmountComparisonBar
 *   requestedAmount={500}
 *   approvedAmount={400}
 *   currency="LYD"
 *   copayPercentage={20}
 *   showBreakdown={true}
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, LinearProgress, Divider, useTheme } from '@mui/material';

const AmountComparisonBar = ({
  requestedAmount = 0,
  approvedAmount = 0,
  currency = 'LYD',
  copayPercentage = 0,
  deductible = 0,
  showBreakdown = true,
  size = 'medium',
  language = 'ar',
  status = null // Claim status to determine if approval/rejection is finalized
}) => {
  const theme = useTheme();

  // Check if claim has been processed (approved, rejected, or settled)
  const isProcessed = ['APPROVED', 'REJECTED', 'SETTLED', 'PARTIALLY_APPROVED'].includes(status);

  // Only show approval percentage and rejected amount if claim has been processed
  const approvalPercentage = isProcessed && requestedAmount > 0 ? Math.round((approvedAmount / requestedAmount) * 100) : 0;

  // Rejected amount only applies to processed claims
  const rejectedAmount = isProcessed ? requestedAmount - approvedAmount : 0;
  const copayAmount = isProcessed ? approvedAmount * (copayPercentage / 100) : 0;
  const insurerPays = isProcessed ? approvedAmount - copayAmount - deductible : 0;
  const memberPays = isProcessed ? copayAmount + deductible + rejectedAmount : 0;

  const formatCurrency = (amount) => {
    return `${amount.toLocaleString()} ${currency}`;
  };

  const labels = {
    ar: {
      requested: 'المبلغ المطلوب',
      approved: 'المبلغ الموافق عليه',
      rejected: 'المبلغ المرفوض',
      copay: `نسبة التحمل (${copayPercentage}%)`,
      deductible: 'مبلغ التحمل',
      insurerPays: 'تدفع الشركة',
      memberPays: 'يدفع المؤمَّن عليه',
      financialSummary: 'الملخص المالي',
      pendingReview: 'قيد المراجعة - لم يتم تحديد المبالغ بعد'
    },
    en: {
      requested: 'Requested Amount',
      approved: 'Approved Amount',
      rejected: 'Rejected Amount',
      copay: `Co-pay (${copayPercentage}%)`,
      deductible: 'Deductible',
      insurerPays: 'Insurer Pays',
      memberPays: 'Member Pays',
      financialSummary: 'Financial Summary',
      pendingReview: 'Under Review - Amounts not yet determined'
    }
  };

  const t = labels[language] || labels.ar;

  const barHeight = size === 'small' ? 8 : size === 'large' ? 16 : 12;

  return (
    <Box sx={{ width: '100%' }}>
      {/* Requested Amount Bar */}
      <Stack spacing={0.5} sx={{ mb: '1.0rem' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant={size === 'small' ? 'caption' : 'body2'} color="text.secondary">
            {t.requested}
          </Typography>
          <Typography variant={size === 'small' ? 'body2' : 'body1'} fontWeight={500}>
            {formatCurrency(requestedAmount)}
          </Typography>
        </Stack>
        <LinearProgress
          variant="determinate"
          value={100}
          sx={{
            height: barHeight,
            borderRadius: 1,
            bgcolor: theme.palette.grey[200],
            '& .MuiLinearProgress-bar': {
              bgcolor: theme.palette.grey[400],
              borderRadius: 1
            }
          }}
        />
      </Stack>

      {/* Approved Amount Bar */}
      <Stack spacing={0.5} sx={{ mb: '1.0rem' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant={size === 'small' ? 'caption' : 'body2'} color="text.secondary">
            {t.approved}
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography
              variant="caption"
              sx={{
                px: 1,
                py: 0.25,
                borderRadius: 1,
                bgcolor: approvalPercentage >= 80 ? 'success.lighter' : approvalPercentage >= 50 ? 'warning.lighter' : 'error.lighter',
                color: approvalPercentage >= 80 ? 'success.dark' : approvalPercentage >= 50 ? 'warning.dark' : 'error.dark'
              }}
            >
              {approvalPercentage}%
            </Typography>
            <Typography variant={size === 'small' ? 'body2' : 'body1'} fontWeight={600} color="success.main">
              {formatCurrency(approvedAmount)}
            </Typography>
          </Stack>
        </Stack>
        <LinearProgress
          variant="determinate"
          value={approvalPercentage}
          sx={{
            height: barHeight,
            borderRadius: 1,
            bgcolor: theme.palette.grey[200],
            '& .MuiLinearProgress-bar': {
              bgcolor: theme.palette.success.main,
              borderRadius: 1
            }
          }}
        />
      </Stack>

      {/* Financial Breakdown */}
      {showBreakdown && (
        <Box sx={{ mt: '1.5rem', p: '1.0rem', bgcolor: 'grey.50', borderRadius: '0.25rem' }}>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: '1.0rem' }}>
            {t.financialSummary}
          </Typography>

          {/* Show pending message if not processed */}
          {!isProcessed ? (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: '1.0rem' }}>
              {t.pendingReview}
            </Typography>
          ) : (
            <Stack spacing={1}>
              {/* Insurer Pays */}
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack direction="row" spacing={1} alignItems="center">
                  <Box sx={{ width: '0.75rem', height: '0.75rem', borderRadius: '50%', bgcolor: 'success.main' }} />
                  <Typography variant="body2">{t.insurerPays}</Typography>
                </Stack>
                <Typography variant="body2" fontWeight={600} color="success.main">
                  {formatCurrency(Math.max(0, insurerPays))}
                </Typography>
              </Stack>

              {/* Copay */}
              {copayPercentage > 0 && (
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Box sx={{ width: '0.75rem', height: '0.75rem', borderRadius: '50%', bgcolor: 'warning.main' }} />
                    <Typography variant="body2">{t.copay}</Typography>
                  </Stack>
                  <Typography variant="body2" fontWeight={500} color="warning.dark">
                    {formatCurrency(copayAmount)}
                  </Typography>
                </Stack>
              )}

              {/* Deductible */}
              {deductible > 0 && (
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Box sx={{ width: '0.75rem', height: '0.75rem', borderRadius: '50%', bgcolor: 'info.main' }} />
                    <Typography variant="body2">{t.deductible}</Typography>
                  </Stack>
                  <Typography variant="body2" fontWeight={500} color="info.dark">
                    {formatCurrency(deductible)}
                  </Typography>
                </Stack>
              )}

              {/* Rejected */}
              {rejectedAmount > 0 && (
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Box sx={{ width: '0.75rem', height: '0.75rem', borderRadius: '50%', bgcolor: 'error.main' }} />
                    <Typography variant="body2">{t.rejected}</Typography>
                  </Stack>
                  <Typography variant="body2" fontWeight={500} color="error.main">
                    {formatCurrency(rejectedAmount)}
                  </Typography>
                </Stack>
              )}

              <Divider sx={{ my: 1 }} />

              {/* Total Member Pays */}
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="body2" fontWeight={600}>
                  {t.memberPays}
                </Typography>
                <Typography variant="body1" fontWeight={700} color="text.primary">
                  {formatCurrency(Math.max(0, memberPays))}
                </Typography>
              </Stack>
            </Stack>
          )}
        </Box>
      )}
    </Box>
  );
};

AmountComparisonBar.propTypes = {
  requestedAmount: PropTypes.number.isRequired,
  approvedAmount: PropTypes.number,
  currency: PropTypes.string,
  copayPercentage: PropTypes.number,
  deductible: PropTypes.number,
  showBreakdown: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  language: PropTypes.oneOf(['ar', 'en']),
  status: PropTypes.string // Claim status: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, SETTLED
};

export default AmountComparisonBar;


