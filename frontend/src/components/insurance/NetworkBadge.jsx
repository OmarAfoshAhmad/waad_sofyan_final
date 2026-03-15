/**
 * NetworkBadge Component
 * Shows Provider network status with visual indicator
 *
 * Usage:
 * <NetworkBadge
 *   networkTier="PREFERRED" // PREFERRED | STANDARD | OUT_OF_NETWORK
 *   showLabel={true}
 *   size="medium"
 * />
 */

import PropTypes from 'prop-types';
import { Chip, Stack, Typography, Box, Tooltip, useTheme } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import VerifiedIcon from '@mui/icons-material/Verified';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import StarIcon from '@mui/icons-material/Star';

const NETWORK_TIERS = {
  PREFERRED: {
    labelAr: 'شبكة مفضلة',
    labelEn: 'Preferred Network',
    shortAr: 'مفضل',
    shortEn: 'Preferred',
    color: 'success',
    icon: VerifiedIcon,
    discount: '15-25%'
  },
  TIER_1: {
    labelAr: 'المستوى الأول',
    labelEn: 'Tier 1 Network',
    shortAr: 'الأول',
    shortEn: 'Tier 1',
    color: 'success',
    icon: CheckCircleIcon,
    discount: '10-20%'
  },
  STANDARD: {
    labelAr: 'الشبكة القياسية',
    labelEn: 'Standard Network',
    shortAr: 'قياسي',
    shortEn: 'Standard',
    color: 'primary',
    icon: CheckCircleIcon,
    discount: '5-10%'
  },
  IN_NETWORK: {
    labelAr: 'ضمن الشبكة',
    labelEn: 'In-Network',
    shortAr: 'ضمن الشبكة',
    shortEn: 'In-Network',
    color: 'success',
    icon: CheckCircleIcon,
    discount: ''
  },
  OUT_OF_NETWORK: {
    labelAr: 'خارج الشبكة',
    labelEn: 'Out-of-Network',
    shortAr: 'خارج الشبكة',
    shortEn: 'Out-of-Network',
    color: 'warning',
    icon: WarningAmberIcon,
    discount: '0%'
  },
  NOT_CONTRACTED: {
    labelAr: 'غير متعاقد',
    labelEn: 'Not Contracted',
    shortAr: 'غير متعاقد',
    shortEn: 'Not Contracted',
    color: 'error',
    icon: ErrorOutlineIcon,
    discount: ''
  }
};

const NetworkBadge = ({
  networkTier = 'STANDARD',
  showLabel = true,
  showDiscount = false,
  size = 'medium',
  variant = 'chip', // 'chip' | 'icon' | 'full'
  language = 'ar'
}) => {
  const theme = useTheme();

  const tier = NETWORK_TIERS[networkTier] || NETWORK_TIERS.STANDARD;
  const IconComponent = tier.icon;

  const label = language === 'ar' ? tier.shortAr : tier.shortEn;
  const fullLabel = language === 'ar' ? tier.labelAr : tier.labelEn;

  const iconSize = size === 'small' ? 16 : size === 'large' ? 24 : 20;

  // Icon only variant
  if (variant === 'icon') {
    return (
      <Tooltip title={fullLabel}>
        <IconComponent
          sx={{
            fontSize: iconSize,
            color: `${tier.color}.main`
          }}
        />
      </Tooltip>
    );
  }

  // Full variant with details
  if (variant === 'full') {
    return (
      <Box
        sx={{
          p: '0.75rem',
          borderRadius: '0.25rem',
          bgcolor: `${tier.color}.lighter`,
          border: 1,
          borderColor: `${tier.color}.light`
        }}
      >
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box
            sx={{
              width: '2.5rem',
              height: '2.5rem',
              borderRadius: '50%',
              bgcolor: `${tier.color}.main`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <IconComponent sx={{ fontSize: '1.5rem', color: 'white' }} />
          </Box>
          <Box>
            <Typography variant="subtitle2" fontWeight={600} color={`${tier.color}.dark`}>
              {fullLabel}
            </Typography>
            {showDiscount && tier.discount && (
              <Typography variant="caption" color="text.secondary">
                {language === 'ar' ? `خصم: ${tier.discount}` : `Discount: ${tier.discount}`}
              </Typography>
            )}
          </Box>
          {networkTier === 'PREFERRED' && <StarIcon sx={{ color: 'warning.main', ml: 'auto' }} />}
        </Stack>
      </Box>
    );
  }

  // Chip variant (default)
  return (
    <Chip
      icon={<IconComponent sx={{ fontSize: iconSize }} />}
      label={showLabel ? label : undefined}
      size={size === 'small' ? 'small' : 'medium'}
      color={tier.color}
      variant="outlined"
      sx={{
        fontWeight: 500,
        '& .MuiChip-icon': {
          color: `${tier.color}.main`
        }
      }}
    />
  );
};

NetworkBadge.propTypes = {
  networkTier: PropTypes.oneOf(['PREFERRED', 'TIER_1', 'STANDARD', 'IN_NETWORK', 'OUT_OF_NETWORK', 'NOT_CONTRACTED']),
  showLabel: PropTypes.bool,
  showDiscount: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  variant: PropTypes.oneOf(['chip', 'icon', 'full']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default NetworkBadge;

// Export network tier constants for use in other components
export { NETWORK_TIERS };


