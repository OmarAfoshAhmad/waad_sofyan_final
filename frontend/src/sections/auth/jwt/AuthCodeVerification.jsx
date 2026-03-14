// material-ui
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import FormHelperText from '@mui/material/FormHelperText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// third-party
import * as Yup from 'yup';
import { Formik } from 'formik';
import OutlinedInput from '@mui/material/OutlinedInput';

// project imports
import AnimateButton from 'components/@extended/AnimateButton';

// ============================|| JWT - CODE VERIFICATION ||============================ //

export default function AuthCodeVerification() {
  return (
    <Formik
      initialValues={{ otp: '' }}
      validationSchema={Yup.object({
        otp: Yup.string().length(4, 'OTP must be exactly 4 digits').required('OTP is required')
      })}
      onSubmit={(values, { resetForm }) => {
        resetForm();
        console.log(values);

        // reset focus after submission
        const activeElement = document.activeElement;
        if (activeElement) activeElement.blur();
      }}
    >
      {({ errors, handleSubmit, touched, values, setFieldValue }) => (
        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid size={12}>
              <Box
                sx={(theme) => ({
                  '& input': {
                    border: '1px solid',
                    borderColor: 'divider',
                    ...(touched.otp && errors.otp && { borderColor: 'error.main' }),
                    '&:focus-visible': {
                      outline: 'none !important',
                      borderColor: 'primary.main',
                      boxShadow: theme.vars.customShadows.primary,
                      ...(touched.otp && errors.otp && { borderColor: 'error.main', boxShadow: theme.vars.customShadows.error })
                    }
                  }
                })}
              >
                <OutlinedInput
                  fullWidth
                  id="otp-input"
                  name="otp"
                  value={values.otp}
                  onChange={(e) => setFieldValue('otp', e.target.value)}
                  inputProps={{ inputMode: 'numeric', pattern: '[0-9]*', maxLength: 4 }}
                  placeholder="Enter 4-digit code"
                />
                {touched.otp && errors.otp && (
                  <FormHelperText error id="standard-weight-helper-text-otp">
                    {errors.otp}
                  </FormHelperText>
                )}
              </Box>
            </Grid>
            <Grid size={12}>
              <AnimateButton>
                <Button disableElevation fullWidth size="large" type="submit" variant="contained">
                  Continue
                </Button>
              </AnimateButton>
            </Grid>
            <Grid size={12}>
              <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between' }}>
                <Typography>Did not receive the email? Check your spam filter, or</Typography>
                <Typography variant="body1" sx={{ minWidth: '5.3125rem', ml: '1.0rem', textDecoration: 'none', cursor: 'pointer' }} color="primary">
                  Resend code
                </Typography>
              </Stack>
            </Grid>
          </Grid>
        </form>
      )}
    </Formik>
  );
}
