/**
 * StatusTimeline Component
 * Visualizes workflow progress for Claims and Pre-Approvals
 *
 * Usage:
 * <StatusTimeline
 *   steps={[
 *     { key: 'SUBMITTED', label: 'مقدمة', labelEn: 'Submitted', date: '2024-12-15' },
 *     { key: 'RECEIVED', label: 'مستلمة', labelEn: 'Received', date: '2024-12-15' },
 *     { key: 'REVIEW', label: 'قيد المراجعة', labelEn: 'Under Review' },
 *     { key: 'DECISION', label: 'القرار', labelEn: 'Decision' }
 *   ]}
 *   currentStep="REVIEW"
 *   variant="horizontal" // or "vertical"
 * />
 */

import PropTypes from 'prop-types';
import { Box, Typography, Stack, useTheme } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonCheckedIcon from '@mui/icons-material/RadioButtonChecked';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import dayjs from 'dayjs';

const StatusTimeline = ({ steps = [], currentStep, variant = 'horizontal', size = 'medium', showDates = true, language = 'ar' }) => {
  const theme = useTheme();

  const getCurrentStepIndex = () => {
    return steps.findIndex((step) => step.key === currentStep);
  };

  const currentIndex = getCurrentStepIndex();

  const getStepStatus = (index) => {
    if (index < currentIndex) return 'completed';
    if (index === currentIndex) return 'current';
    return 'pending';
  };

  const getStepIcon = (status) => {
    const iconSize = size === 'small' ? 20 : size === 'large' ? 32 : 24;

    switch (status) {
      case 'completed':
        return <CheckCircleIcon sx={{ fontSize: iconSize, color: theme.palette.success.main }} />;
      case 'current':
        return <RadioButtonCheckedIcon sx={{ fontSize: iconSize, color: theme.palette.primary.main }} />;
      default:
        return <RadioButtonUncheckedIcon sx={{ fontSize: iconSize, color: theme.palette.grey[400] }} />;
    }
  };

  const getLineColor = (index) => {
    if (index < currentIndex) return theme.palette.success.main;
    return theme.palette.grey[300];
  };

  const formatDate = (date) => {
    if (!date) return '';
    return dayjs(date).format('DD/MM');
  };

  if (variant === 'vertical') {
    return (
      <Stack spacing={0}>
        {steps.map((step, index) => {
          const status = getStepStatus(index);
          const isLast = index === steps.length - 1;

          return (
            <Box key={step.key} sx={{ display: 'flex', gap: '1.0rem' }}>
              {/* Icon and Line */}
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                {getStepIcon(status)}
                {!isLast && (
                  <Box
                    sx={{
                      width: '0.125rem',
                      height: '2.5rem',
                      bgcolor: getLineColor(index),
                      my: 0.5
                    }}
                  />
                )}
              </Box>

              {/* Content */}
              <Box sx={{ pb: isLast ? 0 : 2 }}>
                <Typography
                  variant={size === 'small' ? 'body2' : 'body1'}
                  fontWeight={status === 'current' ? 600 : 400}
                  color={status === 'pending' ? 'text.secondary' : 'text.primary'}
                >
                  {language === 'ar' ? step.label : step.labelEn}
                </Typography>
                {showDates && step.date && (
                  <Typography variant="caption" color="text.secondary">
                    {formatDate(step.date)}
                  </Typography>
                )}
              </Box>
            </Box>
          );
        })}
      </Stack>
    );
  }

  // Horizontal variant
  return (
    <Box sx={{ width: '100%', py: 1 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ position: 'relative' }}>
        {/* Background line */}
        <Box
          sx={{
            position: 'absolute',
            top: '50%',
            left: 0,
            right: 0,
            height: '0.125rem',
            bgcolor: theme.palette.grey[300],
            transform: 'translateY(-50%)',
            zIndex: 0
          }}
        />

        {/* Progress line */}
        <Box
          sx={{
            position: 'absolute',
            top: '50%',
            left: 0,
            width: `${(currentIndex / (steps.length - 1)) * 100}%`,
            height: '0.125rem',
            bgcolor: theme.palette.success.main,
            transform: 'translateY(-50%)',
            zIndex: 1,
            transition: 'width 0.3s ease'
          }}
        />

        {steps.map((step, index) => {
          const status = getStepStatus(index);

          return (
            <Box
              key={step.key}
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                zIndex: 2,
                bgcolor: 'background.paper',
                px: 0.5
              }}
            >
              {getStepIcon(status)}
              <Typography
                variant={size === 'small' ? 'caption' : 'body2'}
                fontWeight={status === 'current' ? 600 : 400}
                color={status === 'pending' ? 'text.secondary' : 'text.primary'}
                sx={{ mt: 0.5, textAlign: 'center', minWidth: '3.75rem' }}
              >
                {language === 'ar' ? step.label : step.labelEn}
              </Typography>
              {showDates && step.date && (
                <Typography variant="caption" color="text.secondary">
                  {formatDate(step.date)}
                </Typography>
              )}
            </Box>
          );
        })}
      </Stack>
    </Box>
  );
};

StatusTimeline.propTypes = {
  steps: PropTypes.arrayOf(
    PropTypes.shape({
      key: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
      labelEn: PropTypes.string,
      date: PropTypes.string
    })
  ).isRequired,
  currentStep: PropTypes.string.isRequired,
  variant: PropTypes.oneOf(['horizontal', 'vertical']),
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  showDates: PropTypes.bool,
  language: PropTypes.oneOf(['ar', 'en'])
};

export default StatusTimeline;
