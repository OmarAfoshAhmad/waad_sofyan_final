import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import GavelIcon from '@mui/icons-material/Gavel';
import { useSnackbar } from 'notistack';
import claimPendingServicesService from 'services/api/claim-pending-services.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import { formatCurrency } from 'utils/formatters';

const OPEN_STATUSES = new Set(['PRELIMINARY', 'NEEDS_INFO', 'SPLIT_REQUIRED']);
const STATUS_META = {
  PRELIMINARY: ['حساب مبدئي', 'warning'],
  NEEDS_INFO: ['يحتاج معلومات', 'info'],
  SPLIT_REQUIRED: ['يحتاج تقسيم', 'warning'],
  APPROVED_CLAIM_ONLY: ['معتمد لهذه المطالبة', 'success'],
  APPROVED_FOR_CONTRACT: ['معتمد ومضاف للعقد', 'success'],
  LINKED_EXISTING: ['مرتبط بخدمة موجودة', 'success'],
  REJECTED: ['مرفوض', 'error']
};
const DECISIONS = [
  ['APPROVED_CLAIM_ONLY', 'اعتماد لهذه المطالبة فقط'],
  ['APPROVED_FOR_CONTRACT', 'اعتماد وإضافته إلى عقد مقدم الخدمة'],
  ['LINKED_EXISTING', 'ربطه بخدمة موجودة في العقد'],
  ['NEEDS_INFO', 'طلب معلومات إضافية'],
  ['SPLIT_REQUIRED', 'إعادته للتقسيم إلى خدمات'],
  ['REJECTED', 'رفض الخدمة']
];
const emptyCreate = { serviceCode: '', serviceName: '', proposedCategoryId: '', proposedUnitPrice: '' };
const emptyDecision = {
  decision: 'APPROVED_CLAIM_ONLY',
  reason: '',
  finalServiceCode: '',
  finalServiceName: '',
  finalCategoryId: '',
  finalUnitPrice: '',
  linkedPricingItemId: '',
  contractEffectiveFrom: ''
};

