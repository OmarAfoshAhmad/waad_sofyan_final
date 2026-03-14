/**
 * TbaDetailField - Unified Detail Field Component (View Mode)
 * Phase D3 - TbaForm System (Mantis-Native)
 *
 * ⚠️ CONTRACT:
 * - Read-only display of data
 * - Label + Value format
 * - Fallback to "-" for empty values
 * - RTL compatible
 *
 * Usage:
 * <TbaDetailField
 *   label="الاسم (عربي)"
 *   value={service.nameAr}
 * />
 */

import PropTypes from 'prop-types';

// MUI Components
import { Box, Paper, Typography, Chip, Stack } from '@mui/material';

// ============================================================================
// COMPONENT
// ============================================================================

const TbaDetailField = ({
  label,
  value,
  children,
  fallback = '-',
  variant = 'outlined', // 'outlined' | 'standard' | 'filled'
  chip = false,
  chipColor = 'default',
  multiline = false,
  icon: Icon,
  sx = {}
}) => {
  // Format display value
  const displayValue = value !== null && value !== undefined && value !== '' ? value : fallback;

  // Determine if value is empty
  const isEmpty = displayValue === fallback && !children;

  const content = (
    <Box>
      {/* Label */}
      <Typography
        variant="caption"
        color="text.secondary"
        display="block"
        sx={{ mb: 0.75, fontWeight: 500, textTransform: 'uppercase', letterSpacing: 0.5, fontSize: '0.7rem' }}
      >
        {label}
      </Typography>

      {/* Value */}
      {children ? (
        children
      ) : (
        <Stack direction="row" alignItems="center" spacing={1}>
          {Icon && <Icon sx={{ fontSize: '1.125rem' }} color={isEmpty ? 'disabled' : 'action'} />}

          {chip ? (
            <Chip label={displayValue} color={chipColor} size="small" variant="light" />
          ) : (
            <Typography
              variant="body2"
              fontWeight={isEmpty ? 400 : 500}
              color={isEmpty ? 'text.disabled' : 'text.primary'}
              sx={{ whiteSpace: multiline ? 'pre-wrap' : 'normal', lineHeight: 1.6 }}
            >
              {displayValue}
            </Typography>
          )}
        </Stack>
      )}
    </Box>
  );

  if (variant === 'standard') {
    return <Box sx={sx}>{content}</Box>;
  }

  if (variant === 'filled') {
    return (
      <Paper
        elevation={0}
        sx={{
          p: '0.875rem',
          bgcolor: 'grey.50',
          borderRadius: '0.375rem',
          ...sx
        }}
      >
        {content}
      </Paper>
    );
  }

  // Default: outlined
  return (
    <Paper
      variant="outlined"
      sx={{
        p: '0.875rem',
        borderRadius: '0.375rem',
        borderColor: 'grey.200',
        ...sx
      }}
    >
      {content}
    </Paper>
  );
};

TbaDetailField.propTypes = {
  /** Field label */
  label: PropTypes.string.isRequired,
  /** Field value */
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number, PropTypes.bool]),
  /** Custom children content (overrides value) */
  children: PropTypes.node,
  /** Fallback for empty values */
  fallback: PropTypes.string,
  /** Enable multiline display */
  multiline: PropTypes.bool,
  /** Visual variant */
  variant: PropTypes.oneOf(['outlined', 'standard', 'filled']),
  /** Display as chip */
  chip: PropTypes.bool,
  /** Chip color (when chip=true) */
  chipColor: PropTypes.string,
  /** Icon component */
  icon: PropTypes.elementType,
  /** Additional styles */
  sx: PropTypes.object
};

export default TbaDetailField;



