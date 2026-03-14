/**
 * MedicalServiceSelector - Unified Medical Service Selection Component
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL LAW (NON-NEGOTIABLE)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * MedicalService MUST always be represented as:
 *   CODE + NAME + CATEGORY
 *
 * Anywhere a service is selectable or displayed.
 * NO EXCEPTIONS.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Display Format:
 *   [SVC-001] أشعة مقطعية CT Scan
 *   🗂 التصنيف: الأشعة التشخيصية
 *
 * Search Behavior:
 * - Type ANY of: Code, Arabic name, English name, Category
 *
 * Selection:
 * - Stores medicalServiceId ONLY
 *
 * Used in:
 * - Provider Contract form (Pricing Item selector)
 * - Benefit Policy Rule form (Service selector)
 * - Provider Portal (Claim / PreAuth service lines)
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Autocomplete,
  Box,
  Chip,
  CircularProgress,
  FormControl,
  FormHelperText,
  InputAdornment,
  Stack,
  TextField,
  Typography,
  Paper,
  alpha
} from '@mui/material';
import {
  MedicalServices as MedicalIcon,
  Search as SearchIcon,
  Category as CategoryIcon,
  CheckCircle as CheckIcon
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { lookupMedicalServices } from 'services/api/medical-services.service';

// ═══════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════

const LABELS = {
  placeholder: 'ابحث برمز الخدمة أو اسمها أو التصنيف...',
  noOptions: 'لا توجد خدمات مطابقة',
  loading: 'جاري البحث...',
  categoryLabel: 'التصنيف',
  uncategorized: 'غير مصنف'
};

// ═══════════════════════════════════════════════════════════════════════════
// COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const MedicalServiceSelector = ({
  value = null,
  onChange,
  categoryId = null,
  excludeIds = [],
  disabled = false,
  required = false,
  error = false,
  helperText = '',
  size = 'medium',
  fullWidth = true,
  label = 'الخدمة الطبية',
  placeholder = LABELS.placeholder
}) => {
  // ─────────────────────────────────────────────────────────────────────────
  // STATE
  // ─────────────────────────────────────────────────────────────────────────

  const [inputValue, setInputValue] = useState('');
  const [selectedService, setSelectedService] = useState(null);

  // ─────────────────────────────────────────────────────────────────────────
  // DATA FETCHING
  // ─────────────────────────────────────────────────────────────────────────

  const {
    data: services = [],
    isLoading,
    isFetching
  } = useQuery({
    queryKey: ['medical-services-lookup', inputValue, categoryId],
    queryFn: () => lookupMedicalServices({ q: inputValue || '', categoryId }),
    enabled: true,
    staleTime: 30000, // 30 seconds
    gcTime: 5 * 60 * 1000, // 5 minutes
    placeholderData: (prev) => prev
  });

  // ─────────────────────────────────────────────────────────────────────────
  // SYNC VALUE PROP WITH LOCAL STATE
  // ─────────────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (value && typeof value === 'object') {
      setSelectedService(value);
    } else if (value && typeof value === 'number') {
      // If only ID is passed, find the service in the list
      const found = services.find((s) => s.id === value);
      if (found) {
        setSelectedService(found);
      }
    } else {
      setSelectedService(null);
    }
  }, [value, services]);

  // ─────────────────────────────────────────────────────────────────────────
  // FILTERED OPTIONS (exclude already selected)
  // ─────────────────────────────────────────────────────────────────────────

  const filteredOptions = useMemo(() => {
    if (!services || !Array.isArray(services)) return [];
    return services.filter((s) => !excludeIds.includes(s.id));
  }, [services, excludeIds]);

  // ─────────────────────────────────────────────────────────────────────────
  // HANDLERS
  // ─────────────────────────────────────────────────────────────────────────

  const handleChange = useCallback(
    (event, newValue) => {
      setSelectedService(newValue);
      if (onChange) {
        // Return full service object for display, but parent should store ID only
        onChange(newValue);
      }
    },
    [onChange]
  );

  const handleInputChange = useCallback((event, newInputValue) => {
    setInputValue(newInputValue);
  }, []);

  // ─────────────────────────────────────────────────────────────────────────
  // OPTION LABEL
  // ─────────────────────────────────────────────────────────────────────────

  const getOptionLabel = useCallback((option) => {
    if (!option) return '';
    if (option.getDisplayLabel) return option.getDisplayLabel(); // In case DTO methods were somehow available (unlikely in JS)

    let label = `[${option.code}] `;
    if (option.nameAr) label += option.nameAr;
    else if (option.name) label += option.name;

    if (option.nameEn) label += ` ${option.nameEn}`;
    return label;
  }, []);

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER OPTION
  // ─────────────────────────────────────────────────────────────────────────

  const renderOption = useCallback(
    (props, option, { selected }) => {
      const { key, ...restProps } = props;
      const categoryName = option.categoryName || LABELS.uncategorized;
      const isExcluded = excludeIds.includes(option.id);

      return (
        <Box
          component="li"
          key={key}
          {...restProps}
          sx={{
            opacity: isExcluded ? 0.4 : 1,
            pointerEvents: isExcluded ? 'none' : 'auto',
            py: '0.75rem',
            px: '1.0rem',
            '&:hover': {
              bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08)
            }
          }}
        >
          <Stack direction="row" alignItems="flex-start" spacing={2} width="100%">
            {/* Service Icon */}
            <Box
              sx={{
                p: 1,
                borderRadius: 1,
                bgcolor: (theme) => alpha(theme.palette.warning.main, 0.1),
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <MedicalIcon sx={{ fontSize: '1.5rem', color: 'warning.main' }} />
            </Box>

            {/* Service Info */}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              {/* Code + Name */}
              <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                <Chip
                  label={option.code}
                  size="small"
                  color="primary"
                  variant="outlined"
                  sx={{
                    fontWeight: 700,
                    fontFamily: 'monospace',
                    fontSize: '0.75rem'
                  }}
                />
                <Typography variant="body1" fontWeight={600} noWrap sx={{ maxWidth: '25.0rem' }}>
                  {option.nameAr || option.name}
                  {option.nameEn && (
                    <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1, fontWeight: 400 }}>
                      {option.nameEn}
                    </Typography>
                  )}
                </Typography>
              </Stack>

              {/* Category Label */}
              <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 0.5 }}>
                <CategoryIcon sx={{ fontSize: '0.875rem', color: 'text.secondary' }} />
                <Typography variant="caption" color="text.secondary">
                  {LABELS.categoryLabel}: {option.categoryNameAr || option.categoryName || LABELS.uncategorized}
                  {option.categoryNameEn && ` (${option.categoryNameEn})`}
                </Typography>
              </Stack>
            </Box>

            {/* Selected Indicator */}
            {selected && <CheckIcon sx={{ color: 'success.main', fontSize: '1.25rem' }} />}
          </Stack>
        </Box>
      );
    },
    [excludeIds]
  );

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER INPUT
  // ─────────────────────────────────────────────────────────────────────────

  const renderInput = useCallback(
    (params) => (
      <TextField
        {...params}
        label={label}
        placeholder={placeholder}
        required={required}
        error={error}
        size={size}
        InputProps={{
          ...params.InputProps,
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon color="action" />
            </InputAdornment>
          ),
          endAdornment: (
            <>
              {(isLoading || isFetching) && inputValue ? <CircularProgress color="inherit" size={20} /> : null}
              {params.InputProps.endAdornment}
            </>
          )
        }}
      />
    ),
    [label, placeholder, required, error, size, isLoading, isFetching, inputValue]
  );

  // ─────────────────────────────────────────────────────────────────────────
  // CUSTOM PAPER COMPONENT
  // ─────────────────────────────────────────────────────────────────────────

  const CustomPaper = useCallback(
    (props) => (
      <Paper
        {...props}
        elevation={8}
        sx={{
          mt: 1,
          borderRadius: '0.125rem',
          border: '1px solid',
          borderColor: 'divider'
        }}
      />
    ),
    []
  );

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────────────────

  return (
    <FormControl fullWidth={fullWidth} error={error}>
      <Autocomplete
        value={selectedService}
        onChange={handleChange}
        inputValue={inputValue}
        onInputChange={handleInputChange}
        options={filteredOptions}
        getOptionLabel={getOptionLabel}
        renderOption={renderOption}
        renderInput={renderInput}
        PaperComponent={CustomPaper}
        isOptionEqualToValue={(option, val) => option?.id === val?.id}
        loading={isLoading || isFetching}
        loadingText={LABELS.loading}
        noOptionsText={LABELS.noOptions}
        disabled={disabled}
        clearOnEscape
        handleHomeEndKeys
        selectOnFocus
        blurOnSelect
        autoHighlight
        filterOptions={(x) => x} // Disable client-side filtering - server handles it
        sx={{
          '& .MuiAutocomplete-inputRoot': {
            paddingLeft: 1
          }
        }}
      />
      {helperText && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
// PROP TYPES
// ═══════════════════════════════════════════════════════════════════════════

MedicalServiceSelector.propTypes = {
  /** Selected service object or ID */
  value: PropTypes.oneOfType([PropTypes.object, PropTypes.number]),
  /** Callback when selection changes - receives full service object */
  onChange: PropTypes.func.isRequired,
  /** Filter services by category ID */
  categoryId: PropTypes.number,
  /** Array of service IDs to exclude from options */
  excludeIds: PropTypes.arrayOf(PropTypes.number),
  /** Disable the selector */
  disabled: PropTypes.bool,
  /** Mark as required field */
  required: PropTypes.bool,
  /** Show error state */
  error: PropTypes.bool,
  /** Helper text below input */
  helperText: PropTypes.string,
  /** Input size: 'small' | 'medium' */
  size: PropTypes.oneOf(['small', 'medium']),
  /** Full width */
  fullWidth: PropTypes.bool,
  /** Input label */
  label: PropTypes.string,
  /** Placeholder text */
  placeholder: PropTypes.string
};

export default MedicalServiceSelector;


