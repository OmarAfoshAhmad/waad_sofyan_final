/**
 * Unified Member Create Page
 *
 * Creates a Principal member with optional inline Dependents.
 * Uses new Unified Architecture (single Member entity with parent_id).
 *
 * Architecture:
 * - Principal: parent_id = NULL, has Barcode (auto-generated)
 * - Dependent: parent_id references Principal, NO Barcode
 * - Card Numbers: Principal (NNNNNN), Dependent (NNNNNN-NN)
 *
 * @module UnifiedMemberCreate
 * @since 2026-01-11
 */

import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
  IconButton,
  Divider,
  Tabs,
  Tab,
  Box,
  FormControl,
  InputLabel,
  Select,
  FormHelperText,
  Alert,
  CircularProgress,
  Avatar,
  Switch,
  FormControlLabel
} from '@mui/material';
import {
  Save as SaveIcon,
  ArrowBack as ArrowBackIcon,
  PeopleAlt as PeopleAltIcon,
  ExpandMore as ExpandMoreIcon,
  PersonAdd as PersonAddIcon,
  Badge as BadgeIcon,
  ContactPhone as ContactPhoneIcon,
  Person as PersonIcon,
  FlashOn as FlashIcon,
  Star as VIPStarIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { createPrincipalMember, uploadPhoto, GENDERS } from 'services/api/unified-members.service';
import { getEffectiveBenefitPolicy } from 'services/api/benefit-policies.service';
import axiosClient from 'utils/axios';
import { openSnackbar } from 'api/snackbar';

/**
 * Unified Member Create Component
 */
const UnifiedMemberCreate = () => {
  const navigate = useNavigate();

  const menuProps = {
    PaperProps: {
      sx: {
        '& .MuiMenuItem-root': { fontSize: '12px' },
        maxHeight: 300,
        minWidth: 200 // Added for better visibility of long options
      }
    }
  };

  // Loading & Error States
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  // Principal Member Form (aligned with MemberCreateDto)
  const [principalForm, setPrincipalForm] = useState({
    fullName: '',
    nationalNumber: '', // Optional - Civil ID optional as per architecture
    birthDate: null,
    gender: '',
    nationality: 'ليبي',
    phone: '',
    email: '',
    address: '',
    employerOrganizationId: '',
    employeeNumber: '',
    joinDate: null,
    occupation: '',
    policyNumber: '',
    status: 'ACTIVE',
    startDate: dayjs(),
    endDate: null,
    notes: '',
    isFastTrack: false,
    isVip: false,
    isUrgent: false,
    emergencyNotes: '',
    noEmployer: false,
    active: true
  });

  // Tab State
  const [tabValue, setTabValue] = useState(0);
  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  useEffect(() => {
    if (principalForm.isFastTrack && tabValue !== 0) {
      setTabValue(0);
    }
  }, [principalForm.isFastTrack, tabValue]);

  // Lookup Data
  const [employers, setEmployers] = useState([]);
  const [benefitPolicies, setBenefitPolicies] = useState([]);

  /**
   * Helper to identify tabs with validation errors
   */
  const getTabErrorCount = (index) => {
    if (index === 0) {
      return (errors.fullName ? 1 : 0) + (errors.birthDate ? 1 : 0) + (errors.gender ? 1 : 0) + (errors.nationalNumber ? 1 : 0);
    }
    if (index === 1) {
      return errors.employerOrganizationId ? 1 : 0;
    }
    if (index === 2) {
      return (errors.phone ? 1 : 0) + (errors.email ? 1 : 0);
    }
    return 0;
  };

  const [searchParams] = useSearchParams();

  // Fetch lookup data
  useEffect(() => {
    fetchEmployers();

    // Check for mode=fast-track in URL
    if (searchParams.get('mode') === 'fast-track') {
      setPrincipalForm((prev) => ({
        ...prev,
        isFastTrack: true,
        isVip: true,
        isUrgent: true
      }));
    }
  }, [searchParams]);

  const fetchEmployers = async () => {
    try {
      // Use selectors endpoint for dropdown population - faster and lighter
      const response = await axiosClient.get('/employers/selectors');
      setEmployers(response.data?.data || []);
    } catch (error) {
      console.error('Error fetching employers:', error);
      openSnackbar({
        open: true,
        message: 'خطأ في جلب جهات العمل',
        variant: 'alert',
        alert: { color: 'error' }
      });
    }
  };

  const fetchBenefitPolicies = async () => {
    // Deleted as per user request: Policy linking moved to Contacts section
  };

  /**
   * Handle principal form change
   */
  const handlePrincipalChange = (field) => (eventOrValue) => {
    // Handle both event objects (from inputs) and direct values (from DatePicker)
    let value;
    if (eventOrValue === null || eventOrValue === undefined) {
      value = null;
    } else if (eventOrValue?.target !== undefined) {
      value = eventOrValue.target.value;
    } else {
      value = eventOrValue; // Direct value from DatePicker
    }

    // 🛡️ SECURITY & UX: Input Restriction
    // Allow ONLY numbers for National ID and Phone
    if ((field === 'nationalNumber' || field === 'phone' || field === 'employeeNumber') && typeof value === 'string') {
      value = value.replace(/\D/g, ''); // Remove non-digits

      // Limit Length
      if (field === 'nationalNumber' && value.length > 12) return; // Max 12
      if (field === 'phone' && value.length > 10) return; // Max 10
    }

    if (field === 'employerOrganizationId') {
      handleEmployerChange(value);
      return;
    }

    setPrincipalForm((prev) => ({
      ...prev,
      [field]: value
    }));
    // Clear error for this field
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  /**
   * Handle Employer Selection & Auto-Link Policy
   */
  const handleEmployerChange = async (employerId) => {
    // Update employer ID
    setPrincipalForm((prev) => ({
      ...prev,
      employerOrganizationId: employerId,
      benefitPolicyId: null, // Reset first
      benefitPolicyName: null
    }));

    if (errors.employerOrganizationId) {
      setErrors((prev) => ({ ...prev, employerOrganizationId: null }));
    }

    if (!employerId) return;

    try {
      // Auto-fetch effective policy
      const policy = await getEffectiveBenefitPolicy(employerId);

      if (policy && policy.id) {
        setPrincipalForm((prev) => ({
          ...prev,
          benefitPolicyId: policy.id,
          benefitPolicyName: policy.name, // For display if needed
          policyNumber: policy.policyNumber // Use policy number from effective policy
        }));

        openSnackbar({
          open: true,
          message: `تم ربط الوثيقة تلقائياً: ${policy.name}`,
          variant: 'alert',
          alert: { color: 'info' }
        });
      }
    } catch (error) {
      console.warn('No effective policy found or error fetching:', error);
      // Silent fail is okay, just means no auto-link
    }
  };

  const validatePrincipalForm = () => {
    const newErrors = {};
    // Required fields
    if (!principalForm.fullName?.trim()) newErrors.fullName = 'الاسم الكامل مطلوب';

    // Bypass minor validations in Fast Track
    if (!principalForm.isFastTrack) {
      if (!principalForm.birthDate) newErrors.birthDate = 'تاريخ الميلاد مطلوب';
      if (!principalForm.gender) newErrors.gender = 'الجنس مطلوب';
      if (!principalForm.noEmployer && !principalForm.employerOrganizationId) {
        newErrors.employerOrganizationId = 'جهة العمل مطلوبة';
      }
    } else {
      // In Fast Track, we only need the employer
      if (!principalForm.employerOrganizationId) {
        newErrors.employerOrganizationId = 'جهة العمل مطلوبة';
      }
    }

    // 🛡️ SECURITY & DATA INTEGRITY VALIDATION
    // 1. National ID: Must be exactly 12 digits
    if (principalForm.nationalNumber && principalForm.nationalNumber.length !== 12) {
      newErrors.nationalNumber = 'الرقم الوطني يجب أن يتكون من 12 خانة';
    }

    // 2. Phone Number: Libyan Format (091, 092, 093, 094, 095, 096)
    if (principalForm.phone) {
      if (!/^(091|092|094|093|095|096)\d{7}$/.test(principalForm.phone)) {
        newErrors.phone = 'رقم الهاتف غير صحيح (يجب أن يبدأ بـ 09x ويتكون من 10 أرقام)';
      }
    }

    setErrors(newErrors);

    // Auto-switch tabs based on error location
    if (newErrors.fullName || newErrors.birthDate || newErrors.gender || newErrors.nationalNumber) {
      setTabValue(0);
    } else if (newErrors.employerOrganizationId) {
      setTabValue(1);
    } else if (newErrors.phone || newErrors.email) {
      setTabValue(2);
    }

    return Object.keys(newErrors).length === 0;
  };

  /**
   * Submit form - Create Principal with Dependents
   */
  const handleSubmit = async () => {
    // Validate
    if (!validatePrincipalForm()) {
      openSnackbar({
        open: true,
        message: 'يرجى تصحيح الأخطاء في النموذج',
        variant: 'alert',
        alert: { color: 'error' }
      });
      return;
    }

    setLoading(true);

    try {
      // Prepare payload
      const payload = {
        fullName: principalForm.fullName.trim(),
        nationalNumber: principalForm.nationalNumber?.trim() || null,
        birthDate: principalForm.birthDate
          ? dayjs(principalForm.birthDate).format('YYYY-MM-DD')
          : principalForm.isFastTrack
            ? '1900-01-01'
            : null,
        gender: principalForm.gender || (principalForm.isFastTrack ? 'UNDEFINED' : ''),
        maritalStatus: principalForm.maritalStatus || (principalForm.isFastTrack ? 'SINGLE' : null),
        nationality: principalForm.nationality || 'ليبي',
        phone: principalForm.phone?.trim() || null,
        email: principalForm.email || null,
        address: principalForm.address || null,
        employerId: principalForm.employerOrganizationId, // ✅ FIXED: Send as employerId
        benefitPolicyId: principalForm.benefitPolicyId || null,
        employeeNumber: principalForm.employeeNumber || null,
        joinDate: principalForm.joinDate ? dayjs(principalForm.joinDate).format('YYYY-MM-DD') : null,
        occupation: principalForm.occupation || null,
        policyNumber: principalForm.policyNumber || null,
        status: principalForm.isFastTrack ? 'PENDING' : principalForm.status || 'ACTIVE',
        active: principalForm.active !== false,
        startDate: principalForm.startDate ? dayjs(principalForm.startDate).format('YYYY-MM-DD') : null,
        endDate: principalForm.endDate ? dayjs(principalForm.endDate).format('YYYY-MM-DD') : null,
        notes: principalForm.notes || null,
        isVip: principalForm.isVip || (principalForm.isFastTrack ? true : false),
        isUrgent: principalForm.isUrgent || (principalForm.isFastTrack ? true : false),
        isFastTrack: principalForm.isFastTrack,
        emergencyNotes: principalForm.emergencyNotes || null
      };

      console.log('Creating principal member with payload:', JSON.stringify(payload, null, 2));

      // Call API
      const response = await createPrincipalMember(payload);

      console.log('Member created successfully:', response);

      // ✅ FIXED: Response is already unwrapped by service (response.data in service)
      // Check if response has the member data
      const createdMember = response?.data || response;

      if (!createdMember?.id) {
        throw new Error('Invalid response: Missing member ID');
      }

      // Upload Photo if selected
      if (principalForm.photoFile) {
        try {
          await uploadPhoto(createdMember.id, principalForm.photoFile);
        } catch (uploadError) {
          console.error('Photo upload failed', uploadError);
          openSnackbar({ message: 'تم إنشاء العضو ولكن فشل رفع الصورة', variant: 'alert', alert: { color: 'warning' } });
        }
      }

      openSnackbar({
        open: true,
        message: 'تم إضافة المنتفع الرئيسي بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      // Navigate to view page (use the actual ID from response)
      navigate(`/members/${createdMember.id}`);
    } catch (error) {
      console.error('Error creating member:', error);

      const errorMessage = error.response?.data?.message || error.message || 'خطأ في إنشاء العضو';

      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <ModernPageHeader
        title={principalForm.isFastTrack ? 'تسجيل طارئ / VIP (مسار سريع)' : 'إضافة منتفع رئيسي جديد'}
        icon={principalForm.isFastTrack ? <FlashIcon sx={{ color: '#ff9100' }} /> : <PersonAddIcon />}
        breadcrumbs={[
          { label: 'الرئيسية', href: '/' },
          { label: 'المنتفعين', href: '/members' },
          { label: principalForm.isFastTrack ? 'تسجيل طارئ' : 'إضافة منتفع' }
        ]}
        actions={
          <Stack direction="row" spacing={2} alignItems="center">
            <FormControlLabel
              control={
                <Switch
                  color="warning"
                  checked={principalForm.isFastTrack}
                  onChange={(e) => {
                    const checked = e.target.checked;
                    setTabValue(0); // Reset to first tab for fast track
                    setPrincipalForm((prev) => ({
                      ...prev,
                      isFastTrack: checked,
                      isVip: checked ? true : prev.isVip,
                      isUrgent: checked ? true : prev.isUrgent
                    }));
                  }}
                />
              }
              label={
                <Typography
                  variant="subtitle2"
                  sx={{ color: principalForm.isFastTrack ? 'warning.main' : 'text.secondary', fontWeight: 600 }}
                >
                  وضع التسجيل السريع (طوارئ)
                </Typography>
              }
            />
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/members')}>
              رجوع
            </Button>
          </Stack>
        }
      />

      <MainCard
        title="بيانات المنتفع الرئيسي"
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
            aria-label="member tabs"
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              minHeight: 48,
              '& .MuiTab-root': {
                minHeight: 48,
                fontSize: '13px',
                fontWeight: 500,
                color: 'text.secondary',
                transition: 'all 0.2s',
                px: 3,
                '&.Mui-selected': {
                  color: 'primary.main',
                  bgcolor: 'primary.lighter',
                  fontWeight: 600
                }
              },
              '& .MuiTabs-indicator': {
                height: 3,
                borderRadius: '3px 3px 0 0'
              }
            }}
          >
            {[
              <Tab
                key="personal"
                label={
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <span>البيانات الشخصية</span>
                    {getTabErrorCount(0) > 0 && <span style={{ color: '#f44336', fontSize: '16px' }}>●</span>}
                  </Stack>
                }
                icon={<PersonIcon />}
                iconPosition="start"
                sx={{ color: getTabErrorCount(0) > 0 ? 'error.main' : 'inherit' }}
              />,
              !principalForm.isFastTrack && (
                <Tab
                  key="employment"
                  label={
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <span>بيانات العمل</span>
                      {getTabErrorCount(1) > 0 && <span style={{ color: '#f44336', fontSize: '16px' }}>●</span>}
                    </Stack>
                  }
                  icon={<BadgeIcon />}
                  iconPosition="start"
                  sx={{ color: getTabErrorCount(1) > 0 ? 'error.main' : 'inherit' }}
                />
              ),
              !principalForm.isFastTrack && (
                <Tab
                  key="contact"
                  label={
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <span>معلومات الاتصال</span>
                      {getTabErrorCount(2) > 0 && <span style={{ color: '#f44336', fontSize: '16px' }}>●</span>}
                    </Stack>
                  }
                  icon={<ContactPhoneIcon />}
                  iconPosition="start"
                  sx={{ color: getTabErrorCount(2) > 0 ? 'error.main' : 'inherit' }}
                />
              )
            ].filter(Boolean)}
          </Tabs>
        </Box>

        {Object.keys(errors).length > 0 && (
          <Box sx={{ px: 3, pt: 2 }}>
            <Alert
              severity="error"
              variant="outlined"
              sx={{
                bgcolor: 'error.lighter',
                borderColor: 'error.light',
                '& .MuiAlert-message': { fontWeight: 600, fontSize: '13px' }
              }}
            >
              توجد أخطاء في المدخلات؛ يرجى مراجعة التبويبات المميزة باللون الأحمر (عدد الحقول المعيبة: {Object.keys(errors).length})
            </Alert>
          </Box>
        )}

        {/* Scrollable Content Area */}
        <Box sx={{ flex: 1, overflowY: 'auto', p: 3 }}>
          {/* Tab 0: Personal Info */}
          <div role="tabpanel" hidden={tabValue !== 0}>
            {tabValue === 0 && (
              <Grid container spacing={3}>
                {/* Right Column: Fields (Occupies more space, comes first in RTL) */}
                <Grid size={{ xs: 12, md: 9 }}>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <Alert severity="info" sx={{ mb: 2, '& .MuiAlert-message': { fontSize: '12px' } }}>
                        يتم توليد رقم البطاقة والباركود تلقائياً عند الحفظ.
                      </Alert>
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        required
                        label="الاسم الكامل"
                        value={principalForm.fullName}
                        onChange={handlePrincipalChange('fullName')}
                        error={!!errors.fullName}
                        helperText={errors.fullName}
                        size="small"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="الرقم الوطني"
                        value={principalForm.nationalNumber}
                        onChange={handlePrincipalChange('nationalNumber')}
                        error={!!errors.nationalNumber}
                        helperText={errors.nationalNumber || 'اختياري (12 خانة)'}
                        size="small"
                        inputProps={{ maxLength: 12 }}
                      />
                    </Grid>
                    {!principalForm.isFastTrack ? (
                      <>
                        <Grid size={{ xs: 12, md: 4 }}>
                          <DatePicker
                            label="تاريخ الميلاد *"
                            value={principalForm.birthDate}
                            onChange={handlePrincipalChange('birthDate')}
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
                            <InputLabel id="gender-label">الجنس</InputLabel>
                            <Select
                              labelId="gender-label"
                              value={principalForm.gender}
                              onChange={handlePrincipalChange('gender')}
                              label="الجنس"
                              MenuProps={menuProps}
                            >
                              <MenuItem value="">
                                <em>اختر...</em>
                              </MenuItem>
                              <MenuItem value={GENDERS.MALE}>ذكر</MenuItem>
                              <MenuItem value={GENDERS.FEMALE}>أنثى</MenuItem>
                            </Select>
                            {errors.gender && <FormHelperText>{errors.gender}</FormHelperText>}
                          </FormControl>
                        </Grid>

                        <Grid size={{ xs: 12, md: 12 }}>
                          <TextField
                            fullWidth
                            label="الجنسية"
                            value={principalForm.nationality}
                            onChange={handlePrincipalChange('nationality')}
                            size="small"
                          />
                        </Grid>
                      </>
                    ) : (
                      <>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <TextField
                            fullWidth
                            label="رقم الهاتف"
                            value={principalForm.phone}
                            onChange={handlePrincipalChange('phone')}
                            error={!!errors.phone}
                            helperText={errors.phone}
                            size="small"
                            inputProps={{ maxLength: 10 }}
                          />
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <FormControl fullWidth required error={!!errors.employerOrganizationId} size="small">
                            <InputLabel id="employer-label">جهة العمل</InputLabel>
                            <Select
                              labelId="employer-label"
                              value={principalForm.employerOrganizationId}
                              onChange={handlePrincipalChange('employerOrganizationId')}
                              label="جهة العمل"
                            >
                              <MenuItem value="">
                                <em>اختر جهة العمل...</em>
                              </MenuItem>
                              {employers.map((emp) => (
                                <MenuItem key={emp.id} value={emp.id}>
                                  {emp.label}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </Grid>
                      </>
                    )}

                    {principalForm.isFastTrack && (
                      <Grid size={{ xs: 12 }}>
                        <Paper sx={{ p: 2, mt: 1, bgcolor: 'warning.lighter', border: '1px dashed', borderColor: 'warning.main' }}>
                          <Typography
                            variant="subtitle2"
                            color="warning.main"
                            sx={{ mb: 1, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}
                          >
                            <FlashIcon fontSize="small" /> تفاصيل حالة الطوارئ / VIP
                          </Typography>
                          <Grid container spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <TextField
                                fullWidth
                                label="ملاحظات سريعة للجراحين/المقدمين"
                                multiline
                                rows={2}
                                value={principalForm.emergencyNotes}
                                onChange={(e) => setPrincipalForm((prev) => ({ ...prev, emergencyNotes: e.target.value }))}
                                placeholder="هذا الحقل يظهر فوراً لمقدمي الخدمة عند مسح الباركود..."
                                size="small"
                              />
                            </Grid>
                          </Grid>
                        </Paper>
                      </Grid>
                    )}
                  </Grid>
                </Grid>

                {/* Left Column: Photo Upload (Sticky behavior) */}
                <Grid size={{ xs: 12, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 3, textAlign: 'center', height: '100%', bgcolor: 'grey.50', borderStyle: 'dashed' }}>
                    <Box
                      position="relative"
                      sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}
                    >
                      <Avatar
                        src={principalForm.photoPreview}
                        sx={{
                          width: 140,
                          height: 140,
                          fontSize: '4rem',
                          cursor: 'pointer',
                          border: '3px solid',
                          borderColor: 'primary.light',
                          mb: 2
                        }}
                        onClick={() => document.getElementById('photo-upload').click()}
                      >
                        {principalForm.fullName ? principalForm.fullName.charAt(0) : <PersonAddIcon sx={{ fontSize: 60 }} />}
                      </Avatar>
                      <input
                        accept="image/*"
                        id="photo-upload"
                        type="file"
                        hidden
                        onChange={(e) => {
                          if (e.target.files && e.target.files[0]) {
                            const file = e.target.files[0];
                            setPrincipalForm((prev) => ({
                              ...prev,
                              photoFile: file,
                              photoPreview: URL.createObjectURL(file)
                            }));
                          }
                        }}
                      />
                      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        الصورة الشخصية
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ mb: 2 }}>
                        اضغط على الدائرة للرفع
                      </Typography>
                      <Button variant="outlined" size="small" onClick={() => document.getElementById('photo-upload').click()}>
                        اختيار صورة
                      </Button>
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
                <Grid size={{ xs: 12 }}>
                  <FormControl fullWidth required error={!!errors.employerOrganizationId} size="small">
                    <InputLabel id="employer-label">جهة العمل</InputLabel>
                    <Select
                      labelId="employer-label"
                      value={principalForm.employerOrganizationId}
                      onChange={(e) => handlePrincipalChange('employerOrganizationId')(e)}
                      label="جهة العمل"
                      MenuProps={menuProps}
                    >
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
                    {errors.employerOrganizationId && <FormHelperText>{errors.employerOrganizationId}</FormHelperText>}
                    {principalForm.benefitPolicyName && (
                      <FormHelperText sx={{ color: 'primary.main', fontWeight: 'bold' }}>
                        تم الربط بالوثيقة: {principalForm.benefitPolicyName}
                      </FormHelperText>
                    )}
                  </FormControl>
                </Grid>

                {!principalForm.isFastTrack && (
                  <>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label="رقم الموظف"
                        value={principalForm.employeeNumber}
                        onChange={handlePrincipalChange('employeeNumber')}
                        size="small"
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <DatePicker
                        label="تاريخ الالتحاق"
                        value={principalForm.joinDate}
                        onChange={handlePrincipalChange('joinDate')}
                        slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField
                        fullWidth
                        label="المهنة"
                        value={principalForm.occupation}
                        onChange={handlePrincipalChange('occupation')}
                        size="small"
                      />
                    </Grid>
                  </>
                )}

                <Grid size={{ xs: 12 }}>
                  <Divider sx={{ my: 1 }} />
                </Grid>

                <Grid size={{ xs: 12, md: 6 }}>
                  <DatePicker
                    label="تاريخ البداية"
                    value={principalForm.startDate}
                    onChange={handlePrincipalChange('startDate')}
                    slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                  />
                </Grid>
                {!principalForm.isFastTrack && (
                  <>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <DatePicker
                        label="تاريخ النهاية"
                        value={principalForm.endDate}
                        onChange={handlePrincipalChange('endDate')}
                        slotProps={{ textField: { fullWidth: true, size: 'small' } }}
                      />
                    </Grid>
                  </>
                )}
                <Grid size={{ xs: 12 }}>
                  <TextField
                    fullWidth
                    label="ملاحظات عامة"
                    value={principalForm.notes}
                    onChange={handlePrincipalChange('notes')}
                    multiline
                    rows={2}
                    size="small"
                  />
                </Grid>

                <Grid size={{ xs: 12 }}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={principalForm.active !== false}
                        onChange={(e) => setPrincipalForm((prev) => ({ ...prev, active: e.target.checked }))}
                        color="success"
                      />
                    }
                    label={
                      <Typography variant="body2" color={principalForm.active !== false ? 'success.main' : 'text.secondary'}>
                        {principalForm.active !== false ? 'نشط' : 'غير نشط'}
                      </Typography>
                    }
                  />
                </Grid>

                {!principalForm.isFastTrack && (principalForm.isVip || principalForm.isUrgent) && (
                  <Grid size={{ xs: 12 }}>
                    <Paper sx={{ p: 2, bgcolor: 'warning.lighter', border: '1px dashed', borderColor: 'warning.main' }}>
                      <Typography
                        variant="subtitle2"
                        color="warning.main"
                        sx={{ mb: 1, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}
                      >
                        <FlashIcon fontSize="small" /> تفاصيل حالة الطوارئ / VIP
                      </Typography>
                      <Grid container spacing={2}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <Stack direction="row" spacing={3}>
                            <FormControlLabel
                              control={
                                <Switch
                                  checked={principalForm.isVip}
                                  onChange={(e) => setPrincipalForm((prev) => ({ ...prev, isVip: e.target.checked }))}
                                />
                              }
                              label="تصنيف VIP"
                            />
                            <FormControlLabel
                              control={
                                <Switch
                                  checked={principalForm.isUrgent}
                                  onChange={(e) => setPrincipalForm((prev) => ({ ...prev, isUrgent: e.target.checked }))}
                                />
                              }
                              label="حالة مستعجلة"
                            />
                          </Stack>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <TextField
                            fullWidth
                            label="ملاحظات سريعة للجراحين/المقدمين"
                            multiline
                            rows={2}
                            value={principalForm.emergencyNotes}
                            onChange={(e) => setPrincipalForm((prev) => ({ ...prev, emergencyNotes: e.target.value }))}
                            placeholder="هذا الحقل يظهر فوراً لمقدمي الخدمة عند مسح الباركود..."
                            size="small"
                          />
                        </Grid>
                      </Grid>
                    </Paper>
                  </Grid>
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
                    value={principalForm.phone}
                    onChange={handlePrincipalChange('phone')}
                    error={!!errors.phone}
                    helperText={errors.phone || 'يجب أن يكون ليبي (09x) و10 أرقام'}
                    size="small"
                    inputProps={{ maxLength: 10 }}
                  />
                </Grid>
                {!principalForm.isFastTrack && (
                  <>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        fullWidth
                        label="البريد الإلكتروني"
                        type="email"
                        value={principalForm.email}
                        onChange={handlePrincipalChange('email')}
                        error={!!errors.email}
                        helperText={errors.email}
                        size="small"
                      />
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <TextField
                        fullWidth
                        label="العنوان"
                        value={principalForm.address}
                        onChange={handlePrincipalChange('address')}
                        multiline
                        rows={2}
                        size="small"
                      />
                    </Grid>
                  </>
                )}
              </Grid>
            )}
          </div>
        </Box>

        <Divider />
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'flex-end', gap: 2, bgcolor: 'background.default' }}>
          <Button variant="outlined" onClick={() => navigate('/members')}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
            onClick={handleSubmit}
            disabled={loading}
          >
            {loading ? 'جاري الحفظ...' : 'حفظ البيانات'}
          </Button>
        </Box>
      </MainCard>
    </>
  );
};

export default UnifiedMemberCreate;
