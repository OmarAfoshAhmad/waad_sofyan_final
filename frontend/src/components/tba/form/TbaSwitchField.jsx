/**
 * TbaSwitchField - Unified Switch Field Component
 * Phase D3 - TbaForm System (Mantis-Native)
 *
 * ⚠️ CONTRACT:
 * - Switch with label and helper text
 * - Unified styling
 * - RTL compatible
 * - Mantis theme compliant
 *
 * Usage:
 * <TbaSwitchField
 *   label="تفعيل الخدمة"
 *   helperText="الخدمة نشطة وظاهرة في النظام"
 *   checked={form.active}
 *   onChange={handleChange('active')}
 * />
 */

import PropTypes from 'prop-types';

// MUI Components
import { Box, Switch, Typography, Paper, Stack } from '@mui/material';

// ============================================================================
// COMPONENT
// ============================================================================

const TbaSwitchField = ({
  label,
  helperText,
  helperTextOff,
  checked = false,
  onChange,
  disabled = false,
  readOnly = false,
  variant = 'outlined', // 'outlined' | 'standard'
  sx = {}
}) => {
  // Determine helper text based on checked state
  const displayHelperText = checked ? helperText : helperTextOff || helperText;

  const switchControl = (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        width: '100%'
      }}
    >
      <Stack spacing={0.25} sx={{ flex: 1 }}>
        <Typography variant="body2" fontWeight={500} color="text.primary">
          {label}
        </Typography>
        {displayHelperText && (
          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.4 }}>
            {displayHelperText}
          </Typography>
        )}
      </Stack>
      <Switch checked={checked} onChange={onChange} disabled={disabled || readOnly} color="primary" sx={{ ml: '1.0rem' }} />
    </Box>
  );

  if (variant === 'standard') {
    return <Box sx={sx}>{switchControl}</Box>;
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        p: '1.0rem',
        borderRadius: '0.25rem',
        bgcolor: readOnly ? 'action.hover' : 'background.paper',
        borderColor: 'grey.200',
        transition: 'all 0.2s ease-in-out',
        '&:hover': {
          borderColor: 'primary.light',
          bgcolor: 'grey.50'
        },
        ...sx
      }}
    >
      {switchControl}
    </Paper>
  );
};

TbaSwitchField.propTypes = {
  /** Switch label */
  label: PropTypes.string.isRequired,
  /** Helper text when ON */
  helperText: PropTypes.string,
  /** Helper text when OFF (optional, defaults to helperText) */
  helperTextOff: PropTypes.string,
  /** Checked state */
  checked: PropTypes.bool,
  /** Change handler */
  onChange: PropTypes.func,
  /** Disabled state */
  disabled: PropTypes.bool,
  /** Read-only mode */
  readOnly: PropTypes.bool,
  /** Visual variant */
  variant: PropTypes.oneOf(['outlined', 'standard']),
  /** Additional styles */
  sx: PropTypes.object
};

export default TbaSwitchField;
