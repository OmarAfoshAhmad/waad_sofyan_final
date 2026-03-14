import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
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
  InputLabel
} from '@mui/material';
import { MailOutlined, SettingOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import axios from 'utils/axios';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { openSnackbar } from 'store/reducers/snackbar';
import { useDispatch } from 'react-redux';

const EmailSettingsPage = () => {
  const dispatch = useDispatch();
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [settings, setSettings] = useState({
    id: null,
    emailAddress: '',
    displayName: '',
    smtpHost: '',
    smtpPort: 587,
    smtpUsername: '',
    smtpPassword: '',
    imapHost: '',
    imapPort: 993,
    imapUsername: '',
    imapPassword: '',
    encryptionType: 'TLS',
    listenerEnabled: false,
    syncIntervalMins: 5
  });

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    setLoading(true);
    try {
      const response = await axios.get('/api/admin/settings/email');
      if (response.data && response.data.id) {
        setSettings({ ...response.data, smtpPassword: '', imapPassword: '' });
      }
    } catch (error) {
      console.error('Failed to fetch email settings', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value, checked, type } = e.target;
    setSettings((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      await axios.post('/api/admin/settings/email', settings);
      dispatch(
        openSnackbar({
          open: true,
          message: 'تم حفظ إعدادات البريد بنجاح',
          variant: 'alert',
          alert: { color: 'success' },
          close: false
        })
      );
      fetchSettings();
    } catch (error) {
      dispatch(
        openSnackbar({
          open: true,
          message: 'فشل في حفظ الإعدادات',
          variant: 'alert',
          alert: { color: 'error' },
          close: false
        })
      );
    } finally {
      setLoading(false);
    }
  };

  const handleTestConnection = async (type) => {
    setTesting(true);
    try {
      const endpoint = type === 'imap' ? '/api/admin/settings/email/test-imap' : '/api/admin/settings/email/test-smtp';
      const response = await axios.post(endpoint, settings);
      if (response.data) {
        dispatch(
          openSnackbar({
            open: true,
            message: `تم الاتصال بخادم ${type.toUpperCase()} بنجاح`,
            variant: 'alert',
            alert: { color: 'success' },
            close: false
          })
        );
      } else {
        throw new Error('Connection failed');
      }
    } catch (error) {
      dispatch(
        openSnackbar({
          open: true,
          message: `فشل الاتصال بخادم ${type.toUpperCase()}. يرجى التأكد من الإعدادات.`,
          variant: 'alert',
          alert: { color: 'error' },
          close: false
        })
      );
    } finally {
      setTesting(false);
    }
  };

  if (loading && !settings.id) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <ModernPageHeader 
        title="إعدادات البريد الإلكتروني" 
        subtitle="تكوين البريد الخاص باستقبال الموافقات المسبقة والردود الآلية" 
        icon={MailOutlined} 
      />

      <Grid container spacing={3} sx={{ mt: '1.0rem' }}>
        {/* Basic Settings */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h5" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                <SettingOutlined style={{ marginRight: '0.375rem' }} /> الإعدادات الأساسية
              </Typography>
              <Divider sx={{ mb: '1.0rem' }} />
              <Stack spacing={2}>
                <TextField
                  fullWidth
                  label="عنوان البريد الإلكتروني"
                  name="emailAddress"
                  value={settings.emailAddress}
                  onChange={handleChange}
                  placeholder="preauth@alwahacare.com"
                />
                <TextField
                  fullWidth
                  label="الاسم الظاهر للمرسل"
                  name="displayName"
                  value={settings.displayName}
                  onChange={handleChange}
                  placeholder="شركة وعد - الموافقات المسبقة"
                />
                <FormControl fullWidth>
                  <InputLabel>نوع التشفير</InputLabel>
                  <Select
                    name="encryptionType"
                    value={settings.encryptionType}
                    label="نوع التشفير"
                    onChange={handleChange}
                  >
                    <MenuItem value="TLS">STARTTLS (الأكثر شيوعاً)</MenuItem>
                    <MenuItem value="SSL">SSL/TLS</MenuItem>
                    <MenuItem value="NONE">بدون تشفير</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* Sync Settings */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h5" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                <CheckCircleOutlined style={{ marginRight: '0.375rem' }} /> حالة المزامنة والنشاط
              </Typography>
              <Divider sx={{ mb: '1.0rem' }} />
              <Stack spacing={3}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={settings.listenerEnabled}
                      onChange={handleChange}
                      name="listenerEnabled"
                      color="primary"
                    />
                  }
                  label="تفعيل مستكشف البريد الآلي (Email Listener)"
                />
                <TextField
                  fullWidth
                  type="number"
                  label="فترة التحديث (بالدقائق)"
                  name="syncIntervalMins"
                  value={settings.syncIntervalMins}
                  onChange={handleChange}
                  helperText="الوقت المستغرق بين كل عملية فحص للبريد الجديد"
                />
                <Alert severity={settings.listenerEnabled ? "info" : "warning"}>
                  {settings.listenerEnabled 
                    ? "النظام سيقوم بفحص البريد آلياً وتحويله لطلبات موافقة."
                    : "الاستقبال الآلي متوقف حالياً."}
                </Alert>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* SMTP Settings */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h5" gutterBottom sx={{ display: 'flex', justifyContent: 'space-between' }}>
                إعدادات خادم الإرسال (SMTP)
                <Button 
                  size="small" 
                  variant="outlined" 
                  onClick={() => handleTestConnection('smtp')} 
                  disabled={testing}
                  startIcon={testing ? <CircularProgress size={16} /> : <CheckCircleOutlined />}
                >
                  فحص الإرسال
                </Button>
              </Typography>
              <Divider sx={{ mb: '1.0rem' }} />
              <Stack spacing={2}>
                <TextField
                  fullWidth
                  label="خادم SMTP"
                  name="smtpHost"
                  value={settings.smtpHost}
                  onChange={handleChange}
                  placeholder="smtp.hostinger.com"
                />
                <TextField
                  fullWidth
                  type="number"
                  label="منفذ SMTP"
                  name="smtpPort"
                  value={settings.smtpPort}
                  onChange={handleChange}
                />
                <TextField
                  fullWidth
                  label="اسم مستخدم SMTP"
                  name="smtpUsername"
                  value={settings.smtpUsername}
                  onChange={handleChange}
                />
                <TextField
                  fullWidth
                  type="password"
                  label="كلمة مرور SMTP"
                  name="smtpPassword"
                  value={settings.smtpPassword}
                  onChange={handleChange}
                  placeholder="أدخل كلمة المرور الجديدة فقط في حال الرغبة في تغييرها"
                />
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* IMAP Settings */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h5" gutterBottom sx={{ display: 'flex', justifyContent: 'space-between' }}>
                إعدادات خادم الاستقبال (IMAP)
                <Button 
                  size="small" 
                  variant="outlined" 
                  onClick={() => handleTestConnection('imap')} 
                  disabled={testing}
                  startIcon={testing ? <CircularProgress size={16} /> : <CheckCircleOutlined />}
                >
                  فحص الاستقبال
                </Button>
              </Typography>
              <Divider sx={{ mb: '1.0rem' }} />
              <Stack spacing={2}>
                <TextField
                  fullWidth
                  label="خادم IMAP"
                  name="imapHost"
                  value={settings.imapHost}
                  onChange={handleChange}
                  placeholder="imap.hostinger.com"
                />
                <TextField
                  fullWidth
                  type="number"
                  label="منفذ IMAP"
                  name="imapPort"
                  value={settings.imapPort}
                  onChange={handleChange}
                />
                <TextField
                  fullWidth
                  label="اسم مستخدم IMAP"
                  name="imapUsername"
                  value={settings.imapUsername}
                  onChange={handleChange}
                />
                <TextField
                  fullWidth
                  type="password"
                  label="كلمة مرور IMAP"
                  name="imapPassword"
                  value={settings.imapPassword}
                  onChange={handleChange}
                  placeholder="أدخل كلمة المرور الجديدة فقط في حال الرغبة في تغييرها"
                />
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* Action Buttons */}
        <Grid item xs={12}>
          <Box display="flex" justifyContent="flex-end" sx={{ gap: '1.0rem', mb: '2.0rem' }}>
            <Button variant="outlined" color="secondary" onClick={fetchSettings}>
              إلغاء التعديلات
            </Button>
            <Button variant="contained" color="primary" size="large" onClick={handleSave} disabled={loading}>
              {loading ? <CircularProgress size={24} /> : 'حفظ كافة الإعدادات'}
            </Button>
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};

export default EmailSettingsPage;


