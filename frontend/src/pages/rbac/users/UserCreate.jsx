import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Divider,
  Grid, InputAdornment, MenuItem, Paper, Stack, Step, StepLabel, Stepper, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  ToggleButton, ToggleButtonGroup, Typography
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import SearchIcon from '@mui/icons-material/Search';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import EmployerSelectField from 'components/tba/EmployerSelectField';
import { useTableRefresh } from 'contexts/TableRefreshContext';
import { openSnackbar } from 'api/snackbar';
import axios from 'utils/axios';
import accessControlService from 'services/rbac/access-control.service';
import { SystemRole, RoleDisplayNames } from 'constants/rbac';
import { validatePassword } from 'utils/passwordValidator';

const STEPS = ['الدور الأساسي', 'نطاق الوصول', 'الصلاحيات الفعلية', 'مراجعة وتأكيد'];
const CATEGORIES = {
  MEMBERS: 'المستفيدون', CLAIMS: 'المطالبات', PREAUTHORIZATIONS: 'الموافقات المسبقة',
  PROVIDERS: 'مقدمو الخدمة', EMPLOYERS: 'جهات العمل', CONTRACTS_PRICING: 'العقود والأسعار',
  BENEFITS: 'المنافع', SETTLEMENTS: 'التسويات', REPORTS: 'التقارير',
  USERS_SECURITY: 'المستخدمون والأمن', SYSTEM: 'إعدادات النظام'
};
const ROLE_HELP = {
  DATA_ENTRY: 'إدخال وتحديث البيانات التشغيلية ضمن جهة محددة.',
  EMPLOYER_ADMIN: 'متابعة مستفيدي ومطالبات جهة عمل واحدة.',
  PROVIDER_STAFF: 'بوابة مقدم الخدمة وتقديم المطالبات والموافقات.',
  MEDICAL_REVIEWER: 'مراجعة طبية دون الاعتماد النهائي.',
  MEDICAL_REVIEW_HEAD: 'اعتماد قرارات فريق المراجعة.',
  INSURANCE_MANAGER: 'إشراف قرارات التأمين الحساسة.',
  ACCOUNTANT: 'إدارة التسويات والتقارير المالية.',
  FINANCE_VIEWER: 'قراءة مالية فقط دون تعديل.'
};
const initialForm = { username: '', password: '', confirmPassword: '', fullName: '', email: '', phone: '', userType: '', employerId: '', providerId: '' };
const needsEmployer = (role) => [SystemRole.EMPLOYER_ADMIN, SystemRole.DATA_ENTRY].includes(role);
const needsProvider = (role) => role === SystemRole.PROVIDER_STAFF;

