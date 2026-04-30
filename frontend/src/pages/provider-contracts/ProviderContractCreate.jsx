import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import { addYears, format } from 'date-fns';

import {
  Alert,
  Autocomplete,
  Button,
  CircularProgress,
  FormControlLabel,
  Grid,
  InputAdornment,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { ArrowBack, Save, Add as AddIcon } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

import { createProviderContract, PRICING_MODEL_CONFIG } from 'services/api/provider-contracts.service';
import { getProviderSelector } from 'services/api/providers.service';

const PRICING_MODELS = [
  { value: 'DISCOUNT', label: PRICING_MODEL_CONFIG.DISCOUNT.label },
  { value: 'FIXED', label: PRICING_MODEL_CONFIG.FIXED.label },
  { value: 'TIERED', label: PRICING_MODEL_CONFIG.TIERED.label },
  { value: 'NEGOTIATED', label: PRICING_MODEL_CONFIG.NEGOTIATED.label }
];

const ProviderContractCreate = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  const [errors, setErrors] = useState({});
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [autoContractCode, setAutoContractCode] = useState('AUTO-GENERATED');

  const [formData, setFormData] = useState({
    providerId: '',
    startDate: new Date(),
    endDate: addYears(new Date(), 1),
    pricingModel: 'DISCOUNT',
    discountPercent: 10,
    discountBeforeRejection: false,
    notes: ''
  });

  const {
    data: providersResponse,
    isLoading: providersLoading,
    error: providersError
  } = useQuery({
    queryKey: ['providers', 'selector'],
    queryFn: getProviderSelector,
    staleTime: 5 * 60 * 1000
  });

  const providers = useMemo(() => {
    return Array.isArray(providersResponse) ? providersResponse : providersResponse?.data || [];
  }, [providersResponse]);

  useEffect(() => {
    if (!selectedProvider || !formData.startDate) {
      setAutoContractCode('AUTO-GENERATED');
      return;
    }

    const providerInitials = (selectedProvider.name || '')
      .split(' ')
      .slice(0, 2)
      .map((word) => word?.[0] || '')
      .join('')
      .toUpperCase();

    const year = format(formData.startDate, 'yyyy');
    const month = format(formData.startDate, 'MM');
    const suffix = Date.now().toString().slice(-3);
    setAutoContractCode(`PC-${providerInitials || 'XX'}-${year}${month}-${suffix}`);
  }, [selectedProvider, formData.startDate]);

  const createMutation = useMutation({
    mutationFn: createProviderContract,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      enqueueSnackbar('تم إنشاء العقد بنجاح', { variant: 'success' });
      navigate('/provider-contracts');
    },
    onError: (error) => {
      enqueueSnackbar(error?.message || 'فشل إنشاء العقد', { variant: 'error' });
    }
  });

  const handleInputChange = (field) => (event) => {
    setFormData((prev) => ({ ...prev, [field]: event.target.value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: '' }));
    }
  };

  const handleDateChange = (field) => (value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: '' }));
    }
  };

  const handleProviderChange = (_, value) => {
    setSelectedProvider(value);
    setFormData((prev) => ({ ...prev, providerId: value?.id || '' }));
    if (errors.providerId) {
      setErrors((prev) => ({ ...prev, providerId: '' }));
    }
  };

  const validate = () => {
    const nextErrors = {};

    if (!formData.providerId) nextErrors.providerId = 'يرجى اختيار مقدم خدمة';
    if (!formData.startDate) nextErrors.startDate = 'تاريخ البداية مطلوب';
    if (!formData.endDate) nextErrors.endDate = 'تاريخ النهاية مطلوب';

    if (formData.startDate && formData.endDate && formData.endDate <= formData.startDate) {
      nextErrors.endDate = 'تاريخ النهاية يجب أن يكون بعد تاريخ البداية';
    }

    if (!formData.pricingModel) nextErrors.pricingModel = 'نموذج التسعير مطلوب';

    if (formData.pricingModel === 'DISCOUNT') {
      const value = Number(formData.discountPercent);
      if (Number.isNaN(value) || value < 0 || value > 100) {
        nextErrors.discountPercent = 'نسبة الخصم يجب أن تكون بين 0 و 100';
      }
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    const payload = {
      providerId: formData.providerId,
      contractCode: autoContractCode,
      startDate: format(formData.startDate, 'yyyy-MM-dd'),
      endDate: format(formData.endDate, 'yyyy-MM-dd'),
      pricingModel: formData.pricingModel,
      discountPercent: formData.pricingModel === 'DISCOUNT' ? Number(formData.discountPercent) : null,
      discountBeforeRejection: formData.discountBeforeRejection,
      notes: formData.notes || null
    };

    createMutation.mutate(payload);
  };

  return (
    <>
      <ModernPageHeader
        title="إضافة عقد مقدم خدمة"
        subtitle="إنشاء عقد جديد بنفس نمط شاشة تعديل العقد"
        icon={AddIcon}
        breadcrumbs={[{ label: 'العقود', path: '/provider-contracts' }, { label: 'إضافة عقد' }]}
        actions={
          <Button startIcon={<ArrowBack />} onClick={() => navigate('/provider-contracts')} disabled={createMutation.isPending}>
            عودة
          </Button>
        }
      />

      <MainCard>
        <form onSubmit={handleSubmit}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Autocomplete
                fullWidth
                options={providers}
                value={selectedProvider}
                onChange={handleProviderChange}
                isOptionEqualToValue={(option, value) => option.id === value?.id}
                getOptionLabel={(option) => option?.name || ''}
                loading={providersLoading}
                disabled={providersLoading || createMutation.isPending}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="مقدم الخدمة *"
                    error={!!errors.providerId}
                    helperText={errors.providerId || 'ابحث واختر مقدم الخدمة'}
                  />
                )}
                noOptionsText={providersLoading ? 'جاري التحميل...' : 'لا توجد بيانات'}
              />
              {providersError && (
                <Typography variant="caption" color="error.main" sx={{ display: 'block', mt: 0.5 }}>
                  تعذر تحميل قائمة مقدمي الخدمة
                </Typography>
              )}
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth label="رمز العقد" value={autoContractCode} disabled />
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth label="الحالة" value="DRAFT" disabled helperText="سيتم إنشاء العقد كمسودة" />
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <LocalizationProvider dateAdapter={AdapterDateFns}>
                <DatePicker
                  label="تاريخ البداية *"
                  value={formData.startDate}
                  onChange={handleDateChange('startDate')}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      error: !!errors.startDate,
                      helperText: errors.startDate
                    }
                  }}
                />
              </LocalizationProvider>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <LocalizationProvider dateAdapter={AdapterDateFns}>
                <DatePicker
                  label="تاريخ النهاية *"
                  value={formData.endDate}
                  onChange={handleDateChange('endDate')}
                  minDate={formData.startDate || undefined}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      error: !!errors.endDate,
                      helperText: errors.endDate
                    }
                  }}
                />
              </LocalizationProvider>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                select
                fullWidth
                label="نموذج التسعير *"
                value={formData.pricingModel}
                onChange={handleInputChange('pricingModel')}
                error={!!errors.pricingModel}
                helperText={errors.pricingModel}
              >
                {PRICING_MODELS.map((model) => (
                  <MenuItem key={model.value} value={model.value}>
                    {model.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                fullWidth
                type="number"
                label="نسبة الخصم"
                value={formData.discountPercent}
                onChange={handleInputChange('discountPercent')}
                error={!!errors.discountPercent}
                helperText={errors.discountPercent || 'من 0 إلى 100'}
                disabled={formData.pricingModel !== 'DISCOUNT'}
                InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                inputProps={{ min: 0, max: 100, step: 0.5 }}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 3 }}>
              <Tooltip title={formData.discountBeforeRejection
                ? 'قبل: خصم نسبة التخفيض من حصة المرفق أولاً ثم خصم المرفوض'
                : 'بعد: خصم المرفوض أولاً ثم تطبيق نسبة التخفيض'}
              >
                <FormControlLabel
                  control={
                    <Switch
                      checked={formData.discountBeforeRejection}
                      onChange={(e) => setFormData((prev) => ({ ...prev, discountBeforeRejection: e.target.checked }))}
                      disabled={formData.pricingModel !== 'DISCOUNT'}
                    />
                  }
                  label={formData.discountBeforeRejection ? 'الخصم قبل المرفوض' : 'الخصم بعد المرفوض'}
                  sx={{ mt: 1 }}
                />
              </Tooltip>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                multiline
                rows={2}
                label="ملاحظات"
                value={formData.notes}
                onChange={handleInputChange('notes')}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Alert severity="info" sx={{ py: 0.5 }}>
                تم ضغط توزيع الحقول لتقليل الحاجة إلى السكرول.
              </Alert>
            </Grid>

            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end">
                <Button variant="outlined" onClick={() => navigate('/provider-contracts')} disabled={createMutation.isPending}>
                  إلغاء
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={createMutation.isPending ? <CircularProgress size={18} /> : <Save />}
                  disabled={createMutation.isPending || providersLoading}
                >
                  {createMutation.isPending ? 'جاري الحفظ...' : 'حفظ العقد'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </form>
      </MainCard>
    </>
  );
};

export default ProviderContractCreate;
