import React, { useState } from 'react';
import {
  Box,
  Grid,
  Typography,
  TextField,
  Button,
  Switch,
  FormControlLabel,
  Divider,
  Alert,
  CircularProgress,
  Stack,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Paper
} from '@mui/material';
import { SettingOutlined, CheckCircleOutlined } from '@ant-design/icons';
import axios from 'utils/axios';
import { openSnackbar } from 'api/snackbar';

const EmailSettingsTab = ({ settings, setSettings }) => {
  const [testing, setTesting] = useState(false);

  const handleChange = (e) => {
    const { name, value, checked, type } = e.target;
    setSettings((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleTestConnection = async (type) => {
    setTesting(true);
    const label = type === 'imap' ? 'خادم الاستقبال (IMAP)' : 'خادم الإرسال (SMTP)';
    try {
      const endpoint = type === 'imap' ? '/admin/settings/email/test-imap' : '/admin/settings/email/test-smtp';
      const response = await axios.post(endpoint, settings);
      
      if (response.data === true) {
        openSnackbar({
          open: true,
          message: `تم الاتصال بـ ${label} بنجاح`,
          variant: 'alert',
          alert: { color: 'success' },
          close: true
        });
      } else {
        throw new Error('فشل الاتصال. يرجى التحقق من صحة البيانات.');
      }
    } catch (error) {
      const backendError = error.response?.data?.message || error.message;
      openSnackbar({
        open: true,
        message: `فشل الاتصال بـ ${label}. ${backendError}`,
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
    <Box sx={{ p: '1.0rem', height: '100%' }}>
      <Grid container spacing={2}>
        {/* Basic Settings */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.125rem' }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <SettingOutlined /> الإعدادات الأساسية
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
            <Stack spacing={2}>
              <TextField
                fullWidth
                size="small"
                label="عنوان البريد الإلكتروني"
                name="emailAddress"
                value={settings.emailAddress || ''}
                onChange={handleChange}
              />
              <TextField
                fullWidth
                size="small"
                label="الاسم الظاهر للمرسل"
                name="displayName"
                value={settings.displayName || ''}
                onChange={handleChange}
              />
              <FormControl fullWidth size="small">
                <InputLabel>نوع التشفير</InputLabel>
                <Select
                  name="encryptionType"
                  value={settings.encryptionType || 'TLS'}
                  label="نوع التشفير"
                  onChange={handleChange}
                >
                  <MenuItem value="TLS">STARTTLS</MenuItem>
                  <MenuItem value="SSL">SSL/TLS</MenuItem>
                  <MenuItem value="NONE">بدون تشفير</MenuItem>
                </Select>
              </FormControl>
            </Stack>
          </Paper>
        </Grid>

        {/* Sync Settings */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.125rem' }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CheckCircleOutlined /> المزامنة والنشاط
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
            <Stack spacing={2}>
              <FormControlLabel
                control={
                  <Switch
                    checked={!!settings.listenerEnabled}
                    onChange={handleChange}
                    name="listenerEnabled"
                    color="primary"
                  />
                }
                label="تفعيل مستكشف البريد الآلي"
              />
              <TextField
                fullWidth
                size="small"
                type="number"
                label="فترة التحديث (دقائق)"
                name="syncIntervalMins"
                value={settings.syncIntervalMins || 5}
                onChange={handleChange}
              />
              <TextField
                fullWidth
                size="small"
                label="فلترة الموضوع (كلمة مفتاحية)"
                name="subjectFilter"
                placeholder="مثال: [APPROVAL] أو [TBA]"
                value={settings.subjectFilter || ''}
                onChange={handleChange}
                helperText="سيتم جلب الرسائل التي تحتوي على هذه الكلمة في العنوان فقط (اتركها فارغة لجلب الكل)"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={!!settings.onlyFromProviders}
                    onChange={handleChange}
                    name="onlyFromProviders"
                    color="secondary"
                  />
                }
                label="استلام من مقدمي الخدمة المسجلين فقط"
              />
              <Alert severity={settings.listenerEnabled ? "info" : "warning"} sx={{ py: 0 }}>
                {settings.listenerEnabled ? "الاستقبال مفعل" : "الاستقبال متوقف"}
              </Alert>
            </Stack>
          </Paper>
        </Grid>

        {/* SMTP */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.125rem' }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
              <Typography variant="subtitle1" fontWeight={700}>خادم الإرسال (SMTP)</Typography>
              <Button size="small" variant="outlined" onClick={() => handleTestConnection('smtp')} disabled={testing}>
                فحص الإرسال
              </Button>
            </Box>
            <Divider sx={{ mb: '1.0rem' }} />
            <Stack spacing={1.5}>
              <TextField fullWidth size="small" label="خادم SMTP" name="smtpHost" value={settings.smtpHost || ''} onChange={handleChange} />
              <TextField fullWidth size="small" type="number" label="المنفذ" name="smtpPort" value={settings.smtpPort || 587} onChange={handleChange} />
              <TextField fullWidth size="small" label="اسم المستخدم" name="smtpUsername" value={settings.smtpUsername || ''} onChange={handleChange} />
              <TextField fullWidth size="small" type="password" label="كلمة المرور" name="smtpPassword" value={settings.smtpPassword || ''} onChange={handleChange} placeholder="أدخل للتغيير" />
            </Stack>
          </Paper>
        </Grid>

        {/* IMAP */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.125rem' }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
              <Typography variant="subtitle1" fontWeight={700}>خادم الاستقبال (IMAP)</Typography>
              <Button size="small" variant="outlined" onClick={() => handleTestConnection('imap')} disabled={testing}>
                فحص الاستقبال
              </Button>
            </Box>
            <Divider sx={{ mb: '1.0rem' }} />
            <Stack spacing={1.5}>
              <TextField fullWidth size="small" label="خادم IMAP" name="imapHost" value={settings.imapHost || ''} onChange={handleChange} />
              <TextField fullWidth size="small" type="number" label="المنفذ" name="imapPort" value={settings.imapPort || 993} onChange={handleChange} />
              <TextField fullWidth size="small" label="اسم المستخدم" name="imapUsername" value={settings.imapUsername || ''} onChange={handleChange} />
              <TextField fullWidth size="small" type="password" label="كلمة المرور" name="imapPassword" value={settings.imapPassword || ''} onChange={handleChange} placeholder="أدخل للتغيير" />
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default EmailSettingsTab;
