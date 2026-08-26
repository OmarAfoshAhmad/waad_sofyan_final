import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress,
  Grid, InputAdornment, MenuItem, Paper, Stack, Switch, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import SearchIcon from '@mui/icons-material/Search';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { openSnackbar } from 'api/snackbar';
import accessControlService from 'services/rbac/access-control.service';
import { RoleDisplayNames, SystemRole } from 'constants/rbac';

const CATEGORIES = {
  MEMBERS: 'المستفيدون', CLAIMS: 'المطالبات', PREAUTHORIZATIONS: 'الموافقات المسبقة',
  PROVIDERS: 'مقدمو الخدمة', EMPLOYERS: 'جهات العمل', CONTRACTS_PRICING: 'العقود والأسعار',
  BENEFITS: 'المنافع', SETTLEMENTS: 'التسويات', REPORTS: 'التقارير',
  USERS_SECURITY: 'المستخدمون والأمن', SYSTEM: 'إعدادات النظام'
};

export default function RolePermissions() {
  const navigate = useNavigate();
  const [catalogue, setCatalogue] = useState([]);
  const [roles, setRoles] = useState([]);
  const [selectedRole, setSelectedRole] = useState('');
  const [selected, setSelected] = useState(new Set());
  const [baseline, setBaseline] = useState(new Set());
  const [reason, setReason] = useState('');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([accessControlService.getPermissionCatalogue(), accessControlService.getRoleTemplates()])
      .then(([permissions, templates]) => {
        setCatalogue(permissions || []);
        setRoles((templates || []).filter((role) => role.roleCode !== SystemRole.SUPER_ADMIN));
      })
      .catch((err) => setError(err?.response?.data?.message || 'تعذر تحميل عقد الأدوار والصلاحيات.'))
      .finally(() => setLoading(false));
  }, []);

  const chooseRole = (role) => {
    const values = new Set(role.permissionCodes || []);
    setSelectedRole(role.roleCode);
    setSelected(values);
    setBaseline(new Set(values));
    setReason('');
  };
  const changed = useMemo(() => selected.size !== baseline.size || [...selected].some((code) => !baseline.has(code)), [selected, baseline]);
  const visible = useMemo(() => catalogue.filter((permission) =>
    (category === 'ALL' || permission.category === category) &&
    (!query.trim() || `${permission.displayNameAr} ${permission.code}`.toLowerCase().includes(query.trim().toLowerCase()))
  ), [catalogue, category, query]);

  const toggle = (code) => setSelected((current) => {
    const next = new Set(current);
    next.has(code) ? next.delete(code) : next.add(code);
    return next;
  });

  const save = async () => {
    if (!changed || !reason.trim()) return;
    try {
      setSaving(true);
      const updated = await accessControlService.updateRoleTemplate(selectedRole, [...selected], reason.trim());
      setRoles((current) => current.map((role) => role.roleCode === selectedRole ? updated : role));
      setBaseline(new Set(updated.permissionCodes || []));
      setReason('');
      openSnackbar({ open: true, message: 'تم تحديث قالب الدور وسحب جلسات المستخدمين المتأثرين', variant: 'alert', alert: { color: 'success' } });
    } catch (err) {
      openSnackbar({ open: true, message: err?.response?.data?.messageAr || err?.response?.data?.message || 'تعذر تحديث الدور', variant: 'alert', alert: { color: 'error' } });
    } finally { setSaving(false); }
  };

  if (loading) return <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>;
  return <Box dir="rtl">
    <ModernPageHeader title="الأدوار والصلاحيات" subtitle="تعديل القوالب المركزية مع أثر فوري وسجل تدقيق دائم" actions={<Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/admin/users')}>عودة</Button>} />
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    <Grid container spacing={2}>
      <Grid item xs={12} md={3}>
        <MainCard title="قوالب الأدوار">
          <Stack spacing={1}>{roles.map((role) => <Card key={role.roleCode} variant="outlined" sx={{ borderColor: selectedRole === role.roleCode ? 'primary.main' : 'divider' }}><CardActionArea onClick={() => chooseRole(role)}><CardContent><Typography fontWeight={700}>{RoleDisplayNames[role.roleCode]?.ar || role.displayNameAr}</Typography><Typography variant="caption" color="text.secondary">{role.permissionCodes.length} صلاحية</Typography></CardContent></CardActionArea></Card>)}</Stack>
        </MainCard>
      </Grid>
      <Grid item xs={12} md={9}>
        <MainCard title="الصلاحيات الموروثة من الدور">
          {!selectedRole ? <Alert severity="info">اختر دورًا لعرض صلاحياته.</Alert> : <Stack spacing={2}>
            <Alert severity="warning">التغيير يؤثر على جميع مستخدمي الدور ويلغي جلساتهم بعد نجاح الحفظ.</Alert>
            <Grid container spacing={1}><Grid item xs={12} md={7}><TextField fullWidth size="small" placeholder="ابحث باسم الصلاحية أو رمزها" value={query} onChange={(event) => setQuery(event.target.value)} InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }} /></Grid><Grid item xs={12} md={5}><TextField select fullWidth size="small" value={category} onChange={(event) => setCategory(event.target.value)}><MenuItem value="ALL">كل الأقسام</MenuItem>{Object.entries(CATEGORIES).map(([code, label]) => <MenuItem key={code} value={code}>{label}</MenuItem>)}</TextField></Grid></Grid>
            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 500 }}><Table stickyHeader size="small"><TableHead><TableRow><TableCell>القسم</TableCell><TableCell>الصلاحية</TableCell><TableCell align="center">ضمن الدور</TableCell></TableRow></TableHead><TableBody>{visible.map((permission) => <TableRow key={permission.code}><TableCell>{CATEGORIES[permission.category]}</TableCell><TableCell><Typography fontWeight={600}>{permission.displayNameAr} {permission.sensitive && <WarningAmberRoundedIcon color="warning" sx={{ fontSize: 16, verticalAlign: 'middle' }} />}</Typography><Typography variant="caption" color="text.secondary">{permission.code}</Typography></TableCell><TableCell align="center"><Switch checked={selected.has(permission.code)} onChange={() => toggle(permission.code)} /></TableCell></TableRow>)}</TableBody></Table></TableContainer>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="flex-start"><TextField fullWidth required label="سبب تعديل قالب الدور" value={reason} onChange={(event) => setReason(event.target.value)} helperText="يُحفظ في سجل التدقيق ولا يمكن تعديله أو حذفه." /><Chip color={changed ? 'warning' : 'success'} label={changed ? 'تغييرات غير محفوظة' : 'القالب محفوظ'} /><Button variant="contained" startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <SaveOutlinedIcon />} disabled={!changed || !reason.trim() || saving} onClick={save}>حفظ</Button></Stack>
          </Stack>}
        </MainCard>
      </Grid>
    </Grid>
  </Box>;
}
