import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
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
import { useNavigate } from 'react-router-dom';
import medicalDictionaryService from 'services/api/medical-dictionary.service';

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
  const [error, setError] = useState('');

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

  useEffect(() => {
    loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  const copySessionId = async (id) => {
    try {
      await navigator.clipboard.writeText(String(id));
    } catch {
      // Clipboard is a convenience only.
    }
  };

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h2" sx={{ fontWeight: 900 }}>
              جلسات تنظيم قوائم الأسعار
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
                          <Stack direction="row" spacing={0.75} justifyContent="center">
                            <Button size="small" startIcon={<ContentCopyIcon />} onClick={() => copySessionId(session.id)}>
                              نسخ الرقم
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
      </Stack>
    </Box>
  );
}
