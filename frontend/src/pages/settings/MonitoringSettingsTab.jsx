import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography
} from '@mui/material';
import {
  HealthAndSafety as HealthIcon,
  NotificationsActive as TelegramIcon,
  Save as SaveIcon,
  Send as SendIcon,
  WarningAmber as WarningIcon
} from '@mui/icons-material';
import systemMonitoringService from 'services/api/systemMonitoring.service';

const DEFAULT_SETTINGS = {
  telegramEnabled: false,
  tokenConfigured: false,
  maskedBotToken: '',
  botToken: '',
  chatId: '',
  threadId: '',
  alertEnvironment: 'local',
  minIntervalSeconds: 300,
  recoveryEnabled: true,
  automaticMonitoringEnabled: false,
  checkIntervalSeconds: 300,
  diskWarningPercent: 80,
  diskCriticalPercent: 90,
  maxBackupAgeHours: 72,
  repeatedErrorThreshold: 10,
  repeatedErrorWindowMinutes: 15,
  alertCooldownSeconds: 1800,
  lastAutoCheckAt: null,
  lastAutoCheckStatus: null,
  lastAutoCheckMessage: null,
  lastExternalHeartbeatAt: null,
  lastExternalHeartbeatSource: null,
  lastExternalHeartbeatStatus: null,
  lastAlertState: null
};

const HEARTBEAT_STALE_MS = 10 * 60 * 1000;

const STATUS_LABELS = {
  OK: 'سليم',
  WARNING: 'تحذير',
  CRITICAL: 'خطأ',
  UNKNOWN: 'غير معروف'
};

const statusColor = (status) => {
  if (status === 'OK') return 'success';
  if (status === 'WARNING') return 'warning';
  if (status === 'CRITICAL') return 'error';
  return 'default';
};

const formatDate = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('ar-LY');
  } catch {
    return value;
  }
};

const errorMessage = (error) =>
  error?.response?.data?.messageAr ||
  error?.response?.data?.data?.messageAr ||
  error?.userMessage ||
  'فشل تنفيذ العملية. تحقق من البيانات وحاول مرة أخرى.';

const validateRules = (settings) => {
  if (Number(settings.checkIntervalSeconds) < 60) return 'فترة الفحص يجب ألا تقل عن 60 ثانية.';
  if (Number(settings.diskWarningPercent) < 1 || Number(settings.diskWarningPercent) > 99) return 'حد تحذير القرص يجب أن يكون بين 1 و 99.';
  if (Number(settings.diskCriticalPercent) <= Number(settings.diskWarningPercent)) return 'حد القرص الحرج يجب أن يكون أكبر من حد التحذير.';
  if (Number(settings.diskCriticalPercent) > 100) return 'حد القرص الحرج يجب ألا يتجاوز 100.';
  if (Number(settings.maxBackupAgeHours) < 1) return 'الحد الأقصى لعمر النسخة الاحتياطية يجب ألا يقل عن ساعة واحدة.';
  if (Number(settings.repeatedErrorThreshold) < 1) return 'حد أخطاء النظام المتكررة يجب ألا يقل عن 1.';
  if (Number(settings.repeatedErrorWindowMinutes) < 1) return 'نافذة أخطاء النظام يجب ألا تقل عن دقيقة واحدة.';
  if (Number(settings.alertCooldownSeconds) < 60) return 'فترة تهدئة التنبيهات يجب ألا تقل عن 60 ثانية.';
  return null;
};

