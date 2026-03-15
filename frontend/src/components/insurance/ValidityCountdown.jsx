/**
 * ValidityCountdown Component
 * Shows Pre-Approval validity period with countdown
 *
 * Usage:
 * <ValidityCountdown
 *   approvalDate="2024-12-15"
 *   validityDays={30}
 *   showAction={true}
 *   onConvertToClaim={() => {}}
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, Chip, Button, LinearProgress, useTheme } from '@mui/material';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorIcon from '@mui/icons-material/Error';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import dayjs from 'dayjs';

const ValidityCountdown = ({
  approvalDate,
  validityDays = 30,
  status = 'APPROVED', // PENDING | APPROVED | REJECTED | EXPIRED | USED
  showAction = true,
  showProgress = true,
  onConvertToClaim,
  size = 'medium',
  language = 'ar'
}) => {
  const theme = useTheme();

  const approval = dayjs(approvalDate);
  const expiryDate = approval.add(validityDays, 'day');
  const today = dayjs();
  const daysRemaining = expiryDate.diff(today, 'day');
  const hoursRemaining = expiryDate.diff(today, 'hour');

  const percentageUsed = validityDays > 0 ? Math.min(100, Math.max(0, ((validityDays - daysRemaining) / validityDays) * 100)) : 100;

  const isExpired = daysRemaining < 0;
  const isCritical = daysRemaining >= 0 && daysRemaining <= 3;
  const isWarning = daysRemaining > 3 && daysRemaining <= 7;
  const isUsed = status === 'USED';
  const isPending = status === 'PENDING';
  const isRejected = status === 'REJECTED';

  const getStatusColor = () => {
    if (isUsed) return theme.palette.info.main;
    if (isRejected) return theme.palette.error.main;
    if (isPending) return theme.palette.warning.main;
    if (isExpired) return theme.palette.error.main;
    if (isCritical) return theme.palette.error.main;
    if (isWarning) return theme.palette.warning.main;
    return theme.palette.success.main;
  };

  const labels = {
    ar: {
      validUntil: 'صالحة حتى',
      daysRemaining: 'يوم متبقي',
      hoursRemaining: 'ساعة متبقية',
      expired: 'منتهية الصلاحية',
      used: 'تم استخدامها',
      pending: 'قيد الانتظار',
      rejected: 'مرفوضة',
      convertToClaim: 'تحويل إلى مطالبة',
      expiresIn: 'تنتهي خلال',
      expiredOn: 'انتهت في',
      urgent: 'عاجل - استخدم الآن'
    },
    en: {
      validUntil: 'Valid Until',
      daysRemaining: 'days remaining',
      hoursRemaining: 'hours remaining',
      expired: 'Expired',
      used: 'Used',
      pending: 'Pending',
      rejected: 'Rejected',
      convertToClaim: 'Convert to Claim',
      expiresIn: 'Expires in',
      expiredOn: 'Expired on',
      urgent: 'Urgent - Use Now'
    }
  };

  const t = labels[language] || labels.ar;

  const getStatusChip = () => {
    if (isUsed) {
      return <Chip icon={<CheckCircleIcon />} label={t.used} color="info" size={size === 'small' ? 'small' : 'medium'} />;
    }
    if (isRejected) {
      return <Chip icon={<ErrorIcon />} label={t.rejected} color="error" size={size === 'small' ? 'small' : 'medium'} />;
    }
    if (isPending) {
      return <Chip icon={<AccessTimeIcon />} label={t.pending} color="warning" size={size === 'small' ? 'small' : 'medium'} />;
    }
    if (isExpired) {
      return <Chip icon={<ErrorIcon />} label={t.expired} color="error" size={size === 'small' ? 'small' : 'medium'} />;
    }
    if (isCritical) {
      return (
        <Chip
          icon={<WarningAmberIcon />}
          label={t.urgent}
          color="error"
          size={size === 'small' ? 'small' : 'medium'}
          sx={{ animation: 'pulse 1.5s infinite' }}
        />
      );
    }
    return null;
  };

  const getTimeDisplay = () => {
    if (isUsed || isRejected || isPending) return null;

    if (isExpired) {
      return (
        <Typography variant="body2" color="error.main" fontWeight={500}>
          {t.expiredOn}: {expiryDate.format('DD/MM/YYYY')}
        </Typography>
      );
    }

    if (daysRemaining === 0) {
      return (
        <Typography variant="body2" color={getStatusColor()} fontWeight={600}>
          {hoursRemaining} {t.hoursRemaining}
        </Typography>
      );
    }

    return (
      <Typography variant="body2" color={getStatusColor()} fontWeight={600}>
        {daysRemaining} {t.daysRemaining}
      </Typography>
    );
  };

  // Compact mode for lists
  if (size === 'small') {
    return (
      <Stack direction="row" spacing={1} alignItems="center">
        {getStatusChip() || (
          <Chip
            icon={<AccessTimeIcon />}
            label={`${Math.max(0, daysRemaining)}d`}
            size="small"
            sx={{
              bgcolor: `${getStatusColor()}15`,
              color: getStatusColor(),
              '& .MuiChip-icon': { color: getStatusColor() }
            }}
          />
        )}
      </Stack>
    );
  }

  return (
    <Box
      sx={{
        p: '1.0rem',
        borderRadius: '0.25rem',
        bgcolor:
          isExpired || isRejected
            ? 'error.lighter'
            : isPending
              ? 'warning.lighter'
              : isUsed
                ? 'info.lighter'
                : isCritical
                  ? 'error.lighter'
                  : isWarning
                    ? 'warning.lighter'
                    : 'success.lighter',
        border: 1,
        borderColor:
          isExpired || isRejected
            ? 'error.light'
            : isPending
              ? 'warning.light'
              : isUsed
                ? 'info.light'
                : isCritical
                  ? 'error.light'
                  : isWarning
                    ? 'warning.light'
                    : 'success.light'
      }}
    >
      <Stack spacing={2}>
        {/* Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Stack direction="row" spacing={1} alignItems="center">
            <AccessTimeIcon sx={{ color: getStatusColor(), fontSize: '1.25rem' }} />
            <Typography variant="subtitle2" fontWeight={600}>
              {t.validUntil}
            </Typography>
          </Stack>
          {getStatusChip()}
        </Stack>

        {/* Expiry Date */}
        <Box>
          <Typography variant="h5" fontWeight={700} color={getStatusColor()}>
            {expiryDate.format('DD MMM YYYY')}
          </Typography>
          {getTimeDisplay()}
        </Box>

        {/* Progress Bar */}
        {showProgress && status === 'APPROVED' && !isExpired && (
          <Box>
            <LinearProgress
              variant="determinate"
              value={percentageUsed}
              sx={{
                height: '0.375rem',
                borderRadius: 1,
                bgcolor: 'rgba(255,255,255,0.5)',
                '& .MuiLinearProgress-bar': {
                  bgcolor: getStatusColor(),
                  borderRadius: 1
                }
              }}
            />
            <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
              <Typography variant="caption" color="text.secondary">
                {approval.format('DD/MM')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {expiryDate.format('DD/MM')}
              </Typography>
            </Stack>
          </Box>
        )}

        {/* Action Button */}
        {showAction && status === 'APPROVED' && !isExpired && onConvertToClaim && (
          <Button
            variant="contained"
            color={isCritical ? 'error' : 'primary'}
            endIcon={<ArrowForwardIcon />}
            onClick={onConvertToClaim}
            fullWidth
            size={size === 'large' ? 'large' : 'medium'}
          >
            {t.convertToClaim}
          </Button>
        )}
      </Stack>

      {/* Pulse animation for critical state */}
      <style>
        {`
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.7; }
          }
        `}
      </style>
    </Box>
  );
};

ValidityCountdown.propTypes = {
  approvalDate: PropTypes.string,
  validityDays: PropTypes.number,
  status: PropTypes.oneOf(['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'USED']),
  showAction: PropTypes.bool,
  showProgress: PropTypes.bool,
  onConvertToClaim: PropTypes.func,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default ValidityCountdown;
