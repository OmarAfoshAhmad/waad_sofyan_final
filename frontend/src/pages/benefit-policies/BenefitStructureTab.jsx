import { useState } from 'react';
import { Alert, Box, Button, Chip, Grid, IconButton, MenuItem, Stack, TextField, Tooltip, Typography,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper,
  Dialog, DialogTitle, DialogContent, DialogActions, FormControlLabel, Switch } from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import LinkIcon from '@mui/icons-material/Link';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import MainCard from 'components/MainCard';
import {
  createBenefitGroup, updateBenefitGroup, createLimitBucket, deleteBenefitGroup, deleteLimitBucket, deleteRuleBucketLink,
  getBenefitStructure, importBenefitStructure, linkRuleToBucket, downloadBenefitStructureTemplate
} from 'services/api/benefit-structure.service';
import { getPolicyRules } from 'services/api/benefit-policy-rules.service';

const contexts = [
  ['OUTPATIENT', 'عيادات خارجية'],
  ['INPATIENT', 'إيواء'],
  ['OPERATING_ROOM', 'غرفة عمليات'],
  ['EMERGENCY', 'طوارئ وإسعاف'],
  ['SPECIAL', 'منفعة خاصة'],
  ['ANY', 'عام']
];

const periods = [
  ['PER_SERVICE', 'لكل خدمة'], ['PER_VISIT', 'لكل زيارة'], ['DAILY', 'يومي'], ['MONTHLY', 'شهري'],
  ['ANNUAL', 'سنوي'], ['MULTI_YEAR_POLICY', 'كل عدة سنوات من بداية الوثيقة'],
  ['POLICY_PERIOD', 'مدة الوثيقة'], ['LIFETIME', 'مدى الحياة']
];

const contextLabels = Object.fromEntries(contexts);
const periodLabels = Object.fromEntries(periods);
const aggregationLabels = {
  INDIVIDUAL: 'سقف مستقل',
  SHARED: 'سقف مشترك',
  HIERARCHICAL: 'سقف عام وفرعي'
};
const consumptionLabels = { PRIMARY: 'أساسي', SECONDARY: 'ثانوي', PARALLEL: 'متوازٍ' };

const apiError = (error, fallback) => error?.response?.data?.messageAr
  || error?.response?.data?.message
  || error?.response?.data?.error
  || error?.message
  || fallback;

