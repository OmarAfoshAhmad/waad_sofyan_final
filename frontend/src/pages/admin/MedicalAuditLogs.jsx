import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  MenuItem
} from '@mui/material';
// project imports
import MainCard from 'components/MainCard';
import auditService from 'services/api/audit.service';
import providersService from 'services/api/providers.service';
import { getEmployerSelectors } from 'services/api/employers.service';
import { useTableState } from 'hooks/useTableState';
import { useSnackbar } from 'notistack';

// assets
import {
  Search as SearchIcon,
  FilterAlt as FilterAltIcon,
  Download as DownloadIcon,
  Refresh as RefreshIcon,
  Info as InfoIcon,
  History as HistoryIcon
} from '@mui/icons-material';

// ==============================|| MEDICAL AUDIT LOGS PAGE ||============================== //

const MedicalAuditLogs = () => {
  const { enqueueSnackbar } = useSnackbar();
  const tableState = useTableState({ initialPageSize: 20 });
  const [claimId, setClaimId] = useState('');
  const [entityType, setEntityType] = useState('');
  const [entityId, setEntityId] = useState('');
  const [providerId, setProviderId] = useState('');
  const [employerId, setEmployerId] = useState('');
  const [action, setAction] = useState('');
  const [source, setSource] = useState('');
  const [correlationId, setCorrelationId] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const { data: providerOptions = [] } = useQuery({
    queryKey: ['medical-audit-provider-options'],
    queryFn: () => providersService.getSelector(),
    staleTime: 5 * 60 * 1000
  });

  const { data: employerOptions = [] } = useQuery({
    queryKey: ['medical-audit-employer-options'],
    queryFn: () => getEmployerSelectors(),
    staleTime: 5 * 60 * 1000
  });

  // Details Dialog State
  const [detailsLog, setDetailsLog] = useState(null);

  const {
    data: logData,
    isPending: isLoading,
    refetch,
    isFetching,
    error: loadError
  } = useQuery({
    queryKey: [
      'medical-audit-logs',
      tableState.page,
      tableState.pageSize,
      claimId,
      entityType,
      entityId,
      providerId,
      employerId,
      action,
      source,
      correlationId,
      fromDate,
      toDate
    ],
    queryFn: () =>
      auditService.search({
        page: tableState.page + 1,
        size: tableState.pageSize,
        claimId: claimId || undefined,
        entityType: entityType || undefined,
        entityId: entityId || undefined,
        providerId: providerId || undefined,
        employerId: employerId || undefined,
        action: action || undefined,
        source: source || undefined,
        correlationId: correlationId || undefined,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined
      })
  });

  const handleExport = async () => {
    try {
      const blob = await auditService.exportXlsx({ claimId, entityType, entityId, providerId, employerId, action, source, correlationId });
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
    setProviderId('');
    setEmployerId('');
    setAction('');
    setSource('');
    setCorrelationId('');
    setFromDate('');
    setToDate('');
    tableState.setPage(0);
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
              <MenuItem value="VISIT">زيارة / سجل طبي</MenuItem>
              <MenuItem value="PROVIDER">مقدم خدمة</MenuItem>
              <MenuItem value="PROVIDER_CONTRACT">عقد مقدم خدمة</MenuItem>
              <MenuItem value="MEDICAL_REVIEWER_PROVIDER">ربط مراجع بمقدم خدمة</MenuItem>
              <MenuItem value="FEATURE_FLAG">ميزة نظام</MenuItem>
              <MenuItem value="EMPLOYER">جهة عمل</MenuItem>
              <MenuItem value="EMPLOYER_CONTRACT">وثيقة / عقد جهة عمل</MenuItem>
              <MenuItem value="PRICE_LIST">قائمة أسعار</MenuItem>
              <MenuItem value="MEDICAL_DICTIONARY">القاموس الطبي</MenuItem>
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
              <MenuItem value="VIEW">عرض</MenuItem>
              <MenuItem value="CREATED">إنشاء</MenuItem>
              <MenuItem value="UPDATED">تعديل</MenuItem>
              <MenuItem value="DELETED">حذف</MenuItem>
              <MenuItem value="RESTORED">استعادة</MenuItem>
              <MenuItem value="ACTIVATED">تفعيل</MenuItem>
              <MenuItem value="SUSPENDED">إيقاف مؤقت</MenuItem>
              <MenuItem value="TERMINATED">إنهاء</MenuItem>
              <MenuItem value="IMPORTED">استيراد</MenuItem>
              <MenuItem value="EXPORTED">تصدير</MenuItem>
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
              select
              label="المنشأة (مقدم الخدمة)"
              size="small"
              value={providerId}
              onChange={(e) => setProviderId(e.target.value)}
              sx={{ width: 200 }}
            >
              <MenuItem value="">كل المنشآت</MenuItem>
              {providerOptions.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="الشركة (جهة العمل)"
              size="small"
              value={employerId}
              onChange={(e) => setEmployerId(e.target.value)}
              sx={{ width: 200 }}
            >
              <MenuItem value="">كل الشركات</MenuItem>
              {employerOptions.map((e) => (
                <MenuItem key={e.id} value={e.id}>
                  {e.label || e.name}
                </MenuItem>
              ))}
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
                المنشأة
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                الشركة
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
                <TableCell colSpan={11} align="center" sx={{ py: 5 }}>
                  جاري تحميل سجلات التدقيق...
                </TableCell>
              </TableRow>
            ) : logs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={11} align="center" sx={{ py: 10 }}>
                  <Typography color="text.secondary">لا توجد سجلات تدقيق مطابقة للبحث</Typography>
                </TableCell>
              </TableRow>
            ) : (
              logs.map((log) => (
                  <TableRow key={log.id} hover>
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
                    <TableCell align="right">{log.facilityName || '—'}</TableCell>
                    <TableCell align="right">{log.companyName || '—'}</TableCell>
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
              ))
            )}
          </TableBody>
        </Table>
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            إجمالي السجلات: {totalCount}
          </Typography>
        </Box>
      </TableContainer>

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
