import { useState } from 'react';
import { Alert, Box, Button, Chip, Grid, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import LinkIcon from '@mui/icons-material/Link';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import MainCard from 'components/MainCard';
import {
  createBenefitGroup, createLimitBucket, deleteBenefitGroup, deleteLimitBucket, deleteRuleBucketLink,
  getBenefitStructure, importBenefitStructure, linkRuleToBucket
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
  const [group, setGroup] = useState({ code: '', nameAr: '', contextType: 'OUTPATIENT', aggregationMode: 'INDIVIDUAL' });
  const [bucket, setBucket] = useState({
    benefitGroupId: '', code: '', nameAr: '', contextType: 'OUTPATIENT', amountLimit: '', timesLimit: '', daysLimit: '',
    periodType: 'POLICY_PERIOD', periodValue: 1, countingMethod: 'EACH_LINE', consumptionBasis: 'COMPANY_SHARE', shared: false
  });
  const [link, setLink] = useState({ ruleId: '', bucketId: '' });
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const canEdit = policyStatus === 'DRAFT';
  const canImport = true;
  const { data = { groups: [], buckets: [], links: [] }, isLoading, error } = useQuery({
    queryKey: ['benefit-structure', policyId], queryFn: () => getBenefitStructure(policyId), enabled: !!policyId
  });
  const { data: rules = [] } = useQuery({
    queryKey: ['benefit-policy-rules', policyId], queryFn: () => getPolicyRules(policyId), enabled: !!policyId
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['benefit-structure', policyId] });
  const groupMutation = useMutation({
    mutationFn: (payload) => createBenefitGroup(policyId, payload),
    onSuccess: () => { enqueueSnackbar('تم إنشاء مجموعة المنفعة', { variant: 'success' }); refresh(); },
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
    mutationFn: (dryRun) => importBenefitStructure(policyId, importFile, dryRun),
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
      <MainCard title="استيراد وثيقة التغطية والأوعية">
        <Stack spacing={1.5}>
          <Typography variant="body2">افحص ملف «استيراد_اسم الوثيقة.xlsx» أولًا. لن تُحفظ أي بيانات أثناء الفحص، ولا يتاح الاعتماد إذا وُجدت أخطاء.</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
            <Button component="label" variant="outlined" disabled={!canImport || importMutation.isPending}>
              اختيار ملف Excel
              <input hidden type="file" accept=".xlsx" onChange={(e) => { setImportFile(e.target.files?.[0] || null); setImportResult(null); }} />
            </Button>
            <Typography variant="body2">{importFile?.name || 'لم يُحدّد ملف'}</Typography>
            <Button variant="contained" disabled={!canImport || !importFile || importMutation.isPending}
              onClick={() => importMutation.mutate(true)}>فحص الملف</Button>
            <Button color="success" variant="contained"
              disabled={!canImport || !importFile || importMutation.isPending || !importResult?.dryRun || (importResult?.errors?.length ?? 1) > 0}
              onClick={() => importMutation.mutate(false)}>اعتماد الاستيراد</Button>
          </Stack>
          {importMutation.isError && <Alert severity="error">{apiError(importMutation.error, 'تعذر فحص ملف الاستيراد.')}</Alert>}
          {importResult && <Alert severity={(importResult.errors?.length || 0) > 0 ? 'error' : 'success'}>
            القواعد: {importResult.rules}، المجموعات: {importResult.groups}، الأوعية: {importResult.buckets}، الروابط: {importResult.links}، المنافع الخاصة: {importResult.specialBenefits}.
            {(importResult.errors || []).map((item) => <Typography component="div" key={item}>• {item}</Typography>)}
            {(importResult.warnings || []).map((item) => <Typography component="div" key={item}>• تنبيه: {item}</Typography>)}
          </Alert>}
        </Stack>
      </MainCard>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <MainCard title="مجموعة منفعة جديدة">
            <Stack spacing={2}>
              <TextField label="الكود" value={group.code} onChange={(e) => setGroup({ ...group, code: e.target.value })} />
              <TextField label="اسم المجموعة" value={group.nameAr} onChange={(e) => setGroup({ ...group, nameAr: e.target.value })} />
              <TextField select label="السياق" value={group.contextType} onChange={(e) => setGroup({ ...group, contextType: e.target.value })}>
                {contexts.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
              </TextField>
              <TextField select label="طريقة تجميع السقف" value={group.aggregationMode} onChange={(e) => setGroup({ ...group, aggregationMode: e.target.value })}>
                <MenuItem value="INDIVIDUAL">سقف مستقل</MenuItem><MenuItem value="SHARED">سقف مشترك</MenuItem>
                <MenuItem value="HIERARCHICAL">سقف عام وفرعي</MenuItem>
              </TextField>
              <Button variant="contained" disabled={!canEdit || !group.code || !group.nameAr || groupMutation.isPending}
                onClick={() => groupMutation.mutate({ ...group, active: true })}>حفظ المجموعة</Button>
            </Stack>
          </MainCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
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
      <MainCard title="ربط قاعدة التغطية بالسقف">
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
      <MainCard title="خريطة ربط قواعد التغطية بالأوعية">
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
      <MainCard title="البنية الحالية">
        {isLoading ? <Typography>جارٍ التحميل...</Typography> : data.groups.length === 0 ? <Alert severity="info">لا توجد مجموعات منافع.</Alert> : data.groups.map((g) => (
          <Box key={g.id} sx={{ py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              <Typography fontWeight={700}>{g.nameAr}</Typography>
              <Chip size="small" color="primary" label={aggregationLabels[g.aggregationMode] || g.aggregationMode} />
              <Chip size="small" variant="outlined" label={contextLabels[g.contextType] || g.contextType} />
              <Box sx={{ flexGrow: 1 }} />
              <Tooltip title="حذف المجموعة">
                <IconButton size="small" color="error" disabled={!canEdit || deleteGroupMutation.isPending}
                  onClick={() => window.confirm(`هل تريد حذف مجموعة «${g.nameAr}»؟ يجب حذف أوعيتها أولًا.`) && deleteGroupMutation.mutate(g.id)}>
                  <DeleteOutlineIcon />
                </IconButton>
              </Tooltip>
            </Stack>
            <Stack spacing={1} sx={{ mt: 1 }}>{data.buckets.filter((b) => b.benefitGroupId === g.id).map((b) => (
              <Stack key={b.id} direction="row" spacing={1} alignItems="center" sx={{ p: 1, borderRadius: 1, bgcolor: 'action.hover' }}>
                <Box sx={{ flexGrow: 1 }}>
                  <Typography variant="body2" fontWeight={600}>{b.nameAr}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    السقف: {b.amountLimit ?? 'غير محدود'}، المرات: {b.timesLimit ?? 'غير محدودة'}، الفترة: {periodLabels[b.periodType] || b.periodType}
                    {b.periodType === 'MULTI_YEAR_POLICY' ? ` (${b.periodValue} سنوات)` : ''}، القواعد المرتبطة: {data.links.filter((l) => l.bucket.id === b.id).length}
                  </Typography>
                </Box>
                <Tooltip title="حذف الوعاء">
                  <IconButton size="small" color="error" disabled={!canEdit || deleteBucketMutation.isPending}
                    onClick={() => window.confirm(`هل تريد حذف وعاء «${b.nameAr}»؟ فك روابطه أولًا إن وجدت.`) && deleteBucketMutation.mutate(b.id)}>
                    <DeleteOutlineIcon />
                  </IconButton>
                </Tooltip>
              </Stack>
            ))}</Stack>
          </Box>
        ))}
      </MainCard>
    </Stack>
  );
}
