import { useState, useEffect, useCallback, useMemo } from 'react';
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
  Stack,
  Tooltip,
  LinearProgress,
  Switch,
  FormControlLabel
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import PersonIcon from '@mui/icons-material/Person';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import BalanceIcon from '@mui/icons-material/Balance';
import SettingsIcon from '@mui/icons-material/Settings';
import EditIcon from '@mui/icons-material/Edit';
import AttachmentIcon from '@mui/icons-material/Attachment';

import { reviewerPreAuthService, preApprovalsService } from 'services/api';
import { useSnackbar } from 'notistack';
import { useReviewer } from 'contexts/ReviewerContext';
import { formatCurrency } from 'utils/currency-formatter';
import { DocumentPreviewDrawer } from 'components/tba/documents';

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

const STATUS_COLORS = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
  PARTIALLY_APPROVED: 'success'
};
const STATUS_LABELS = {
  PENDING: 'قيد الانتظار',
  UNDER_REVIEW: 'قيد المراجعة',
  APPROVED: 'موافق عليه',
  REJECTED: 'مرفوض',
  CANCELLED: 'ملغى',
  PARTIALLY_APPROVED: 'موافقة جزئية'
};

const LINE_STATUS_COLORS = { PENDING: 'warning', APPROVED: 'success', PARTIALLY_APPROVED: 'info', REJECTED: 'error' };
const getLineDecisionStatus = (line) => line?.decisionStatus || line?.status || 'PENDING';
const getLineDecisionNotes = (line) => line?.decisionNotes || line?.reviewerNotes || '';
const getLineServiceCode = (line) => line?.providerServiceCode || line?.serviceCode || line?.code || '-';
const getLineRequestedAmount = (line) => line?.requestedAmount ?? line?.manualPrice ?? line?.contractPrice ?? null;
const getAttachmentName = (attachment, index = 0) =>
  attachment?.fileName || attachment?.originalFileName || attachment?.name || `مرفق ${index + 1}`;
