import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
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
import axios from 'utils/axios';
import {
  Business as BusinessIcon,
  CloudUpload as CloudUploadIcon,
  Description as ReportIcon,
  LocalHospital as ProviderPortalIcon,
  Lock as SecurityIcon,
  Key as KeyIcon,
  Palette as PaletteIcon,
  Preview as PreviewIcon,
  Save as SaveIcon,
  Settings as SettingsIcon,
  Speed as SpeedIcon,
  Mail as MailIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import waadLogoFallback from 'assets/images/waad-logo.png';
import featureFlagsService from 'services/api/featureFlags.service';
import systemSettingsService from 'services/api/systemSettings.service';
import { companyService } from 'services/api/company.service';
import reportSettingsService from 'services/api/reports-settings.service';
import useSystemConfig from 'hooks/useSystemConfig';
import useConfig from 'hooks/useConfig';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import EmailSettingsTab from './EmailSettingsTab';
import AIKeySettingsPage from './AIKeySettingsPage';

const KEYS = {
  systemNameAr: 'SYSTEM_NAME_AR',
  systemNameEn: 'SYSTEM_NAME_EN',
  logoUrl: 'LOGO_URL',
  fontFamily: 'FONT_FAMILY',
  fontSizeBase: 'FONT_SIZE_BASE',
  claimSlaDays: 'CLAIM_SLA_DAYS',
  preApprovalSlaDays: 'PRE_APPROVAL_SLA_DAYS',
  claimBackdatedMonths: 'CLAIM_BACKDATED_MONTHS',
  beneficiaryNumberFormat: 'BENEFICIARY_NUMBER_FORMAT',
  beneficiaryNumberPrefix: 'BENEFICIARY_NUMBER_PREFIX',
  beneficiaryNumberDigits: 'BENEFICIARY_NUMBER_DIGITS',
  eligibilityStrictMode: 'ELIGIBILITY_STRICT_MODE',
  waitingPeriodDaysDefault: 'WAITING_PERIOD_DAYS_DEFAULT',
  eligibilityGracePeriodDays: 'ELIGIBILITY_GRACE_PERIOD_DAYS',
  // إعدادات المظهر
  tableHeaderBg:   'TABLE_HEADER_BG',
  tableHeaderText: 'TABLE_HEADER_TEXT',
  tableRowEven:    'TABLE_ROW_EVEN',
  selectionColor:  'SELECTION_COLOR',
  primaryColor:    'PRIMARY_COLOR'
};

const PROVIDER_PORTAL_FLAG_KEY = 'PROVIDER_PORTAL_ENABLED';

// ✅ تحميل كسول: لا تُرَندَر محتويات التاب إلا عند اختياره
const TabPanel = ({ children, value, index }) => {
  if (value !== index) return null;
  return (
    <Box role="tabpanel" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {children}
    </Box>
  );
};

const FieldGroup = ({ title, children, icon: Icon, color = 'primary.main' }) => (
  <Box>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '0.75rem' }}>
      {Icon && <Icon sx={{ fontSize: '1.25rem', color }} />}
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

/**
 * يحوّل أي قيمة لون (hex أو rgba أو rgb) إلى hex مناسب لـ input[type=color]
 * إذا فشل التحويل يُعيد #000000
 */
const toHexColor = (color) => {
  if (!color) return '#000000';
  const s = String(color).trim();
  // إذا كان بالفعل hex
  if (/^#[0-9a-fA-F]{3,6}$/.test(s)) return s.length === 4
    ? '#' + s[1]+s[1]+s[2]+s[2]+s[3]+s[3]
    : s;
  // استخراج من rgba/rgb
  const m = s.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/);
  if (m) {
    const toH = (n) => Math.round(Number(n)).toString(16).padStart(2, '0');
    return `#${toH(m[1])}${toH(m[2])}${toH(m[3])}`;
  }
  return '#000000';
};

const toInt = (value, fallback) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
};

const cleanStr = (value, fallback) => {
  if (!value || value === 'undefined' || value === 'null') return fallback;
  return value;
};

/**
 * يضمن أن قيمة اللون الـ hex تبدأ بـ #
 * مثال: '409c86' → '#409c86', '#409c86' → '#409c86'
 * القيم من نوع rgba/rgb تُترك كما هي لأنها صالحة لـ MUI
 */
