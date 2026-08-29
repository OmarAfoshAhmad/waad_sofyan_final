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
  Badge,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
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
  CloudUpload as CloudUploadIcon,
  SwapHoriz as SwapHorizIcon
} from '@mui/icons-material';
import DatePicker from 'components/common/SystemDatePicker';
import dayjs from 'dayjs';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import {
  getMember,
  updateMember,
  uploadPhoto,
  deletePhoto,
  previewEmployerTransfer,
  transferEmployerFamily,
  GENDERS
} from 'services/api/unified-members.service';
import { getBenefitPoliciesByEmployer } from 'services/api/benefit-policies.service';
import axiosClient from 'utils/axios';
import { openSnackbar } from 'api/snackbar';
import { MemberAvatar } from '../../components/tba';
import useAuth from 'hooks/useAuth';
import { getMemberCapabilities } from './memberCapabilities';
import { normalizeApiError } from 'utils/api-error';

const RELATIONSHIP_LABELS = {
  WIFE: 'زوجة', HUSBAND: 'زوج', SON: 'ابن', DAUGHTER: 'ابنة',
  FATHER: 'أب', MOTHER: 'أم', BROTHER: 'أخ', SISTER: 'أخت'
};
const STATUS_LABELS = { ACTIVE: 'نشط', SUSPENDED: 'موقوف', PENDING: 'قيد المراجعة', TERMINATED: 'منتهي' };

/**
 * Unified Member Edit Component
 */
