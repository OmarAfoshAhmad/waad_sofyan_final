/**
 * SmartServicePicker - مكون ذكي لاختيار الخدمات الطبية في بوابة مقدم الخدمة
 *
 * 🚀 المميزات:
 * ✅ تحميل تلقائي من العقد النشط فقط
 * ✅ بحث سريع بالكود أو الاسم
 * ✅ فلترة بالتصنيف
 * ✅ عرض السعر التعاقدي
 * ✅ دعم Multi-Select
 * ✅ يرجع medicalServiceId الصحيح (ليس pricing item id)
 *
 * @author WAAD TPA System 2026
 */

import { useState, useMemo, useCallback, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Stack,
  TextField,
  Autocomplete,
  Chip,
  Typography,
  Avatar,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  InputAdornment,
  Paper,
  Divider,
  Alert,
  Tooltip
} from '@mui/material';
import {
  MedicalServices as MedicalIcon,
  Search as SearchIcon,
  Category as CategoryIcon,
  FilterList as FilterIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  LocalOffer as PriceIcon
} from '@mui/icons-material';
import axiosClient from 'utils/axios';

// ════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ════════════════════════════════════════════════════════════════════════════
const PROVIDER_PORTAL_URL = '/provider';

// ════════════════════════════════════════════════════════════════════════════
// COMPONENT
// ════════════════════════════════════════════════════════════════════════════
const SmartServicePicker = ({
  onSelect,
  selectedIds = [],
  placeholder = 'ابحث برمز الخدمة أو اسمها...',
  showCategoryFilter = true,
  showPrices = true,
  size = 'small',
  error = false,
  helperText = '',
  disabled = false,
  multiSelect = false
}) => {
  // ════════════════════════════════════════════════════════════════════════════
  // STATE
  // ════════════════════════════════════════════════════════════════════════════
  const [searchValue, setSearchValue] = useState(null);
  const [categoryFilter, setCategoryFilter] = useState('');

  // ════════════════════════════════════════════════════════════════════════════
  // DATA FETCHING - Direct from Provider Portal API
  // ════════════════════════════════════════════════════════════════════════════
  const {
    data: servicesData,
    isLoading,
    isError,
    error: fetchError
  } = useQuery({
    queryKey: ['provider-contract-services-smart'],
    queryFn: async () => {
      // Call Provider Portal endpoint directly
      const response = await axiosClient.get(`${PROVIDER_PORTAL_URL}/my-contract/services`, {
        params: { page: 0, size: 500 }
      });

      const data = response.data?.data || response.data;
      const items = data?.content || data?.items || data || [];

      // Map to standardized format with CORRECT medicalServiceId
      return items.map((item) => {
        // CRITICAL: Use medicalServiceId, NOT the pricing item id
        const serviceId = item.medicalServiceId || item.id;

        return {
          id: serviceId, // This is MedicalService ID
          pricingItemId: item.id, // Keep pricing item id for reference
          code: item.serviceCode || '',
          name: item.serviceName || '',
          category: item.categoryName || '',
          price: item.contractPrice,
          currency: item.currency || 'LYD',
          hasContract: item.hasContract !== false,
          effectiveFrom: item.effectiveFrom,
          effectiveTo: item.effectiveTo
        };
      });
    },
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
    refetchOnWindowFocus: false
  });

  const services = servicesData || [];

  // ════════════════════════════════════════════════════════════════════════════
  // EXTRACT UNIQUE CATEGORIES
  // ════════════════════════════════════════════════════════════════════════════
  const categories = useMemo(() => {
    const uniqueCategories = new Set();
    services.forEach((service) => {
      if (service.category) {
        uniqueCategories.add(service.category);
      }
    });
    return Array.from(uniqueCategories).sort();
  }, [services]);

  // ════════════════════════════════════════════════════════════════════════════
  // FILTERED SERVICES
  // ════════════════════════════════════════════════════════════════════════════
  const filteredServices = useMemo(() => {
    if (!categoryFilter) return services;
    return services.filter((s) => s.category === categoryFilter);
  }, [services, categoryFilter]);

  // ════════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ════════════════════════════════════════════════════════════════════════════
  const handleSelect = useCallback(
    (event, newValue) => {
      if (newValue && onSelect) {
        // Ensure we pass the service with medicalServiceId
        console.log('📤 SmartServicePicker selected:', {
          id: newValue.id,
          code: newValue.code,
          name: newValue.name
        });
        onSelect(event, newValue);
        if (!multiSelect) {
          setSearchValue(null);
        }
      }
    },
    [onSelect, multiSelect]
  );

  const handleCategoryChange = useCallback((event) => {
    setCategoryFilter(event.target.value);
  }, []);

  const clearCategory = useCallback(() => {
    setCategoryFilter('');
  }, []);

  // ════════════════════════════════════════════════════════════════════════════
  // RENDER OPTION
  // ════════════════════════════════════════════════════════════════════════════
  const renderOption = useCallback(
    (props, option) => {
      const isSelected = selectedIds.includes(option.id);

      return (
        <Box
          component="li"
          {...props}
          sx={{
            opacity: isSelected ? 0.5 : 1,
            '&:hover': { bgcolor: 'action.hover' },
            py: 1
          }}
        >
          <Stack direction="row" alignItems="center" spacing={2} sx={{ width: '100%' }}>
            {/* Service Icon */}
            <Avatar
              sx={{
                bgcolor: option.hasContract ? 'success.lighter' : 'warning.lighter',
                width: '2.25rem',
                height: '2.25rem'
              }}
            >
              <MedicalIcon sx={{ fontSize: '1.125rem', color: option.hasContract ? 'success.main' : 'warning.main' }} />
            </Avatar>

            {/* Service Info */}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                {/* Code Chip */}
                <Chip
                  label={option.code}
                  size="small"
                  color="primary"
                  variant="outlined"
                  sx={{ fontWeight: 'bold', fontFamily: 'monospace', fontSize: '0.75rem' }}
                />
                {/* Category Badge */}
                {option.category && (
                  <Chip
                    label={option.category}
                    size="small"
                    variant="filled"
                    sx={{
                      bgcolor: 'grey.100',
                      color: 'text.secondary',
                      fontSize: '0.75rem'
                    }}
                  />
                )}
                {/* Contract Status */}
                {option.hasContract ? (
                  <Tooltip title="لها سعر تعاقدي">
                    <CheckIcon sx={{ fontSize: '1.0rem', color: 'success.main' }} />
                  </Tooltip>
                ) : (
                  <Tooltip title="بدون عقد">
                    <WarningIcon sx={{ fontSize: '1.0rem', color: 'warning.main' }} />
                  </Tooltip>
                )}
              </Stack>
              <Typography variant="body2" fontWeight="medium" sx={{ mt: 0.5 }} noWrap>
                {option.name}
              </Typography>
            </Box>

            {/* Price Badge */}
            {showPrices && (
              <Chip
                icon={<PriceIcon sx={{ fontSize: '0.875rem' }} />}
                label={option.price ? `${Number(option.price).toLocaleString('en-US')} ${option.currency || 'د.ل'}` : '—'}
                size="small"
                color={option.price ? 'success' : 'default'}
                variant={option.price ? 'filled' : 'outlined'}
              />
            )}

            {/* Already Selected Badge */}
            {isSelected && <Chip label="✓ مضافة" size="small" color="info" variant="filled" />}
          </Stack>
        </Box>
      );
    },
    [selectedIds, showPrices]
  );

  // ════════════════════════════════════════════════════════════════════════════
  // FILTER OPTIONS (Local Fast Search)
  // ════════════════════════════════════════════════════════════════════════════
  const filterOptions = useCallback((options, state) => {
    const input = state.inputValue.toLowerCase().trim();
    if (!input) return options;

    return options.filter((opt) => {
      const code = (opt.code || '').toLowerCase();
      const name = (opt.name || '').toLowerCase();

      return code.includes(input) || code.startsWith(input) || name.includes(input);
    });
  }, []);

  // ════════════════════════════════════════════════════════════════════════════
  // ERROR STATE
  // ════════════════════════════════════════════════════════════════════════════
  if (isError) {
    return (
      <Alert severity="error" sx={{ mb: '1.0rem' }}>
        فشل في تحميل الخدمات: {fetchError?.message || 'خطأ غير معروف'}
      </Alert>
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  // RENDER
  // ════════════════════════════════════════════════════════════════════════════
  return (
    <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'grey.50', borderRadius: '0.25rem', mb: '1.0rem' }}>
      <Stack spacing={2}>
        {/* Status Row */}
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="subtitle2" color="primary.main">
            🏥 خدمات العقد النشط
          </Typography>
          {isLoading ? (
            <CircularProgress size={16} />
          ) : (
            <Chip label={`${services.length} خدمة متاحة`} size="small" color="success" variant="outlined" />
          )}
        </Stack>

        {/* Category Filter Row */}
        {showCategoryFilter && categories.length > 0 && (
          <Stack direction="row" spacing={2} alignItems="center">
            <FilterIcon color="action" fontSize="small" />
            <FormControl size={size} sx={{ minWidth: '11.25rem' }}>
              <InputLabel>فلترة بالتصنيف</InputLabel>
              <Select value={categoryFilter} onChange={handleCategoryChange} label="فلترة بالتصنيف">
                <MenuItem value="">
                  <em>كل التصنيفات ({services.length})</em>
                </MenuItem>
                <Divider />
                {categories.map((cat) => {
                  const count = services.filter((s) => s.category === cat).length;
                  return (
                    <MenuItem key={cat} value={cat}>
                      <Stack direction="row" justifyContent="space-between" sx={{ width: '100%' }}>
                        <span>{cat}</span>
                        <Chip label={count} size="small" sx={{ ml: 1, height: '1.25rem', fontSize: '0.7rem' }} />
                      </Stack>
                    </MenuItem>
                  );
                })}
              </Select>
            </FormControl>

            {categoryFilter && (
              <Chip
                label={categoryFilter}
                onDelete={clearCategory}
                color="primary"
                variant="outlined"
                size="small"
                icon={<CategoryIcon />}
              />
            )}

            <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
              {filteredServices.length} خدمة
            </Typography>
          </Stack>
        )}

        {/* Service Search Autocomplete */}
        <Autocomplete
          options={filteredServices}
          getOptionLabel={(option) => (option ? `${option.code} - ${option.name}` : '')}
          value={searchValue}
          onChange={handleSelect}
          loading={isLoading}
          disabled={disabled || isLoading}
          isOptionEqualToValue={(option, value) => option?.id === value?.id}
          filterOptions={filterOptions}
          renderOption={renderOption}
          renderInput={(params) => (
            <TextField
              {...params}
              label="البحث عن خدمة طبية"
              placeholder={placeholder}
              size={size}
              error={error}
              helperText={helperText}
              InputProps={{
                ...params.InputProps,
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: (
                  <>
                    {isLoading ? <CircularProgress color="inherit" size={18} /> : null}
                    {params.InputProps.endAdornment}
                  </>
                )
              }}
            />
          )}
          noOptionsText={
            isLoading
              ? 'جاري التحميل...'
              : categoryFilter
                ? `لا توجد خدمات في تصنيف "${categoryFilter}"`
                : services.length === 0
                  ? 'لا توجد خدمات في العقد النشط'
                  : 'لا توجد خدمات مطابقة للبحث'
          }
          loadingText="جاري تحميل الخدمات..."
          groupBy={showCategoryFilter && !categoryFilter && services.length > 10 ? (option) => option.category || 'غير مصنف' : undefined}
          sx={{
            '& .MuiAutocomplete-groupLabel': {
              bgcolor: 'primary.lighter',
              color: 'primary.dark',
              fontWeight: 'bold',
              fontSize: '0.8rem'
            }
          }}
        />

        {/* Quick Tips */}
        {!isLoading && services.length > 0 && (
          <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
            <Typography variant="caption" color="text.secondary">
              💡
            </Typography>
            <Chip label="ابحث بالكود" size="small" variant="outlined" sx={{ fontSize: '0.75rem', height: '1.25rem' }} />
            <Chip label="أو الاسم" size="small" variant="outlined" sx={{ fontSize: '0.75rem', height: '1.25rem' }} />
            <Chip label="أو فلتر بالتصنيف" size="small" variant="outlined" sx={{ fontSize: '0.75rem', height: '1.25rem' }} />
          </Stack>
        )}
      </Stack>
    </Paper>
  );
};

export default SmartServicePicker;



