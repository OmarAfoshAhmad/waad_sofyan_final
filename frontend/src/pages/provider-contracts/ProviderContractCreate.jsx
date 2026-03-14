import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import { format, addYears, differenceInMonths } from 'date-fns';

// MUI Components
import {
  Box,
  Button,
  Grid,
  TextField,
  MenuItem,
  Typography,
  Paper,
  Alert,
  Divider,
  Chip,
  Autocomplete,
  InputAdornment,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  FormHelperText,
  Stack
} from '@mui/material';
import { ArrowBack, Save, Description, Business, DateRange, AttachMoney, Info, Refresh } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Services
import { createProviderContract } from 'services/api/provider-contracts.service';
import { getProviderSelector } from 'services/api/providers.service';

const PRICING_MODELS = [
  { value: 'DISCOUNT', label: 'نسبة خصم', description: 'خصم نسبة مئوية من السعر الأصلي', icon: '💰' },
  { value: 'FIXED', label: 'سعر ثابت', description: 'أسعار محددة لكل خدمة', icon: '📌' },
  { value: 'TIERED', label: 'تسعير متدرج', description: 'أسعار متدرجة حسب الكمية', icon: '📊' },
  { value: 'NEGOTIATED', label: 'سعر تفاوضي', description: 'أسعار حسب الاتفاق المسبق', icon: '🤝' }
];

/**
 * Provider Contract Create Page - Enhanced Version
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * ✅ Auto-generated contract code (Read-Only)
 * ✅ Better provider selection with active/verified providers
 * ✅ Logical sections with icons
 * ✅ Contract duration calculation
 * ✅ Enhanced pricing model selection
 * ✅ Real-time validation
 *
 * @version 2.0
 * @since 2026-01-03
 */