const getAttachmentMimeType = (attachment) => attachment?.contentType || attachment?.mimeType || attachment?.fileType || '';

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
  const [attachments, setAttachments] = useState([]);
  const [attachmentsLoading, setAttachmentsLoading] = useState(false);
  const [selectedAttachmentId, setSelectedAttachmentId] = useState(null);
  const [attachmentsPreviewOpen, setAttachmentsPreviewOpen] = useState(false);

  // Dialog states
  const [dialogType, setDialogType] = useState(null); // 'reject_all' | 'request_info' | 'line_decision' | 'finalize_confirm'
  const [notes, setNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');

  // Line Decision State
  const [selectedLine, setSelectedLine] = useState(null);
  const [lineDecisionType, setLineDecisionType] = useState('APPROVED');
  const [lineApprovedAmount, setLineApprovedAmount] = useState('');

  const fetchAttachments = useCallback(
    async (fallbackAttachments = []) => {
      setAttachmentsLoading(true);
      try {
        const rawAttachments = await preApprovalsService.getAttachments(id).catch(() => fallbackAttachments || []);
        const list = Array.isArray(rawAttachments) ? rawAttachments : rawAttachments?.items || rawAttachments?.content || [];
        const normalized = list.map((attachment, index) => ({
          id: attachment?.id || `preauth-attachment-${index}`,
          documentUrl: attachment?.id
            ? `/pre-authorizations/${id}/attachments/${attachment.id}`
            : attachment?.url || attachment?.fileUrl || attachment?.downloadUrl || '',
          fileName: getAttachmentName(attachment, index),
          fileSize: attachment?.fileSize || attachment?.size,
          mimeType: getAttachmentMimeType(attachment),
          fileType: getAttachmentMimeType(attachment),
          documentTitle: attachment?.attachmentType || 'مرفق موافقة',
          attachmentType: attachment?.attachmentType,
          raw: attachment
        }));
        setAttachments(normalized);
        setSelectedAttachmentId((prev) => prev || normalized[0]?.id || null);
      } catch (err) {
        console.error('Failed to load pre-auth attachments:', err);
        setAttachments([]);
      } finally {
        setAttachmentsLoading(false);
      }
    },
    [id]
  );

  // Fetch request and lines
  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      // Fetch request details using standard service, or if reviewer service has it
      const data = await preApprovalsService.getById(id);
      setRequest(data);
      fetchAttachments(data?.attachments || []);

      // Fetch line details
      const linesData = await reviewerPreAuthService.getLines(id);
      setLines(linesData || data.lines || []);
    } catch (err) {
      console.error('Failed to load request:', err);
      setError(err?.userMessage || err?.message || 'فشل في تحميل بيانات الطلب');
    } finally {
      setLoading(false);
    }
  }, [fetchAttachments, id]);

  useEffect(() => {
    if (id) fetchData();
  }, [fetchData, id]);

  const handleDownloadAttachment = useCallback(
    async (attachment) => {
      if (!attachment?.id) return;
      try {
        const blob = await preApprovalsService.downloadAttachment(id, attachment.id);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = attachment.fileName || `pre-auth-attachment-${attachment.id}`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      } catch (err) {
        enqueueSnackbar(err?.userMessage || err?.message || 'فشل تحميل المرفق', { variant: 'error' });
      }
    },
    [enqueueSnackbar, id]
  );

  const handleOpenAttachmentPreview = useCallback(
    (attachmentId = null) => {
      setSelectedAttachmentId(attachmentId || attachments[0]?.id || null);
      setAttachmentsPreviewOpen(true);
    },
    [attachments]
  );

  const handleCloseAttachmentPreview = useCallback(() => {
    setAttachmentsPreviewOpen(false);
  }, []);

  const previewDocuments = useMemo(
    () =>
      attachments.map((attachment) => ({
        id: attachment.id,
        documentUrl: attachment.documentUrl,
        fileName: attachment.fileName,
        fileSize: attachment.fileSize,
        mimeType: attachment.mimeType || attachment.fileType,
        documentTitle: attachment.documentTitle,
        onDownload: () => handleDownloadAttachment(attachment)
      })),
    [attachments, handleDownloadAttachment]
  );

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

  const handleRequestInfo = async () => {
    setActionLoading(true);
    try {
      await reviewerPreAuthService.requestInfo(id, notes);
      enqueueSnackbar('تم إعادة الطلب للمزود للتعديل بنجاح', { variant: 'success' });
      setDialogType(null);
      fetchData();
    } catch (err) {
      console.error(err);
      enqueueSnackbar(err.message || 'فشل في إعادة الطلب للتعديل', { variant: 'error' });
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
      decisionStatus: lineDecisionType,
      approvedAmount: lineDecisionType === 'PARTIALLY_APPROVED' ? parseFloat(lineApprovedAmount) : null,
      decisionNotes: notes
    };
    submitLineDecision(selectedLine.id, decisionData);
  };

  const handleInlineAction = (line, actionType) => {
    if (actionType === 'APPROVED') {
      submitLineDecision(line.id, { decisionStatus: 'APPROVED', decisionNotes: 'موافق عليه بالكامل' });
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

  const financialSummary = useMemo(() => {
    return lines.reduce(
      (summary, line) => {
        const status = getLineDecisionStatus(line);
        const requested = Number(getLineRequestedAmount(line) || 0);
        const approved = status === 'PENDING' ? 0 : Number(line?.approvedAmount || 0);
        const patientShare = status === 'PENDING' ? 0 : Number(line?.patientShare || 0);
        const companyShare = status === 'PENDING' ? 0 : Number(line?.companyShare || 0);

        summary.requested += requested;
        summary.approved += approved;
        summary.patientShare += patientShare;
        summary.companyShare += companyShare;
        summary.rejected += Math.max(0, requested - approved);
        summary.totalLines += 1;

        if (status === 'PENDING') summary.pendingLines += 1;
        if (status === 'APPROVED') summary.approvedLines += 1;
        if (status === 'PARTIALLY_APPROVED') summary.partialLines += 1;
        if (status === 'REJECTED') summary.rejectedLines += 1;

        return summary;
      },
      {
        requested: 0,
        approved: 0,
        patientShare: 0,
        companyShare: 0,
        rejected: 0,
        totalLines: 0,
        pendingLines: 0,
        approvedLines: 0,
        partialLines: 0,
        rejectedLines: 0
      }
    );
  }, [lines]);

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
        <Alert severity="error" action={<Button onClick={fetchData}>إعادة المحاولة</Button>}>
          {error}
        </Alert>
      </Box>
    );

  if (!request)
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="warning">الطلب غير موجود.</Alert>
      </Box>
    );

  const isPending = ['PENDING', 'UNDER_REVIEW'].includes(request.status);
  const canStartReview = request.status === 'PENDING';
  const allLinesDecided = lines.length > 0 && lines.every((line) => getLineDecisionStatus(line) !== 'PENDING');
  const finalDecisionLabel =
    financialSummary.rejectedLines === financialSummary.totalLines
      ? 'رفض كامل'
      : financialSummary.rejectedLines > 0 || financialSummary.partialLines > 0
        ? 'موافقة جزئية'
        : 'موافقة كاملة';

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

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'المطلوب', value: financialSummary.requested, color: 'text.primary' },
          { label: 'المعتمد المتوقع', value: financialSummary.approved, color: 'success.main' },
          { label: 'حصة الشركة', value: financialSummary.companyShare, color: 'primary.main' },
          { label: 'حصة المستفيد', value: financialSummary.patientShare, color: 'warning.main' },
          { label: 'المرفوض/الفارق', value: financialSummary.rejected, color: financialSummary.rejected > 0 ? 'error.main' : 'text.secondary' }
        ].map((item) => (
          <Grid item xs={12} sm={6} md={2.4} key={item.label}>
            <Card sx={{ height: '100%', borderTop: '3px solid', borderColor: item.color }}>
              <CardContent sx={{ py: 1.5 }}>
                <Typography variant="caption" color="text.secondary">
                  {item.label}
                </Typography>
                <Typography variant="h6" fontWeight={800} color={item.color}>
                  {formatCurrency(item.value)}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 3 }}>
        <Chip label={`إجمالي السطور: ${financialSummary.totalLines}`} variant="outlined" color="primary" />
        <Chip label={`موافق: ${financialSummary.approvedLines}`} variant="outlined" color="success" />
        <Chip label={`جزئي: ${financialSummary.partialLines}`} variant="outlined" color="info" />
        <Chip label={`مرفوض: ${financialSummary.rejectedLines}`} variant="outlined" color="error" />
        <Chip
          label={`معلق: ${financialSummary.pendingLines}`}
          variant={financialSummary.pendingLines > 0 ? 'filled' : 'outlined'}
          color={financialSummary.pendingLines > 0 ? 'warning' : 'default'}
        />
        <Chip label={`القرار المتوقع: ${finalDecisionLabel}`} color="secondary" variant="outlined" />
      </Stack>

      <Grid container spacing={3}>
        {/* Left: Details and Lines */}
        <Grid item xs={12} md={8}>
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
                    المستفيد
                  </Typography>
                  <Typography fontWeight="bold">{request.memberName || request.memberFullName || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    مقدم الخدمة
                  </Typography>
                  <Typography fontWeight="bold">{request.providerName || '-'}</Typography>
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
              action={
                <Chip icon={<AttachmentIcon />} label={`${attachments.length} مرفق`} size="small" color="primary" variant="outlined" />
              }
            />
            <Divider />
            <CardContent>
              <Grid container spacing={3}>
                <Grid item xs={12}>
                  <Typography variant="caption" color="text.secondary">
                    التشخيص (Diagnosis)
                  </Typography>
                  <Typography fontWeight="bold" sx={{ mt: 0.5 }}>
                    {request.diagnosisDescription || request.diagnosis || 'غير محدد'}
                    {request.diagnosisCode && <Chip size="small" label={request.diagnosisCode} sx={{ ml: 1 }} />}
                  </Typography>
                </Grid>

                <Grid item xs={12}>
                  <Typography variant="caption" color="text.secondary">
                    الملاحظات الطبية (Clinical Notes)
                  </Typography>
                  <Typography
                    color="text.secondary"
                    sx={{
                      mt: 0.5,
                      p: 1.5,
                      bgcolor: 'grey.50',
                      borderRadius: 1,
                      border: '1px solid',
                      borderColor: 'grey.200',
                      minHeight: '60px'
                    }}
                  >
                    {request.clinicalNotes || 'لا توجد ملاحظات طبية مرفقة.'}
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* Notes History */}
          {(request.notes || request.rejectionReason) && (
            <Card sx={{ mb: 3, borderLeft: '4px solid', borderColor: 'warning.main' }}>
              <CardHeader title="سجل الملاحظات والتواصل" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
              <Divider />
              <CardContent>
                <Stack spacing={2}>
                  {request.notes && (
                    <Box sx={{ p: 2, bgcolor: '#fff4e5', borderRadius: 2 }}>
                      <Typography variant="caption" color="warning.dark" fontWeight="bold">
                        آخر ملاحظة مرسلة:
                      </Typography>
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        {request.notes}
                      </Typography>
                    </Box>
                  )}
                  {request.rejectionReason && request.status === 'REJECTED' && (
                    <Box sx={{ p: 2, bgcolor: '#fdeded', borderRadius: 2 }}>
                      <Typography variant="caption" color="error.dark" fontWeight="bold">
                        سبب الرفض:
                      </Typography>
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        {request.rejectionReason}
                      </Typography>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>
          )}

          {/* Service Lines Table */}
          <Card>
            <CardHeader title="الخدمات والتدقيق المالي" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold' }} />
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
                        <strong>المطلوب</strong>
                      </TableCell>
                      <TableCell align="right">
                        <strong>المعتمد</strong>
                      </TableCell>
                      <TableCell align="center">
                        <strong>الحالة</strong>
                      </TableCell>
                      {isPending && (
                        <TableCell align="center">
                          <strong>الإجراءات</strong>
                        </TableCell>
                      )}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {lines.map((line, idx) => {
                      const lineStatus = getLineDecisionStatus(line);
                      const lineNotes = getLineDecisionNotes(line);
                      const requestedAmount = getLineRequestedAmount(line);
                      return (
                        <TableRow key={idx} hover sx={{ bgcolor: lineStatus !== 'PENDING' ? 'grey.50' : 'inherit' }}>
                          <TableCell>
                            <Typography variant="body2" fontWeight="bold">
                              {line.serviceName || line.name || '-'}
                            </Typography>
                            {lineNotes && (
                              <Typography variant="caption" color="text.secondary" display="block">
                                ملاحظة: {lineNotes}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell align="center">
                            <Chip label={getLineServiceCode(line)} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell align="right">{requestedAmount != null ? formatCurrency(requestedAmount) : '—'}</TableCell>
                          <TableCell align="right">
                            {lineStatus === 'PENDING' ? (
                              '—'
                            ) : (
                              <Typography fontWeight="bold" color={lineStatus === 'REJECTED' ? 'error' : 'success.main'}>
                                {line.approvedAmount !== null ? formatCurrency(line.approvedAmount) : '—'}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell align="center">
                            <Chip
                              label={STATUS_LABELS[lineStatus] || lineStatus}
                              color={LINE_STATUS_COLORS[lineStatus] || 'default'}
                              size="small"
                            />
                          </TableCell>
                          {isPending && (
                            <TableCell align="center">
                              {lineStatus === 'PENDING' ? (
                                <Stack direction="row" spacing={0.5} justifyContent="center">
                                  <Tooltip title="موافقة كلية">
                                    <IconButton
                                      size="small"
                                      color="success"
                                      disabled={actionLoading || canStartReview}
                                      onClick={() =>
                                        inlineEditing ? handleInlineAction(line, 'APPROVED') : openLineDecisionModal(line, 'APPROVED')
                                      }
                                    >
                                      <CheckCircleIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                  <Tooltip title="موافقة جزئية (تعديل السعر)">
                                    <IconButton
                                      size="small"
                                      color="info"
                                      disabled={actionLoading || canStartReview}
                                      onClick={() => openLineDecisionModal(line, 'PARTIALLY_APPROVED')}
                                    >
                                      <BalanceIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                  <Tooltip title="رفض الخدمة">
                                    <IconButton
                                      size="small"
                                      color="error"
                                      disabled={actionLoading || canStartReview}
                                      onClick={() => openLineDecisionModal(line, 'REJECTED')}
                                    >
                                      <CancelIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                </Stack>
                              ) : (
                                <Button size="small" disabled={actionLoading} onClick={() => openLineDecisionModal(line, lineStatus)}>
                                  تعديل القرار
                                </Button>
                              )}
                            </TableCell>
                          )}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Right: Actions Panel */}
        <Grid item xs={12} md={4}>
          <Box sx={{ mb: 3, display: 'flex', justifyContent: { xs: 'stretch', md: 'flex-end' } }}>
            <Card variant="outlined" sx={{ width: '100%' }}>
              <CardHeader
                avatar={<AttachmentIcon color="primary" />}
                title="المرفقات الطبية"
                subheader={attachmentsLoading ? 'جارٍ تحميل المرفقات...' : `${attachments.length} مرفق`}
                action={
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<AttachmentIcon />}
                    onClick={() => handleOpenAttachmentPreview()}
                    disabled={attachmentsLoading || attachments.length === 0}
                  >
                    معاينة
                  </Button>
                }
              />
              <Divider />
              <CardContent>
                {attachments.length === 0 ? (
                  <Alert severity="info">لا توجد مرفقات طبية لهذا الطلب.</Alert>
                ) : (
                  <Stack spacing={1}>
                    {attachments.slice(0, 4).map((attachment) => (
                      <Paper
                        key={attachment.id}
                        variant="outlined"
                        sx={{ p: 1, cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
                        onClick={() => handleOpenAttachmentPreview(attachment.id)}
                      >
                        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" fontWeight={700} noWrap>
                              {attachment.fileName}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {attachment.documentTitle || 'مرفق موافقة'}
                            </Typography>
                          </Box>
                          <Button size="small" onClick={(event) => { event.stopPropagation(); handleDownloadAttachment(attachment); }}>
                            تحميل
                          </Button>
                        </Stack>
                      </Paper>
                    ))}
                    {attachments.length > 4 && (
                      <Typography variant="caption" color="text.secondary">
                        و {attachments.length - 4} مرفقات أخرى داخل العارض.
                      </Typography>
                    )}
                  </Stack>
                )}
              </CardContent>
            </Card>
          </Box>

          <Card sx={{ mb: 3, border: isPending ? '2px solid' : undefined, borderColor: 'primary.main' }}>
            <CardHeader title="لوحة القرار" titleTypographyProps={{ variant: 'h6', fontWeight: 'bold', color: 'primary.main' }} />
            <CardContent>
              {isPending ? (
                <Stack spacing={2}>
                  {canStartReview ? (
                    <>
                      <Alert severity="info" variant="outlined">
                        يجب بدء المراجعة لتفعيل أزرار اتخاذ القرار.
                      </Alert>
                      <Button
                        fullWidth
                        variant="contained"
                        color="info"
                        onClick={handleStartReview}
                        disabled={actionLoading}
                        startIcon={<AssignmentTurnedInIcon />}
                      >
                        بدء المراجعة
                      </Button>
                    </>
                  ) : (
                    <>
                      <Alert severity={allLinesDecided ? 'success' : 'warning'} variant="outlined">
                        {allLinesDecided
                          ? 'تم تدقيق جميع الخدمات. يمكنك الآن إنهاء المراجعة.'
                          : 'يرجى اتخاذ قرار لكل خدمة في الجدول على اليمين.'}
                      </Alert>
                      <Button
                        fullWidth
                        variant="contained"
                        color="success"
                        size="large"
                        onClick={() => setDialogType('finalize_confirm')}
                        disabled={actionLoading || !allLinesDecided}
                        startIcon={<CheckCircleIcon />}
                      >
                        مراجعة الملخص ثم الإرسال
                      </Button>
                      <Divider />
                      <Button
                        fullWidth
                        variant="outlined"
                        color="error"
                        onClick={() => {
                          setRejectionReason('');
                          setDialogType('reject_all');
                        }}
                        startIcon={<CancelIcon />}
                        disabled={actionLoading}
                      >
                        رفض كلي للطلب
                      </Button>
                      <Button
                        fullWidth
                        variant="outlined"
                        color="warning"
                        onClick={() => {
                          setNotes('');
                          setDialogType('request_info');
                        }}
                        startIcon={<EditIcon />}
                        disabled={actionLoading}
                        sx={{ mt: 1 }}
                      >
                        إعادة للمزود للتعديل
                      </Button>
                    </>
                  )}
                </Stack>
              ) : (
                <Box textAlign="center" py={2}>
                  <Chip
                    icon={request.status === 'APPROVED' ? <CheckCircleIcon /> : <CancelIcon />}
                    label={STATUS_LABELS[request.status] || request.status}
                    color={STATUS_COLORS[request.status] || 'default'}
                    sx={{ fontSize: '1rem', py: 2, px: 1 }}
                  />
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
          <Alert severity="warning" sx={{ mb: 2 }}>
            سيتم رفض جميع الخدمات في هذا الطلب وإخطار المستشفى.
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
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleRejectAll}
            disabled={actionLoading || !rejectionReason}
            startIcon={<CancelIcon />}
          >
            تأكيد الرفض
          </Button>
        </DialogActions>
      </Dialog>

      {/* ===== FINALIZE CONFIRMATION DIALOG ===== */}
      <Dialog open={dialogType === 'finalize_confirm'} onClose={() => !actionLoading && setDialogType(null)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <CheckCircleIcon color="success" /> تأكيد إنهاء مراجعة الموافقة
        </DialogTitle>
        <DialogContent>
          <Alert severity={financialSummary.rejected > 0 ? 'warning' : 'success'} sx={{ mb: 2 }}>
            سيتم تثبيت قرارات السطور وإرسال النتيجة النهائية لمقدم الخدمة. القرار المتوقع: <strong>{finalDecisionLabel}</strong>.
          </Alert>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            {[
              { label: 'المطلوب', value: financialSummary.requested, color: 'text.primary' },
              { label: 'المعتمد', value: financialSummary.approved, color: 'success.main' },
              { label: 'حصة الشركة', value: financialSummary.companyShare, color: 'primary.main' },
              { label: 'حصة المستفيد', value: financialSummary.patientShare, color: 'warning.main' },
              { label: 'المرفوض/الفارق', value: financialSummary.rejected, color: 'error.main' }
            ].map((item) => (
              <Grid item xs={12} sm={6} md={2.4} key={item.label}>
                <Box sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    {item.label}
                  </Typography>
                  <Typography variant="subtitle1" fontWeight={800} color={item.color}>
                    {formatCurrency(item.value)}
                  </Typography>
                </Box>
              </Grid>
            ))}
          </Grid>

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`موافق: ${financialSummary.approvedLines}`} color="success" variant="outlined" />
            <Chip label={`جزئي: ${financialSummary.partialLines}`} color="info" variant="outlined" />
            <Chip label={`مرفوض: ${financialSummary.rejectedLines}`} color="error" variant="outlined" />
            <Chip label={`معلق: ${financialSummary.pendingLines}`} color={financialSummary.pendingLines > 0 ? 'warning' : 'default'} />
          </Stack>

          {financialSummary.pendingLines > 0 && (
            <Alert severity="error" sx={{ mt: 2 }}>
              لا يمكن إنهاء المراجعة قبل اتخاذ قرار لكل سطر خدمة.
            </Alert>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            رجوع للتدقيق
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={handleFinalize}
            disabled={actionLoading || !allLinesDecided}
            startIcon={<CheckCircleIcon />}
          >
            تأكيد الإرسال النهائي
          </Button>
        </DialogActions>
      </Dialog>

      {/* ===== REQUEST INFO DIALOG ===== */}
      <Dialog open={dialogType === 'request_info'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ bgcolor: 'warning.main', color: 'white', display: 'flex', alignItems: 'center', gap: 1 }}>
          <EditIcon /> إعادة الطلب للمزود للتعديل
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Alert severity="warning" sx={{ mb: 2 }}>
            سيتم إعادة الطلب لمقدم الخدمة لتعديله بناءً على الملاحظات التالية.
          </Alert>
          <TextField
            fullWidth
            required
            multiline
            rows={3}
            label="الملاحظات المطلوبة من المزود *"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="warning"
            onClick={handleRequestInfo}
            disabled={actionLoading || !notes}
            startIcon={<EditIcon />}
          >
            تأكيد الإعادة
          </Button>
        </DialogActions>
      </Dialog>

      {/* ===== LINE DECISION DIALOG ===== */}
      <Dialog open={dialogType === 'line_decision'} onClose={() => setDialogType(null)} maxWidth="sm" fullWidth>
        <DialogTitle
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            bgcolor: lineDecisionType === 'REJECTED' ? 'error.main' : lineDecisionType === 'APPROVED' ? 'success.main' : 'info.main',
            color: 'white'
          }}
        >
          {lineDecisionType === 'REJECTED' ? <CancelIcon /> : lineDecisionType === 'APPROVED' ? <CheckCircleIcon /> : <BalanceIcon />}
          {lineDecisionType === 'REJECTED'
            ? 'رفض الخدمة'
            : lineDecisionType === 'APPROVED'
              ? 'موافقة على الخدمة'
              : 'موافقة جزئية وتعديل السعر'}
        </DialogTitle>
        <DialogContent sx={{ pt: 3 }}>
          <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
            {selectedLine?.serviceName || selectedLine?.name}
          </Typography>

          {lineDecisionType === 'PARTIALLY_APPROVED' && (
            <TextField
              fullWidth
              required
              type="number"
              label="المبلغ المعتمد الجديد"
              value={lineApprovedAmount}
              onChange={(e) => setLineApprovedAmount(e.target.value)}
              sx={{ mb: 2, mt: 1 }}
              helperText={`السعر المطلوب كان: ${getLineRequestedAmount(selectedLine) || 0} د.ل`}
            />
          )}

          <TextField
            fullWidth
            required={lineDecisionType !== 'APPROVED'}
            multiline
            rows={3}
            label="الملاحظات الطبية أو الإدارية"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDialogType(null)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color={lineDecisionType === 'REJECTED' ? 'error' : 'primary'}
            onClick={handleLineDecisionModalConfirm}
            disabled={actionLoading}
          >
            حفظ القرار
          </Button>
        </DialogActions>
      </Dialog>

      <DocumentPreviewDrawer
        open={attachmentsPreviewOpen}
        onClose={handleCloseAttachmentPreview}
        documents={previewDocuments}
        initialDocumentId={selectedAttachmentId}
        showDownload
      />
    </Box>
  );
};

export default PreAuthReviewPage;
