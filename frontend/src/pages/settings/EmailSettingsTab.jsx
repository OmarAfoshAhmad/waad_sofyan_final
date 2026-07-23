import React, { useState } from 'react';
import { Box, Grid, Typography, TextField, Button, Divider, CircularProgress, Stack, MenuItem, Select, FormControl, InputLabel, Paper, Alert } from '@mui/material';
import { SettingOutlined } from '@ant-design/icons';
import axios from 'utils/axios';
import { openSnackbar } from 'api/snackbar';

const EmailSettingsTab = ({ settings, setSettings }) => {
  const [testing, setTesting] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setSettings((previous) => ({ ...previous, [name]: value }));
  };

  const testSmtp = async () => {
    setTesting(true);
    try {
      const response = await axios.post('/admin/settings/email/test-smtp', settings);
      if (response.data !== true) throw new Error('فشل اختبار الاتصال');
      openSnackbar({ open: true, message: 'تم الاتصال بخادم الإرسال بنجاح', variant: 'alert', alert: { color: 'success' }, close: true });
    } catch (error) {
      openSnackbar({
        open: true,
        message: error.response?.data?.message || 'فشل الاتصال بخادم الإرسال',
        variant: 'alert',
        alert: { color: 'error' },
        close: true
      });
    } finally {
      setTesting(false);
    }
  };

  if (!settings) return null;

  return (
    <Box sx={{ p: '1rem' }}>
      <Alert severity="info" sx={{ mb: 2 }}>
        هذه الإعدادات للبريد الصادر فقط. تُرسل طلبات الموافقة المسبقة وتُتابع حصريًا عبر بوابة مقدمي الخدمة.
      </Alert>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1rem', borderRadius: '0.25rem', height: '100%' }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <SettingOutlined /> هوية المرسل
            </Typography>
            <Divider sx={{ mb: '1rem' }} />
            <Stack spacing={2}>
              <TextField fullWidth size="small" label="عنوان البريد الإلكتروني" name="emailAddress" value={settings.emailAddress || ''} onChange={handleChange} />
              <TextField fullWidth size="small" label="الاسم الظاهر للمرسل" name="displayName" value={settings.displayName || ''} onChange={handleChange} />
              <FormControl fullWidth size="small">
                <InputLabel>نوع التشفير</InputLabel>
                <Select name="encryptionType" value={settings.encryptionType || 'TLS'} label="نوع التشفير" onChange={handleChange}>
                  <MenuItem value="TLS">STARTTLS</MenuItem>
                  <MenuItem value="SSL">SSL/TLS</MenuItem>
                </Select>
              </FormControl>
            </Stack>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1rem', borderRadius: '0.25rem' }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
              <Typography variant="subtitle1" fontWeight={700}>خادم الإرسال (SMTP)</Typography>
              <Button size="small" variant="outlined" onClick={testSmtp} disabled={testing}>
                {testing ? <CircularProgress size={18} /> : 'فحص الإرسال'}
              </Button>
            </Box>
            <Divider sx={{ mb: '1rem' }} />
            <Stack spacing={1.5}>
              <TextField fullWidth size="small" label="خادم SMTP" name="smtpHost" value={settings.smtpHost || ''} onChange={handleChange} />
              <TextField fullWidth size="small" type="number" label="المنفذ" name="smtpPort" value={settings.smtpPort || 587} onChange={handleChange} />
              <TextField fullWidth size="small" label="اسم المستخدم" name="smtpUsername" value={settings.smtpUsername || ''} onChange={handleChange} />
              <TextField
                fullWidth
                size="small"
                type="password"
                label="كلمة المرور"
                name="smtpPassword"
                value={settings.smtpPassword || ''}
                onChange={handleChange}
                placeholder={settings.smtpPasswordConfigured ? 'محفوظة — أدخل قيمة جديدة للتغيير' : 'أدخل كلمة المرور'}
              />
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default EmailSettingsTab;
