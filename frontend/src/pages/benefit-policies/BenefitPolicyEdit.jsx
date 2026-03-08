import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Formik, Form, useFormikContext } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';

// MUI Components
import {
  Grid,
  Button,
  Stack,
  TextField,
  MenuItem,
  InputAdornment,
  Alert,
  Typography,
  Divider,
  CircularProgress,
  Box,
  FormControlLabel,
  Switch
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
import EditIcon from '@mui/icons-material/Edit';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Services
import { getBenefitPolicyById, updateBenefitPolicy, getBenefitPoliciesByEmployer } from 'services/api/benefit-policies.service';
import { getEmployerSelectors } from 'services/api/employers.service';

/**
 * Validation Schema - Yup
 * Defines all validation rules for the Benefit Policy edit form
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
 * Helper component to handle form value changes with useEffect
 * This is needed because hooks cannot be called inside Formik's render callback
 */
const FormValuesEffect = ({ checkOverlap, policyId }) => {
  const { values } = useFormikContext();

  useEffect(() => {
    if (values.employerOrgId && values.startDate && values.endDate && values.status === 'ACTIVE') {
      const timer = setTimeout(() => {
        checkOverlap(values.employerOrgId, values.startDate, values.endDate, policyId);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [values.employerOrgId, values.startDate, values.endDate, values.status, checkOverlap, policyId]);

  return null;
};

/**
 * Benefit Policy Edit Page
 * Allows editing existing benefit policy with validation and overlap checking
 */
const BenefitPolicyEdit = () => {
  const navigate = useNavigate();
  const { id } = useParams();

  const [policy, setPolicy] = useState(null);
  const [employers, setEmployers] = useState([]);
  const [loadingPolicy, setLoadingPolicy] = useState(true);
  const [loadingEmployers, setLoadingEmployers] = useState(true);
  const [generalError, setGeneralError] = useState(null);
  const [overlapWarning, setOverlapWarning] = useState(null);

  // Fetch Policy Data
  useEffect(() => {
    let mounted = true;

    const fetchPolicy = async () => {
      try {
        setLoadingPolicy(true);
        const data = await getBenefitPolicyById(id);
        if (mounted) {
          setPolicy(data);
        }
      } catch (err) {
        console.error('Failed to fetch policy:', err);
        if (mounted) {
          const msg = err.response?.data?.message || err.message || 'فشل في تحميل بيانات الوثيقة';
          setGeneralError(msg);
        }
      } finally {
        if (mounted) setLoadingPolicy(false);
      }
    };

    if (id) {
      fetchPolicy();
    }

    return () => {
      mounted = false;
    };
  }, [id]);

  // Fetch Employers Data
  useEffect(() => {
    let mounted = true;

    const fetchSelectors = async () => {
      try {
        const data = await getEmployerSelectors();
        if (mounted) {
          setEmployers(Array.isArray(data) ? data : []);
        }
      } catch (err) {
        console.error('Failed to fetch employers:', err);
      } finally {
        if (mounted) setLoadingEmployers(false);
      }
    };

    fetchSelectors();

    return () => {
      mounted = false;
    };
  }, []);

  // Check for overlapping policies
  const checkOverlap = async (employerOrgId, startDate, endDate, currentPolicyId) => {
    try {
      const policies = await getBenefitPoliciesByEmployer(employerOrgId);

      const overlapping = policies.filter((p) => {
        // Skip the current policy being edited
        if (p.id === currentPolicyId) return false;

        // Only check active policies
        if (p.status !== 'ACTIVE') return false;

        // Check date overlap
        const pStart = dayjs(p.startDate);
        const pEnd = dayjs(p.endDate);
        const newStart = dayjs(startDate);
        const newEnd = dayjs(endDate);

        return (newStart.isBefore(pEnd) || newStart.isSame(pEnd)) && (newEnd.isAfter(pStart) || newEnd.isSame(pStart));
      });

      if (overlapping.length > 0) {
        const names = overlapping.map((p) => p.name).join('، ');
        setOverlapWarning(`تحذير: توجد وثائق نشطة أخرى في نفس الفترة: ${names}`);
      } else {
        setOverlapWarning(null);
      }
    } catch (err) {
      console.error('Failed to check overlap:', err);
      // Silent fail - don't block submission
    }
  };

  // Handle Form Submission
  const handleSubmit = async (values, { setSubmitting }) => {
    setGeneralError(null);

    try {
      // Transform Form Values to API Payload
      const payload = {
        name: values.name.trim(),
        policyCode: values.policyCode?.trim() || null,
        description: values.description?.trim() || null,
        employerOrgId: values.employerOrgId,
        startDate: values.startDate ? dayjs(values.startDate).format('YYYY-MM-DD') : null,
        endDate: values.endDate ? dayjs(values.endDate).format('YYYY-MM-DD') : null,
        annualLimit: parseFloat(values.annualLimit),
        defaultCoveragePercent: parseInt(values.defaultCoveragePercent, 10),
        notes: values.notes?.trim() || null,
        status: values.status
      };

      await updateBenefitPolicy(id, payload);

      // Success - Navigate back
      navigate('/benefit-policies');
    } catch (err) {
      console.error('Update Policy Error:', err);
      const msg = err.response?.data?.message || err.message || 'فشل تحديث وثيقة المنافع. يرجى المحاولة لاحقاً.';
      setGeneralError(msg);
      setSubmitting(false);
    }
  };

  // Show loading spinner while fetching data
  if (loadingPolicy) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  // Show error if policy not found
  if (!policy && !loadingPolicy) {
    return (
      <Alert severity="error" sx={{ mt: 2 }}>
        الوثيقة غير موجودة أو حدث خطأ في التحميل
      </Alert>
    );
  }

  // Initial Form Values from fetched policy
  const initialValues = {
    name: policy?.name || '',
    policyCode: policy?.policyCode || '',
    description: policy?.description || '',
    employerOrgId: policy?.employerOrgId || '',
    startDate: policy?.startDate ? dayjs(policy.startDate) : dayjs(),
    endDate: policy?.endDate ? dayjs(policy.endDate) : dayjs().add(1, 'year'),
    annualLimit: policy?.annualLimit || '10000',
    defaultCoveragePercent: policy?.defaultCoveragePercent || 80,
    notes: policy?.notes || '',
    status: policy?.status || 'DRAFT'
  };

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title="تعديل وثيقة المنافع"
        subtitle={`تحديث بيانات: ${policy?.name || ''}`}
        icon={EditIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/dashboard' }, { label: 'وثائق المنافع', path: '/benefit-policies' }, { label: 'تعديل' }]}
      />

      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <MainCard
          content={false}
          sx={{
            height: 'calc(100vh - 210px)',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          {generalError && (
            <Box sx={{ p: 2, pb: 0 }}>
              <Alert severity="error" variant="outlined" onClose={() => setGeneralError(null)}>
                {generalError}
              </Alert>
            </Box>
          )}

          {overlapWarning && (
            <Box sx={{ p: 2, pb: 0 }}>
              <Alert severity="warning" variant="outlined" onClose={() => setOverlapWarning(null)}>
                {overlapWarning}
              </Alert>
            </Box>
          )}

          <Formik initialValues={initialValues} validationSchema={validationSchema} onSubmit={handleSubmit} enableReinitialize>
            {({ values, errors, touched, handleChange, handleBlur, setFieldValue, isSubmitting }) => (
              <Form autoComplete="off" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <FormValuesEffect checkOverlap={checkOverlap} policyId={policy?.id} />

                <Box sx={{ flex: 1, overflowY: 'auto', p: 2 }}>
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
                        size="small"
                        name="policyCode"
                        value={values.policyCode}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.policyCode && Boolean(errors.policyCode)}
                        helperText={(touched.policyCode && errors.policyCode) || 'اتركه فارغاً للتوليد التلقائي'}
                        InputProps={{ readOnly: Boolean(policy?.policyCode) }}
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
                        onChange={handleChange}
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
                            <MenuItem key={emp.id} value={emp.id} sx={{ fontSize: '13px' }}>
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
                        size="small"
                        label="حالة الوثيقة"
                        name="status"
                        value={values.status}
                        onChange={handleChange}
                        onBlur={handleBlur}
                      >
                        <MenuItem value="DRAFT" sx={{ fontSize: '13px' }}>مسودة (Draft)</MenuItem>
                        <MenuItem value="ACTIVE" sx={{ fontSize: '13px' }}>نشط (Active)</MenuItem>
                        <MenuItem value="INACTIVE" sx={{ fontSize: '13px' }}>غير نشط (Inactive)</MenuItem>
                      </TextField>
                    </Grid>

                    {/* ── Section 2: مدة السريان ── */}
                    <Grid size={{ xs: 12 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
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

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="الحد للفرد"
                        name="perMemberLimit"
                        type="number"
                        size="small"
                        value={values.perMemberLimit}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.perMemberLimit && Boolean(errors.perMemberLimit)}
                        helperText={(touched.perMemberLimit && errors.perMemberLimit) || 'اختياري'}
                        InputProps={{ endAdornment: <InputAdornment position="end">د.ل</InputAdornment> }}
                      />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="الحد للعائلة"
                        name="perFamilyLimit"
                        type="number"
                        size="small"
                        value={values.perFamilyLimit}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        error={touched.perFamilyLimit && Boolean(errors.perFamilyLimit)}
                        helperText={(touched.perFamilyLimit && errors.perFamilyLimit) || 'اختياري'}
                        InputProps={{ endAdornment: <InputAdornment position="end">د.ل</InputAdornment> }}
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
                        label="ملاحظات توضيحية"
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
                <Box sx={{ p: 2, display: 'flex', justifyContent: 'flex-end', gap: 2, bgcolor: 'background.default' }}>
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
                    sx={{ minWidth: 140 }}
                  >
                    حفظ التعديلات
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

export default BenefitPolicyEdit;
