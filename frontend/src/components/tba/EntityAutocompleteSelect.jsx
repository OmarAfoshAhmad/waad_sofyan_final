import PropTypes from 'prop-types';
import { Autocomplete, Box, TextField, Typography } from '@mui/material';

const normalizeOptionLabel = (option) => option?.label || option?.name || option?.nameAr || option?.fullName || '';

const EntityAutocompleteSelect = ({
  value,
  onChange,
  options = [],
  label,
  placeholder,
  allLabel,
  icon,
  loading = false,
  disabled = false,
  required = false,
  error = false,
  helperText = '',
  size = 'small',
  fullWidth = true,
  sx = {}
}) => {
  const resolvedOptions = allLabel ? [{ id: '', label: allLabel, name: allLabel, code: '' }, ...options] : options;
  const selected = resolvedOptions.find((option) => String(option?.id ?? '') === String(value ?? '')) || null;

  return (
    <Autocomplete
      value={selected}
      onChange={(event, newValue) => onChange(newValue?.id === '' ? null : (newValue?.id ?? null), newValue ?? null)}
      options={resolvedOptions}
      getOptionLabel={normalizeOptionLabel}
      isOptionEqualToValue={(option, val) => String(option?.id ?? '') === String(val?.id ?? '')}
      loading={loading}
      disabled={disabled}
      size={size}
      fullWidth={fullWidth}
      sx={sx}
      noOptionsText="لا توجد نتائج"
      loadingText="جاري التحميل..."
      clearText="مسح"
      openText="فتح القائمة"
      closeText="إغلاق القائمة"
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          required={required}
          error={error}
          helperText={helperText}
          InputProps={{
            ...params.InputProps,
            startAdornment: (
              <>
                {icon}
                {params.InputProps.startAdornment}
              </>
            )
          }}
        />
      )}
      renderOption={(props, option) => (
        <Box component="li" {...props} key={`${option.id}-${normalizeOptionLabel(option)}`}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>
              {normalizeOptionLabel(option)}
            </Typography>
            {option.code && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', direction: 'ltr', textAlign: 'right' }} noWrap>
                {option.code}
              </Typography>
            )}
          </Box>
        </Box>
      )}
    />
  );
};

EntityAutocompleteSelect.propTypes = {
  value: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onChange: PropTypes.func.isRequired,
  options: PropTypes.array,
  label: PropTypes.string.isRequired,
  placeholder: PropTypes.string,
  allLabel: PropTypes.string,
  icon: PropTypes.node,
  loading: PropTypes.bool,
  disabled: PropTypes.bool,
  required: PropTypes.bool,
  error: PropTypes.bool,
  helperText: PropTypes.string,
  size: PropTypes.oneOf(['small', 'medium']),
  fullWidth: PropTypes.bool,
  sx: PropTypes.object
};

export default EntityAutocompleteSelect;
