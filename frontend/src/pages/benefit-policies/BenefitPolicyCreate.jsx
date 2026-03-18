import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Formik, Form } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';

// MUI Components
import {
  Grid,
  Button,
  CircularProgress,
  TextField,
  MenuItem,
  InputAdornment,
  Alert,
  Typography,
  Divider,
  Box,
  Stack
} from '@mui/material';
import { LoadingButton } from '@mui/lab';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';

// Icons
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import PolicyIcon from '@mui/icons-material/Policy';
import BusinessIcon from '@mui/icons-material/Business';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import DescriptionIcon from '@mui/icons-material/Description';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Services
import { createBenefitPolicy } from 'services/api/benefit-policies.service';
import { getEmployerSelectors } from 'services/api/employers.service';

/**
 * Validation Schema - Yup
 * Defines all validation rules for the Benefit Policy form
 */
const validationSchema = Yup.object().shape({
  name: Yup.string()
    .required('اسم الوثيقة مطلوب')
    .min(5, 'الاسم يجب أن يكون 5 أحرف على الأقل')
    .max(255, 'الاسم يجب أن لا يتجاوز 255 حرفاً'),

  employerOrgId: Yup.mixed().required('يجب اختيار الشريك (صاحب العمل)'),

  policyCode: Yup.string().nullable(),

  startDate: Yup.date().nullable().required('تاريخ البدء مطلوب').typeError('تاريخ غير صالح'),

  endDate: Yup.date()
    .nullable()
    .required('تاريخ الانتهاء مطلوب')
    .min(Yup.ref('startDate'), 'تاريخ الانتهاء يجب أن يكون بعد تاريخ البدء')
    .typeError('تاريخ غير صالح'),

  annualLimit: Yup.number()
    .required('السقف السنوي مطلوب')
    .positive('يجب أن يكون أكبر من صفر')
    .max(10000000, 'قيمة السقف السنوي كبيرة جداً'),

  defaultCoveragePercent: Yup.number().required('نسبة التغطية مطلوبة').min(0, 'النسبة لا تقل عن 0%').max(100, 'النسبة لا تزيد عن 100%'),

  status: Yup.string().required('الحالة مطلوبة'),

  description: Yup.string().max(1000, 'الوصف طويل جداً')
});

/**
 * Benefit Policy Create Page
 * Modern implementation using Formik, Yup, and Material UI v5
 */