const UnifiedMemberEdit = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const { user } = useAuth();
  const capabilities = getMemberCapabilities(user);

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
    blockedReason: '',
    startDate: null,
    endDate: null,
    notes: '',
    photoPreview: null,
    photoFile: null,
    hasExistingPhoto: false
  });

  // Lookup Data
  const [employers, setEmployers] = useState([]);
  const [isPrincipal, setIsPrincipal] = useState(false);

  // Batch هـ -- whole-family employer transfer
  const [employerTransferDialog, setEmployerTransferDialog] = useState({
    open: false,
    step: 'select', // 'select' -> 'preview'
    newEmployerId: '',
    policyOptions: [],
    newPolicyId: '',
    noPolicy: false,
    effectiveDate: new Date().toISOString().slice(0, 10),
    reason: '',
    preview: null,
    loading: false
  });

  /**
   * Helper to check if a tab has validation errors
   */
  const getTabErrorCount = (index) => {
    if (index === 0) {
      return (
        (errors.fullName ? 1 : 0) +
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
        blockedReason: data.blockedReason || '',
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
      const [orgsRes] = await Promise.all([axiosClient.get('/employers/selectors')]);
      setEmployers(orgsRes.data?.data || []);
    } catch (error) {
      console.error('Error fetching lookup data:', error);
    }
  };

  const openEmployerTransferDialog = () => {
    setEmployerTransferDialog({
      open: true,
      step: 'select',
      newEmployerId: '',
      policyOptions: [],
      newPolicyId: '',
      noPolicy: false,
      effectiveDate: new Date().toISOString().slice(0, 10),
      reason: '',
      preview: null,
      loading: false
    });
  };

  const selectNewEmployerForTransfer = async (newEmployerId) => {
    setEmployerTransferDialog((prev) => ({ ...prev, newEmployerId, newPolicyId: '', noPolicy: false, policyOptions: [] }));
    if (!newEmployerId) return;
    try {
      const policies = await getBenefitPoliciesByEmployer(newEmployerId);
      const list = Array.isArray(policies) ? policies : policies?.content || [];
      setEmployerTransferDialog((prev) => (prev.newEmployerId === newEmployerId ? { ...prev, policyOptions: list } : prev));
    } catch {
      // Leave policyOptions empty -- forces an explicit "no policy" choice rather than a guess.
    }
  };

  const loadTransferPreview = async () => {
    const { newEmployerId } = employerTransferDialog;
    if (!newEmployerId) {
      openSnackbar({ open: true, message: 'اختر جهة العمل الجديدة أولاً', variant: 'alert', alert: { color: 'warning' } });
      return;
    }
    setEmployerTransferDialog((prev) => ({ ...prev, loading: true }));
    try {
      const res = await previewEmployerTransfer(id, newEmployerId);
      const preview = res?.data || res;
      setEmployerTransferDialog((prev) => ({ ...prev, step: 'preview', preview, loading: false }));
    } catch (err) {
      openSnackbar({
        open: true,
        message: normalizeApiError(err).message || 'تعذّر تحميل معاينة النقل',
        variant: 'alert',
        alert: { color: 'error' }
      });
      setEmployerTransferDialog((prev) => ({ ...prev, loading: false }));
    }
  };

  const confirmEmployerTransfer = async () => {
    const { newEmployerId, newPolicyId, noPolicy, effectiveDate, reason, preview } = employerTransferDialog;
    if (!noPolicy && !newPolicyId) {
      openSnackbar({ open: true, message: 'حدّد الوثيقة الجديدة، أو أكّد عدم وجود وثيقة لهذه الأسرة', variant: 'alert', alert: { color: 'warning' } });
      return;
    }
    if (!reason.trim()) {
      openSnackbar({ open: true, message: 'سبب نقل جهة العمل إلزامي', variant: 'alert', alert: { color: 'warning' } });
      return;
    }
    const expectedVersions = {};
    (preview?.familyMembers || []).forEach((m) => {
      expectedVersions[m.memberId] = m.version;
    });
    setEmployerTransferDialog((prev) => ({ ...prev, loading: true }));
    try {
      await transferEmployerFamily(id, {
        newEmployerId,
        newPolicyId: noPolicy ? null : newPolicyId,
        noPolicy,
        effectiveDate,
        reason: reason.trim(),
        expectedVersions
      });
      openSnackbar({ open: true, message: 'تم نقل الأسرة إلى جهة العمل الجديدة بنجاح', variant: 'alert', alert: { color: 'success' } });
      setEmployerTransferDialog((prev) => ({ ...prev, open: false, loading: false }));
      fetchMemberData();
    } catch (err) {
      openSnackbar({
        open: true,
        message: normalizeApiError(err).message || 'تعذّر نقل الأسرة -- تأكد أن بيانات الأسرة لم تتغيّر أثناء المعاينة',
        variant: 'alert',
        alert: { color: 'error' }
      });
      setEmployerTransferDialog((prev) => ({ ...prev, loading: false }));
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

  /**
   * Validation
   */
  const validateForm = () => {
    const newErrors = {};
    if (!form.fullName?.trim()) newErrors.fullName = 'الاسم الكامل مطلوب';

    // employerId and relationship are deliberately NOT validated here: both
    // fields are read-only in this form (moved to the dedicated employer-
    // transfer and relationship-correction operations), so requiring them
    // would block saving an ordinary metadata edit (phone, address...) on
    // any legacy record that happens to be missing one -- the user has no
    // way to fix that field from this screen at all.

    if (form.nationalNumber && form.nationalNumber.length !== 12) {
      newErrors.nationalNumber = 'الرقم الوطني يجب أن يتكون من 12 خانة';
    }

    if (form.phone && !/^(091|092|094|093|095|096)\d{7}$/.test(form.phone)) {
      newErrors.phone = 'رقم الهاتف غير صحيح';
    }

    setErrors(newErrors);

    if (newErrors.fullName || newErrors.nationalNumber) {
      setTabValue(0);
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
        // status/active are intentionally NOT part of this descriptive update.
        // Lifecycle transitions use the dedicated audited dialog/endpoints.
        startDate: form.startDate ? dayjs(form.startDate).format('YYYY-MM-DD') : null,
        endDate: form.endDate ? dayjs(form.endDate).format('YYYY-MM-DD') : null,
        notes: form.notes || null
      };

      // employerId / relationship are deliberately NOT sent: the generic
      // update path refuses to CHANGE them (moving a member between employers
      // or altering the family structure are separate, audited operations).
      // Round-tripping them unchanged would work, but not sending them at all
      // keeps this payload honest about what it is allowed to modify.

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
        message: normalizeApiError(error).message || 'خطأ في تحديث البيانات',
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
        title={`تعديل بيانات ${isPrincipal ? 'الموظف' : 'المنتفع التابع'}`}
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
                        label="تاريخ الميلاد"
                        value={form.birthDate}
                        onChange={handleChange('birthDate')}
                        slotProps={{
                          textField: {
                            fullWidth: true,
                            error: !!errors.birthDate,
                            helperText: errors.birthDate,
                            size: 'small'
                          }
                        }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <FormControl fullWidth error={!!errors.gender} size="small">
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
                        <TextField
                          fullWidth
                          label="صلة القرابة"
                          value={RELATIONSHIP_LABELS[form.relationship] || form.relationship || '-'}
                          slotProps={{ input: { readOnly: true } }}
                          helperText="تغيير القرابة عملية أسرية مستقلة ومدققة."
                          size="small"
                        />
                      </Grid>
                    )}
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="الجنسية" value={form.nationality} onChange={handleChange('nationality')} size="small" />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="حالة المستفيد"
                        value={STATUS_LABELS[form.status] || form.status || '-'}
                        slotProps={{ input: { readOnly: true } }}
                        helperText="غيّر الحالة من الإجراء المستقل لضمان السبب والتدقيق وأثر الأسرة."
                        size="small"
                      />
                    </Grid>
                    {form.status === 'SUSPENDED' && form.blockedReason && (
                      <Grid size={{ xs: 12, md: 6 }} sx={{ display: 'flex', alignItems: 'center' }}>
                        <Tooltip title={form.blockedReason}>
                          <Chip label={`سبب الإيقاف: ${form.blockedReason}`} color="warning" variant="outlined" sx={{ maxWidth: '100%' }} />
                        </Tooltip>
                      </Grid>
                    )}
                  </Grid>
                </Grid>

                {/* Left Column: Photo Upload (Sticky behavior) */}
                <Grid size={{ xs: 12, md: 3 }}>
                  <Paper
                    variant="outlined"
                    sx={{ p: '1.5rem', textAlign: 'center', height: '100%', bgcolor: 'grey.50', borderStyle: 'dashed' }}
                  >
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
                          variant="portrait"
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
                      <Stack direction="row" spacing={1} alignItems="flex-start">
                        <TextField
                          fullWidth
                          label="جهة العمل"
                          value={employers.find((emp) => String(emp.id) === String(form.employerId))?.label || form.employerId || '-'}
                          slotProps={{ input: { readOnly: true } }}
                          helperText="نقل جهة العمل عملية مستقلة ومؤرخة ولا يتم من التعديل العام."
                          size="small"
                        />
                        {capabilities.transfer && (
                          <Button variant="outlined" size="small" startIcon={<SwapHorizIcon />} onClick={openEmployerTransferDialog} sx={{ mt: 0.5, whiteSpace: 'nowrap' }}>
                            نقل إلى جهة أخرى
                          </Button>
                        )}
                      </Stack>
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

      {/* نقل رئيس أسرة وأسرته إلى جهة عمل أخرى -- عملية مستقلة كاملة */}
      <Dialog
        open={employerTransferDialog.open}
        onClose={() => (employerTransferDialog.loading ? null : setEmployerTransferDialog((prev) => ({ ...prev, open: false })))}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>نقل الأسرة إلى جهة عمل أخرى</DialogTitle>
        <DialogContent>
          {employerTransferDialog.step === 'select' ? (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <DialogContentText>
                سينتقل رئيس الأسرة وكل تابعيه معاً إلى الجهة الجديدة، أو لا أحد ينتقل. لن يتأثر أي سجل مالي أو مطالبة سابقة لتاريخ السريان.
              </DialogContentText>
              <FormControl fullWidth size="small">
                <InputLabel>جهة العمل الجديدة</InputLabel>
                <Select
                  label="جهة العمل الجديدة"
                  value={employerTransferDialog.newEmployerId}
                  onChange={(e) => selectNewEmployerForTransfer(e.target.value)}
                >
                  {employers
                    .filter((emp) => String(emp.id) !== String(form.employerId))
                    .map((emp) => (
                      <MenuItem key={emp.id} value={emp.id}>
                        {emp.label}
                      </MenuItem>
                    ))}
                </Select>
              </FormControl>
            </Stack>
          ) : (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <DialogContentText>
                {employerTransferDialog.preview?.currentEmployerName} ← {employerTransferDialog.preview?.newEmployerName}
              </DialogContentText>
              <Paper variant="outlined" sx={{ p: 1.5 }}>
                <Typography variant="subtitle2" gutterBottom>
                  أفراد الأسرة المتأثرون ({employerTransferDialog.preview?.familyMembers?.length || 0})
                </Typography>
                {(employerTransferDialog.preview?.familyMembers || []).map((m) => (
                  <Typography key={m.memberId} variant="body2" color="text.secondary">
                    {m.principal ? '(رئيس الأسرة) ' : ''}
                    {m.fullName} {m.relationship ? `(${RELATIONSHIP_LABELS[m.relationship] || m.relationship})` : ''}
                  </Typography>
                ))}
              </Paper>
              <FormControl fullWidth size="small">
                <InputLabel>الوثيقة الجديدة</InputLabel>
                <Select
                  label="الوثيقة الجديدة"
                  value={employerTransferDialog.noPolicy ? '__NONE__' : employerTransferDialog.newPolicyId}
                  onChange={(e) => {
                    const value = e.target.value;
                    if (value === '__NONE__') {
                      setEmployerTransferDialog((prev) => ({ ...prev, noPolicy: true, newPolicyId: '' }));
                    } else {
                      setEmployerTransferDialog((prev) => ({ ...prev, noPolicy: false, newPolicyId: value }));
                    }
                  }}
                >
                  {employerTransferDialog.policyOptions.map((p) => (
                    <MenuItem key={p.id} value={p.id}>
                      {p.policyName || p.name || `وثيقة #${p.id}`}
                    </MenuItem>
                  ))}
                  <MenuItem value="__NONE__">
                    <em>بلا وثيقة بعد (تأكيد صريح)</em>
                  </MenuItem>
                </Select>
              </FormControl>
              <TextField
                type="date"
                label="تاريخ السريان"
                fullWidth
                size="small"
                InputLabelProps={{ shrink: true }}
                value={employerTransferDialog.effectiveDate}
                onChange={(e) => setEmployerTransferDialog((prev) => ({ ...prev, effectiveDate: e.target.value }))}
              />
              <TextField
                label="سبب النقل"
                required
                fullWidth
                multiline
                minRows={2}
                value={employerTransferDialog.reason}
                onChange={(e) => setEmployerTransferDialog((prev) => ({ ...prev, reason: e.target.value }))}
              />
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEmployerTransferDialog((prev) => ({ ...prev, open: false }))} disabled={employerTransferDialog.loading}>
            إلغاء
          </Button>
          {employerTransferDialog.step === 'select' ? (
            <Button variant="contained" disabled={!employerTransferDialog.newEmployerId || employerTransferDialog.loading} onClick={loadTransferPreview}>
              {employerTransferDialog.loading ? 'جارِ التحميل...' : 'متابعة'}
            </Button>
          ) : (
            <Button
              variant="contained"
              color="warning"
              disabled={employerTransferDialog.loading || !employerTransferDialog.reason.trim() || (!employerTransferDialog.noPolicy && !employerTransferDialog.newPolicyId)}
              onClick={confirmEmployerTransfer}
            >
              {employerTransferDialog.loading ? 'جارِ النقل...' : 'تأكيد نقل الأسرة'}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </>
  );
};

export default UnifiedMemberEdit;
