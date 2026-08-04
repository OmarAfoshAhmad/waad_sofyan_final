import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
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
import { CheckCircle as ResolveIcon, Refresh as RefreshIcon, Visibility as DetailIcon } from '@mui/icons-material';
import systemErrorsService from 'services/api/systemErrors.service';

const SEVERITY_LABELS = { INFO: 'معلومة', WARN: 'تحذير', ERROR: 'خطأ', CRITICAL: 'حرج' };
const SOURCE_LABELS = { BACKEND: 'الخادم', FRONTEND: 'الواجهة' };

const severityColor = (s) => {
  if (s === 'CRITICAL') return 'error';
  if (s === 'ERROR') return 'error';
  if (s === 'WARN') return 'warning';
  return 'info';
};

const formatDate = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('ar-LY');
  } catch {
    return value;
  }
};

const SystemErrorLogTab = () => {
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  const [filters, setFilters] = useState({ source: '', severity: '', resolved: '' });

  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [savingId, setSavingId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = { page, size };
      if (filters.source) params.source = filters.source;
      if (filters.severity) params.severity = filters.severity;
      if (filters.resolved !== '') params.resolved = filters.resolved;
      const data = await systemErrorsService.list(params);
      setRows(data?.content || []);
      setTotal(data?.totalElements ?? 0);
    } catch (e) {
      setError(e?.response?.data?.messageAr || e?.userMessage || 'فشل تحميل سجل الأخطاء');
    } finally {
      setLoading(false);
    }
  }, [page, size, filters]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openDetail = async (id) => {
    setDetailLoading(true);
    setDetail({ id });
    try {
      const data = await systemErrorsService.get(id);
      setDetail(data);
    } catch (e) {
      setError(e?.userMessage || 'فشل تحميل تفاصيل الخطأ');
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const toggleResolve = async (row) => {
    setSavingId(row.id);
    setMessage(null);
    setError(null);
    try {
      const next = !row.resolved;
      await systemErrorsService.resolve(row.id, { resolved: next });
      setMessage(next ? 'تم تعليم الخطأ كمحلول.' : 'تم إعادة فتح الخطأ.');
      await loadData();
      if (detail?.id === row.id) {
        await openDetail(row.id);
      }
    } catch (e) {
      setError(e?.userMessage || 'فشل تحديث حالة الخطأ');
    } finally {
      setSavingId(null);
    }
  };

  return (
    <Box sx={{ p: '1rem', overflow: 'auto', height: '100%' }} dir="rtl">
      <Stack spacing={2}>
        {message && (
          <Alert severity="success" onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}
        {error && (
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Card variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
              <Typography variant="h6">سجل أخطاء النظام</Typography>
              <Button size="small" startIcon={<RefreshIcon />} onClick={loadData} disabled={loading}>
                تحديث
              </Button>
            </Stack>
            <Alert severity="info">
              يعرض هذا السجل أخطاء الخادم (500) وأخطاء الواجهة التي ظهرت للمستخدمين، مع رقم التتبع، للمساعدة في التشخيص بدل الاعتماد على صور
              الشاشة. لا يتم تسجيل كلمات المرور أو الرموز السرية.
            </Alert>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="المصدر"
                  value={filters.source}
                  onChange={(e) => {
                    setPage(0);
                    setFilters((p) => ({ ...p, source: e.target.value }));
                  }}
                >
                  <MenuItem value="">الكل</MenuItem>
                  <MenuItem value="BACKEND">الخادم</MenuItem>
                  <MenuItem value="FRONTEND">الواجهة</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="الخطورة"
                  value={filters.severity}
                  onChange={(e) => {
                    setPage(0);
                    setFilters((p) => ({ ...p, severity: e.target.value }));
                  }}
                >
                  <MenuItem value="">الكل</MenuItem>
                  {Object.entries(SEVERITY_LABELS).map(([v, l]) => (
                    <MenuItem key={v} value={v}>
                      {l}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="الحالة"
                  value={filters.resolved}
                  onChange={(e) => {
                    setPage(0);
                    setFilters((p) => ({ ...p, resolved: e.target.value }));
                  }}
                >
                  <MenuItem value="">الكل</MenuItem>
                  <MenuItem value="false">غير محلولة</MenuItem>
                  <MenuItem value="true">محلولة</MenuItem>
                </TextField>
              </Grid>
            </Grid>

            {loading ? (
              <Box display="flex" justifyContent="center" py={4}>
                <CircularProgress />
              </Box>
            ) : (
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>الوقت</TableCell>
                      <TableCell>المصدر</TableCell>
                      <TableCell>الخطورة</TableCell>
                      <TableCell>المستخدم</TableCell>
                      <TableCell>المسار</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>الرسالة</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell align="center">إجراءات</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={9} align="center">
                          لا توجد أخطاء مسجلة
                        </TableCell>
                      </TableRow>
                    )}
                    {rows.map((row) => (
                      <TableRow key={row.id} hover>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(row.occurredAt)}</TableCell>
                        <TableCell>{SOURCE_LABELS[row.source] || row.source}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={severityColor(row.severity)}
                            variant="outlined"
                            label={SEVERITY_LABELS[row.severity] || row.severity}
                          />
                        </TableCell>
                        <TableCell>{row.username || '—'}</TableCell>
                        <TableCell>
                          <Typography variant="caption" dir="ltr">
                            {row.path || row.frontendRoute || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell dir="ltr">{row.statusCode || '—'}</TableCell>
                        <TableCell sx={{ maxWidth: 260 }}>
                          <Typography variant="body2" noWrap title={row.userMessage || ''}>
                            {row.userMessage || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            color={row.resolved ? 'success' : 'default'}
                            variant="outlined"
                            label={row.resolved ? 'محلول' : 'غير محلول'}
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Stack direction="row" spacing={0.5} justifyContent="center">
                            <Button size="small" startIcon={<DetailIcon />} onClick={() => openDetail(row.id)}>
                              تفاصيل
                            </Button>
                            <Button
                              size="small"
                              color={row.resolved ? 'inherit' : 'success'}
                              startIcon={savingId === row.id ? <CircularProgress size={14} /> : <ResolveIcon />}
                              disabled={savingId === row.id}
                              onClick={() => toggleResolve(row)}
                            >
                              {row.resolved ? 'إعادة فتح' : 'محلول'}
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
            <TablePagination
              component="div"
              count={total}
              page={page}
              onPageChange={(e, p) => setPage(p)}
              rowsPerPage={size}
              onRowsPerPageChange={(e) => {
                setSize(parseInt(e.target.value, 10));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50, 100]}
              labelRowsPerPage="عدد الصفوف"
            />
          </Stack>
        </Card>
      </Stack>

      <Dialog open={Boolean(detail)} onClose={() => setDetail(null)} maxWidth="md" fullWidth dir="rtl">
        <DialogTitle>تفاصيل الخطأ {detail?.id ? `#${detail.id}` : ''}</DialogTitle>
        <DialogContent dividers>
          {detailLoading ? (
            <Box display="flex" justifyContent="center" py={4}>
              <CircularProgress />
            </Box>
          ) : detail ? (
            <Stack spacing={1.5}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    الوقت
                  </Typography>
                  <Typography variant="body2">{formatDate(detail.occurredAt)}</Typography>
                </Grid>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    المصدر
                  </Typography>
                  <Typography variant="body2">{SOURCE_LABELS[detail.source] || detail.source}</Typography>
                </Grid>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    الخطورة
                  </Typography>
                  <Typography variant="body2">{SEVERITY_LABELS[detail.severity] || detail.severity}</Typography>
                </Grid>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    رقم التتبع
                  </Typography>
                  <Typography variant="body2" dir="ltr">
                    {detail.correlationId || '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    المستخدم
                  </Typography>
                  <Typography variant="body2">
                    {detail.username || '—'} {detail.role ? `(${detail.role})` : ''}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    رمز الحالة
                  </Typography>
                  <Typography variant="body2" dir="ltr">
                    {detail.statusCode || '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 8 }}>
                  <Typography variant="caption" color="text.secondary">
                    المسار
                  </Typography>
                  <Typography variant="body2" dir="ltr">
                    {detail.httpMethod || ''} {detail.path || detail.frontendRoute || '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    البيئة
                  </Typography>
                  <Typography variant="body2">{detail.environment || '—'}</Typography>
                </Grid>
              </Grid>
              <Alert severity="info">{detail.userMessage || 'لا توجد رسالة للمستخدم'}</Alert>
              {detail.browser && (
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    المتصفح
                  </Typography>
                  <Typography variant="body2" dir="ltr">
                    {detail.browser}
                  </Typography>
                </Box>
              )}
              <Box>
                <Typography variant="caption" color="text.secondary">
                  الرسالة الفنية
                </Typography>
                <Typography variant="body2" dir="ltr" sx={{ wordBreak: 'break-word' }}>
                  {detail.technicalMessage || detail.exceptionClass || '—'}
                </Typography>
              </Box>
              <StackTraceViewer stack={detail.stackExcerpt} />
              {detail.resolved && (
                <Alert severity="success">
                  محلول بواسطة {detail.resolvedBy || '—'} في {formatDate(detail.resolvedAt)}
                  {detail.notes ? ` — ${detail.notes}` : ''}
                </Alert>
              )}
            </Stack>
          ) : (
            <Typography variant="body2">لا توجد تفاصيل</Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetail(null)}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

const StackTraceViewer = ({ stack }) => {
  const [open, setOpen] = useState(false);
  if (!stack) return null;
  return (
    <Box>
      <Button size="small" onClick={() => setOpen((v) => !v)}>
        {open ? 'إخفاء التتبع الفني' : 'عرض التتبع الفني'}
      </Button>
      <Collapse in={open}>
        <Box
          component="pre"
          sx={{
            mt: 1,
            p: 1.5,
            bgcolor: 'action.hover',
            borderRadius: 1,
            fontSize: '0.72rem',
            overflowX: 'auto',
            direction: 'ltr',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            maxHeight: 320
          }}
        >
          {stack}
        </Box>
      </Collapse>
    </Box>
  );
};

export default SystemErrorLogTab;
