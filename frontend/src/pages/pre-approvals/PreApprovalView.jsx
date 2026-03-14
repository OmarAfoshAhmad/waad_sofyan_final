import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography,
  Alert,
  IconButton,
  Tooltip
} from '@mui/material';
import {
  ArrowBack,
  AssignmentTurnedIn as PreApprovalIcon,
  MedicalServices as MedicalIcon,
  AttachFile as AttachmentIcon,
  Receipt as ClaimIcon,
  CloudUpload as UploadIcon,
  VisibilityOutlined as ShowDocsIcon,
  VisibilityOffOutlined as HideDocsIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader, DocumentSidePanel } from 'components/tba';
import { usePreApprovalDetails } from 'hooks/usePreApprovals';
import { FileUploader, AttachmentList } from 'components/upload';
import {
  uploadPreAuthAttachment,
  getPreAuthAttachments,
  downloadPreAuthAttachment,
  deletePreAuthAttachment
} from 'services/api/files.service';
// import MedicalDocumentSidePreview from 'components/medical/MedicalDocumentSidePreview';
// import DocumentSideViewer from 'components/documents/DocumentSideViewer';
import DocumentPreviewPanel from 'components/documents/DocumentPreviewPanel';

// Insurance UX Components - Phase B2 Step 3
import {
  StatusTimeline,
  CardStatusBadge,
  PriorityBadge,
  ValidityCountdown,
  AmountComparisonBar,
  getWorkflowSteps
} from 'components/insurance';

// Pre-Approval Status Mapping for CardStatusBadge
const PREAPPROVAL_STATUS_MAP = {
  PENDING: 'PENDING',
  UNDER_REVIEW: 'PENDING',
  APPROVAL_IN_PROGRESS: 'PENDING',
  NEEDS_CORRECTION: 'SUSPENDED',
  APPROVED: 'ACTIVE',
  ACKNOWLEDGED: 'ACTIVE',
  REJECTED: 'BLOCKED',
  EXPIRED: 'EXPIRED',
  CANCELLED: 'INACTIVE',
  USED: 'INACTIVE'
};

// Arabic labels for statuses
const STATUS_LABELS = {
  PENDING: 'قيد المراجعة',
  UNDER_REVIEW: 'قيد المراجعة الطبية',
  APPROVAL_IN_PROGRESS: 'جاري معالجة الموافقة',
  NEEDS_CORRECTION: 'تحتاج تصحيح',
  APPROVED: 'تمت الموافقة',
  ACKNOWLEDGED: 'تم الاطلاع',
  REJECTED: 'مرفوض',
  EXPIRED: 'منتهية الصلاحية',
  CANCELLED: 'ملغى',
  USED: 'مستخدمة'
};

// Helper Info Row Component
const InfoRow = ({ label, value, valueColor }) => (
  <Grid container spacing={2} sx={{ mb: 1.5 }}>
    <Grid size={{ xs: 12, sm: 4 }}>
      <Typography variant="subtitle2" color="text.secondary">
        {label}
      </Typography>
    </Grid>
    <Grid size={{ xs: 12, sm: 8 }}>
      <Typography variant="body1" color={valueColor || 'text.primary'}>
        {value ?? '-'}
      </Typography>
    </Grid>
  </Grid>
);

