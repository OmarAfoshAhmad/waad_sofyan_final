/**
 * Unified Member Edit Page
 *
 * Edits a Principal or Dependent member.
 * Matches UnifiedMemberCreate layout (Tabs + Photo inside Tab 0).
 *
 * @module UnifiedMemberEdit
 * @since 2026-01-31
 */

import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Grid,
  MenuItem,
  Stack,
  TextField,
  FormControl,
  InputLabel,
  Select,
  FormHelperText,
  CircularProgress,
  Alert,
  Box,
  Tabs,
  Tab,
  Paper,
  Avatar,
  Typography,
  Divider,
  IconButton,
  Tooltip,
  FormControlLabel,
  Switch,
  Badge
} from '@mui/material';
import {
  Save as SaveIcon,
  ArrowBack as ArrowBackIcon,
  Person as PersonIcon,
  Badge as BadgeIcon,
  FamilyRestroom as FamilyRestroomIcon,
  ContactPhone as ContactPhoneIcon,
  Delete as DeleteIcon,
  PhotoCamera as PhotoCameraIcon,
  Edit as EditIcon,
  CloudUpload as CloudUploadIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { getMember, updateMember, uploadPhoto, deletePhoto, RELATIONSHIPS, GENDERS } from 'services/api/unified-members.service';
import axiosClient from 'utils/axios';
import { openSnackbar } from 'api/snackbar';
import { MemberAvatar } from '../../components/tba';

/**
 * Unified Member Edit Component
 */
const UnifiedMemberEdit = () => {
  const navigate = useNavigate();
  const { id } = useParams();

  // Tab State
  const [tabValue, setTabValue] = useState(0);
  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  const menuProps = {
    PaperProps: {
      sx: {
        '& .MuiMenuItem-root': { fontSize: '0.75rem' },
        maxHeight: '18.75rem',
        minWidth: '12.5rem'
      }
    }
  };

  // Loading & States
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});
  const [fetchError, setFetchError] = useState(null);

  // Form State
  const [form, setForm] = useState({
    fullName: '',
    nationalNumber: '',
    birthDate: null,
    gender: '',
    nationality: 'ليبي',
    phone: '',
    email: '',
    address: '',
    relationship: '',
    employerId: '',
    employeeNumber: '',
    joinDate: null,
    occupation: '',
    status: 'ACTIVE',
    startDate: null,
    endDate: null,
    notes: '',
    photoPreview: null,
    photoFile: null,
    hasExistingPhoto: false
  });

  // Lookup Data
  const [employers, setEmployers] = useState([]);
  const [benefitPolicies, setBenefitPolicies] = useState([]);
  const [isPrincipal, setIsPrincipal] = useState(false);

  /**
   * Helper to check if a tab has validation errors
   */
  const getTabErrorCount = (index) => {
    if (index === 0) {
      return (
        (errors.fullName ? 1 : 0) +
        (errors.birthDate ? 1 : 0) +
        (errors.gender ? 1 : 0) +
        (errors.nationalNumber ? 1 : 0) +
        (errors.relationship ? 1 : 0)
      );
    }
    if (index === 1) {
      return errors.employerId ? 1 : 0;
    }
    if (index === 2) {
      return (errors.phone ? 1 : 0) + (errors.email ? 1 : 0);
    }
    return 0;
  };

  useEffect(() => {
    fetchMemberData();
    fetchLookupData();
  }, [id]);

  const fetchMemberData = async () => {
    try {
      setLoading(true);
      const data = await getMember(id);

      const isPrinc = data.type === 'PRINCIPAL';
      setIsPrincipal(isPrinc);

      setForm({
        fullName: data.fullName || '',
        nationalNumber: data.nationalNumber || '',
        birthDate: data.birthDate ? dayjs(data.birthDate) : null,
        gender: data.gender || '',
        nationality: data.nationality || 'ليبي',
        phone: data.phone || '',
        email: data.email || '',
        address: data.address || '',
        relationship: data.relationship || '',
        employerId: data.employerId || '',
        employeeNumber: data.employeeNumber || '',
        joinDate: data.joinDate ? dayjs(data.joinDate) : null,
        occupation: data.occupation || '',
        status: data.status || 'ACTIVE',
        startDate: data.startDate ? dayjs(data.startDate) : null,
        endDate: data.endDate ? dayjs(data.endDate) : null,
        notes: data.notes || '',
        photoPreview: data.photoUrl
          ? `${data.photoUrl}?t=${new Date().getTime()}`
          : data.profilePhotoPath
            ? `/api/unified-members/${id}/photo?t=${new Date().getTime()}`
            : null,
        hasExistingPhoto: !!data.profilePhotoPath
      });
    } catch (error) {
      console.error('Error fetching member:', error);
      setFetchError('فشل في تحميل بيانات المنتفع');
    } finally {
      setLoading(false);
    }
  };

  const fetchLookupData = async () => {
    try {
      const [orgsRes, policiesRes] = await Promise.all([
        axiosClient.get('/employers/selectors'),
        axiosClient.get('/benefit-policies', { params: { size: 1000 } })
      ]);
      setEmployers(orgsRes.data?.data || []);
      setBenefitPolicies(policiesRes.data?.data?.content || []);
    } catch (error) {
      console.error('Error fetching lookup data:', error);
    }
  };

  /**
   * Handle form field changes
   */
  const handleChange = (field) => (eventOrValue) => {
    let value;
    if (eventOrValue === null || eventOrValue === undefined) {
      value = null;
    } else if (eventOrValue?.target !== undefined) {
      value = eventOrValue.target.value;
    } else {
      value = eventOrValue;
    }

    if ((field === 'nationalNumber' || field === 'phone' || field === 'employeeNumber') && typeof value === 'string') {
      value = value.replace(/\D/g, '');
      if (field === 'nationalNumber' && value.length > 12) return;
      if (field === 'phone' && value.length > 10) return;
    }

    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  /**
   * Photo Management
   */
  const handlePhotoSelect = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setForm((prev) => ({
        ...prev,
        photoFile: file,
        photoPreview: URL.createObjectURL(file)
      }));
    }
  };

  const handleDeletePhoto = async () => {
    try {
      await deletePhoto(id);
      setForm((prev) => ({
        ...prev,
        photoFile: null,
        photoPreview: null,
        hasExistingPhoto: false
      }));
      openSnackbar({ message: 'تم حذف الصورة بنجاح', variant: 'alert', alert: { color: 'success' } });
    } catch (error) {
      console.error('Photo delete failed', error);
      openSnackbar({ message: 'فشل حذف الصورة', variant: 'alert', alert: { color: 'error' } });
    }
  };

  const handleStatusToggle = (event) => {
    setForm((prev) => ({ ...prev, status: event.target.checked ? 'ACTIVE' : 'SUSPENDED' }));
  };

  /**
   * Validation
   */
  const validateForm = () => {
    const newErrors = {};
    if (!form.fullName?.trim()) newErrors.fullName = 'الاسم الكامل مطلوب';
    if (!form.birthDate) newErrors.birthDate = 'تاريخ الميلاد مطلوب';
    if (!form.gender) newErrors.gender = 'الجنس مطلوب';

    if (isPrincipal && !form.employerId) newErrors.employerId = 'جهة العمل مطلوبة';
    if (!isPrincipal && !form.relationship) newErrors.relationship = 'صلة القرابة مطلوبة';

    if (form.nationalNumber && form.nationalNumber.length !== 12) {
      newErrors.nationalNumber = 'الرقم الوطني يجب أن يتكون من 12 خانة';
    }

    if (form.phone && !/^(091|092|094|093|095|096)\d{7}$/.test(form.phone)) {
      newErrors.phone = 'رقم الهاتف غير صحيح';
    }

    setErrors(newErrors);

    if (newErrors.fullName || newErrors.birthDate || newErrors.gender || newErrors.nationalNumber || newErrors.relationship) {
      setTabValue(0);
    } else if (newErrors.employerId) {
      setTabValue(1);
    } else if (newErrors.phone || newErrors.email) {
      setTabValue(2);
    }

    return Object.keys(newErrors).length === 0;
  };

  /**
   * Submit
   */
  const handleSubmit = async () => {
    if (!validateForm()) return;

    try {
      setSaving(true);
      const payload = {
        fullName: form.fullName.trim(),
        nationalNumber: form.nationalNumber?.trim() || null,
        birthDate: form.birthDate ? dayjs(form.birthDate).format('YYYY-MM-DD') : null,
        gender: form.gender || 'UNDEFINED',
        nationality: form.nationality || 'ليبي',
        phone: form.phone || null,
        email: form.email || null,
        address: form.address || null,
        employeeNumber: form.employeeNumber || null,
        joinDate: form.joinDate ? dayjs(form.joinDate).format('YYYY-MM-DD') : null,
        occupation: form.occupation || null,
        status: form.status || 'ACTIVE',
        startDate: form.startDate ? dayjs(form.startDate).format('YYYY-MM-DD') : null,
        endDate: form.endDate ? dayjs(form.endDate).format('YYYY-MM-DD') : null,
        notes: form.notes || null
      };

      if (isPrincipal) {
        payload.employerId = form.employerId;
      } else {
        payload.relationship = form.relationship;
      }

      await updateMember(id, payload);

      if (form.photoFile) {
        try {
          await uploadPhoto(id, form.photoFile);
        } catch (photoError) {
          console.error('Photo upload failed but member data was saved:', photoError);
          openSnackbar({
            message: 'تم حفظ البيانات بنجاح، ولكن فشل تحميل الصورة',
            variant: 'alert',
            alert: { color: 'warning' }
          });
          navigate('/members');
          return;
        }
      }

      openSnackbar({ message: 'تم تحديث بيانات المنتفع بنجاح', variant: 'alert', alert: { color: 'success' } });
      setTimeout(() => {
        navigate('/members');
      }, 500);
    } catch (error) {
      console.error('Error updating member:', error);
      openSnackbar({
        message: error.response?.data?.message || 'خطأ في تحديث البيانات',
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading)
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  if (fetchError)
    return (
      <MainCard>
        <Alert severity="error">{fetchError}</Alert>
        <Button variant="outlined" sx={{ mt: '1.0rem' }} onClick={() => navigate('/members')}>
          رجوع
        </Button>
      </MainCard>
    );

  return (
    <>
      <ModernPageHeader
        title={`تعديل بيانات ${isPrincipal ? 'المنتفع الرئيسي' : 'المنتفع التابع'}`}
        subtitle={form.fullName}
        icon={<EditIcon />}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/members')}>
            رجوع
          </Button>
        }
      />

      <MainCard
        content={false}
        sx={{
          height: 'calc(100vh - 180px)',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'grey.50' }}>
          <Tabs
            value={tabValue}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              minHeight: '3.0rem',
              '& .MuiTab-root': {
                minHeight: '3.0rem',
                fontSize: '0.8125rem',
                px: '1.5rem',
                '&.Mui-selected': { color: 'primary.main', bgcolor: 'rgba(var(--palette-primary-mainChannel) / 0.15)', fontWeight: 600 }
              },
              '& .MuiTabs-indicator': {
                backgroundColor: 'primary.main',
                height: '0.1875rem',
                borderRadius: '3px 3px 0 0'
              }
            }}
          >
            <Tab
              label={
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <span>البيانات الشخصية</span>
                  {getTabErrorCount(0) > 0 && <span style={{ color: '#f44336', fontSize: '1.0rem' }}>●</span>}
                </Stack>
              }
              icon={<PersonIcon />}
              iconPosition="start"
              sx={{ color: getTabErrorCount(0) > 0 ? 'error.main' : 'inherit' }}
            />
            <Tab
              label={
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <span>{isPrincipal ? 'بيانات العمل' : 'صلة القرابة'}</span>
                  {getTabErrorCount(1) > 0 && <span style={{ color: '#f44336', fontSize: '1.0rem' }}>●</span>}
                </Stack>
              }
              icon={isPrincipal ? <BadgeIcon /> : <FamilyRestroomIcon />}
              iconPosition="start"
              sx={{ color: getTabErrorCount(1) > 0 ? 'error.main' : 'inherit' }}
            />
            <Tab
              label={
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <span>معلومات الاتصال</span>
                  {getTabErrorCount(2) > 0 && <span style={{ color: '#f44336', fontSize: '1.0rem' }}>●</span>}
                </Stack>
              }
              icon={<ContactPhoneIcon />}
              iconPosition="start"
              sx={{ color: getTabErrorCount(2) > 0 ? 'error.main' : 'inherit' }}
            />
          </Tabs>
        </Box>

        {Object.keys(errors).length > 0 && (
          <Box sx={{ px: '1.5rem', pt: '1.0rem' }}>
            <Alert
              severity="error"
              variant="outlined"
              sx={{
                bgcolor: 'error.lighter',
                borderColor: 'error.light',
                '& .MuiAlert-message': { fontWeight: 600, fontSize: '0.8125rem' }
              }}
            >
              توجد أخطاء في المدخلات؛ يرجى مراجعة التبويبات المميزة باللون الأحمر (عدد الحقول المعيبة: {Object.keys(errors).length})
            </Alert>
          </Box>
        )}

        <Box sx={{ flex: 1, overflowY: 'auto', p: '1.5rem' }}>
          {/* Tab 0: Personal Info */}
          <div role="tabpanel" hidden={tabValue !== 0}>
            {tabValue === 0 && (
              <Grid container spacing={3}>
                {/* Right Column: Fields (Occupies more space, comes first in RTL) */}
                <Grid size={{ xs: 12, md: 9 }}>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Alert severity="info" sx={{ mb: '1.0rem', '& .MuiAlert-message': { fontSize: '0.75rem' } }}>
                        يتم تحديث رقم البطاقة والباركود آلياً عند الحفظ إذا لزم الأمر.
                      </Alert>
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        required
                        label="الاسم الكامل"
                        value={form.fullName}
                        onChange={handleChange('fullName')}
                        error={!!errors.fullName}
                        helperText={errors.fullName}
                        size="small"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="الرقم الوطني"
                        value={form.nationalNumber}
                        onChange={handleChange('nationalNumber')}
                        error={!!errors.nationalNumber}
                        helperText={errors.nationalNumber || 'اختياري (12 خانة)'}
                        size="small"
                        inputProps={{ maxLength: 12 }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <DatePicker
                        label="تاريخ الميلاد *"
                        value={form.birthDate}
                        onChange={handleChange('birthDate')}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            required: true,
                            error: !!errors.birthDate,
                            helperText: errors.birthDate,
                            size: 'small'
                          }
                        }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl fullWidth required error={!!errors.gender} size="small">
                        <InputLabel>الجنس</InputLabel>
                        <Select value={form.gender} onChange={handleChange('gender')} label="الجنس" MenuProps={menuProps}>
                          <MenuItem value="">
                            <em>اختر...</em>
                          </MenuItem>
                          <MenuItem value={GENDERS.MALE}>ذكر</MenuItem>
                          <MenuItem value={GENDERS.FEMALE}>أنثى</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>

                    {!isPrincipal && (
                      <Grid size={{ xs: 12, md: 4 }}>
                        <FormControl fullWidth required error={!!errors.relationship} size="small">
                          <InputLabel>صلة القرابة</InputLabel>
                          <Select
                            value={form.relationship}
                            onChange={handleChange('relationship')}
                            label="صلة القرابة"
                            MenuProps={menuProps}
                          >
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
                        </FormControl>
                      </Grid>
                    )}
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="الجنسية" value={form.nationality} onChange={handleChange('nationality')} size="small" />
                    </Grid>
                  </Grid>
                </Grid>

                {/* Left Column: Photo Upload (Sticky behavior) */}
                <Grid size={{ xs: 12, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: '1.5rem', textAlign: 'center', height: '100%', bgcolor: 'grey.50', borderStyle: 'dashed' }}>
                    <Box
                      position="relative"
                      sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}
                    >
                      <Badge
                        overlap="circular"
                        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                        badgeContent={
                          <IconButton
                            color="primary"
                            aria-label="upload picture"
                            component="span"
                            sx={{
                              bgcolor: 'background.paper',
                              boxShadow: 2,
                              '&:hover': { bgcolor: 'background.paper' },
                              width: '2.25rem',
                              height: '2.25rem',
                              border: '2px solid white'
                            }}
                            onClick={() => document.getElementById('photo-upload').click()}
                          >
                            <CloudUploadIcon sx={{ fontSize: '1.25rem' }} />
                          </IconButton>
                        }
                      >
                        <MemberAvatar
                          member={{ id: id, photoUrl: form.photoPreview, fullName: form.fullName }}
                          size={120}
                          refreshTrigger={form.photoPreview}
                          sx={{
                            cursor: 'pointer',
                            fontSize: '3rem',
                            border: '4px solid',
                            borderColor: 'background.paper',
                            boxShadow: 1
                          }}
                          onClick={() => document.getElementById('photo-upload').click()}
                        />
                      </Badge>
                      <input accept="image/*" id="photo-upload" type="file" hidden onChange={handlePhotoSelect} />
                      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        الصورة الشخصية
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ mb: '1.0rem' }}>
                        اضغط على الدائرة للرفع
                      </Typography>
                      <Button variant="outlined" size="small" onClick={() => document.getElementById('photo-upload').click()}>
                        اختيار صورة
                      </Button>

                      {(form.photoPreview || form.hasExistingPhoto) && (
                        <Button
                          size="small"
                          color="error"
                          variant="text"
                          startIcon={<DeleteIcon />}
                          onClick={handleDeletePhoto}
                          sx={{ mt: 1, fontSize: '0.75rem' }}
                        >
                          حذف الصورة
                        </Button>
                      )}
                    </Box>
                  </Paper>
                </Grid>
              </Grid>
            )}
          </div>

          {/* Tab 1: Employment Info */}
          <div role="tabpanel" hidden={tabValue !== 1}>
            {tabValue === 1 && (
              <Grid container spacing={2}>
                {isPrincipal ? (
                  <>
                    <Grid size={{ xs: 12 }}>
                      <FormControl fullWidth required error={!!errors.employerId} size="small">
                        <InputLabel>جهة العمل</InputLabel>
                        <Select value={form.employerId} onChange={handleChange('employerId')} label="جهة العمل" MenuProps={menuProps}>
                          <MenuItem value="">
                            <em>اختر جهة العمل...</em>
                          </MenuItem>
                          {Array.isArray(employers) &&
                            employers.map((emp) => (
                              <MenuItem key={emp.id} value={emp.id}>
                                {emp.label}
                              </MenuItem>
                            ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label="الرقم الوظيفي"
                        value={form.employeeNumber}
                        onChange={handleChange('employeeNumber')}
                        size="small"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <DatePicker
                        label="تاريخ الالتحاق"
                        value={form.joinDate}
                        onChange={handleChange('joinDate')}
                        slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth label="المهنة" value={form.occupation} onChange={handleChange('occupation')} size="small" />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                      <Divider sx={{ my: 1 }} />
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControlLabel
                        control={<Switch checked={form.status === 'ACTIVE'} onChange={handleStatusToggle} color="success" />}
                        label={form.status === 'ACTIVE' ? 'نشط' : 'غير نشط'}
                        sx={{ ml: 1, mt: 0.5 }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <DatePicker
                        label="تاريخ البدء"
                        value={form.startDate}
                        onChange={handleChange('startDate')}
                        slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <DatePicker
                        label="تاريخ الانتهاء"
                        value={form.endDate}
                        onChange={handleChange('endDate')}
                        slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <TextField
                        fullWidth
                        label="ملاحظات"
                        value={form.notes}
                        onChange={handleChange('notes')}
                        multiline
                        rows={3}
                        size="small"
                      />
                    </Grid>
                  </>
                ) : (
                  <Box sx={{ p: '1.0rem', bgcolor: 'grey.50', borderRadius: 1, width: '100%' }}>
                    <Typography variant="body2" color="text.secondary">
                      لا توجد بيانات عمل للمنتفع التابع. صلة القرابة موجودة في "البيانات الشخصية".
                    </Typography>
                  </Box>
                )}
              </Grid>
            )}
          </div>

          {/* Tab 2: Contact Info */}
          <div role="tabpanel" hidden={tabValue !== 2}>
            {tabValue === 2 && (
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="رقم الهاتف"
                    value={form.phone}
                    onChange={handleChange('phone')}
                    error={!!errors.phone}
                    helperText={errors.phone || 'يجب أن يكون ليبي (09x) و10 أرقام'}
                    size="small"
                    inputProps={{ maxLength: 10 }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="البريد الإلكتروني"
                    type="email"
                    value={form.email}
                    onChange={handleChange('email')}
                    error={!!errors.email}
                    helperText={errors.email}
                    size="small"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <TextField
                    fullWidth
                    label="العنوان"
                    value={form.address}
                    onChange={handleChange('address')}
                    multiline
                    rows={2}
                    size="small"
                  />
                </Grid>
              </Grid>
            )}
          </div>
        </Box>

        <Divider />
        <Box sx={{ p: '1.0rem', display: 'flex', justifyContent: 'flex-end', gap: '1.0rem', bgcolor: 'background.default' }}>
          <Button variant="outlined" onClick={() => navigate(`/members/${id}`)}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            startIcon={saving ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
            onClick={handleSubmit}
            disabled={saving}
          >
            {saving ? 'جاري الحفظ...' : 'حفظ التغييرات'}
          </Button>
        </Box>
      </MainCard>
    </>
  );
};

export default UnifiedMemberEdit;



