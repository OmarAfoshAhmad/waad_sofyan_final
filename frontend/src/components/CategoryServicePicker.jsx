/**
 * CategoryServicePicker - مكون اختيار التصنيف والخدمة الطبية (معياري)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL LAW (قانون معماري):
 * لا يمكن اختيار خدمة طبية بدون اختيار التصنيف الطبي أولاً
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Flow:
 * 1. Select Medical Category (REQUIRED FIRST)
 * 2. Select Medical Service (filtered by category)
 * 3. Display Coverage Result (read-only)
 * 4. Display PA Requirement (read-only)
 *
 * ❌ Service selector DISABLED until category selected
 * ❌ Service RESET if category changes
 * ❌ Form BLOCKED if category missing
 */

import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Stack,
  TextField,
  Autocomplete,
  Chip,
  Typography,
  Avatar,
  CircularProgress,
  InputAdornment,
  Paper,
  Divider,
  Alert,
  Collapse
} from '@mui/material';
import {
  MedicalServices as MedicalIcon,
  Category as CategoryIcon,
  Lock as LockIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Info as InfoIcon
} from '@mui/icons-material';
import axiosClient from 'utils/axios';

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

const LABELS = {
  selectCategory: 'اختر التصنيف الطبي أولاً',
  selectService: 'اختر الخدمة الطبية',
  categoryRequired: 'يجب اختيار التصنيف قبل الخدمة',
  loadingCategories: 'جاري تحميل التصنيفات...',
  loadingServices: 'جاري تحميل الخدمات...',
  noCategories: 'لا توجد تصنيفات متاحة',
  noServices: 'لا توجد خدمات في هذا التصنيف',
  covered: 'مغطاة',
  notCovered: 'غير مغطاة',
  coverage: 'نسبة التغطية',
  patientShare: 'حصة المريض',
  requiresPA: 'تتطلب موافقة مسبقة',
  noPA: 'لا تتطلب موافقة مسبقة',
  selectCategoryFirst: '⚠️ يجب اختيار التصنيف الطبي أولاً',
  categoryServiceInfo: 'اختر التصنيف الطبي ثم الخدمة لحساب التغطية بشكل صحيح'
};

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

