import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useSearchParams, useNavigate } from 'react-router-dom';

// material-ui
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import Grid from '@mui/material/Grid';
import Link from '@mui/material/Link';
import InputAdornment from '@mui/material/InputAdornment';
import InputLabel from '@mui/material/InputLabel';
import OutlinedInput from '@mui/material/OutlinedInput';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha, useTheme } from '@mui/material/styles';
import CircularProgress from '@mui/material/CircularProgress';

// third-party
import * as Yup from 'yup';
import { Formik } from 'formik';

// project imports
import IconButton from 'components/@extended/IconButton';
import AnimateButton from 'components/@extended/AnimateButton';

import useAuth from 'hooks/useAuth';
import { getDefaultRouteForRole } from 'utils/roleRoutes';

// assets
import EyeOutlined from '@ant-design/icons/EyeOutlined';
import EyeInvisibleOutlined from '@ant-design/icons/EyeInvisibleOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import LoginIcon from '@mui/icons-material/Login';

// ============================|| JWT - LOGIN ||============================ //

export default function AuthLogin({ isDemo = false }) {
  const [checked, setChecked] = useState(false);
  const theme = useTheme();

  const { login } = useAuth();
  const navigate = useNavigate();

  const [showPassword, setShowPassword] = useState(false);
  const handleClickShowPassword = () => {
    setShowPassword(!showPassword);
  };

  const handleMouseDownPassword = (event) => {
    event.preventDefault();
  };

  const [searchParams] = useSearchParams();
  const auth = searchParams.get('auth'); // get auth and set route based on that

  return (
    <>
      <Formik
        initialValues={{
          email: '',
          password: '',
          submit: null
        }}
        validationSchema={Yup.object().shape({
          email: Yup.string().max(255).required('اسم المستخدم أو البريد الإلكتروني مطلوب'),
          password: Yup.string()
            .required('كلمة المرور مطلوبة')
            .test('no-leading-trailing-whitespace', 'كلمة المرور لا يمكن أن تبدأ أو تنتهي بمسافات', (value) => value === value?.trim())
        })}
        onSubmit={async (values, { setErrors, setStatus, setSubmitting }) => {
          try {
            // Phase C Migration: Login now returns user data (no redirect path needed)
            const trimmedIdentifier = values.email.trim();
            const user = await login({
              identifier: trimmedIdentifier,
              password: values.password
            });

            setStatus({ success: true });
            setSubmitting(false);

            // Phase 5.5: Role-based landing page redirect
            const primaryRole = user?.role || (Array.isArray(user?.roles) ? user.roles[0] : null);
            const landingRoute = getDefaultRouteForRole(primaryRole);
            navigate(landingRoute);
          } catch (err) {
            console.error('Login error:', err);
            setStatus({ success: false });
            setErrors({ submit: err.message || 'بيانات الدخول غير صحيحة. يرجى المحاولة مرة أخرى.' });
            setSubmitting(false);
          }
        }}
      >
        {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => (
          <form noValidate onSubmit={handleSubmit}>
            <Grid container spacing={3}>
              {/* Username/Email Field */}
              <Grid size={12}>
                <Stack sx={{ gap: 1 }}>
                  <InputLabel htmlFor="email-login" sx={{ fontWeight: 600 }}>
                    اسم المستخدم أو البريد الإلكتروني
                  </InputLabel>
                  <OutlinedInput
                    id="email-login"
                    type="text"
                    value={values.email}
                    name="email"
                    onBlur={handleBlur}
                    onChange={handleChange}
                    placeholder="أدخل اسم المستخدم أو البريد الإلكتروني"
                    fullWidth
                    error={Boolean(touched.email && errors.email)}
                    startAdornment={
                      <InputAdornment position="start">
                        <PersonOutlineIcon color="action" />
                      </InputAdornment>
                    }
                    sx={{
                      '& .MuiOutlinedInput-notchedOutline': {
                        borderRadius: '0.25rem'
                      },
                      '&:hover .MuiOutlinedInput-notchedOutline': {
                        borderColor: theme.palette.primary.main
                      },
                      '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                        borderWidth: 2
                      }
                    }}
                  />
                </Stack>
                {touched.email && errors.email && (
                  <FormHelperText error id="standard-weight-helper-text-email-login">
                    {errors.email}
                  </FormHelperText>
                )}
              </Grid>

              {/* Password Field */}
              <Grid size={12}>
                <Stack sx={{ gap: 1 }}>
                  <InputLabel htmlFor="password-login" sx={{ fontWeight: 600 }}>
                    كلمة المرور
                  </InputLabel>
                  <OutlinedInput
                    fullWidth
                    error={Boolean(touched.password && errors.password)}
                    id="-password-login"
                    type={showPassword ? 'text' : 'password'}
                    value={values.password}
                    name="password"
                    onBlur={handleBlur}
                    onChange={handleChange}
                    startAdornment={
                      <InputAdornment position="start">
                        <LockOutlinedIcon color="action" />
                      </InputAdornment>
                    }
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
                    placeholder="أدخل كلمة المرور"
                    sx={{
                      '& .MuiOutlinedInput-notchedOutline': {
                        borderRadius: '0.25rem'
                      },
                      '&:hover .MuiOutlinedInput-notchedOutline': {
                        borderColor: theme.palette.primary.main
                      },
                      '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                        borderWidth: 2
                      }
                    }}
                  />
                </Stack>
                {touched.password && errors.password && (
                  <FormHelperText error id="standard-weight-helper-text-password-login">
                    {errors.password}
                  </FormHelperText>
                )}
              </Grid>

              {/* Remember Me & Forgot Password (Removed) */}

              {/* Error Message */}
              {errors.submit && (
                <Grid size={12}>
                  <FormHelperText
                    error
                    sx={{
                      fontSize: '0.875rem',
                      textAlign: 'center',
                      bgcolor: alpha(theme.palette.error.main, 0.1),
                      p: '0.75rem',
                      borderRadius: '0.25rem'
                    }}
                  >
                    {errors.submit}
                  </FormHelperText>
                </Grid>
              )}

              {/* Submit Button */}
              <Grid size={12}>
                <AnimateButton>
                  <Button
                    disableElevation
                    disabled={isSubmitting}
                    fullWidth
                    size="large"
                    type="submit"
                    variant="contained"
                    color="primary"
                    startIcon={isSubmitting ? <CircularProgress size={20} color="inherit" /> : <LoginIcon />}
                    sx={{
                      py: '0.75rem',
                      borderRadius: '0.25rem',
                      fontWeight: 600,
                      fontSize: '1rem',
                    }}
                  >
                    {isSubmitting ? 'جاري تسجيل الدخول...' : 'تسجيل الدخول'}
                  </Button>
                </AnimateButton>
              </Grid>
            </Grid>
          </form>
        )}
      </Formik>
    </>
  );
}

AuthLogin.propTypes = { isDemo: PropTypes.bool };