const ClaimPendingServicesPanel = ({ claimId, claimStatus, canDecide, locked, onChanged }) => {
  const { enqueueSnackbar } = useSnackbar();
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [decisionItem, setDecisionItem] = useState(null);
  const [createForm, setCreateForm] = useState(emptyCreate);
  const [decisionForm, setDecisionForm] = useState(emptyDecision);
  const editable = claimStatus === 'UNDER_REVIEW' && !locked;
  const unresolvedCount = useMemo(() => items.filter((item) => OPEN_STATUSES.has(item.status)).length, [items]);

  const load = useCallback(async () => {
    if (!claimId) return;
    try {
      setLoading(true);
      const [pending, allCategories] = await Promise.all([claimPendingServicesService.list(claimId), getAllMedicalCategories()]);
      setItems(Array.isArray(pending) ? pending : []);
      setCategories((Array.isArray(allCategories) ? allCategories : []).filter((category) => category.active !== false));
    } catch (error) {
      enqueueSnackbar(error?.message || 'تعذر تحميل الخدمات المدخلة أثناء المراجعة', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [claimId, enqueueSnackbar]);
  useEffect(() => {
    load();
  }, [load]);

  const createService = async () => {
    if (!createForm.serviceName.trim() || !createForm.proposedCategoryId || Number(createForm.proposedUnitPrice) <= 0) {
      enqueueSnackbar('أدخل اسم الخدمة والتصنيف والسعر بصورة صحيحة', { variant: 'warning' });
      return;
    }
    try {
      setSaving(true);
      await claimPendingServicesService.create(claimId, {
        serviceCode: createForm.serviceCode.trim() || null,
        serviceName: createForm.serviceName.trim(),
        proposedCategoryId: Number(createForm.proposedCategoryId),
        proposedUnitPrice: Number(createForm.proposedUnitPrice)
      });
      setCreateOpen(false);
      setCreateForm(emptyCreate);
      await load();
      await onChanged?.();
      enqueueSnackbar('أضيفت الخدمة وحُسبت مبدئياً دون استهلاك السقوف', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar(error?.message || 'تعذر إضافة الخدمة', { variant: 'error' });
    } finally {
      setSaving(false);
    }
  };

  const openDecision = (item) => {
    setDecisionItem(item);
    setDecisionForm({
      ...emptyDecision,
      finalServiceCode: item.finalServiceCode || item.proposedServiceCode || '',
      finalServiceName: item.finalServiceName || item.proposedServiceName || '',
      finalCategoryId: item.finalCategoryId || item.proposedCategoryId || '',
      finalUnitPrice: item.finalUnitPrice || item.proposedUnitPrice || ''
    });
  };
  const submitDecision = async () => {
    if (!decisionForm.reason.trim()) {
      enqueueSnackbar('سبب القرار إلزامي', { variant: 'warning' });
      return;
    }
    const approved = ['APPROVED_CLAIM_ONLY', 'APPROVED_FOR_CONTRACT'].includes(decisionForm.decision);
    if (approved && (!decisionForm.finalServiceName.trim() || !decisionForm.finalCategoryId || Number(decisionForm.finalUnitPrice) <= 0)) {
      enqueueSnackbar('الاسم والتصنيف والسعر النهائي مطلوبة للاعتماد', { variant: 'warning' });
      return;
    }
    if (decisionForm.decision === 'LINKED_EXISTING' && !decisionForm.linkedPricingItemId) {
      enqueueSnackbar('حدد رقم خدمة العقد الموجودة', { variant: 'warning' });
      return;
    }
    try {
      setSaving(true);
      await claimPendingServicesService.decide(claimId, decisionItem.id, {
        ...decisionForm,
        reason: decisionForm.reason.trim(),
        finalServiceCode: decisionForm.finalServiceCode.trim() || null,
        finalServiceName: decisionForm.finalServiceName.trim() || null,
        finalCategoryId: decisionForm.finalCategoryId ? Number(decisionForm.finalCategoryId) : null,
        finalUnitPrice: decisionForm.finalUnitPrice ? Number(decisionForm.finalUnitPrice) : null,
        linkedPricingItemId: decisionForm.linkedPricingItemId ? Number(decisionForm.linkedPricingItemId) : null,
        contractEffectiveFrom: decisionForm.contractEffectiveFrom || null
      });
      setDecisionItem(null);
      await load();
      await onChanged?.();
      enqueueSnackbar('حُفظ القرار وأُعيد الحساب المبدئي', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar(error?.message || 'تعذر حفظ القرار', { variant: 'error' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <CircularProgress size={24} />;
  return (
    <Stack spacing={1.25}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1}>
        <Box>
          <Typography variant="subtitle2" fontWeight={700}>
            خدمات أُدخلت أثناء المراجعة
          </Typography>
          <Typography variant="caption" color="text.secondary">
            السعر مبدئي للحساب فقط؛ لا يُستهلك السقف ولا ينشأ التزام مالي قبل الاعتماد النهائي.
          </Typography>
        </Box>
        <Button startIcon={<AddIcon />} variant="outlined" disabled={!editable || saving} onClick={() => setCreateOpen(true)}>
          إدخال خدمة
        </Button>
      </Stack>
      {unresolvedCount > 0 && (
        <Alert severity="warning">يوجد {unresolvedCount} خدمة تنتظر قراراً، ولا يمكن اعتماد المطالبة نهائياً قبل حسمها.</Alert>
      )}
      {items.length === 0 ? (
        <Alert severity="info">لم تُضف خدمات جديدة من شاشة المراجعة.</Alert>
      ) : (
        items.map((item) => {
          const [label, color] = STATUS_META[item.status] || [item.status, 'default'];
          return (
            <Box key={item.id} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.25 }}>
              <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="body2" fontWeight={700}>
                      {item.finalServiceName || item.proposedServiceName}
                    </Typography>
                    <Chip size="small" color={color} label={label} />
                    {item.dictionaryVersion && <Chip size="small" variant="outlined" label={`القاموس ${item.dictionaryVersion}`} />}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    السعر: {formatCurrency(item.finalUnitPrice || item.proposedUnitPrice || 0)}
                    {item.classificationReason ? ` — ${item.classificationReason}` : ''}
                  </Typography>
                </Box>
                {canDecide && editable && OPEN_STATUSES.has(item.status) && (
                  <Button size="small" startIcon={<GavelIcon />} onClick={() => openDecision(item)}>
                    اتخاذ قرار
                  </Button>
                )}
              </Stack>
              {item.decisionReason && (
                <Alert severity="info" sx={{ mt: 1 }}>
                  {item.decisionReason}
                </Alert>
              )}
            </Box>
          );
        })
      )}
      <Dialog open={createOpen} onClose={() => !saving && setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>إدخال خدمة جديدة أثناء المراجعة</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ mt: 0.25 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                fullWidth
                label="رمز الخدمة (اختياري)"
                value={createForm.serviceCode}
                onChange={(e) => setCreateForm({ ...createForm, serviceCode: e.target.value })}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <TextField
                required
                fullWidth
                label="اسم الخدمة"
                value={createForm.serviceName}
                onChange={(e) => setCreateForm({ ...createForm, serviceName: e.target.value })}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 7 }}>
              <TextField
                required
                select
                fullWidth
                label="التصنيف الطبي"
                value={createForm.proposedCategoryId}
                onChange={(e) => setCreateForm({ ...createForm, proposedCategoryId: e.target.value })}
              >
                {categories.map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.name || c.nameAr}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <TextField
                required
                fullWidth
                type="number"
                label="السعر المبدئي"
                value={createForm.proposedUnitPrice}
                onChange={(e) => setCreateForm({ ...createForm, proposedUnitPrice: e.target.value })}
                inputProps={{ min: 0.01, step: 0.01 }}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)} disabled={saving}>
            إلغاء
          </Button>
          <Button variant="contained" onClick={createService} disabled={saving}>
            إضافة وحساب مبدئي
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={Boolean(decisionItem)} onClose={() => !saving && setDecisionItem(null)} fullWidth maxWidth="md">
        <DialogTitle>قرار الخدمة المدخلة</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ mt: 0.25 }}>
            <Grid size={12}>
              <TextField
                select
                required
                fullWidth
                label="القرار"
                value={decisionForm.decision}
                onChange={(e) => setDecisionForm({ ...decisionForm, decision: e.target.value })}
              >
                {DECISIONS.map(([value, label]) => (
                  <MenuItem key={value} value={value}>
                    {label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            {decisionForm.decision === 'LINKED_EXISTING' ? (
              <Grid size={12}>
                <TextField
                  required
                  fullWidth
                  type="number"
                  label="رقم خدمة العقد الموجودة"
                  value={decisionForm.linkedPricingItemId}
                  onChange={(e) => setDecisionForm({ ...decisionForm, linkedPricingItemId: e.target.value })}
                />
              </Grid>
            ) : !['REJECTED', 'NEEDS_INFO', 'SPLIT_REQUIRED'].includes(decisionForm.decision) ? (
              <>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField
                    fullWidth
                    label="الرمز النهائي"
                    value={decisionForm.finalServiceCode}
                    onChange={(e) => setDecisionForm({ ...decisionForm, finalServiceCode: e.target.value })}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 8 }}>
                  <TextField
                    required
                    fullWidth
                    label="الاسم النهائي"
                    value={decisionForm.finalServiceName}
                    onChange={(e) => setDecisionForm({ ...decisionForm, finalServiceName: e.target.value })}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 7 }}>
                  <TextField
                    required
                    select
                    fullWidth
                    label="التصنيف النهائي"
                    value={decisionForm.finalCategoryId}
                    onChange={(e) => setDecisionForm({ ...decisionForm, finalCategoryId: e.target.value })}
                  >
                    {categories.map((c) => (
                      <MenuItem key={c.id} value={c.id}>
                        {c.name || c.nameAr}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid size={{ xs: 12, md: 5 }}>
                  <TextField
                    required
                    fullWidth
                    type="number"
                    label="السعر النهائي"
                    value={decisionForm.finalUnitPrice}
                    onChange={(e) => setDecisionForm({ ...decisionForm, finalUnitPrice: e.target.value })}
                  />
                </Grid>
                {decisionForm.decision === 'APPROVED_FOR_CONTRACT' && (
                  <Grid size={12}>
                    <TextField
                      fullWidth
                      type="date"
                      label="سريان السعر في العقد"
                      value={decisionForm.contractEffectiveFrom}
                      onChange={(e) => setDecisionForm({ ...decisionForm, contractEffectiveFrom: e.target.value })}
                      InputLabelProps={{ shrink: true }}
                    />
                  </Grid>
                )}
              </>
            ) : null}
            <Grid size={12}>
              <TextField
                required
                fullWidth
                multiline
                rows={3}
                label="سبب القرار"
                value={decisionForm.reason}
                onChange={(e) => setDecisionForm({ ...decisionForm, reason: e.target.value })}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionItem(null)} disabled={saving}>
            إلغاء
          </Button>
          <Button variant="contained" onClick={submitDecision} disabled={saving}>
            حفظ القرار
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};
export default ClaimPendingServicesPanel;
