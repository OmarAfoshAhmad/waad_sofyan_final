/**
 * PriorityBadge Component
 * Shows urgency/priority level for Pre-Approvals
 *
 * Usage:
 * <PriorityBadge
 *   priority="URGENT" // ROUTINE | URGENT | EMERGENCY
 *   size="medium"
 * />
 */

import PropTypes from 'prop-types';
import { Chip, Box, Typography, useTheme } from '@mui/material';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import PriorityHighIcon from '@mui/icons-material/PriorityHigh';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

const PRIORITY_CONFIG = {
  ROUTINE: {
    labelAr: 'عادي',
    labelEn: 'Routine',
    color: 'info',
    icon: AccessTimeIcon,
    responseTime: '3-5 أيام عمل',
    responseTimeEn: '3-5 business days'
  },
  URGENT: {
    labelAr: 'عاجل',
    labelEn: 'Urgent',
    color: 'warning',
    icon: PriorityHighIcon,
    responseTime: '24-48 ساعة',
    responseTimeEn: '24-48 hours'
  },
  EMERGENCY: {
    labelAr: 'طارئ',
    labelEn: 'Emergency',
    color: 'error',
    icon: LocalHospitalIcon,
    responseTime: '4-8 ساعات',
    responseTimeEn: '4-8 hours'
  }
};

const PriorityBadge = ({
  priority = 'ROUTINE',
  showResponseTime = false,
  size = 'medium',
  variant = 'chip', // 'chip' | 'full'
  language = 'ar'
}) => {
  const theme = useTheme();

  const config = PRIORITY_CONFIG[priority] || PRIORITY_CONFIG.ROUTINE;
  const IconComponent = config.icon;

  const label = language === 'ar' ? config.labelAr : config.labelEn;
  const responseTime = language === 'ar' ? config.responseTime : config.responseTimeEn;
  const iconSize = size === 'small' ? 14 : size === 'large' ? 22 : 18;

  const labels = {
    ar: { expectedResponse: 'وقت الاستجابة المتوقع' },
    en: { expectedResponse: 'Expected Response Time' }
  };
  const t = labels[language] || labels.ar;

  // Full variant with response time
  if (variant === 'full') {
    return (
      <Box
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.75rem',
          px: '1.0rem',
          py: 1,
          borderRadius: '0.25rem',
          bgcolor: `${config.color}.lighter`,
          border: 1,
          borderColor: `${config.color}.light`
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '2.25rem',
            height: '2.25rem',
            borderRadius: '50%',
            bgcolor: `${config.color}.main`,
            color: 'white'
          }}
        >
          <IconComponent sx={{ fontSize: '1.25rem' }} />
        </Box>
        <Box>
          <Typography variant="body2" fontWeight={600} color={`${config.color}.dark`}>
            {label}
          </Typography>
          {showResponseTime && (
            <Typography variant="caption" color="text.secondary">
              {t.expectedResponse}: {responseTime}
            </Typography>
          )}
        </Box>
      </Box>
    );
  }

  // Chip variant (default)
  return (
    <Chip
      icon={<IconComponent sx={{ fontSize: iconSize }} />}
      label={label}
      size={size === 'small' ? 'small' : 'medium'}
      color={config.color}
      sx={{
        fontWeight: 600,
        '& .MuiChip-icon': {
          color: 'inherit'
        },
        ...(priority === 'EMERGENCY' && {
          animation: 'pulse 1.5s infinite'
        })
      }}
    />
  );
};

PriorityBadge.propTypes = {
  priority: PropTypes.oneOf(['ROUTINE', 'URGENT', 'EMERGENCY']),
  showResponseTime: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  variant: PropTypes.oneOf(['chip', 'full']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default PriorityBadge;

// Export config for use in other components
export { PRIORITY_CONFIG };


