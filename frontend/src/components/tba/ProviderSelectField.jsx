import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import EntityAutocompleteSelect from './EntityAutocompleteSelect';
import { getProviderSelectorsCached } from 'services/api/providers.service';

const ProviderSelectField = ({
  value,
  onChange,
  label = 'مقدم الخدمة',
  placeholder = 'اختر مقدم الخدمة...',
  required = false,
  error = false,
  helperText = '',
  disabled = false,
  size = 'small',
  fullWidth = true,
  sx = {},
  options,
  allLabel
}) => {
  const [providers, setProviders] = useState([]);
  const [loading, setLoading] = useState(!options);

  useEffect(() => {
    if (options) {
      setProviders(Array.isArray(options) ? options : []);
      setLoading(false);
      return undefined;
    }

    let cancelled = false;
    (async () => {
      try {
        const items = await getProviderSelectorsCached();
        if (!cancelled) setProviders(Array.isArray(items) ? items : []);
      } catch (err) {
        console.error('[ProviderSelectField] Failed to load providers:', err);
        if (!cancelled) setProviders([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [options]);

  return (
    <EntityAutocompleteSelect
      value={value}
      onChange={onChange}
      options={providers}
      label={label}
      placeholder={placeholder}
      allLabel={allLabel}
      loading={loading}
      disabled={disabled}
      required={required}
      error={error}
      helperText={helperText}
      size={size}
      fullWidth={fullWidth}
      sx={sx}
      icon={<LocalHospitalIcon fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />}
    />
  );
};

ProviderSelectField.propTypes = {
  value: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  placeholder: PropTypes.string,
  required: PropTypes.bool,
  error: PropTypes.bool,
  helperText: PropTypes.string,
  disabled: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium']),
  fullWidth: PropTypes.bool,
  sx: PropTypes.object,
  options: PropTypes.array,
  allLabel: PropTypes.string
};

export default ProviderSelectField;
