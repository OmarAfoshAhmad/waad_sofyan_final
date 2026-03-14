/**
 * CardStatusBadge Component
 * Shows member card status with visual indicator
 *
 * Usage:
 * <CardStatusBadge
 *   status="ACTIVE" // ACTIVE | SUSPENDED | BLOCKED | EXPIRED | PENDING
 *   size="medium"
 * />
 */

import PropTypes from 'prop-types';
import { Chip, Stack, Typography, Box, Tooltip, useTheme } from '@mui/material';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PauseCircleIcon from '@mui/icons-material/PauseCircle';
import BlockIcon from '@mui/icons-material/Block';
import ErrorIcon from '@mui/icons-material/Error';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';

const CARD_STATUS_CONFIG = {
  ACTIVE: {
    labelAr: 'نشطة',
    labelEn: 'Active',
    color: 'success',
    icon: CheckCircleIcon,
    bgColor: '#E8F5E9',
    borderColor: '#4CAF50'
  },
  SUSPENDED: {
    labelAr: 'موقوفة',
    labelEn: 'Suspended',
    color: 'warning',
    icon: PauseCircleIcon,
    bgColor: '#FFF3E0',
    borderColor: '#FF9800'
  },
  BLOCKED: {
    labelAr: 'محظورة',
    labelEn: 'Blocked',
    color: 'error',
    icon: BlockIcon,
    bgColor: '#FFEBEE',
    borderColor: '#F44336'
  },
  EXPIRED: {
    labelAr: 'منتهية',
    labelEn: 'Expired',
    color: 'error',
    icon: ErrorIcon,
    bgColor: '#FFEBEE',
    borderColor: '#F44336'
  },
  PENDING: {
    labelAr: 'قيد الإصدار',
    labelEn: 'Pending',
    color: 'info',
    icon: HourglassEmptyIcon,
    bgColor: '#E3F2FD',
    borderColor: '#2196F3'
  },
  INACTIVE: {
    labelAr: 'غير نشطة',
    labelEn: 'Inactive',
    color: 'default',
    icon: CreditCardIcon,
    bgColor: '#FAFAFA',
    borderColor: '#9E9E9E'
  }
};

const CardStatusBadge = ({
  status = 'ACTIVE',
  cardNumber,
  showCardIcon = true,
  size = 'medium',
  variant = 'chip', // 'chip' | 'card' | 'minimal'
  language = 'ar'
}) => {
  const theme = useTheme();

  const config = CARD_STATUS_CONFIG[status] || CARD_STATUS_CONFIG.INACTIVE;
  const IconComponent = config.icon;

  const label = language === 'ar' ? config.labelAr : config.labelEn;
  const iconSize = size === 'small' ? 14 : size === 'large' ? 22 : 18;

  // Minimal variant - just icon
  if (variant === 'minimal') {
    return (
      <Tooltip title={label}>
        <IconComponent
          sx={{
            fontSize: iconSize,
            color: `${config.color}.main`
          }}
        />
      </Tooltip>
    );
  }

  // Card variant - looks like a mini card
  if (variant === 'card') {
    return (
      <Box
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.75rem',
          px: '1.0rem',
          py: 1,
          borderRadius: '0.125rem',
          bgcolor: config.bgColor,
          border: 2,
          borderColor: config.borderColor,
          minWidth: size === 'small' ? 120 : 160
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: size === 'small' ? 32 : 40,
            height: size === 'small' ? 24 : 28,
            borderRadius: 1,
            bgcolor: config.borderColor,
            color: 'white'
          }}
        >
          <CreditCardIcon sx={{ fontSize: size === 'small' ? 16 : 20 }} />
        </Box>
        <Box>
          <Typography variant={size === 'small' ? 'caption' : 'body2'} fontWeight={600} color={`${config.color}.dark`}>
            {label}
          </Typography>
          {cardNumber && (
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
              {cardNumber.slice(-4).padStart(cardNumber.length, '•')}
            </Typography>
          )}
        </Box>
        <IconComponent
          sx={{
            ml: 'auto',
            fontSize: size === 'small' ? 18 : 24,
            color: config.borderColor
          }}
        />
      </Box>
    );
  }

  // Chip variant (default)
  return (
    <Chip
      icon={showCardIcon ? <CreditCardIcon sx={{ fontSize: iconSize }} /> : <IconComponent sx={{ fontSize: iconSize }} />}
      label={label}
      size={size === 'small' ? 'small' : 'medium'}
      sx={{
        fontWeight: 500,
        bgcolor: config.bgColor,
        color: config.borderColor,
        borderColor: config.borderColor,
        border: 1,
        '& .MuiChip-icon': {
          color: config.borderColor
        }
      }}
    />
  );
};

CardStatusBadge.propTypes = {
  status: PropTypes.oneOf(['ACTIVE', 'SUSPENDED', 'BLOCKED', 'EXPIRED', 'PENDING', 'INACTIVE']),
  cardNumber: PropTypes.string,
  showCardIcon: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  variant: PropTypes.oneOf(['chip', 'card', 'minimal']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default CardStatusBadge;

// Export config for use in other components
export { CARD_STATUS_CONFIG };


