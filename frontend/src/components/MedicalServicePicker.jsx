/**
 * MedicalServicePicker - مكون متقدم لاختيار الخدمات الطبية
 *
 * المميزات:
 * ✅ البحث بالكود أو الاسم
 * ✅ الفلترة بالتصنيف
 * ✅ عرض سعر العقد
 * ✅ قابل لإعادة الاستخدام في المطالبات والموافقات
 */

import { useState, useMemo } from 'react';
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
  Divider
} from '@mui/material';
import {
  MedicalServices as MedicalIcon,
  Search as SearchIcon,
  Category as CategoryIcon,
  FilterList as FilterIcon
} from '@mui/icons-material';

/**
 * Props:
 * @param {Array} services - قائمة الخدمات الطبية
 * @param {boolean} loading - حالة التحميل
 * @param {Function} onSelect - عند اختيار خدمة
 * @param {Array} selectedIds - قائمة معرفات الخدمات المختارة مسبقاً
 * @param {string} placeholder - نص placeholder
 * @param {boolean} showCategoryFilter - إظهار فلتر التصنيف
 * @param {boolean} showPrices - إظهار الأسعار
 * @param {string} size - حجم المكون ('small' | 'medium')
 */
const MedicalServicePicker = ({
  services = [],
  loading = false,
  onSelect,
  selectedIds = [],
  placeholder = 'ابحث برمز الخدمة أو اسمها...',
  showCategoryFilter = true,
  showPrices = true,
  size = 'small',
  error = false,
  helperText = ''
}) => {
  // ========================= STATE =========================
  const [searchValue, setSearchValue] = useState(null);
  const [categoryFilter, setCategoryFilter] = useState('');

  // ========================= EXTRACT UNIQUE CATEGORIES =========================
  const categories = useMemo(() => {
    const uniqueCategories = new Set();
    services.forEach((service) => {
      if (service.category || service.categoryName) {
        uniqueCategories.add(service.category || service.categoryName);
      }
    });
    return Array.from(uniqueCategories).sort();
  }, [services]);

  // ========================= FILTERED SERVICES =========================
  const filteredServices = useMemo(() => {
    if (!categoryFilter) return services;
    return services.filter((s) => (s.category || s.categoryName) === categoryFilter);
  }, [services, categoryFilter]);

  // ========================= HANDLERS =========================
  const handleSelect = (event, newValue) => {
    if (newValue && onSelect) {
      onSelect(newValue);
      setSearchValue(null);
    }
  };

  const handleCategoryChange = (event) => {
    setCategoryFilter(event.target.value);
  };

  const clearCategory = () => {
    setCategoryFilter('');
  };

  // ========================= RENDER OPTION =========================
  const renderOption = (props, option) => {
    const isSelected = selectedIds.includes(option.id);
    const categoryLabel = option.category || option.categoryName || 'غير مصنف';

    return (
      <Box
        component="li"
        {...props}
        sx={{
          opacity: isSelected ? 0.5 : 1,
          '&:hover': { bgcolor: 'action.hover' }
        }}
      >
        <Stack direction="row" alignItems="center" spacing={2} sx={{ width: '100%', py: 0.5 }}>
          {/* Service Icon */}
          <Avatar sx={{ bgcolor: 'warning.lighter', width: '2.5rem', height: '2.5rem' }}>
            <MedicalIcon sx={{ fontSize: '1.25rem', color: 'warning.main' }} />
          </Avatar>

          {/* Service Info */}
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              {/* Code Chip */}
              <Chip
                label={option.code || option.serviceCode || 'N/A'}
                size="small"
                color="primary"
                variant="outlined"
                sx={{ fontWeight: 'bold', fontFamily: 'monospace' }}
              />
              {/* Category Badge */}
              <Chip
                label={categoryLabel}
                size="small"
                variant="filled"
                sx={{
                  bgcolor: 'grey.100',
                  color: 'text.secondary',
                  fontSize: '0.7rem'
                }}
              />
            </Stack>
            <Typography variant="body2" fontWeight="medium" sx={{ mt: 0.5 }}>
              {option.name || option.serviceName}
            </Typography>
          </Box>

          {/* Price Badge */}
          {showPrices && (
            <Chip
              label={option.price ? `${Number(option.price).toLocaleString()} د.ل` : 'سعر العقد'}
              size="small"
              color={option.price ? 'success' : 'default'}
              variant={option.price ? 'filled' : 'outlined'}
            />
          )}

          {/* Already Selected Badge */}
          {isSelected && <Chip label="مضافة" size="small" color="info" />}
        </Stack>
      </Box>
    );
  };

  // ========================= FILTER OPTIONS =========================
  const filterOptions = (options, state) => {
    const input = state.inputValue.toLowerCase().trim();
    if (!input) return options;

    return options.filter((opt) => {
      const code = (opt.code || opt.serviceCode || '').toLowerCase();
      const name = (opt.name || opt.serviceName || '').toLowerCase();

      return code.includes(input) || code.startsWith(input) || name.includes(input);
    });
  };

  // ========================= RENDER =========================
  return (
    <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'grey.50', borderRadius: '0.25rem', mb: '1.0rem' }}>
      <Stack spacing={2}>
        {/* Category Filter Row */}
        {showCategoryFilter && categories.length > 0 && (
          <Stack direction="row" spacing={2} alignItems="center">
            <FilterIcon color="action" />
            <FormControl size={size} sx={{ minWidth: '12.5rem' }}>
              <InputLabel>فلترة بالتصنيف</InputLabel>
              <Select value={categoryFilter} onChange={handleCategoryChange} label="فلترة بالتصنيف">
                <MenuItem value="">
                  <em>كل التصنيفات ({services.length})</em>
                </MenuItem>
                <Divider />
                {categories.map((cat) => {
                  const count = services.filter((s) => (s.category || s.categoryName) === cat).length;
                  return (
                    <MenuItem key={cat} value={cat}>
                      <Stack direction="row" justifyContent="space-between" sx={{ width: '100%' }}>
                        <span>{cat}</span>
                        <Chip label={count} size="small" sx={{ ml: 1 }} />
                      </Stack>
                    </MenuItem>
                  );
                })}
              </Select>
            </FormControl>

            {categoryFilter && (
              <Chip
                label={`التصنيف: ${categoryFilter}`}
                onDelete={clearCategory}
                color="primary"
                variant="outlined"
                icon={<CategoryIcon />}
              />
            )}

            <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
              {filteredServices.length} خدمة متاحة
            </Typography>
          </Stack>
        )}

        {/* Service Search Autocomplete */}
        <Autocomplete
          options={filteredServices}
          getOptionLabel={(option) => (option ? `${option.code || option.serviceCode} - ${option.name}` : '')}
          value={searchValue}
          onChange={handleSelect}
          loading={loading}
          disabled={loading}
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
                    {loading ? <CircularProgress color="inherit" size={20} /> : null}
                    {params.InputProps.endAdornment}
                  </>
                )
              }}
            />
          )}
          noOptionsText={categoryFilter ? `لا توجد خدمات في تصنيف "${categoryFilter}"` : 'لا توجد خدمات مطابقة'}
          loadingText="جاري البحث..."
          groupBy={showCategoryFilter && !categoryFilter ? (option) => option.category || option.categoryName || 'غير مصنف' : undefined}
          sx={{
            '& .MuiAutocomplete-groupLabel': {
              bgcolor: 'primary.lighter',
              color: 'primary.dark',
              fontWeight: 'bold'
            }
          }}
        />

        {/* Quick Search Tips */}
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Typography variant="caption" color="text.secondary">
            💡 نصائح البحث:
          </Typography>
          <Chip label="ابحث بالكود مباشرة" size="small" variant="outlined" sx={{ fontSize: '0.75rem' }} />
          <Chip label="أو اكتب جزء من الاسم" size="small" variant="outlined" sx={{ fontSize: '0.75rem' }} />
          <Chip label="أو فلتر بالتصنيف أولاً" size="small" variant="outlined" sx={{ fontSize: '0.75rem' }} />
        </Stack>
      </Stack>
    </Paper>
  );
};

export default MedicalServicePicker;

