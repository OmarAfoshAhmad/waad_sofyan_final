import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Grid,
  Typography,
  Switch,
  FormControlLabel,
  Stack,
  Alert,
  InputAdornment,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  OutlinedInput
} from '@mui/material';
import { Save as SaveIcon, Cancel as CancelIcon, CardGiftcard as CardGiftcardIcon } from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { createBenefitPackage } from 'services/api/benefit-packages.service';
import { useAllMedicalPackages } from 'hooks/useMedicalPackages';

/**
 * Benefit Package Create Page
 * Form to create a new benefit package
 */
const BenefitPackageCreate = () => {
  const navigate = useNavigate();
  const { data: medicalPackages, loading: packagesLoading } = useAllMedicalPackages();

  const [form, setForm] = useState({
    code: '',
    name: '',
    description: '',
    coverageLimit: '',
    validityDays: '',
    medicalPackageIds: [],
    active: true
  });

  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  const handlePackagesChange = (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, medicalPackageIds: typeof value === 'string' ? value.split(',') : value }));
    if (errors.medicalPackageIds) {
      setErrors((prev) => ({ ...prev, medicalPackageIds: null }));
    }
  };

  const validate = () => {
    const newErrors = {};

    if (!form.code.trim()) newErrors.code = 'الكود مطلوب';
    if (!form.name.trim()) newErrors.name = 'الاسم مطلوب';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validate()) return;

    setLoading(true);
    setApiError(null);

    try {
      const payload = {
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        coverageLimit: form.coverageLimit ? parseFloat(form.coverageLimit) : null,
        validityDays: form.validityDays ? parseInt(form.validityDays, 10) : null,
        medicalPackageIds: form.medicalPackageIds.map((id) => parseInt(id, 10)),
        active: form.active
      };

      await createBenefitPackage(payload);
      navigate('/benefit-packages');
    } catch (err) {
      console.error('Failed to create benefit package:', err);
      setApiError(err.response?.data?.message || err.message || 'فشل إنشاء الباقة');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    navigate('/benefit-packages');
  };

  const breadcrumbs = [{ title: 'باقات المنافع', path: '/benefit-packages' }, { title: 'إضافة باقة جديدة' }];

  return (
    <>
      <ModernPageHeader
        title="إضافة باقة منافع جديدة"
        subtitle="أدخل بيانات باقة المنافع الجديدة"
        icon={CardGiftcardIcon}
        breadcrumbs={breadcrumbs}
      />

      <MainCard>
        {apiError && (
          <Alert severity="error" sx={{ mb: '1.5rem' }} onClose={() => setApiError(null)}>
            {apiError}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            {/* Basic Information */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom>
                المعلومات الأساسية
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                required
                label="الكود"
                value={form.code}
                onChange={handleChange('code')}
                error={!!errors.code}
                helperText={errors.code}
                placeholder="BP001"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                required
                label="الاسم"
                value={form.name}
                onChange={handleChange('name')}
                error={!!errors.name}
                helperText={errors.name}
                placeholder="باقة المنافع الشاملة"
              />
            </Grid>

            <Grid size={12}>
              <TextField
                fullWidth
                multiline
                rows={3}
                label="الوصف"
                value={form.description}
                onChange={handleChange('description')}
                placeholder="وصف الباقة..."
              />
            </Grid>

            {/* Medical Packages Selection */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
                الباقات الطبية المشمولة
              </Typography>
            </Grid>

            <Grid size={12}>
              <FormControl fullWidth error={!!errors.medicalPackageIds}>
                <InputLabel>اختر الباقات الطبية</InputLabel>
                <Select
                  multiple
                  value={form.medicalPackageIds}
                  onChange={handlePackagesChange}
                  input={<OutlinedInput label="اختر الباقات الطبية" />}
                  renderValue={(selected) => (
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                      {selected.map((pkgId) => {
                        const pkg = medicalPackages?.find((p) => p.id === parseInt(pkgId, 10));
                        return <Chip key={pkgId} label={pkg?.name || pkgId} size="small" />;
                      })}
                    </Box>
                  )}
                  disabled={packagesLoading}
                >
                  <MenuItem value="" disabled>
                    -- اختر الباقات الطبية --
                  </MenuItem>
                  {medicalPackages?.map((pkg) => (
                    <MenuItem key={pkg.id} value={pkg.id}>
                      {pkg.name} ({pkg.code})
                    </MenuItem>
                  ))}
                </Select>
                {errors.medicalPackageIds && (
                  <Typography variant="caption" color="error" sx={{ mt: 0.5, ml: '1.0rem' }}>
                    {errors.medicalPackageIds}
                  </Typography>
                )}
              </FormControl>
            </Grid>

            {/* Coverage & Validity */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
                التغطية والصلاحية
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                type="number"
                label="حد التغطية (LYD)"
                value={form.coverageLimit}
                onChange={handleChange('coverageLimit')}
                inputProps={{ step: 0.01, min: 0 }}
                InputProps={{
                  startAdornment: <InputAdornment position="start">LYD</InputAdornment>
                }}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                type="number"
                label="صلاحية الباقة (بالأيام)"
                value={form.validityDays}
                onChange={handleChange('validityDays')}
                inputProps={{ min: 1 }}
                InputProps={{
                  endAdornment: <InputAdornment position="end">يوم</InputAdornment>
                }}
                helperText="عدد الأيام التي تكون فيها الباقة صالحة"
              />
            </Grid>

            {/* Status */}
            <Grid size={12}>
              <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
                الحالة
              </Typography>
            </Grid>

            <Grid size={12}>
              <FormControlLabel
                control={<Switch checked={form.active} onChange={handleChange('active')} />}
                label={form.active ? 'نشط' : 'غير نشط'}
              />
            </Grid>

            {/* Action Buttons */}
            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                <Button variant="outlined" startIcon={<CancelIcon />} onClick={handleCancel} disabled={loading}>
                  إلغاء
                </Button>
                
                  <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
                    {loading ? 'جارٍ الحفظ...' : 'حفظ'}
                  </Button>
                  
              </Stack>
            </Grid>
          </Grid>
        </form>
      </MainCard>
    </>
  );
};

export default BenefitPackageCreate;
