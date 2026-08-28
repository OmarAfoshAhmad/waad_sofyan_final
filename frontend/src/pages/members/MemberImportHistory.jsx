import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  Download as DownloadIcon,
  ErrorOutline as ErrorOutlineIcon,
  Undo as UndoIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import useAuth from 'hooks/useAuth';
import {
  getImportLogs,
  getImportErrors,
  previewImportRollback,
  executeImportRollback
} from 'services/api/unified-members.service';

const STATUS_LABEL = { COMPLETED: 'مكتمل', PARTIAL: 'مكتمل مع أخطاء', FAILED: 'فشل', PENDING: 'قيد الإعداد', VALIDATING: 'جارِ التحقق', PROCESSING: 'جارِ المعالجة' };
const STATUS_COLOR = { COMPLETED: 'success', PARTIAL: 'warning', FAILED: 'error', PENDING: 'default', VALIDATING: 'info', PROCESSING: 'info' };
const ROLLBACK_SKIP_REASON = {
  HAS_PROTECTED_HISTORY: 'له سجل مالي أو طبي يجب الحفاظ عليه',
  MODIFIED_AFTER_IMPORT: 'عُدّلت بياناته بعد الاستيراد ولن تُكتب فوق التعديل اللاحق',
  MEMBER_MISSING: 'لم يعد السجل موجوداً',
  FAMILY_STILL_REFERENCES_MEMBER: 'ما زال مرتبطاً بأسرة محفوظة'
};

