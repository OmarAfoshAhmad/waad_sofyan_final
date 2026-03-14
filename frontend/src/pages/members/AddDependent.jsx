/**
 * Add Dependent Page
 *
 * Adds a new Dependent to an existing Principal member.
 * Uses new Unified Architecture (single Member entity with parent_id).
 *
 * @module AddDependent
 * @since 2026-01-12
 */

import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
  FormControl,
  InputLabel,
  Select,
  FormHelperText,
  CircularProgress,
  Alert,
  Box,
  Chip,
  Paper
} from '@mui/material';
import { Save as SaveIcon, ArrowBack as ArrowBackIcon, PersonAdd as PersonAddIcon } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import MemberAvatar from 'components/tba/MemberAvatar';
import { getMember, addDependent, uploadPhoto, RELATIONSHIPS, GENDERS } from 'services/api/unified-members.service';
import { openSnackbar } from 'api/snackbar';

/**
 * Add Dependent Component
 */
const AddDependent = () => {
  const navigate = useNavigate();
  const { id: principalId } = useParams();

  // Loading & Error States
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});
  const [fetchError, setFetchError] = useState(null);

  // Principal Member Info
  const [principal, setPrincipal] = useState(null);

  // Dependent Form
  const [form, setForm] = useState({
    fullName: '',
    nationalNumber: '',
    birthDate: null,
    gender: '',
    relationship: '',
    photoFile: null,
    photoPreview: null
  });

  // Fetch principal data
  useEffect(() => {
    fetchPrincipalData();
  }, [principalId]);

  const fetchPrincipalData = async () => {
    try {
      setLoading(true);
      const data = await getMember(principalId);

      if (data.type !== 'PRINCIPAL') {
        setFetchError('يمكن إضافة تابع فقط للمنتفع الرئيسي');
        return;
      }

      setPrincipal(data);
    } catch (error) {
      console.error('Error fetching principal:', error);
      setFetchError(error.response?.data?.message || 'فشل في تحميل بيانات المنتفع الرئيسي');
    } finally {
      setLoading(false);
    }
  };

  // Handle form field changes
  const handleFieldChange = (field) => (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));

    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  // Handle date changes
  const handleDateChange = (field) => (date) => {
    setForm((prev) => ({ ...prev, [field]: date }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  // Validate form
  const validateForm = () => {
    const newErrors = {};

    if (!form.fullName.trim()) {
      newErrors.fullName = 'الاسم الكامل مطلوب';
    }

    if (!form.birthDate) {
      newErrors.birthDate = 'تاريخ الميلاد مطلوب';
    }

    if (!form.gender) {
      newErrors.gender = 'الجنس مطلوب';
    }

    if (!form.relationship) {
      newErrors.relationship = 'صلة القرابة مطلوبة';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // Handle form submission
  const handleSubmit = async () => {
    if (!validateForm()) {
      openSnackbar({
        open: true,
        message: 'يرجى تصحيح الأخطاء في النموذج',
        variant: 'alert',
        alert: { color: 'error' }
      });
      return;
    }

    try {
      setSaving(true);

      const payload = {
        fullName: form.fullName.trim(),
        nationalNumber: form.nationalNumber?.trim() || null,
        birthDate: form.birthDate ? dayjs(form.birthDate).format('YYYY-MM-DD') : null,
        gender: form.gender,
        relationship: form.relationship
      };

      console.log('Adding dependent with payload:', payload);

      const response = await addDependent(principalId, payload);
      const createdMember = response?.data || response;

      // Upload photo if selected
      if (form.photoFile && createdMember?.id) {
        try {
          await uploadPhoto(createdMember.id, form.photoFile);
        } catch (uploadError) {
          console.error('Photo upload failed:', uploadError);
          // We don't fail the whole process, just warn
        }
      }

      openSnackbar({
        open: true,
        message: 'تمت إضافة التابع بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      navigate(`/members/${principalId}`);
    } catch (error) {
      console.error('Error adding dependent:', error);

      const errorMessage = error.response?.data?.message || error.message || 'خطأ في إضافة التابع';

      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setSaving(false);
    }
  };

  // Loading state
  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  // Error state
  if (fetchError) {
    return (
      <MainCard>
        <Alert severity="error" sx={{ mb: '1.0rem' }}>
          {fetchError}
        </Alert>
        <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/members')}>
          العودة للقائمة
        </Button>
      </MainCard>
    );
  }

  return (
    <>
      <ModernPageHeader
        title="إضافة تابع جديد"
        subtitle={`إضافة تابع للمنتفع الرئيسي: ${principal?.fullName}`}
        icon={<PersonAddIcon />}
        breadcrumbs={[
          { label: 'الرئيسية', href: '/' },
          { label: 'المنتفعين', href: '/members' },
          { label: principal?.fullName, href: `/members/${principalId}` },
          { label: 'إضافة تابع' }
        ]}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate(`/members/${principalId}`)}>
            رجوع
          </Button>
        }
      />

      <Stack spacing={3}>
        {/* Help Info */}
        <Alert severity="info" sx={{ '& .MuiAlert-message': { width: '100%' } }}>
          <Typography variant="body2">يمكنك إضافة التابعين الآن. التابعون لا يملكون Barcode خاص بهم.</Typography>
        </Alert>

        {/* Principal Info Summary (Optional, but good for context) */}
        <MainCard content={false} sx={{ p: '1.0rem', bgcolor: 'primary.lighter', border: '1px dashed', borderColor: 'primary.main' }}>
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="subtitle2" color="primary.main">
              <strong>إضافة تابع للمنتفع الرئيسي:</strong> {principal?.fullName}
            </Typography>
            <Chip label={principal?.cardNumber} size="small" color="primary" />
          </Stack>
        </MainCard>

        {/* Dependent Form - Matches UnifiedMemberCreate Style */}
        <Paper variant="outlined" sx={{ p: '1.5rem', bgcolor: 'background.paper' }}>
          <Typography variant="subtitle2" sx={{ mb: '1.5rem', fontWeight: 'bold' }}>
            إضافة تابع جديد
          </Typography>

          <Grid container spacing={2} alignItems="flex-start">
            {/* Full Name - Wider */}
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                required
                label="الاسم الكامل"
                value={form.fullName}
                onChange={handleFieldChange('fullName')}
                error={!!errors.fullName}
                helperText={errors.fullName}
                size="small"
                sx={{ minWidth: '13.75rem' }}
              />
            </Grid>

            {/* Relationship */}
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth required error={!!errors.relationship} size="small" sx={{ minWidth: '9.375rem' }}>
                <InputLabel>القرابة</InputLabel>
                <Select value={form.relationship} onChange={handleFieldChange('relationship')} label="القرابة">
                  {Object.entries(RELATIONSHIPS).map(([key, value]) => (
                    <MenuItem key={key} value={value}>
                      {value === 'WIFE'
                        ? 'زوجة'
                        : value === 'HUSBAND'
                          ? 'زوج'
                          : value === 'SON'
                            ? 'ابن'
                            : value === 'DAUGHTER'
                              ? 'ابنة'
                              : value === 'FATHER'
                                ? 'أب'
                                : value === 'MOTHER'
                                  ? 'أم'
                                  : value === 'BROTHER'
                                    ? 'أخ'
                                    : value === 'SISTER'
                                      ? 'أخت'
                                      : value}
                    </MenuItem>
                  ))}
                </Select>
                {errors.relationship && <FormHelperText>{errors.relationship}</FormHelperText>}
              </FormControl>
            </Grid>

            {/* Gender */}
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth required error={!!errors.gender} size="small" sx={{ minWidth: '8.125rem' }}>
                <InputLabel>الجنس</InputLabel>
                <Select value={form.gender} onChange={handleFieldChange('gender')} label="الجنس">
                  {Object.entries(GENDERS).map(([key, value]) => (
                    <MenuItem key={key} value={value}>
                      {value === 'MALE' ? 'ذكر' : value === 'FEMALE' ? 'أنثى' : 'غير محدد'}
                    </MenuItem>
                  ))}
                </Select>
                {errors.gender && <FormHelperText>{errors.gender}</FormHelperText>}
              </FormControl>
            </Grid>

            {/* Birth Date */}
            <Grid size={{ xs: 12, md: 2 }}>
              <DatePicker
                label="تاريخ الميلاد *"
                value={form.birthDate}
                onChange={handleDateChange('birthDate')}
                maxDate={dayjs()}
                slotProps={{
                  textField: {
                    fullWidth: true,
                    size: 'small',
                    error: !!errors.birthDate,
                    helperText: errors.birthDate,
                    sx: { minWidth: '9.375rem' }
                  }
                }}
              />
            </Grid>

            {/* National ID */}
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                fullWidth
                label="الرقم الوطني"
                value={form.nationalNumber}
                onChange={handleFieldChange('nationalNumber')}
                placeholder="اختياري"
                size="small"
                sx={{ minWidth: '9.375rem' }}
              />
            </Grid>

            {/* Photo Upload for Dependent */}
            <Grid sx={{ mt: '1.0rem' }} size={12}>
              <Paper variant="outlined" sx={{ p: '1.0rem', bgcolor: 'grey.50', borderStyle: 'dashed' }}>
                <Stack direction="row" spacing={3} alignItems="center">
                  <Box position="relative">
                    <MemberAvatar
                      member={{
                        fullName: form.fullName,
                        photoUrl: form.photoPreview
                      }}
                      size={100}
                      sx={{ mb: '1.0rem', cursor: 'pointer', border: '2px solid', borderColor: 'divider' }}
                      onClick={() => document.getElementById('dep-photo-upload').click()}
                    />
                    <input
                      accept="image/*"
                      id="dep-photo-upload"
                      type="file"
                      hidden
                      onChange={(e) => {
                        if (e.target.files && e.target.files[0]) {
                          const file = e.target.files[0];
                          setForm((prev) => ({
                            ...prev,
                            photoFile: file,
                            photoPreview: URL.createObjectURL(file)
                          }));
                        }
                      }}
                    />
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                      الصورة الشخصية للتابع
                    </Typography>
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                      يفضل أن تكون الصورة واضحة وبخلفية بيضاء
                    </Typography>
                    <Button variant="outlined" size="small" onClick={() => document.getElementById('dep-photo-upload').click()}>
                      اختيار صورة
                    </Button>
                  </Box>
                </Stack>
              </Paper>
            </Grid>

            {/* Action Button */}
            <Grid sx={{ mt: 1 }} size={{ xs: 12, md: 12 }}>
              <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button
                  variant="contained"
                  startIcon={saving ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
                  onClick={handleSubmit}
                  disabled={saving}
                  sx={{ height: '2.5rem', px: '2.0rem' }}
                >
                  إضافة التابع
                </Button>
              </Box>
            </Grid>
          </Grid>
        </Paper>
      </Stack>
    </>
  );
};

export default AddDependent;