export default function UserCreate() {
  const navigate = useNavigate();
  const { triggerRefresh } = useTableRefresh();
  const [step, setStep] = useState(0);
  const [form, setForm] = useState(initialForm);
  const [errors, setErrors] = useState({});
  const [catalogue, setCatalogue] = useState([]);
  const [roles, setRoles] = useState([]);
  const [providers, setProviders] = useState([]);
  const [overrides, setOverrides] = useState({});
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const [changeReason, setChangeReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    let mounted = true;
    Promise.all([
      accessControlService.getPermissionCatalogue(), accessControlService.getRoleTemplates(),
      axios.get('/providers/selector').then((r) => r.data?.data?.items || r.data?.items || r.data?.data || r.data || [])
    ]).then(([permissions, templates, providerRows]) => {
      if (!mounted) return;
      setCatalogue(permissions || []);
      setRoles((templates || []).filter((item) => item.roleCode !== SystemRole.SUPER_ADMIN));
      setProviders(providerRows || []);
    }).catch((error) => mounted && setLoadError(error?.response?.data?.message || 'تعذر تحميل عقد الصلاحيات؛ تم إيقاف الإنشاء حمايةً من إعداد ناقص.'))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, []);

  const template = roles.find((item) => item.roleCode === form.userType);
  const inherited = useMemo(() => new Set(template?.permissionCodes || []), [template]);
  const effective = useMemo(() => {
    const set = new Set(inherited);
    Object.entries(overrides).forEach(([code, mode]) => mode === 'GRANT' ? set.add(code) : mode === 'REVOKE' && set.delete(code));
    return set;
  }, [inherited, overrides]);
  const visiblePermissions = useMemo(() => catalogue.filter((item) =>
    (category === 'ALL' || item.category === category) &&
    (!query.trim() || `${item.displayNameAr} ${item.code}`.toLowerCase().includes(query.trim().toLowerCase()))
  ), [catalogue, category, query]);

  const change = (field, value) => {
    setForm((old) => ({ ...old, [field]: value, ...(field === 'userType' ? { employerId: '', providerId: '' } : {}) }));
    if (field === 'userType') setOverrides({});
    setErrors((old) => ({ ...old, [field]: undefined }));
  };
  const validate = () => {
    const next = {};
    if (step === 0) {
      if (!form.userType) next.userType = 'اختر الدور الأساسي';
      if (!form.fullName.trim()) next.fullName = 'الاسم مطلوب';
      if (form.username.trim().length < 3) next.username = 'اسم المستخدم 3 أحرف على الأقل';
      if (!/^\S+@\S+\.\S+$/.test(form.email)) next.email = 'البريد غير صالح';
      const password = validatePassword(form.password);
      if (!password.valid) next.password = password.errors.join(' • ');
      if (form.password !== form.confirmPassword) next.confirmPassword = 'كلمتا المرور غير متطابقتين';
    }
    if (step === 1 && needsEmployer(form.userType) && !form.employerId) next.employerId = 'جهة العمل مطلوبة';
    if (step === 1 && needsProvider(form.userType) && !form.providerId) next.providerId = 'مقدم الخدمة مطلوب';
    setErrors(next);
    return Object.keys(next).length === 0;
  };
  const next = () => validate() && setStep((value) => value + 1);

  const submit = async () => {
    if (Object.keys(overrides).length && !changeReason.trim()) {
      setErrors((old) => ({ ...old, changeReason: 'سبب الاستثناءات الشخصية إلزامي' }));
      return;
    }
    const permissionOverrides = Object.entries(overrides).map(([permissionCode, mode]) => ({ permissionCode, mode, reason: changeReason.trim() }));
    const user = { username: form.username.trim(), password: form.password, fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.trim() || null, userType: form.userType, employerId: needsEmployer(form.userType) ? Number(form.employerId) : null, providerId: needsProvider(form.userType) ? Number(form.providerId) : null };
    try {
      setSaving(true);
      await accessControlService.createManagedUser(user, permissionOverrides);
      triggerRefresh();
      openSnackbar({ open: true, message: 'تم إنشاء المستخدم وتطبيق النطاق والصلاحيات', variant: 'alert', alert: { color: 'success' } });
      navigate('/admin/users');
    } catch (error) {
      openSnackbar({ open: true, message: error?.response?.data?.messageAr || error?.response?.data?.message || 'تعذر إنشاء المستخدم', variant: 'alert', alert: { color: 'error' } });
    } finally { setSaving(false); }
  };

  if (loading) return <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>;
  return <Box dir="rtl">
    <ModernPageHeader title="إضافة مستخدم جديد" subtitle="الدور والنطاق والصلاحيات في قرار واحد قابل للمراجعة" breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'المستخدمون', path: '/admin/users' }, { label: 'إضافة' }]} actions={<Button variant="outlined" onClick={() => navigate('/admin/users')}>عودة</Button>} />
    {loadError && <Alert severity="error" sx={{ mb: 2 }}>{loadError}</Alert>}
    <MainCard contentSX={{ p: { xs: 2, md: 3 } }}>
      <Stepper activeStep={step} alternativeLabel sx={{ mb: 4 }}>{STEPS.map((label) => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}</Stepper>

      {step === 0 && <Stack spacing={3}>
        <Box><Typography variant="h3">الدور الأساسي</Typography><Typography color="text.secondary">قالب بداية، وليس حكماً نهائياً على الصلاحيات.</Typography></Box>
        <Grid container spacing={1.5}>{roles.map((role) => <Grid item xs={12} sm={6} md={4} key={role.roleCode}><Card variant="outlined" sx={{ height: '100%', borderColor: form.userType === role.roleCode ? 'primary.main' : 'divider', bgcolor: form.userType === role.roleCode ? 'primary.lighter' : 'background.paper' }}><CardActionArea sx={{ height: '100%' }} onClick={() => change('userType', role.roleCode)}><CardContent><Stack direction="row" justifyContent="space-between"><AdminPanelSettingsOutlinedIcon color="primary" /><Chip size="small" label={`${role.permissionCodes.length} صلاحية`} /></Stack><Typography variant="h5" mt={1}>{RoleDisplayNames[role.roleCode]?.ar || role.displayNameAr}</Typography><Typography variant="body2" color="text.secondary" mt={1}>{ROLE_HELP[role.roleCode]}</Typography></CardContent></CardActionArea></Card></Grid>)}</Grid>
        {errors.userType && <Alert severity="error">{errors.userType}</Alert>}
        <Divider />
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}><TextField fullWidth label="الاسم الكامل" value={form.fullName} onChange={(e) => change('fullName', e.target.value)} error={!!errors.fullName} helperText={errors.fullName} /></Grid>
          <Grid item xs={12} md={6}><TextField fullWidth label="اسم المستخدم" value={form.username} onChange={(e) => change('username', e.target.value)} error={!!errors.username} helperText={errors.username} /></Grid>
          <Grid item xs={12} md={6}><TextField fullWidth label="البريد الإلكتروني" value={form.email} onChange={(e) => change('email', e.target.value)} error={!!errors.email} helperText={errors.email} /></Grid>
          <Grid item xs={12} md={6}><TextField fullWidth label="الهاتف" value={form.phone} onChange={(e) => change('phone', e.target.value)} /></Grid>
          <Grid item xs={12} md={6}><TextField fullWidth type="password" label="كلمة المرور" value={form.password} onChange={(e) => change('password', e.target.value)} error={!!errors.password} helperText={errors.password} /></Grid>
          <Grid item xs={12} md={6}><TextField fullWidth type="password" label="تأكيد كلمة المرور" value={form.confirmPassword} onChange={(e) => change('confirmPassword', e.target.value)} error={!!errors.confirmPassword} helperText={errors.confirmPassword} /></Grid>
        </Grid>
      </Stack>}

      {step === 1 && <Stack spacing={3} sx={{ maxWidth: 850, mx: 'auto' }}><Box textAlign="center"><Typography variant="h3">نطاق الوصول</Typography><Typography color="text.secondary">النطاق يحدد أين يعمل المستخدم، ولا يمنحه عمليات إضافية.</Typography></Box>{needsEmployer(form.userType) && <EmployerSelectField value={form.employerId} onChange={(value) => change('employerId', value?.target ? value.target.value : value)} error={!!errors.employerId} helperText={errors.employerId} required />}{needsProvider(form.userType) && <TextField select fullWidth label="مقدم الخدمة" value={form.providerId} onChange={(e) => change('providerId', e.target.value)} error={!!errors.providerId} helperText={errors.providerId}>{providers.map((provider) => <MenuItem key={provider.id} value={provider.id}>{provider.name}</MenuItem>)}</TextField>}{!needsEmployer(form.userType) && !needsProvider(form.userType) && <Alert severity="info">دور داخلي عام؛ تبقى كل عملية حساسة مقيدة بصلاحيتها وقواعد قسمها.</Alert>}</Stack>}

      {step === 2 && <Stack spacing={2}><Box><Typography variant="h3">الصلاحيات الفعلية</Typography><Typography color="text.secondary">السحب الصريح يتقدم على الدور. استخدم الاستثناء فقط لحاجة موثقة.</Typography></Box><Grid container spacing={1}><Grid item xs={12} md={7}><TextField fullWidth size="small" placeholder="بحث..." value={query} onChange={(e) => setQuery(e.target.value)} InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }} /></Grid><Grid item xs={12} md={5}><TextField select fullWidth size="small" value={category} onChange={(e) => setCategory(e.target.value)}><MenuItem value="ALL">كل التصنيفات</MenuItem>{Object.entries(CATEGORIES).map(([code, label]) => <MenuItem key={code} value={code}>{label}</MenuItem>)}</TextField></Grid></Grid><TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 520 }}><Table stickyHeader size="small"><TableHead><TableRow><TableCell>التصنيف</TableCell><TableCell>الصلاحية</TableCell><TableCell>الحالة</TableCell><TableCell align="center">القرار</TableCell></TableRow></TableHead><TableBody>{visiblePermissions.map((permission) => { const mode = overrides[permission.code] || 'INHERIT'; const enabled = effective.has(permission.code); return <TableRow key={permission.code}><TableCell>{CATEGORIES[permission.category]}</TableCell><TableCell><Typography fontWeight={600}>{permission.displayNameAr} {permission.sensitive && <WarningAmberRoundedIcon color="warning" sx={{ fontSize: 16, verticalAlign: 'middle' }} />}</Typography><Typography variant="caption" color="text.secondary">{permission.code}</Typography></TableCell><TableCell><Chip size="small" color={enabled ? 'success' : 'default'} label={mode === 'GRANT' ? 'منحة شخصية' : mode === 'REVOKE' ? 'مسحوبة' : inherited.has(permission.code) ? 'موروثة' : 'غير موروثة'} /></TableCell><TableCell align="center"><ToggleButtonGroup exclusive size="small" value={mode} onChange={(_, value) => value && setOverrides((old) => ({ ...old, [permission.code]: value }))}><ToggleButton value="INHERIT">من الدور</ToggleButton><ToggleButton value="GRANT" color="success">منح</ToggleButton><ToggleButton value="REVOKE" color="error">سحب</ToggleButton></ToggleButtonGroup></TableCell></TableRow>; })}</TableBody></Table></TableContainer></Stack>}

      {step === 3 && <Grid container spacing={2}><Grid item xs={12} md={8}><Stack spacing={2}><Alert severity="warning" icon={<SecurityOutlinedIcon />}>أي تعديل أمني لاحق يسحب جلسات المستخدم فوراً بعد نجاح الحفظ.</Alert><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h4">ملخص الحساب</Typography><Grid container spacing={2} mt={0.5}><Grid item xs={6}>الاسم: <b>{form.fullName}</b></Grid><Grid item xs={6}>المستخدم: <b>{form.username}</b></Grid><Grid item xs={6}>الدور: <b>{RoleDisplayNames[form.userType]?.ar}</b></Grid><Grid item xs={6}>النطاق: <b>{needsEmployer(form.userType) ? `جهة #${form.employerId}` : needsProvider(form.userType) ? `مقدم #${form.providerId}` : 'داخلي عام'}</b></Grid></Grid></Paper><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h4" mb={1}>الاختلاف عن قالب الدور</Typography>{Object.keys(overrides).length ? <Stack spacing={2}><Stack direction="row" gap={1} flexWrap="wrap">{Object.entries(overrides).map(([code, mode]) => <Chip key={code} color={mode === 'GRANT' ? 'success' : 'error'} label={`${catalogue.find((p) => p.code === code)?.displayNameAr}: ${mode === 'GRANT' ? 'منح' : 'سحب'}`} />)}</Stack><TextField fullWidth required multiline minRows={2} label="سبب الاستثناءات الشخصية" value={changeReason} onChange={(event) => { setChangeReason(event.target.value); setErrors((old) => ({ ...old, changeReason: undefined })); }} error={!!errors.changeReason} helperText={errors.changeReason || 'سيظهر في سجل التدقيق.'} /></Stack> : <Typography color="text.secondary">لا توجد استثناءات شخصية.</Typography>}</Paper></Stack></Grid><Grid item xs={12} md={4}><Card sx={{ bgcolor: 'success.lighter' }}><CardContent><CheckCircleOutlineIcon color="success" /><Typography variant="h1">{effective.size}</Typography><Typography>صلاحية فعّالة</Typography></CardContent></Card></Grid></Grid>}

      <Divider sx={{ my: 3 }} /><Stack direction="row" justifyContent="space-between"><Button variant="outlined" disabled={!step || saving} onClick={() => setStep((value) => value - 1)} startIcon={<ArrowBackIcon />}>السابق</Button>{step < 3 ? <Button variant="contained" disabled={!!loadError} onClick={next} endIcon={<ArrowForwardIcon />}>التالي</Button> : <Button variant="contained" color="success" disabled={saving || !!loadError} onClick={submit} startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <CheckCircleOutlineIcon />}>مراجعة وحفظ</Button>}</Stack>
    </MainCard>
  </Box>;
}
