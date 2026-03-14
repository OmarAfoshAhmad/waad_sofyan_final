import { useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Chip from '@mui/material/Chip';

// project imports
import MainCard from 'components/MainCard';
import { openSnackbar } from 'api/snackbar';

// icons
import { SaveOutlined, ApiOutlined, EyeOutlined, EyeInvisibleOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';

// ==============================|| SYSTEM SETTINGS - INTEGRATIONS ||============================== //

export default function TabIntegrations() {
  const initialValues = {
    // API Configuration
    apiKey: 'sk_test_abc123xyz789',
    enableApiAccess: true,

    // Payment Gateway
    enablePaymentGateway: true,
    paymentGatewayUrl: 'https://payment-gateway.example.com/api',
    paymentMerchantId: 'MERCHANT_12345',
    paymentApiKey: 'pk_live_payment_key_xyz',

    // SMS Gateway
    enableSMSGateway: true,
    smsGatewayUrl: 'https://sms-gateway.example.com/api',
    smsUsername: 'tba_waad_user',
    smsApiKey: 'sk_sms_key_abc123',

    // Webhook URLs
    webhookClaims: 'https://api.example.com/webhooks/claims',
    webhookPreauth: 'https://api.example.com/webhooks/preauth',
    webhookMembers: 'https://api.example.com/webhooks/members'
  };

  const [formData, setFormData] = useState(initialValues);
  const [loading, setLoading] = useState(false);
  const [showKeys, setShowKeys] = useState({
    apiKey: false,
    paymentApiKey: false,
    smsApiKey: false
  });
  const [testResults, setTestResults] = useState({
    payment: null,
    sms: null
  });

  const handleChange = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setFormData({ ...formData, [field]: value });
  };

  const toggleKeyVisibility = (key) => {
    setShowKeys({ ...showKeys, [key]: !showKeys[key] });
  };

  const handleTestConnection = async (type) => {
    try {
      openSnackbar({
        open: true,
        message: `Testing ${type === 'payment' ? 'Payment Gateway' : 'SMS Gateway'} connection...`,
        variant: 'info'
      });

      // Simulate API test with 1.2s delay
      await new Promise((resolve) => setTimeout(resolve, 1200));

      setTestResults({ ...testResults, [type]: 'success' });
      openSnackbar({
        open: true,
        message: `${type === 'payment' ? 'Payment Gateway' : 'SMS Gateway'} connection successful`,
        variant: 'success'
      });
    } catch {
      setTestResults({ ...testResults, [type]: 'error' });
      openSnackbar({
        open: true,
        message: `Failed to connect to ${type === 'payment' ? 'Payment Gateway' : 'SMS Gateway'}`,
        variant: 'error'
      });
    }
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      localStorage.setItem('system_integration_settings', JSON.stringify(formData));

      openSnackbar({
        open: true,
        message: 'Integration settings saved successfully',
        variant: 'success'
      });
    } catch {
      openSnackbar({
        open: true,
        message: 'Failed to save integration settings',
        variant: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Integration Settings" avatar={<ApiOutlined style={{ fontSize: '1.5rem' }} />}>
          <Grid container spacing={3}>
            {/* API Configuration */}
            <Grid size={12}>
              <Divider textAlign="left">API Configuration</Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                label="API Key"
                type={showKeys.apiKey ? 'text' : 'password'}
                value={formData.apiKey}
                onChange={handleChange('apiKey')}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => toggleKeyVisibility('apiKey')} edge="end">
                          {showKeys.apiKey ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }
                }}
                helperText="API key for external integrations"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ height: '100%' }}>
                <FormControlLabel
                  control={<Switch checked={formData.enableApiAccess} onChange={handleChange('enableApiAccess')} />}
                  label="Enable API Access"
                />
                {formData.enableApiAccess && <Chip label="Active" color="success" size="small" />}
              </Stack>
            </Grid>

            {/* Webhook URLs */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Webhook URLs
              </Divider>
            </Grid>

            <Grid size={12}>
              <TextField
                fullWidth
                label="Claims Webhook URL"
                value={formData.webhookClaims}
                onChange={handleChange('webhookClaims')}
                placeholder="https://api.example.com/webhooks/claims"
                helperText="Webhook for claims notifications"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Pre-authorization Webhook URL"
                value={formData.webhookPreauth}
                onChange={handleChange('webhookPreauth')}
                placeholder="https://api.example.com/webhooks/preauth"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Members Webhook URL"
                value={formData.webhookMembers}
                onChange={handleChange('webhookMembers')}
                placeholder="https://api.example.com/webhooks/members"
              />
            </Grid>

            {/* Payment Gateway */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Payment Gateway Integration
              </Divider>
            </Grid>

            <Grid size={12}>
              <Stack direction="row" spacing={2} alignItems="center">
                <FormControlLabel
                  control={<Switch checked={formData.enablePaymentGateway} onChange={handleChange('enablePaymentGateway')} />}
                  label="Enable Payment Gateway"
                />
                {testResults.payment === 'success' && <CheckCircleOutlined style={{ color: 'green', fontSize: '1.25rem' }} />}
                {testResults.payment === 'error' && <CloseCircleOutlined style={{ color: 'red', fontSize: '1.25rem' }} />}
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Gateway URL"
                value={formData.paymentGatewayUrl}
                onChange={handleChange('paymentGatewayUrl')}
                disabled={!formData.enablePaymentGateway}
                placeholder="https://payment-gateway.example.com/api"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Merchant ID"
                value={formData.paymentMerchantId}
                onChange={handleChange('paymentMerchantId')}
                disabled={!formData.enablePaymentGateway}
                placeholder="MERCHANT_12345"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                label="Payment API Key"
                type={showKeys.paymentApiKey ? 'text' : 'password'}
                value={formData.paymentApiKey}
                onChange={handleChange('paymentApiKey')}
                disabled={!formData.enablePaymentGateway}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => toggleKeyVisibility('paymentApiKey')} edge="end">
                          {showKeys.paymentApiKey ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }
                }}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => handleTestConnection('payment')}
                disabled={!formData.enablePaymentGateway}
                sx={{ height: '3.5rem' }}
              >
                Test Connection
              </Button>
            </Grid>

            {/* SMS Gateway */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                SMS Gateway Integration
              </Divider>
            </Grid>

            <Grid size={12}>
              <Stack direction="row" spacing={2} alignItems="center">
                <FormControlLabel
                  control={<Switch checked={formData.enableSMSGateway} onChange={handleChange('enableSMSGateway')} />}
                  label="Enable SMS Gateway"
                />
                {testResults.sms === 'success' && <CheckCircleOutlined style={{ color: 'green', fontSize: '1.25rem' }} />}
                {testResults.sms === 'error' && <CloseCircleOutlined style={{ color: 'red', fontSize: '1.25rem' }} />}
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="SMS Gateway URL"
                value={formData.smsGatewayUrl}
                onChange={handleChange('smsGatewayUrl')}
                disabled={!formData.enableSMSGateway}
                placeholder="https://sms-gateway.example.com/api"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Username"
                value={formData.smsUsername}
                onChange={handleChange('smsUsername')}
                disabled={!formData.enableSMSGateway}
                placeholder="tba_waad_user"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 8 }}>
              <TextField
                fullWidth
                label="SMS API Key"
                type={showKeys.smsApiKey ? 'text' : 'password'}
                value={formData.smsApiKey}
                onChange={handleChange('smsApiKey')}
                disabled={!formData.enableSMSGateway}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => toggleKeyVisibility('smsApiKey')} edge="end">
                          {showKeys.smsApiKey ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }
                }}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => handleTestConnection('sms')}
                disabled={!formData.enableSMSGateway}
                sx={{ height: '3.5rem' }}
              >
                Test SMS Connection
              </Button>
            </Grid>

            {/* Save Button */}
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
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
