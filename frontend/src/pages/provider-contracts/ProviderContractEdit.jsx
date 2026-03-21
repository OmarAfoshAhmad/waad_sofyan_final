import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import { format } from 'date-fns';

import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { ArrowBack, Save, Edit as EditIcon } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

import {
  getProviderContractById,
  updateProviderContract,
  PRICING_MODEL_CONFIG
} from 'services/api/provider-contracts.service';

const PRICING_MODELS = [
  { value: 'DISCOUNT', label: PRICING_MODEL_CONFIG.DISCOUNT.label },
  { value: 'FIXED', label: PRICING_MODEL_CONFIG.FIXED.label },
  { value: 'TIERED', label: PRICING_MODEL_CONFIG.TIERED.label },
  { value: 'NEGOTIATED', label: PRICING_MODEL_CONFIG.NEGOTIATED.label }
];

const ProviderContractEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  const [errors, setErrors] = useState({});

  const { data: contract, isLoading } = useQuery({
    queryKey: ['provider-contract', id],
    queryFn: () => getProviderContractById(id),
    enabled: !!id,
    staleTime: 30000
  });

  const initialFormData = useMemo(() => {
    if (!contract) {
      return {
        contractCode: '',
        startDate: null,
        endDate: null,
        pricingModel: 'DISCOUNT',
        discountPercent: 0,
        notes: ''
      };
    }

    return {
      contractCode: contract.contractCode || '',
      startDate: contract.startDate ? new Date(contract.startDate) : null,
      endDate: contract.endDate ? new Date(contract.endDate) : null,
      pricingModel: contract.pricingModel || 'DISCOUNT',
      discountPercent: contract.discountPercent ?? 0,
      notes: contract.notes || ''
    };
  }, [contract]);

  const [formData, setFormData] = useState(null);

  useEffect(() => {
    if (contract && formData === null) {
      setFormData(initialFormData);
    }
  }, [contract, formData, initialFormData]);

  const updateMutation = useMutation({
    mutationFn: (payload) => updateProviderContract(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      enqueueSnackbar('تم تحديث العقد بنجاح', { variant: 'success' });
      navigate(`/provider-contracts/${id}`);
    },
    onError: (error) => {
      enqueueSnackbar(error?.message || 'فشل تحديث العقد', { variant: 'error' });
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

  const validate = () => {
    const nextErrors = {};

    if (!formData?.startDate) nextErrors.startDate = 'تاريخ البداية مطلوب';
    if (!formData?.endDate) nextErrors.endDate = 'تاريخ النهاية مطلوب';

    if (formData?.startDate && formData?.endDate && formData.endDate <= formData.startDate) {
      nextErrors.endDate = 'تاريخ النهاية يجب أن يكون بعد تاريخ البداية';
    }

    if (!formData?.pricingModel) {
      nextErrors.pricingModel = 'نموذج التسعير مطلوب';
    }

    if (formData?.pricingModel === 'DISCOUNT') {
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
    if (!formData || !contract) return;
    if (!validate()) return;

    const payload = {
      providerId: contract.providerId || contract.provider?.id,
      contractCode: formData.contractCode,
      startDate: format(formData.startDate, 'yyyy-MM-dd'),
      endDate: format(formData.endDate, 'yyyy-MM-dd'),
      pricingModel: formData.pricingModel,
      discountPercent: formData.pricingModel === 'DISCOUNT' ? Number(formData.discountPercent) : null,
      notes: formData.notes || null
    };

    updateMutation.mutate(payload);
  };

  if (isLoading || !formData) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: '4rem' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <>
      <ModernPageHeader
        title="تعديل بيانات العقد"
        subtitle="تحديث معلومات عقد مقدم الخدمة"
        icon={EditIcon}
        breadcrumbs={[{ label: 'العقود', path: '/provider-contracts' }, { label: 'تعديل العقد' }]}
        actions={
          <Button startIcon={<ArrowBack />} onClick={() => navigate(`/provider-contracts/${id}`)} disabled={updateMutation.isPending}>
            عودة
          </Button>
        }
      />

      <MainCard>
        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid size={12}>
              <Alert severity="info">رقم العقد ومقدم الخدمة ثابتان، ويمكنك تعديل التواريخ، نموذج التسعير، والخصم والملاحظات.</Alert>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth label="رمز العقد" value={formData.contractCode} disabled />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="مقدم الخدمة"
                value={contract?.providerName || contract?.provider?.name || '-'}
                disabled
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
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

            <Grid size={{ xs: 12, md: 6 }}>
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

            <Grid size={{ xs: 12, md: 6 }}>
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

            <Grid size={{ xs: 12, md: 6 }}>
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

            <Grid size={12}>
              <TextField
                fullWidth
                multiline
                rows={4}
                label="ملاحظات"
                value={formData.notes}
                onChange={handleInputChange('notes')}
              />
            </Grid>

            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end">
                <Button variant="outlined" onClick={() => navigate(`/provider-contracts/${id}`)} disabled={updateMutation.isPending}>
                  إلغاء
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={updateMutation.isPending ? <CircularProgress size={18} /> : <Save />}
                  disabled={updateMutation.isPending}
                >
                  {updateMutation.isPending ? 'جاري الحفظ...' : 'حفظ التعديلات'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </form>
      </MainCard>
    </>
  );
};

export default ProviderContractEdit;
