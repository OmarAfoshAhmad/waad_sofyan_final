/**
 * TbaSelectField - Unified Select Field Component
 * Phase D3 - TbaForm System (Mantis-Native)
 *
 * ⚠️ CONTRACT:
 * - Wrapper around MUI Select
 * - Unified styling: size="small", fullWidth
 * - Arabic placeholder support
 * - Empty/null safe
 * - RTL compatible
 *
 * Usage:
 * <TbaSelectField
 *   label="التصنيف"
 *   value={form.categoryId}
 *   options={categories}
 *   optionValue="id"
 *   optionLabel="nameAr"
 *   required
 *   error={errors.categoryId}
 *   onChange={handleChange('categoryId')}
 * />
 */

import PropTypes from 'prop-types';

// MUI Components
import { FormControl, InputLabel, Select, MenuItem, FormHelperText } from '@mui/material';

// ============================================================================
// COMPONENT
// ============================================================================

const TbaSelectField = ({
  label,
  value,
  onChange,
  options = [],
  optionValue = 'id',
  optionLabel = 'name',
  placeholder = '-- اختر --',
  error,
  helperText,
  required = false,
  disabled = false,
  readOnly = false,
  loading = false,
  sx = {},
  ...rest
}) => {
  // Ensure options is always an array
  const safeOptions = Array.isArray(options) ? options : [];

  // Get display value for option
  const getOptionLabel = (option) => {
    if (!option) return '';
    if (typeof optionLabel === 'function') {
      return optionLabel(option);
    }
    return option[optionLabel] || option.name || '-';
  };

  // Get value for option
  const getOptionValue = (option) => {
    if (!option) return '';
    if (typeof optionValue === 'function') {
      return optionValue(option);
    }
    return option[optionValue] ?? option.id ?? '';
  };

  return (
    <FormControl fullWidth size="small" error={!!error} required={required} disabled={disabled || readOnly || loading}>
      <InputLabel>{label}</InputLabel>
      <Select
        value={value ?? ''}
        onChange={onChange}
        label={label}
        sx={{
          bgcolor: readOnly ? 'action.hover' : 'background.paper',
          borderRadius: '0.375rem',
          '&:hover:not(.Mui-disabled)': {
            bgcolor: 'grey.50'
          },
          '&.Mui-focused': {
            bgcolor: 'background.paper',
            boxShadow: '0 0 0 2px rgba(24, 144, 255, 0.1)'
          },
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: 'grey.300'
          },
          ...sx
        }}
        {...rest}
      >
        {/* Placeholder option */}
        <MenuItem value="">
          <em>{placeholder}</em>
        </MenuItem>

        {/* Loading state */}
        {loading && (
          <MenuItem value="" disabled>
            جاري التحميل...
          </MenuItem>
        )}

        {/* Options */}
        {safeOptions.map((option, index) => (
          <MenuItem key={getOptionValue(option) || index} value={getOptionValue(option)}>
            {getOptionLabel(option)}
          </MenuItem>
        ))}
      </Select>

      {/* Helper/Error text */}
      {(error || helperText) && <FormHelperText>{error || helperText}</FormHelperText>}
    </FormControl>
  );
};

TbaSelectField.propTypes = {
  /** Field label */
  label: PropTypes.string.isRequired,
  /** Selected value */
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  /** Change handler */
  onChange: PropTypes.func,
  /** Options array */
  options: PropTypes.array,
  /** Property name for option value (or function) */
  optionValue: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
  /** Property name for option label (or function) */
  optionLabel: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
  /** Placeholder text */
  placeholder: PropTypes.string,
  /** Error message */
  error: PropTypes.string,
  /** Helper text */
  helperText: PropTypes.string,
  /** Required field */
  required: PropTypes.bool,
  /** Disabled state */
  disabled: PropTypes.bool,
  /** Read-only mode */
  readOnly: PropTypes.bool,
  /** Loading state */
  loading: PropTypes.bool,
  /** Additional styles */
  sx: PropTypes.object
};

export default TbaSelectField;