const PreApprovalView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { preApproval, loading, error } = usePreApprovalDetails(id);

  // Attachments state
  const [attachments, setAttachments] = useState([]);
  const [loadingAttachments, setLoadingAttachments] = useState(false);

  // Document side panel state (old)
  const [showDocumentPanel, setShowDocumentPanel] = useState(true);

  // Medical Document Side Preview (new)
  const [previewDocument, setPreviewDocument] = useState(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [focusMode, setFocusMode] = useState(false);

  // Fetch attachments when preApproval loads
  const fetchAttachments = useCallback(async () => {
    if (!id) return;
    try {
      setLoadingAttachments(true);
      const result = await getPreAuthAttachments(id);
      setAttachments(result?.data || result || []);
    } catch (err) {
      console.error('Error fetching attachments:', err);
    } finally {
      setLoadingAttachments(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) {
      fetchAttachments();
    }
  }, [id, fetchAttachments]);

  // Upload success handler
  const handleUploadSuccess = async () => {
    await fetchAttachments();
  };

  // Download attachment
  const handleDownloadAttachment = async (attachmentId) => {
    try {
      const blob = await downloadPreAuthAttachment(id, attachmentId);
      const attachment = attachments.find((a) => a.id === attachmentId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment?.fileName || attachment?.originalFileName || 'attachment';
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Error downloading attachment:', err);
    }
  };

  // Delete attachment
  const handleDeleteAttachment = async (attachmentId) => {
    try {
      await deletePreAuthAttachment(id, attachmentId);
      await fetchAttachments();
    } catch (err) {
      console.error('Error deleting attachment:', err);
    }
  };

  // Medical Document Preview Handler
  const handlePreviewDocument = useCallback((attachment) => {
    if (!attachment) return;

    const fileKey = attachment.fileKey || `pre-auth/${attachment.fileName || attachment.id}`;

    setPreviewDocument({
      id: attachment.id,
      name: attachment.fileName || attachment.originalFileName || 'مستند طبي',
      type: attachment.contentType || attachment.mimeType,
      mimeType: attachment.contentType || attachment.mimeType,
      fileKey: fileKey,
      description: attachment.attachmentType || 'مستند موافقة مسبقة'
    });
    setPreviewOpen(true);
  }, []);

  const handleClosePreview = useCallback(() => {
    setPreviewOpen(false);
    setPreviewDocument(null);
  }, []);

  const handleToggleFocus = useCallback(() => {
    setFocusMode((prev) => !prev);
  }, []);

  const handleBack = () => {
    navigate('/pre-approvals/inbox');
  };

  // Navigate to Provider Portal claims submission pre-filled with this pre-auth data
  const handleConvertToClaim = () => {
    if (!preApproval) return;
    navigate('/provider/claims/submit', {
      state: {
        fromPreAuth: true,
        preAuthId: preApproval.id,
        preAuthNumber: preApproval.preAuthNumber,
        visitId: preApproval.visitId,
        memberId: preApproval.memberId,
        memberName: preApproval.memberName,
        providerId: preApproval.providerId,
        approvedAmount: preApproval.approvedAmount
      }
    });
  };

  // Build preview URL for document side panel
  const buildDocumentPreviewUrl = useCallback(
    async (document) => {
      if (!preApproval?.id || !document?.id) return null;
      try {
        const blob = await downloadPreAuthAttachment(preApproval.id, document.id);
        return URL.createObjectURL(blob);
      } catch (err) {
        console.error('Error building preview URL:', err);
        return null;
      }
    },
    [preApproval?.id]
  );

  // Pre-Auth attachment type labels (Arabic)
  const PREAUTH_ATTACHMENT_LABELS = {
    MEDICAL_REPORT: 'تقرير طبي',
    LAB_RESULT: 'نتائج مختبر',
    RADIOLOGY: 'أشعة / تصوير',
    PRESCRIPTION: 'وصفة طبية',
    REFERRAL: 'تحويل طبي',
    OTHER: 'مستند آخر'
  };

  // Convert attachments to DocumentSidePanel format
  const documentPanelData = attachments.map((att) => ({
    id: att.id,
    name: att.fileName || att.originalFileName || 'مستند',
    type: PREAUTH_ATTACHMENT_LABELS[att.attachmentType] || att.attachmentType || 'مستند',
    mimeType: att.contentType || att.mimeType || 'application/octet-stream',
    size: att.fileSize || att.size || 0,
    status: 'UPLOADED',
    uploadedAt: att.uploadedAt || att.createdAt
  }));

  if (loading) {
    return (
      <MainCard>
        <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
          <CircularProgress />
        </Box>
      </MainCard>
    );
  }

  if (error) {
    return (
      <MainCard>
        <Alert severity="error">{error}</Alert>
      </MainCard>
    );
  }

  if (!preApproval) {
    return (
      <MainCard>
        <Alert severity="warning">لم يتم العثور على الطلب</Alert>
      </MainCard>
    );
  }

  // Get workflow steps for timeline
  const timelineSteps = getWorkflowSteps('preapproval', preApproval?.status, 'ar');

  return (
    <>
      <ModernPageHeader
        title={`طلب موافقة مسبقة ${preApproval?.referenceNumber ?? `#${preApproval?.id}` ?? '-'}`}
        subtitle={preApproval?.memberName ?? preApproval?.member?.fullName ?? '-'}
        icon={PreApprovalIcon}
        breadcrumbs={[
          { label: 'الرئيسية', href: '/' },
          { label: 'الموافقات المسبقة', href: '/pre-approvals' },
          { label: `طلب ${preApproval?.referenceNumber ?? `#${preApproval?.id}` ?? '-'}` }
        ]}
        actions={
          <Stack direction="row" spacing={2} alignItems="center">
            <CardStatusBadge
              status={PREAPPROVAL_STATUS_MAP[preApproval?.status] ?? 'PENDING'}
              customLabel={STATUS_LABELS[preApproval?.status] ?? preApproval?.status}
              size="medium"
              variant="detailed"
            />
            <PriorityBadge
              priority={preApproval?.priority ?? 'ROUTINE'}
              size="medium"
              variant="chip"
              showResponseTime={false}
              language="ar"
            />
            <Tooltip title={showDocumentPanel ? 'إخفاء المستندات' : 'عرض المستندات'}>
              <IconButton onClick={() => setShowDocumentPanel(!showDocumentPanel)} color={showDocumentPanel ? 'primary' : 'default'}>
                {showDocumentPanel ? <HideDocsIcon /> : <ShowDocsIcon />}
              </IconButton>
            </Tooltip>
            <Button variant="outlined" startIcon={<ArrowBack />} onClick={handleBack}>
              رجوع
            </Button>
          </Stack>
        }
      />
      <Grid container spacing={2}>
        {/* ===================== MAIN CONTENT AREA ===================== */}
        <Grid size={{ xs: 12, md: showDocumentPanel ? 8 : 12 }}>
          <MainCard>
            <Stack spacing={2}>
              {/* ===================== WORKFLOW TIMELINE ===================== */}
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>
                    مسار الطلب
                  </Typography>
                  {/* Insurance UX - StatusTimeline */}
                  <StatusTimeline
                    steps={timelineSteps}
                    currentStep={preApproval?.status === 'PENDING' ? 'MEDICAL_REVIEW' : preApproval?.status}
                    variant="horizontal"
                    size="medium"
                    showDates={true}
                    language="ar"
                  />
                </CardContent>
              </Card>

              {/* ===================== VALIDITY COUNTDOWN (APPROVED ONLY) ===================== */}
              {preApproval?.status === 'APPROVED' && (
                <Card variant="outlined" sx={{ bgcolor: 'success.lighter', borderColor: 'success.light' }}>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      صلاحية الموافقة
                    </Typography>
                    <Divider sx={{ mb: 2 }} />
                    {/* Insurance UX - ValidityCountdown */}
                    <ValidityCountdown
                      approvalDate={preApproval?.reviewedAt ?? preApproval?.updatedAt ?? preApproval?.createdAt}
                      validityDays={preApproval?.validityDays ?? 30}
                      status={preApproval?.status}
                      showAction={true}
                      showProgress={true}
                      onConvertToClaim={handleConvertToClaim}
                      size="medium"
                      language="ar"
                    />
                  </CardContent>
                </Card>
              )}

              <Grid container spacing={2}>
                {/* ===================== BASIC INFORMATION ===================== */}
                <Grid size={{ xs: 12, md: 6 }}>
                  <Card variant="outlined" sx={{ height: '100%' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        المعلومات الأساسية
                      </Typography>
                      <Divider sx={{ mb: 2 }} />
                      <InfoRow label="رقم الطلب" value={preApproval?.referenceNumber || `PA-${preApproval?.id}`} />
                      <InfoRow label="المؤمَّن عليه" value={preApproval?.memberName ?? preApproval?.member?.fullName} />
                      <InfoRow label="الرقم الوطني" value={preApproval?.memberNationalNumber ?? preApproval?.member?.nationalNumber} />
                      <InfoRow label="رقم البطاقة" value={preApproval?.memberCardNumber ?? '-'} />
                      <InfoRow label="جهة العمل" value={preApproval?.member?.employerName ?? preApproval?.employerName ?? '-'} />
                      {/* NOTE: InsuranceCompany/InsurancePolicy/BenefitPackage fields REMOVED - Use BenefitPolicy only */}
                    </CardContent>
                  </Card>
                </Grid>

                {/* ===================== MEDICAL INFORMATION ===================== */}
                <Grid size={{ xs: 12, md: 6 }}>
                  <Card variant="outlined" sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                        <MedicalIcon color="primary" fontSize="small" />
                        <Typography variant="h6">المعلومات الطبية</Typography>
                      </Stack>
                      <Divider sx={{ mb: 2 }} />
                      <InfoRow label="مقدم الخدمة" value={preApproval?.providerName} />
                      <InfoRow label="الخدمة" value={preApproval?.serviceName ?? preApproval?.serviceCode ?? '-'} />
                      <InfoRow label="التشخيص" value={preApproval?.diagnosisDescription ?? preApproval?.diagnosisCode ?? '-'} />
                    </CardContent>
                  </Card>
                </Grid>

                {/* ===================== ATTACHMENTS SECTION ===================== */}
                <Grid size={12}>
                  <Card variant="outlined">
                    <CardContent>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                        <AttachmentIcon color="primary" fontSize="small" />
                        <Typography variant="h6">المرفقات والمستندات</Typography>
                      </Stack>
                      <Divider sx={{ mb: 2 }} />

                      {/* Upload Section - Only for non-finalized statuses */}
                      {preApproval?.status !== 'APPROVED' && preApproval?.status !== 'REJECTED' && preApproval?.status !== 'CANCELLED' && (
                        <Box sx={{ mb: 3 }}>
                          <Alert severity="info" sx={{ mb: 2 }}>
                            <Typography variant="body2">
                              يمكنك رفع المستندات الداعمة مثل التقارير الطبية، الفحوصات، الأشعة وغيرها
                            </Typography>
                          </Alert>
                          <FileUploader
                            uploadFn={async (file, attachmentType) => {
                              return await uploadPreAuthAttachment(id, file, attachmentType);
                            }}
                            attachmentTypes={[
                              { value: 'MEDICAL_REPORT', label: 'تقرير طبي' },
                              { value: 'LAB_RESULT', label: 'نتائج مختبر' },
                              { value: 'RADIOLOGY', label: 'أشعة / تصوير' },
                              { value: 'PRESCRIPTION', label: 'وصفة طبية' },
                              { value: 'REFERRAL', label: 'تحويل طبي' },
                              { value: 'OTHER', label: 'مستند آخر' }
                            ]}
                            onUploadSuccess={handleUploadSuccess}
                            maxSize={10 * 1024 * 1024}
                            accept="application/pdf,image/jpeg,image/png"
                            label="رفع مستند داعم"
                          />
                        </Box>
                      )}

                      {/* Attachments List */}
                      {loadingAttachments ? (
                        <Box display="flex" justifyContent="center" py={3}>
                          <CircularProgress size={24} />
                        </Box>
                      ) : (
                        <AttachmentList
                          attachments={attachments}
                          onPreview={handlePreviewDocument}
                          onDownload={handleDownloadAttachment}
                          onDelete={handleDeleteAttachment}
                          canDelete={
                            preApproval?.status !== 'APPROVED' && preApproval?.status !== 'REJECTED' && preApproval?.status !== 'CANCELLED'
                          }
                          emptyMessage="لا توجد مرفقات. يمكنك رفع المستندات الداعمة من الأعلى."
                        />
                      )}
                    </CardContent>
                  </Card>
                </Grid>

                {/* ===================== FINANCIAL INFORMATION ===================== */}
                <Grid size={12}>
                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        المعلومات المالية والموافقة
                      </Typography>
                      <Divider sx={{ mb: 3 }} />

                       <Alert severity="info">
                        هذه الموافقة المسبقة إدارية وطبية فقط ولا تترتب عليها التزامات مالية في هذه المرحلة.
                      </Alert>

                      {/* Reviewer Comment */}
                      {preApproval?.reviewerComment && (
                        <Box
                          sx={{
                            mt: 3,
                            p: 2,
                            borderRadius: 1,
                            bgcolor: preApproval?.status === 'REJECTED' ? 'error.lighter' : 'success.lighter'
                          }}
                        >
                          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                            تعليق المراجع
                          </Typography>
                          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                            {preApproval.reviewerComment}
                          </Typography>
                        </Box>
                      )}

                      {/* Review Date */}
                      {preApproval?.reviewedAt && (
                        <Box sx={{ mt: 2 }}>
                          <InfoRow label="تاريخ المراجعة" value={new Date(preApproval.reviewedAt).toLocaleString('ar-KW')} />
                        </Box>
                      )}
                    </CardContent>
                  </Card>
                </Grid>

                {/* ===================== ACTION HINT (APPROVED ONLY) ===================== */}
                {preApproval?.status === 'APPROVED' && (
                  <Grid size={12}>
                    <Card variant="outlined" sx={{ bgcolor: 'info.lighter', borderColor: 'info.light' }}>
                      <CardContent>
                        <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
                          <Box>
                            <Typography variant="h6" gutterBottom>
                              تحويل إلى مطالبة
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              يمكنك تحويل هذه الموافقة المسبقة إلى مطالبة بعد تقديم الخدمة للمؤمَّن عليه
                            </Typography>
                          </Box>
                          {/* Placeholder button - disabled until implementation */}
                          <Button variant="contained" color="info" startIcon={<ClaimIcon />} disabled sx={{ opacity: 0.7 }}>
                            تحويل إلى مطالبة
                          </Button>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                )}

                {/* ===================== AUDIT INFORMATION ===================== */}
                <Grid size={12}>
                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        معلومات التدقيق
                      </Typography>
                      <Divider sx={{ mb: 2 }} />
                      <Grid container spacing={2}>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <InfoRow
                            label="تاريخ الإنشاء"
                            value={preApproval?.createdAt ? new Date(preApproval.createdAt).toLocaleString('ar-KW') : '-'}
                          />
                        </Grid>
                        <Grid size={{ xs: 12, md: 6 }}>
                          <InfoRow
                            label="تاريخ آخر تحديث"
                            value={preApproval?.updatedAt ? new Date(preApproval.updatedAt).toLocaleString('ar-KW') : '-'}
                          />
                        </Grid>
                        {preApproval?.createdBy && (
                          <Grid size={{ xs: 12, md: 6 }}>
                            <InfoRow label="أنشئ بواسطة" value={preApproval.createdBy} />
                          </Grid>
                        )}
                        {preApproval?.updatedBy && (
                          <Grid size={{ xs: 12, md: 6 }}>
                            <InfoRow label="آخر تحديث بواسطة" value={preApproval.updatedBy} />
                          </Grid>
                        )}
                      </Grid>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Stack>
          </MainCard>
        </Grid>

        {/* ===================== DOCUMENT SIDE PANEL (SPLIT LAYOUT) ===================== */}
        {showDocumentPanel && (
          <Grid size={{ xs: 12, md: 5 }}>
            <Box sx={{ position: 'sticky', top: 16, height: 'calc(100vh - 100px)' }}>
              {/* If no document selected, show list or empty state */}
              {!previewDocument ? (
                <DocumentSidePanel
                  documents={documentPanelData}
                  loading={loadingAttachments}
                  onRefresh={fetchAttachments}
                  onSelect={(doc) => {
                    setPreviewDocument({
                      fileUrl: buildDocumentPreviewUrl(doc),
                      fileName: doc.fileName,
                      fileType: doc.fileType,
                      mimeType: doc.mimeType
                    });
                  }}
                  downloadUrlBuilder={buildDocumentPreviewUrl}
                  variant="list"
                  title="المستندات المرفقة"
                  emptyMessage="لا توجد مستندات مرفقة بهذا الطلب"
                />
              ) : (
                <DocumentPreviewPanel
                  fileUrl={previewDocument.fileUrl}
                  fileType={previewDocument.fileType || previewDocument.mimeType}
                  fileName={previewDocument.fileName}
                  onClose={() => setPreviewDocument(null)}
                />
              )}
            </Box>
          </Grid>
        )}
      </Grid>

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* Document Side Viewer - REMOVED (Replaced by Split View) */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
    </>
  );
};

export default PreApprovalView;