const MonitoringSettingsTab = () => {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [settingsData, healthData] = await Promise.all([systemMonitoringService.getSettings(), systemMonitoringService.getHealth()]);
      setSettings({ ...DEFAULT_SETTINGS, ...settingsData, botToken: '' });
      setHealth(healthData);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const saveSettings = async () => {
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const validation = validateRules(settings);
      if (validation) {
        setError(validation);
        return;
      }
      const payload = {
        ...settings,
        botToken: settings.botToken?.trim() || null,
        minIntervalSeconds: Number(settings.minIntervalSeconds || 300),
        checkIntervalSeconds: Number(settings.checkIntervalSeconds || 300),
        diskWarningPercent: Number(settings.diskWarningPercent || 80),
        diskCriticalPercent: Number(settings.diskCriticalPercent || 90),
        maxBackupAgeHours: Number(settings.maxBackupAgeHours || 72),
        repeatedErrorThreshold: Number(settings.repeatedErrorThreshold || 10),
        repeatedErrorWindowMinutes: Number(settings.repeatedErrorWindowMinutes || 15),
        alertCooldownSeconds: Number(settings.alertCooldownSeconds || 1800)
      };
      const saved = await systemMonitoringService.updateSettings(payload);
      setSettings({ ...DEFAULT_SETTINGS, ...saved, botToken: '' });
      setMessage('تم حفظ إعدادات التنبيهات والمراقبة بنجاح.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const testTelegram = async () => {
    setTesting(true);
    setMessage(null);
    setError(null);
    try {
      const result = await systemMonitoringService.testTelegram();
      if (result?.success) {
        setMessage(result.messageAr || 'تم إرسال رسالة الاختبار بنجاح.');
      } else {
        setError(result?.messageAr || 'فشل إرسال رسالة الاختبار. تحقق من Bot Token و Chat ID واتصال السيرفر بالإنترنت.');
      }
      const refreshed = await systemMonitoringService.getSettings();
      setSettings({ ...DEFAULT_SETTINGS, ...refreshed, botToken: '' });
    } catch (e) {
      setError(errorMessage(e) || 'فشل إرسال رسالة الاختبار. تحقق من Bot Token و Chat ID واتصال السيرفر بالإنترنت.');
    } finally {
      setTesting(false);
    }
  };

  const tokenDisplay = useMemo(() => {
    if (settings.botToken) return settings.botToken;
    return settings.maskedBotToken || '';
  }, [settings.botToken, settings.maskedBotToken]);

  const heartbeatStatus = useMemo(() => {
    if (!settings.lastExternalHeartbeatAt) {
      return { label: 'غير مُشغَّل', color: 'default' };
    }
    const age = Date.now() - new Date(settings.lastExternalHeartbeatAt).getTime();
    if (Number.isNaN(age) || age > HEARTBEAT_STALE_MS) {
      return { label: 'نبضة قديمة', color: 'warning' };
    }
    if (settings.lastExternalHeartbeatStatus && settings.lastExternalHeartbeatStatus !== 'UP') {
      return { label: 'يبلّغ عن خلل', color: 'warning' };
    }
    return { label: 'نشِط', color: 'success' };
  }, [settings.lastExternalHeartbeatAt, settings.lastExternalHeartbeatStatus]);

  const telegramStatus = useMemo(() => {
    if (!settings.telegramEnabled) {
      return { label: 'غير مفعّل', color: 'default' };
    }
    const hasToken = settings.tokenConfigured || Boolean(settings.botToken?.trim());
    if (!hasToken || !settings.chatId?.trim()) {
      return { label: 'مفعّل لكن ناقص Bot Token / Chat ID', color: 'warning' };
    }
    return { label: 'مفعّل وجاهز', color: 'success' };
  }, [settings.telegramEnabled, settings.tokenConfigured, settings.botToken, settings.chatId]);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={320}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: '1rem', overflow: 'auto', height: '100%' }} dir="rtl">
      <Stack spacing={2}>
        {message && (
          <Alert severity="success" onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}
        {error && (
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <TelegramIcon color="primary" />
              <Typography variant="h6">إعدادات Telegram</Typography>
              <Chip size="small" color={telegramStatus.color} label={telegramStatus.label} variant="outlined" />
            </Stack>
            <Alert severity="info">
              يتم استخدام Telegram لإرسال تنبيهات تشغيل النظام مثل توقف الخدمة أو امتلاء القرص أو فشل النسخ الاحتياطي. يمكنك إرسال رسالة
              اختبار يدوية للتأكد من صحة الإعدادات، كما تُرسل التنبيهات تلقائيًا عند تفعيل المراقبة التلقائية أدناه.
            </Alert>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={Boolean(settings.telegramEnabled)}
                      onChange={(e) => setSettings((prev) => ({ ...prev, telegramEnabled: e.target.checked }))}
                    />
                  }
                  label="تفعيل تنبيهات Telegram"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 8 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Bot Token"
                  type={settings.botToken ? 'password' : 'text'}
                  value={tokenDisplay}
                  onChange={(e) => setSettings((prev) => ({ ...prev, botToken: e.target.value }))}
                  placeholder={settings.tokenConfigured ? settings.maskedBotToken : 'أدخل Bot Token'}
                  helperText={
                    settings.tokenConfigured && !settings.botToken
                      ? 'تم حفظ Token مسبقًا ويتم عرضه بشكل مخفي. اكتب قيمة جديدة فقط إذا أردت تغييره.'
                      : 'لن يتم عرض الـ Token كنص واضح بعد الحفظ.'
                  }
                  inputProps={{ dir: 'ltr' }}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Chat ID"
                  value={settings.chatId || ''}
                  onChange={(e) => setSettings((prev) => ({ ...prev, chatId: e.target.value }))}
                  inputProps={{ dir: 'ltr' }}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="بيئة التنبيه"
                  value={settings.alertEnvironment || 'local'}
                  onChange={(e) => setSettings((prev) => ({ ...prev, alertEnvironment: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="number"
                  label="أقل مدة بين التنبيهات بالثواني"
                  value={settings.minIntervalSeconds || 300}
                  onChange={(e) => setSettings((prev) => ({ ...prev, minIntervalSeconds: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Telegram Thread ID اختياري"
                  value={settings.threadId || ''}
                  onChange={(e) => setSettings((prev) => ({ ...prev, threadId: e.target.value }))}
                  inputProps={{ dir: 'ltr' }}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={Boolean(settings.recoveryEnabled)}
                      onChange={(e) => setSettings((prev) => ({ ...prev, recoveryEnabled: e.target.checked }))}
                    />
                  }
                  label="تفعيل رسائل التعافي"
                />
              </Grid>
            </Grid>
            <Stack direction="row" spacing={1} justifyContent="flex-start">
              <Button
                variant="contained"
                startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />}
                onClick={saveSettings}
                disabled={saving}
              >
                حفظ الإعدادات
              </Button>
            </Stack>
          </Stack>
        </Card>

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="h6">اختبار التنبيهات</Typography>
            <Typography variant="body2" color="text.secondary">
              يرسل هذا الزر رسالة اختبار فورية إلى Telegram للتأكد من صحة Bot Token و Chat ID.
            </Typography>
            <Button
              variant="outlined"
              color="primary"
              startIcon={testing ? <CircularProgress size={16} /> : <SendIcon />}
              onClick={testTelegram}
              disabled={testing}
              sx={{ alignSelf: 'flex-start' }}
            >
              {testing ? 'جاري إرسال رسالة الاختبار...' : 'إرسال رسالة اختبار'}
            </Button>
            <Typography variant="body2" color="text.secondary">
              آخر اختبار: {formatDate(settings.lastTestAt)} —{' '}
              {settings.lastTestStatus === 'SUCCESS' ? 'ناجح' : settings.lastTestStatus === 'FAILED' ? 'فشل' : 'لا يوجد'}
            </Typography>
            {settings.lastTestMessage && (
              <Alert severity={settings.lastTestStatus === 'SUCCESS' ? 'success' : 'warning'}>{settings.lastTestMessage}</Alert>
            )}
          </Stack>
        </Card>

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <HealthIcon color="primary" />
              <Typography variant="h6">حالة النظام</Typography>
              <Chip
                size="small"
                color={statusColor(health?.overallStatus)}
                label={STATUS_LABELS[health?.overallStatus] || health?.overallStatus || 'غير معروف'}
                variant="outlined"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              البيئة: {health?.environment || '—'} — آخر فحص: {formatDate(health?.serverTime)} — Git:{' '}
              <span dir="ltr">{health?.gitCommit || '—'}</span>
            </Typography>
            <Grid container spacing={2}>
              {(health?.cards || []).map((card) => (
                <Grid key={card.key} size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                        <Typography fontWeight={700}>{card.titleAr}</Typography>
                        <Chip size="small" color={statusColor(card.status)} label={STATUS_LABELS[card.status] || card.status} />
                      </Stack>
                      <Typography variant="body2">{card.descriptionAr}</Typography>
                      {card.details && (
                        <Typography variant="caption" color="text.secondary" dir="ltr">
                          {card.details}
                        </Typography>
                      )}
                      <Typography variant="caption" color="text.secondary">
                        آخر فحص: {formatDate(card.checkedAt)}
                      </Typography>
                    </Stack>
                  </Paper>
                </Grid>
              ))}
            </Grid>
          </Stack>
        </Card>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <WarningIcon color="warning" />
                  <Typography variant="h6">قواعد التنبيه التلقائي</Typography>
                  <Chip
                    size="small"
                    color={settings.automaticMonitoringEnabled ? 'success' : 'default'}
                    label={settings.automaticMonitoringEnabled ? 'مفعلة' : 'متوقفة'}
                    variant="outlined"
                  />
                </Stack>
                <Divider />
                <FormControlLabel
                  control={
                    <Switch
                      checked={Boolean(settings.automaticMonitoringEnabled)}
                      onChange={(e) => setSettings((prev) => ({ ...prev, automaticMonitoringEnabled: e.target.checked }))}
                    />
                  }
                  label="تفعيل المراقبة التلقائية"
                />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="فترة الفحص بالثواني"
                      value={settings.checkIntervalSeconds || 300}
                      onChange={(e) => setSettings((prev) => ({ ...prev, checkIntervalSeconds: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="فترة تهدئة التنبيهات بالثواني"
                      value={settings.alertCooldownSeconds || 1800}
                      onChange={(e) => setSettings((prev) => ({ ...prev, alertCooldownSeconds: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="تحذير مساحة القرص %"
                      value={settings.diskWarningPercent || 80}
                      onChange={(e) => setSettings((prev) => ({ ...prev, diskWarningPercent: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="حد مساحة القرص الحرج %"
                      value={settings.diskCriticalPercent || 90}
                      onChange={(e) => setSettings((prev) => ({ ...prev, diskCriticalPercent: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="أقصى عمر للنسخة الاحتياطية بالساعات"
                      value={settings.maxBackupAgeHours || 72}
                      onChange={(e) => setSettings((prev) => ({ ...prev, maxBackupAgeHours: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="حد أخطاء النظام المتكررة"
                      value={settings.repeatedErrorThreshold || 10}
                      onChange={(e) => setSettings((prev) => ({ ...prev, repeatedErrorThreshold: e.target.value }))}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField
                      fullWidth
                      size="small"
                      type="number"
                      label="نافذة أخطاء النظام بالدقائق"
                      value={settings.repeatedErrorWindowMinutes || 15}
                      onChange={(e) => setSettings((prev) => ({ ...prev, repeatedErrorWindowMinutes: e.target.value }))}
                    />
                  </Grid>
                </Grid>
                <Alert severity="info">
                  القواعد الفعالة: قاعدة البيانات، مساحة القرص، مسار النسخ الاحتياطي، مسار المرفقات، عمر آخر نسخة احتياطية، وتكرار أخطاء
                  النظام. يتم إرسال التنبيه عند تغير الحالة أو ارتفاع الخطورة أو انتهاء فترة التهدئة، ويتم إرسال رسالة تعافٍ واحدة عند رجوع
                  الحالة إلى السليم.
                </Alert>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      آخر فحص تلقائي: {formatDate(settings.lastAutoCheckAt)}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      نتيجة آخر فحص: {settings.lastAutoCheckStatus || '—'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="body2" color="text.secondary">
                      آخر رسالة فحص: {settings.lastAutoCheckMessage || '—'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="body2" color="text.secondary">
                      آخر حالة تنبيه: {settings.lastAlertState?.alertKey || '—'} / {settings.lastAlertState?.status || '—'} /{' '}
                      {settings.lastAlertState?.lastSummary || '—'}
                    </Typography>
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1} justifyContent="flex-start">
                  <Button
                    variant="contained"
                    startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />}
                    onClick={saveSettings}
                    disabled={saving}
                  >
                    حفظ قواعد التنبيه
                  </Button>
                </Stack>
              </Stack>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card variant="outlined" sx={{ p: 2, height: '100%' }}>
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="h6">المراقب الخارجي</Typography>
                  <Chip size="small" color={heartbeatStatus.color} label={heartbeatStatus.label} variant="outlined" />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  المراقب الخارجي أداة مستقلة تعمل خارج هذا النظام لاكتشاف توقف الموقع بالكامل حتى لو توقف backend، وترسل تنبيه Telegram.
                  يجب تشغيلها كخدمة/حاوية منفصلة من مجلد <span dir="ltr">tools/external-monitor</span>.
                </Typography>
                <Grid container spacing={1}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      آخر نبضة: {formatDate(settings.lastExternalHeartbeatAt)}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      الحالة المُبلّغة: {settings.lastExternalHeartbeatStatus || '—'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="body2" color="text.secondary">
                      المصدر: <span dir="ltr">{settings.lastExternalHeartbeatSource || '—'}</span>
                    </Typography>
                  </Grid>
                </Grid>
                {!settings.lastExternalHeartbeatAt && (
                  <Alert severity="warning">
                    لم تصل أي نبضة بعد. شغّل الأداة الخارجية واضبط <span dir="ltr">WAAD_MONITOR_HEARTBEAT_URL</span> على مسار
                    <span dir="ltr"> /api/v1/system/monitoring/external-heartbeat</span>. راجع ملف README داخل المجلد للتعليمات.
                  </Alert>
                )}
              </Stack>
            </Card>
          </Grid>
        </Grid>
      </Stack>
    </Box>
  );
};

export default MonitoringSettingsTab;
