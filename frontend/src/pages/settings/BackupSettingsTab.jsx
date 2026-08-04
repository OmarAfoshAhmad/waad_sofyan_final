import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  Backup as BackupIcon,
  DeleteSweep as PurgeIcon,
  Download as DownloadIcon,
  FactCheck as ValidateIcon,
  PlayArrow as RunIcon,
  RestartAlt as RehearseIcon,
  Save as SaveIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { openSnackbar } from 'api/snackbar';
import { systemBackupService } from 'services/api/system-backup.service';

const BACKUP_TYPES = [
  { value: 'FULL_SYSTEM', label: 'النظام الكامل (قاعدة البيانات + المرفقات)' },
  { value: 'DATABASE_ONLY', label: 'قاعدة البيانات فقط' },
  { value: 'FILES_ONLY', label: 'المرفقات فقط' }
];

const STATUS_CHIP = {
  SUCCESS: { label: 'ناجحة', color: 'success' },
  FAILED: { label: 'فاشلة', color: 'error' },
  RUNNING: { label: 'قيد التنفيذ', color: 'warning' }
};

const notify = (message, color) => openSnackbar({ open: true, message, variant: 'alert', alert: { color }, close: true });

const formatBytes = (bytes) => {
  if (bytes === null || bytes === undefined) return '—';
  if (bytes < 1024) return `${bytes} بايت`;
  const units = ['ك.بايت', 'م.بايت', 'ج.بايت', 'ت.بايت'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
};

const formatDateTime = (value) => (value ? new Date(value).toLocaleString('ar-LY') : '—');

const formatDuration = (ms) => {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${ms} م.ث`;
  const seconds = ms / 1000;
  return seconds < 60 ? `${seconds.toFixed(1)} ث` : `${Math.floor(seconds / 60)} د ${Math.round(seconds % 60)} ث`;
};

const errorMessage = (error, fallback) => error?.response?.data?.messageAr || error?.response?.data?.message || error?.message || fallback;

const BackupSettingsTab = () => {
  const [status, setStatus] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [busyJobId, setBusyJobId] = useState(null);
  const [manualType, setManualType] = useState('FULL_SYSTEM');
  const [manualNote, setManualNote] = useState('');

  const load = useCallback(async () => {
    try {
      const [statusData, jobsData, settingsData] = await Promise.all([
        systemBackupService.getStatus(),
        systemBackupService.list(),
        systemBackupService.getSettings()
      ]);
      setStatus(statusData);
      setJobs(jobsData);
      setSettings(settingsData);
    } catch (error) {
      notify(errorMessage(error, 'تعذر تحميل بيانات النسخ الاحتياطي'), 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const runBackup = async () => {
    setRunning(true);
    try {
      const job = await systemBackupService.create(manualType, manualNote.trim() || null);
      if (job?.status === 'SUCCESS') {
        notify(`تمت النسخة الاحتياطية بنجاح (${formatBytes(job.fileSize)})`, 'success');
      } else {
        notify(`فشلت النسخة الاحتياطية: ${job?.errorMessage || 'سبب غير معروف'}`, 'error');
      }
      setManualNote('');
      await load();
    } catch (error) {
      notify(errorMessage(error, 'تعذر تنفيذ النسخة الاحتياطية'), 'error');
    } finally {
      setRunning(false);
    }
  };

  const saveSettings = async () => {
    setSaving(true);
    try {
      const saved = await systemBackupService.updateSettings(settings);
      setSettings(saved);
      notify('تم حفظ إعدادات النسخ الاحتياطي', 'success');
      await load();
    } catch (error) {
      notify(errorMessage(error, 'تعذر حفظ الإعدادات'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const validateJob = async (id) => {
    setBusyJobId(id);
    try {
      const result = await systemBackupService.validate(id);
      notify(result.messageAr, result.valid ? 'success' : 'error');
    } catch (error) {
      notify(errorMessage(error, 'تعذر التحقق من النسخة'), 'error');
    } finally {
      setBusyJobId(null);
    }
  };

  const rehearseJob = async (id) => {
    setBusyJobId(id);
    try {
      const result = await systemBackupService.rehearse(id);
      notify(result.messageAr, result.success ? 'success' : 'error');
    } catch (error) {
      notify(errorMessage(error, 'تعذر اختبار الاستعادة'), 'error');
    } finally {
      setBusyJobId(null);
    }
  };

  const downloadJob = async (job) => {
    setBusyJobId(job.id);
    try {
      const blob = await systemBackupService.download(job.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = job.fileName || `waad-backup-${job.id}.zip`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      notify(errorMessage(error, 'تعذر تنزيل النسخة'), 'error');
    } finally {
      setBusyJobId(null);
    }
  };

  const purge = async (dryRun) => {
    if (!dryRun) {
      const confirmed = window.confirm('سيتم حذف النسخ الأقدم من مدة الاحتفاظ نهائياً. لن تُحذف آخر نسخة ناجحة. هل تريد المتابعة؟');
      if (!confirmed) return;
    }
    try {
      const result = await systemBackupService.purge(dryRun);
      notify(result.messageAr, 'success');
      if (!dryRun) await load();
    } catch (error) {
      notify(errorMessage(error, 'تعذر تنفيذ التنظيف'), 'error');
    }
  };

  const updateSetting = (key, value) => setSettings((previous) => ({ ...previous, [key]: value }));

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={2}>
      <ModernPageHeader
        title="النسخ الاحتياطي والاستعادة"
        subtitle="نسخ فورية ومجدولة لقاعدة البيانات والمرفقات، مع التحقق من السلامة وسياسة الاحتفاظ"
        icon={BackupIcon}
      />

      <MainCard title="حالة وجهة النسخ">
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography variant="caption" color="text.secondary">
              المسار على السيرفر
            </Typography>
            <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
              {status?.localPath || '—'}
            </Typography>
            <Box sx={{ mt: 1 }}>
              <Chip
                size="small"
                label={status?.localPathWritable ? 'قابل للكتابة' : 'غير قابل للكتابة'}
                color={status?.localPathWritable ? 'success' : 'error'}
              />
            </Box>
          </Grid>
          <Grid item xs={6} md={3}>
            <Typography variant="caption" color="text.secondary">
              المساحة المتاحة
            </Typography>
            <Typography variant="h5">{formatBytes(status?.localUsableSpace)}</Typography>
          </Grid>
          <Grid item xs={6} md={3}>
            <Typography variant="caption" color="text.secondary">
              إجمالي النسخ
            </Typography>
            <Typography variant="h5">
              {status?.backupCount ?? 0}
              <Typography component="span" variant="body2" color="success.main" sx={{ mx: 0.5 }}>
                ({status?.successfulBackupCount ?? 0} ناجحة
              </Typography>
              <Typography component="span" variant="body2" color="error.main">
                / {status?.failedBackupCount ?? 0} فاشلة)
              </Typography>
            </Typography>
          </Grid>
        </Grid>
        {!status?.localPathWritable && (
          <Alert severity="error" sx={{ mt: 2 }}>
            مسار النسخ الاحتياطي غير قابل للكتابة. لن تنجح أي نسخة حتى تتم معالجة صلاحيات المسار على السيرفر.
          </Alert>
        )}
      </MainCard>

      <MainCard title="نسخة احتياطية فورية">
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              select
              fullWidth
              size="small"
              label="نوع النسخة"
              value={manualType}
              onChange={(event) => setManualType(event.target.value)}
            >
              {BACKUP_TYPES.map((type) => (
                <MenuItem key={type.value} value={type.value}>
                  {type.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} md={5}>
            <TextField
              fullWidth
              size="small"
              label="ملاحظة (اختياري)"
              value={manualNote}
              onChange={(event) => setManualNote(event.target.value)}
            />
          </Grid>
          <Grid item xs={12} md={3}>
            <Button
              fullWidth
              variant="contained"
              startIcon={running ? <CircularProgress size={18} color="inherit" /> : <RunIcon />}
              disabled={running || !status?.localPathWritable}
              onClick={runBackup}
            >
              {running ? 'جارٍ التنفيذ…' : 'تنفيذ الآن'}
            </Button>
          </Grid>
        </Grid>
        {running && (
          <Alert severity="info" sx={{ mt: 2 }}>
            قد تستغرق النسخة عدة دقائق حسب حجم البيانات. لا تغلق الصفحة حتى تكتمل.
          </Alert>
        )}
      </MainCard>

      <MainCard title="النسخ التلقائي المجدول وسياسة الاحتفاظ">
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={3}>
            <FormControlLabel
              control={
                <Switch
                  checked={Boolean(settings?.autoBackupEnabled)}
                  onChange={(event) => updateSetting('autoBackupEnabled', event.target.checked)}
                />
              }
              label="تفعيل النسخ التلقائي"
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              fullWidth
              size="small"
              label="نوع النسخة المجدولة"
              value={settings?.autoBackupType || 'FULL_SYSTEM'}
              onChange={(event) => updateSetting('autoBackupType', event.target.value)}
              disabled={!settings?.autoBackupEnabled}
            >
              {BACKUP_TYPES.map((type) => (
                <MenuItem key={type.value} value={type.value}>
                  {type.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="number"
              label="الساعة (0–23)"
              inputProps={{ min: 0, max: 23 }}
              value={settings?.autoBackupHour ?? 2}
              onChange={(event) => updateSetting('autoBackupHour', Number(event.target.value))}
              disabled={!settings?.autoBackupEnabled}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="number"
              label="الدقيقة (0–59)"
              inputProps={{ min: 0, max: 59 }}
              value={settings?.autoBackupMinute ?? 0}
              onChange={(event) => updateSetting('autoBackupMinute', Number(event.target.value))}
              disabled={!settings?.autoBackupEnabled}
            />
          </Grid>

          <Grid item xs={12}>
            <Divider />
          </Grid>

          <Grid item xs={12} md={3}>
            <TextField
              fullWidth
              size="small"
              type="number"
              label="مدة الاحتفاظ (بالأيام)"
              inputProps={{ min: 1 }}
              value={settings?.retentionDays ?? 30}
              onChange={(event) => updateSetting('retentionDays', Number(event.target.value))}
              helperText="لا تُحذف آخر نسخة ناجحة مهما بلغ عمرها"
            />
          </Grid>
          <Grid item xs={12} md={9}>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Button
                variant="contained"
                startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
                disabled={saving}
                onClick={saveSettings}
              >
                حفظ الإعدادات
              </Button>
              <Button variant="outlined" startIcon={<PurgeIcon />} onClick={() => purge(true)}>
                معاينة التنظيف
              </Button>
              <Button variant="outlined" color="error" startIcon={<PurgeIcon />} onClick={() => purge(false)}>
                تنفيذ التنظيف
              </Button>
            </Stack>
          </Grid>

          {settings?.lastAutoBackupAt && (
            <Grid item xs={12}>
              <Alert severity={settings.lastAutoBackupStatus === 'SUCCESS' ? 'success' : 'warning'}>
                آخر نسخة تلقائية: {formatDateTime(settings.lastAutoBackupAt)} — {settings.lastAutoBackupMessage}
              </Alert>
            </Grid>
          )}
        </Grid>
      </MainCard>

      <MainCard title="سجل النسخ الاحتياطية" content={false}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>النوع</TableCell>
                <TableCell>الحالة</TableCell>
                <TableCell>الحجم</TableCell>
                <TableCell>المدة</TableCell>
                <TableCell>التاريخ</TableCell>
                <TableCell>المنفّذ</TableCell>
                <TableCell align="center">إجراءات</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {jobs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">لا توجد نسخ احتياطية بعد</Typography>
                  </TableCell>
                </TableRow>
              )}
              {jobs.map((job) => {
                const chip = STATUS_CHIP[job.status] || { label: job.status, color: 'default' };
                const isSuccess = job.status === 'SUCCESS';
                const busy = busyJobId === job.id;
                return (
                  <TableRow key={job.id} hover>
                    <TableCell>{job.id}</TableCell>
                    <TableCell>{BACKUP_TYPES.find((t) => t.value === job.type)?.label || job.type}</TableCell>
                    <TableCell>
                      <Tooltip title={job.errorMessage || ''} arrow disableHoverListener={!job.errorMessage}>
                        <Chip size="small" label={chip.label} color={chip.color} />
                      </Tooltip>
                    </TableCell>
                    <TableCell>{formatBytes(job.fileSize)}</TableCell>
                    <TableCell>{formatDuration(job.durationMs)}</TableCell>
                    <TableCell>{formatDateTime(job.startedAt)}</TableCell>
                    <TableCell>{job.createdBy || '—'}</TableCell>
                    <TableCell align="center">
                      <Stack direction="row" spacing={0.5} justifyContent="center">
                        <Tooltip title="تنزيل">
                          <span>
                            <Button size="small" disabled={!isSuccess || busy} onClick={() => downloadJob(job)}>
                              <DownloadIcon fontSize="small" />
                            </Button>
                          </span>
                        </Tooltip>
                        <Tooltip title="التحقق من السلامة (checksum)">
                          <span>
                            <Button size="small" disabled={!isSuccess || busy} onClick={() => validateJob(job.id)}>
                              <ValidateIcon fontSize="small" />
                            </Button>
                          </span>
                        </Tooltip>
                        <Tooltip title="اختبار الاستعادة (بدون المساس بقاعدة التشغيل)">
                          <span>
                            <Button size="small" disabled={!isSuccess || busy} onClick={() => rehearseJob(job.id)}>
                              <RehearseIcon fontSize="small" />
                            </Button>
                          </span>
                        </Tooltip>
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </MainCard>
    </Stack>
  );
};

export default BackupSettingsTab;
