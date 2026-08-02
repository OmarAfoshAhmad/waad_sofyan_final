import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { useNavigate } from 'react-router-dom';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { searchProviderContracts } from 'services/api/provider-contracts.service';

const STATUSES = [
  { value: 'ALL', label: 'كل الجلسات' },
  { value: 'READY_TO_POST', label: 'جاهزة للترحيل' },
  { value: 'NEEDS_REVIEW', label: 'تحتاج مراجعة' },
  { value: 'POSTED_TO_CONTRACT', label: 'مرحلة للعقود' },
  { value: 'DRAFT', label: 'مسودة' },
  { value: 'CANCELLED', label: 'ملغاة' }
];

const statusColor = (status) => {
  switch (status) {
    case 'POSTED_TO_CONTRACT':
      return 'success';
    case 'READY_TO_POST':
      return 'info';
    case 'NEEDS_REVIEW':
      return 'warning';
    case 'CANCELLED':
      return 'error';
    default:
      return 'default';
  }
};

const statusLabel = (status) => STATUSES.find((item) => item.value === status)?.label || status || '-';

const normalizePage = (response) => ({
  content: response?.content || response?.items || response?.data?.content || response?.data?.items || [],
  totalElements: response?.totalElements ?? response?.total ?? response?.data?.totalElements ?? response?.data?.total ?? 0
});

