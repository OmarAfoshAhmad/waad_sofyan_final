// material-ui
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';

// project imports
import MainCard from 'components/MainCard';
import { openSnackbar } from 'api/snackbar';

// third-party
import * as Yup from 'yup';
import { Formik } from 'formik';

// icons
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons';

// ==============================|| SYSTEM SETTINGS - GENERAL ||============================== //

const timezones = [
  { value: 'Africa/Tripoli', label: 'Africa/Tripoli (Libya - UTC+2)' },
  { value: 'Africa/Cairo', label: 'Africa/Cairo (Egypt - UTC+2)' },
  { value: 'Asia/Riyadh', label: 'Asia/Riyadh (Saudi Arabia - UTC+3)' },
  { value: 'Asia/Dubai', label: 'Asia/Dubai (UAE - UTC+4)' },
  { value: 'Europe/London', label: 'Europe/London (UK - UTC+0)' },
  { value: 'Europe/Paris', label: 'Europe/Paris (France - UTC+1)' },
  { value: 'America/New_York', label: 'America/New_York (USA East - UTC-5)' }
];

const languages = [
  { value: 'en', label: 'English' },
  { value: 'ar', label: 'العربية (Arabic)' }
];

const dateFormats = [
  { value: 'DD/MM/YYYY', label: 'DD/MM/YYYY (31/12/2024)' },
  { value: 'MM/DD/YYYY', label: 'MM/DD/YYYY (12/31/2024)' },
  { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD (2024-12-31)' },
  { value: 'DD-MM-YYYY', label: 'DD-MM-YYYY (31-12-2024)' }
];

const themeModes = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System Default' }
];

// Form validation schema
const validationSchema = Yup.object().shape({
  systemName: Yup.string().min(3, 'System name must be at least 3 characters').max(100, 'Too long').required('System name is required'),
  defaultLanguage: Yup.string().oneOf(['en', 'ar'], 'Invalid language').required('Language is required'),
  timezone: Yup.string().required('Timezone is required'),
  dateFormat: Yup.string().required('Date format is required'),
  themeMode: Yup.string().oneOf(['light', 'dark', 'system'], 'Invalid theme mode').required('Theme mode is required')
});

export default function TabGeneral() {
  const initialValues = {
    systemName: 'TBA-WAAD System',
    defaultLanguage: 'ar',
    timezone: 'Africa/Tripoli',
    dateFormat: 'DD/MM/YYYY',
    enableRTL: true,
    themeMode: 'system'
  };

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // Save to localStorage for demo
      localStorage.setItem('system_general_settings', JSON.stringify(values));

      // Update RTL direction based on language
      if (values.enableRTL || values.defaultLanguage === 'ar') {
        document.dir = 'rtl';
      } else {
        document.dir = 'ltr';
      }

      openSnackbar({
        open: true,
        message: 'General settings saved successfully',
        variant: 'success'
      });
    } catch {
      openSnackbar({
        open: true,
        message: 'Failed to save settings',
        variant: 'error'
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="General Settings">
          <Formik initialValues={initialValues} validationSchema={validationSchema} onSubmit={handleSubmit}>
            {({ values, errors, touched, handleChange, handleBlur, handleSubmit, setFieldValue, isSubmitting, resetForm }) => (
              <form noValidate onSubmit={handleSubmit}>
                <Grid container spacing={3}>
                  {/* System Information */}
                  <Grid size={12}>
                    <Divider textAlign="left">System Information</Divider>
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      id="systemName"
                      name="systemName"
                      label="System Name *"
                      value={values.systemName}
                      onChange={handleChange}
                      onBlur={handleBlur}
                      error={touched.systemName && Boolean(errors.systemName)}
                      helperText={touched.systemName && errors.systemName}
                      placeholder="Enter system name"
                    />
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      select
                      fullWidth
                      id="defaultLanguage"
                      name="defaultLanguage"
                      label="Default Language *"
                      value={values.defaultLanguage}
                      onChange={(e) => {
                        handleChange(e);
                        // Auto-enable RTL for Arabic
                        if (e.target.value === 'ar') {
                          setFieldValue('enableRTL', true);
                        }
                      }}
                      onBlur={handleBlur}
                      error={touched.defaultLanguage && Boolean(errors.defaultLanguage)}
                      helperText={touched.defaultLanguage && errors.defaultLanguage}
                    >
                      {languages.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>

                  {/* Regional Settings */}
                  <Grid size={12}>
                    <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                      Regional Settings
                    </Divider>
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      select
                      fullWidth
                      id="timezone"
                      name="timezone"
                      label="Timezone *"
                      value={values.timezone}
                      onChange={handleChange}
                      onBlur={handleBlur}
                      error={touched.timezone && Boolean(errors.timezone)}
                      helperText={touched.timezone && errors.timezone}
                    >
                      {timezones.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      select
                      fullWidth
                      id="dateFormat"
                      name="dateFormat"
                      label="Date Format *"
                      value={values.dateFormat}
                      onChange={handleChange}
                      onBlur={handleBlur}
                      error={touched.dateFormat && Boolean(errors.dateFormat)}
                      helperText={touched.dateFormat && errors.dateFormat}
                    >
                      {dateFormats.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>

                  {/* Display Settings */}
                  <Grid size={12}>
                    <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                      Display Settings
                    </Divider>
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      select
                      fullWidth
                      id="themeMode"
                      name="themeMode"
                      label="Theme Mode *"
                      value={values.themeMode}
                      onChange={handleChange}
                      onBlur={handleBlur}
                      error={touched.themeMode && Boolean(errors.themeMode)}
                      helperText={touched.themeMode && errors.themeMode}
                    >
                      {themeModes.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          id="enableRTL"
                          name="enableRTL"
                          checked={values.enableRTL}
                          onChange={(e) => setFieldValue('enableRTL', e.target.checked)}
                          color="primary"
                        />
                      }
                      label="Enable Right-to-Left (RTL) Layout"
                    />
                  </Grid>

                  {/* Action Buttons */}
                  <Grid size={12}>
                    <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                      <Button variant="outlined" startIcon={<ReloadOutlined />} onClick={() => resetForm()} disabled={isSubmitting}>
                        Reset
                      </Button>
                      <Button variant="contained" type="submit" startIcon={<SaveOutlined />} disabled={isSubmitting}>
                        {isSubmitting ? 'Saving...' : 'Save Settings'}
                      </Button>
                    </Stack>
                  </Grid>
                </Grid>
              </form>
            )}
          </Formik>
        </MainCard>
      </Grid>
    </Grid>
  );
}
