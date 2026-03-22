/**
 * EmployerFilterSelector
 *
 * Standalone employer/partner filter dropdown component.
 * Can be used independently without EmployerFilterContext.
 *
 * Features:
 * - Autocomplete dropdown with search
 * - Shows employer name (Arabic primary, English secondary)
 * - Controlled component (value passed from parent)
 * - Clear filter button
 * - Proper event handling for parent state management
 *
 * Usage:
 * ```jsx
 * import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';
 *
 * <EmployerFilterSelector
 *   selectedEmployerId={employerId}
 *   onEmployerChange={(employer) => setEmployerId(employer?.id || null)}
 *   showAllOption={true}
 * />
 * ```
 */

import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Autocomplete, TextField, Box, Typography, IconButton, Chip } from '@mui/material';
import ClearIcon from '@mui/icons-material/Clear';
import BusinessIcon from '@mui/icons-material/Business';
import useAuth from 'hooks/useAuth';

// Services
import { getEmployerSelectors } from 'services/api/employers.service';

// Context - for auto-connect mode
import { useEmployerFilter } from 'contexts/EmployerFilterContext';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const EmployerFilterSelector = ({
  selectedEmployerId: propSelectedEmployerId,
  onEmployerChange: propOnEmployerChange,
  showAllOption = true,
  size = 'small',
  label = 'الشريك',
  placeholder = 'اختر شريكاً...',
  disabled = false,
  inverseColors = false,
  sx = {}
}) => {
  const { user } = useAuth();
  const userRole = (user?.role || (Array.isArray(user?.roles) ? user.roles[0] : '') || '').toUpperCase();
  const isProviderUser = userRole === 'PROVIDER' || userRole === 'PROVIDER_STAFF';

  // Auto-connect to EmployerFilterContext when no props provided
  const { selectedEmployerId: contextEmployerId, setEmployer, clearFilter } = useEmployerFilter();

  // Use props if provided, otherwise fall back to context
  const selectedEmployerId = propSelectedEmployerId !== undefined ? propSelectedEmployerId : contextEmployerId;
  const onEmployerChange =
    propOnEmployerChange ||
    ((emp) => {
      if (emp) {
        setEmployer(emp);
      } else {
        clearFilter();
      }
    });

  const [employers, setEmployers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedValue, setSelectedValue] = useState(null);

  /**
   * Load employers on mount
   */
  useEffect(() => {
    if (isProviderUser) {
      setEmployers(showAllOption ? [{ id: 'ALL', label: 'الكل (All)', name: 'الكل' }] : []);
      setLoading(false);
      return;
    }

    const loadEmployers = async () => {
      try {
        setLoading(true);
        const response = await getEmployerSelectors();

        // Response should be array of { id, label, code }
        let items = Array.isArray(response) ? response : [];

        // Add "All" option if requested
        if (showAllOption) {
          const allOption = { id: 'ALL', label: 'الكل (All)', name: 'الكل' };
          items = [allOption, ...items];
        }

        setEmployers(items);

        // If selectedEmployerId is provided, find and set the employer object
        if (selectedEmployerId) {
          if (selectedEmployerId === 'ALL') {
            setSelectedValue(items[0]);
          } else {
            const found = items.find((emp) => emp.id === selectedEmployerId);
            if (found) {
              setSelectedValue(found);
            }
          }
        }
      } catch (error) {
        const isForbidden = error?.response?.status === 403 || error?.status === 403;
        if (isForbidden) {
          console.warn('[EmployerFilter] Employer selectors not permitted for current role (403)');
          setEmployers(showAllOption ? [{ id: 'ALL', label: 'الكل (All)', name: 'الكل' }] : []);
        } else {
          console.error('[EmployerFilter] Failed to load employers:', error);
          setEmployers([]);
        }
      } finally {
        setLoading(false);
      }
    };

    loadEmployers();
  }, [isProviderUser, showAllOption]);

  /**
   * Sync selected value when selectedEmployerId changes
   */
  useEffect(() => {
    if (selectedEmployerId && employers.length > 0) {
      const found = employers.find((emp) => emp.id === selectedEmployerId);
      if (found) {
        setSelectedValue(found);
      }
    } else {
      setSelectedValue(null);
    }
  }, [selectedEmployerId, employers]);

  /**
   * Handle employer selection
   */
  const handleChange = (event, value) => {
    setSelectedValue(value);

    // Call parent handler with full employer object
    if (onEmployerChange) {
      // If "ALL" is selected, pass null to parent to clear filter
      if (value && value.id === 'ALL') {
        onEmployerChange(null);
      } else {
        onEmployerChange(value);
      }
    }
  };

  /**
   * Handle clear button
   */
  const handleClear = () => {
    setSelectedValue(null);

    if (onEmployerChange) {
      onEmployerChange(null);
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ...sx }}>
      <Autocomplete
        value={selectedValue}
        onChange={handleChange}
        options={employers}
        getOptionLabel={(option) => option?.label || option?.name || ''}
        isOptionEqualToValue={(option, value) => option?.id === value?.id}
        loading={loading}
        disabled={disabled}
        size={size}
        sx={{
          minWidth: '18.75rem',
          ...(inverseColors && {
            '& .MuiInputBase-root': { color: '#fff', bgcolor: 'rgba(255,255,255,0.1)' },
            '& .MuiInputLabel-root': { color: 'rgba(255,255,255,0.7)' },
            '& .MuiInputLabel-root.Mui-focused': { color: '#fff' },
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.3)' },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.5)' },
            '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#fff' },
            '& .MuiSvgIcon-root': { color: 'rgba(255,255,255,0.8)' },
          })
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            label={label}
            placeholder={placeholder}
            size={size}
            InputProps={{
              ...params.InputProps,
              startAdornment: (
                <>
                  <BusinessIcon sx={{ mr: 1, color: inverseColors ? 'rgba(255,255,255,0.7)' : 'text.secondary' }} />
                  {params.InputProps.startAdornment}
                </>
              )
            }}
          />
        )}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id}>
            <Box>
              <Typography variant="body1">{option.label || option.name}</Typography>
              {option.code && (
                <Typography variant="caption" color="text.secondary">
                  {option.code}
                </Typography>
              )}
            </Box>
          </Box>
        )}
      />

      {selectedValue && showAllOption && (
        <Chip
          label={selectedValue?.label || selectedValue?.name || 'مُفلتر'}
          onDelete={handleClear}
          color="primary"
          variant={inverseColors ? 'filled' : 'outlined'}
          size="small"
          deleteIcon={<ClearIcon />}
          sx={{
            flexShrink: 0,
            ...(inverseColors && {
              bgcolor: 'rgba(255,255,255,0.2)',
              color: '#fff',
              backdropFilter: 'blur(4px)',
              fontWeight: 600,
              border: '1px solid rgba(255,255,255,0.1)',
              '& .MuiChip-deleteIcon': {
                color: 'rgba(255,255,255,0.7)',
                '&:hover': { color: '#fff' }
              }
            })
          }}
        />
      )}
    </Box>
  );
};

// ============================================================================
// PROP TYPES
// ============================================================================

EmployerFilterSelector.propTypes = {
  selectedEmployerId: PropTypes.number,
  onEmployerChange: PropTypes.func,
  showAllOption: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium']),
  label: PropTypes.string,
  placeholder: PropTypes.string,
  disabled: PropTypes.bool,
  inverseColors: PropTypes.bool,
  sx: PropTypes.object
};

export default EmployerFilterSelector;