export default function PriceListSessionsPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState('ALL');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [posting, setPosting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [contractOptions, setContractOptions] = useState([]);
  const [contractsLoading, setContractsLoading] = useState(false);
  const [postDialog, setPostDialog] = useState({ open: false, session: null, contract: null, effectiveFrom: '' });
  const [deleteDialog, setDeleteDialog] = useState({ open: false, session: null });

  const filteredSessions = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return sessions;
    return sessions.filter((session) =>
      [session.sessionName, session.originalFileName, session.providerName, session.contractCode, session.status]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q))
    );
  }, [sessions, query]);

  const totals = useMemo(
    () => ({
      ready: sessions.filter((s) => s.status === 'READY_TO_POST').length,
      posted: sessions.filter((s) => s.status === 'POSTED_TO_CONTRACT').length,
      review: sessions.filter((s) => s.status === 'NEEDS_REVIEW').length
    }),
    [sessions]
  );

  const loadSessions = async () => {
    setLoading(true);
    setError('');
    try {
      const params = { page: 0, size: 100 };
      if (status !== 'ALL') params.status = status;
      const response = await medicalDictionaryService.listPriceListClassificationSessions(params);
      const page = normalizePage(response);
      setSessions(page.content);
      setTotal(page.totalElements || page.content.length);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل تحميل جلسات تنظيم قوائم الأسعار');
    } finally {
      setLoading(false);
    }
  };

  const loadActiveContracts = async () => {
    setContractsLoading(true);
    try {
      const response = await searchProviderContracts({ status: 'ACTIVE', page: 0, size: 80 });
      const content = response?.content || response?.items || response?.data?.content || response?.data?.items || [];
      setContractOptions(Array.isArray(content) ? content : []);
    } catch {
      setContractOptions([]);
    } finally {
      setContractsLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  useEffect(() => {
    loadActiveContracts();
  }, []);

  const copySessionId = async (id) => {
    try {
      await navigator.clipboard.writeText(String(id));
    } catch {
      // Clipboard is a convenience only.
    }
  };

  const openPostDialog = (session) => {
    setPostDialog({ open: true, session, contract: null, effectiveFrom: '' });
    setError('');
    setSuccess('');
  };

  const closePostDialog = () => {
    if (posting) return;
    setPostDialog({ open: false, session: null, contract: null, effectiveFrom: '' });
  };

  const openDeleteDialog = (session) => {
    setDeleteDialog({ open: true, session });
    setError('');
    setSuccess('');
  };

  const closeDeleteDialog = () => {
    if (deleting) return;
    setDeleteDialog({ open: false, session: null });
  };

  const deleteSelectedSession = async () => {
    if (!deleteDialog.session?.id) return;
    setDeleting(true);
    setError('');
    setSuccess('');
    try {
      await medicalDictionaryService.deletePriceListClassificationSession(deleteDialog.session.id);
      setSuccess(`تم حذف القائمة المصنفة رقم ${deleteDialog.session.id} لأنها لم تُرحّل لأي عقد.`);
      setDeleteDialog({ open: false, session: null });
      await loadSessions();
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر حذف القائمة المصنفة');
    } finally {
      setDeleting(false);
    }
  };

  const postSelectedSession = async () => {
    if (!postDialog.session?.id || !postDialog.contract?.id) {
      setError('اختر عقداً نشطاً قبل الترحيل.');
      return;
    }
    setPosting(true);
    setError('');
    setSuccess('');
    try {
      const response = await medicalDictionaryService.postPriceListClassificationSessionToContract(postDialog.session.id, {
        contractId: postDialog.contract.id,
        effectiveFrom: postDialog.effectiveFrom || null,
        replaceEffectivePrices: true,
        onlyReviewedItems: true
      });
      setSuccess(
        `تم ترحيل ${response.created || 0} سعر. أُغلقت ${response.superseded || 0} أسعار سابقة، ورُفض ${response.rejected || 0}.`
      );
      setPostDialog({ open: false, session: null, contract: null, effectiveFrom: '' });
      await loadSessions();
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل ترحيل القائمة المصنفة إلى العقد');
    } finally {
      setPosting(false);
    }
  };

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h2" sx={{ fontWeight: 900 }}>
              القوائم المصنفة
            </Typography>
            <Typography color="text.secondary" sx={{ mt: 0.75 }}>
              متابعة القوائم المصنفة، جاهزية الترحيل، وما تم إرساله لعقود مقدمي الخدمة.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={loadSessions} disabled={loading}>
              تحديث
            </Button>
            <Button variant="contained" onClick={() => navigate('/price-list-classifier')}>
              تنظيم قائمة جديدة
            </Button>
          </Stack>
        </Stack>

        <Card>
          <CardContent>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Chip label={`الإجمالي ${total}`} />
              <Chip color="info" label={`جاهزة ${totals.ready}`} />
              <Chip color="success" label={`مرحلة ${totals.posted}`} />
              <Chip color="warning" label={`تحتاج مراجعة ${totals.review}`} />
              <TextField
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="بحث باسم الجلسة، الملف، مقدم الخدمة، العقد..."
                size="small"
                sx={{ flex: 1, minWidth: 260 }}
              />
              <Select size="small" value={status} onChange={(event) => setStatus(event.target.value)} sx={{ minWidth: 190 }}>
                {STATUSES.map((item) => (
                  <MenuItem key={item.value} value={item.value}>
                    {item.label}
                  </MenuItem>
                ))}
              </Select>
            </Stack>
          </CardContent>
        </Card>

        {error && <Alert severity="error">{error}</Alert>}
        {success && <Alert severity="success">{success}</Alert>}

        <Card>
          <CardContent>
            {loading ? (
              <Stack alignItems="center" sx={{ py: 6 }}>
                <CircularProgress />
              </Stack>
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>#</TableCell>
                      <TableCell>الجلسة</TableCell>
                      <TableCell>مقدم الخدمة / العقد</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>الملخص</TableCell>
                      <TableCell>آخر تحديث</TableCell>
                      <TableCell align="center">إجراءات</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredSessions.map((session) => (
                      <TableRow key={session.id} hover>
                        <TableCell>{session.id}</TableCell>
                        <TableCell>
                          <Typography fontWeight={800}>{session.sessionName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {session.originalFileName || '-'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography>{session.providerName || '-'}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {session.contractCode || (session.contractId ? `عقد #${session.contractId}` : 'لم يرحل لعقد بعد')}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" color={statusColor(session.status)} label={statusLabel(session.status)} />
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.75} flexWrap="wrap">
                            <Chip size="small" label={`صفوف ${session.totalRows || 0}`} />
                            <Chip size="small" color="success" label={`ثقة ${session.highConfidence || 0}`} />
                            <Chip size="small" color="warning" label={`مراجعة ${session.needsReview || 0}`} />
                            <Chip size="small" color="error" label={`مجهول ${session.unknown || 0}`} />
                            <Chip size="small" color="info" variant="outlined" label={`مرحّل ${session.posted || 0}`} />
                          </Stack>
                        </TableCell>
                        <TableCell>
                          {session.updatedAt ? new Date(session.updatedAt).toLocaleString('ar-LY') : '-'}
                        </TableCell>
                        <TableCell align="center">
                          <Stack direction="row" spacing={0.75} justifyContent="center" flexWrap="wrap">
                            <Button size="small" startIcon={<ContentCopyIcon />} onClick={() => copySessionId(session.id)}>
                              نسخ الرقم
                            </Button>
                            <Button
                              size="small"
                              color="success"
                              variant="contained"
                              startIcon={<PlaylistAddCheckIcon />}
                              disabled={session.status === 'POSTED_TO_CONTRACT' || (session.unknown || 0) > 0 || (session.needsReview || 0) > 0}
                              onClick={() => openPostDialog(session)}
                            >
                              ترحيل
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<DeleteOutlineIcon />}
                              disabled={session.status === 'POSTED_TO_CONTRACT' || (session.posted || 0) > 0}
                              onClick={() => openDeleteDialog(session)}
                            >
                              حذف
                            </Button>
                            <Button size="small" startIcon={<OpenInNewIcon />} onClick={() => navigate(`/price-list-classifier?sessionId=${session.id}`)}>
                              فتح الأداة
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {!filteredSessions.length && (
                      <TableRow>
                        <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                          لا توجد جلسات مطابقة.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
            <Divider sx={{ mt: 2 }} />
            <Typography variant="caption" color="text.secondary">
              ملاحظة: هذه الصفحة للمتابعة التشغيلية. الحساب المالي النهائي يبقى في محرك التغطية والمطالبات.
            </Typography>
          </CardContent>
        </Card>

        <Dialog open={postDialog.open} onClose={closePostDialog} maxWidth="sm" fullWidth>
          <DialogTitle>ترحيل قائمة مصنفة إلى عقد مقدم خدمة</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info">
                سيتم إنشاء أسعار عقد جديدة من تاريخ النفاذ. إذا وُجد سعر فعال سابق لنفس الخدمة سيتم إغلاقه تاريخياً قبل السعر الجديد.
              </Alert>
              <Typography fontWeight={800}>{postDialog.session?.sessionName}</Typography>
              <Autocomplete
                options={contractOptions}
                loading={contractsLoading}
                value={postDialog.contract}
                onChange={(_, contract) =>
                  setPostDialog((current) => ({
                    ...current,
                    contract,
                    effectiveFrom: current.effectiveFrom || contract?.startDate || ''
                  }))
                }
                getOptionLabel={(option) =>
                  option
                    ? `${option.contractCode || `#${option.id}`} — ${option.provider?.name || 'مقدم خدمة غير محدد'} — ${
                        option.pricingScopeLabel || option.pricingScope || ''
                      }`
                    : ''
                }
                isOptionEqualToValue={(option, value) => String(option?.id) === String(value?.id)}
                renderInput={(params) => <TextField {...params} label="العقد النشط" placeholder="ابحث باسم مقدم الخدمة أو كود العقد" />}
              />
              <TextField
                type="date"
                label="تاريخ نفاذ الأسعار"
                value={postDialog.effectiveFrom}
                onChange={(event) => setPostDialog((current) => ({ ...current, effectiveFrom: event.target.value }))}
                InputLabelProps={{ shrink: true }}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={closePostDialog} disabled={posting}>
              إلغاء
            </Button>
            <Button
              variant="contained"
              color="success"
              startIcon={posting ? <CircularProgress size={18} /> : <PlaylistAddCheckIcon />}
              disabled={posting || !postDialog.contract}
              onClick={postSelectedSession}
            >
              ترحيل للعقد
            </Button>
          </DialogActions>
        </Dialog>

        <Dialog open={deleteDialog.open} onClose={closeDeleteDialog} maxWidth="sm" fullWidth>
          <DialogTitle>
            <Stack direction="row" spacing={1} alignItems="center">
              <WarningAmberIcon color="error" />
              <span>تأكيد حذف القائمة المصنفة</span>
            </Stack>
          </DialogTitle>
          <DialogContent>
            <Alert severity="error" sx={{ mb: 2 }}>
              سيتم حذف القائمة المصنفة وبنودها من المتابعة. لا يمكن حذف قائمة تم ترحيلها لعقد مقدم خدمة.
            </Alert>
            <Typography>
              هل تريد حذف القائمة:
            </Typography>
            <Typography sx={{ mt: 1, fontWeight: 900 }}>
              {deleteDialog.session?.sessionName}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              رقم القائمة: {deleteDialog.session?.id} — عدد الصفوف: {deleteDialog.session?.totalRows || 0}
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeDeleteDialog} disabled={deleting}>
              تراجع
            </Button>
            <Button
              variant="contained"
              color="error"
              startIcon={deleting ? <CircularProgress size={18} /> : <DeleteOutlineIcon />}
              disabled={deleting}
              onClick={deleteSelectedSession}
            >
              حذف نهائي
            </Button>
          </DialogActions>
        </Dialog>
      </Stack>
    </Box>
  );
}
