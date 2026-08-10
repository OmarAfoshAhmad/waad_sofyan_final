import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
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
  TablePagination,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import RefreshIcon from '@mui/icons-material/Refresh';
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { useNavigate } from 'react-router-dom';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { getActiveContractByProvider } from 'services/api/provider-contracts.service';

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

const isPostedSession = (session) => session?.status === 'POSTED_TO_CONTRACT' || (session?.posted || 0) > 0;

// Mixed sessions are intentionally postable: approved rows go to the contract,
// while review/unknown/excluded rows remain in the review workflow.
const isPostableSession = (session) => Number(session?.highConfidence || 0) > 0;

const contractProviderId = (contract) => Number(contract?.provider?.id ?? contract?.providerId ?? 0) || null;

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
  const [selectedIds, setSelectedIds] = useState([]);
  const [postDialog, setPostDialog] = useState({
    open: false,
    sessions: [],
    targets: [],
    contract: null,
    effectiveFrom: '',
    diffLoading: false,
    diff: null,
    diffError: ''
  });
  const [deleteDialog, setDeleteDialog] = useState({ open: false, sessions: [] });
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

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

  const pagedSessions = useMemo(
    () => filteredSessions.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage),
    [filteredSessions, page, rowsPerPage]
  );

  const selectedSessions = useMemo(() => sessions.filter((session) => selectedIds.includes(session.id)), [sessions, selectedIds]);

  const deleteEligibleSessions = useMemo(() => selectedSessions.filter((session) => !isPostedSession(session)), [selectedSessions]);

  const postEligibleSessions = useMemo(() => selectedSessions.filter((session) => isPostableSession(session)), [selectedSessions]);

  const allPagedSelected = pagedSessions.length > 0 && pagedSessions.every((session) => selectedIds.includes(session.id));
  const somePagedSelected = pagedSessions.some((session) => selectedIds.includes(session.id));

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

  useEffect(() => {
    loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  useEffect(() => {
    setPage(0);
  }, [query, status]);

  const toggleSessionSelection = (sessionId) => {
    setSelectedIds((current) => (current.includes(sessionId) ? current.filter((id) => id !== sessionId) : [...current, sessionId]));
  };

  const togglePagedSelection = () => {
    const pageIds = pagedSessions.map((session) => session.id);
    setSelectedIds((current) => {
      if (allPagedSelected) {
        return current.filter((id) => !pageIds.includes(id));
      }
      return Array.from(new Set([...current, ...pageIds]));
    });
  };

  const loadLinkedActiveContract = async (session) => {
    if (!session?.providerId) throw new Error('القائمة غير مرتبطة بمقدم خدمة.');
    const contract = await getActiveContractByProvider(session.providerId);
    if (!contract?.id) throw new Error(`لا يوجد عقد عام نشط لمقدم الخدمة «${session.providerName || '-'}».`);
    return contract;
  };

  const openPostDialog = async (session) => {
    setContractsLoading(true);
    setError('');
    setSuccess('');
    try {
      const contract = await loadLinkedActiveContract(session);
      const effectiveFrom = contract.startDate || '';
      setContractOptions([contract]);
      const targets = [{ sessionId: session.id, contract, effectiveFrom }];
      setPostDialog({ open: true, sessions: [session], targets, contract, effectiveFrom, diffLoading: false, diff: null, diffError: '' });
      void loadPostDiff(contract, effectiveFrom, [session], targets);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'تعذر تحديد العقد النشط لمقدم الخدمة.');
    } finally {
      setContractsLoading(false);
    }
  };

  const openBulkPostDialog = async () => {
    if (!postEligibleSessions.length) return;
    setContractsLoading(true);
    setError('');
    setSuccess('');
    try {
      const resolved = await Promise.all(
        postEligibleSessions.map(async (session) => {
          try {
            const contract = await loadLinkedActiveContract(session);
            return { session, contract, effectiveFrom: contract.startDate || '', error: null };
          } catch (err) {
            return { session, contract: null, effectiveFrom: '', error: err?.response?.data?.message || err?.message };
          }
        })
      );
      const valid = resolved.filter((item) => item.contract);
      const failed = resolved.filter((item) => !item.contract);
      if (!valid.length) throw new Error('لا توجد عقود نشطة مرتبطة بالقوائم المحددة.');
      const targets = valid.map((item) => ({ sessionId: item.session.id, contract: item.contract, effectiveFrom: item.effectiveFrom }));
      setContractOptions(valid.map((item) => item.contract));
      setPostDialog({
        open: true,
        sessions: valid.map((item) => item.session),
        targets,
        contract: null,
        effectiveFrom: '',
        diffLoading: false,
        diff: null,
        diffError: ''
      });
      if (failed.length) {
        setError(
          `تم استبعاد ${failed.length} قائمة لعدم وجود عقد نشط مرتبط: ${failed.map((item) => item.session.providerName || `#${item.session.id}`).join('، ')}`
        );
      }
      void loadPostDiff(
        null,
        null,
        valid.map((item) => item.session),
        targets
      );
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'تعذر تحديد العقد النشط لمقدم الخدمة.');
    } finally {
      setContractsLoading(false);
    }
  };

  const closePostDialog = () => {
    if (posting) return;
    setPostDialog({
      open: false,
      sessions: [],
      targets: [],
      contract: null,
      effectiveFrom: '',
      diffLoading: false,
      diff: null,
      diffError: ''
    });
  };

  const openDeleteDialog = (session) => {
    setDeleteDialog({ open: true, sessions: [session] });
    setError('');
    setSuccess('');
  };

  const openBulkDeleteDialog = () => {
    if (!deleteEligibleSessions.length) return;
    setDeleteDialog({ open: true, sessions: deleteEligibleSessions });
    setError('');
    setSuccess('');
  };

  const closeDeleteDialog = () => {
    if (deleting) return;
    setDeleteDialog({ open: false, sessions: [] });
  };

  const loadPostDiff = async (
    contract = postDialog.contract,
    effectiveFrom = postDialog.effectiveFrom,
    sessions = postDialog.sessions,
    targets = postDialog.targets
  ) => {
    const sessionsToPost = sessions || [];
    if (!sessionsToPost.length) return;
    setPostDialog((current) => ({ ...current, diffLoading: true, diff: null, diffError: '' }));
    try {
      const responses = [];
      for (const session of sessionsToPost) {
        const target = targets?.find((item) => item.sessionId === session.id);
        const targetContract = target?.contract || contract;
        const targetEffectiveFrom = target?.effectiveFrom || effectiveFrom;
        if (!targetContract?.id) throw new Error(`لا يوجد عقد نشط مرتبط بالقائمة #${session.id}`);
        const diff = await medicalDictionaryService.diffPriceListClassificationSessionWithContract(session.id, {
          contractId: targetContract.id,
          effectiveFrom: targetEffectiveFrom || null,
          replaceEffectivePrices: false,
          onlyReviewedItems: true
        });
        responses.push(diff);
      }
      const aggregate = responses.reduce(
        (acc, diff) => ({
          total: acc.total + (diff.total || 0),
          createCount: acc.createCount + (diff.createCount || 0),
          updateCount: acc.updateCount + (diff.updateCount || 0),
          identicalCount: acc.identicalCount + (diff.identicalCount || 0),
          rejectedCount: acc.rejectedCount + (diff.rejectedCount || 0),
          hasChanges: acc.hasChanges || Boolean(diff.hasChanges),
          items: [...acc.items, ...(diff.items || []).map((item) => ({ ...item, sessionId: diff.sessionId }))]
        }),
        { total: 0, createCount: 0, updateCount: 0, identicalCount: 0, rejectedCount: 0, hasChanges: false, items: [] }
      );
      setPostDialog((current) => ({ ...current, diffLoading: false, diff: aggregate, diffError: '' }));
    } catch (err) {
      setPostDialog((current) => ({
        ...current,
        diffLoading: false,
        diff: null,
        diffError: err?.response?.data?.message || 'تعذر إنشاء تقرير الفروقات مع عقد مقدم الخدمة'
      }));
    }
  };

  const deleteSelectedSession = async () => {
    const sessionsToDelete = deleteDialog.sessions || [];
    if (!sessionsToDelete.length) return;
    setDeleting(true);
    setError('');
    setSuccess('');
    const deletedIds = [];
    const failedNames = [];
    try {
      for (const session of sessionsToDelete) {
        try {
          await medicalDictionaryService.deletePriceListClassificationSession(session.id);
          deletedIds.push(session.id);
        } catch {
          failedNames.push(session.sessionName || `#${session.id}`);
        }
      }
      setSuccess(`تم حذف ${deletedIds.length} قائمة مصنفة غير مرحلة.${failedNames.length ? ` تعذر حذف: ${failedNames.join('، ')}` : ''}`);
      if (failedNames.length) {
        setError(`تعذر حذف ${failedNames.length} قائمة. راجع حالتها أو ارتباطها بالعقود.`);
      }
      setSelectedIds((current) => current.filter((id) => !deletedIds.includes(id)));
      setDeleteDialog({ open: false, sessions: [] });
      await loadSessions();
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر حذف القائمة المصنفة');
    } finally {
      setDeleting(false);
    }
  };

  const postSelectedSession = async () => {
    const sessionsToPost = postDialog.sessions || [];
    if (!sessionsToPost.length || (!postDialog.contract?.id && !postDialog.targets?.length)) {
      setError('لا توجد عقود نشطة مرتبطة بالقوائم المحددة.');
      return;
    }
    if (postDialog.diff && !postDialog.diff.hasChanges) {
      setSuccess('لا يوجد تغييرات بين قائمة التنظيم وأسعار العقد؛ لم يتم تنفيذ أي ترحيل جديد.');
      closePostDialog();
      return;
    }
    setPosting(true);
    setError('');
    setSuccess('');
    let created = 0;
    let updated = 0;
    let superseded = 0;
    let rejected = 0;
    const postedIds = [];
    const failedNames = [];
    try {
      for (const session of sessionsToPost) {
        try {
          const target = postDialog.targets?.find((item) => item.sessionId === session.id);
          const targetContract = target?.contract || postDialog.contract;
          const targetEffectiveFrom = target?.effectiveFrom || postDialog.effectiveFrom;
          const response = await medicalDictionaryService.postPriceListClassificationSessionToContract(session.id, {
            contractId: targetContract.id,
            effectiveFrom: targetEffectiveFrom || null,
            replaceEffectivePrices: false,
            onlyReviewedItems: true
          });
          created += response.created || 0;
          updated += response.updated || 0;
          superseded += response.superseded || 0;
          rejected += response.rejected || 0;
          postedIds.push(session.id);
        } catch {
          failedNames.push(session.sessionName || `#${session.id}`);
        }
      }
      setSuccess(
        `تمت معالجة ${postedIds.length} قائمة: أُنشئ ${created} سعر، حُدّث ${updated} سعر، أُغلق ${superseded} سعر سابق، ورُفض ${rejected} بند.`
      );
      if (failedNames.length) {
        setError(`تعذر ترحيل ${failedNames.length} قائمة: ${failedNames.join('، ')}`);
      }
      setSelectedIds((current) => current.filter((id) => !postedIds.includes(id)));
      setPostDialog({ open: false, sessions: [], targets: [], contract: null, effectiveFrom: '' });
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
        <ModernPageHeader
          title="القوائم المصنفة"
          subtitle="متابعة القوائم المصنفة، جاهزية الترحيل، وما تم إرساله لعقود مقدمي الخدمة."
          actions={
            <>
              <Button variant="outlined" startIcon={<RefreshIcon />} onClick={loadSessions} disabled={loading}>
                تحديث
              </Button>
              <Button variant="contained" onClick={() => navigate('/price-list-classifier')}>
                تنظيم قائمة جديدة
              </Button>
            </>
          }
        />

        <MainCard contentSX={{ p: 2 }}>
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
        </MainCard>

        {error && <Alert severity="error">{error}</Alert>}
        {success && <Alert severity="success">{success}</Alert>}

        {selectedIds.length > 0 && (
          <MainCard contentSX={{ p: 1.5, bgcolor: 'primary.lighter' }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.25} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Typography sx={{ flex: 1, fontWeight: 700 }}>
                تم تحديد {selectedIds.length} قائمة — {postEligibleSessions.length} تحتوي خدمات معتمدة قابلة للترحيل. ستبقى بقية الخدمات في
                قسم المراجعة.
              </Typography>
              <Button
                color="error"
                variant="outlined"
                startIcon={<DeleteOutlineIcon />}
                disabled={!deleteEligibleSessions.length}
                onClick={openBulkDeleteDialog}
                sx={{ minHeight: 40, minWidth: 135 }}
              >
                حذف المحدد
              </Button>
              <Button
                variant="contained"
                startIcon={<PlaylistAddCheckIcon />}
                disabled={!postEligibleSessions.length}
                onClick={openBulkPostDialog}
                sx={{ minHeight: 40, minWidth: 145 }}
              >
                ترحيل المعتمد
              </Button>
              <Button variant="text" onClick={() => setSelectedIds([])} sx={{ minHeight: 40 }}>
                إلغاء التحديد
              </Button>
            </Stack>
          </MainCard>
        )}

        <MainCard contentSX={{ p: 0 }}>
          {loading ? (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={allPagedSelected}
                        indeterminate={!allPagedSelected && somePagedSelected}
                        onChange={togglePagedSelection}
                        disabled={!pagedSessions.length}
                      />
                    </TableCell>
                    <TableCell>#</TableCell>
                    <TableCell width="30%">الجلسة</TableCell>
                    <TableCell width="30%" align="center">
                      مقدم الخدمة
                    </TableCell>
                    <TableCell align="center">الحالة</TableCell>
                    <TableCell align="center">الملخص</TableCell>
                    <TableCell align="center">آخر تحديث</TableCell>
                    <TableCell align="center">إجراءات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pagedSessions.map((session) => (
                    <TableRow key={session.id} hover selected={selectedIds.includes(session.id)}>
                      <TableCell padding="checkbox">
                        <Checkbox checked={selectedIds.includes(session.id)} onChange={() => toggleSessionSelection(session.id)} />
                      </TableCell>
                      <TableCell>{session.id}</TableCell>
                      <TableCell>
                        <Typography fontWeight={800}>{session.sessionName}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {session.originalFileName || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Chip size="small" label={session.providerName || '-'} variant="outlined" color="primary" />
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          size="small"
                          color={statusColor(session.status)}
                          label={statusLabel(session.status)}
                          sx={{ minWidth: 120, borderRadius: '6px', fontWeight: 'bold' }}
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 85px)', gap: 0.5, justifyContent: 'center' }}>
                          <Chip size="small" label={`صفوف ${session.totalRows || 0}`} sx={{ borderRadius: '4px', width: '100%' }} />
                          <Chip
                            size="small"
                            color="success"
                            label={`ثقة ${session.highConfidence || 0}`}
                            sx={{ borderRadius: '4px', width: '100%' }}
                          />
                          <Chip
                            size="small"
                            color="warning"
                            label={`مراجعة ${session.needsReview || 0}`}
                            sx={{ borderRadius: '4px', width: '100%' }}
                          />
                          <Chip
                            size="small"
                            color="info"
                            variant="outlined"
                            label={`مرحّل ${session.posted || 0}`}
                            sx={{ borderRadius: '4px', width: '100%', gridColumn: '2' }}
                          />
                          <Chip
                            size="small"
                            color="error"
                            label={`مجهول ${session.unknown || 0}`}
                            sx={{ borderRadius: '4px', width: '100%', gridColumn: '3' }}
                          />
                        </Box>
                      </TableCell>
                      <TableCell align="center">
                        {session.updatedAt ? (
                          <Stack alignItems="center" spacing={0}>
                            <Typography variant="body2">{new Date(session.updatedAt).toLocaleDateString('ar-LY')}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {new Date(session.updatedAt).toLocaleTimeString('ar-LY')}
                            </Typography>
                          </Stack>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="column" spacing={1} alignItems="center">
                          <Button
                            size="small"
                            color="error"
                            variant="contained"
                            startIcon={<DeleteOutlineIcon />}
                            disabled={isPostedSession(session)}
                            onClick={() => openDeleteDialog(session)}
                            sx={{ minWidth: 90 }}
                          >
                            حذف
                          </Button>
                          <Button
                            size="small"
                            color="success"
                            variant="contained"
                            startIcon={<PlaylistAddCheckIcon />}
                            disabled={!isPostableSession(session)}
                            onClick={() => openPostDialog(session)}
                            sx={{ minWidth: 90 }}
                          >
                            {isPostedSession(session) ? 'ترحيل المتبقي' : 'ترحيل المعتمد'}
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                  {!filteredSessions.length && (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                        لا توجد جلسات مطابقة.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          <TablePagination
            component="div"
            count={filteredSessions.length}
            page={page}
            onPageChange={(e, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value, 10));
              setPage(0);
            }}
            labelRowsPerPage="الصفوف لكل صفحة:"
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
          />
        </MainCard>

        <Dialog open={postDialog.open} onClose={closePostDialog} maxWidth="sm" fullWidth>
          <DialogTitle>
            {postDialog.sessions.length > 1
              ? `ترحيل ${postDialog.sessions.length} قوائم مصنفة إلى عقد`
              : 'ترحيل قائمة مصنفة إلى عقد مقدم خدمة'}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info">
                سيتم إنشاء أسعار عقد جديدة من تاريخ النفاذ. إذا وُجد سعر فعال سابق لنفس الخدمة سيتم إغلاقه تاريخياً قبل السعر الجديد.
              </Alert>
              {postDialog.sessions.length > 1 ? (
                <Box sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                  <Typography fontWeight={900} sx={{ mb: 1 }}>
                    القوائم التي سيتم ترحيلها:
                  </Typography>
                  <Stack spacing={0.5}>
                    {postDialog.sessions.slice(0, 5).map((session) => (
                      <Typography key={session.id} variant="body2">
                        #{session.id} — {session.sessionName}
                      </Typography>
                    ))}
                    {postDialog.sessions.length > 5 && (
                      <Typography variant="caption" color="text.secondary">
                        و {postDialog.sessions.length - 5} قوائم أخرى.
                      </Typography>
                    )}
                  </Stack>
                </Box>
              ) : (
                <Typography fontWeight={800}>{postDialog.sessions[0]?.sessionName}</Typography>
              )}
              {postDialog.sessions.length === 1 ? (
                <>
                  <Autocomplete
                    options={contractOptions.filter((option) => contractProviderId(option) === Number(postDialog.sessions[0]?.providerId))}
                    loading={contractsLoading}
                    value={postDialog.contract}
                    disabled
                    getOptionLabel={(option) =>
                      option
                        ? `${option.contractCode || `#${option.id}`} — ${option.provider?.name || 'مقدم خدمة غير محدد'} — ${
                            option.pricingScopeLabel || option.pricingScope || ''
                          }`
                        : ''
                    }
                    isOptionEqualToValue={(option, value) => String(option?.id) === String(value?.id)}
                    renderInput={(params) => <TextField {...params} label="العقد النشط المرتبط بمقدم الخدمة" />}
                  />
                  <TextField
                    type="date"
                    label="تاريخ نفاذ الأسعار"
                    value={postDialog.effectiveFrom}
                    onChange={(event) => {
                      const nextEffectiveFrom = event.target.value;
                      const targets = [
                        { sessionId: postDialog.sessions[0].id, contract: postDialog.contract, effectiveFrom: nextEffectiveFrom }
                      ];
                      setPostDialog((current) => ({ ...current, targets, effectiveFrom: nextEffectiveFrom, diff: null, diffError: '' }));
                      if (postDialog.contract?.id) loadPostDiff(postDialog.contract, nextEffectiveFrom, postDialog.sessions, targets);
                    }}
                    InputLabelProps={{ shrink: true }}
                  />
                </>
              ) : (
                <Alert severity="success">
                  سيُوجَّه كل مرفق تلقائياً إلى عقده العام النشط، ويُستخدم تاريخ بداية ذلك العقد كتاريخ نفاذ لأسعاره.
                </Alert>
              )}
              {(postDialog.contract || postDialog.targets?.length > 0) && (
                <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'stretch', sm: 'center' }}>
                    <Typography fontWeight={900} sx={{ flex: 1 }}>
                      تقرير الفروقات قبل الترحيل
                    </Typography>
                    <Button size="small" variant="outlined" onClick={() => loadPostDiff()} disabled={postDialog.diffLoading}>
                      تحديث التقرير
                    </Button>
                  </Stack>
                  {postDialog.diffLoading && (
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                      <CircularProgress size={18} />
                      <Typography variant="body2">جاري مقارنة قائمة التنظيم مع أسعار العقد...</Typography>
                    </Stack>
                  )}
                  {postDialog.diffError && (
                    <Alert severity="error" sx={{ mt: 1 }}>
                      {postDialog.diffError}
                    </Alert>
                  )}
                  {postDialog.diff && (
                    <Stack spacing={1.25} sx={{ mt: 1.5 }}>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Chip label={`الإجمالي ${postDialog.diff.total}`} />
                        <Chip color="success" label={`جديد ${postDialog.diff.createCount}`} />
                        <Chip color="info" label={`سيتحدث ${postDialog.diff.updateCount}`} />
                        <Chip color="default" label={`مطابق ${postDialog.diff.identicalCount}`} />
                        <Chip color="error" label={`مرفوض ${postDialog.diff.rejectedCount}`} />
                      </Stack>
                      {!postDialog.diff.hasChanges ? (
                        <Alert severity="success">لا يوجد تغييرات. قائمة التنظيم مطابقة لأسعار العقد، ولن يتم إرسال تحديثات زائدة.</Alert>
                      ) : (
                        <Alert severity="warning">توجد تغييرات ستؤثر على أسعار عقد مقدم الخدمة عند اعتماد الترحيل.</Alert>
                      )}
                      <Box sx={{ maxHeight: 180, overflow: 'auto' }}>
                        {postDialog.diff.items
                          .filter((item) => item.action !== 'IDENTICAL')
                          .slice(0, 8)
                          .map((item) => (
                            <Typography key={`${item.sessionId}-${item.itemId}`} variant="caption" display="block" sx={{ py: 0.25 }}>
                              {item.action === 'CREATE' ? 'إضافة' : item.action === 'UPDATE' ? 'تحديث' : 'رفض'} — {item.serviceName} —{' '}
                              {item.message}
                            </Typography>
                          ))}
                      </Box>
                    </Stack>
                  )}
                </Box>
              )}
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
              disabled={
                posting ||
                postDialog.diffLoading ||
                (!postDialog.contract && !postDialog.targets?.length) ||
                (postDialog.diff && !postDialog.diff.hasChanges)
              }
              onClick={postSelectedSession}
            >
              {postDialog.sessions.length > 1 ? 'ترحيل كل قائمة إلى عقد مرفقها' : 'ترحيل للعقد'}
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
              هل تريد حذف {deleteDialog.sessions.length > 1 ? `${deleteDialog.sessions.length} قوائم مصنفة` : 'القائمة'}؟
            </Typography>
            <Box sx={{ mt: 1.5, maxHeight: 180, overflow: 'auto', border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1 }}>
              {(deleteDialog.sessions || []).map((session) => (
                <Typography key={session.id} variant="body2" sx={{ py: 0.5 }}>
                  #{session.id} — {session.sessionName} — صفوف {session.totalRows || 0}
                </Typography>
              ))}
            </Box>
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
