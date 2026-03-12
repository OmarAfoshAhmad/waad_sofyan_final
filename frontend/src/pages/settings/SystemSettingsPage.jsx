import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Slider,
  Stack,
  Switch,
  Tab,
  Tabs,
  TextField,
  Typography
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
  Business as BusinessIcon,
  CloudUpload as CloudUploadIcon,
  LocalHospital as ProviderPortalIcon,
  Lock as SecurityIcon,
  Preview as PreviewIcon,
  Refresh as RefreshIcon,
  Save as SaveIcon,
  Settings as SettingsIcon,
  Speed as SpeedIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import waadLogoFallback from 'assets/images/waad-logo.png';
import featureFlagsService from 'services/api/featureFlags.service';
import systemSettingsService from 'services/api/systemSettings.service';
import { companyService } from 'services/api/company.service';
import useSystemConfig from 'hooks/useSystemConfig';
import useConfig from 'hooks/useConfig';

const KEYS = {
  systemNameAr: 'SYSTEM_NAME_AR',
  systemNameEn: 'SYSTEM_NAME_EN',
  logoUrl: 'LOGO_URL',
  fontFamily: 'FONT_FAMILY',
  fontSizeBase: 'FONT_SIZE_BASE',
  claimSlaDays: 'CLAIM_SLA_DAYS',
  preApprovalSlaDays: 'PRE_APPROVAL_SLA_DAYS',
  beneficiaryNumberFormat: 'BENEFICIARY_NUMBER_FORMAT',
  beneficiaryNumberPrefix: 'BENEFICIARY_NUMBER_PREFIX',
  beneficiaryNumberDigits: 'BENEFICIARY_NUMBER_DIGITS',
  eligibilityStrictMode: 'ELIGIBILITY_STRICT_MODE',
  waitingPeriodDaysDefault: 'WAITING_PERIOD_DAYS_DEFAULT',
  eligibilityGracePeriodDays: 'ELIGIBILITY_GRACE_PERIOD_DAYS'
};

const PROVIDER_PORTAL_FLAG_KEY = 'PROVIDER_PORTAL_ENABLED';

const TabPanel = ({ children, value, index }) => (
  <Box role="tabpanel" hidden={value !== index} sx={{ height: '100%', display: value === index ? 'flex' : 'none', flexDirection: 'column' }}>
    {children}
  </Box>
);

const FieldGroup = ({ title, children, icon: Icon, color = 'primary.main' }) => (
  <Box>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
      {Icon && <Icon sx={{ fontSize: 20, color }} />}
      <Typography variant="subtitle2" fontWeight={700} color={color}>
        {title}
      </Typography>
    </Box>
    {children}
  </Box>
);

const toBool = (value, fallback = false) => {
  if (value === undefined || value === null) return fallback;
  return String(value).toLowerCase() === 'true';
};

const toInt = (value, fallback) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
};

