import { useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import InputAdornment from '@mui/material/InputAdornment';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';
import { openSnackbar } from 'api/snackbar';

// icons
import { SaveOutlined, ReloadOutlined, LockOutlined } from '@ant-design/icons';

// ==============================|| SYSTEM SETTINGS - SECURITY ||============================== //

const passwordExpiryOptions = [
  { value: 0, label: 'Never' },
  { value: 30, label: '30 Days' },
  { value: 60, label: '60 Days' },
  { value: 90, label: '90 Days' },
  { value: 180, label: '180 Days' }
];

export default function TabSecurity() {
  const initialValues = {
    minPasswordLength: 8,
    passwordExpiry: 90,
    requireUppercase: true,
    requireNumbers: true,
    requireSymbols: true,
    enforceStrongPassword: true,
    forcePasswordChangeFirstLogin: true,
    enable2FA: false,
    sessionTimeout: 30,
    maxLoginAttempts: 5,
    accountLockoutDuration: 15
  };

  const [formData, setFormData] = useState(initialValues);
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setFormData({ ...formData, [field]: value });
  };

  const handleReset = () => {
    setFormData(initialValues);
  };

  const validateForm = () => {
    // Validate password length
    if (formData.minPasswordLength < 6 || formData.minPasswordLength > 32) {
      openSnackbar({
        open: true,
        message: 'Password length must be between 6 and 32 characters',
        variant: 'warning'
      });
      return false;
    }

    // Validate session timeout
    if (formData.sessionTimeout < 5 || formData.sessionTimeout > 1440) {
      openSnackbar({
        open: true,
        message: 'Session timeout must be between 5 and 1440 minutes',
        variant: 'warning'
      });
      return false;
    }

    // Validate max login attempts
    if (formData.maxLoginAttempts < 3 || formData.maxLoginAttempts > 10) {
      openSnackbar({
        open: true,
        message: 'Maximum login attempts must be between 3 and 10',
        variant: 'warning'
      });
      return false;
    }

    // Validate lockout duration
    if (formData.accountLockoutDuration < 5 || formData.accountLockoutDuration > 60) {
      openSnackbar({
        open: true,
        message: 'Account lockout duration must be between 5 and 60 minutes',
        variant: 'warning'
      });
      return false;
    }

    return true;
  };

  const handleSave = async () => {
    if (!validateForm()) {
      return;
    }

    setLoading(true);
    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // Save to localStorage (demo)
      localStorage.setItem('system_security_settings', JSON.stringify(formData));

      openSnackbar({
        open: true,
        message: 'Security settings saved successfully',
        variant: 'success'
      });
    } catch {
      openSnackbar({
        open: true,
        message: 'Failed to save security settings',
        variant: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Security Settings" avatar={<LockOutlined style={{ fontSize: '1.5rem' }} />}>
          <Grid container spacing={3}>
            {/* Password Policy */}
            <Grid size={12}>
              <Divider textAlign="left">Password Policy</Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Minimum Password Length"
                type="number"
                value={formData.minPasswordLength}
                onChange={handleChange('minPasswordLength')}
                slotProps={{
                  input: {
                    endAdornment: <InputAdornment position="end">characters</InputAdornment>,
                    inputProps: { min: 6, max: 32 }
                  }
                }}
                helperText="Recommended: 8-16 characters"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                select
                label="Password Expiration"
                value={formData.passwordExpiry}
                onChange={handleChange('passwordExpiry')}
              >
                {passwordExpiryOptions.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.requireUppercase} onChange={handleChange('requireUppercase')} />}
                label="Require Uppercase Letters"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.requireNumbers} onChange={handleChange('requireNumbers')} />}
                label="Require Numbers"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={<Switch checked={formData.requireSymbols} onChange={handleChange('requireSymbols')} />}
                label="Require Special Symbols"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={
                  <Switch checked={formData.enforceStrongPassword} onChange={handleChange('enforceStrongPassword')} color="warning" />
                }
                label="Enforce Strong Password Policy"
              />
            </Grid>

            <Grid size={12}>
              <FormControlLabel
                control={
                  <Switch checked={formData.forcePasswordChangeFirstLogin} onChange={handleChange('forcePasswordChangeFirstLogin')} />
                }
                label="Force Password Change on First Login"
              />
            </Grid>

            {/* Authentication Settings */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Authentication Settings
              </Divider>
            </Grid>

            <Grid size={12}>
              <FormControlLabel
                control={<Switch checked={formData.enable2FA} onChange={handleChange('enable2FA')} color="success" />}
                label="Enable Two-Factor Authentication (2FA)"
              />
              <Typography variant="caption" display="block" color="textSecondary" sx={{ ml: '2.0rem' }}>
                Require users to verify their identity using a second factor
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Session Timeout"
                type="number"
                value={formData.sessionTimeout}
                onChange={handleChange('sessionTimeout')}
                slotProps={{
                  input: {
                    endAdornment: <InputAdornment position="end">minutes</InputAdornment>,
                    inputProps: { min: 5, max: 1440 }
                  }
                }}
                helperText="Idle time before automatic logout (5-1440 minutes)"
              />
            </Grid>

            {/* Login Security */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Login Security
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Maximum Login Attempts"
                type="number"
                value={formData.maxLoginAttempts}
                onChange={handleChange('maxLoginAttempts')}
                slotProps={{
                  input: {
                    endAdornment: <InputAdornment position="end">attempts</InputAdornment>,
                    inputProps: { min: 3, max: 10 }
                  }
                }}
                helperText="Lock account after this many failed login attempts"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Account Lockout Duration"
                type="number"
                value={formData.accountLockoutDuration}
                onChange={handleChange('accountLockoutDuration')}
                slotProps={{
                  input: {
                    endAdornment: <InputAdornment position="end">minutes</InputAdornment>,
                    inputProps: { min: 5, max: 60 }
                  }
                }}
                helperText="How long to lock the account after max attempts"
              />
            </Grid>

            {/* Save Button */}
            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                <Button variant="outlined" startIcon={<ReloadOutlined />} onClick={handleReset} disabled={loading}>
                  Reset
                </Button>
                <Button variant="contained" startIcon={<SaveOutlined />} onClick={handleSave} disabled={loading}>
                  {loading ? 'Saving...' : 'Save Changes'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
