import { useSearchParams } from 'react-router-dom';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';
import { alpha, useTheme } from '@mui/material/styles';

// project imports
import useAuth from 'hooks/useAuth';
import AuthWrapper from 'sections/auth/AuthWrapper';
import AuthLogin from 'sections/auth/jwt/AuthLogin';
import Logo from 'components/logo';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// assets - security icons
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import HttpsOutlinedIcon from '@mui/icons-material/HttpsOutlined';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';

// ================================|| JWT - LOGIN ||================================ //

export default function Login() {
  const { isLoggedIn } = useAuth();
  const theme = useTheme();
  const { companyName, getLogoSrc, hasLogo } = useCompanySettings();

  const [searchParams] = useSearchParams();
  const auth = searchParams.get('auth');
  console.log(auth);

  return (
    <AuthWrapper>
      <Grid container spacing={3}>
        {/* Header Section */}
        <Grid size={12}>
          <Stack sx={{ alignItems: 'center', mb: 1 }}>
            {/* Internal Branding - Simplified */}
            <Box sx={{ mb: '1.0rem', textAlign: 'center' }}>
              {hasLogo() ? (
                <Box
                  component="img"
                  src={getLogoSrc()}
                  alt={companyName}
                  sx={{ height: '3.75rem', width: 'auto', objectFit: 'contain', mb: 1 }}
                />
              ) : (
                <Logo />
              )}
              <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                وعد
              </Typography>
            </Box>

            {/* Welcome Text - Clean without icon */}
            <Typography
              variant="h3"
              sx={{
                fontWeight: 700,
                background: `linear-gradient(135deg, ${theme.palette.primary.main}, ${theme.palette.primary.dark})`,
                backgroundClip: 'text',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                mb: 1
              }}
            >
              مرحباً بك
            </Typography>
          </Stack>
        </Grid>

        {/* Divider */}
        <Grid size={12}>
          <Divider sx={{ my: 1 }}>
            <Typography variant="caption" color="text.secondary">
              بيانات الدخول
            </Typography>
          </Divider>
        </Grid>

        {/* Login Form */}
        <Grid size={12}>
          <AuthLogin isDemo={isLoggedIn} />
        </Grid>

        {/* Registration Notice */}
        <Grid size={12}>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: 'block', textAlign: 'center', mt: '0.75rem', fontSize: '0.75rem' }}
          >
            🔐 التسجيل متاح فقط من داخل النظام عبر مسؤول الحسابات
          </Typography>
        </Grid>
      </Grid>
    </AuthWrapper>
  );
}


