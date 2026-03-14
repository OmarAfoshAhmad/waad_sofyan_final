/**
 * CoverageGauge Component
 * Shows coverage period progress with days remaining
 *
 * Usage:
 * <CoverageGauge
 *   startDate="2024-01-01"
 *   endDate="2024-12-31"
 *   showWarning={true}
 *   warningDays={30}
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, CircularProgress, Chip, useTheme } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import dayjs from 'dayjs';

const CoverageGauge = ({
  startDate,
  endDate,
  showWarning = true,
  warningDays = 30,
  criticalDays = 7,
  size = 'medium',
  variant = 'circular', // 'circular' | 'linear' | 'compact'
  language = 'ar'
}) => {
  const theme = useTheme();

  const start = dayjs(startDate);
  const end = dayjs(endDate);
  const today = dayjs();

  const totalDays = end.diff(start, 'day');
  const daysElapsed = today.diff(start, 'day');
  const daysRemaining = end.diff(today, 'day');

  const percentageUsed = totalDays > 0 ? Math.min(100, Math.max(0, (daysElapsed / totalDays) * 100)) : 0;
  const percentageRemaining = 100 - percentageUsed;

  const isExpired = daysRemaining < 0;
  const isCritical = daysRemaining >= 0 && daysRemaining <= criticalDays;
  const isWarning = daysRemaining > criticalDays && daysRemaining <= warningDays;
  const isHealthy = daysRemaining > warningDays;

  const getStatusColor = () => {
    if (isExpired) return theme.palette.error.main;
    if (isCritical) return theme.palette.error.main;
    if (isWarning) return theme.palette.warning.main;
    return theme.palette.success.main;
  };

  const getStatusIcon = () => {
    if (isExpired) return <ErrorIcon sx={{ color: 'error.main' }} />;
    if (isCritical) return <ErrorIcon sx={{ color: 'error.main' }} />;
    if (isWarning) return <WarningAmberIcon sx={{ color: 'warning.main' }} />;
    return <CheckCircleIcon sx={{ color: 'success.main' }} />;
  };

  const labels = {
    ar: {
      daysRemaining: 'يوم متبقي',
      expired: 'منتهية',
      renewNow: 'تجديد الآن',
      renewingSoon: 'تجديد قريباً',
      active: 'نشطة',
      coverage: 'فترة التغطية',
      used: 'مستخدم',
      remaining: 'متبقي'
    },
    en: {
      daysRemaining: 'days remaining',
      expired: 'Expired',
      renewNow: 'Renew Now',
      renewingSoon: 'Renewing Soon',
      active: 'Active',
      coverage: 'Coverage Period',
      used: 'Used',
      remaining: 'Remaining'
    }
  };

  const t = labels[language] || labels.ar;

  const circleSize = size === 'small' ? 80 : size === 'large' ? 140 : 100;
  const thickness = size === 'small' ? 4 : size === 'large' ? 6 : 5;

  // Compact variant - just a badge
  if (variant === 'compact') {
    return (
      <Chip
        icon={getStatusIcon()}
        label={isExpired ? t.expired : `${Math.max(0, daysRemaining)} ${t.daysRemaining}`}
        size={size === 'small' ? 'small' : 'medium'}
        sx={{
          bgcolor: isExpired ? 'error.lighter' : isWarning ? 'warning.lighter' : isCritical ? 'error.lighter' : 'success.lighter',
          color: isExpired ? 'error.dark' : isWarning ? 'warning.dark' : isCritical ? 'error.dark' : 'success.dark',
          fontWeight: 500,
          '& .MuiChip-icon': {
            color: 'inherit'
          }
        }}
      />
    );
  }

  // Linear variant - progress bar
  if (variant === 'linear') {
    return (
      <Box sx={{ width: '100%' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t.coverage}
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            {getStatusIcon()}
            <Typography variant="body2" fontWeight={600} color={getStatusColor()}>
              {isExpired ? t.expired : `${Math.max(0, daysRemaining)} ${t.daysRemaining}`}
            </Typography>
          </Stack>
        </Stack>

        <Box sx={{ position: 'relative', height: '0.375rem', borderRadius: 1, bgcolor: 'grey.200' }}>
          <Box
            sx={{
              position: 'absolute',
              left: 0,
              top: 0,
              height: '100%',
              width: `${percentageUsed}%`,
              borderRadius: 1,
              bgcolor: getStatusColor(),
              transition: 'width 0.3s ease'
            }}
          />
        </Box>

        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="caption" color="text.secondary">
            {start.format('MMM YYYY')}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {end.format('MMM YYYY')}
          </Typography>
        </Stack>

        {showWarning && (isWarning || isCritical) && (
          <Chip
            size="small"
            icon={<WarningAmberIcon />}
            label={isCritical ? t.renewNow : t.renewingSoon}
            sx={{
              mt: 1,
              bgcolor: isCritical ? 'error.lighter' : 'warning.lighter',
              color: isCritical ? 'error.dark' : 'warning.dark',
              '& .MuiChip-icon': { color: 'inherit' }
            }}
          />
        )}
      </Box>
    );
  }

  // Circular variant (default)
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Box sx={{ position: 'relative', display: 'inline-flex' }}>
        {/* Background circle */}
        <CircularProgress
          variant="determinate"
          value={100}
          size={circleSize}
          thickness={thickness}
          sx={{ color: theme.palette.grey[200] }}
        />

        {/* Progress circle */}
        <CircularProgress
          variant="determinate"
          value={percentageRemaining}
          size={circleSize}
          thickness={thickness}
          sx={{
            color: getStatusColor(),
            position: 'absolute',
            left: 0,
            '& .MuiCircularProgress-circle': {
              strokeLinecap: 'round'
            }
          }}
        />

        {/* Center content */}
        <Box
          sx={{
            top: 0,
            left: 0,
            bottom: 0,
            right: 0,
            position: 'absolute',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column'
          }}
        >
          <Typography variant={size === 'small' ? 'h6' : size === 'large' ? 'h3' : 'h4'} fontWeight={700} color={getStatusColor()}>
            {isExpired ? '!' : Math.max(0, daysRemaining)}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ mt: -0.5 }}>
            {isExpired ? t.expired : t.daysRemaining.split(' ')[0]}
          </Typography>
        </Box>
      </Box>

      {/* Status chip */}
      {showWarning && (
        <Chip
          size="small"
          label={isExpired ? t.expired : isCritical ? t.renewNow : isWarning ? t.renewingSoon : t.active}
          sx={{
            mt: '0.75rem',
            bgcolor: isExpired ? 'error.lighter' : isCritical ? 'error.lighter' : isWarning ? 'warning.lighter' : 'success.lighter',
            color: isExpired ? 'error.dark' : isCritical ? 'error.dark' : isWarning ? 'warning.dark' : 'success.dark',
            fontWeight: 500
          }}
        />
      )}
    </Box>
  );
};

CoverageGauge.propTypes = {
  startDate: PropTypes.string.isRequired,
  endDate: PropTypes.string.isRequired,
  showWarning: PropTypes.bool,
  warningDays: PropTypes.number,
  criticalDays: PropTypes.number,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  variant: PropTypes.oneOf(['circular', 'linear', 'compact']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default CoverageGauge;


