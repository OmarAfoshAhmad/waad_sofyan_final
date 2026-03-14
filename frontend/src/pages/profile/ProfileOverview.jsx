/**
 * Profile Overview Page - Enterprise Clean Design
 *
 * Phase C - Simplified Profile
 * - READ-ONLY user information display
 * - Simple Change Password form with API integration
 * - Arabic labels only
 * - No complex tabs or permissions
 *
 * Last Updated: 2024-12-21
 */

import { useState, useContext } from 'react';
import {
  Box,
  Grid,
  Stack,
  Typography,
  Avatar,
  Divider,
  TextField,
  Button,
  Alert,
  InputAdornment,
  IconButton,
  LinearProgress,
  Chip
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import PersonIcon from '@mui/icons-material/Person';
import LockIcon from '@mui/icons-material/Lock';

// Project imports
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { AuthContext } from 'contexts/AuthContext';
import { strengthColor, strengthIndicator } from 'utils/password-strength';
import { profileService } from 'services/api/profile.service';

// ==============================|| ROLE LABELS (ARABIC) ||============================== //

const ROLE_LABELS = {
  SUPER_ADMIN: 'مدير النظام',
  ACCOUNTANT: 'محاسب',
  MEDICAL_REVIEWER: 'مراجع طبي',
  PROVIDER_STAFF: 'موظف مقدم خدمة',
  EMPLOYER_ADMIN: 'مدير جهة العمل',
  DATA_ENTRY: 'مدخل بيانات',
  FINANCE_VIEWER: 'مشاهد مالي'
};

const ROLE_COLORS = {
  SUPER_ADMIN: 'error',
  ACCOUNTANT: 'warning',
  MEDICAL_REVIEWER: 'secondary',
  PROVIDER_STAFF: 'info',
  EMPLOYER_ADMIN: 'primary',
  DATA_ENTRY: 'default',
  FINANCE_VIEWER: 'default'
};

// ==============================|| PASSWORD STRENGTH LABELS (ARABIC) ||============================== //

const STRENGTH_LABELS = {
  Poor: 'ضعيفة',
  Weak: 'ضعيفة جداً',
  Normal: 'متوسطة',
  Good: 'جيدة',
  Strong: 'قوية'
};

// ==============================|| AVATAR HELPER ||============================== //

// Generate deterministic color from string
function stringToColor(string) {
  let hash = 0;
  let i;

  for (i = 0; i < string.length; i += 1) {
    hash = string.charCodeAt(i) + ((hash << 5) - hash);
  }

  let color = '#';

  for (i = 0; i < 3; i += 1) {
    const value = (hash >> (i * 8)) & 0xff;
    color += `00${value.toString(16)}`.slice(-2);
  }

  return color;
}

// Get avatar props with fallback
function getAvatarProps(user, size = { width: '5.0rem', height: '5.0rem' }) {
  if (user?.profileImageUrl) {
    return { src: user.profileImageUrl, sx: size };
  }

  // Fallback to first letter
  const name = user?.fullName || user?.name || user?.username || 'U';
  const firstLetter = name.charAt(0).toUpperCase();
  const bgColor = stringToColor(user?.username || 'default');

  return {
    sx: { ...size, bgcolor: bgColor, color: '#fff', fontSize: '2rem' },
    children: firstLetter
  };
}

// ==============================|| INFO ROW COMPONENT ||============================== //

const InfoRow = ({ label, value }) => (
  <Stack direction="row" spacing={2} alignItems="center" sx={{ py: '0.75rem' }}>
    <Typography variant="body2" color="text.secondary" sx={{ minWidth: '8.75rem', fontWeight: 500 }}>
      {label}
    </Typography>
    <Typography variant="body1">{value ?? '—'}</Typography>
  </Stack>
);

// ==============================|| PROFILE OVERVIEW ||============================== //

export default function ProfileOverview() {
  const { user } = useContext(AuthContext);

  // Password change state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [alert, setAlert] = useState({ show: false, type: 'success', message: '' });

  // Password strength
  const strength = strengthIndicator(newPassword);
  const strengthColorObj = strengthColor(strength);
  const strengthLabel = STRENGTH_LABELS[strengthColorObj.label] ?? strengthColorObj.label;

  // Validation
  const currentPasswordEmpty = currentPassword.length === 0;
  const passwordTooShort = newPassword.length > 0 && newPassword.length < 8;
  const passwordMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
  const canSubmit = currentPassword.length > 0 && newPassword.length >= 8 && newPassword === confirmPassword && !isSubmitting;

  // Get user role(s)
  const userRoles = Array.isArray(user?.roles) ? user.roles : user?.role ? [user.role] : [];
  const primaryRole = userRoles[0] ?? 'USER';
  const roleLabel = ROLE_LABELS[primaryRole] ?? primaryRole;
  const roleColor = ROLE_COLORS[primaryRole] ?? 'default';

  // Get linked entity name (Employer or Provider - NO InsuranceCompany concept)
  const getLinkedEntity = () => {
    if (user?.employerName) return user.employerName;
    if (user?.providerName) return user.providerName;
    if (user?.companyName) return user.companyName;
    return 'شركة وعد (TPA)';
  };

  // Get avatar props for profile display
  const avatarProps = getAvatarProps(user);

  // Handle password change
  const handlePasswordChange = async () => {
    if (!canSubmit) return;

    setIsSubmitting(true);
    setAlert({ show: false, type: 'success', message: '' });

    try {
      // Call backend API to change password
      await profileService.changePassword({
        currentPassword,
        newPassword,
        confirmPassword
      });

      // Success
      setAlert({
        show: true,
        type: 'success',
        message: 'تم تغيير كلمة المرور بنجاح'
      });

      // Clear form
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      // Extract error message from API response
      const errorMessage = error?.response?.data?.message ?? error?.message ?? 'حدث خطأ أثناء تغيير كلمة المرور';

      setAlert({
        show: true,
        type: 'error',
        message: errorMessage
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title="الملف الشخصي"
        subtitle="عرض معلومات الحساب وتغيير كلمة المرور"
        icon={PersonIcon}
        breadcrumbs={[{ title: 'الرئيسية', url: '/dashboard' }, { title: 'الملف الشخصي' }]}
      />

      <Grid container spacing={3}>
        {/* Profile Information Card */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" alignItems="center" spacing={1}>
                <PersonIcon sx={{ color: 'primary.main' }} />
                <Typography variant="h5">معلومات الحساب</Typography>
              </Stack>
            }
          >
            <Stack spacing={0} divider={<Divider />}>
              {/* Avatar & Name */}
              <Stack direction="row" spacing={3} alignItems="center" sx={{ py: '1.0rem' }}>
                <Avatar {...avatarProps} />
                <Stack spacing={0.5}>
                  <Typography variant="h4">{user?.fullName || user?.name || user?.username || 'مستخدم'}</Typography>
                  <Typography variant="body2" color="text.secondary" dir="ltr">
                    {user?.username ?? '—'}
                  </Typography>
                </Stack>
              </Stack>

              {/* Role */}
              <Stack direction="row" spacing={2} alignItems="center" sx={{ py: '0.75rem' }}>
                <Typography variant="body2" color="text.secondary" sx={{ minWidth: '8.75rem', fontWeight: 500 }}>
                  الدور
                </Typography>
                <Chip label={roleLabel} color={roleColor} size="small" variant="outlined" />
              </Stack>

              {/* Linked Entity */}
              <InfoRow label="جهة الارتباط" value={getLinkedEntity()} />

              {/* Last Login (optional) */}
              {user?.lastLogin && <InfoRow label="آخر تسجيل دخول" value={new Date(user.lastLogin).toLocaleString('en-US')} />}
            </Stack>
          </MainCard>
        </Grid>

        {/* Change Password Card */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" alignItems="center" spacing={1}>
                <LockIcon sx={{ color: 'primary.main' }} />
                <Typography variant="h5">تغيير كلمة المرور</Typography>
              </Stack>
            }
          >
            <Stack spacing={3}>
              {/* Alert */}
              {alert.show && (
                <Alert severity={alert.type} onClose={() => setAlert({ ...alert, show: false })}>
                  {alert.message}
                </Alert>
              )}

              {/* Current Password */}
              <TextField
                fullWidth
                label="كلمة المرور الحالية"
                type={showCurrentPassword ? 'text' : 'password'}
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowCurrentPassword(!showCurrentPassword)} edge="end">
                          {showCurrentPassword ? <VisibilityOff /> : <Visibility />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }
                }}
              />

              {/* New Password */}
              <Stack spacing={1}>
                <TextField
                  fullWidth
                  label="كلمة المرور الجديدة"
                  type={showNewPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  error={passwordTooShort}
                  helperText={passwordTooShort ? 'كلمة المرور يجب أن تكون 8 أحرف على الأقل' : ''}
                  slotProps={{
                    input: {
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton onClick={() => setShowNewPassword(!showNewPassword)} edge="end">
                            {showNewPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      )
                    }
                  }}
                />

                {/* Password Strength */}
                {newPassword.length > 0 && (
                  <Stack spacing={0.5}>
                    <LinearProgress
                      variant="determinate"
                      value={(strength / 5) * 100}
                      sx={{
                        height: '0.375rem',
                        borderRadius: 1,
                        bgcolor: 'grey.200',
                        '& .MuiLinearProgress-bar': {
                          bgcolor: strengthColorObj.color
                        }
                      }}
                    />
                    <Typography variant="caption" sx={{ color: strengthColorObj.color }}>
                      قوة كلمة المرور: {strengthLabel}
                    </Typography>
                  </Stack>
                )}
              </Stack>

              {/* Confirm Password */}
              <TextField
                fullWidth
                label="تأكيد كلمة المرور"
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                error={passwordMismatch}
                helperText={passwordMismatch ? 'كلمات المرور غير متطابقة' : ''}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowConfirmPassword(!showConfirmPassword)} edge="end">
                          {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }
                }}
              />

              {/* Submit Button */}
              <Button variant="contained" size="large" onClick={handlePasswordChange} disabled={!canSubmit} sx={{ mt: 1 }}>
                {isSubmitting ? 'جاري التغيير...' : 'تغيير كلمة المرور'}
              </Button>
            </Stack>
          </MainCard>
        </Grid>
      </Grid>
    </Box>
  );
}


