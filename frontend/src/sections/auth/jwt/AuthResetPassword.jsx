import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

// material-ui
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import InputLabel from '@mui/material/InputLabel';
import OutlinedInput from '@mui/material/OutlinedInput';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// third-party
import * as Yup from 'yup';
import { Formik } from 'formik';

// project imports
import useAuth from 'hooks/useAuth';
import IconButton from 'components/@extended/IconButton';
import AnimateButton from 'components/@extended/AnimateButton';
import authService from 'services/api/auth.service';

import { strengthColor, strengthIndicator } from 'utils/password-strength';
import { openSnackbar } from 'api/snackbar';

// assets
import EyeOutlined from '@ant-design/icons/EyeOutlined';
import EyeInvisibleOutlined from '@ant-design/icons/EyeInvisibleOutlined';

// ============================|| JWT - RESET PASSWORD ||============================ //

export default function AuthResetPassword() {
  const navigate = useNavigate();

  const { isLoggedIn } = useAuth();

  const [level, setLevel] = useState();
  const [showPassword, setShowPassword] = useState(false);
  const handleClickShowPassword = () => {
    setShowPassword(!showPassword);
  };

  const handleMouseDownPassword = (event) => {
    event.preventDefault();
  };

  const changePassword = (value) => {
    const temp = strengthIndicator(value);
    setLevel(strengthColor(temp));
  };

  const [searchParams] = useSearchParams();
  const auth = searchParams.get('auth'); // get auth and set route based on that
  const token = searchParams.get('token') || '';
  const modeFromQuery = (searchParams.get('mode') || '').toUpperCase();
  const emailFromQuery = searchParams.get('email') || '';

  const [resetConfig, setResetConfig] = useState({ method: 'TOKEN', otpLength: 6 });

  useEffect(() => {
    const loadResetConfig = async () => {
      try {
        const config = await authService.getPasswordResetConfig();
        setResetConfig({
          method: (config?.method || 'TOKEN').toUpperCase(),
          otpLength: config?.otpLength || 6
        });
      } catch {
        setResetConfig({ method: 'TOKEN', otpLength: 6 });
      }
    };

    loadResetConfig();
  }, []);

  const effectiveMode = modeFromQuery === 'OTP' ? 'OTP' : (token ? 'TOKEN' : resetConfig.method);

  useEffect(() => {
    changePassword('');
  }, []);

  return (
    <Formik
      initialValues={{
        email: emailFromQuery,
        otp: '',
        password: '',
        confirmPassword: '',
        submit: null
      }}
      validationSchema={Yup.object().shape({
        email:
          effectiveMode === 'OTP'
            ? Yup.string().email('Must be a valid email').max(255).required('Email is required')
            : Yup.string().nullable(),
        otp:
          effectiveMode === 'OTP'
            ? Yup.string()
                .required('OTP is required')
                .matches(new RegExp(`^\\d{${Math.max(4, Math.min(10, resetConfig.otpLength || 6))}}$`), `OTP must be ${Math.max(4, Math.min(10, resetConfig.otpLength || 6))} digits`)
            : Yup.string().nullable(),
        password: Yup.string().max(255).required('Password is required'),
        confirmPassword: Yup.string()
          .required('Confirm Password is required')
          .test('confirmPassword', 'Both Password must be match!', (confirmPassword, yup) => yup.parent.password === confirmPassword)
      })}
      onSubmit={async (values, { setErrors, setStatus, setSubmitting }) => {
        try {
          if (effectiveMode === 'OTP') {
            await authService.resetPasswordWithOtp(values.email, values.otp, values.password);
          } else {
            if (!token) {
              throw new Error('Reset token is missing');
            }
            await authService.resetPasswordWithToken(token, values.password, values.confirmPassword);
          }

          setStatus({ success: true });
          setSubmitting(false);
          openSnackbar({
            open: true,
            message: 'Successfully reset password.',
            variant: 'alert',

            alert: {
              color: 'success'
            }
          });

          setTimeout(() => {
            navigate(isLoggedIn ? '/auth/login' : auth ? `/${auth}/login?auth=jwt` : '/login', { replace: true });
          }, 1500);
        } catch (err) {
          console.error(err);
          setStatus({ success: false });
          setErrors({ submit: err.message });
          setSubmitting(false);
        }
      }}
    >
      {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => (
        <form noValidate onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            {effectiveMode === 'OTP' && (
              <>
                <Grid size={12}>
                  <Stack sx={{ gap: 1 }}>
                    <InputLabel htmlFor="email-reset">Email Address</InputLabel>
                    <OutlinedInput
                      fullWidth
                      id="email-reset"
                      type="email"
                      value={values.email}
                      name="email"
                      onBlur={handleBlur}
                      onChange={handleChange}
                      error={Boolean(touched.email && errors.email)}
                      placeholder="Enter email address"
                    />
                  </Stack>
                  {touched.email && errors.email && <FormHelperText error>{errors.email}</FormHelperText>}
                </Grid>

                <Grid size={12}>
                  <Stack sx={{ gap: 1 }}>
                    <InputLabel htmlFor="otp-reset">OTP Code</InputLabel>
                    <OutlinedInput
                      fullWidth
                      id="otp-reset"
                      type="text"
                      value={values.otp}
                      name="otp"
                      onBlur={handleBlur}
                      onChange={handleChange}
                      error={Boolean(touched.otp && errors.otp)}
                      placeholder={`Enter ${Math.max(4, Math.min(10, resetConfig.otpLength || 6))}-digit OTP`}
                    />
                  </Stack>
                  {touched.otp && errors.otp && <FormHelperText error>{errors.otp}</FormHelperText>}
                </Grid>
              </>
            )}

            <Grid size={12}>
              <Stack sx={{ gap: 1 }}>
                <InputLabel htmlFor="password-reset">Password</InputLabel>
                <OutlinedInput
                  fullWidth
                  error={Boolean(touched.password && errors.password)}
                  id="password-reset"
                  type={showPassword ? 'text' : 'password'}
                  value={values.password}
                  name="password"
                  onBlur={handleBlur}
                  onChange={(e) => {
                    handleChange(e);
                    changePassword(e.target.value);
                  }}
                  endAdornment={
                    <InputAdornment position="end">
                      <IconButton
                        aria-label="toggle password visibility"
                        onClick={handleClickShowPassword}
                        onMouseDown={handleMouseDownPassword}
                        edge="end"
                        color="secondary"
                      >
                        {showPassword ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                      </IconButton>
                    </InputAdornment>
                  }
                  placeholder="Enter password"
                />
              </Stack>
              {touched.password && errors.password && (
                <FormHelperText error id="helper-text-password-reset">
                  {errors.password}
                </FormHelperText>
              )}
              <FormControl fullWidth sx={{ mt: '1.0rem' }}>
                <Grid container spacing={2} sx={{ alignItems: 'center' }}>
                  <Grid>
                    <Box sx={{ bgcolor: level?.color, width: '5.3125rem', height: '0.375rem', borderRadius: '0.4375rem' }} />
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle1" fontSize="0.75rem">
                      {level?.label}
                    </Typography>
                  </Grid>
                </Grid>
              </FormControl>
            </Grid>
            <Grid size={12}>
              <Stack sx={{ gap: 1 }}>
                <InputLabel htmlFor="confirm-password-reset">Confirm Password</InputLabel>
                <OutlinedInput
                  fullWidth
                  error={Boolean(touched.confirmPassword && errors.confirmPassword)}
                  id="confirm-password-reset"
                  type="password"
                  value={values.confirmPassword}
                  name="confirmPassword"
                  onBlur={handleBlur}
                  onChange={handleChange}
                  placeholder="Enter confirm password"
                />
              </Stack>
              {touched.confirmPassword && errors.confirmPassword && (
                <FormHelperText error id="helper-text-confirm-password-reset">
                  {errors.confirmPassword}
                </FormHelperText>
              )}
            </Grid>

            {errors.submit && (
              <Grid size={12}>
                <FormHelperText error>{errors.submit}</FormHelperText>
              </Grid>
            )}
            <Grid size={12}>
              <AnimateButton>
                <Button disableElevation disabled={isSubmitting} fullWidth size="large" type="submit" variant="contained" color="primary">
                  Reset Password
                </Button>
              </AnimateButton>
            </Grid>
          </Grid>
        </form>
      )}
    </Formik>
  );
}


