/**
 * RBAC User Edit Page - Phase D3 Step 3
 * 2-Step Stepper: User Info → Roles Assignment
 *
 * ⚠️ Key Features:
 * 1. Step 1: Basic info (fullName, email, phone) - username readonly
 * 2. Step 2: Assign roles (multi-select)
 * 3. Password change optional (only if filled)
 * 4. SUPER_ADMIN only access
 * 5. Arabic UI
 */

import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// MUI Components
import {
  Box,
  Grid,
  TextField,
  Button,
  Stepper,
  Step,
  StepLabel,
  Typography,
  FormControlLabel,
  Switch,
  Alert,
  CircularProgress,
  InputAdornment,
  IconButton,
  Checkbox,
  Paper,
  Stack,
  Chip,
  Collapse,
  MenuItem
} from '@mui/material';

// MUI Icons
import EditIcon from '@mui/icons-material/Edit';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import SaveIcon from '@mui/icons-material/Save';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import PersonIcon from '@mui/icons-material/Person';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import PhoneIcon from '@mui/icons-material/Phone';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import KeyIcon from '@mui/icons-material/Key';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import TbaFormSection from 'components/tba/form/TbaFormSection';
import CircularLoader from 'components/CircularLoader';

// Contexts
import { useTableRefresh } from 'contexts/TableRefreshContext';

// Services
import usersService from 'services/rbac/users.service';
import { SystemRole, getRoleDisplayName } from 'constants/rbac';
import { refreshToken } from 'services/auth/tokenRefresh.service';
import providersService from 'services/api/providers.service';
import employersService from 'services/api/employers.service';

// Snackbar
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const STEPS = ['معلومات المستخدم', 'تعيين الأدوار'];

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get role color based on role name
 */
const getRoleColor = (roleName) => {
  const roleColors = {
    SUPER_ADMIN: 'error',
    ACCOUNTANT: 'warning',
    MEDICAL_REVIEWER: 'secondary',
    PROVIDER_STAFF: 'info',
    EMPLOYER_ADMIN: 'primary',
    DATA_ENTRY: 'default',
    FINANCE_VIEWER: 'default'
  };
  return roleColors[roleName] || 'primary';
};

/**
 * Validate Step 1 fields (Edit mode - NO password changes)
 */
const validateStep1 = (form) => {
  const errors = {};

  // Full name validation
  if (!form.fullName?.trim()) {
    errors.fullName = 'الاسم الكامل مطلوب';
  }

  // Email validation
  if (!form.email?.trim()) {
    errors.email = 'البريد الإلكتروني مطلوب';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = 'البريد الإلكتروني غير صالح';
  }

  // NO password validation - password changes require separate endpoint

  return errors;
};

// ============================================================================
// STEP 1 COMPONENT - User Info (Edit Mode)
// ============================================================================

const Step1UserInfoEdit = ({ form, setForm, errors, setErrors }) => {
  const handleChange = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  return (
    <TbaFormSection title="معلومات المستخدم الأساسية" icon={PersonIcon}>
      <Grid container spacing={2.5}>
        {/* Username (readonly) */}
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="اسم المستخدم"
            value={form.username}
            disabled
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PersonIcon color="action" />
                </InputAdornment>
              )
            }}
            helperText="لا يمكن تغيير اسم المستخدم"
          />
        </Grid>

        {/* Full Name */}
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="الاسم الكامل"
            value={form.fullName}
            onChange={handleChange('fullName')}
            error={!!errors.fullName}
            helperText={errors.fullName}
            required
          />
        </Grid>

        {/* Email */}
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="البريد الإلكتروني"
            type="email"
            value={form.email}
            onChange={handleChange('email')}
            error={!!errors.email}
            helperText={errors.email}
            required
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <EmailIcon color="action" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Phone */}
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="رقم الهاتف"
            value={form.phone}
            onChange={handleChange('phone')}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PhoneIcon color="action" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Active Status */}
        <Grid size={{ xs: 12 }}>
          <FormControlLabel
            control={<Switch checked={form.active} onChange={handleChange('active')} color="primary" />}
            label="المستخدم نشط"
          />
        </Grid>
      </Grid>
    </TbaFormSection>
  );
};