export default function BenefitStructureTab({ policyId, policyStatus }) {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const [group, setGroup] = useState({
    code: '', nameAr: '', contextType: 'OUTPATIENT', aggregationMode: 'SHARED',
    amountLimit: '', timesLimit: '', daysLimit: '', periodType: 'POLICY_PERIOD',
    countingMethod: 'EACH_UNIT', ruleIds: [], active: true
  });
  const [bucket, setBucket] = useState({
    benefitGroupId: '', code: '', nameAr: '', contextType: 'OUTPATIENT', amountLimit: '', timesLimit: '', daysLimit: '',
    periodType: 'POLICY_PERIOD', periodValue: 1, countingMethod: 'EACH_LINE', consumptionBasis: 'COMPANY_SHARE', shared: false
  });
  const [link, setLink] = useState({ ruleId: '', bucketId: '' });
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [editingGroupId, setEditingGroupId] = useState(null);
  const [groupSearch, setGroupSearch] = useState('');
  const canEdit = policyStatus === 'DRAFT';
  const canImport = true;
  const { data = { groups: [], buckets: [], links: [] }, isLoading, error } = useQuery({
    queryKey: ['benefit-structure', policyId], queryFn: () => getBenefitStructure(policyId), enabled: !!policyId
  });
  const { data: rules = [] } = useQuery({
    queryKey: ['benefit-policy-rules', policyId], queryFn: () => getPolicyRules(policyId), enabled: !!policyId
  });
  // هذا التبويب مخصص للمجموعات المشتركة؛ التصنيفات المستقلة تُدار من تبويب قواعد التغطية.
  const groupedBenefits = data.groups.filter((item) => item.aggregationMode !== 'INDIVIDUAL');
  const visibleGroups = groupedBenefits.filter((item) => {
    const query = groupSearch.trim().toLowerCase();
    if (!query) return true;
    const bucketIds = new Set(data.buckets.filter((bucket) => bucket.benefitGroupId === item.id).map((bucket) => bucket.id));
    const memberNames = data.links.filter((link) => bucketIds.has(link.bucket?.id)).map((link) => {
      const rule = rules.find((candidate) => Number(candidate.id) === Number(link.ruleId));
      return rule?.medicalCategoryName || rule?.medicalServiceName || '';
    }).join(' ');
    return `${item.code || ''} ${item.nameAr || ''} ${memberNames}`.toLowerCase().includes(query);
  });
  const resetGroupForm = () => {
    setEditingGroupId(null);
    setGroup({
      code: '', nameAr: '', contextType: 'OUTPATIENT', aggregationMode: 'SHARED',
      amountLimit: '', timesLimit: '', daysLimit: '', periodType: 'POLICY_PERIOD',
      countingMethod: 'EACH_UNIT', ruleIds: [], active: true
    });
  };
  const openCreateGroup = () => { resetGroupForm(); setGroupModalOpen(true); };
  const openEditGroup = (item) => {
    const buckets = data.buckets.filter((bucketItem) => bucketItem.benefitGroupId === item.id);
    const bucketIds = new Set(buckets.map((bucketItem) => bucketItem.id));
    const memberIds = data.links.filter((linked) => bucketIds.has(linked.bucket?.id)).map((linked) => linked.ruleId);
    const primary = buckets[0];
    setEditingGroupId(item.id);
    setGroup({
      code: item.code, nameAr: item.nameAr, contextType: item.contextType, aggregationMode: item.aggregationMode || 'SHARED',
      amountLimit: primary?.amountLimit ?? '', timesLimit: primary?.timesLimit ?? '', daysLimit: primary?.daysLimit ?? '',
      periodType: primary?.periodType || 'POLICY_PERIOD', countingMethod: primary?.countingMethod || 'EACH_UNIT',
      ruleIds: memberIds, active: item.active !== false
    });
    setGroupModalOpen(true);
  };
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['benefit-structure', policyId] });
  const groupMutation = useMutation({
    mutationFn: (payload) => editingGroupId
      ? updateBenefitGroup(policyId, editingGroupId, payload)
      : createBenefitGroup(policyId, payload),
    onSuccess: () => {
      enqueueSnackbar(editingGroupId ? 'تم تحديث مجموعة المنافع بأمان' : 'تم إنشاء مجموعة المنافع', { variant: 'success' });
      setGroupModalOpen(false);
      resetGroupForm();
      refresh();
      queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId] });
    },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر إنشاء المجموعة'), { variant: 'error' })
  });
  const bucketMutation = useMutation({
    mutationFn: (payload) => createLimitBucket(policyId, payload),
    onSuccess: () => { enqueueSnackbar('تم إنشاء وعاء السقف', { variant: 'success' }); refresh(); },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر إنشاء الوعاء'), { variant: 'error' })
  });
  const linkMutation = useMutation({
    mutationFn: () => linkRuleToBucket(policyId, Number(link.ruleId), {
      bucketId: Number(link.bucketId), consumptionOrder: 1, consumptionMode: 'PRIMARY', mandatory: true
    }),
    onSuccess: () => { enqueueSnackbar('تم ربط قاعدة التغطية بالوعاء', { variant: 'success' }); refresh(); },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر إنشاء الربط'), { variant: 'error' })
  });
  const deleteGroupMutation = useMutation({
    mutationFn: (groupId) => deleteBenefitGroup(policyId, groupId),
    onSuccess: () => { enqueueSnackbar('تم حذف المجموعة', { variant: 'success' }); refresh(); },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر حذف المجموعة'), { variant: 'error' })
  });
  const deleteBucketMutation = useMutation({
    mutationFn: (bucketId) => deleteLimitBucket(policyId, bucketId),
    onSuccess: () => { enqueueSnackbar('تم حذف الوعاء', { variant: 'success' }); refresh(); },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر حذف الوعاء'), { variant: 'error' })
  });
  const deleteLinkMutation = useMutation({
    mutationFn: (linkId) => deleteRuleBucketLink(policyId, linkId),
    onSuccess: () => { enqueueSnackbar('تم فك الربط', { variant: 'success' }); refresh(); },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر فك الربط'), { variant: 'error' })
  });
  const importMutation = useMutation({
    mutationFn: (dryRun) => importBenefitStructure(policyId, importFile, dryRun, 'MERGE'),
    onSuccess: (result, dryRun) => {
      setImportResult(result);
      if (!dryRun) {
        refresh();
        queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId] });
      }
    },
    onError: (err) => enqueueSnackbar(apiError(err, 'تعذر استيراد ملف التغطية'), { variant: 'error' })
  });

  if (error) {
    const message = error?.response?.data?.messageAr || error?.response?.data?.message || error?.message;
    return <Alert severity="error">
      <Typography component="div" fontWeight={700}>تعذر تحميل مجموعات المنافع وأوعية السقوف.</Typography>
      <Typography component="div" variant="body2">{message || 'لم يرسل الخادم تفاصيل إضافية.'}</Typography>
    </Alert>;
  }
  return (
    <Stack spacing={2}>
      <Box>
        <Dialog open={groupModalOpen} onClose={() => !groupMutation.isPending && setGroupModalOpen(false)} fullWidth maxWidth="lg" dir="rtl">
          <DialogTitle>{editingGroupId ? 'تعديل مجموعة المنافع' : 'مجموعة منافع جديدة'}</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth disabled={!!editingGroupId} label="كود المجموعة (اختياري)" placeholder="يُولّد تلقائيًا" value={group.code} onChange={(e) => setGroup({ ...group, code: e.target.value })} /></Grid>
                <Grid size={{ xs: 12, md: 5 }}><TextField fullWidth label="اسم المجموعة" value={group.nameAr} onChange={(e) => setGroup({ ...group, nameAr: e.target.value })} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth select label="نطاق التطبيق" value={group.contextType} onChange={(e) => setGroup({ ...group, contextType: e.target.value, ruleIds: [] })}>
                  {contexts.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField></Grid>
                <Grid size={12}><Alert severity={group.ruleIds.length < 2 ? 'warning' : 'info'}>
                  اختر منفعتين على الأقل. تغيير نطاق التطبيق يمسح الاختيارات السابقة لمنع ربط منافع غير متوافقة.
                </Alert></Grid>
                <Grid size={12}><TextField fullWidth select SelectProps={{
                    multiple: true,
                    renderValue: (selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, py: 0.25 }}>
                        {selected.map((id) => {
                          const selectedRule = rules.find((r) => Number(r.id) === Number(id));
                          const name = selectedRule?.medicalCategoryName || selectedRule?.medicalServiceName || `قاعدة ${id}`;
                          return <Chip key={id} size="small" color="primary" variant="outlined" label={name} />;
                        })}
                      </Box>
                    )
                  }} label="المنافع/قواعد التغطية داخل المجموعة"
                  value={group.ruleIds} onChange={(e) => setGroup({ ...group, ruleIds: e.target.value })}>
                  {rules.filter((r) => group.contextType === 'ANY' || r.encounterType === group.contextType).map((r) => (
                    <MenuItem key={r.id} value={r.id}>{r.medicalCategoryName || r.medicalServiceName || `قاعدة ${r.id}`}</MenuItem>
                  ))}
                </TextField></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="سقف المنفعة المالي" value={group.amountLimit} onChange={(e) => setGroup({ ...group, amountLimit: e.target.value })} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="سقف المرات" value={group.timesLimit} onChange={(e) => setGroup({ ...group, timesLimit: e.target.value })} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth select label="المدة الزمنية للمنفعة" value={group.periodType} onChange={(e) => setGroup({ ...group, periodType: e.target.value })}>
                  {periods.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField></Grid>
                <Grid size={12}><FormControlLabel control={<Switch checked={group.active !== false} onChange={(e) => setGroup({ ...group, active: e.target.checked })} />} label="المجموعة نشطة" /></Grid>
              </Grid>
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
              <Button color="inherit" disabled={groupMutation.isPending} onClick={() => setGroupModalOpen(false)}>إلغاء</Button>
              <Button variant="contained" disabled={!canEdit || !group.nameAr || group.ruleIds.length < 2 || groupMutation.isPending}
                onClick={() => groupMutation.mutate({
                  ...group, active: true,
                  amountLimit: group.amountLimit === '' ? null : Number(group.amountLimit),
                  timesLimit: group.timesLimit === '' ? null : Number(group.timesLimit),
                  daysLimit: null,
                  ruleIds: group.ruleIds.map(Number)
                })}>حفظ المجموعة وإنشاء قاعدتها</Button>
          </DialogActions>
        </Dialog>
      </Box>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }} sx={{ display: 'none' }}>
          <MainCard title="وعاء سقف جديد">
            <Stack spacing={2}>
              <TextField select label="مجموعة المنفعة" value={bucket.benefitGroupId}
                onChange={(e) => setBucket({ ...bucket, benefitGroupId: e.target.value })}>
                {data.groups.map((g) => <MenuItem key={g.id} value={g.id}>{g.nameAr}</MenuItem>)}
              </TextField>
              <TextField label="الكود" value={bucket.code} onChange={(e) => setBucket({ ...bucket, code: e.target.value })} />
              <TextField label="اسم الوعاء" value={bucket.nameAr} onChange={(e) => setBucket({ ...bucket, nameAr: e.target.value })} />
              <Grid container spacing={1}>
                <Grid size={4}><TextField fullWidth type="number" label="السقف المالي" value={bucket.amountLimit} onChange={(e) => setBucket({ ...bucket, amountLimit: e.target.value })} /></Grid>
                <Grid size={4}><TextField fullWidth type="number" label="عدد المرات" value={bucket.timesLimit} onChange={(e) => setBucket({ ...bucket, timesLimit: e.target.value })} /></Grid>
                <Grid size={4}><TextField fullWidth type="number" label="عدد الأيام" value={bucket.daysLimit} onChange={(e) => setBucket({ ...bucket, daysLimit: e.target.value })} /></Grid>
              </Grid>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, sm: 8 }}><TextField fullWidth select label="دورية السقف" value={bucket.periodType}
                  onChange={(e) => setBucket({ ...bucket, periodType: e.target.value })}>
                  {periods.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
                </TextField></Grid>
                <Grid size={{ xs: 12, sm: 4 }}><TextField fullWidth type="number" label="عدد السنوات" value={bucket.periodValue}
                  disabled={bucket.periodType !== 'MULTI_YEAR_POLICY'} inputProps={{ min: 2 }}
                  onChange={(e) => setBucket({ ...bucket, periodValue: Number(e.target.value) })} /></Grid>
              </Grid>
              <Button variant="contained" disabled={!canEdit || !bucket.benefitGroupId || !bucket.code || !bucket.nameAr || bucketMutation.isPending}
                onClick={() => bucketMutation.mutate({ ...bucket, benefitGroupId: Number(bucket.benefitGroupId),
                  amountLimit: bucket.amountLimit === '' ? null : Number(bucket.amountLimit), timesLimit: bucket.timesLimit === '' ? null : Number(bucket.timesLimit),
                  daysLimit: bucket.daysLimit === '' ? null : Number(bucket.daysLimit), periodValue: bucket.periodType === 'MULTI_YEAR_POLICY' ? Number(bucket.periodValue) : 1,
                  active: true })}>حفظ الوعاء</Button>
            </Stack>
          </MainCard>
        </Grid>
      </Grid>
      <MainCard title="ربط قاعدة التغطية بالسقف" sx={{ display: 'none' }}>
        <Grid container spacing={2} alignItems="center">
          <Grid size={{ xs: 12, md: 5 }}><TextField fullWidth select label="قاعدة التصنيف والسياق" value={link.ruleId}
            onChange={(e) => setLink({ ...link, ruleId: e.target.value })}>
            {rules.map((r) => <MenuItem key={r.id} value={r.id}>{r.medicalCategoryName} — {r.encounterType}</MenuItem>)}
          </TextField></Grid>
          <Grid size={{ xs: 12, md: 5 }}><TextField fullWidth select label="وعاء السقف" value={link.bucketId}
            onChange={(e) => setLink({ ...link, bucketId: e.target.value })}>
            {data.buckets.map((b) => <MenuItem key={b.id} value={b.id}>{b.nameAr}</MenuItem>)}
          </TextField></Grid>
          <Grid size={{ xs: 12, md: 2 }}><Button fullWidth variant="contained" startIcon={<LinkIcon />}
            disabled={!canEdit || !link.ruleId || !link.bucketId || linkMutation.isPending}
            onClick={() => linkMutation.mutate()}>إنشاء الربط</Button></Grid>
        </Grid>
      </MainCard>
      <MainCard title="خريطة ربط قواعد التغطية بالأوعية" sx={{ display: 'none' }}>
        <Stack spacing={1.5}>
          {data.links.length === 0 && <Alert severity="info">لا توجد روابط بعد. اختر قاعدة ووعاء من القسم السابق ثم اضغط «إنشاء الربط».</Alert>}
          {data.links.map((item) => {
            const rule = rules.find((candidate) => candidate.id === item.ruleId);
            const groupName = data.groups.find((candidate) => candidate.id === item.bucket.benefitGroupId)?.nameAr;
            return (
              <Box key={item.id} sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 2, bgcolor: 'background.paper' }}>
                <Grid container spacing={1.5} alignItems="center">
                  <Grid size={{ xs: 12, md: 5 }}>
                    <Box sx={{ p: 1.5, borderRadius: 1.5, bgcolor: 'primary.lighter', border: '1px solid', borderColor: 'primary.light' }}>
                      <Typography variant="caption" color="text.secondary">قاعدة التغطية</Typography>
                      <Typography fontWeight={700}>{rule?.medicalCategoryName || `قاعدة رقم ${item.ruleId}`}</Typography>
                      <Chip size="small" sx={{ mt: 0.75 }} label={contextLabels[rule?.encounterType] || rule?.encounterType || 'السياق غير محدد'} />
                    </Box>
                  </Grid>
                  <Grid size={{ xs: 12, md: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', color: 'success.main', px: { xs: 4, md: 0 } }}>
                      <Box sx={{ height: 3, flex: 1, bgcolor: 'success.main', borderRadius: 2 }} />
                      <LinkIcon sx={{ mx: 0.75, fontSize: 30 }} />
                      <Box sx={{ height: 3, flex: 1, bgcolor: 'success.main', borderRadius: 2 }} />
                    </Box>
                    <Typography variant="caption" color="success.dark" sx={{ display: 'block', textAlign: 'center', mt: 0.25 }}>
                      {consumptionLabels[item.consumptionMode] || item.consumptionMode}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 5 }}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Box sx={{ p: 1.5, flex: 1, borderRadius: 1.5, bgcolor: 'success.lighter', border: '1px solid', borderColor: 'success.light' }}>
                        <Typography variant="caption" color="text.secondary">وعاء السقف{groupName ? ` — ${groupName}` : ''}</Typography>
                        <Typography fontWeight={700}>{item.bucket.nameAr}</Typography>
                        <Typography variant="body2">السقف: {item.bucket.amountLimit ?? 'غير محدود'} | المرات: {item.bucket.timesLimit ?? 'غير محدودة'}</Typography>
                      </Box>
                      <Tooltip title="فك هذا الربط">
                        <IconButton color="warning" disabled={!canEdit || deleteLinkMutation.isPending}
                          onClick={() => window.confirm('هل تريد فك ارتباط قاعدة التغطية بهذا الوعاء؟') && deleteLinkMutation.mutate(item.id)}>
                          <LinkOffIcon />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </Grid>
                </Grid>
              </Box>
            );
          })}
        </Stack>
      </MainCard>
      <MainCard>
        <Stack spacing={1.5}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'nowrap', overflowX: 'auto', pb: 0.5 }}>
            <Typography variant="h5" sx={{ whiteSpace: 'nowrap', ml: 'auto' }}>مجموعات المنافع الحالية</Typography>
            <TextField size="small" placeholder="بحث في المجموعات" value={groupSearch} onChange={(e) => setGroupSearch(e.target.value)} sx={{ minWidth: 210 }} />
            <Button variant="contained" sx={{ whiteSpace: 'nowrap' }} disabled={!canEdit} onClick={openCreateGroup}>إنشاء مجموعة جديدة</Button>
            <Button variant="outlined" color="secondary" sx={{ whiteSpace: 'nowrap' }} onClick={() => downloadBenefitStructureTemplate(policyId)}>تنزيل القالب</Button>
            <Button component="label" variant="outlined" sx={{ whiteSpace: 'nowrap' }} disabled={!canImport || importMutation.isPending}>
              {importFile?.name ? 'تغيير الملف' : 'اختيار ملف Excel'}
              <input hidden type="file" accept=".xlsx" onChange={(e) => { setImportFile(e.target.files?.[0] || null); setImportResult(null); }} />
            </Button>
            <Button variant="contained" sx={{ whiteSpace: 'nowrap' }} disabled={!canImport || !importFile || importMutation.isPending}
              onClick={() => importMutation.mutate(true)}>فحص الملف</Button>
            <Button color="success" variant="contained" sx={{ whiteSpace: 'nowrap' }}
              disabled={!canImport || !importFile || importMutation.isPending || !importResult?.dryRun || (importResult?.errors?.length ?? 1) > 0}
              onClick={() => importMutation.mutate(false)}>اعتماد الاستيراد</Button>
          </Stack>
          {importMutation.isError && <Alert severity="error">{apiError(importMutation.error, 'تعذر فحص ملف الاستيراد.')}</Alert>}
          {importResult && <Alert severity={(importResult.errors?.length || 0) > 0 ? 'error' : 'success'}>
            فحص الدمج الآمن: سيُنشأ {importResult.created}، وسيُحدّث {importResult.updated}، ولن يُعطّل أي إعداد غير موجود في الملف.
            {(importResult.errors || []).map((item) => <Typography component="div" key={item}>• {item}</Typography>)}
            {(importResult.warnings || []).map((item) => <Typography component="div" key={item}>• تنبيه: {item}</Typography>)}
          </Alert>}
        {isLoading ? <Typography>جارٍ التحميل...</Typography> : visibleGroups.length === 0 ? <Alert severity="info">لا توجد مجموعات مطابقة.</Alert> : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>الكود</TableCell><TableCell>اسم المجموعة</TableCell><TableCell>السياق</TableCell>
                  <TableCell sx={{ minWidth: 320 }}>المنافع ضمن المجموعة</TableCell>
                  <TableCell>السقف المالي</TableCell><TableCell>سقف المرات</TableCell>
                  <TableCell>المدة الزمنية</TableCell><TableCell>الحالة</TableCell><TableCell align="center">إجراء</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleGroups.map((g) => {
                  const groupBuckets = data.buckets.filter((b) => b.benefitGroupId === g.id);
                  const groupLinks = data.links.filter((l) => groupBuckets.some((b) => b.id === l.bucket.id));
                  const groupRules = groupLinks.map((l) => rules.find((r) => Number(r.id) === Number(l.ruleId))).filter(Boolean);
                  const primaryBucket = groupBuckets[0];
                  return <TableRow key={g.id} hover>
                    <TableCell><Chip size="small" label={g.code} variant="outlined" /></TableCell>
                    <TableCell><Typography fontWeight={700}>{g.nameAr}</Typography></TableCell>
                    <TableCell>{contextLabels[g.contextType] || g.contextType}</TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {groupRules.length ? groupRules.map((r) => <Chip key={r.id} size="small" color="primary" variant="outlined"
                          label={r.medicalCategoryName || r.medicalServiceName || `قاعدة ${r.id}`} />) : <Typography variant="caption" color="text.secondary">لا توجد منافع مرتبطة</Typography>}
                      </Box>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{primaryBucket?.amountLimit != null ? `${primaryBucket.amountLimit} د.ل` : 'بلا سقف'}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{primaryBucket?.timesLimit != null ? `${primaryBucket.timesLimit} مرة` : 'بلا سقف'}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{primaryBucket ? periodLabels[primaryBucket.periodType] || primaryBucket.periodType : '—'}</TableCell>
                    <TableCell><Chip size="small" color={g.active ? 'success' : 'default'} label={g.active ? 'نشطة' : 'متوقفة'} /></TableCell>
                    <TableCell align="center"><Tooltip title="تعديل المجموعة"><IconButton size="small" color="primary" disabled={!canEdit}
                      onClick={() => openEditGroup(g)}><EditOutlinedIcon /></IconButton></Tooltip><Tooltip title="حذف المجموعة"><IconButton size="small" color="error"
                      disabled={!canEdit || deleteGroupMutation.isPending}
                      onClick={() => window.confirm(`هل تريد حذف مجموعة «${g.nameAr}»؟`) && deleteGroupMutation.mutate(g.id)}><DeleteOutlineIcon /></IconButton></Tooltip></TableCell>
                  </TableRow>;
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        </Stack>
      </MainCard>
    </Stack>
  );
}
