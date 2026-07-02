import React from 'react';
import { Chip, Tooltip, Box, Typography } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import MonetizationOnIcon from '@mui/icons-material/MonetizationOn';

/**
 * Enterprise-grade Price Variance Badge Component.
 * Visually distinguishes pricing deviations from the Provider Contract.
 */
const PriceVarianceBadge = ({ contractPrice, manualPrice, status, variancePercentage }) => {
  const getBadgeConfig = () => {
    switch (status) {
      case 'MATCH_CONTRACT':
        return {
          color: 'success',
          icon: <CheckCircleOutlineIcon fontSize="small" />,
          label: 'مطابق للعقد',
          tooltip: `السعر مطابق للتسعيرة التعاقدية (${contractPrice} د.ل)`
        };
      case 'BELOW_CONTRACT':
        return {
          color: 'info',
          icon: <MonetizationOnIcon fontSize="small" />,
          label: `أقل من العقد بـ ${variancePercentage}%`,
          tooltip: `السعر اليدوي أقل من العقد (${contractPrice} د.ل)`
        };
      case 'ABOVE_CONTRACT':
        return {
          color: 'warning',
          icon: <WarningAmberIcon fontSize="small" />,
          label: `أعلى من العقد بـ ${variancePercentage}%`,
          tooltip: `تجاوز السعر التعاقدي (${contractPrice} د.ل)`
        };
      case 'HIGH_VARIANCE':
      case 'CRITICAL_VARIANCE':
        return {
          color: 'error',
          icon: <ErrorOutlineIcon fontSize="small" />,
          label: `تجاوز كبير (${variancePercentage}%)`,
          tooltip: `تجاوز كبير جداً للسعر التعاقدي (${contractPrice} د.ل) - يتطلب مراجعة دقيقة`
        };
      case 'UNLISTED':
        return {
          color: 'secondary', // Purple
          icon: <HelpOutlineIcon fontSize="small" />,
          label: 'خدمة غير مدرجة',
          tooltip: 'هذه الخدمة غير موجودة في العقد وتتطلب تسعير يدوي ومرفقات'
        };
      case 'MISSING_PRICE':
      default:
        return {
          color: 'default',
          icon: null,
          label: 'لا يوجد سعر',
          tooltip: 'لم يتم تحديد سعر للخدمة'
        };
    }
  };

  const config = getBadgeConfig();

  return (
    <Tooltip title={config.tooltip} placement="top" arrow>
      <Chip
        icon={config.icon}
        label={
          <Box display="flex" alignItems="center" gap={1}>
            <Typography variant="caption" fontWeight="bold">
              {config.label}
            </Typography>
          </Box>
        }
        color={config.color}
        size="small"
        variant="filled"
        sx={{
          borderRadius: '4px',
          fontWeight: 'bold',
          '& .MuiChip-label': { padding: '4px 8px' }
        }}
      />
    </Tooltip>
  );
};

export default PriceVarianceBadge;