// ============================================================================
// STEP 1.5 COMPONENT - Reset Password (Edit Mode - Super Admin)
// ============================================================================

const Step1ResetPassword = ({ userId, form, setForm, errors, setErrors }) => {
  const [isResetting, setIsResetting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleChange = (field) => (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  const handleResetPassword = async () => {
    // Validation
    const resetErrors = {};
    if (!form.newPassword || form.newPassword.length < 8) {
      resetErrors.newPassword = 'كلمة المرور يجب أن تكون 8 أحرف على الأقل';
    }
    if (form.newPassword !== form.confirmPassword) {
      resetErrors.confirmPassword = 'كلمات المرور غير متطابقة';
    }

    if (Object.keys(resetErrors).length > 0) {
      setErrors((prev) => ({ ...prev, ...resetErrors }));
      return;
    }

    setIsResetting(true);
    try {
      await usersService.resetPassword(userId, form.newPassword);

      openSnackbar({
        open: true,
        message: 'تم إعادة تعيين كلمة المرور بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      // Clear fields after success
      setForm((prev) => ({ ...prev, newPassword: '', confirmPassword: '' }));
    } catch (err) {
      const errorMessage = err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل إعادة تعيين كلمة المرور';
      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <TbaFormSection title="إعادة تعيين كلمة المرور" icon={KeyIcon} sx={{ mt: 3 }}>
      <Grid container spacing={2.5} alignItems="flex-start">
        <Grid size={{ xs: 12 }}>
          <Alert severity="warning" sx={{ mb: 1 }}>
            هذا القسم يُستخدم لإجبار تغيير كلمة مرور المستخدم من قِبل مدير النظام.
          </Alert>
        </Grid>

        {/* New Password */}
        <Grid size={{ xs: 12, sm: 5 }}>
          <TextField
            fullWidth
            label="كلمة المرور الجديدة"
            type={showPassword ? 'text' : 'password'}
            value={form.newPassword || ''}
            onChange={handleChange('newPassword')}
            error={!!errors.newPassword}
            helperText={errors.newPassword}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                    {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  </IconButton>
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Confirm Password */}
        <Grid size={{ xs: 12, sm: 5 }}>
          <TextField
            fullWidth
            label="تأكيد كلمة المرور"
            type={showConfirmPassword ? 'text' : 'password'}
            value={form.confirmPassword || ''}
            onChange={handleChange('confirmPassword')}
            error={!!errors.confirmPassword}
            helperText={errors.confirmPassword}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowConfirmPassword(!showConfirmPassword)} edge="end">
                    {showConfirmPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  </IconButton>
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Action Button */}
        <Grid size={{ xs: 12, sm: 2 }} sx={{ display: 'flex', mt: 1 }}>
          <Button
            variant="contained"
            color="warning"
            fullWidth
            onClick={handleResetPassword}
            disabled={isResetting || !form.newPassword || !form.confirmPassword}
            startIcon={isResetting ? <CircularProgress size={20} color="inherit" /> : <LockIcon />}
            sx={{ height: 48 }}
          >
            إعادة تعيين
          </Button>
        </Grid>
      </Grid>
    </TbaFormSection>
  );
};


// ============================================================================
// STEP 2 COMPONENT - Roles Assignment
// ============================================================================

const Step2Roles = ({
  selectedRoles,
  setSelectedRoles,
  allRoles,
  loading,
  form,
  setForm,
  providerOptions,
  employerOptions,
  errors,
  setErrors
}) => {
  // Check if EMPLOYER_ADMIN role is selected
  const hasEmployerAdminRole = selectedRoles.some((roleId) => {
    const role = allRoles.find((r) => r?.id === roleId);
    return role?.name === 'EMPLOYER_ADMIN' || role?.name === 'EMPLOYER_USER';
  });

  const hasProviderStaffRole = selectedRoles.some((roleId) => {
    const role = allRoles.find((r) => r?.id === roleId);
    return role?.name === 'PROVIDER_STAFF';
  });

  const handleToggleRole = (roleId) => {
    setSelectedRoles((prev) => {
      if (prev.includes(roleId)) {
        return prev.filter((id) => id !== roleId);
      }
      return [...prev, roleId];
    });
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <TbaFormSection title="تعيين الأدوار للمستخدم" subtitle="اختر دوراً واحداً أو أكثر" icon={AdminPanelSettingsIcon}>
      <Alert severity="info" sx={{ mb: 2 }}>
        الأدوار تحدد صلاحيات المستخدم في النظام. يمكنك تعيين أكثر من دور للمستخدم.
      </Alert>

      <Grid container spacing={2}>
        {allRoles.map((role) => {
          const isSelected = selectedRoles.includes(role?.id);
          const roleName = role?.name || '';
          const isProtected = roleName === 'SUPER_ADMIN';

          return (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={role?.id}>
              <Paper
                onClick={() => !isProtected && handleToggleRole(role?.id)}
                elevation={isSelected ? 3 : 0}
                sx={{
                  p: 2,
                  cursor: isProtected ? 'not-allowed' : 'pointer',
                  border: '2px solid',
                  borderColor: isSelected ? 'primary.main' : 'divider',
                  borderRadius: 2,
                  bgcolor: isSelected ? 'primary.lighter' : 'background.paper',
                  opacity: isProtected ? 0.6 : 1,
                  transition: 'all 0.2s ease',
                  '&:hover': {
                    borderColor: isProtected ? 'divider' : 'primary.light',
                    bgcolor: isProtected ? 'background.paper' : isSelected ? 'primary.lighter' : 'grey.50'
                  }
                }}
              >
                <Stack direction="row" spacing={1.5} alignItems="flex-start">
                  <Checkbox
                    checked={isSelected}
                    disabled={isProtected}
                    onChange={() => handleToggleRole(role?.id)}
                    sx={{ p: 0, mt: 0.25 }}
                  />
                  <Box sx={{ flex: 1 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="subtitle2" fontWeight="medium">
                        {role?.name || '-'}
                      </Typography>
                      <Chip
                        label={roleName}
                        size="small"
                        color={getRoleColor(roleName)}
                        variant="light"
                        sx={{ height: 20, fontSize: '0.7rem' }}
                      />
                    </Stack>
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                      {role?.description || `${role?.permissions?.length || 0} صلاحية`}
                    </Typography>
                  </Box>
                  {isSelected && <CheckCircleIcon color="primary" fontSize="small" />}
                </Stack>
              </Paper>
            </Grid>
          );
        })}
      </Grid>

      {allRoles.length === 0 && <Alert severity="warning">لا توجد أدوار متاحة في النظام</Alert>}

      {hasProviderStaffRole && (
        <Box sx={{ mt: 3, pt: 2, borderTop: '1px dashed', borderColor: 'info.main' }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            يجب اختيار مقدم خدمة عند تعيين دور <strong>PROVIDER_STAFF</strong>.
          </Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                select
                label="مقدم الخدمة"
                value={form.providerId ?? ''}
                onChange={(e) => {
                  const value = e.target.value;
                  setForm((prev) => ({ ...prev, providerId: value ? Number(value) : null }));
                  if (errors.providerId) {
                    setErrors((prev) => ({ ...prev, providerId: null }));
                  }
                }}
                error={!!errors.providerId}
                helperText={errors.providerId || 'اختر مقدم الخدمة المرتبط بهذا المستخدم'}
                required
              >
                <MenuItem value="">-- اختر مقدم الخدمة --</MenuItem>
                {providerOptions.map((provider) => (
                  <MenuItem key={provider.id} value={provider.id}>
                    {provider.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
          </Grid>
        </Box>
      )}

      {hasEmployerAdminRole && (
        <Box sx={{ mt: 3, pt: 2, borderTop: '1px dashed', borderColor: 'primary.main' }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            يجب اختيار صاحب عمل عند تعيين دور <strong>EMPLOYER_ADMIN</strong>.
          </Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                select
                label="صاحب العمل"
                value={form.employerId ?? ''}
                onChange={(e) => {
                  const value = e.target.value;
                  setForm((prev) => ({ ...prev, employerId: value ? Number(value) : null }));
                  if (errors.employerId) {
                    setErrors((prev) => ({ ...prev, employerId: null }));
                  }
                }}
                error={!!errors.employerId}
                helperText={errors.employerId || 'اختر صاحب العمل المرتبط بهذا المستخدم'}
                required
              >
                <MenuItem value="">-- اختر صاحب العمل --</MenuItem>
                {employerOptions.map((employer) => (
                  <MenuItem key={employer.id} value={employer.id}>
                    {employer.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
          </Grid>
        </Box>
      )}

      {/* Custom Permissions for EMPLOYER users */}
      {hasEmployerAdminRole && (
        <Box sx={{ mt: 3, pt: 2, borderTop: '1px dashed', borderColor: 'warning.main' }}>
          <Alert severity="info" icon={<AdminPanelSettingsIcon />} sx={{ mb: 2 }}>
            <Typography variant="body2" fontWeight="medium" gutterBottom>
              صلاحيات مخصصة لمستخدم الشريك
            </Typography>
            <Typography variant="caption">حدد ما يمكن لهذا المستخدم رؤيته وإدارته في النظام</Typography>
          </Alert>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.canViewClaims}
                    onChange={(e) => setForm({ ...form, canViewClaims: e.target.checked })}
                    color="primary"
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight="medium">
                      المطالبات
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكن رؤية وإدارة المطالبات
                    </Typography>
                  </Box>
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.canViewVisits}
                    onChange={(e) => setForm({ ...form, canViewVisits: e.target.checked })}
                    color="primary"
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight="medium">
                      الزيارات
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكن رؤية وإدارة الزيارات
                    </Typography>
                  </Box>
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.canViewReports}
                    onChange={(e) => setForm({ ...form, canViewReports: e.target.checked })}
                    color="primary"
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight="medium">
                      التقارير
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكن رؤية التقارير التحليلية
                    </Typography>
                  </Box>
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.canViewMembers}
                    onChange={(e) => setForm({ ...form, canViewMembers: e.target.checked })}
                    color="primary"
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight="medium">
                      المؤمنين
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكن رؤية وإدارة المؤمنين
                    </Typography>
                  </Box>
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.canViewBenefitPolicies}
                    onChange={(e) => setForm({ ...form, canViewBenefitPolicies: e.target.checked })}
                    color="primary"
                  />
                }
                label={
                  <Box>
                    <Typography variant="body2" fontWeight="medium">
                      وثائق المنافع
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكن رؤية وثائق التغطية التأمينية
                    </Typography>
                  </Box>
                }
              />
            </Grid>
          </Grid>
        </Box>
      )}

      {/* Selected roles summary */}
      {selectedRoles.length > 0 && (
        <Box sx={{ mt: 3, pt: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            الأدوار المختارة ({selectedRoles.length}):
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {selectedRoles.map((roleId) => {
              const role = allRoles.find((r) => r?.id === roleId);
              return (
                <Chip
                  key={roleId}
                  label={role?.name || roleId}
                  color={getRoleColor(role?.name)}
                  size="small"
                  onDelete={() => handleToggleRole(roleId)}
                />
              );
            })}
          </Stack>
        </Box>
      )}
    </TbaFormSection>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const UserEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { triggerRefresh } = useTableRefresh();

  // State
  const [activeStep, setActiveStep] = useState(0);
  const [form, setForm] = useState({
    username: '',
    fullName: '',
    email: '',
    phone: '',
    active: true,
    newPassword: '',
    confirmPassword: '',
    employerId: null,
    providerId: null,
    // Custom permissions for EMPLOYER users
    canViewClaims: true,
    canViewVisits: true,
    canViewReports: true,
    canViewMembers: true,
    canViewBenefitPolicies: true
  });
  const [originalRoleIds, setOriginalRoleIds] = useState([]);
  const [selectedRoles, setSelectedRoles] = useState([]);
  const [allRoles, setAllRoles] = useState([]);
  const [providerOptions, setProviderOptions] = useState([]);
  const [employerOptions, setEmployerOptions] = useState([]);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [rolesLoading, setRolesLoading] = useState(true);
  const [submitError, setSubmitError] = useState(null);

  // Load user and roles on mount
  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    try {
      setLoading(true);
      setRolesLoading(true);

      const [userRes, providersRes, employersRes] = await Promise.all([
        usersService.getUserById(id),
        providersService.getSelector().catch(() => []),
        employersService.getEmployerSelectors().catch(() => [])
      ]);

      const user = userRes?.data?.data || userRes?.data;
      const roles = Object.values(SystemRole).map((name, idx) => ({ id: idx + 1, name, displayName: getRoleDisplayName(name) }));

      if (user) {
        setForm({
          username: user.username || '',
          fullName: user.fullName || '',
          email: user.email || '',
          phone: user.phone || '',
          active: user.active !== false && user.enabled !== false,
          employerId: user.employerId || null,
          providerId: user.providerId || null,
          // Custom permissions for EMPLOYER users
          canViewClaims: user.canViewClaims !== false,
          canViewVisits: user.canViewVisits !== false,
          canViewReports: user.canViewReports !== false,
          canViewMembers: user.canViewMembers !== false,
          canViewBenefitPolicies: user.canViewBenefitPolicies !== false
        });

        // Set current roles (supports both `roles[]` and single `role`/`userType` response shapes)
        const currentRoleNames = Array.isArray(user.roles) && user.roles.length > 0
          ? user.roles.map((r) => (typeof r === 'string' ? r : r?.name || r?.role)).filter(Boolean)
          : [user.role || user.userType].filter(Boolean);

        const currentRoleIds = roles.filter((r) => currentRoleNames.includes(r.name)).map((r) => r.id);
        setOriginalRoleIds(currentRoleIds);
        setSelectedRoles(currentRoleIds);
      }

      setAllRoles(Array.isArray(roles) ? roles : []);
      setProviderOptions(
        Array.isArray(providersRes)
          ? providersRes
            .map((provider) => ({
              id: provider?.id,
              label: provider?.name || provider?.providerName || provider?.label || provider?.code || `#${provider?.id}`
            }))
            .filter((provider) => provider.id != null)
          : []
      );
      setEmployerOptions(
        Array.isArray(employersRes)
          ? employersRes
            .map((employer) => ({
              id: employer?.id,
              label: employer?.label || employer?.name || employer?.companyName || `#${employer?.id}`
            }))
            .filter((employer) => employer.id != null)
          : []
      );
    } catch (err) {
      console.error('[UserEdit] Load error:', err);
      setSubmitError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل بيانات المستخدم');
    } finally {
      setLoading(false);
      setRolesLoading(false);
    }
  };

  // ========================================
  // STEP NAVIGATION
  // ========================================

  const handleNext = () => {
    if (activeStep === 0) {
      const step1Errors = validateStep1(form);
      if (Object.keys(step1Errors).length > 0) {
        setErrors(step1Errors);
        return;
      }
    }
    setActiveStep((prev) => prev + 1);
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  // ========================================
  // SUBMIT HANDLER
  // ========================================

  const handleSubmit = useCallback(async () => {
    try {
      setSaving(true);
      setSubmitError(null);

      const selectedRole = selectedRoles.length > 0 ? allRoles.find((r) => r?.id === selectedRoles[0]) : null;

      if (!selectedRole?.name) {
        setErrors((prev) => ({ ...prev, userType: 'يجب اختيار دور للمستخدم' }));
        throw new Error('يجب اختيار دور للمستخدم');
      }

      if (selectedRole.name === 'PROVIDER_STAFF' && !form.providerId) {
        setErrors((prev) => ({ ...prev, providerId: 'اختيار مقدم الخدمة مطلوب لهذا الدور' }));
        throw new Error('اختيار مقدم الخدمة مطلوب لهذا الدور');
      }

      if (selectedRole.name === 'EMPLOYER_ADMIN' && !form.employerId) {
        setErrors((prev) => ({ ...prev, employerId: 'اختيار صاحب العمل مطلوب لهذا الدور' }));
        throw new Error('اختيار صاحب العمل مطلوب لهذا الدور');
      }

      // Prepare update payload
      const payload = {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone?.trim() || null,
        active: form.active,
        userType: selectedRole.name,
        employerId: selectedRole.name === 'EMPLOYER_ADMIN' ? form.employerId : null,
        providerId: selectedRole.name === 'PROVIDER_STAFF' ? form.providerId : null
      };

      // Add custom permissions if EMPLOYER role is selected
      const hasEmployerRole = selectedRoles.some((roleId) => {
        const role = allRoles.find((r) => r?.id === roleId);
        return role?.name === 'EMPLOYER_ADMIN' || role?.name === 'EMPLOYER_USER';
      });

      if (hasEmployerRole) {
        payload.canViewClaims = form.canViewClaims;
        payload.canViewVisits = form.canViewVisits;
        payload.canViewReports = form.canViewReports;
        payload.canViewMembers = form.canViewMembers;
        payload.canViewBenefitPolicies = form.canViewBenefitPolicies;
      }

      await usersService.updateUser(id, payload);

      // Success
      openSnackbar({
        open: true,
        message: 'تم تحديث المستخدم بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      // Refresh list and navigate
      triggerRefresh();
      navigate('/admin/users');
    } catch (err) {
      console.error('[UserEdit] Submit error:', err);
      const errorMessage = err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحديث المستخدم. يرجى المحاولة لاحقاً';
      setSubmitError(errorMessage);

      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setSaving(false);
    }
  }, [form, selectedRoles, allRoles, id, triggerRefresh, navigate]);

  // ========================================
  // LOADING STATE
  // ========================================

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularLoader />
      </Box>
    );
  }

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title={`تعديل المستخدم: ${form.username}`}
        subtitle="تعديل بيانات المستخدم وأدواره"
        icon={EditIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'المستخدمين', path: '/admin/users' },
          { label: 'تعديل' }
        ]}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/admin/users')}>
            العودة للقائمة
          </Button>
        }
      />

      {/* Stepper */}
      <MainCard sx={{ mb: 3 }}>
        <Stepper activeStep={activeStep} alternativeLabel>
          {STEPS.map((label, index) => (
            <Step key={label} completed={activeStep > index}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      </MainCard>

      {/* Error Alert */}
      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setSubmitError(null)}>
          {submitError}
        </Alert>
      )}

      {/* Step Content */}
      <MainCard>
        {activeStep === 0 && (<Box><Step1UserInfoEdit form={form} setForm={setForm} errors={errors} setErrors={setErrors} /><Step1ResetPassword userId={id} form={form} setForm={setForm} errors={errors} setErrors={setErrors} /></Box>)}

        {activeStep === 1 && (
          <Step2Roles
            selectedRoles={selectedRoles}
            setSelectedRoles={setSelectedRoles}
            allRoles={allRoles}
            loading={rolesLoading}
            form={form}
            setForm={setForm}
            providerOptions={providerOptions}
            employerOptions={employerOptions}
            errors={errors}
            setErrors={setErrors}
          />
        )}

        {/* Navigation Buttons */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3, pt: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Button variant="outlined" onClick={handleBack} disabled={activeStep === 0 || saving} startIcon={<ArrowForwardIcon />}>
            السابق
          </Button>

          {activeStep === STEPS.length - 1 ? (
            <Button
              variant="contained"
              onClick={handleSubmit}
              disabled={saving}
              startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
            >
              {saving ? 'جاري الحفظ...' : 'حفظ التغييرات'}
            </Button>
          ) : (
            <Button variant="contained" onClick={handleNext} endIcon={<ArrowBackIcon />}>
              التالي
            </Button>
          )}
        </Box>
      </MainCard>
    </Box>
  );
};

export default UserEdit;