function downloadCsv(filename, headers, rows) {
  const escape = (value) => {
    const text = value === null || value === undefined ? '' : String(value);
    return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  const lines = [headers.map(escape).join(','), ...rows.map((row) => row.map(escape).join(','))];
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.parentNode.removeChild(link);
  window.URL.revokeObjectURL(url);
}

const MemberImportHistory = () => {
  const { enqueueSnackbar } = useSnackbar();
  const { user } = useAuth();
  const permissions = new Set(user?.permissions || []);
  const canRollback = permissions.has('MEMBER_IMPORT') && permissions.has('DANGER_ZONE_EXECUTE');

  const [logs, setLogs] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0); // MUI TablePagination is 0-based; backend is 1-based
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const [rollbackTarget, setRollbackTarget] = useState(null); // the log row being considered
  const [rollbackPreview, setRollbackPreview] = useState(null);
  const [rollbackReason, setRollbackReason] = useState('');
  const [rollbackLoading, setRollbackLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getImportLogs(page + 1, pageSize);
      const data = response?.data || response;
      setLogs(data?.content || []);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل تحميل سجل الاستيراد', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, enqueueSnackbar]);

  useEffect(() => {
    load();
  }, [load]);

  const handleExportErrors = async (log) => {
    try {
      const response = await getImportErrors(log.importBatchId);
      const errors = response?.data || response || [];
      if (!errors.length) {
        enqueueSnackbar('لا توجد أخطاء مسجَّلة لهذه الدفعة', { variant: 'info' });
        return;
      }
      downloadCsv(
        `اخطاء_الاستيراد_${log.importBatchId}.csv`,
        ['الصف', 'الحقل', 'السبب', 'البيانات'],
        errors.map((e) => [e.rowNumber, e.errorField || '-', e.errorMessage, e.rowData || '-'])
      );
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل تصدير الأخطاء', { variant: 'error' });
    }
  };

  const openRollbackDialog = async (log) => {
    setRollbackTarget(log);
    setRollbackReason('');
    setRollbackPreview(null);
    try {
      const response = await previewImportRollback(log.importBatchId);
      setRollbackPreview(response?.data || response);
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل تحميل معاينة التراجع', { variant: 'error' });
      setRollbackTarget(null);
    }
  };

  const confirmRollback = async () => {
    if (!rollbackReason.trim()) {
      enqueueSnackbar('سبب التراجع إلزامي', { variant: 'warning' });
      return;
    }
    setRollbackLoading(true);
    try {
      const response = await executeImportRollback(rollbackTarget.importBatchId, rollbackReason.trim());
      const result = response?.data || response;
      enqueueSnackbar(result?.message || 'تم التراجع بنجاح', { variant: 'success' });
      setRollbackTarget(null);
      load();
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل تنفيذ التراجع', { variant: 'error' });
    } finally {
      setRollbackLoading(false);
    }
  };

  const rows = useMemo(() => logs, [logs]);

  return (
    <MainCard content={false}>
      <ModernPageHeader
        title="سجل استيراد الأعضاء"
        subtitle="كل دفعات استيراد الأعضاء بملف Excel: من نفّذها، متى، وأعدادها. يمكن تصدير الأخطاء أو التراجع عن دفعة كاملة."
        actions={
          <IconButton onClick={load} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        }
      />
      <TableContainer sx={{ maxWidth: '100%', overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>الملف</TableCell>
              <TableCell>المنفّذ</TableCell>
              <TableCell>الوقت</TableCell>
              <TableCell align="center">إجمالي</TableCell>
              <TableCell align="center">جديد</TableCell>
              <TableCell align="center">معدَّل</TableCell>
              <TableCell align="center">أخطاء</TableCell>
              <TableCell align="center">الحالة</TableCell>
              <TableCell align="center">إجراءات</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">لا توجد عمليات استيراد مسجَّلة</Typography>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((log) => (
                <TableRow key={log.id} hover>
                  <TableCell>
                    <Typography variant="body2" noWrap sx={{ maxWidth: 220 }}>
                      {log.fileName || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell>{log.importedByUsername || '-'}</TableCell>
                  <TableCell>
                    {log.createdAt ? new Date(log.createdAt).toLocaleString('ar-EG') : '-'}
                  </TableCell>
                  <TableCell align="center">{log.totalRows ?? 0}</TableCell>
                  <TableCell align="center">{log.createdCount ?? 0}</TableCell>
                  <TableCell align="center">{log.updatedCount ?? 0}</TableCell>
                  <TableCell align="center">
                    {(log.errorCount ?? 0) > 0 ? (
                      <Chip
                        size="small"
                        color="error"
                        icon={<ErrorOutlineIcon fontSize="small" />}
                        label={log.errorCount}
                      />
                    ) : (
                      0
                    )}
                  </TableCell>
                  <TableCell align="center">
                    <Chip size="small" color={STATUS_COLOR[log.status] || 'default'} label={STATUS_LABEL[log.status] || log.status} />
                  </TableCell>
                  <TableCell align="center">
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                      {(log.errorCount ?? 0) > 0 && (
                        <Tooltip title="تصدير الأخطاء">
                          <IconButton size="small" onClick={() => handleExportErrors(log)}>
                            <DownloadIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {canRollback && (log.status === 'COMPLETED' || log.status === 'PARTIAL') && (
                        <Tooltip title="تراجع عن هذا الاستيراد">
                          <IconButton size="small" color="warning" onClick={() => openRollbackDialog(log)}>
                            <UndoIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={totalElements}
        page={page}
        onPageChange={(e, newPage) => setPage(newPage)}
        rowsPerPage={pageSize}
        onRowsPerPageChange={(e) => {
          setPageSize(parseInt(e.target.value, 10));
          setPage(0);
        }}
        labelRowsPerPage="عدد الصفوف"
      />

      <Dialog open={Boolean(rollbackTarget)} onClose={() => (rollbackLoading ? null : setRollbackTarget(null))} maxWidth="sm" fullWidth>
        <DialogTitle>التراجع عن دفعة الاستيراد {rollbackTarget?.importBatchId}</DialogTitle>
        <DialogContent dividers>
          {!rollbackPreview ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress size={28} />
            </Box>
          ) : rollbackPreview.alreadyRolledBack ? (
            <Typography color="error">سبق التراجع عن هذه الدفعة.</Typography>
          ) : (
            <Stack spacing={2}>
              <Typography variant="body2">
                هذه الدفعة أنشأت <b>{rollbackPreview.createdCount}</b> عضو وعدّلت <b>{rollbackPreview.updatedCount}</b> عضو.
              </Typography>
              <Typography variant="body2" color="success.main">
                سيُحذف <b>{rollbackPreview.wouldRevertCreatedCount}</b> عضو أُنشئ حديثاً، وستُعاد <b>{rollbackPreview.wouldRevertUpdatedCount}</b>
                {' '}عضو معدَّل إلى قيمها السابقة.
              </Typography>
              <Typography variant="caption" color="text.secondary">
                التراجع لا يكتب فوق أي تعديل حدث بعد الاستيراد، ويحافظ على السجلات ذات الأثر المالي أو الطبي.
              </Typography>
              {rollbackPreview.wouldSkipCount > 0 && (
                <Box>
                  <Typography variant="body2" color="warning.main" fontWeight="bold">
                    سيُستثنى {rollbackPreview.wouldSkipCount} عضو للحماية التالية:
                  </Typography>
                  <Box sx={{ maxHeight: 150, overflow: 'auto', mt: 1 }}>
                    {rollbackPreview.skips?.map((s) => (
                      <Typography key={s.memberId} variant="caption" display="block">
                        {s.memberName || `#${s.memberId}`} — {ROLLBACK_SKIP_REASON[s.reason] || s.reason || 'سبب حماية غير محدد'}
                      </Typography>
                    ))}
                  </Box>
                </Box>
              )}
              <Divider />
              <TextField
                label="سبب التراجع"
                required
                multiline
                minRows={2}
                value={rollbackReason}
                onChange={(e) => setRollbackReason(e.target.value)}
                disabled={rollbackLoading}
              />
              <Typography variant="caption" color="warning.main">
                العملية لا يمكن إلغاؤها بعد اكتمالها؛ راجع الأعداد والاستثناءات قبل التأكيد.
              </Typography>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRollbackTarget(null)} disabled={rollbackLoading}>
            إلغاء
          </Button>
          {rollbackPreview && !rollbackPreview.alreadyRolledBack && (
            <Button
              variant="contained"
              color="warning"
              disabled={rollbackLoading || !rollbackReason.trim()}
              onClick={confirmRollback}
            >
              {rollbackLoading ? 'جارِ التراجع...' : 'تأكيد التراجع'}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </MainCard>
  );
};

export default MemberImportHistory;
