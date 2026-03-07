import { useNavigate, useSearchParams } from 'react-router-dom';

// material-ui
import Button from '@mui/material/Button';
import FormHelperText from '@mui/material/FormHelperText';
import Grid from '@mui/material/Grid';
import InputLabel from '@mui/material/InputLabel';
import OutlinedInput from '@mui/material/OutlinedInput';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// third-party
import * as Yup from 'yup';
import { Formik } from 'formik';

// project imports
import useAuth from 'hooks/useAuth';
import AnimateButton from 'components/@extended/AnimateButton';
import { openSnackbar } from 'api/snackbar';
import authService from 'services/api/auth.service';

// ============================|| JWT - FORGOT PASSWORD ||============================ //

export default function AuthForgotPassword() {
  const navigate = useNavigate();

  const { isLoggedIn } = useAuth();

  const [searchParams] = useSearchParams();
  const auth = searchParams.get('auth'); // get auth and set route based on that

  return (
    <>
      <Formik
        initialValues={{
          email: '',
          submit: null
        }}
        validationSchema={Yup.object().shape({
          email: Yup.string().email('Must be a valid email').max(255).required('Email is required')
        })}
        onSubmit={async (values, { setErrors, setStatus, setSubmitting }) => {
          try {
            const config = await authService.getPasswordResetConfig();
            const method = (config?.method || 'TOKEN').toUpperCase();

            if (method === 'OTP') {
              await authService.requestPasswordResetOtp(values.email);
              setStatus({ success: true });
              setSubmitting(false);
              openSnackbar({
                open: true,
                message: 'OTP sent to your email. Enter it to reset password.',
                variant: 'alert',
                alert: {
                  color: 'success'
                }
              });

              setTimeout(() => {
                navigate(`/auth/reset-password?mode=otp&email=${encodeURIComponent(values.email)}`, { replace: true });
              }, 1200);
              return;
            }

            await authService.requestPasswordResetToken(values.email);
            setStatus({ success: true });
            setSubmitting(false);
            openSnackbar({
              open: true,
              message: 'Check your email for the password reset link',
              variant: 'alert',
              alert: {
                color: 'success'
              }
            });

            setTimeout(() => {
              navigate(isLoggedIn ? '/auth/check-mail' : auth ? `/${auth}/check-mail?auth=jwt` : '/check-mail', { replace: true });
            }, 1500);
          } catch (err) {
            console.error(err);
            setStatus({ success: false });
            setErrors({ submit: err?.response?.data?.message || err.message || 'Failed to process password reset request' });
            setSubmitting(false);
          }
        }}
      >
        {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => (
          <form noValidate onSubmit={handleSubmit}>
            <Grid container spacing={3}>
              <Grid size={12}>
                <Stack sx={{ gap: 1 }}>
                  <InputLabel htmlFor="email-forgot">Email Address</InputLabel>
                  <OutlinedInput
                    fullWidth
                    error={Boolean(touched.email && errors.email)}
                    id="email-forgot"
                    type="email"
                    value={values.email}
                    name="email"
                    onBlur={handleBlur}
                    onChange={handleChange}
                    placeholder="Enter email address"
                  />
                </Stack>
                {touched.email && errors.email && (
                  <FormHelperText error id="helper-text-email-forgot">
                    {errors.email}
                  </FormHelperText>
                )}
              </Grid>
              {errors.submit && (
                <Grid size={12}>
                  <FormHelperText error>{errors.submit}</FormHelperText>
                </Grid>
              )}
              <Grid sx={{ mb: -2 }} size={12}>
                <Typography variant="caption">Do not forgot to check SPAM box.</Typography>
              </Grid>
              <Grid size={12}>
                <AnimateButton>
                  <Button disableElevation disabled={isSubmitting} fullWidth size="large" type="submit" variant="contained" color="primary">
                    Send Password Reset Email
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
