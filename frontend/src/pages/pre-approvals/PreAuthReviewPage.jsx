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
  Paper,
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
  LinearProgress
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import PersonIcon from '@mui/icons-material/Person';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import WarningIcon from '@mui/icons-material/Warning';
import MainCard from 'components/MainCard';
import { preApprovalsService } from 'services/api';
import { useSnackbar } from 'notistack';

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

const PRIORITY_COLORS = {
  EMERGENCY: 'error',
  URGENT: 'warning',
  ROUTINE: 'info',
  ELECTIVE: 'default'
};

const PRIORITY_LABELS = {
  EMERGENCY: 'طارئ جداً',
  URGENT: 'عاجل',
  ROUTINE: 'روتيني',
  ELECTIVE: 'اختياري'
};

const STATUS_COLORS = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default'
};

const STATUS_LABELS = {
  PENDING: 'قيد الانتظار',
  UNDER_REVIEW: 'قيد المراجعة',
  APPROVED: 'معتمدة',
  REJECTED: 'مرفوضة',
  CANCELLED: 'ملغاة'
};

const PreAuthReviewPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  // Data state
  const [request, setRequest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Dialog state
  const [dialogType, setDialogType] = useState(null); // 'approve' | 'reject' | 'info'
  const [notes, setNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [approvedAmount, setApprovedAmount] = useState('');

  // Fetch request details
  const fetchRequest = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await preApprovalsService.getById(id);
      setRequest(data);
      // Pre-fill approved amount
      if (data?.requestedAmount) setApprovedAmount(String(data.requestedAmount));
    } catch (err) {
      console.error('Failed to load request:', err);
      setError(err?.message || 'فشل في تحميل بيانات الطلب');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) fetchRequest();
  }, [fetchRequest, id]);

  // Start review (change status to UNDER_REVIEW if PENDING)
  const handleStartReview = async () => {
    try {
      setActionLoading(true);
      await preApprovalsService.startReview(id);
      enqueueSnackbar('تم بدء المراجعة بنجاح - الحالة: قيد المراجعة', { variant: 'info' });
      fetchRequest();
    } catch (err) {
      enqueueSnackbar(err?.message || 'فشل بدء المراجعة', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  // Approve
  const handleApprove = async () => {
    try {
      setActionLoading(true);
      await preApprovalsService.approve(id, { approvalNotes: notes });
      enqueueSnackbar('✅ تمت الموافقة على الطلب بنجاح!', { variant: 'success' });
      setDialogType(null);
      navigate('/pre-approvals');
    } catch (err) {
      enqueueSnackbar(err?.message || 'فشل في اعتماد الطلب', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  // Reject
  const handleReject = async () => {
    if (!rejectionReason) {
      enqueueSnackbar('يرجى تحديد سبب الرفض', { variant: 'warning' });
      return;
    }
    try {
      setActionLoading(true);
      await preApprovalsService.reject(id, { rejectionReason, rejectionNotes: notes });
      enqueueSnackbar('❌ تم رفض الطلب وإخطار مقدم الخدمة', { variant: 'info' });
      setDialogType(null);
      navigate('/pre-approvals');
    } catch (err) {
      enqueueSnackbar(err?.message || 'فشل في رفض الطلب', { variant: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  if (loading)
    return (
      <Box sx={{ p: 4 }}>
        <LinearProgress />
        <Typography color="text.secondary" mt={2} textAlign="center">
          جاري تحميل بيانات الطلب...
        </Typography>
      </Box>
    );

  if (error)
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error" action={<Button onClick={fetchRequest}>إعادة المحاولة</Button>}>
          {error}
        </Alert>
      </Box>
    );

  if (!request)
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="warning">الطلب غير موجود أو تعذر تحميله.</Alert>
      </Box>
    );

  const isPending = ['PENDING', 'UNDER_REVIEW', 'APPROVAL_IN_PROGRESS'].includes(request.status);
  const canStartReview = request.status === 'PENDING';

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton onClick={() => navigate('/pre-approvals')} color="primary">
            <ArrowBackIcon />
          </IconButton>
          <Box>
            <Typography variant="h4" fontWeight="bold">
              مراجعة الطلب: {request.referenceNumber || `#${request.id}`}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, mt: 0.5, alignItems: 'center' }}>
              <Chip
                label={STATUS_LABELS[request.status] || request.status}
                color={STATUS_COLORS[request.status] || 'default'}
                size="small"
              />
              {request.priority && (
                <Chip
                  label={PRIORITY_LABELS[request.priority] || request.priority}
                  color={PRIORITY_COLORS[request.priority] || 'default'}
                  size="small"
                  variant="outlined"
                />
              )}
            </Box>
          </Box>
        </Box>

        {/* Action buttons - visible only if pending */}
        {isPending && (
          <Stack direction="row" spacing={1}>
            {canStartReview && (
              <Button
                variant="outlined"
                color="info"
                onClick={handleStartReview}
                disabled={actionLoading}
                startIcon={<AssignmentTurnedInIcon />}
              >
                بدء المراجعة
              </Button>
            )}
            <Button
              variant="outlined"
              color="error"
              onClick={() => {
                setNotes('');
                setRejectionReason('');
                setDialogType('reject');
              }}
              startIcon={<CancelIcon />}
              disabled={actionLoading}
            >
              رفض الطلب
            </Button>
            <Button
              variant="contained"
              color="success"
              onClick={() => {
                setNotes('');
                setDialogType('approve');
              }}
              startIcon={<CheckCircleIcon />}
              disabled={actionLoading}
              sx={{ fontWeight: 'bold' }}
            >
              الموافقة على الطلب
            </Button>
          </Stack>
        )}

        {!isPending && (
          <Chip
            icon={request.status === 'APPROVED' ? <CheckCircleIcon /> : <CancelIcon />}
            label={`تم اتخاذ القرار: ${STATUS_LABELS[request.status] || request.status}`}
            color={STATUS_COLORS[request.status] || 'default'}
          />
        )}
      </Box>

      <Grid container spacing={3}>
        {/* Left: Request Details */}
        <Grid item xs={12} md={7}>
          {/* Member Info */}
          <Card sx={{ mb: 3 }}>
            <CardHeader
              avatar={<PersonIcon color="primary" />}
              title="بيانات المستفيد"
              titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }}
            />
            <CardContent>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    اسم المستفيد
                  </Typography>
                  <Typography fontWeight="bold">{request.memberName || request.memberFullName || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    رقم البطاقة
                  </Typography>
                  <Typography fontWeight="bold">{request.memberCardNumber || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    مقدم الخدمة
                  </Typography>
                  <Typography fontWeight="bold">{request.providerName || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    تاريخ الطلب
                  </Typography>
                  <Typography fontWeight="bold">
                    {request.createdAt ? new Date(request.createdAt).toLocaleDateString('ar-LY') : '-'}
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* Clinical Info */}
          <Card sx={{ mb: 3 }}>
            <CardHeader
              avatar={<MedicalServicesIcon color="secondary" />}
              title="البيانات السريرية"
              titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }}
            />
            <CardContent>
              <Grid container spacing={2}>
                {request.chiefComplaint && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">
                      الشكوى الرئيسية
                    </Typography>
                    <Typography>{request.chiefComplaint}</Typography>
                  </Grid>
                )}
                {request.diagnosis && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">
                      التشخيص
                    </Typography>
                    <Typography fontWeight="bold">{request.diagnosis}</Typography>
                  </Grid>
                )}
                {request.treatmentPlan && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">
                      الخطة العلاجية
                    </Typography>
                    <Typography>{request.treatmentPlan}</Typography>
                  </Grid>
                )}
                {request.clinicalNotes && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">
                      الملاحظات الطبية
                    </Typography>
                    <Typography color="text.secondary">{request.clinicalNotes}</Typography>
                  </Grid>
                )}
              </Grid>
            </CardContent>
          </Card>

          {/* Service Lines */}
          <Card>
            <CardHeader title="الخدمات والأسعار المطلوبة" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
            <CardContent sx={{ p: 0 }}>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: 'grey.100' }}>
                      <TableCell>
                        <strong>الخدمة</strong>
                      </TableCell>
                      <TableCell align="center">
                        <strong>الكود</strong>
                      </TableCell>
                      <TableCell align="right">
                        <strong>سعر العقد</strong>
                      </TableCell>
                      <TableCell align="right">
                        <strong>السعر المطلوب</strong>
                      </TableCell>
                      <TableCell align="center">
                        <strong>الفرق</strong>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {/* If request has lines array */}
                    {request.lines?.length > 0 ? (
                      request.lines.map((line, idx) => {
                        const variance =
                          line.contractPrice && line.manualPrice
                            ? Math.round(((line.manualPrice - line.contractPrice) / line.contractPrice) * 100)
                            : 0;
                        return (
                          <TableRow key={idx} hover>
                            <TableCell>
                              <Typography variant="body2" fontWeight="bold">
                                {line.name || line.serviceName || '-'}
                              </Typography>
                              {line.overrideReason && (
                                <Typography variant="caption" color="warning.main" display="block">
                                  ⚠ {line.overrideReason}
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell align="center">
                              <Chip label={line.code || line.serviceCode || '-'} size="small" variant="outlined" />
                            </TableCell>
                            <TableCell align="right">
                              {line.contractPrice ? `${Number(line.contractPrice).toLocaleString()} د.ل` : '—'}
                            </TableCell>
                            <TableCell align="right">
                              <Typography fontWeight="bold">
                                {line.manualPrice ? `${Number(line.manualPrice).toLocaleString()} د.ل` : '-'}
                              </Typography>
                            </TableCell>
                            <TableCell align="center">
                              {variance > 0 ? (
                                <Chip
                                  label={`+${variance}%`}
                                  color={variance > 20 ? 'error' : variance > 10 ? 'warning' : 'default'}
                                  size="small"
                                />
                              ) : (
                                <Typography variant="caption" color="text.secondary">
                                  —
                                </Typography>
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })
                    ) : (
                      // Single-line request
                      <TableRow hover>
                        <TableCell>
                          <Typography variant="body2" fontWeight="bold">
                            {request.serviceName || request.serviceCode || '-'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">
                          <Chip label={request.serviceCode || '-'} size="small" variant="outlined" />
                        </TableCell>
                        <TableCell align="right">
                          {request.contractPrice ? `${Number(request.contractPrice).toLocaleString()} د.ل` : '—'}
                        </TableCell>
                        <TableCell align="right">
                          <Typography fontWeight="bold">
                            {request.requestedAmount ? `${Number(request.requestedAmount).toLocaleString()} د.ل` : '-'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">—</TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>

              {/* Total */}
              <Box sx={{ p: 2, bgcolor: 'grey.50', display: 'flex', justifyContent: 'flex-end', gap: 3 }}>
                <Box textAlign="right">
                  <Typography variant="caption" color="text.secondary">
                    إجمالي المطلوب
                  </Typography>
                  <Typography variant="h6" fontWeight="bold" color="primary.main">
                    {Number(request.requestedAmount || 0).toLocaleString()} د.ل
                  </Typography>
                </Box>
                {request.approvedAmount && (
                  <Box textAlign="right">
                    <Typography variant="caption" color="text.secondary">
                      المبلغ المعتمد
                    </Typography>
                    <Typography variant="h6" fontWeight="bold" color="success.main">
                      {Number(request.approvedAmount).toLocaleString()} د.ل
                    </Typography>
                  </Box>
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Right: Decision Panel */}
        <Grid item xs={12} md={5}>
          {/* Decision Action Card - sticky */}
          <Card sx={{ mb: 3, border: isPending ? '2px solid' : undefined, borderColor: 'primary.main' }}>
            <CardHeader title="قرار المراجعة" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold', color: 'primary.main' }} />
            <CardContent>
              {isPending ? (
                <Stack spacing={2}>
                  <Alert severity="info" variant="outlined" icon={<WarningIcon />}>
                    هذا الطلب بانتظار قرارك — راجع البيانات السريرية والأسعار قبل اتخاذ القرار.
                  </Alert>
                  {canStartReview && (
                    <Button
                      fullWidth
                      variant="outlined"
                      color="info"
                      onClick={handleStartReview}
                      disabled={actionLoading}
                      startIcon={actionLoading ? <CircularProgress size={16} /> : <AssignmentTurnedInIcon />}
                    >
                      بدء المراجعة (تغيير الحالة إلى: قيد المراجعة)
                    </Button>
                  )}
                  <Divider />
                  <Button
                    fullWidth
                    variant="contained"
                    color="success"
                    size="large"
                    onClick={() => {
                      setNotes('');
                      setDialogType('approve');
                    }}
                    startIcon={<CheckCircleIcon />}
                    disabled={actionLoading}
                    sx={{ fontWeight: 'bold', py: 1.5 }}
                  >
                    ✅ الموافقة على الطلب
                  </Button>
                  <Button
                    fullWidth
                    variant="contained"
                    color="error"
                    size="large"
                    onClick={() => {
                      setNotes('');
                      setRejectionReason('');
                      setDialogType('reject');
                    }}
                    startIcon={<CancelIcon />}
                    disabled={actionLoading}
                    sx={{ py: 1.5 }}
                  >
                    ❌ رفض الطلب
                  </Button>
                  <Button
                    fullWidth
                    variant="outlined"
                    color="warning"
                    onClick={() => {
                      setNotes('');
                      setDialogType('info');
                    }}
                    startIcon={<HelpOutlineIcon />}
                    disabled={actionLoading}
                  >
                    طلب معلومات إضافية
                  </Button>
                </Stack>
              ) : (
                <Box textAlign="center" py={2}>
                  <Chip
                    icon={request.status === 'APPROVED' ? <CheckCircleIcon /> : <CancelIcon />}
                    label={STATUS_LABELS[request.status] || request.status}
                    color={STATUS_COLORS[request.status] || 'default'}
                    sx={{ fontSize: '1rem', py: 2, px: 1 }}
                  />
                  {request.approvalNotes && (
                    <Box mt={2}>
                      <Typography variant="caption" color="text.secondary">
                        ملاحظات المراجع
                      </Typography>
                      <Typography>{request.approvalNotes}</Typography>
                    </Box>
                  )}
                  {request.rejectionReason && (
                    <Box mt={2}>
                      <Typography variant="caption" color="text.secondary">
                        سبب الرفض
                      </Typography>
                      <Typography color="error">{request.rejectionReason}</Typography>
                    </Box>
                  )}
                </Box>
              )}
            </CardContent>
          </Card>

          {/* Notes if any */}
          {request.providerNotes && (
            <Card>
              <CardHeader title="ملاحظات مقدم الخدمة" titleTypographyProps={{ variant: 'subtitle1' }} />
              <CardContent>
                <Typography color="text.secondary">{request.providerNotes}</Typography>
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>

      {/* ===== APPROVE DIALOG ===== */}
      <Dialog open={dialogType === 'approve'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ bgcolor: 'success.main', color: 'white', display: 'flex', alignItems: 'center', gap: 1 }}>
          <CheckCircleIcon /> تأكيد الموافقة على الطلب
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Alert severity="success" sx={{ mb: 2 }}>
            سيتم إخطار مقدم الخدمة بالموافقة فور تأكيدها.
          </Alert>
          <Typography variant="body2" mb={1}>
            الطلب: <strong>{request.referenceNumber || `#${request.id}`}</strong> — المستفيد: <strong>{request.memberName || '-'}</strong>
          </Typography>
          <Typography variant="body2" mb={2}>
            المبلغ المطلوب: <strong>{Number(request.requestedAmount || 0).toLocaleString()} د.ل</strong>
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={3}
            label="ملاحظات الموافقة (اختياري)"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="أي ملاحظات أو شروط للموافقة..."
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={handleApprove}
            disabled={actionLoading}
            startIcon={actionLoading ? <CircularProgress size={16} /> : <CheckCircleIcon />}
          >
            تأكيد الموافقة
          </Button>
        </DialogActions>
      </Dialog>

      {/* ===== REJECT DIALOG ===== */}
      <Dialog open={dialogType === 'reject'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ bgcolor: 'error.main', color: 'white', display: 'flex', alignItems: 'center', gap: 1 }}>
          <CancelIcon /> تأكيد رفض الطلب
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Alert severity="warning" sx={{ mb: 2 }}>
            سيتم إخطار مقدم الخدمة بالرفض وسببه.
          </Alert>
          <TextField
            select
            fullWidth
            required
            label="سبب الرفض *"
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            sx={{ mb: 2 }}
          >
            {REJECTION_REASONS.map((r) => (
              <MenuItem key={r.value} value={r.value}>
                {r.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            fullWidth
            multiline
            rows={3}
            label="تفاصيل إضافية (اختياري)"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="وضح سبب الرفض بشكل أوضح..."
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReject}
            disabled={actionLoading || !rejectionReason}
            startIcon={actionLoading ? <CircularProgress size={16} /> : <CancelIcon />}
          >
            تأكيد الرفض
          </Button>
        </DialogActions>
      </Dialog>

      {/* ===== REQUEST INFO DIALOG ===== */}
      <Dialog open={dialogType === 'info'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <HelpOutlineIcon color="warning" /> طلب معلومات إضافية
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            سيتم إخطار مقدم الخدمة بطلب المعلومات وتغيير الحالة إلى "يحتاج تصحيح".
          </Alert>
          <TextField
            fullWidth
            required
            multiline
            rows={4}
            label="المعلومات المطلوبة *"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="اكتب بوضوح ما تحتاجه من مقدم الخدمة..."
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="warning"
            disabled={actionLoading || !notes.trim()}
            startIcon={actionLoading ? <CircularProgress size={16} /> : <HelpOutlineIcon />}
          >
            إرسال الطلب
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PreAuthReviewPage;
