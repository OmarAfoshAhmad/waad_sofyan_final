/**
 * System Settings Admin Page
 * Manage system-wide configurable settings
 *
 * Features:
 * - View all settings by category
 * - Edit editable settings
 * - SLA configuration
 * - SLA compliance report
 */

import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  TextField,
  MenuItem,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Tooltip,
  Switch,
  FormControlLabel,
  LinearProgress
} from '@mui/material';
import {
  Settings as SettingsIcon,
  Edit as EditIcon,
  Refresh as RefreshIcon,
  Save as SaveIcon,
  RestartAlt as ResetIcon,
  Assessment as ReportIcon,
  Schedule as ScheduleIcon,
  ToggleOn as ToggleOnIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import systemSettingsService from 'services/api/systemSettings.service';
import featureFlagsService from 'services/api/featureFlags.service';
import useSystemConfig from 'hooks/useSystemConfig';

const PASSWORD_RESET_METHOD_KEY = 'PASSWORD_RESET_METHOD';
const PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY = 'PASSWORD_RESET_TOKEN_EXPIRY_MINUTES';
const PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY = 'PASSWORD_RESET_OTP_EXPIRY_MINUTES';
const PASSWORD_RESET_OTP_LENGTH_KEY = 'PASSWORD_RESET_OTP_LENGTH';

const PASSWORD_RESET_SETTINGS_KEYS = new Set([
  PASSWORD_RESET_METHOD_KEY,
  PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY,
  PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY,
  PASSWORD_RESET_OTP_LENGTH_KEY
]);

// Tab panel component
function TabPanel({ children, value, index, ...other }) {
  return (
    <div role="tabpanel" hidden={value !== index} id={`settings-tabpanel-${index}`} aria-labelledby={`settings-tab-${index}`} {...other}>
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

const SystemSettingsPage = () => {
  const { refresh: refreshSystemConfig } = useSystemConfig();

  // State
  const [loading, setLoading] = useState(true);
  const [settings, setSettings] = useState([]);
  const [categories, setCategories] = useState([]);
  const [activeTab, setActiveTab] = useState(0);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // SLA state
  const [slaDays, setSlaDays] = useState(null);
  const [newSlaDays, setNewSlaDays] = useState('');
  const [slaUpdating, setSlaUpdating] = useState(false);
  const [slaReport, setSlaReport] = useState(null);
  const [reportLoading, setReportLoading] = useState(false);

  // Feature flags state
  const [featureFlags, setFeatureFlags] = useState([]);
  const [flagsLoading, setFlagsLoading] = useState(false);
  const [flagTogglingKey, setFlagTogglingKey] = useState(null);

  // Password reset settings state
  const [passwordResetConfig, setPasswordResetConfig] = useState({
    method: 'TOKEN',
    tokenExpiryMinutes: '60',
    otpExpiryMinutes: '10',
    otpLength: '6'
  });
  const [passwordResetSaving, setPasswordResetSaving] = useState(false);

  // Edit dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingSetting, setEditingSetting] = useState(null);
  const [editValue, setEditValue] = useState('');
  const [saving, setSaving] = useState(false);

  // Load settings
  const loadSettings = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const data = await systemSettingsService.getAll();
      setSettings(data || []);

      // Extract unique categories
      const uniqueCategories = [...new Set((data || []).map((s) => s.category).filter(Boolean))];
      setCategories(uniqueCategories);
    } catch (err) {
      console.error('Failed to load settings:', err);
      setError('فشل تحميل الإعدادات');
    } finally {
      setLoading(false);
    }
  }, []);

  // Load SLA info
  const loadSlaInfo = useCallback(async () => {
    try {
      const data = await systemSettingsService.getClaimSlaDays();
      setSlaDays(data.slaDays);
      setNewSlaDays(data.slaDays.toString());
    } catch (err) {
      console.error('Failed to load SLA info:', err);
    }
  }, []);

  // Load SLA report
  const loadSlaReport = useCallback(async () => {
    try {
      setReportLoading(true);
      const data = await systemSettingsService.getSlaComplianceReport();
      setSlaReport(data);
    } catch (err) {
      console.error('Failed to load SLA report:', err);
      setError('فشل تحميل تقرير SLA');
    } finally {
      setReportLoading(false);
    }
  }, []);

  // Load feature flags
  const loadFeatureFlags = useCallback(async () => {
    try {
      setFlagsLoading(true);
      const data = await featureFlagsService.getAllFlags();
      setFeatureFlags(data || []);
    } catch (err) {
      console.error('Failed to load feature flags:', err);
    } finally {
      setFlagsLoading(false);
    }
  }, []);

  // Toggle a feature flag
  const handleToggleFlag = async (flagKey, currentEnabled) => {
    try {
      setFlagTogglingKey(flagKey);
      await featureFlagsService.toggleFlag(flagKey, !currentEnabled);
      setFeatureFlags((prev) =>
        prev.map((f) => (f.flagKey === flagKey ? { ...f, enabled: !currentEnabled } : f))
      );
      // Bust the sessionStorage cache so Navigation re-reads the updated flags
      refreshSystemConfig();
      setSuccess(`تم ${!currentEnabled ? 'تفعيل' : 'تعطيل'} الميزة بنجاح`);
      setTimeout(() => setSuccess(null), 4000);
    } catch (err) {
      setError(err.response?.data?.message || 'فشل تغيير حالة الميزة');
    } finally {
      setFlagTogglingKey(null);
    }
  };

  useEffect(() => {
    loadSettings();
    loadSlaInfo();
    loadFeatureFlags();
  }, [loadSettings, loadSlaInfo, loadFeatureFlags]);

  // Update SLA days
  const handleUpdateSlaDays = async () => {
    const newValue = parseInt(newSlaDays, 10);
    if (isNaN(newValue) || newValue < 1 || newValue > 30) {
      setError('يجب أن تكون أيام SLA بين 1 و 30');
      return;
    }

    try {
      setSlaUpdating(true);
      setError(null);

      const result = await systemSettingsService.updateClaimSlaDays(newValue);
      setSlaDays(result.newValue);
      setSuccess(`تم تحديث أيام SLA من ${result.oldValue} إلى ${result.newValue}`);

      setTimeout(() => setSuccess(null), 5000);
    } catch (err) {
      console.error('Failed to update SLA days:', err);
      setError(err.response?.data?.message || 'فشل تحديث أيام SLA');
    } finally {
      setSlaUpdating(false);
    }
  };

  // Reset SLA days
  const handleResetSlaDays = async () => {
    try {
      setSlaUpdating(true);
      setError(null);

      const result = await systemSettingsService.resetClaimSlaDays();
      setSlaDays(result.newValue);
      setNewSlaDays(result.newValue.toString());
      setSuccess(`تم إعادة تعيين أيام SLA إلى القيمة الافتراضية (${result.newValue})`);

      setTimeout(() => setSuccess(null), 5000);
    } catch (err) {
      console.error('Failed to reset SLA days:', err);
      setError('فشل إعادة تعيين أيام SLA');
    } finally {
      setSlaUpdating(false);
    }
  };

  // Open edit dialog
  const handleEditSetting = (setting) => {
    setEditingSetting(setting);
    setEditValue(setting.settingValue);
    setEditDialogOpen(true);
  };

  // Save setting
  const handleSaveSetting = async () => {
    if (!editingSetting) return;

    try {
      setSaving(true);
      setError(null);

      await systemSettingsService.updateSetting(editingSetting.settingKey, editValue);
      setSuccess(`تم تحديث الإعداد: ${editingSetting.settingKey}`);
      setEditDialogOpen(false);
      loadSettings();

      setTimeout(() => setSuccess(null), 5000);
    } catch (err) {
      console.error('Failed to save setting:', err);
      setError(err.response?.data?.message || 'فشل حفظ الإعداد');
    } finally {
      setSaving(false);
    }
  };

  // Get settings by category
  const getSettingsByCategory = (category) => {
    return settings.filter((s) => {
      if (s.category !== category) {
        return false;
      }

      // These settings are edited in the dedicated password reset tab.
      if (category === 'SECURITY' && PASSWORD_RESET_SETTINGS_KEYS.has(s.settingKey)) {
        return false;
      }

      return true;
    });
  };

  const getSettingValue = (key, fallback) => {
    const setting = settings.find((s) => s.settingKey === key);
    return setting?.settingValue ?? fallback;
  };

  useEffect(() => {
    if (!settings.length) {
      return;
    }

    setPasswordResetConfig({
      method: getSettingValue(PASSWORD_RESET_METHOD_KEY, 'TOKEN').toUpperCase() === 'OTP' ? 'OTP' : 'TOKEN',
      tokenExpiryMinutes: getSettingValue(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY, '60'),
      otpExpiryMinutes: getSettingValue(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY, '10'),
      otpLength: getSettingValue(PASSWORD_RESET_OTP_LENGTH_KEY, '6')
    });
  }, [settings]);

  const validatePasswordResetConfig = () => {
    const tokenMinutes = Number(passwordResetConfig.tokenExpiryMinutes);
    const otpMinutes = Number(passwordResetConfig.otpExpiryMinutes);
    const otpLength = Number(passwordResetConfig.otpLength);

    if (!['TOKEN', 'OTP'].includes(passwordResetConfig.method)) {
      return 'طريقة الاسترجاع يجب أن تكون TOKEN أو OTP';
    }
    if (!Number.isInteger(tokenMinutes) || tokenMinutes < 5 || tokenMinutes > 1440) {
      return 'مدة صلاحية رابط الاسترجاع يجب أن تكون بين 5 و 1440 دقيقة';
    }
    if (!Number.isInteger(otpMinutes) || otpMinutes < 1 || otpMinutes > 60) {
      return 'مدة صلاحية OTP يجب أن تكون بين 1 و 60 دقيقة';
    }
    if (!Number.isInteger(otpLength) || otpLength < 4 || otpLength > 10) {
      return 'طول OTP يجب أن يكون بين 4 و 10 أرقام';
    }

    return null;
  };

  const handleSavePasswordResetConfig = async () => {
    const validationError = validatePasswordResetConfig();
    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setPasswordResetSaving(true);
      setError(null);

      await Promise.all([
        systemSettingsService.updateSetting(PASSWORD_RESET_METHOD_KEY, passwordResetConfig.method),
        systemSettingsService.updateSetting(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY, String(passwordResetConfig.tokenExpiryMinutes)),
        systemSettingsService.updateSetting(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY, String(passwordResetConfig.otpExpiryMinutes)),
        systemSettingsService.updateSetting(PASSWORD_RESET_OTP_LENGTH_KEY, String(passwordResetConfig.otpLength))
      ]);

      setSuccess('تم حفظ إعدادات استرجاع كلمة المرور بنجاح');
      await loadSettings();
      setTimeout(() => setSuccess(null), 5000);
    } catch (err) {
      console.error('Failed to save password reset settings:', err);
      setError(err.response?.data?.message || 'فشل حفظ إعدادات استرجاع كلمة المرور');
    } finally {
      setPasswordResetSaving(false);
    }
  };

  const handleResetPasswordResetDefaults = () => {
    setPasswordResetConfig({
      method: 'TOKEN',
      tokenExpiryMinutes: '60',
      otpExpiryMinutes: '10',
      otpLength: '6'
    });
  };

  // Render value type chip
  const renderTypeChip = (type) => {
    const colors = {
      INTEGER: 'primary',
      DECIMAL: 'secondary',
      BOOLEAN: 'success',
      STRING: 'default',
      JSON: 'warning'
    };
    return <Chip size="small" label={type} color={colors[type] || 'default'} />;
  };

  // Render setting value
  const renderSettingValue = (setting) => {
    if (setting.valueType === 'BOOLEAN') {
      return (
        <Chip
          size="small"
          label={setting.settingValue === 'true' ? 'نعم' : 'لا'}
          color={setting.settingValue === 'true' ? 'success' : 'default'}
        />
      );
    }
    return setting.settingValue;
  };

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <CircularProgress />
        <Typography sx={{ mt: 2 }}>جاري تحميل الإعدادات...</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <ModernPageHeader
        title="إعدادات النظام"
        subtitle="إدارة الإعدادات العامة للنظام"
        icon={SettingsIcon}
        actions={
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => {
              loadSettings();
              loadSlaInfo();
              loadFeatureFlags();
            }}
          >
            تحديث
          </Button>
        }
      />

      {/* Alerts */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      {/* Tabs */}
      <MainCard>
        <Tabs value={activeTab} onChange={(e, v) => setActiveTab(v)} variant="scrollable" scrollButtons="auto">
          <Tab label="إعدادات SLA" icon={<ScheduleIcon />} iconPosition="start" />
          <Tab label="تقرير الامتثال" icon={<ReportIcon />} iconPosition="start" />
          <Tab label="الميزات والوحدات" icon={<ToggleOnIcon />} iconPosition="start" />
          <Tab label="استرجاع كلمة المرور" icon={<SettingsIcon />} iconPosition="start" />
          {categories.map((cat) => (
            <Tab key={cat} label={cat} />
          ))}
        </Tabs>

        {/* SLA Settings Tab */}
        <TabPanel value={activeTab} index={0}>
          <Grid container spacing={3}>
            {/* Current SLA */}
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    أيام SLA الحالية للمطالبات
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, my: 3 }}>
                    <Typography variant="h2" color="primary">
                      {slaDays || '-'}
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                      يوم عمل
                    </Typography>
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    يجب معالجة المطالبات خلال هذه الفترة
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            {/* Update SLA */}
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    تحديث أيام SLA
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, my: 2 }}>
                    <TextField
                      label="أيام SLA الجديدة"
                      type="number"
                      value={newSlaDays}
                      onChange={(e) => setNewSlaDays(e.target.value)}
                      inputProps={{ min: 1, max: 30 }}
                      size="small"
                      sx={{ width: 150 }}
                    />
                    <Button
                      variant="contained"
                      startIcon={slaUpdating ? <CircularProgress size={16} /> : <SaveIcon />}
                      onClick={handleUpdateSlaDays}
                      disabled={slaUpdating}
                    >
                      تحديث
                    </Button>
                    <Button variant="outlined" startIcon={<ResetIcon />} onClick={handleResetSlaDays} disabled={slaUpdating}>
                      إعادة تعيين
                    </Button>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    القيمة بين 1 و 30 يوم عمل. التغيير يؤثر على المطالبات الجديدة فقط.
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* SLA Compliance Report Tab */}
        <TabPanel value={activeTab} index={1}>
          <Box sx={{ mb: 2 }}>
            <Button
              variant="contained"
              startIcon={reportLoading ? <CircularProgress size={16} /> : <ReportIcon />}
              onClick={loadSlaReport}
              disabled={reportLoading}
            >
              تحميل التقرير
            </Button>
          </Box>

          {slaReport && (
            <Grid container spacing={3}>
              <Grid item xs={12} md={4}>
                <Card sx={{ bgcolor: 'success.lighter' }}>
                  <CardContent>
                    <Typography variant="h6" color="success.dark">
                      ضمن SLA
                    </Typography>
                    <Typography variant="h3" color="success.main">
                      {slaReport.withinSla || 0}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ bgcolor: 'warning.lighter' }}>
                  <CardContent>
                    <Typography variant="h6" color="warning.dark">
                      قريب من الموعد
                    </Typography>
                    <Typography variant="h3" color="warning.main">
                      {slaReport.nearingSla || 0}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ bgcolor: 'error.lighter' }}>
                  <CardContent>
                    <Typography variant="h6" color="error.dark">
                      تجاوز SLA
                    </Typography>
                    <Typography variant="h3" color="error.main">
                      {slaReport.breachedSla || 0}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              {slaReport.complianceRate !== undefined && (
                <Grid item xs={12}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        نسبة الامتثال
                      </Typography>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <LinearProgress
                          variant="determinate"
                          value={slaReport.complianceRate}
                          sx={{ flex: 1, height: 10, borderRadius: 5 }}
                          color={slaReport.complianceRate >= 90 ? 'success' : slaReport.complianceRate >= 70 ? 'warning' : 'error'}
                        />
                        <Typography variant="h5">{slaReport.complianceRate}%</Typography>
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              )}
            </Grid>
          )}
        </TabPanel>

        {/* Feature Flags Tab */}
        <TabPanel value={activeTab} index={2}>
          {flagsLoading ? (
            <Box sx={{ p: 3, textAlign: 'center' }}><CircularProgress /></Box>
          ) : (
            <Grid container spacing={2}>
              {featureFlags.length === 0 && (
                <Grid item xs={12}>
                  <Typography color="text.secondary" align="center">لا توجد إعدادات ميزات</Typography>
                </Grid>
              )}
              {featureFlags.map((flag) => (
                <Grid item xs={12} md={6} key={flag.flagKey}>
                  <Card variant="outlined" sx={{ p: 2, borderColor: flag.enabled ? 'success.main' : 'divider', transition: 'border-color 0.3s' }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="subtitle1" fontWeight={600}>{flag.flagName}</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{flag.description}</Typography>
                        <Typography variant="caption" color="text.disabled" fontFamily="monospace" sx={{ mt: 0.5, display: 'block' }}>{flag.flagKey}</Typography>
                      </Box>
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}>
                        {flagTogglingKey === flag.flagKey ? (
                          <CircularProgress size={24} />
                        ) : (
                          <Switch
                            checked={flag.enabled}
                            onChange={() => handleToggleFlag(flag.flagKey, flag.enabled)}
                            color="success"
                          />
                        )}
                        <Chip
                          size="small"
                          label={flag.enabled ? 'مفعّل' : 'معطّل'}
                          color={flag.enabled ? 'success' : 'default'}
                        />
                      </Box>
                    </Box>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
          <Alert severity="info" sx={{ mt: 3 }}>
            تسري التغييرات فوراً. قد يحتاج المستخدمون إلى إعادة تحميل الصفحة لرؤية التأثير.
          </Alert>
        </TabPanel>

        {/* Password Reset Settings Tab */}
        <TabPanel value={activeTab} index={3}>
          <Grid container spacing={3}>
            <Grid item xs={12} md={8}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    إعدادات استرجاع كلمة المرور
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    اختر طريقة الاسترجاع الأساسية وحدد مدد الصلاحية. الإعدادات تطبق مباشرة على واجهة "نسيت كلمة المرور".
                  </Typography>

                  <Grid container spacing={2}>
                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        select
                        label="طريقة الاسترجاع"
                        value={passwordResetConfig.method}
                        onChange={(e) => setPasswordResetConfig((prev) => ({ ...prev, method: e.target.value }))}
                      >
                        <MenuItem value="TOKEN">رابط عبر البريد (TOKEN)</MenuItem>
                        <MenuItem value="OTP">رمز تحقق (OTP)</MenuItem>
                      </TextField>
                    </Grid>

                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        type="number"
                        label="مدة صلاحية رابط الاسترجاع (دقيقة)"
                        value={passwordResetConfig.tokenExpiryMinutes}
                        onChange={(e) => setPasswordResetConfig((prev) => ({ ...prev, tokenExpiryMinutes: e.target.value }))}
                        inputProps={{ min: 5, max: 1440 }}
                      />
                    </Grid>

                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        type="number"
                        label="مدة صلاحية OTP (دقيقة)"
                        value={passwordResetConfig.otpExpiryMinutes}
                        onChange={(e) => setPasswordResetConfig((prev) => ({ ...prev, otpExpiryMinutes: e.target.value }))}
                        inputProps={{ min: 1, max: 60 }}
                      />
                    </Grid>

                    <Grid item xs={12} md={6}>
                      <TextField
                        fullWidth
                        type="number"
                        label="طول OTP"
                        value={passwordResetConfig.otpLength}
                        onChange={(e) => setPasswordResetConfig((prev) => ({ ...prev, otpLength: e.target.value }))}
                        inputProps={{ min: 4, max: 10 }}
                      />
                    </Grid>
                  </Grid>

                  <Box sx={{ display: 'flex', gap: 2, mt: 3 }}>
                    <Button
                      variant="contained"
                      startIcon={passwordResetSaving ? <CircularProgress size={16} /> : <SaveIcon />}
                      onClick={handleSavePasswordResetConfig}
                      disabled={passwordResetSaving}
                    >
                      حفظ الإعدادات
                    </Button>
                    <Button variant="outlined" startIcon={<ResetIcon />} onClick={handleResetPasswordResetDefaults} disabled={passwordResetSaving}>
                      إعادة القيم الافتراضية
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ bgcolor: 'info.lighter' }}>
                <CardContent>
                  <Typography variant="subtitle1" gutterBottom>
                    ملاحظات مهمة
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    اختيار TOKEN يعني تعطيل مسار OTP في API.
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    اختيار OTP يعني تعطيل مسار رابط البريد في API.
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    القيم هنا مرتبطة مباشرة بمفاتيح SYSTEM_SETTINGS ضمن فئة SECURITY.
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* Category Settings Tabs */}
        {categories.map((category, idx) => (
          <TabPanel key={category} value={activeTab} index={idx + 4}>
            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>المفتاح</TableCell>
                    <TableCell>القيمة</TableCell>
                    <TableCell>النوع</TableCell>
                    <TableCell>الوصف</TableCell>
                    <TableCell>قابل للتعديل</TableCell>
                    <TableCell>إجراءات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {getSettingsByCategory(category).map((setting) => (
                    <TableRow key={setting.id}>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {setting.settingKey}
                        </Typography>
                      </TableCell>
                      <TableCell>{renderSettingValue(setting)}</TableCell>
                      <TableCell>{renderTypeChip(setting.valueType)}</TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {setting.description || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={setting.isEditable ? 'نعم' : 'لا'} color={setting.isEditable ? 'success' : 'default'} />
                      </TableCell>
                      <TableCell>
                        {setting.isEditable && (
                          <Tooltip title="تعديل">
                            <IconButton size="small" color="primary" onClick={() => handleEditSetting(setting)}>
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  {getSettingsByCategory(category).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} align="center">
                        <Typography color="text.secondary">لا توجد إعدادات في هذه الفئة</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </TabPanel>
        ))}
      </MainCard>

      {/* Edit Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>تعديل الإعداد</DialogTitle>
        <DialogContent>
          {editingSetting && (
            <Box sx={{ pt: 2 }}>
              <Typography variant="subtitle2" gutterBottom>
                المفتاح: <code>{editingSetting.settingKey}</code>
              </Typography>
              {editingSetting.description && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  {editingSetting.description}
                </Typography>
              )}

              {editingSetting.valueType === 'BOOLEAN' ? (
                <FormControlLabel
                  control={<Switch checked={editValue === 'true'} onChange={(e) => setEditValue(e.target.checked ? 'true' : 'false')} />}
                  label={editValue === 'true' ? 'مفعل' : 'غير مفعل'}
                />
              ) : (
                <TextField
                  fullWidth
                  label="القيمة"
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  type={editingSetting.valueType === 'INTEGER' || editingSetting.valueType === 'DECIMAL' ? 'number' : 'text'}
                  multiline={editingSetting.valueType === 'JSON'}
                  rows={editingSetting.valueType === 'JSON' ? 4 : 1}
                />
              )}

              {editingSetting.validationRules && (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                  قواعد التحقق: {editingSetting.validationRules}
                </Typography>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>إلغاء</Button>
          <Button
            variant="contained"
            onClick={handleSaveSetting}
            disabled={saving}
            startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />}
          >
            حفظ
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SystemSettingsPage;
