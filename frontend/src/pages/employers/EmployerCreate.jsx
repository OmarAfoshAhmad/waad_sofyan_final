import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, CircularProgress, FormControlLabel, Grid, InputAdornment, Stack, Switch, TextField, Divider, Typography } from '@mui/material';
import { ArrowBack as ArrowBackIcon, Save as SaveIcon, Business as BusinessIcon } from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { createEmployer, checkEmployerField } from 'services/api/employers.service';

const LABELS = {
  list: 'جهات العمل',
  add: 'إضافة جهة عمل',
  back: 'رجوع',
  code: 'رمز جهة العمل',
  codePlaceholder: 'مثال: JFZ أو EMP-01',
  name: 'اسم جهة العمل',
  namePlaceholder: 'أدخل اسم جهة العمل (عربي أو إنجليزي)',
  email: 'البريد الإلكتروني',
  emailPlaceholder: 'example@company.com',
  phone: 'رقم الهاتف',
  phonePlaceholder: '+966XXXXXXXXX',
  address: 'العنوان',
  addressPlaceholder: 'أدخل عنوان جهة العمل',
  active: 'نشط',
  cancel: 'إلغاء',
  save: 'حفظ',
  saving: 'جار الحفظ...',
  required: 'هذا الحقل مطلوب',
  invalidEmail: 'البريد الإلكتروني غير صحيح',
  fixErrors: 'الرجاء تصحيح الأخطاء',
  createdSuccess: 'تم إنشاء جهة العمل بنجاح',
  saveError: 'فشل في حفظ جهة العمل',
};

const emptyEmployer = { code: '', name: '', email: '', phone: '', address: '', active: true };
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Parse backend error — handles both raw Axios errors and service-restructured errors
// Supports: CODE_DUPLICATE:/NAME_DUPLICATE: prefix (409) + BUSINESS_RULE_VIOLATION (422)
const parseApiError = (err) => {
  const msg = err?.response?.data?.message || err?.message || '';
  const errorCode = err?.response?.data?.errorCode || err?.errorCode || '';
  if (msg.startsWith('CODE_DUPLICATE:')) return { code: msg.replace('CODE_DUPLICATE:', '') };
  if (msg.startsWith('NAME_DUPLICATE:')) return { name: msg.replace('NAME_DUPLICATE:', '') };
  const lm = msg.toLowerCase();
  if (lm.includes('code already exists') || (errorCode === 'BUSINESS_RULE_VIOLATION' && lm.includes('code'))) {
    return { code: 'هذا الرمز مستخدم مسبقاً، اختر رمزاً آخر' };
  }
  if (lm.includes('name already exists') || (errorCode === 'BUSINESS_RULE_VIOLATION' && lm.includes('name'))) {
    return { name: 'اسم جهة العمل هذا مستخدم مسبقاً، اختر اسماً آخر' };
  }
  return null;
};

const validateField = (field, value) => {
  if (field === 'code') {
    if (!value?.trim()) return LABELS.required;
    if (value.trim().length < 2) return 'الرمز يجب أن يكون حرفين على الأقل';
    if (value.trim().length > 20) return 'الرمز لا يتجاوز 20 حرفاً';
    if (!/^[A-Za-z0-9\-_]+$/.test(value.trim())) return 'الرمز يحتوي على أحرف غير مسموح بها';
  }
  if (field === 'name') {
    if (!value?.trim()) return LABELS.required;
    if (value.trim().length < 2) return 'الاسم يجب أن يكون حرفين على الأقل';
    if (value.trim().length > 100) return 'الاسم لا يتجاوز 100 حرف';
  }
  if (field === 'email' && value?.trim()) {
    if (!EMAIL_REGEX.test(value.trim())) return LABELS.invalidEmail;
  }
  return null;
};