const CategoryServicePicker = ({
  // Required props
  onSelect, // Callback when service is selected: (service, category) => void
  memberId, // Member ID for coverage calculation
  providerId, // Provider ID for contract pricing

  // Optional state props
  selectedCategoryId = null, // Pre-selected category
  selectedServiceId = null, // Pre-selected service

  // Optional UI props
  disabled = false,
  size = 'small',
  showCoverageInfo = true, // Show coverage/PA info after selection
  showPrices = true, // Show contract prices

  // Callbacks
  onCategoryChange, // Optional callback when category changes
  onCoverageResolved, // Optional callback when coverage is calculated

  // Error handling
  error = false,
  helperText = ''
}) => {
  // ═══════════════════════════════════════════════════════════════════════════
  // STATE
  // ═══════════════════════════════════════════════════════════════════════════

  // Categories state
  const [categories, setCategories] = useState([]);
  const [loadingCategories, setLoadingCategories] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState(null);

  // Services state
  const [services, setServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(false);
  const [selectedService, setSelectedService] = useState(null);

  // Coverage state
  const [coverageInfo, setCoverageInfo] = useState(null);
  const [loadingCoverage, setLoadingCoverage] = useState(false);

  // Error state
  const [fetchError, setFetchError] = useState(null);

  // ═══════════════════════════════════════════════════════════════════════════
  // FETCH CATEGORIES ON MOUNT
  // ═══════════════════════════════════════════════════════════════════════════

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        setLoadingCategories(true);
        setFetchError(null);

        const response = await axiosClient.get('/medical-categories/all');
        const data = response.data?.data || response.data || [];

        setCategories(data);

        // If pre-selected category, set it
        if (selectedCategoryId) {
          const preSelected = data.find((c) => c.id === selectedCategoryId);
          if (preSelected) {
            setSelectedCategory(preSelected);
          }
        }
      } catch (err) {
        console.error('[CategoryServicePicker] Failed to fetch categories:', err);
        setFetchError('فشل في تحميل التصنيفات');
      } finally {
        setLoadingCategories(false);
      }
    };

    fetchCategories();
  }, [selectedCategoryId]);

  // ═══════════════════════════════════════════════════════════════════════════
  // FETCH SERVICES WHEN CATEGORY CHANGES
  // ═══════════════════════════════════════════════════════════════════════════

  useEffect(() => {
    if (!selectedCategory) {
      setServices([]);
      setSelectedService(null);
      setCoverageInfo(null);
      return;
    }

    const fetchServices = async () => {
      try {
        setLoadingServices(true);
        setFetchError(null);

        // CANONICAL ENDPOINT: Get services filtered by category
        const response = await axiosClient.get(`/medical-categories/${selectedCategory.id}/medical-services`);
        const data = response.data?.data || response.data || [];

        setServices(data);

        // If pre-selected service, set it (only if it belongs to this category)
        if (selectedServiceId) {
          const preSelected = data.find((s) => s.id === selectedServiceId);
          if (preSelected) {
            setSelectedService(preSelected);
          }
        }
      } catch (err) {
        console.error('[CategoryServicePicker] Failed to fetch services:', err);
        setFetchError('فشل في تحميل الخدمات');
      } finally {
        setLoadingServices(false);
      }
    };

    fetchServices();
  }, [selectedCategory, selectedServiceId]);

  // ═══════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════

  const handleCategoryChange = useCallback(
    (event, newValue) => {
      setSelectedCategory(newValue);

      // ARCHITECTURAL LAW: Reset service when category changes
      setSelectedService(null);
      setCoverageInfo(null);

      // Notify parent
      if (onCategoryChange) {
        onCategoryChange(newValue);
      }

      // Clear selection in parent
      if (onSelect) {
        onSelect(null, newValue);
      }
    },
    [onCategoryChange, onSelect]
  );

  const handleServiceChange = useCallback(
    (event, newValue) => {
      setSelectedService(newValue);

      // Notify parent with both service and category
      if (onSelect && newValue) {
        onSelect(
          {
            ...newValue,
            categoryId: selectedCategory?.id,
            categoryName: selectedCategory?.name,
            categoryCode: selectedCategory?.code
          },
          selectedCategory
        );
      }

      // Fetch coverage info if memberId is provided
      if (memberId && newValue) {
        setLoadingCoverage(true);
        axiosClient
          .post('/eligibility/check', { memberId, serviceDate: new Date().toISOString().slice(0, 10) })
          .then((res) => {
            const coverage = res.data?.data || res.data || null;
            setCoverageInfo(coverage);
            if (onCoverageResolved) onCoverageResolved(coverage, newValue);
          })
          .catch(() => setCoverageInfo(null))
          .finally(() => setLoadingCoverage(false));
      } else {
        setCoverageInfo(null);
      }
    },
    [memberId, onCoverageResolved, onSelect, selectedCategory]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  const renderCategoryOption = (props, option) => (
    <Box component="li" {...props}>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ width: '100%' }}>
        <Avatar sx={{ bgcolor: 'primary.lighter', width: '2.25rem', height: '2.25rem' }}>
          <CategoryIcon sx={{ fontSize: '1.125rem', color: 'primary.main' }} />
        </Avatar>
        <Box sx={{ flex: 1 }}>
          <Typography variant="body2" fontWeight="medium">
            {option.name}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {option.code}
          </Typography>
        </Box>
      </Stack>
    </Box>
  );

  const renderServiceOption = (props, option) => (
    <Box component="li" {...props}>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ width: '100%' }}>
        <Avatar sx={{ bgcolor: 'warning.lighter', width: '2.25rem', height: '2.25rem' }}>
          <MedicalIcon sx={{ fontSize: '1.125rem', color: 'warning.main' }} />
        </Avatar>
        <Box sx={{ flex: 1 }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <Chip
              label={option.code}
              size="small"
              color="primary"
              variant="outlined"
              sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}
            />
          </Stack>
          <Typography variant="body2" fontWeight="medium" sx={{ mt: 0.5 }}>
            {option.name}
          </Typography>
        </Box>
        {showPrices && option.basePrice && (
          <Chip
            label={`${Number(option.basePrice).toLocaleString()} د.ل`}
            size="small"
            color="success"
            icon={<LockIcon sx={{ fontSize: '0.75rem' }} />}
          />
        )}
      </Stack>
    </Box>
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // MAIN RENDER
  // ═══════════════════════════════════════════════════════════════════════════

  return (
    <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'grey.50', borderRadius: '0.125rem' }}>
      <Stack spacing={2}>
        {/* Info Alert */}
        <Alert severity="info" icon={<InfoIcon />} sx={{ py: 0.5 }}>
          <Typography variant="caption">{LABELS.categoryServiceInfo}</Typography>
        </Alert>

        {/* Error Display */}
        {fetchError && <Alert severity="error">{fetchError}</Alert>}

        {/* STEP 1: Category Selection (REQUIRED FIRST) */}
        <Box>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
            <Chip
              label="1"
              size="small"
              color={selectedCategory ? 'success' : 'primary'}
              sx={{ width: '1.5rem', height: '1.5rem', fontWeight: 'bold' }}
            />
            <Typography variant="subtitle2" fontWeight="bold" color="primary.main">
              {LABELS.selectCategory}
            </Typography>
            {selectedCategory && <CheckIcon color="success" fontSize="small" />}
          </Stack>

          <Autocomplete
            options={categories}
            getOptionLabel={(option) => option?.name || ''}
            value={selectedCategory}
            onChange={handleCategoryChange}
            loading={loadingCategories}
            disabled={disabled || loadingCategories}
            isOptionEqualToValue={(option, value) => option?.id === value?.id}
            renderOption={renderCategoryOption}
            renderInput={(params) => (
              <TextField
                {...params}
                placeholder="ابحث عن التصنيف الطبي..."
                size={size}
                error={error && !selectedCategory}
                InputProps={{
                  ...params.InputProps,
                  startAdornment: (
                    <InputAdornment position="start">
                      <CategoryIcon color="primary" />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <>
                      {loadingCategories ? <CircularProgress color="inherit" size={20} /> : null}
                      {params.InputProps.endAdornment}
                    </>
                  )
                }}
              />
            )}
            noOptionsText={LABELS.noCategories}
            loadingText={LABELS.loadingCategories}
          />
        </Box>

        <Divider />

        {/* STEP 2: Service Selection (DISABLED until category selected) */}
        <Box>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
            <Chip
              label="2"
              size="small"
              color={selectedService ? 'success' : selectedCategory ? 'primary' : 'default'}
              sx={{ width: '1.5rem', height: '1.5rem', fontWeight: 'bold' }}
            />
            <Typography variant="subtitle2" fontWeight="bold" color={selectedCategory ? 'primary.main' : 'text.disabled'}>
              {LABELS.selectService}
            </Typography>
            {selectedService && <CheckIcon color="success" fontSize="small" />}
          </Stack>

          {/* Warning when no category selected */}
          <Collapse in={!selectedCategory}>
            <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 1, py: 0.5 }}>
              <Typography variant="caption">{LABELS.selectCategoryFirst}</Typography>
            </Alert>
          </Collapse>

          <Autocomplete
            options={services}
            getOptionLabel={(option) => (option ? `${option.code} - ${option.name}` : '')}
            value={selectedService}
            onChange={handleServiceChange}
            loading={loadingServices}
            disabled={disabled || !selectedCategory || loadingServices}
            isOptionEqualToValue={(option, value) => option?.id === value?.id}
            renderOption={renderServiceOption}
            renderInput={(params) => (
              <TextField
                {...params}
                placeholder={selectedCategory ? 'ابحث عن الخدمة الطبية...' : LABELS.categoryRequired}
                size={size}
                error={error && selectedCategory && !selectedService}
                helperText={helperText}
                InputProps={{
                  ...params.InputProps,
                  startAdornment: (
                    <InputAdornment position="start">
                      <MedicalIcon color={selectedCategory ? 'warning' : 'disabled'} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <>
                      {loadingServices ? <CircularProgress color="inherit" size={20} /> : null}
                      {params.InputProps.endAdornment}
                    </>
                  )
                }}
              />
            )}
            noOptionsText={LABELS.noServices}
            loadingText={LABELS.loadingServices}
          />

          {/* Service count info */}
          {selectedCategory && services.length > 0 && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              {services.length} خدمة متاحة في تصنيف "{selectedCategory.name}"
            </Typography>
          )}
        </Box>

        {/* STEP 3: Coverage Info Display (if service selected) */}
        {showCoverageInfo && selectedService && (
          <>
            <Divider />
            <Box sx={{ bgcolor: 'success.lighter', p: '0.75rem', borderRadius: 1 }}>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                <Chip
                  icon={<CategoryIcon />}
                  label={`التصنيف: ${selectedCategory?.name}`}
                  color="primary"
                  variant="outlined"
                  size="small"
                />
                <Chip icon={<MedicalIcon />} label={`الخدمة: ${selectedService?.code}`} color="warning" variant="filled" size="small" />
                {loadingCoverage && <Chip label="جاري التحقق من التغطية..." size="small" />}
                {!loadingCoverage && coverageInfo?.covered && (
                  <Chip icon={<CheckIcon />} label={`${LABELS.coverage}: ${coverageInfo.coveragePercent}%`} color="success" size="small" />
                )}
                {!loadingCoverage && coverageInfo?.requiresPA && <Chip icon={<WarningIcon />} label={LABELS.requiresPA} color="warning" size="small" />}
              </Stack>
            </Box>
          </>
        )}
      </Stack>
    </Paper>
  );
};

// ═══════════════════════════════════════════════════════════════════════════════
// PROP TYPES
// ═══════════════════════════════════════════════════════════════════════════════

CategoryServicePicker.propTypes = {
  onSelect: PropTypes.func.isRequired,
  memberId: PropTypes.number,
  providerId: PropTypes.number,
  selectedCategoryId: PropTypes.number,
  selectedServiceId: PropTypes.number,
  disabled: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'medium']),
  showCoverageInfo: PropTypes.bool,
  showPrices: PropTypes.bool,
  onCategoryChange: PropTypes.func,
  onCoverageResolved: PropTypes.func,
  error: PropTypes.bool,
  helperText: PropTypes.string
};

export default CategoryServicePicker;


