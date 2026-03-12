/**
 * User Create Page – Simplified
 * Single form: username, password, userType (single-select dropdown)
 * POST /admin/users with { username, password, userType, ...optional }
 * No assign-roles step. Redirect to /admin/users on success.
 */

import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

// MUI
import {
  Box,
  Grid,
  TextField,
  Button,
  Alert,
  CircularProgress,
  InputAdornment,
  IconButton,
  MenuItem
} from '@mui/material';

// MUI Icons
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import PhoneIcon from '@mui/icons-material/Phone';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import TbaFormSection from 'components/tba/form/TbaFormSection';

// Contexts
import { useTableRefresh } from 'contexts/TableRefreshContext';

// Services & Constants
import usersService from 'services/rbac/users.service';
import employersService from 'services/api/employers.service';
import { SystemRole, RoleDisplayNames } from 'constants/rbac';

// Snackbar
import { openSnackbar } from 'api/snackbar';

// Utils
import { validatePassword } from 'utils/passwordValidator';

// ============================================================================
// ROLE OPTIONS for dropdown (exclude SUPER_ADMIN – only backend can set that)
// ============================================================================

const USER_TYPE_OPTIONS = Object.values(SystemRole)
  .filter((role) => role !== 'SUPER_ADMIN')
  .map((role) => ({
    value: role,
    label: RoleDisplayNames[role]?.ar || role,
    labelEn: RoleDisplayNames[role]?.en || role
  }));

// ============================================================================
// VALIDATION
// ============================================================================

const validate = (form) => {
  const errors = {};

  if (!form.username?.trim()) {
    errors.username = 'اسم المستخدم مطلوب';
  } else if (form.username.length < 3) {
    errors.username = 'اسم المستخدم يجب أن يكون 3 أحرف على الأقل';
  } else if (form.username.length > 50) {
    errors.username = 'اسم المستخدم يجب أن لا يتجاوز 50 حرف';
  }

  if (!form.fullName?.trim()) {
    errors.fullName = 'الاسم الكامل مطلوب';
  }

  if (!form.email?.trim()) {
    errors.email = 'البريد الإلكتروني مطلوب';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = 'البريد الإلكتروني غير صالح';
  }

  const passwordValidation = validatePassword(form.password);
  if (!passwordValidation.valid) {
    errors.password = passwordValidation.errors.join(' • ');
  }

  if (form.password !== form.confirmPassword) {
    errors.confirmPassword = 'كلمة المرور غير متطابقة';
  }

  if (!form.userType) {
    errors.userType = 'نوع المستخدم مطلوب';
  }

  // Requirement: employerId is required for EMPLOYER_ADMIN
  if (form.userType === SystemRole.EMPLOYER_ADMIN && !form.employerId) {
    errors.employerId = 'يجب اختيار جهة العمل لمدير جهة العمل';
  }

  return errors;
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const UserCreate = () => {
  const navigate = useNavigate();
  const { triggerRefresh } = useTableRefresh();

  const [form, setForm] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    fullName: '',
    email: '',
    phone: '',
    userType: '',
    employerId: ''
  });

  const [employers, setEmployers] = useState([]);

  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // ========================================
  // INITIAL DATA
  // ========================================
  useState(() => {
    const fetchEmployers = async () => {
      try {
        const data = await employersService.getEmployerSelectors();
        setEmployers(data || []);
      } catch (err) {
        console.error('Failed to fetch employers:', err);
      }
    };
    fetchEmployers();
  }, []);

  const handleChange = (field) => (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: null }));
    }
  };

  // ========================================
  // SUBMIT – single API call, no assign-roles
  // ========================================

  const handleSubmit = useCallback(async () => {
    const validationErrors = validate(form);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    try {
      setLoading(true);
      setSubmitError(null);

      const payload = {
        username: form.username.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone?.trim() || null,
        userType: form.userType,
        employerId: form.userType === SystemRole.EMPLOYER_ADMIN ? form.employerId : null
      };

      await usersService.createUser(payload);

      openSnackbar({
        open: true,
        message: 'تم إنشاء المستخدم بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });

      triggerRefresh();
      navigate('/admin/users');
    } catch (err) {
      console.error('[UserCreate] Submit error:', err);
      const errorMessage =
        err?.response?.data?.messageAr || err?.response?.data?.message || err.message || 'فشل إنشاء المستخدم. يرجى المحاولة لاحقاً';
      setSubmitError(errorMessage);

      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  }, [form, triggerRefresh, navigate]);

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title="إنشاء مستخدم جديد"
        subtitle="إضافة مستخدم جديد للنظام"
        icon={PersonAddIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'المستخدمين', path: '/admin/users' },
          { label: 'إنشاء مستخدم' }
        ]}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/admin/users')}>
            العودة للقائمة
          </Button>
        }
      />

      {/* Error Alert */}
      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setSubmitError(null)}>
          {submitError}
        </Alert>
      )}

      <MainCard>
        <TbaFormSection title="معلومات المستخدم" icon={PersonIcon}>
          <Grid container spacing={2.5}>
            {/* Username */}
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="اسم المستخدم"
                value={form.username}
                onChange={handleChange('username')}
                error={!!errors.username}
                helperText={errors.username}
                required
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <PersonIcon color="action" />
                    </InputAdornment>
                  )
                }}
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

            {/* Password */}
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="كلمة المرور"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={handleChange('password')}
                error={!!errors.password}
                helperText={errors.password}
                required
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <LockIcon color="action" />
                    </InputAdornment>
                  ),
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
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="تأكيد كلمة المرور"
                type={showConfirmPassword ? 'text' : 'password'}
                value={form.confirmPassword}
                onChange={handleChange('confirmPassword')}
                error={!!errors.confirmPassword}
                helperText={errors.confirmPassword}
                required
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <LockIcon color="action" />
                    </InputAdornment>
                  ),
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

            {/* User Type – Single Select Dropdown */}
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                fullWidth
                label="نوع المستخدم"
                value={form.userType}
                onChange={handleChange('userType')}
                error={!!errors.userType}
                helperText={errors.userType || 'اختر دور المستخدم في النظام'}
                required
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <AdminPanelSettingsIcon color="action" />
                    </InputAdornment>
                  )
                }}
              >
                {USER_TYPE_OPTIONS.map((opt) => (
                  <MenuItem key={opt.value} value={opt.value}>
                    {opt.label} ({opt.labelEn})
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            {/* Employer Selection – Conditional for EMPLOYER_ADMIN */}
            {form.userType === SystemRole.EMPLOYER_ADMIN && (
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  select
                  fullWidth
                  label="جهة العمل (Employer)"
                  value={form.employerId}
                  onChange={handleChange('employerId')}
                  error={!!errors.employerId}
                  helperText={errors.employerId || 'اختر جهة العمل المرتبطة بهذا المستخدم'}
                  required
                >
                  {employers.map((emp) => (
                    <MenuItem key={emp.id} value={emp.id}>
                      {emp.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
            )}
          </Grid>
        </TbaFormSection>

        {/* Submit */}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3, pt: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
            size="large"
          >
            {loading ? 'جاري الحفظ...' : 'حفظ المستخدم'}
          </Button>
        </Box>
      </MainCard>
    </Box>
  );
};

export default UserCreate;