const EmployerCreate = () => {
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();
  const [employer, setEmployer] = useState(emptyEmployer);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});
  const [checking, setChecking] = useState({ code: false, name: false });
  const debounceRef = useRef({});

  const scheduleUniquenessCheck = (field, value) => {
    clearTimeout(debounceRef.current[field]);
    debounceRef.current[field] = setTimeout(async () => {
      setChecking((prev) => ({ ...prev, [field]: true }));
      const available = await checkEmployerField(field, value, null);
      setChecking((prev) => ({ ...prev, [field]: false }));
      if (!available) {
        const msg = field === 'code'
          ? 'هذا الرمز مستخدم مسبقاً، اختر رمزاً آخر'
          : 'اسم جهة العمل هذا مستخدم مسبقاً، اختر اسماً آخر';
        setErrors((prev) => ({ ...prev, [field]: msg }));
      }
    }, 400);
  };

  const handleChange = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setEmployer((prev) => ({ ...prev, [field]: value }));
    const err = validateField(field, value);
    setErrors((prev) => ({ ...prev, [field]: err || null }));
    if (!err && (field === 'code' || field === 'name') && value.trim().length >= 2) {
      scheduleUniquenessCheck(field, value);
    }
  };

  const validate = () => {
    const newErrors = {
      code: validateField('code', employer.code),
      name: validateField('name', employer.name),
      email: validateField('email', employer.email),
    };
    // Remove null entries
    Object.keys(newErrors).forEach((k) => { if (!newErrors[k]) delete newErrors[k]; });
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) {
      enqueueSnackbar(LABELS.fixErrors, { variant: 'warning' });
      return;
    }
    try {
      setSaving(true);
      await createEmployer(employer);
      enqueueSnackbar(LABELS.createdSuccess, { variant: 'success' });
      navigate('/employers');
    } catch (err) {
      const fieldError = parseApiError(err);
      if (fieldError) {
        setErrors((prev) => ({ ...prev, ...fieldError }));
        enqueueSnackbar(LABELS.fixErrors, { variant: 'warning' });
      } else {
        enqueueSnackbar(LABELS.saveError, { variant: 'error' });
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <ModernPageHeader
        title={LABELS.add}
        icon={BusinessIcon}
        breadcrumbs={[
          { label: LABELS.list, path: '/employers' },
          { label: LABELS.add, path: '/employers/create' }
        ]}
        actions={
          <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/employers')} variant="outlined">
            {LABELS.back}
          </Button>
        }
      />

      <MainCard>
        <Box component="form" onSubmit={handleSubmit} noValidate>
          {/* Section: Basic Info */}
          <Typography variant="subtitle1" fontWeight={600} color="primary" sx={{ mb: '1.0rem' }}>
            المعلومات الأساسية
          </Typography>
          <Grid container spacing={2.5}>
            {/* Code */}
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label={LABELS.code}
                value={employer.code}
                error={!!errors.code}
                helperText={errors.code || ' '}
                FormHelperTextProps={{ sx: { whiteSpace: 'normal', wordBreak: 'break-word', minHeight: '1.25rem' } }}
                placeholder={LABELS.codePlaceholder}
                inputProps={{ dir: 'ltr', style: { textTransform: 'uppercase' } }}
                onChange={(e) => {
                  const v = e.target.value.toUpperCase();
                  setEmployer((prev) => ({ ...prev, code: v }));
                  const err = validateField('code', v);
                  setErrors((prev) => ({ ...prev, code: err || null }));
                  if (!err && v.trim().length >= 2) scheduleUniquenessCheck('code', v);
                }}
                InputProps={checking.code ? { endAdornment: <InputAdornment position="end"><CircularProgress size={16} /></InputAdornment> } : undefined}
              />
            </Grid>

            {/* Name */}
            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                label={LABELS.name}
                value={employer.name}
                onChange={handleChange('name')}
                error={!!errors.name}
                helperText={errors.name || ' '}
                FormHelperTextProps={{ sx: { whiteSpace: 'normal', wordBreak: 'break-word', minHeight: '1.25rem' } }}
                placeholder={LABELS.namePlaceholder}
                autoFocus
                InputProps={checking.name ? { endAdornment: <InputAdornment position="end"><CircularProgress size={16} /></InputAdornment> } : undefined}
              />
            </Grid>
          </Grid>

          <Divider sx={{ my: '1.5rem' }} />

          {/* Section: Contact Info */}
          <Typography variant="subtitle1" fontWeight={600} color="primary" sx={{ mb: '1.0rem' }}>
            معلومات التواصل
          </Typography>
          <Grid container spacing={2.5}>
            {/* Email */}
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label={LABELS.email}
                value={employer.email}
                onChange={handleChange('email')}
                error={!!errors.email}
                helperText={errors.email}
                placeholder={LABELS.emailPlaceholder}
                inputProps={{ dir: 'ltr' }}
              />
            </Grid>

            {/* Phone */}
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label={LABELS.phone}
                value={employer.phone}
                onChange={handleChange('phone')}
                error={!!errors.phone}
                helperText={errors.phone}
                placeholder={LABELS.phonePlaceholder}
                inputProps={{ dir: 'ltr' }}
              />
            </Grid>

            {/* Active Status */}
            <Grid size={{ xs: 12, sm: 12, md: 4 }}>
              <FormControlLabel
                control={<Switch checked={employer.active} onChange={handleChange('active')} color="primary" />}
                label={LABELS.active}
                sx={{ mt: 1 }}
              />
            </Grid>

            {/* Address */}
            <Grid size={12}>
              <TextField
                fullWidth
                label={LABELS.address}
                value={employer.address}
                onChange={handleChange('address')}
                error={!!errors.address}
                helperText={errors.address}
                placeholder={LABELS.addressPlaceholder}
                multiline
                rows={2}
              />
            </Grid>
          </Grid>

          {/* Form Actions */}
          <Divider sx={{ my: '1.5rem' }} />
          <Stack direction="row" spacing={2} justifyContent="flex-end">
            <Button variant="outlined" onClick={() => navigate('/employers')} disabled={saving}>
              {LABELS.cancel}
            </Button>
            <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={saving}>
              {saving ? LABELS.saving : LABELS.save}
            </Button>
          </Stack>
        </Box>
      </MainCard>
    </>
  );
};

export default EmployerCreate;

