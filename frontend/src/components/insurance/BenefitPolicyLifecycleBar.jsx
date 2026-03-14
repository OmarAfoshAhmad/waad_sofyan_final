/**
 * BenefitPolicyLifecycleBar Component
 * Visual timeline of benefit policy period with current position marker
 *
 * Usage:
 * <BenefitPolicyLifecycleBar
 *   startDate="2024-01-01"
 *   endDate="2024-12-31"
 *   showRenewalReminder={true}
 *   renewalDays={30}
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, Chip, Tooltip, useTheme } from '@mui/material';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import EventIcon from '@mui/icons-material/Event';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import dayjs from 'dayjs';

const BenefitPolicyLifecycleBar = ({
  startDate,
  endDate,
  showRenewalReminder = true,
  renewalDays = 30,
  policyType = 'GROUP', // GROUP | INDIVIDUAL | CORPORATE
  memberCount,
  size = 'medium',
  language = 'ar'
}) => {
  const theme = useTheme();

  const start = dayjs(startDate);
  const end = dayjs(endDate);
  const today = dayjs();

  const totalDays = end.diff(start, 'day');
  const daysElapsed = Math.max(0, today.diff(start, 'day'));
  const daysRemaining = Math.max(0, end.diff(today, 'day'));

  const progressPercentage = totalDays > 0 ? Math.min(100, Math.max(0, (daysElapsed / totalDays) * 100)) : 0;

  const isExpired = today.isAfter(end);
  const isNotStarted = today.isBefore(start);
  const isRenewalSoon = daysRemaining <= renewalDays && !isExpired;

  const labels = {
    ar: {
      policyPeriod: 'فترة البوليصة',
      start: 'البداية',
      end: 'النهاية',
      today: 'اليوم',
      daysRemaining: 'يوم متبقي',
      expired: 'منتهية',
      notStarted: 'لم تبدأ بعد',
      renewalReminder: 'موعد التجديد قريب',
      active: 'نشطة',
      group: 'جماعية',
      individual: 'فردية',
      corporate: 'شركات',
      members: 'أعضاء'
    },
    en: {
      policyPeriod: 'Policy Period',
      start: 'Start',
      end: 'End',
      today: 'Today',
      daysRemaining: 'days remaining',
      expired: 'Expired',
      notStarted: 'Not Started',
      renewalReminder: 'Renewal Due Soon',
      active: 'Active',
      group: 'Group',
      individual: 'Individual',
      corporate: 'Corporate',
      members: 'members'
    }
  };

  const t = labels[language] || labels.ar;

  const policyTypeLabels = {
    GROUP: language === 'ar' ? t.group : t.group,
    INDIVIDUAL: language === 'ar' ? t.individual : t.individual,
    CORPORATE: language === 'ar' ? t.corporate : t.corporate
  };

  const getStatusColor = () => {
    if (isExpired) return theme.palette.error.main;
    if (isNotStarted) return theme.palette.info.main;
    if (isRenewalSoon) return theme.palette.warning.main;
    return theme.palette.success.main;
  };

  const getStatusLabel = () => {
    if (isExpired) return t.expired;
    if (isNotStarted) return t.notStarted;
    if (isRenewalSoon) return `${daysRemaining} ${t.daysRemaining}`;
    return t.active;
  };

  const barHeight = size === 'small' ? 8 : size === 'large' ? 16 : 12;

  return (
    <Box sx={{ width: '100%' }}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: '0.75rem' }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <CalendarTodayIcon sx={{ fontSize: '1.125rem', color: 'text.secondary' }} />
          <Typography variant="subtitle2" fontWeight={600}>
            {t.policyPeriod}
          </Typography>
          <Chip label={policyTypeLabels[policyType]} size="small" variant="outlined" sx={{ height: '1.25rem', fontSize: '0.7rem' }} />
        </Stack>

        <Stack direction="row" spacing={1} alignItems="center">
          {memberCount !== undefined && (
            <Chip
              label={`${memberCount} ${t.members}`}
              size="small"
              sx={{
                bgcolor: 'primary.lighter',
                color: 'primary.dark',
                fontSize: '0.7rem'
              }}
            />
          )}
          <Chip
            icon={isExpired ? <WarningAmberIcon /> : isRenewalSoon ? <AutorenewIcon /> : <CheckCircleIcon />}
            label={getStatusLabel()}
            size="small"
            sx={{
              bgcolor: `${getStatusColor()}15`,
              color: getStatusColor(),
              fontWeight: 500,
              '& .MuiChip-icon': { color: getStatusColor() }
            }}
          />
        </Stack>
      </Stack>

      {/* Timeline Bar */}
      <Box sx={{ position: 'relative', mb: '1.5rem' }}>
        {/* Background track */}
        <Box
          sx={{
            height: barHeight,
            borderRadius: barHeight / 2,
            bgcolor: theme.palette.grey[200],
            position: 'relative',
            overflow: 'visible'
          }}
        >
          {/* Progress fill */}
          <Box
            sx={{
              position: 'absolute',
              left: 0,
              top: 0,
              height: '100%',
              width: `${progressPercentage}%`,
              borderRadius: barHeight / 2,
              bgcolor: getStatusColor(),
              transition: 'width 0.5s ease'
            }}
          />

          {/* Today marker */}
          {!isExpired && !isNotStarted && (
            <Tooltip title={t.today} arrow>
              <Box
                sx={{
                  position: 'absolute',
                  left: `${progressPercentage}%`,
                  top: -4,
                  transform: 'translateX(-50%)',
                  width: barHeight + 8,
                  height: barHeight + 8,
                  borderRadius: '50%',
                  bgcolor: getStatusColor(),
                  border: 2,
                  borderColor: 'background.paper',
                  boxShadow: 2,
                  cursor: 'pointer',
                  zIndex: 2
                }}
              />
            </Tooltip>
          )}

          {/* Renewal zone indicator */}
          {showRenewalReminder && !isExpired && (
            <Box
              sx={{
                position: 'absolute',
                right: 0,
                top: 0,
                height: '100%',
                width: `${(renewalDays / totalDays) * 100}%`,
                maxWidth: '30%',
                borderRadius: `0 ${barHeight / 2}px ${barHeight / 2}px 0`,
                bgcolor: 'warning.lighter',
                opacity: 0.5,
                pointerEvents: 'none'
              }}
            />
          )}
        </Box>

        {/* Start and End labels */}
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 1, px: 0.5 }}>
          <Stack alignItems="flex-start">
            <Typography variant="caption" color="text.secondary">
              {t.start}
            </Typography>
            <Typography variant="body2" fontWeight={500}>
              {start.format('DD MMM YYYY')}
            </Typography>
          </Stack>

          {/* Today label (positioned absolutely based on progress) */}
          {!isExpired && !isNotStarted && progressPercentage > 20 && progressPercentage < 80 && (
            <Stack
              alignItems="center"
              sx={{
                position: 'absolute',
                left: `${progressPercentage}%`,
                transform: 'translateX(-50%)',
                top: barHeight + 8
              }}
            >
              <Typography variant="caption" color={getStatusColor()} fontWeight={600}>
                {t.today}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {today.format('DD/MM')}
              </Typography>
            </Stack>
          )}

          <Stack alignItems="flex-end">
            <Typography variant="caption" color="text.secondary">
              {t.end}
            </Typography>
            <Typography variant="body2" fontWeight={500}>
              {end.format('DD MMM YYYY')}
            </Typography>
          </Stack>
        </Stack>
      </Box>

      {/* Renewal Reminder Alert */}
      {showRenewalReminder && isRenewalSoon && (
        <Box
          sx={{
            mt: '1.0rem',
            p: '0.75rem',
            borderRadius: 1,
            bgcolor: 'warning.lighter',
            border: 1,
            borderColor: 'warning.light',
            display: 'flex',
            alignItems: 'center',
            gap: 1
          }}
        >
          <AutorenewIcon sx={{ color: 'warning.main' }} />
          <Typography variant="body2" color="warning.dark" fontWeight={500}>
            {t.renewalReminder} - {daysRemaining} {t.daysRemaining}
          </Typography>
        </Box>
      )}
    </Box>
  );
};

BenefitPolicyLifecycleBar.propTypes = {
  startDate: PropTypes.string.isRequired,
  endDate: PropTypes.string.isRequired,
  showRenewalReminder: PropTypes.bool,
  renewalDays: PropTypes.number,
  policyType: PropTypes.oneOf(['GROUP', 'INDIVIDUAL', 'CORPORATE']),
  memberCount: PropTypes.number,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default BenefitPolicyLifecycleBar;