const BenefitPolicyCreate = () => {
  const navigate = useNavigate();
  const [employers, setEmployers] = useState([]);
  const [loadingEmployers, setLoadingEmployers] = useState(true);
  const [generalError, setGeneralError] = useState(null);

  // Initial Form Values
  const initialValues = {
    name: '',
    policyCode: '',
    description: '',
    employerOrgId: '',
    startDate: dayjs(),
    endDate: dayjs().add(1, 'year'),
    annualLimit: '10000',
    defaultCoveragePercent: '80',
    notes: '',
    status: 'DRAFT'
  };

  // Fetch Employers Data Function
  const fetchEmployers = async () => {
    setLoadingEmployers(true);
    try {
      const data = await getEmployerSelectors();
      setEmployers(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch employers:', err);
    } finally {
      setLoadingEmployers(false);
    }
  };

  // Fetch Employers on Mount
  useEffect(() => {
    fetchEmployers();
  }, []);

  // Handle Form Submission
  const handleSubmit = async (values, { setSubmitting }) => {
    setGeneralError(null);
    try {
      // Transform Form Values to API Payload
      const payload = {
        name: values.name.trim(),
        policyCode: values.policyCode?.trim() || null,
        description: values.description?.trim() || null,
        employerOrgId: values.employerOrgId, // Assuming ID is stored directly
        startDate: values.startDate ? dayjs(values.startDate).format('YYYY-MM-DD') : null,
        endDate: values.endDate ? dayjs(values.endDate).format('YYYY-MM-DD') : null,
        annualLimit: parseFloat(values.annualLimit),
        defaultCoveragePercent: parseInt(values.defaultCoveragePercent, 10),
        notes: values.notes?.trim() || null,
        status: values.status
      };

      await createBenefitPolicy(payload);

      // Success - Navigate back
      navigate('/benefit-policies');
    } catch (err) {
      console.error('Create Policy Error:', err);
      const msg = err.response?.data?.message || err.message || 'فشل إنشاء وثيقة المنافع. يرجى المحاولة لاحقاً.';
      setGeneralError(msg);
      setSubmitting(false);
    }
  };

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title="إنشاء وثيقة منافع جديدة"
        subtitle="إدخال بيانات وثيقة التأمين والشروط المالية"
        icon={PolicyIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/dashboard' },
          { label: 'وثائق المنافع', path: '/benefit-policies' },
          { label: 'إنشاء وثيقة' }
        ]}
      />

      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <MainCard
          content={false}
          sx={{
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          {generalError && (
            <Box sx={{ p: '1.0rem', pb: 0 }}>
              <Alert severity="error" variant="outlined" onClose={() => setGeneralError(null)}>
                {generalError}
              </Alert>
            </Box>
          )}

          <Formik initialValues={initialValues} validationSchema={validationSchema} onSubmit={handleSubmit}>
            {({ values, errors, touched, handleChange, handleBlur, setFieldValue, isSubmitting }) => (
              <Form autoComplete="off" style={{ display: 'flex', flexDirection: 'column' }}>
                <Box sx={{ p: '1.0rem' }}>
                  <Grid container spacing={2}>

                    {/* ── Section 1: هوية الوثيقة ── */}
                    <Grid size={{ xs: 12 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                        <PolicyIcon fontSize="small" color="action" />
                        <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>هوية الوثيقة</Typography>
                      </Stack>
                      <Divider />
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label="رمز الوثيقة"
                        name="policyCode"
                        size="small"
                        value={values.policyCode || 'سيتم التوليد تلقائياً'}
                        disabled
                        helperText="يتم توليده تلقائياً عند الحفظ"
                        sx={{ '& .MuiInputBase-input.Mui-disabled': { WebkitTextFillColor: 'text.secondary', fontStyle: 'italic' } }}
                      />
                    </Grid>

                    <Grid size={{ xs: 12, md: 8 }}>
                      <TextField
                        fullWidth
                        label="اسم الوثيقة"
                        name="name"
                        size="small"
                        placeholder="مثال: وثيقة التأمين الصحي - شركة الواحة"
                        value={values.name}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.name && Boolean(errors.name)}
                        helperText={touched.name && errors.name}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <PolicyIcon fontSize="small" color="action" />
                            </InputAdornment>
                          )
                        }}
                      />
                    </Grid>

                    <Grid size={{ xs: 12, md: 8 }}>
                      <TextField
                        fullWidth
                        select
                        size="small"
                        label="الشريك (صاحب العمل)"
                        name="employerOrgId"
                        value={values.employerOrgId}
                        onChange={(e) => {
                          const selectedId = e.target.value;
                          setFieldValue('employerOrgId', selectedId);
                          // Auto-populate name from employer
                          if (!values.name) {
                            const emp = employers.find((em) => em.id === selectedId);
                            if (emp) setFieldValue('name', `وثيقة ${emp.label || emp.name}`);
                          }
                        }}
                        onBlur={handleBlur}
                        error={touched.employerOrgId && Boolean(errors.employerOrgId)}
                        helperText={(touched.employerOrgId && errors.employerOrgId) || 'اختر المؤسسة صاحبة الوثيقة'}
                        disabled={loadingEmployers}
                      >
                        {loadingEmployers ? (
                          <MenuItem value="" disabled>
                            <CircularProgress size={16} sx={{ mr: 1 }} /> جارٍ التحميل...
                          </MenuItem>
                        ) : employers.length > 0 ? (
                          employers.map((emp) => (
                            <MenuItem key={emp.id} value={emp.id} sx={{ fontSize: '0.8125rem' }}>
                              {emp.label || emp.name}
                            </MenuItem>
                          ))
                        ) : (
                          <MenuItem value="" disabled>لا يوجد شركاء متاحين</MenuItem>
                        )}
                      </TextField>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        select
                        label="حالة الوثيقة"
                        name="status"
                        size="small"
                        value={values.status}
                        onChange={handleChange}
                        onBlur={handleBlur}
                      >
                        <MenuItem value="DRAFT" sx={{ fontSize: '0.8125rem' }}>مسودة (Draft)</MenuItem>
                        <MenuItem value="ACTIVE" sx={{ fontSize: '0.8125rem' }}>نشط (Active)</MenuItem>
                        <MenuItem value="INACTIVE" sx={{ fontSize: '0.8125rem' }}>غير نشط (Inactive)</MenuItem>
                      </TextField>
                    </Grid>

                    {/* ── Section 2: مدة السريان ── */}
                    <Grid size={{ xs: 12 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5, mt: 1 }}>
                        <CalendarTodayIcon fontSize="small" color="action" />
                        <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>مدة السريان</Typography>
                      </Stack>
                      <Divider />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <DatePicker
                        label="تاريخ البدء *"
                        value={values.startDate}
                        onChange={(value) => setFieldValue('startDate', value)}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            size: 'small',
                            error: touched.startDate && Boolean(errors.startDate),
                            helperText: touched.startDate && errors.startDate
                          }
                        }}
                      />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <DatePicker
                        label="تاريخ الانتهاء *"
                        value={values.endDate}
                        onChange={(value) => setFieldValue('endDate', value)}
                        minDate={values.startDate || dayjs()}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            size: 'small',
                            error: touched.endDate && Boolean(errors.endDate),
                            helperText: touched.endDate && errors.endDate
                          }
                        }}
                      />
                    </Grid>

                    {/* ── Section 3: الحدود المالية ── */}
                    <Grid size={{ xs: 12 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                        <AttachMoneyIcon fontSize="small" color="action" />
                        <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>الحدود المالية</Typography>
                      </Stack>
                      <Divider />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="السقف السنوي"
                        name="annualLimit"
                        type="number"
                        size="small"
                        value={values.annualLimit}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.annualLimit && Boolean(errors.annualLimit)}
                        helperText={touched.annualLimit && errors.annualLimit}
                        InputProps={{ endAdornment: <InputAdornment position="end">د.ل</InputAdornment> }}
                      />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="نسبة التغطية"
                        name="defaultCoveragePercent"
                        type="number"
                        size="small"
                        value={values.defaultCoveragePercent}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.defaultCoveragePercent && Boolean(errors.defaultCoveragePercent)}
                        helperText={touched.defaultCoveragePercent && errors.defaultCoveragePercent}
                        InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                      />
                    </Grid>

                    {/* ── Section 4: ملاحظات ── */}
                    <Grid size={{ xs: 12 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                        <DescriptionIcon fontSize="small" color="action" />
                        <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>ملاحظات</Typography>
                      </Stack>
                      <Divider />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                      <TextField
                        fullWidth
                        multiline
                        rows={2}
                        label="ملاحظات"
                        name="notes"
                        size="small"
                        value={values.notes}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        placeholder="أضف وصفاً تفصيلياً أو ملاحظات إضافية..."
                      />
                    </Grid>

                  </Grid>
                </Box>

                {/* Sticky Footer Actions */}
                <Divider />
                <Box sx={{ p: '1.0rem', display: 'flex', justifyContent: 'flex-end', gap: '1.0rem', bgcolor: 'background.default' }}>
                  <Button
                    variant="outlined"
                    color="inherit"
                    onClick={() => navigate('/benefit-policies')}
                    startIcon={<CancelIcon />}
                    disabled={isSubmitting}
                  >
                    إلغاء
                  </Button>
                  <LoadingButton
                    type="submit"
                    variant="contained"
                    loading={isSubmitting}
                    loadingPosition="start"
                    startIcon={<SaveIcon />}
                    sx={{ minWidth: '8.75rem' }}
                  >
                    حفظ الوثيقة
                  </LoadingButton>
                </Box>
              </Form>
            )}
          </Formik>
        </MainCard>
      </LocalizationProvider>
    </>
  );
};

export default BenefitPolicyCreate;
