import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';

// material-ui
import {
  Box,
  Stack,
  Typography,
  TextField,
  Button,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  InputAdornment,
  Tooltip,
  IconButton,
  LinearProgress,
  CardContent,
  Checkbox,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  MenuItem
} from '@mui/material';
import { LoadingButton } from '@mui/lab';

// project imports
import MainCard from 'components/MainCard';
import auditService from 'services/api/audit.service';
import { useTableState } from 'hooks/useTableState';
import { useSnackbar } from 'notistack';

// assets
import {
  Search as SearchIcon,
  FilterAlt as FilterAltIcon,
  Download as DownloadIcon,
  Refresh as RefreshIcon,
  Info as InfoIcon,
  History as HistoryIcon,
  Delete as DeleteIcon,
  Lock as LockIcon
} from '@mui/icons-material';

// ==============================|| MEDICAL AUDIT LOGS PAGE ||============================== //

const MedicalAuditLogs = () => {
  const { enqueueSnackbar } = useSnackbar();
  const tableState = useTableState({ initialPageSize: 20 });
  const [claimId, setClaimId] = useState('');
  const [entityType, setEntityType] = useState('');
  const [entityId, setEntityId] = useState('');
  const [action, setAction] = useState('');
  const [source, setSource] = useState('');
  const [correlationId, setCorrelationId] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);

  // Deletion Password Dialog State
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');

  // Details Dialog State
  const [detailsLog, setDetailsLog] = useState(null);

  const {
    data: logData,
    isPending: isLoading,
    refetch,
    isFetching,
    error: loadError
  } = useQuery({
    queryKey: ['medical-audit-logs', tableState.page, tableState.pageSize, claimId, entityType, entityId, action, source, correlationId, fromDate, toDate],
    queryFn: () =>
      auditService.search({
        page: tableState.page + 1,
        size: tableState.pageSize,
        claimId: claimId || undefined,
        entityType: entityType || undefined,
        entityId: entityId || undefined,
        action: action || undefined,
        source: source || undefined,
        correlationId: correlationId || undefined,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined
      })
  });

  const deleteMutation = useMutation({
    mutationFn: (data) => auditService.deleteBulk(data),
    onSuccess: () => {
      enqueueSnackbar('تم حذف السجلات المحددة بنجاح', { variant: 'success' });
      setSelectedIds([]);
      setDeleteDialogOpen(false);
      setDeletePassword('');
      refetch();
    },
    onError: (err) => {
      enqueueSnackbar(err?.response?.data?.messageAr || err?.message || 'حدث خطأ أثناء الحذف', { variant: 'error' });
    }
  });

  const handleSelectAll = (event) => {
    if (event.target.checked) {
      const allIds = logs.map((log) => log.id);
      setSelectedIds(allIds);
    } else {
      setSelectedIds([]);
    }
  };

  const handleSelectRow = (id) => {
    const selectedIndex = selectedIds.indexOf(id);
    let newSelected = [];

    if (selectedIndex === -1) {
      newSelected = [...selectedIds, id];
    } else {
      newSelected = selectedIds.filter((sid) => sid !== id);
    }

    setSelectedIds(newSelected);
  };

  const handleExport = async () => {
    try {
      const blob = await auditService.exportXlsx({ claimId, entityType, entityId, action, source, correlationId });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `medical_audit_logs_${new Date().toISOString().split('T')[0]}.xlsx`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Export failed', err);
    }
  };

  const resetFilters = () => {
    setClaimId('');
    setEntityType('');
    setEntityId('');
    setAction('');
    setSource('');
    setCorrelationId('');
    setFromDate('');
    setToDate('');
    tableState.setPage(0);
  };

  const confirmDelete = () => {
    if (!deletePassword) {
      enqueueSnackbar('يجب إدخال كلمة المرور لتأكيد الحذف', { variant: 'warning' });
      return;
    }
    deleteMutation.mutate({ ids: selectedIds, password: deletePassword });
  };

  const getActionColor = (action) => {
    const value = String(action || '');
    if (value.includes('VOID') || value.includes('DELETE')) return 'error';
    if (value.includes('APPROVE')) return 'success';
    if (value.includes('CREATE')) return 'primary';
    if (value.includes('REJECT')) return 'warning';
    return 'default';
  };

  const formatTimestamp = (timestamp) => {
    if (!timestamp) return '—';
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('ar-LY');
  };

  const formatJson = (value) => {
    if (!value) return '—';
    try {
      const parsed = typeof value === 'string' ? JSON.parse(value) : value;
      return JSON.stringify(parsed, null, 2);
    } catch {
      return String(value);
    }
  };

  const logs = logData?.items || [];
  const totalCount = logData?.total || 0;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h4" sx={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 1 }}>
          <HistoryIcon color="primary" /> سجل التدقيق الطبي (Audit Trail)
        </Typography>
        <Stack direction="row" spacing={1}>
          {selectedIds.length > 0 && (
            <Button variant="contained" color="error" startIcon={<DeleteIcon />} onClick={() => setDeleteDialogOpen(true)}>
              حذف ({selectedIds.length})
            </Button>
          )}
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => refetch()} disabled={isFetching}>
            تحديث
          </Button>
          <Button variant="contained" color="success" startIcon={<DownloadIcon />} onClick={handleExport}>
            تصدير Excel
          </Button>
        </Stack>
      </Box>

      <MainCard>
        <CardContent sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems={{ xs: 'stretch', lg: 'center' }} flexWrap="wrap">
            <TextField
              label="رقم المطالبة (ID)"
              size="small"
              value={claimId}
              onChange={(e) => setClaimId(e.target.value)}
              sx={{ width: 200 }}
            />
            <TextField
              select
              label="نوع الكيان"
              size="small"
              value={entityType}
              onChange={(e) => setEntityType(e.target.value)}
              sx={{ width: 190 }}
              disabled={Boolean(claimId)}
              helperText={claimId ? 'رقم المطالبة يحدد CLAIM تلقائياً' : ' '}
            >
              <MenuItem value="">كل الكيانات</MenuItem>
              <MenuItem value="CLAIM">مطالبة</MenuItem>
              <MenuItem value="CLAIM_LINE">بند مطالبة</MenuItem>
              <MenuItem value="PREAUTHORIZATION">موافقة مسبقة</MenuItem>
              <MenuItem value="MEMBER">مستفيد</MenuItem>
              <MenuItem value="SETTLEMENT">تسوية</MenuItem>
              <MenuItem value="SYSTEM_SETTING">إعداد نظام</MenuItem>
              <MenuItem value="USER_SESSION">جلسة مستخدم</MenuItem>
              <MenuItem value="SIMULATION_RUN">تشغيل محاكاة</MenuItem>
            </TextField>
            <TextField
              label="معرف الكيان"
              size="small"
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              sx={{ width: 180 }}
              disabled={Boolean(claimId)}
            />
            <TextField select label="الإجراء" size="small" value={action} onChange={(e) => setAction(e.target.value)} sx={{ width: 170 }}>
              <MenuItem value="">كل الإجراءات</MenuItem>
              <MenuItem value="CREATED">إنشاء</MenuItem>
              <MenuItem value="UPDATED">تعديل</MenuItem>
              <MenuItem value="STATUS_CHANGE">تغيير حالة</MenuItem>
              <MenuItem value="APPROVED">اعتماد</MenuItem>
              <MenuItem value="REJECTED">رفض</MenuItem>
              <MenuItem value="RECALCULATION">إعادة حساب</MenuItem>
              <MenuItem value="MANUAL_OVERRIDE">تدخل يدوي</MenuItem>
              <MenuItem value="CLAIM_VOIDED">عكس/إلغاء مطالبة</MenuItem>
              <MenuItem value="SIMULATION_EXECUTED">تشغيل محاكاة</MenuItem>
            </TextField>
            <TextField select label="المصدر" size="small" value={source} onChange={(e) => setSource(e.target.value)} sx={{ width: 150 }}>
              <MenuItem value="">كل المصادر</MenuItem>
              <MenuItem value="USER">مستخدم</MenuItem>
              <MenuItem value="SYSTEM">النظام</MenuItem>
              <MenuItem value="API">API</MenuItem>
            </TextField>
            <TextField
              label="معرف الارتباط (Correlation ID)"
              size="small"
              value={correlationId}
              onChange={(e) => setCorrelationId(e.target.value)}
              sx={{ flexGrow: 1 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="disabled" />
                  </InputAdornment>
                )
              }}
            />
            <TextField
              type="date"
              label="من تاريخ"
              size="small"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 160 }}
            />
            <TextField
              type="date"
              label="إلى تاريخ"
              size="small"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 160 }}
            />
            <Button variant="contained" startIcon={<FilterAltIcon />} onClick={() => tableState.setPage(0)}>
              تصفية
            </Button>
            <Button variant="outlined" onClick={resetFilters}>
              إعادة تعيين
            </Button>
          </Stack>
        </CardContent>
      </MainCard>

      {loadError && (
        <Alert severity="error">
          {loadError?.message || 'تعذر تحميل سجل التدقيق الطبي. تحقق من صلاحيات المستخدم أو اتصال الخادم.'}
        </Alert>
      )}

      <TableContainer component={Paper} sx={{ borderRadius: 2, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        {isFetching && <LinearProgress sx={{ height: 2 }} />}
        <Table sx={{ minWidth: 650, direction: 'rtl' }}>
          <TableHead sx={{ bgcolor: 'grey.50' }}>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  indeterminate={selectedIds.length > 0 && selectedIds.length < logs.length}
                  checked={logs.length > 0 && selectedIds.length === logs.length}
                  onChange={handleSelectAll}
                />
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                التاريخ والوقت
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                الإجراء
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                الكيان
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                المعرف
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                المستخدم
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                الدور
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                السبب
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 'bold' }}>
                التفاصيل
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={10} align="center" sx={{ py: 5 }}>
                  جاري تحميل سجلات التدقيق...
                </TableCell>
              </TableRow>
            ) : logs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} align="center" sx={{ py: 10 }}>
                  <Typography color="text.secondary">لا توجد سجلات تدقيق مطابقة للبحث</Typography>
                </TableCell>
              </TableRow>
            ) : (
              logs.map((log) => {
                const isItemSelected = selectedIds.indexOf(log.id) !== -1;
                return (
                  <TableRow key={log.id} hover selected={isItemSelected} onClick={() => handleSelectRow(log.id)} sx={{ cursor: 'pointer' }}>
                    <TableCell padding="checkbox">
                      <Checkbox checked={isItemSelected} />
                    </TableCell>
                    <TableCell align="right" dir="ltr" sx={{ fontSize: '0.8rem' }}>
                      {formatTimestamp(log.timestamp)}
                    </TableCell>
                    <TableCell align="right">
                      <Chip
                        label={log.action}
                        size="small"
                        color={getActionColor(log.action)}
                        variant="tonal"
                        sx={{ fontWeight: 'bold' }}
                      />
                    </TableCell>
                    <TableCell align="right">{log.entityType}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                      {log.entityId}
                    </TableCell>
                    <TableCell align="right">{log.userId === 0 ? 'النظام' : `مستخدم #${log.userId}`}</TableCell>
                    <TableCell align="right">
                      <Typography variant="caption" sx={{ bgcolor: 'grey.200', px: 1, borderRadius: 1 }}>
                        {log.role}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {log.reason || '—'}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="عرض التفاصيل التقنية">
                        <IconButton
                          size="small"
                          color="info"
                          onClick={(e) => {
                            e.stopPropagation();
                            setDetailsLog(log);
                          }}
                        >
                          <InfoIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            إجمالي السجلات: {totalCount}
          </Typography>
        </Box>
      </TableContainer>

      {/* Password Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'error.main' }}>
          <DeleteIcon /> تأكيد حذف سجلات التدقيق
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="warning">سيتم حذف {selectedIds.length} سجلات نهائياً من النظام. هذا الإجراء غير قابل للتراجع.</Alert>
            <Typography variant="body2" color="text.secondary">
              يرجى إدخال كلمة مرورك للمتابعة:
            </Typography>
            <TextField
              fullWidth
              type="password"
              label="كلمة المرور"
              value={deletePassword}
              onChange={(e) => setDeletePassword(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockIcon fontSize="small" />
                  </InputAdornment>
                )
              }}
              autoFocus
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2, pt: 0 }}>
          <Button onClick={() => setDeleteDialogOpen(false)} color="inherit">
            إلغاء
          </Button>
          <LoadingButton onClick={confirmDelete} variant="contained" color="error" loading={deleteMutation.isPending}>
            حذف نهائي
          </LoadingButton>
        </DialogActions>
      </Dialog>

      {/* Details Dialog — shows before/after JSON snapshot of the audited entity */}
      <Dialog open={Boolean(detailsLog)} onClose={() => setDetailsLog(null)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <InfoIcon color="info" /> تفاصيل سجل التدقيق #{detailsLog?.id}
        </DialogTitle>
        <DialogContent>
          {detailsLog && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Stack direction="row" spacing={2} flexWrap="wrap">
                <Typography variant="body2">
                  <b>الإجراء:</b> {detailsLog.action}
                </Typography>
                <Typography variant="body2">
                  <b>الكيان:</b> {detailsLog.entityType} #{detailsLog.entityId}
                </Typography>
                <Typography variant="body2">
                  <b>التاريخ:</b> {formatTimestamp(detailsLog.timestamp)}
                </Typography>
                <Typography variant="body2">
                  <b>معرف الارتباط:</b> {detailsLog.correlationId || '—'}
                </Typography>
              </Stack>
              {detailsLog.reason && (
                <Typography variant="body2">
                  <b>السبب:</b> {detailsLog.reason}
                </Typography>
              )}
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    الحالة قبل التغيير
                  </Typography>
                  <Box
                    component="pre"
                    sx={{
                      p: 1.5,
                      bgcolor: 'grey.100',
                      borderRadius: 1,
                      fontSize: '0.75rem',
                      overflow: 'auto',
                      maxHeight: 320,
                      direction: 'ltr',
                      textAlign: 'left'
                    }}
                  >
                    {formatJson(detailsLog.beforeState)}
                  </Box>
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    الحالة بعد التغيير
                  </Typography>
                  <Box
                    component="pre"
                    sx={{
                      p: 1.5,
                      bgcolor: 'grey.100',
                      borderRadius: 1,
                      fontSize: '0.75rem',
                      overflow: 'auto',
                      maxHeight: 320,
                      direction: 'ltr',
                      textAlign: 'left'
                    }}
                  >
                    {formatJson(detailsLog.afterState)}
                  </Box>
                </Box>
              </Stack>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2, pt: 0 }}>
          <Button onClick={() => setDetailsLog(null)}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default MedicalAuditLogs;
