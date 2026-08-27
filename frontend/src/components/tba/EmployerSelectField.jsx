/**
 * EmployerSelectField
 *
 * Searchable employer picker for plain forms (member create/edit, user
 * create, benefit policy owner, provider contract scoping, report filters,
 * ...). Sibling to EmployerFilterSelector, which is built for the
 * "الشريك" filter-bar UX (context auto-connect, "All" option, Chip clear).
 * This one is the plain single-value form-field shape: controlled by
 * `value` (employer id) + `onChange(employerId)`, with the usual
 * MUI TextField validation props (`required`/`error`/`helperText`) so it
 * drops straight into an existing form's error handling.
 *
 * Replaces the plain, unsearchable `<Select>` + `employers.map(...)`
 * pattern that was duplicated across ~10 screens -- becomes unusable once
 * the employer list grows past a screenful, which is exactly what
 * happened.
 *
 * Usage:
 * ```jsx
 * <EmployerSelectField
 *   value={form.employerId}
 *   onChange={(employerId) => setForm((prev) => ({ ...prev, employerId }))}
 *   required
 *   error={!!errors.employerId}
 *   helperText={errors.employerId}
 * />
 * ```
 *
 * `onChange(employerId, employerOption)` also receives the full selected
 * `{ id, label, code }` option as a second argument, for callers that need
 * more than the id (e.g. auto-filling a "policy name" field from the
 * employer's label on selection).
 */

import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Autocomplete, TextField } from '@mui/material';
import { getEmployerSelectorsCached } from 'services/api/employers.service';

const EmployerSelectField = ({
  value,
  onChange,
  label = 'جهة العمل',
  placeholder = 'اختر جهة العمل...',
  required = false,
  error = false,
  helperText = '',
  disabled = false,
  size = 'small',
  fullWidth = true,
  sx = {}
}) => {
  const [employers, setEmployers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const items = await getEmployerSelectorsCached();
        if (!cancelled) setEmployers(Array.isArray(items) ? items : []);
      } catch (err) {
        console.error('[EmployerSelectField] Failed to load employers:', err);
        if (!cancelled) setEmployers([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const selected = employers.find((emp) => emp.id === value) || null;

  return (
    <Autocomplete
      value={selected}
      onChange={(event, newValue) => onChange(newValue?.id ?? null, newValue ?? null)}
      options={employers}
      getOptionLabel={(option) => option?.label || option?.name || ''}
      isOptionEqualToValue={(option, val) => option?.id === val?.id}
      loading={loading}
      disabled={disabled}
      size={size}
      fullWidth={fullWidth}
      sx={sx}
      renderInput={(params) => (
        <TextField {...params} label={label} placeholder={placeholder} required={required} error={error} helperText={helperText} />
      )}
    />
  );
};

EmployerSelectField.propTypes = {
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
  sx: PropTypes.object
};

export default EmployerSelectField;
