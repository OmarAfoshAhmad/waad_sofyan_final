import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Grid,
  Card,
  CardContent,
  CardHeader,
  Typography,
  Divider,
  Button,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Chip,
  Alert,
  CircularProgress,
  Stack,
  Tooltip,
  LinearProgress,
  Switch,
  FormControlLabel
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import PersonIcon from '@mui/icons-material/Person';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import WarningIcon from '@mui/icons-material/Warning';
import BalanceIcon from '@mui/icons-material/Balance';
import SettingsIcon from '@mui/icons-material/Settings';

import { reviewerPreAuthService, preApprovalsService } from 'services/api';
import { useSnackbar } from 'notistack';
import { useReviewer } from 'contexts/ReviewerContext';

const REJECTION_REASONS = [
  { value: 'NOT_COVERED', label: 'غير مغطى ضمن الوثيقة' },
  { value: 'COSMETIC', label: 'إجراء تجميلي غير مشمول' },
  { value: 'LIMIT_EXCEEDED', label: 'تجاوز الحد الأقصى للوثيقة' },
  { value: 'MEDICALLY_UNJUSTIFIED', label: 'غير مبرر طبياً' },
  { value: 'PRICE_REJECTED', label: 'السعر مرتفع بشكل غير مقبول' },
  { value: 'DUPLICATE_REQUEST', label: 'طلب مكرر' },
  { value: 'EXPIRED_POLICY', label: 'وثيقة منتهية الصلاحية' },
  { value: 'OTHER', label: 'سبب آخر' }
];

const PRIORITY_COLORS = { EMERGENCY: 'error', URGENT: 'warning', ROUTINE: 'info', ELECTIVE: 'default' };
const PRIORITY_LABELS = { EMERGENCY: 'طارئ جداً', URGENT: 'عاجل', ROUTINE: 'روتيني', ELECTIVE: 'اختياري' };

const STATUS_COLORS = { PENDING: 'warning', UNDER_REVIEW: 'info', APPROVED: 'success', REJECTED: 'error', CANCELLED: 'default', PARTIALLY_APPROVED: 'success' };
const STATUS_LABELS = { PENDING: 'قيد الانتظار', UNDER_REVIEW: 'قيد المراجعة', APPROVED: 'موافق عليه', REJECTED: 'مرفوض', CANCELLED: 'ملغى', PARTIALLY_APPROVED: 'موافقة جزئية' };

const LINE_STATUS_COLORS = { PENDING: 'warning', APPROVED: 'success', PARTIALLY_APPROVED: 'info', REJECTED: 'error' };

const PreAuthReviewPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();
  const { inlineEditing, setInlineEditing, audioEnabled, setAudioEnabled } = useReviewer();

  // Data state
  const [request, setRequest] = useState(null);
  const [lines, setLines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Dialog states
  const [dialogType, setDialogType] = useState(null); // 'reject_all' | 'info' | 'line_decision'
  const [notes, setNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  
  // Line Decision State
  const [selectedLine, setSelectedLine] = useState(null);
  const [lineDecisionType, setLineDecisionType] = useState('APPROVED');
  const [lineApprovedAmount, setLineApprovedAmount] = useState('');

  // Fetch request and lines
  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      // Fetch request details using standard service, or if reviewer service has it
      const data = await preApprovalsService.getById(id);
      setRequest(data);
      
      // Fetch line details
      const linesData = await reviewerPreAuthService.getLines(id);
      setLines(linesData || data.lines || []);
    } catch (err) {
      console.error('Failed to load request:', err);
      setError(err?.userMessage || err?.message || 'فشل في تحميل بيانات الطلب');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) fetchData();
  }, [fetchData, id]);

  const handleStartReview = async () => {
    try {
      setActionLoading(true);
      await reviewerPreAuthService.startReview(id);
      enqueueSnackbar('تم بدء المراجعة بنجاح - الحالة: قيد المراجعة', { variant: 'info' });
      fetchData();
    } catch (err) {
      enqueueSnackbar(err?.userMessage || err?.message || 'فشل بدء المراجعة', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleFinalize = async () => {
    try {
      setActionLoading(true);
      await reviewerPreAuthService.finalizeReview(id);
      enqueueSnackbar('✅ تم إنهاء المراجعة بنجاح!', { variant: 'success' });
      navigate('/pre-approvals');
    } catch (err) {
      enqueueSnackbar(err?.userMessage || err?.message || 'فشل في إنهاء المراجعة', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectAll = async () => {
    if (!rejectionReason) {
      enqueueSnackbar('يرجى تحديد سبب الرفض', { variant: 'warning' });
      return;
    }
    try {
      setActionLoading(true);
      const fullReason = `${rejectionReason}${notes ? ' - ' + notes : ''}`;
      await reviewerPreAuthService.rejectAll(id, fullReason);
      enqueueSnackbar('❌ تم رفض الطلب وإخطار مقدم الخدمة', { variant: 'info' });
      setDialogType(null);
      navigate('/pre-approvals');
    } catch (err) {
      enqueueSnackbar(err?.userMessage || err?.message || 'فشل في رفض الطلب', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const submitLineDecision = async (lineId, decisionData) => {
    try {
      setActionLoading(true);
      await reviewerPreAuthService.makeLineDecision(id, lineId, decisionData);
      enqueueSnackbar('تم حفظ القرار للسطر', { variant: 'success' });
      // Refresh lines
      const linesData = await reviewerPreAuthService.getLines(id);
      setLines(linesData || []);
    } catch (err) {
      enqueueSnackbar(err?.userMessage || err?.message || 'فشل في حفظ القرار', { variant: 'error' });
    } finally {
      setActionLoading(false);
      setDialogType(null);
      setSelectedLine(null);
    }
  };

  const handleLineDecisionModalConfirm = () => {
    if (!selectedLine) return;
    
    if (lineDecisionType !== 'APPROVED' && !notes) {
      enqueueSnackbar('الملاحظات مطلوبة عند الرفض أو الموافقة الجزئية', { variant: 'warning' });
      return;
    }

    const decisionData = {
      decision: lineDecisionType,
      approvedAmount: lineDecisionType === 'PARTIALLY_APPROVED' ? parseFloat(lineApprovedAmount) : null,
      reviewerNotes: notes
    };
    submitLineDecision(selectedLine.id, decisionData);
  };

  const handleInlineAction = (line, actionType) => {
    if (actionType === 'APPROVED') {
      submitLineDecision(line.id, { decision: 'APPROVED', reviewerNotes: 'موافق عليه بالكامل' });
    } else {
      // For Partial or Reject, even if inline is enabled, we need input for amount or notes
      // So we fallback to modal for them to ensure clean UX
      openLineDecisionModal(line, actionType);
    }
  };

  const openLineDecisionModal = (line, type) => {
    setSelectedLine(line);
    setLineDecisionType(type);
    setLineApprovedAmount(type === 'PARTIALLY_APPROVED' ? String(line.manualPrice || line.contractPrice || '') : '');
    setNotes('');
    setDialogType('line_decision');
  };

  if (loading)
    return (
      <Box sx={{ p: 4 }}>
        <LinearProgress />
        <Typography color="text.secondary" mt={2} textAlign="center">جاري تحميل بيانات الطلب...</Typography>
      </Box>
    );

  if (error)
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error" action={<Button onClick={fetchData}>إعادة المحاولة</Button>}>{error}</Alert>
      </Box>
    );

  if (!request) return <Box sx={{ p: 4 }}><Alert severity="warning">الطلب غير موجود.</Alert></Box>;

  const isPending = ['PENDING', 'UNDER_REVIEW'].includes(request.status);
  const canStartReview = request.status === 'PENDING';
  const allLinesDecided = lines.length > 0 && lines.every(l => l.status !== 'PENDING');

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton onClick={() => navigate('/pre-approvals')} color="primary"><ArrowBackIcon /></IconButton>
          <Box>
            <Typography variant="h4" fontWeight="bold">مراجعة الطلب: {request.referenceNumber || `#${request.id}`}</Typography>
            <Box sx={{ display: 'flex', gap: 1, mt: 0.5, alignItems: 'center' }}>
              <Chip label={STATUS_LABELS[request.status] || request.status} color={STATUS_COLORS[request.status] || 'default'} size="small" />
              {request.priority && <Chip label={PRIORITY_LABELS[request.priority] || request.priority} color={PRIORITY_COLORS[request.priority] || 'default'} size="small" variant="outlined" />}
            </Box>
          </Box>
        </Box>

        <Stack direction="row" spacing={2} alignItems="center">
          <Tooltip title="إعدادات المراجع">
            <IconButton size="small" color="primary">
              <SettingsIcon />
            </IconButton>
          </Tooltip>
          <FormControlLabel
            control={<Switch size="small" checked={inlineEditing} onChange={(e) => setInlineEditing(e.target.checked)} />}
            label={<Typography variant="caption">تعديل مباشر (Inline)</Typography>}
          />
          <FormControlLabel
            control={<Switch size="small" checked={audioEnabled} onChange={(e) => setAudioEnabled(e.target.checked)} />}
            label={<Typography variant="caption">تنبيهات صوتية</Typography>}
          />
        </Stack>
      </Box>

      <Grid container spacing={3}>
        {/* Left: Details and Lines */}
        <Grid item xs={12} md={8}>
          {/* Member Info */}
          <Card sx={{ mb: 3 }}>
            <CardHeader avatar={<PersonIcon color="primary" />} title="بيانات المستفيد" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">المستفيد</Typography>
                  <Typography fontWeight="bold">{request.memberName || request.memberFullName || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">مقدم الخدمة</Typography>
                  <Typography fontWeight="bold">{request.providerName || '-'}</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* Clinical Info */}
          <Card sx={{ mb: 3 }}>
            <CardHeader avatar={<MedicalServicesIcon color="secondary" />} title="البيانات السريرية" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
            <CardContent>
              <Grid container spacing={2}>
                {request.diagnosis && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">التشخيص</Typography>
                    <Typography fontWeight="bold">{request.diagnosis}</Typography>
                  </Grid>
                )}
                {request.clinicalNotes && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">الملاحظات الطبية</Typography>
                    <Typography color="text.secondary">{request.clinicalNotes}</Typography>
                  </Grid>
                )}
              </Grid>
            </CardContent>
          </Card>

          {/* Service Lines Table */}
          <Card>
            <CardHeader title="الخدمات والتدقيق المالي" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
            <CardContent sx={{ p: 0 }}>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: 'grey.100' }}>
                      <TableCell><strong>الخدمة</strong></TableCell>
                      <TableCell align="center"><strong>الكود</strong></TableCell>
                      <TableCell align="right"><strong>المطلوب</strong></TableCell>
                      <TableCell align="right"><strong>المعتمد</strong></TableCell>
                      <TableCell align="center"><strong>الحالة</strong></TableCell>
                      {isPending && <TableCell align="center"><strong>الإجراءات</strong></TableCell>}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {lines.map((line, idx) => (
                      <TableRow key={idx} hover sx={{ bgcolor: line.status !== 'PENDING' ? 'grey.50' : 'inherit' }}>
                        <TableCell>
                          <Typography variant="body2" fontWeight="bold">{line.serviceName || line.name || '-'}</Typography>
                          {line.reviewerNotes && <Typography variant="caption" color="text.secondary" display="block">ملاحظة: {line.reviewerNotes}</Typography>}
                        </TableCell>
                        <TableCell align="center"><Chip label={line.serviceCode || line.code || '-'} size="small" variant="outlined" /></TableCell>
                        <TableCell align="right">{line.requestedAmount || line.manualPrice ? `${Number(line.requestedAmount || line.manualPrice).toLocaleString()} د.ل` : '—'}</TableCell>
                        <TableCell align="right">
                          {line.status === 'PENDING' ? '—' : 
                            <Typography fontWeight="bold" color={line.status === 'REJECTED' ? 'error' : 'success.main'}>
                              {line.approvedAmount !== null ? `${Number(line.approvedAmount).toLocaleString()} د.ل` : '—'}
                            </Typography>
                          }
                        </TableCell>
                        <TableCell align="center">
                          <Chip label={STATUS_LABELS[line.status] || line.status} color={LINE_STATUS_COLORS[line.status] || 'default'} size="small" />
                        </TableCell>
                        {isPending && (
                          <TableCell align="center">
                            {line.status === 'PENDING' ? (
                              <Stack direction="row" spacing={0.5} justifyContent="center">
                                <Tooltip title="موافقة كلية">
                                  <IconButton size="small" color="success" disabled={actionLoading || canStartReview} 
                                    onClick={() => inlineEditing ? handleInlineAction(line, 'APPROVED') : openLineDecisionModal(line, 'APPROVED')}>
                                    <CheckCircleIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="موافقة جزئية (تعديل السعر)">
                                  <IconButton size="small" color="info" disabled={actionLoading || canStartReview} 
                                    onClick={() => openLineDecisionModal(line, 'PARTIALLY_APPROVED')}>
                                    <BalanceIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="رفض الخدمة">
                                  <IconButton size="small" color="error" disabled={actionLoading || canStartReview} 
                                    onClick={() => openLineDecisionModal(line, 'REJECTED')}>
                                    <CancelIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              </Stack>
                            ) : (
                              <Button size="small" disabled={actionLoading} onClick={() => openLineDecisionModal(line, line.status)}>
                                تعديل القرار
                              </Button>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Right: Actions Panel */}
        <Grid item xs={12} md={4}>
          <Card sx={{ mb: 3, border: isPending ? '2px solid' : undefined, borderColor: 'primary.main' }}>
            <CardHeader title="لوحة القرار" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold', color: 'primary.main' }} />
            <CardContent>
              {isPending ? (
                <Stack spacing={2}>
                  {canStartReview ? (
                    <>
                      <Alert severity="info" variant="outlined">يجب بدء المراجعة لتفعيل أزرار اتخاذ القرار.</Alert>
                      <Button fullWidth variant="contained" color="info" onClick={handleStartReview} disabled={actionLoading} startIcon={<AssignmentTurnedInIcon />}>
                        بدء المراجعة
                      </Button>
                    </>
                  ) : (
                    <>
                      <Alert severity={allLinesDecided ? "success" : "warning"} variant="outlined">
                        {allLinesDecided ? "تم تدقيق جميع الخدمات. يمكنك الآن إنهاء المراجعة." : "يرجى اتخاذ قرار لكل خدمة في الجدول على اليمين."}
                      </Alert>
                      <Button fullWidth variant="contained" color="success" size="large" onClick={handleFinalize} disabled={actionLoading || !allLinesDecided} startIcon={<CheckCircleIcon />}>
                        إنهاء المراجعة (إرسال)
                      </Button>
                      <Divider />
                      <Button fullWidth variant="outlined" color="error" onClick={() => { setRejectionReason(''); setDialogType('reject_all'); }} startIcon={<CancelIcon />} disabled={actionLoading}>
                        رفض كلي للطلب
                      </Button>
                    </>
                  )}
                </Stack>
              ) : (
                <Box textAlign="center" py={2}>
                  <Chip icon={request.status === 'APPROVED' ? <CheckCircleIcon /> : <CancelIcon />} label={STATUS_LABELS[request.status] || request.status} color={STATUS_COLORS[request.status] || 'default'} sx={{ fontSize: '1rem', py: 2, px: 1 }} />
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* ===== REJECT ALL DIALOG ===== */}
      <Dialog open={dialogType === 'reject_all'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ bgcolor: 'error.main', color: 'white', display: 'flex', alignItems: 'center', gap: 1 }}>
          <CancelIcon /> تأكيد الرفض الكلي للطلب
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Alert severity="warning" sx={{ mb: 2 }}>سيتم رفض جميع الخدمات في هذا الطلب وإخطار المستشفى.</Alert>
          <TextField select fullWidth required label="سبب الرفض *" value={rejectionReason} onChange={(e) => setRejectionReason(e.target.value)} sx={{ mb: 2 }}>
            {REJECTION_REASONS.map((r) => (<MenuItem key={r.value} value={r.value}>{r.label}</MenuItem>))}
          </TextField>
          <TextField fullWidth multiline rows={3} label="تفاصيل إضافية (اختياري)" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>إلغاء</Button>
          <Button variant="contained" color="error" onClick={handleRejectAll} disabled={actionLoading || !rejectionReason} startIcon={<CancelIcon />}>تأكيد الرفض</Button>
        </DialogActions>
      </Dialog>

      {/* ===== LINE DECISION DIALOG ===== */}
      <Dialog open={dialogType === 'line_decision'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, bgcolor: lineDecisionType === 'REJECTED' ? 'error.main' : (lineDecisionType === 'APPROVED' ? 'success.main' : 'info.main'), color: 'white' }}>
          {lineDecisionType === 'REJECTED' ? <CancelIcon /> : (lineDecisionType === 'APPROVED' ? <CheckCircleIcon /> : <BalanceIcon />)} 
          {lineDecisionType === 'REJECTED' ? 'رفض الخدمة' : (lineDecisionType === 'APPROVED' ? 'موافقة على الخدمة' : 'موافقة جزئية وتعديل السعر')}
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Typography variant="subtitle1" fontWeight="bold" gutterBottom>{selectedLine?.serviceName || selectedLine?.name}</Typography>
          
          {lineDecisionType === 'PARTIALLY_APPROVED' && (
            <TextField 
              fullWidth required type="number" label="المبلغ المعتمد الجديد" 
              value={lineApprovedAmount} onChange={(e) => setLineApprovedAmount(e.target.value)} sx={{ mb: 2, mt: 1 }}
              helperText={`السعر المطلوب كان: ${selectedLine?.requestedAmount || selectedLine?.manualPrice || 0} د.ل`}
            />
          )}

          <TextField fullWidth required={lineDecisionType !== 'APPROVED'} multiline rows={3} label="الملاحظات الطبية أو الإدارية" value={notes} onChange={(e) => setNotes(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>إلغاء</Button>
          <Button variant="contained" color={lineDecisionType === 'REJECTED' ? 'error' : 'primary'} onClick={handleLineDecisionModalConfirm} disabled={actionLoading}>
            حفظ القرار
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PreAuthReviewPage;
