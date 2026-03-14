/**
 * MemberTypeIndicator Component
 * Shows Principal/Dependent distinction with visual hierarchy
 *
 * Usage:
 * <MemberTypeIndicator
 *   memberType="PRINCIPAL" // PRINCIPAL | DEPENDENT
 *   relationship="WIFE" // Only for dependents
 *   size="medium"
 * />
 */

import PropTypes from 'prop-types';
import { Chip, Stack, Typography, Avatar, Box, useTheme } from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom';
import Face2Icon from '@mui/icons-material/Face2';
import Face3Icon from '@mui/icons-material/Face3';
import Face4Icon from '@mui/icons-material/Face4';
import Face5Icon from '@mui/icons-material/Face5';
import Face6Icon from '@mui/icons-material/Face6';
import ChildCareIcon from '@mui/icons-material/ChildCare';
import ElderlyIcon from '@mui/icons-material/Elderly';
import ElderlyWomanIcon from '@mui/icons-material/ElderlyWoman';

const RELATIONSHIP_CONFIG = {
  WIFE: {
    labelAr: 'زوجة',
    labelEn: 'Wife',
    icon: Face3Icon,
    color: 'secondary'
  },
  HUSBAND: {
    labelAr: 'زوج',
    labelEn: 'Husband',
    icon: Face4Icon,
    color: 'secondary'
  },
  SON: {
    labelAr: 'ابن',
    labelEn: 'Son',
    icon: Face5Icon,
    color: 'info'
  },
  DAUGHTER: {
    labelAr: 'ابنة',
    labelEn: 'Daughter',
    icon: Face6Icon,
    color: 'info'
  },
  FATHER: {
    labelAr: 'أب',
    labelEn: 'Father',
    icon: ElderlyIcon,
    color: 'warning'
  },
  MOTHER: {
    labelAr: 'أم',
    labelEn: 'Mother',
    icon: ElderlyWomanIcon,
    color: 'warning'
  },
  BROTHER: {
    labelAr: 'أخ',
    labelEn: 'Brother',
    icon: Face4Icon,
    color: 'default'
  },
  SISTER: {
    labelAr: 'أخت',
    labelEn: 'Sister',
    icon: Face3Icon,
    color: 'default'
  },
  CHILD: {
    labelAr: 'طفل',
    labelEn: 'Child',
    icon: ChildCareIcon,
    color: 'info'
  }
};

const MemberTypeIndicator = ({
  memberType = 'PRINCIPAL',
  relationship,
  showLabel = true,
  size = 'medium',
  variant = 'chip', // 'chip' | 'avatar' | 'full'
  language = 'ar'
}) => {
  const theme = useTheme();

  const isPrincipal = memberType === 'PRINCIPAL';

  const labels = {
    ar: {
      principal: 'المؤمَّن الرئيسي',
      principalShort: 'رئيسي',
      dependent: 'تابع',
      dependents: 'التابعين'
    },
    en: {
      principal: 'Primary Insured',
      principalShort: 'Principal',
      dependent: 'Dependent',
      dependents: 'Dependents'
    }
  };

  const t = labels[language] || labels.ar;

  const relationshipConfig = RELATIONSHIP_CONFIG[relationship] || {
    labelAr: t.dependent,
    labelEn: t.dependent,
    icon: FamilyRestroomIcon,
    color: 'default'
  };

  const IconComponent = isPrincipal ? PersonIcon : relationshipConfig.icon;
  const iconSize = size === 'small' ? 16 : size === 'large' ? 28 : 20;
  const avatarSize = size === 'small' ? 28 : size === 'large' ? 48 : 36;

  // Get label
  const getLabel = () => {
    if (isPrincipal) {
      return size === 'small' ? t.principalShort : t.principal;
    }
    return language === 'ar' ? relationshipConfig.labelAr : relationshipConfig.labelEn;
  };

  // Avatar variant
  if (variant === 'avatar') {
    return (
      <Avatar
        sx={{
          width: avatarSize,
          height: avatarSize,
          bgcolor: isPrincipal ? 'primary.main' : `${relationshipConfig.color}.lighter`,
          color: isPrincipal ? 'primary.contrastText' : `${relationshipConfig.color}.dark`
        }}
      >
        <IconComponent sx={{ fontSize: iconSize }} />
      </Avatar>
    );
  }

  // Full variant with label and icon
  if (variant === 'full') {
    return (
      <Box
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 1,
          px: '0.75rem',
          py: 0.75,
          borderRadius: '0.125rem',
          bgcolor: isPrincipal ? 'primary.lighter' : `${relationshipConfig.color}.lighter`,
          border: 1,
          borderColor: isPrincipal ? 'primary.light' : `${relationshipConfig.color}.light`
        }}
      >
        <Avatar
          sx={{
            width: avatarSize,
            height: avatarSize,
            bgcolor: isPrincipal ? 'primary.main' : `${relationshipConfig.color}.main`,
            color: 'white'
          }}
        >
          <IconComponent sx={{ fontSize: iconSize }} />
        </Avatar>
        <Box>
          <Typography
            variant={size === 'small' ? 'caption' : 'body2'}
            fontWeight={600}
            color={isPrincipal ? 'primary.dark' : `${relationshipConfig.color}.dark`}
          >
            {getLabel()}
          </Typography>
          {!isPrincipal && relationship && (
            <Typography variant="caption" color="text.secondary" display="block">
              {t.dependent}
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
      label={showLabel ? getLabel() : undefined}
      size={size === 'small' ? 'small' : 'medium'}
      color={isPrincipal ? 'primary' : relationshipConfig.color}
      variant={isPrincipal ? 'filled' : 'outlined'}
      sx={{
        fontWeight: isPrincipal ? 600 : 400,
        '& .MuiChip-icon': {
          color: isPrincipal ? 'inherit' : `${relationshipConfig.color}.main`
        }
      }}
    />
  );
};

MemberTypeIndicator.propTypes = {
  memberType: PropTypes.oneOf(['PRINCIPAL', 'DEPENDENT']),
  relationship: PropTypes.oneOf(['WIFE', 'HUSBAND', 'SON', 'DAUGHTER', 'FATHER', 'MOTHER', 'BROTHER', 'SISTER', 'CHILD']),
  showLabel: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  variant: PropTypes.oneOf(['chip', 'avatar', 'full']),
  language: PropTypes.oneOf(['ar', 'en'])
};

export default MemberTypeIndicator;

// Export relationship config for use in other components
export { RELATIONSHIP_CONFIG };