const ProviderContractCreate = () => {
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();

  // ──────────────────────────────────────────────────────────────────────
  // STATE
  // ──────────────────────────────────────────────────────────────────────

  const [autoContractCode, setAutoContractCode] = useState('AUTO-GENERATED');
  const [contractDuration, setContractDuration] = useState(12);

  const [formData, setFormData] = useState({
    providerId: '',
    contractCode: '',
    startDate: new Date(),
    endDate: addYears(new Date(), 1),
    pricingModel: 'DISCOUNT',
    discountRate: 10.0,
    notes: ''
  });

  const [errors, setErrors] = useState({});
  const [selectedProvider, setSelectedProvider] = useState(null);

  // ──────────────────────────────────────────────────────────────────────
  // DATA FETCHING
  // ──────────────────────────────────────────────────────────────────────

  // Fetch providers with better error handling
  const {
    data: providersResponse,
    isLoading: providersLoading,
    error: providersError,
    refetch: refetchProviders
  } = useQuery({
    queryKey: ['providers', 'selector'],
    queryFn: async () => {
      const response = await getProviderSelector();
      console.log('Providers Selector API Response:', response);
      return response;
    },
    retry: 2,
    staleTime: 5 * 60 * 1000, // 5 minutes
    onError: (error) => {
      console.error('Failed to load providers:', error);
      enqueueSnackbar('فشل تحميل قائمة مقدمي الخدمة', { variant: 'error' });
    }
  });

  // Create contract mutation
  const createMutation = useMutation({
    mutationFn: createProviderContract,
    onSuccess: () => {
      // Invalidate cache to refresh list
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      enqueueSnackbar('تم إنشاء العقد بنجاح', { variant: 'success' });
      navigate('/provider-contracts');
    },
    onError: (error) => {
      console.error('Failed to create contract:', error);
      enqueueSnackbar(error.message || 'فشل إنشاء العقد', { variant: 'error' });
    }
  });

  // Extract providers from response
  // Selector endpoint returns array directly
  const providers = Array.isArray(providersResponse) ? providersResponse : providersResponse?.data || [];

  // ──────────────────────────────────────────────────────────────────────
  // EFFECTS
  // ──────────────────────────────────────────────────────────────────────

  // Auto-generate contract code when provider is selected
  useEffect(() => {
    if (selectedProvider && formData.startDate) {
      const providerInitials = (selectedProvider.name || '')
        .split(' ')
        .slice(0, 2)
        .map((word) => word[0])
        .join('');
      const year = format(formData.startDate, 'yyyy');
      const month = format(formData.startDate, 'MM');
      const timestamp = Date.now().toString().slice(-3);
      setAutoContractCode(`PC-${providerInitials || 'XX'}-${year}${month}-${timestamp}`);
    } else {
      setAutoContractCode('AUTO-GENERATED');
    }
  }, [selectedProvider, formData.startDate]);

  // Calculate contract duration
  useEffect(() => {
    if (formData.startDate && formData.endDate) {
      const months = differenceInMonths(formData.endDate, formData.startDate);
      setContractDuration(months > 0 ? months : 0);
    }
  }, [formData.startDate, formData.endDate]);

  // ──────────────────────────────────────────────────────────────────────
  // HANDLERS
  // ──────────────────────────────────────────────────────────────────────

  const handleProviderChange = (event, newValue) => {
    setSelectedProvider(newValue);
    setFormData({ ...formData, providerId: newValue?.id || '' });
    if (errors.providerId) {
      setErrors({ ...errors, providerId: '' });
    }
  };

  const handleInputChange = (field) => (event) => {
    setFormData({ ...formData, [field]: event.target.value });
    if (errors[field]) {
      setErrors({ ...errors, [field]: '' });
    }
  };

  const handleDateChange = (field) => (newDate) => {
    setFormData({ ...formData, [field]: newDate });
    if (errors[field]) {
      setErrors({ ...errors, [field]: '' });
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.providerId) {
      newErrors.providerId = 'يرجى اختيار مقدم خدمة';
    }

    if (!formData.startDate) {
      newErrors.startDate = 'تاريخ البداية مطلوب';
    }

    if (!formData.endDate) {
      newErrors.endDate = 'تاريخ النهاية مطلوب';
    }

    if (formData.startDate && formData.endDate && formData.endDate <= formData.startDate) {
      newErrors.endDate = 'تاريخ النهاية يجب أن يكون بعد تاريخ البداية';
    }

    if (!formData.pricingModel) {
      newErrors.pricingModel = 'نموذج التسعير مطلوب';
    }

    if (formData.pricingModel === 'DISCOUNT') {
      if (formData.discountRate === '' || formData.discountRate === null || formData.discountRate < 0 || formData.discountRate > 100) {
        newErrors.discountRate = 'نسبة الخصم يجب أن تكون بين 0 و 100';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      enqueueSnackbar('يرجى تعبئة جميع الحقول المطلوبة بشكل صحيح', { variant: 'error' });
      return;
    }

    const payload = {
      providerId: formData.providerId,
      contractCode: autoContractCode, // Use auto-generated code
      startDate: format(formData.startDate, 'yyyy-MM-dd'),
      endDate: format(formData.endDate, 'yyyy-MM-dd'),
      pricingModel: formData.pricingModel,
      discountPercent: formData.pricingModel === 'DISCOUNT' ? parseFloat(formData.discountRate) : null,
      notes: formData.notes || null
    };

    createMutation.mutate(payload);
  };

  // ──────────────────────────────────────────────────────────────────────
  // RENDER
  // ──────────────────────────────────────────────────────────────────────

  return (
    <>
      <ModernPageHeader
        title="إنشاء عقد مقدم خدمة جديد"
        subtitle="إضافة عقد جديد لمقدم خدمة صحية مع تحديد نموذج التسعير والشروط"
        icon={Description}
        breadcrumbs={[{ label: 'العقود', path: '/provider-contracts' }, { label: 'إنشاء عقد جديد' }]}
        actions={
          <Button startIcon={<ArrowBack />} onClick={() => navigate('/provider-contracts')} disabled={createMutation.isLoading}>
            عودة
          </Button>
        }
      />

      <MainCard>
        <form onSubmit={handleSubmit}>
          <Grid container spacing={4}>
            {/* ═════════════════════════════════════════════════════════════ */}
            {/* SECTION 1: Provider Selection */}
            {/* ═════════════════════════════════════════════════════════════ */}
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: '1.5rem', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
                  <Business color="primary" />
                  <Typography variant="h5">اختيار مقدم الخدمة</Typography>
                </Box>

                {providersLoading && (
                  <Alert severity="info" icon={<CircularProgress size={20} />} sx={{ mb: '1.0rem' }}>
                    جاري تحميل قائمة مقدمي الخدمة...
                  </Alert>
                )}

                {providersError && (
                  <Alert
                    severity="error"
                    sx={{ mb: '1.0rem' }}
                    action={
                      <Button color="inherit" size="small" onClick={refetchProviders}>
                        إعادة المحاولة
                      </Button>
                    }
                  >
                    فشل تحميل قائمة مقدمي الخدمة. يرجى المحاولة مرة أخرى.
                  </Alert>
                )}

                {!providersError && (
                  <Box sx={{ mb: '1.0rem', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button size="small" startIcon={<Refresh />} onClick={refetchProviders} disabled={providersLoading}>
                      تحديث قائمة مقدمي الخدمة
                    </Button>
                  </Box>
                )}

                {!providersLoading && providers.length === 0 && (
                  <Alert severity="warning">
                    لا توجد مقدمي خدمة متاحين. يرجى إضافة مقدم خدمة أولاً من{' '}
                    <Button size="small" onClick={() => navigate('/providers/add')}>
                      صفحة مقدمي الخدمات
                    </Button>
                  </Alert>
                )}

                <Autocomplete
                  fullWidth
                  value={selectedProvider}
                  onChange={handleProviderChange}
                  options={providers}
                  getOptionLabel={(option) => option.name || ''}
                  loading={providersLoading}
                  disabled={providersLoading || createMutation.isLoading}
                  isOptionEqualToValue={(option, value) => option.id === value?.id}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="مقدم الخدمة *"
                      error={!!errors.providerId}
                      helperText={errors.providerId || 'ابحث واختر مقدم الخدمة الصحية'}
                      InputProps={{
                        ...params.InputProps,
                        endAdornment: (
                          <>
                            {providersLoading && <CircularProgress color="inherit" size={20} />}
                            {params.InputProps.endAdornment}
                          </>
                        )
                      }}
                    />
                  )}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} key={option.id}>
                      <Stack spacing={0.5} sx={{ width: '100%' }}>
                        <Typography variant="body2" fontWeight={500}>
                          {option.name}
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                          <Typography variant="caption" color="text.secondary">
                            رخصة: {option.licenseNumber}
                          </Typography>
                          {option.city && <Chip label={option.city} size="small" variant="outlined" sx={{ height: '1.125rem' }} />}
                          {option.providerType && (
                            <Chip
                              label={
                                {
                                  HOSPITAL: 'مستشفى',
                                  CLINIC: 'عيادة',
                                  LAB: 'مختبر',
                                  PHARMACY: 'صيدلية',
                                  RADIOLOGY: 'أشعة'
                                }[option.providerType] || option.providerType
                              }
                              size="small"
                              color="primary"
                              variant="outlined"
                              sx={{ height: '1.125rem' }}
                            />
                          )}
                        </Box>
                      </Stack>
                    </Box>
                  )}
                  noOptionsText={providersLoading ? 'جاري التحميل...' : 'لا توجد مقدمي خدمة'}
                />
              </Paper>
            </Grid>

            {/* ═════════════════════════════════════════════════════════════ */}
            {/* SECTION 2: Contract Details */}
            {/* ═════════════════════════════════════════════════════════════ */}
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: '1.5rem', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
                  <DateRange color="primary" />
                  <Typography variant="h5">تفاصيل العقد</Typography>
                </Box>

                <Grid container spacing={3}>
                  {/* Auto-Generated Contract Code */}
                  <Grid size={12}>
                    <Alert severity="info" sx={{ mb: '1.0rem' }}>
                      <Typography variant="body2">
                        <strong>رمز العقد:</strong> سيتم إنشاء رمز تلقائي للعقد عند الحفظ
                      </Typography>
                    </Alert>
                    <TextField
                      fullWidth
                      label="رمز العقد (Auto-Generated Code)"
                      value={autoContractCode}
                      disabled
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Chip label="AUTO" size="small" color="primary" />
                          </InputAdornment>
                        )
                      }}
                      helperText="سيتم توليد الرمز تلقائياً بناءً على مقدم الخدمة والتاريخ"
                    />
                  </Grid>

                  {/* Start Date */}
                  <Grid size={{ xs: 12, md: 6 }}>
                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                      <DatePicker
                        label="تاريخ بداية العقد *"
                        value={formData.startDate}
                        onChange={handleDateChange('startDate')}
                        disabled={createMutation.isLoading}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            error: !!errors.startDate,
                            helperText: errors.startDate || 'تاريخ سريان العقد'
                          }
                        }}
                      />
                    </LocalizationProvider>
                  </Grid>

                  {/* End Date */}
                  <Grid size={{ xs: 12, md: 6 }}>
                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                      <DatePicker
                        label="تاريخ انتهاء العقد *"
                        value={formData.endDate}
                        onChange={handleDateChange('endDate')}
                        disabled={createMutation.isLoading}
                        minDate={formData.startDate}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            error: !!errors.endDate,
                            helperText: errors.endDate || `مدة العقد: ${contractDuration} شهر`
                          }
                        }}
                      />
                    </LocalizationProvider>
                  </Grid>

                  {/* Contract Duration Info */}
                  {contractDuration > 0 && (
                    <Grid size={12}>
                      <Alert severity="success" icon={<Info />}>
                        <Typography variant="body2">
                          مدة العقد: <strong>{contractDuration}</strong> شهر ({Math.floor(contractDuration / 12)} سنة و{' '}
                          {contractDuration % 12} شهر)
                        </Typography>
                      </Alert>
                    </Grid>
                  )}
                </Grid>
              </Paper>
            </Grid>

            {/* ═════════════════════════════════════════════════════════════ */}
            {/* SECTION 3: Pricing Model */}
            {/* ═════════════════════════════════════════════════════════════ */}
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: '1.5rem', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
                  <AttachMoney color="primary" />
                  <Typography variant="h5">نموذج التسعير</Typography>
                </Box>

                <Grid container spacing={3}>
                  {/* Pricing Model */}
                  <Grid size={{ xs: 12, md: formData.pricingModel === 'DISCOUNT' ? 6 : 12 }}>
                    <FormControl fullWidth error={!!errors.pricingModel}>
                      <InputLabel>نموذج التسعير *</InputLabel>
                      <Select
                        value={formData.pricingModel}
                        onChange={handleInputChange('pricingModel')}
                        label="نموذج التسعير *"
                        disabled={createMutation.isLoading}
                      >
                        {PRICING_MODELS.map((model) => (
                          <MenuItem key={model.value} value={model.value}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <span>{model.icon}</span>
                              <Stack spacing={0}>
                                <Typography variant="body2">{model.label}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {model.description}
                                </Typography>
                              </Stack>
                            </Box>
                          </MenuItem>
                        ))}
                      </Select>
                      {errors.pricingModel && <FormHelperText>{errors.pricingModel}</FormHelperText>}
                    </FormControl>
                  </Grid>

                  {/* Discount Rate (conditional) */}
                  {formData.pricingModel === 'DISCOUNT' && (
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        type="number"
                        label="نسبة الخصم % *"
                        value={formData.discountRate}
                        onChange={handleInputChange('discountRate')}
                        error={!!errors.discountRate}
                        helperText={errors.discountRate || 'من 0 إلى 100'}
                        disabled={createMutation.isLoading}
                        inputProps={{
                          min: 0,
                          max: 100,
                          step: 0.5
                        }}
                        InputProps={{
                          endAdornment: <InputAdornment position="end">%</InputAdornment>
                        }}
                      />
                    </Grid>
                  )}

                  {/* Pricing Model Info */}
                  <Grid size={12}>
                    <Alert severity="info">{PRICING_MODELS.find((m) => m.value === formData.pricingModel)?.description}</Alert>
                  </Grid>

                  {/* Notes */}
                  <Grid size={12}>
                    <TextField
                      fullWidth
                      multiline
                      rows={4}
                      label="ملاحظات العقد (اختياري)"
                      value={formData.notes}
                      onChange={handleInputChange('notes')}
                      disabled={createMutation.isLoading}
                      placeholder="أي شروط أو ملاحظات خاصة بالعقد..."
                      helperText="يمكنك إضافة أي معلومات إضافية عن العقد"
                    />
                  </Grid>
                </Grid>
              </Paper>
            </Grid>

            {/* ═════════════════════════════════════════════════════════════ */}
            {/* Action Buttons */}
            {/* ═════════════════════════════════════════════════════════════ */}
            <Grid size={12}>
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '1.0rem', pt: '1.0rem' }}>
                <Button variant="outlined" onClick={() => navigate('/provider-contracts')} disabled={createMutation.isLoading}>
                  إلغاء
                </Button>

                <Button
                  type="submit"
                  variant="contained"
                  startIcon={createMutation.isLoading ? <CircularProgress size={20} /> : <Save />}
                  disabled={createMutation.isLoading || providersLoading || providers.length === 0}
                >
                  {createMutation.isLoading ? 'جاري الحفظ...' : 'حفظ العقد'}
                </Button>
              </Box>
            </Grid>
          </Grid>
        </form>
      </MainCard>
    </>
  );
};

export default ProviderContractCreate;