const SystemSettingsPage = () => {
  const theme = useTheme();
  const { setField } = useConfig();
  const { refresh: refreshSystemConfig } = useSystemConfig();

  const [tabValue, setTabValue] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const [rawSettings, setRawSettings] = useState([]);
  const [providerPortalEnabled, setProviderPortalEnabled] = useState(false);

  const [formData, setFormData] = useState({
    companyId: 1,
    companyName: '',
    companyCode: '',
    companyActive: true,
    businessType: '',
    phone: '',
    email: '',
    address: '',
    website: '',
    taxNumber: '',
    systemNameAr: '',
    systemNameEn: '',
    logoUrl: '',
    fontFamily: 'Tajawal',
    fontSizeBase: 14,
    claimSlaDays: 10,
    preApprovalSlaDays: 3,
    beneficiaryNumberFormat: '[PRO]-[COMP]-[YEAR]-[EMP_NO][REL_SUFFIX]',
    beneficiaryNumberPrefix: 'TD',
    beneficiaryNumberDigits: 8,
    eligibilityStrictMode: true,
    waitingPeriodDaysDefault: 0,
    eligibilityGracePeriodDays: 0
  });

  const settingsMap = useMemo(() => {
    const map = new Map();
    for (const item of rawSettings) {
      map.set(item.settingKey, item.settingValue);
    }
    return map;
  }, [rawSettings]);

  const hasKey = useCallback((key) => settingsMap.has(key), [settingsMap]);

  const loadData = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);

      const [settings, flags, companyResponse] = await Promise.all([
        systemSettingsService.getAll(),
        featureFlagsService.getAllFlags(),
        companyService.getSystemCompany()
      ]);

      const normalized = settings || [];
      setRawSettings(normalized);

      const company = companyResponse?.data || {};

      const byKey = new Map(normalized.map((s) => [s.settingKey, s.settingValue]));
      setFormData({
        companyId: company.id || 1,
        companyName: company.name || 'وعد',
        companyCode: company.code || 'WAAD',
        companyActive: company.active !== undefined ? Boolean(company.active) : true,
        businessType: company.businessType || 'إدارة النفقات الطبية',
        phone: company.phone || '',
        email: company.email || '',
        address: company.address || '',
        website: company.website || '',
        taxNumber: company.taxNumber || '',
        systemNameAr: byKey.get(KEYS.systemNameAr) || 'نظام واعد الطبي',
        systemNameEn: byKey.get(KEYS.systemNameEn) || 'TBA WAAD System',
        logoUrl: byKey.get(KEYS.logoUrl) || company.logoUrl || '',
        fontFamily: byKey.get(KEYS.fontFamily) || 'Tajawal',
        fontSizeBase: toInt(byKey.get(KEYS.fontSizeBase), 14),
        claimSlaDays: toInt(byKey.get(KEYS.claimSlaDays), 10),
        preApprovalSlaDays: toInt(byKey.get(KEYS.preApprovalSlaDays), 3),
        beneficiaryNumberFormat: byKey.get(KEYS.beneficiaryNumberFormat) || '[PRO]-[COMP]-[YEAR]-[EMP_NO][REL_SUFFIX]',
        beneficiaryNumberPrefix: byKey.get(KEYS.beneficiaryNumberPrefix) || 'TD',
        beneficiaryNumberDigits: toInt(byKey.get(KEYS.beneficiaryNumberDigits), 8),
        eligibilityStrictMode: toBool(byKey.get(KEYS.eligibilityStrictMode), true),
        waitingPeriodDaysDefault: toInt(byKey.get(KEYS.waitingPeriodDaysDefault), 0),
        eligibilityGracePeriodDays: toInt(byKey.get(KEYS.eligibilityGracePeriodDays), 0)
      });

      const portalFlag = (flags || []).find((f) => f.flagKey === PROVIDER_PORTAL_FLAG_KEY);
      setProviderPortalEnabled(Boolean(portalFlag?.enabled));
    } catch (e) {
      setError('فشل تحميل نافذة الإعدادات');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const updateField = (field) => (event) => {
    setFormData((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const saveSettingIfExists = async (key, value) => {
    if (!hasKey(key)) return;
    await systemSettingsService.updateSetting(key, String(value));
  };

  const handleSaveAll = async () => {
    try {
      setIsSaving(true);
      setError(null);

      await Promise.all([
        companyService.updateDefaultCompany({
          id: formData.companyId,
          name: formData.companyName,
          code: formData.companyCode,
          active: formData.companyActive,
          logoUrl: formData.logoUrl || null,
          businessType: formData.businessType,
          phone: formData.phone,
          email: formData.email,
          address: formData.address,
          website: formData.website,
          taxNumber: formData.taxNumber
        }),
        saveSettingIfExists(KEYS.systemNameAr, formData.systemNameAr),
        saveSettingIfExists(KEYS.systemNameEn, formData.systemNameEn),
        saveSettingIfExists(KEYS.logoUrl, formData.logoUrl),
        saveSettingIfExists(KEYS.fontFamily, formData.fontFamily),
        saveSettingIfExists(KEYS.fontSizeBase, formData.fontSizeBase),
        saveSettingIfExists(KEYS.claimSlaDays, formData.claimSlaDays),
        saveSettingIfExists(KEYS.preApprovalSlaDays, formData.preApprovalSlaDays),
        saveSettingIfExists(KEYS.beneficiaryNumberFormat, formData.beneficiaryNumberFormat),
        saveSettingIfExists(KEYS.beneficiaryNumberPrefix, formData.beneficiaryNumberPrefix),
        saveSettingIfExists(KEYS.beneficiaryNumberDigits, formData.beneficiaryNumberDigits),
        saveSettingIfExists(KEYS.eligibilityStrictMode, formData.eligibilityStrictMode),
        saveSettingIfExists(KEYS.waitingPeriodDaysDefault, formData.waitingPeriodDaysDefault),
        saveSettingIfExists(KEYS.eligibilityGracePeriodDays, formData.eligibilityGracePeriodDays)
      ]);

      if (formData.fontFamily) setField('fontFamily', formData.fontFamily);
      if (formData.fontSizeBase) setField('fontSize', formData.fontSizeBase);

      refreshSystemConfig();
      setSuccess('تم حفظ الإعدادات بنجاح');
      await loadData();
    } catch (e) {
      setError(e?.response?.data?.message || 'فشل حفظ الإعدادات');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleProviderPortal = async (event) => {
    const next = event.target.checked;
    try {
      setIsSaving(true);
      setError(null);
      await featureFlagsService.toggleFlag(PROVIDER_PORTAL_FLAG_KEY, next);
      setProviderPortalEnabled(next);
      refreshSystemConfig();
      setSuccess(next ? 'تم إظهار بوابة مقدم الخدمة' : 'تم إخفاء بوابة مقدم الخدمة');
    } catch (e) {
      setError(e?.response?.data?.message || 'فشل تحديث حالة بوابة مقدم الخدمة');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={320}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ height: 'calc(100vh - 125px)', display: 'flex', flexDirection: 'column', overflow: 'hidden', px: 0 }}>
      <Box sx={{ px: 2, pt: 1, flexShrink: 0 }}>
        <ModernPageHeader
          title="إعدادات النظام"
          subtitle="نسخة كاملة مع ميزة إظهار بوابة مقدم الخدمة"
          icon={<SettingsIcon sx={{ fontSize: '3.2rem', color: 'primary.main' }} />}
          noIconBox
          actions={
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" onClick={loadData} startIcon={<RefreshIcon />} disabled={isSaving}>
                تحديث
              </Button>
              <Button variant="contained" onClick={() => window.open('/provider/eligibility-check', '_blank')} startIcon={<PreviewIcon />}>
                استعراض
              </Button>
            </Stack>
          }
          sx={{ mb: 1 }}
        />
      </Box>

      <Box sx={{ px: 2 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 1.5 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert severity="success" sx={{ mb: 1.5 }} onClose={() => setSuccess(null)}>
            {success}
          </Alert>
        )}
      </Box>

      <Card
        sx={{
          flex: 1,
          maxHeight: 'calc(100vh - 210px)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          borderRadius: 0,
          border: 'none',
          bgcolor: 'transparent',
          mx: 2,
          mb: 1
        }}
      >
        <Tabs
          value={tabValue}
          onChange={(e, val) => setTabValue(val)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            borderBottom: 1,
            borderColor: 'divider',
            minHeight: 40,
            bgcolor: 'background.paper',
            borderRadius: '8px 8px 0 0',
            '& .MuiTab-root': {
              minHeight: 40,
              fontSize: '0.85rem',
              fontWeight: 700,
              textTransform: 'none',
              py: 0
            }
          }}
        >
          <Tab icon={<BusinessIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="معلومات المؤسسة" />
          <Tab icon={<SecurityIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="قواعد الاستحقاق" />
          <Tab icon={<SpeedIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="المحرك التشغيلي" />
          <Tab icon={<ProviderPortalIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="بوابة مقدم الخدمة" />
        </Tabs>

        <Box sx={{ flex: 1, overflow: 'hidden', bgcolor: 'background.paper', borderRadius: '0 0 8px 8px' }}>
          <TabPanel value={tabValue} index={0}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Stack spacing={2}>
                      <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                        <FieldGroup title="الهوية البصرية" icon={BusinessIcon}>
                          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                            <Box
                              sx={{
                                width: 60,
                                height: 60,
                                borderRadius: 1.5,
                                border: '1px dashed',
                                borderColor: 'divider',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                bgcolor: alpha('#000', 0.02),
                                flexShrink: 0
                              }}
                            >
                              <img
                                src={formData.logoUrl || waadLogoFallback}
                                alt="Logo"
                                style={{ maxWidth: '80%', maxHeight: '80%' }}
                                onError={(e) => {
                                  e.currentTarget.src = waadLogoFallback;
                                }}
                              />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                              <Button variant="outlined" component="label" size="small" startIcon={<CloudUploadIcon />} fullWidth sx={{ mb: 1 }}>
                                تغيير الشعار
                                <input
                                  type="file"
                                  hidden
                                  accept="image/*"
                                  onChange={(e) => {
                                    if (e.target.files?.[0]) {
                                      const reader = new FileReader();
                                      reader.onloadend = () => setFormData((p) => ({ ...p, logoUrl: reader.result || '' }));
                                      reader.readAsDataURL(e.target.files[0]);
                                    }
                                  }}
                                />
                              </Button>
                              <TextField fullWidth size="small" label="رابط الشعار" value={formData.logoUrl} onChange={updateField('logoUrl')} />
                            </Box>
                          </Box>
                        </FieldGroup>
                      </Paper>

                      <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                        <FieldGroup title="المظهر والخط" icon={SettingsIcon}>
                          <Stack spacing={1.5}>
                            <TextField select fullWidth size="small" label="نوع الخط" value={formData.fontFamily} onChange={updateField('fontFamily')}>
                              <MenuItem value="Tajawal">Tajawal</MenuItem>
                              <MenuItem value="Cairo">Cairo</MenuItem>
                            </TextField>
                            <Box>
                              <Typography variant="caption" color="text.secondary" display="block">
                                حجم الخط ({formData.fontSizeBase}px)
                              </Typography>
                              <Slider
                                value={formData.fontSizeBase}
                                onChange={(e, val) => setFormData((p) => ({ ...p, fontSizeBase: Number(val) }))}
                                min={12}
                                max={18}
                                step={1}
                                valueLabelDisplay="auto"
                                size="small"
                              />
                            </Box>
                          </Stack>
                        </FieldGroup>
                      </Paper>
                    </Stack>
                  </Grid>

                  <Grid size={{ xs: 12, md: 8 }}>
                    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                      <FieldGroup title="المعلومات الأساسية" icon={BusinessIcon}>
                        <Grid container spacing={1.5}>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="اسم الشركة" value={formData.companyName} onChange={updateField('companyName')} required />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 3 }}>
                            <TextField fullWidth size="small" label="كود الشركة" value={formData.companyCode} onChange={updateField('companyCode')} required />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 3 }}>
                            <FormControlLabel
                              control={<Switch checked={formData.companyActive} onChange={(e) => setFormData((p) => ({ ...p, companyActive: e.target.checked }))} />}
                              label={formData.companyActive ? 'الشركة نشطة' : 'الشركة غير نشطة'}
                            />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="نوع النشاط" value={formData.businessType} onChange={updateField('businessType')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="اسم النظام (عربي)" value={formData.systemNameAr} onChange={updateField('systemNameAr')} required />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="اسم النظام (إنجليزي)" value={formData.systemNameEn} onChange={updateField('systemNameEn')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="الهاتف" value={formData.phone} onChange={updateField('phone')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="البريد الإلكتروني" value={formData.email} onChange={updateField('email')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="الموقع" value={formData.website} onChange={updateField('website')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" label="الرقم الضريبي" value={formData.taxNumber} onChange={updateField('taxNumber')} />
                          </Grid>
                          <Grid size={12}>
                            <TextField fullWidth size="small" multiline rows={2} label="العنوان" value={formData.address} onChange={updateField('address')} />
                          </Grid>
                        </Grid>
                      </FieldGroup>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
              <Divider />
              <Box sx={{ p: 1.5, display: 'flex', justifyContent: 'flex-end', bgcolor: 'background.paper' }}>
                <Button variant="contained" size="small" startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />} disabled={isSaving} onClick={handleSaveAll}>
                  {isSaving ? 'جاري الحفظ...' : 'حفظ الإعدادات'}
                </Button>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={1}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, maxWidth: 900 }}>
                  <FieldGroup title="قواعد التحقق من الاستحقاق" icon={SecurityIcon}>
                    <Stack spacing={1.5}>
                      <FormControlLabel
                        control={<Switch checked={formData.eligibilityStrictMode} onChange={(e) => setFormData((p) => ({ ...p, eligibilityStrictMode: e.target.checked }))} />}
                        label="تفعيل وضع الاستحقاق الصارم"
                      />

                      <TextField
                        type="number"
                        fullWidth
                        size="small"
                        label="فترة الانتظار الافتراضية (يوم)"
                        value={formData.waitingPeriodDaysDefault}
                        onChange={(e) => setFormData((p) => ({ ...p, waitingPeriodDaysDefault: Number(e.target.value) }))}
                      />

                      <TextField
                        type="number"
                        fullWidth
                        size="small"
                        label="فترة السماح للاستحقاق (يوم)"
                        value={formData.eligibilityGracePeriodDays}
                        onChange={(e) => setFormData((p) => ({ ...p, eligibilityGracePeriodDays: Number(e.target.value) }))}
                      />
                    </Stack>
                  </FieldGroup>
                </Paper>
              </Box>
              <Divider />
              <Box sx={{ p: 1.5, display: 'flex', justifyContent: 'flex-end', bgcolor: 'background.paper' }}>
                <Button variant="contained" size="small" startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />} disabled={isSaving} onClick={handleSaveAll}>
                  {isSaving ? 'جاري الحفظ...' : 'حفظ الإعدادات'}
                </Button>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={2}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 7 }}>
                    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                      <FieldGroup title="الإعدادات التشغيلية" icon={SpeedIcon}>
                        <Grid container spacing={2}>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" type="number" label="SLA المطالبات (يوم)" value={formData.claimSlaDays} onChange={(e) => setFormData((p) => ({ ...p, claimSlaDays: Number(e.target.value) }))} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" type="number" label="SLA الموافقات (يوم)" value={formData.preApprovalSlaDays} onChange={(e) => setFormData((p) => ({ ...p, preApprovalSlaDays: Number(e.target.value) }))} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 8 }}>
                            <TextField fullWidth size="small" label="تنسيق رقم المستفيد" value={formData.beneficiaryNumberFormat} onChange={updateField('beneficiaryNumberFormat')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 2 }}>
                            <TextField fullWidth size="small" label="البادئة" value={formData.beneficiaryNumberPrefix} onChange={updateField('beneficiaryNumberPrefix')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 2 }}>
                            <TextField fullWidth size="small" type="number" label="عدد الأرقام" value={formData.beneficiaryNumberDigits} onChange={(e) => setFormData((p) => ({ ...p, beneficiaryNumberDigits: Number(e.target.value) }))} />
                          </Grid>
                        </Grid>
                      </FieldGroup>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
              <Divider />
              <Box sx={{ p: 1.5, display: 'flex', justifyContent: 'flex-end', bgcolor: 'background.paper' }}>
                <Button variant="contained" size="small" startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />} disabled={isSaving} onClick={handleSaveAll}>
                  {isSaving ? 'جاري الحفظ...' : 'حفظ الإعدادات'}
                </Button>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={3}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, maxWidth: 760 }}>
                  <FieldGroup title="إظهار/إخفاء بوابة مقدم الخدمة" icon={ProviderPortalIcon} color="success.main">
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      عند التعطيل تختفي بوابة مقدم الخدمة من القائمة الجانبية. عند التفعيل تظهر للمستخدمين المخولين.
                    </Typography>

                    <Stack direction="row" alignItems="center" spacing={2}>
                      <Switch checked={providerPortalEnabled} onChange={handleToggleProviderPortal} disabled={isSaving} color="success" />
                      <Typography variant="subtitle1" fontWeight={700} color={providerPortalEnabled ? 'success.main' : 'text.primary'}>
                        {providerPortalEnabled ? 'البوابة ظاهرة' : 'البوابة مخفية'}
                      </Typography>
                      {isSaving && <CircularProgress size={18} />}
                    </Stack>

                    <Button variant="outlined" sx={{ mt: 2 }} startIcon={<PreviewIcon />} onClick={() => window.open('/provider/eligibility-check', '_blank')}>
                      استعراض بوابة مقدم الخدمة
                    </Button>
                  </FieldGroup>
                </Paper>
              </Box>
            </Box>
          </TabPanel>
        </Box>
      </Card>
    </Box>
  );
};

export default SystemSettingsPage;