const ensureHash = (color, fallback = '#000000') => {
  if (!color) return fallback;
  const s = String(color).trim();
  if (!s) return fallback;
  // rgba/rgb/hsl — صالحة مباشرةً
  if (/^(rgba?|hsla?)\(/.test(s)) return s;
  // hex مع # — صالح
  if (s.startsWith('#')) return s;
  // hex بدون # — أضف #
  if (/^[0-9a-fA-F]{3}$|^[0-9a-fA-F]{6}$/.test(s)) return `#${s}`;
  return fallback;
};

const SystemSettingsPage = () => {
  const theme = useTheme();
  const { setField } = useConfig();
  const { refresh: refreshSystemConfig, applyFlags, applyUiConfig } = useSystemConfig();
  const { updateSettings: updateVisualSettings, settings: visualSettings } = useCompanySettings();

  // ✅ ألوان مُحسَّنة مُخزَّنة بـ useMemo بدلاً من الحساب inline
  const alphaColors = useMemo(() => ({
    logoBoxBg:    alpha('#000', 0.02),              // خلفية صندوق الشعار - رمادي خفيف محايد
    primaryFaint: alpha(theme.palette.primary.main, 0.02),
    infoFaint:    alpha(theme.palette.info.main, 0.05),
  }), [theme.palette.primary.main, theme.palette.info.main]);

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
    // إعدادات المظهر — القيم الافتراضية (تُستبدل من backend في loadData)
    tableHeaderBg:   ensureHash((visualSettings || {}).tableHeaderBg,   '#E0F2F1'),
    tableHeaderText: ensureHash((visualSettings || {}).tableHeaderText, '#004D50'),
    tableRowEven:    (visualSettings || {}).tableRowEven    || 'rgba(224,242,241,0.45)',
    selectionColor:  (visualSettings || {}).selectionColor  || 'rgba(0,131,143,0.08)',
    primaryColor:    ensureHash((visualSettings || {}).primaryColor,    '#00838F'),
    claimSlaDays: 10,
    preApprovalSlaDays: 3,
    claimBackdatedMonths: 3,
    beneficiaryNumberFormat: '[PRO]-[COMP]-[YEAR]-[EMP_NO][REL_SUFFIX]',
    beneficiaryNumberPrefix: 'TD',
    beneficiaryNumberDigits: 8,
    eligibilityStrictMode: true,
    waitingPeriodDaysDefault: 0,
    eligibilityGracePeriodDays: 0
  });

  const [emailSettings, setEmailSettings] = useState({
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

      const [settings, flags, companyResponse, reportSettingsResponse, emailSettingsBatch] = await Promise.all([
        systemSettingsService.getAll(),
        featureFlagsService.getAllFlags(),
        companyService.getSystemCompany(),
        reportSettingsService.getActiveSettings(),
        axios.get('/admin/settings/email')
      ]);

      const normalized = settings || [];
      setRawSettings(normalized);

      const emailResponse = emailSettingsBatch?.data;
      if (emailResponse && emailResponse.id) {
        setEmailSettings({ ...emailResponse, smtpPassword: '', imapPassword: '' });
      }

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
        systemNameAr: cleanStr(byKey.get(KEYS.systemNameAr), 'نظام واعد الطبي'),
        systemNameEn: cleanStr(byKey.get(KEYS.systemNameEn), 'TBA WAAD System'),
        logoUrl: cleanStr(byKey.get(KEYS.logoUrl), company.logoUrl || ''),
        fontFamily: byKey.get(KEYS.fontFamily) || 'Tajawal',
        fontSizeBase: toInt(byKey.get(KEYS.fontSizeBase), 14),
        claimSlaDays: toInt(byKey.get(KEYS.claimSlaDays), 10),
        preApprovalSlaDays: toInt(byKey.get(KEYS.preApprovalSlaDays), 3),
        claimBackdatedMonths: toInt(byKey.get(KEYS.claimBackdatedMonths), 3),
        beneficiaryNumberFormat: byKey.get(KEYS.beneficiaryNumberFormat) || '[PRO]-[COMP]-[YEAR]-[EMP_NO][REL_SUFFIX]',
        beneficiaryNumberPrefix: byKey.get(KEYS.beneficiaryNumberPrefix) || 'TD',
        beneficiaryNumberDigits: toInt(byKey.get(KEYS.beneficiaryNumberDigits), 8),
        eligibilityStrictMode: toBool(byKey.get(KEYS.eligibilityStrictMode), true),
        waitingPeriodDaysDefault: toInt(byKey.get(KEYS.waitingPeriodDaysDefault), 0),
        eligibilityGracePeriodDays: toInt(byKey.get(KEYS.eligibilityGracePeriodDays), 0),
        // إعدادات المظهر — من backend أولاً، ثم localStorage كاحتياطي
        tableHeaderBg:   ensureHash(byKey.get(KEYS.tableHeaderBg)   || (visualSettings || {}).tableHeaderBg,   '#E0F2F1'),
        tableHeaderText: ensureHash(byKey.get(KEYS.tableHeaderText) || (visualSettings || {}).tableHeaderText, '#004D50'),
        tableRowEven:    byKey.get(KEYS.tableRowEven)    || (visualSettings || {}).tableRowEven    || 'rgba(224,242,241,0.45)',
        selectionColor:  byKey.get(KEYS.selectionColor)  || (visualSettings || {}).selectionColor  || 'rgba(0,131,143,0.08)',
        primaryColor:    ensureHash(byKey.get(KEYS.primaryColor)    || (visualSettings || {}).primaryColor,    '#00838F'),
        // Report Settings Fields
        pdfSettingsId: reportSettingsResponse?.id,
        claimReportTitle: reportSettingsResponse?.claimReportTitle || 'نظام وعد الطبي',
        claimReportPrimaryColor: reportSettingsResponse?.claimReportPrimaryColor || '#005f6b',
        claimReportIntro: reportSettingsResponse?.claimReportIntro || '',
        claimReportFooterNote: reportSettingsResponse?.claimReportFooterNote || '',
        claimReportSigRightTop: reportSettingsResponse?.claimReportSigRightTop || '',
        claimReportSigRightBottom: reportSettingsResponse?.claimReportSigRightBottom || '',
        claimReportSigLeftTop: reportSettingsResponse?.claimReportSigLeftTop || '',
        claimReportSigLeftBottom: reportSettingsResponse?.claimReportSigLeftBottom || ''
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

  // ✅ مُحفوظة بـ useCallback لمنع إنشاء دوال جديدة عند كل render
  const updateField = useCallback((field) => (event) => {
    setFormData((prev) => ({ ...prev, [field]: event.target.value }));
  }, []);

  // ✅ مُحفوظة بـ useCallback لتجنب إعادة الإنشاء عند كل render
  // يدعم الإنشاء التلقائي إذا لم يكن المفتاح موجوداً في backend (upsert)
  const saveSettingIfExists = useCallback(async (key, value) => {
    if (!hasKey(key) && !Object.values(KEYS).includes(key)) return;
    await systemSettingsService.updateSetting(key, value ? String(value) : '');
  }, [hasKey]);

  const handleSaveAll = async (manualData = null, manualEmail = null) => {
    const dataToSave = manualData || formData;
    const emailToSave = manualEmail || emailSettings;

    try {
      setIsSaving(true);
      setError(null);

      await Promise.all([
        companyService.updateDefaultCompany({
          id: dataToSave.companyId,
          name: dataToSave.companyName,
          code: dataToSave.companyCode,
          active: true, // Force active
          logoUrl: dataToSave.logoUrl || null,
          businessType: dataToSave.businessType,
          phone: dataToSave.phone,
          email: dataToSave.email,
          address: dataToSave.address,
          website: dataToSave.website,
          taxNumber: dataToSave.taxNumber
        }),
        saveSettingIfExists(KEYS.systemNameAr, dataToSave.companyName), // Mirror companyName
        saveSettingIfExists(KEYS.systemNameEn, dataToSave.companyName), // Mirror companyName
        saveSettingIfExists(KEYS.logoUrl, dataToSave.logoUrl),
        saveSettingIfExists(KEYS.fontFamily, dataToSave.fontFamily),
        saveSettingIfExists(KEYS.fontSizeBase, dataToSave.fontSizeBase),
        saveSettingIfExists(KEYS.claimSlaDays, dataToSave.claimSlaDays),
        saveSettingIfExists(KEYS.preApprovalSlaDays, dataToSave.preApprovalSlaDays),
        saveSettingIfExists(KEYS.claimBackdatedMonths, dataToSave.claimBackdatedMonths),
        saveSettingIfExists(KEYS.beneficiaryNumberFormat, dataToSave.beneficiaryNumberFormat),
        saveSettingIfExists(KEYS.beneficiaryNumberPrefix, dataToSave.beneficiaryNumberPrefix),
        saveSettingIfExists(KEYS.beneficiaryNumberDigits, dataToSave.beneficiaryNumberDigits),
        saveSettingIfExists(KEYS.eligibilityStrictMode, dataToSave.eligibilityStrictMode),
        saveSettingIfExists(KEYS.waitingPeriodDaysDefault, dataToSave.waitingPeriodDaysDefault),
        saveSettingIfExists(KEYS.eligibilityGracePeriodDays, dataToSave.eligibilityGracePeriodDays),
        // حفظ إعدادات المظهر في backend
        saveSettingIfExists(KEYS.tableHeaderBg,   dataToSave.tableHeaderBg),
        saveSettingIfExists(KEYS.tableHeaderText, dataToSave.tableHeaderText),
        saveSettingIfExists(KEYS.tableRowEven,    dataToSave.tableRowEven),
        saveSettingIfExists(KEYS.selectionColor,  dataToSave.selectionColor),
        saveSettingIfExists(KEYS.primaryColor,    dataToSave.primaryColor),
        // حفظ إعدادات المظهر في backend
        saveSettingIfExists(KEYS.tableHeaderBg,   dataToSave.tableHeaderBg),
        saveSettingIfExists(KEYS.tableHeaderText, dataToSave.tableHeaderText),
        saveSettingIfExists(KEYS.tableRowEven,    dataToSave.tableRowEven),
        saveSettingIfExists(KEYS.selectionColor,  dataToSave.selectionColor),
        saveSettingIfExists(KEYS.primaryColor,    dataToSave.primaryColor),
        // Save Report Settings
        ...(dataToSave.pdfSettingsId 
          ? [reportSettingsService.updateSettings(dataToSave.pdfSettingsId, {
              claimReportTitle: dataToSave.claimReportTitle,
              claimReportPrimaryColor: dataToSave.claimReportPrimaryColor,
              claimReportIntro: dataToSave.claimReportIntro,
              claimReportFooterNote: dataToSave.claimReportFooterNote,
              claimReportSigRightTop: dataToSave.claimReportSigRightTop,
              claimReportSigRightBottom: dataToSave.claimReportSigRightBottom,
              claimReportSigLeftTop: dataToSave.claimReportSigLeftTop,
              claimReportSigLeftBottom: dataToSave.claimReportSigLeftBottom
            })]
          : []),
        axios.post('/admin/settings/email', emailToSave)
      ]);

      if (dataToSave.fontFamily) setField('fontFamily', dataToSave.fontFamily);
      if (dataToSave.fontSizeBase) setField('fontSize', dataToSave.fontSizeBase);

      // ✅ تحديث فوري بدون انتظار API — ينعكس فوراً على كل المكونات
      applyUiConfig({
        logoUrl:      dataToSave.logoUrl || '',
        fontFamily:   dataToSave.fontFamily,
        fontSizeBase: dataToSave.fontSizeBase,
        systemNameAr: dataToSave.companyName,
        systemNameEn: dataToSave.companyName
      });

      // Sync the global visual context (Navbar, Title, etc)
      updateVisualSettings({
        companyName: dataToSave.companyName,
        companyNameEn: dataToSave.companyName,
        businessType: dataToSave.businessType,
        logoUrl: dataToSave.logoUrl?.startsWith('data:') ? null : (dataToSave.logoUrl || null),
        logoBase64: dataToSave.logoUrl?.startsWith('data:') ? dataToSave.logoUrl : null,
        // حفظ إعدادات المظهر في localStorage
        tableHeaderBg:   dataToSave.tableHeaderBg,
        tableHeaderText: dataToSave.tableHeaderText,
        tableRowEven:    dataToSave.tableRowEven,
        selectionColor:  dataToSave.selectionColor,
        primaryColor:    dataToSave.primaryColor
      });

      refreshSystemConfig();
      setSuccess('تم حفظ الإعدادات بنجاح وتحديث النظام');
      setTimeout(() => setSuccess(null), 3000);
      // ✅ تمت إزالة await loadData() الزائدة - البيانات محدَّثة محلياً، refreshSystemConfig() تكفي
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
      // ✅ تحديث فوري — تظهر/تختفي البوابة من القائمة فوراً بدون انتظار API
      applyFlags({ PROVIDER_PORTAL_ENABLED: next });
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
      <Box sx={{ px: '1.0rem', pt: 1, flexShrink: 0 }}>
        <ModernPageHeader
          title="إعدادات النظام"
          subtitle="تحكم كامل في مظهر وأداء النظام"
          icon={<SettingsIcon sx={{ fontSize: '1.8rem', color: 'primary.main' }} />}
          noIconBox
          actions={
          <Stack direction="row" spacing={1} alignItems="center">
            <Button 
              variant="contained" 
              color="primary"
              onClick={() => handleSaveAll()} 
              startIcon={isSaving ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
              disabled={isSaving}
              sx={{ fontWeight: 700 }}
            >
              حفظ وتطبيق التغييرات
            </Button>
          </Stack>
          }
          sx={{ mb: 1 }}
        />
      </Box>

      <Box sx={{ px: '1.0rem' }}>
        {error && (
          <Alert severity="error" sx={{ mb: '0.75rem' }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert severity="info" sx={{ mb: '0.75rem' }} onClose={() => setSuccess(null)}>
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
          mx: '1.0rem',
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
            minHeight: '2.5rem',
            bgcolor: 'background.paper',
            borderRadius: '8px 8px 0 0',
            '& .MuiTab-root': {
              minHeight: '2.5rem',
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
          <Tab icon={<ReportIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="إعدادات التقارير" />
          <Tab icon={<ProviderPortalIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="بوابة مقدم الخدمة" />
          <Tab icon={<MailIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="إعدادات البريد" />
          <Tab icon={<PaletteIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="المظهر" />
          <Tab icon={<KeyIcon sx={{ fontSize: '1.2rem' }} />} iconPosition="start" label="إعدادات الذكاء الاصطناعي" />
        </Tabs>

        <Box sx={{ flex: 1, overflow: 'hidden', bgcolor: 'background.paper', borderRadius: '0 0 8px 8px' }}>
          <TabPanel value={tabValue} index={0}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
                      <FieldGroup title="المعلومات الأساسية" icon={BusinessIcon}>
                        <Grid container spacing={2}>
                          <Grid size={{ xs: 12, sm: 8 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">اسم الشركة (الاسم المؤثر فى النظام)</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="أدخل اسم الشركة..."
                                value={formData.companyName} 
                                onChange={updateField('companyName')} 
                                required 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 4 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">كود الشركة</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="كود فريد للشركة..."
                                value={formData.companyCode} 
                                onChange={updateField('companyCode')} 
                                required 
                              />
                            </Stack>
                          </Grid>
                          
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">نوع النشاط</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="مثلاً: شركة طبية، مستشفى..."
                                value={formData.businessType} 
                                onChange={updateField('businessType')} 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">الهاتف</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="رقم الهاتف الأساسي..."
                                value={formData.phone} 
                                onChange={updateField('phone')} 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">البريد الإلكتروني</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="example@waad.ly"
                                value={formData.email} 
                                onChange={updateField('email')} 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">الموقع</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="www.example.com"
                                value={formData.website} 
                                onChange={updateField('website')} 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">الرقم الضريبي</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                placeholder="الرقم الضريبي الرسمي..."
                                value={formData.taxNumber} 
                                onChange={updateField('taxNumber')} 
                              />
                            </Stack>
                          </Grid>
                          <Grid size={12}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">العنوان</Typography>
                              <TextField 
                                fullWidth 
                                size="small" 
                                multiline 
                                rows={2} 
                                placeholder="شارع، مدينة، دولة..."
                                value={formData.address} 
                                onChange={updateField('address')} 
                              />
                            </Stack>
                          </Grid>
                        </Grid>
                      </FieldGroup>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={1}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', maxWidth: '56.25rem' }}>
                  <FieldGroup title="قواعد التحقق من الاستحقاق" icon={SecurityIcon}>
                    <Stack spacing={1.5}>
                      <FormControlLabel
                        control={<Switch checked={formData.eligibilityStrictMode} onChange={(e) => setFormData((p) => ({ ...p, eligibilityStrictMode: e.target.checked }))} />}
                        label="تفعيل وضع الاستحقاق الصارم"
                      />

                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" color="text.secondary">فترة الانتظار الافتراضية (يوم)</Typography>
                        <TextField
                          type="number"
                          fullWidth
                          size="small"
                          placeholder="مثلاً: 30"
                          value={formData.waitingPeriodDaysDefault}
                          onChange={(e) => setFormData((p) => ({ ...p, waitingPeriodDaysDefault: Number(e.target.value) }))}
                        />
                      </Stack>

                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" color="text.secondary">فترة السماح للاستحقاق (يوم)</Typography>
                        <TextField
                          type="number"
                          fullWidth
                          size="small"
                          placeholder="مثلاً: 7"
                          value={formData.eligibilityGracePeriodDays}
                          onChange={(e) => setFormData((p) => ({ ...p, eligibilityGracePeriodDays: Number(e.target.value) }))}
                        />
                      </Stack>
                    </Stack>
                  </FieldGroup>
                </Paper>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={2}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 7 }}>
                    <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
                      <FieldGroup title="الإعدادات التشغيلية" icon={SpeedIcon}>
                        <Grid container spacing={2}>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" type="number" label="SLA المطالبات (يوم)" value={formData.claimSlaDays} onChange={(e) => setFormData((p) => ({ ...p, claimSlaDays: Number(e.target.value) }))} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField fullWidth size="small" type="number" label="SLA الموافقات (يوم)" value={formData.preApprovalSlaDays} onChange={(e) => setFormData((p) => ({ ...p, preApprovalSlaDays: Number(e.target.value) }))} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <TextField
                              fullWidth
                              size="small"
                              type="number"
                              label="السماح بمطالبات قديمة (أشهر)"
                              helperText="0 = الشهر الحالي فقط، الحد الأقصى 24 شهراً"
                              inputProps={{ min: 0, max: 24 }}
                              value={formData.claimBackdatedMonths}
                              onChange={(e) => setFormData((p) => ({ ...p, claimBackdatedMonths: Number(e.target.value) }))}
                            />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 8 }}>
                            <TextField fullWidth size="small" label="تنسيق رقم المستفيد" value={formData.beneficiaryNumberFormat} onChange={updateField('beneficiaryNumberFormat')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: '1.0rem' }}>
                            <TextField fullWidth size="small" label="البادئة" value={formData.beneficiaryNumberPrefix} onChange={updateField('beneficiaryNumberPrefix')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: '1.0rem' }}>
                            <TextField fullWidth size="small" type="number" label="عدد الأرقام" value={formData.beneficiaryNumberDigits} onChange={(e) => setFormData((p) => ({ ...p, beneficiaryNumberDigits: Number(e.target.value) }))} />
                          </Grid>
                        </Grid>
                      </FieldGroup>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={3}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 8 }}>
                    <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
                      <FieldGroup title="تخصيص تقرير المطالبات" icon={ReportIcon}>
                        <Grid container spacing={2}>
                          <Grid size={{ xs: 12, sm: 8 }}>
                            <TextField fullWidth size="small" label="عنوان التقرير" value={formData.claimReportTitle} onChange={updateField('claimReportTitle')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 4 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <TextField fullWidth size="small" label="اللون الرئيسي" value={formData.claimReportPrimaryColor} onChange={updateField('claimReportPrimaryColor')} />
                              <Box sx={{ width: '2.25rem', height: '2.25rem', bgcolor: formData.claimReportPrimaryColor, border: '1px solid #ddd', borderRadius: 1, flexShrink: 0 }} />
                            </Box>
                          </Grid>
                          <Grid size={12}>
                            <TextField fullWidth size="small" multiline rows={3} label="نص المقدمة (استخدم {batchCode} لرقم الدفعة)" value={formData.claimReportIntro} onChange={updateField('claimReportIntro')} />
                          </Grid>
                          <Grid size={12}>
                            <TextField fullWidth size="small" multiline rows={2} label="ملاحظة التذييل" value={formData.claimReportFooterNote} onChange={updateField('claimReportFooterNote')} />
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Paper variant="outlined" sx={{ p: '0.75rem', bgcolor: alphaColors.primaryFaint }}>
                              <Typography variant="caption" fontWeight={700} sx={{ mb: 1, display: 'block' }}>التوقيع الأيمن</Typography>
                              <Stack spacing={1}>
                                <TextField fullWidth size="small" label="السطر العلوي" value={formData.claimReportSigRightTop} onChange={updateField('claimReportSigRightTop')} />
                                <TextField fullWidth size="small" label="السطر السفلي" value={formData.claimReportSigRightBottom} onChange={updateField('claimReportSigRightBottom')} />
                              </Stack>
                            </Paper>
                          </Grid>
                          <Grid size={{ xs: 12, sm: 6 }}>
                            <Paper variant="outlined" sx={{ p: '0.75rem', bgcolor: alphaColors.primaryFaint }}>
                              <Typography variant="caption" fontWeight={700} sx={{ mb: 1, display: 'block' }}>التوقيع الأيسر</Typography>
                              <Stack spacing={1}>
                                <TextField fullWidth size="small" label="السطر العلوي" value={formData.claimReportSigLeftTop} onChange={updateField('claimReportSigLeftTop')} />
                                <TextField fullWidth size="small" label="السطر السفلي" value={formData.claimReportSigLeftBottom} onChange={updateField('claimReportSigLeftBottom')} />
                              </Stack>
                            </Paper>
                          </Grid>
                        </Grid>
                      </FieldGroup>
                    </Paper>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                     <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: alphaColors.infoFaint, height: '100%' }}>
                        <Typography variant="subtitle2" fontWeight={700} gutterBottom>تلميحات التقرير</Typography>
                        <Typography variant="body2" sx={{ mb: 1 }}>• يمكنك استخدام الرمز <b>{'{batchCode}'}</b> في حقل المقدمة ليتم استبداله برقم الدفعة الحقيقي.</Typography>
                        <Typography variant="body2" sx={{ mb: 1 }}>• اللون الرئيسي يتحكم في لون العناوين والخطوط الفاصلة وصافي القيمة.</Typography>
                        <Typography variant="body2">• التوقيعات تظهر في الصفحة الأولى من التقرير.</Typography>
                     </Paper>
                  </Grid>
                </Grid>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={4}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', maxWidth: '47.5rem' }}>
                  <FieldGroup title="إظهار/إخفاء بوابة مقدم الخدمة" icon={ProviderPortalIcon} color="success.main">
                    <Typography variant="body2" color="text.secondary" sx={{ mb: '0.75rem' }}>
                      عند التعطيل تختفي بوابة مقدم الخدمة من القائمة الجانبية. عند التفعيل تظهر للمستخدمين المخولين.
                    </Typography>

                    <Stack direction="row" alignItems="center" spacing={2}>
                      <Switch checked={providerPortalEnabled} onChange={handleToggleProviderPortal} disabled={isSaving} color="success" />
                      <Typography variant="subtitle1" fontWeight={700} color={providerPortalEnabled ? 'success.main' : 'text.primary'}>
                        {providerPortalEnabled ? 'البوابة ظاهرة' : 'البوابة مخفية'}
                      </Typography>
                      {isSaving && <CircularProgress size={18} />}
                    </Stack>

                    <Button variant="outlined" sx={{ mt: '1.0rem' }} startIcon={<PreviewIcon />} onClick={() => window.open('/provider/eligibility-check', '_blank')}>
                      استعراض بوابة مقدم الخدمة
                    </Button>
                  </FieldGroup>
                </Paper>
              </Box>
            </Box>
          </TabPanel>

          <TabPanel value={tabValue} index={5}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto' }}>
                <EmailSettingsTab settings={emailSettings} setSettings={setEmailSettings} />
              </Box>
            </Box>
          </TabPanel>

          {/* ===================== تبويب المظهر ===================== */}
          <TabPanel value={tabValue} index={6}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <Grid container spacing={2}>

                  {/* عمود الشعار والخط */}
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Stack spacing={2}>

                      {/* الشعار */}
                      <Paper variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem' }}>
                        <FieldGroup title="شعار المؤسسة" icon={BusinessIcon} color="primary.main">
                          <Box sx={{ display: 'flex', gap: '1.0rem', alignItems: 'center' }}>
                            <Box
                              sx={{
                                width: '3.75rem',
                                height: '3.75rem',
                                borderRadius: '0.375rem',
                                border: '1px dashed',
                                borderColor: 'divider',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                bgcolor: alphaColors.logoBoxBg,
                                flexShrink: 0
                              }}
                            >
                              <img
                                src={formData.logoUrl || waadLogoFallback}
                                alt="Logo"
                                style={{ maxWidth: '80%', maxHeight: '80%' }}
                                onError={(e) => { e.currentTarget.src = waadLogoFallback; }}
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
                              <Stack spacing={0.5}>
                                <Typography variant="subtitle2" color="text.secondary">رابط الشعار</Typography>
                                <TextField fullWidth size="small" placeholder="https://..." value={formData.logoUrl} onChange={updateField('logoUrl')} />
                              </Stack>
                            </Box>
                          </Box>
                        </FieldGroup>
                      </Paper>

                      {/* نوع الخط وحجمه */}
                      <Paper variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem' }}>
                        <FieldGroup title="الخط" icon={SettingsIcon} color="primary.main">
                          <Stack spacing={1.5}>
                            <Stack spacing={0.5}>
                              <Typography variant="subtitle2" color="text.secondary">نوع الخط</Typography>
                              <TextField select fullWidth size="small" value={formData.fontFamily} onChange={updateField('fontFamily')}>
                                <MenuItem value="Tajawal">Tajawal</MenuItem>
                                <MenuItem value="Cairo">Cairo</MenuItem>
                                <MenuItem value="Noto Naskh Arabic">Noto Naskh Arabic</MenuItem>
                                <MenuItem value="Dubai">Dubai</MenuItem>
                              </TextField>
                            </Stack>
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

                  {/* عمود ألوان الجداول والواجهة */}
                  <Grid size={{ xs: 12, md: 8 }}>
                    <Grid container spacing={2} sx={{ alignItems: 'flex-start' }}>

                      {/* منتقيات الألوان - عمودياً */}
                      <Grid size={{ xs: 12, md: 5 }}>
                        <Paper variant="outlined" sx={{ p: '0.5rem 0.75rem', borderRadius: '0.25rem' }}>
                          <FieldGroup title="ألوان الجداول" icon={PaletteIcon} color="primary.main">
                            <Stack spacing={0.75}>

                              {[
                                { label: 'خلفية الترويسة', key: 'tableHeaderBg' },
                                { label: 'نص الترويسة',    key: 'tableHeaderText' },
                                { label: 'اللون الرئيسي',  key: 'primaryColor' },
                                { label: 'لون التحديد',    key: 'selectionColor' },
                                { label: 'صفوف بديلة',     key: 'tableRowEven' },
                              ].map(({ label, key }) => {
                                const val = formData[key] || '';
                                const hex = toHexColor(val);
                                return (
                                  <Box key={key} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                                    {/* مربع ملوّن يعرض اللون الفعلي (بالشفافية) - يفتح المنتقي عند الضغط */}
                                    <Box sx={{ position: 'relative', width: '1.75rem', height: '1.75rem', flexShrink: 0 }}>
                                      <Box sx={{
                                        width: '100%', height: '100%',
                                        bgcolor: val || '#000',
                                        borderRadius: '4px',
                                        border: '1px solid rgba(0,0,0,0.2)',
                                        cursor: 'pointer',
                                      }} />
                                      <Box
                                        component="input"
                                        type="color"
                                        value={hex}
                                        onChange={(e) => setFormData((p) => ({ ...p, [key]: e.target.value }))}
                                        sx={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%', p: 0, border: 'none' }}
                                      />
                                    </Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ width: '85px', flexShrink: 0, lineHeight: 1.2 }}>{label}</Typography>
                                    <TextField
                                      size="small"
                                      value={val}
                                      onChange={(e) => setFormData((p) => ({ ...p, [key]: e.target.value }))}
                                      sx={{ flex: 1, '& .MuiInputBase-input': { py: '4px', fontSize: '0.72rem' } }}
                                      inputProps={{ maxLength: 40, dir: 'ltr' }}
                                    />
                                  </Box>
                                );
                              })}

                            </Stack>
                          </FieldGroup>
                        </Paper>
                      </Grid>

                      {/* المعاينة المباشرة - عمود بجانب الألوان */}
                      <Grid size={{ xs: 12, md: 7 }}>
                        <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
                          <FieldGroup title="معاينة مباشرة" icon={PaletteIcon} color="primary.main">
                            <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
                              {/* ترويسة الجدول */}
                              <Box
                                sx={{
                                  bgcolor: formData.tableHeaderBg,
                                  color: formData.tableHeaderText,
                                  borderBottom: `2px solid ${formData.tableHeaderText}`,
                                  px: 2, py: 1,
                                  display: 'grid',
                                  gridTemplateColumns: '1fr 1fr 1fr',
                                  gap: 2
                                }}
                              >
                                {['اسم المنتج', 'الرمز', 'الحالة'].map((h) => (
                                  <Typography key={h} variant="caption" fontWeight={700}>{h}</Typography>
                                ))}
                              </Box>
                              {/* صفوف المعاينة */}
                              {[
                                ['خدمة طبية أولى', 'SRV-001', 'نشط'],
                                ['تغطية استشفاء', 'SRV-002', 'نشط'],
                                ['إجراء جراحي', 'SRV-003', 'موقوف']
                              ].map((row, i) => (
                                <Box
                                  key={i}
                                  sx={{
                                    bgcolor: i % 2 === 1 ? formData.tableRowEven : 'transparent',
                                    px: 2, py: '0.5rem',
                                    display: 'grid',
                                    gridTemplateColumns: '1fr 1fr 1fr',
                                    gap: 2,
                                    fontSize: '0.8rem'
                                  }}
                                >
                                  {row.map((cell, j) => <span key={j}>{cell}</span>)}
                                </Box>
                              ))}
                            </Box>
                            <Box sx={{ mt: 1.5, display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
                              <Button variant="contained" size="small" sx={{ bgcolor: formData.primaryColor, '&:hover': { bgcolor: formData.primaryColor, opacity: 0.85 } }}>
                                زر رئيسي
                              </Button>
                              <Button variant="outlined" size="small" sx={{ borderColor: formData.primaryColor, color: formData.primaryColor }}>
                                زر ثانوي
                              </Button>
                              <Typography variant="caption" sx={{ color: formData.primaryColor, fontWeight: 700 }}>
                                ● نموذج أيقونة/نص
                              </Typography>
                            </Box>
                          </FieldGroup>
                        </Paper>
                      </Grid>

                    </Grid>
                  </Grid>

                </Grid>
              </Box>
            </Box>
          </TabPanel>
          {/* ===================== نهاية تبويب المظهر ===================== */}

          <TabPanel value={tabValue} index={7}>
            <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
                <AIKeySettingsPage embedded />
              </Box>
            </Box>
          </TabPanel>

        </Box>
      </Card>
      

    </Box>
  );
};

export default SystemSettingsPage;



