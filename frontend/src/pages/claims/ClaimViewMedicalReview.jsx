/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 📋 CLAIM VIEW (MEDICAL REVIEW) - Reference Implementation
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Example implementation of the new Medical Review UX System
 *
 * This is a REFERENCE IMPLEMENTATION showing how to use:
 * - UnifiedAttachmentViewer
 * - MedicalDecisionPanel
 * - MedicalReviewLayout
 *
 * Use this as a template for:
 * - Pre-Authorization View
 * - Approvals View
 * - Any medical review workflow
 *
 * @version 2.0 - Medical Review UX Optimization
 * @date 2026-02-07
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Stack,
  Divider,
  Table,
  TableHead,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Alert,
  CircularProgress,
  TextField,
  Button,
  IconButton,
  MenuItem,
  Paper
} from '@mui/material';
import {
  Receipt as ClaimIcon,
  Person as PersonIcon,
  Business as EmployerIcon,
  CreditCard as PolicyIcon,
  MedicalServices as ServiceIcon,
  Assessment as DiagnosisIcon,
  AttachMoney as CostIcon,
  Chat as ChatIcon,
  Send as SendIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  HelpOutline as ClarifyIcon,
  NavigateBefore as PreviousIcon,
  NavigateNext as NextIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import { alpha } from '@mui/material/styles';

// Components
import { ModernPageHeader } from 'components/tba';
import { UnifiedAttachmentViewer, MedicalReviewLayout } from 'components/medical-review';

// Services
import { claimsService } from 'services/api';
import { getClaimAttachments, downloadClaimAttachment } from 'services/api/files.service';

// Utils
import { formatCurrency, formatDate } from 'utils/formatters';

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

/**
 * Compact Info Row
 */
const InfoRow = ({ label, value, icon: Icon }) => (
  <Box sx={{ mb: 1 }}>
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
      {Icon && <Icon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />}
      <Typography variant="caption" color="text.secondary" fontWeight={500}>
        {label}
      </Typography>
    </Stack>
    <Typography variant="body2" fontWeight={500}>
      {value || '-'}
    </Typography>
  </Box>
);

const hasValue = (value) => value !== null && value !== undefined && `${value}`.trim() !== '';
const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return `${value}`;
  return date.toLocaleString('ar-LY', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const SERVICE_DECISION = {
  APPROVE: 'APPROVE',
  REJECT: 'REJECT',
  CLARIFY: 'CLARIFY'
};
const REVIEW_ACTION_ALLOWED_STATUSES = new Set(['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'NEEDS_CORRECTION']);
const FINALIZED_STATUSES = new Set(['APPROVED', 'REJECTED', 'BATCHED', 'SETTLED']);

const REJECTION_REASONS = [
  'خدمة غير مغطاة',
  'نقص مستندات',
  'عدم مطابقة التشخيص',
  'تجاوز حدود المنفعة',
  'تكرار الخدمة'
];

const normalizeText = (value) =>
  `${value || ''}`
    .toLowerCase()
    .replace(/[\u064B-\u065F]/g, '')
    .replace(/[^\u0600-\u06FFa-z0-9\s]/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

/**
 * Collapsible Section Card
 */
const SectionCard = ({ title, icon: Icon, children, defaultExpanded = true }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <Card
      sx={{
        mb: '0.75rem',
        border: 1,
        borderColor: 'divider',
        boxShadow: 1
      }}
    >
      <CardContent sx={{ p: '1.0rem', '&:last-child': { pb: '1.0rem' } }}>
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ mb: expanded ? 2 : 0, cursor: 'pointer' }}
          onClick={() => setExpanded(!expanded)}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            {Icon && <Icon color="primary" />}
            <Typography variant="subtitle2" fontWeight={600}>
              {title}
            </Typography>
          </Stack>
          <Chip
            label={expanded ? 'إخفاء' : 'عرض'}
            size="small"
            variant="outlined"
            onClick={(e) => {
              e.stopPropagation();
              setExpanded(!expanded);
            }}
          />
        </Stack>
        {expanded && children}
      </CardContent>
    </Card>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const ClaimViewMedicalReview = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  // State
  const [claim, setClaim] = useState(null);
  const [attachments, setAttachments] = useState([]);
  const [medicalNotes, setMedicalNotes] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [draftSavedAt, setDraftSavedAt] = useState(null);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [serviceDecisions, setServiceDecisions] = useState({});
  const [activeServiceKey, setActiveServiceKey] = useState(null);
  const [selectedAttachmentId, setSelectedAttachmentId] = useState(null);

  const draftStorageKey = useMemo(() => `claim-review-draft-${id}`, [id]);
  const chatStorageKey = useMemo(() => `claim-review-chat-${id}`, [id]);

  const currentUser = useMemo(() => {
    try {
      const localUser = localStorage.getItem('user_details');
      if (localUser) {
        return JSON.parse(localUser);
      }
    } catch (error) {
      console.warn('Unable to parse user_details from localStorage:', error);
    }

    try {
      const sessionUser = sessionStorage.getItem('user');
      if (sessionUser) {
        return JSON.parse(sessionUser);
      }
    } catch (error) {
      console.warn('Unable to parse user from sessionStorage:', error);
    }

    return {};
  }, []);

  const currentUserName = currentUser?.fullName || currentUser?.name || currentUser?.username || 'المراجع الطبي';
  const currentUserRole = currentUser?.role || (Array.isArray(currentUser?.roles) ? currentUser.roles[0] : null) || 'MEDICAL_REVIEWER';

  const mapAttachment = useCallback(async (attachment) => {
    const directUrl = attachment.fileUrl || attachment.url || attachment.downloadUrl || '';
    const mimeType = attachment.contentType || attachment.mimeType || attachment.fileType || '';

    return {
      id: attachment.id,
      fileName: attachment.fileName || attachment.originalFileName || attachment.name,
      fileSize: attachment.fileSize || attachment.size,
      mimeType,
      fileType: attachment.fileType,
      url: directUrl,
      downloadUrl: directUrl
    };
  }, []);

  const fetchAttachments = useCallback(async (fallbackAttachments = []) => {
    try {
      const response = await getClaimAttachments(id);
      const rawAttachments = Array.isArray(response) ? response : response?.data || response?.items || [];
      const transformed = await Promise.all(rawAttachments.map((attachment) => mapAttachment(attachment)));
      setAttachments(transformed);
      return;
    } catch (error) {
      console.error('Error fetching attachments from endpoint, using fallback:', error);
    }

    if (Array.isArray(fallbackAttachments) && fallbackAttachments.length > 0) {
      const transformed = await Promise.all(fallbackAttachments.map((attachment) => mapAttachment(attachment)));
      setAttachments(transformed);
    } else {
      setAttachments([]);
    }
  }, [id, mapAttachment]);

  const normalizedClaim = useMemo(() => {
    if (!claim) return null;

    const claimServices = claim.lines || claim.services || claim.claimServices || claim.items || claim.lineItems || [];
    const services = Array.isArray(claimServices)
      ? claimServices.map((service, index) => ({
        id: service.id,
        serviceKey: service.id ? `id-${service.id}` : `${service.medicalServiceCode || service.serviceCode || 'service'}-${index}`,
        serviceName:
          service.medicalServiceName ||
          service.serviceName ||
          service.name ||
          service.description ||
          service.procedureName ||
          '-',
        serviceCode: service.medicalServiceCode || service.serviceCode || service.code || service.procedureCode || '-',
        quantity: service.quantity || 1,
        unitPrice: service.unitPrice ?? service.price ?? service.netPrice ?? 0,
        totalAmount: service.totalPrice ?? service.totalAmount ?? service.claimedAmount ?? 0,
        medicalServiceId: service.medicalServiceId,
        pricingItemId: service.pricingItemId,
        benefitLimit: service.benefitLimit,
        usedAmount: service.usedAmount,
        remainingAmount: service.remainingAmount
      }))
      : claim.serviceName
        ? [
          {
            serviceKey: `single-${claim.serviceCode || 'service'}`,
            serviceName: claim.serviceName,
            serviceCode: claim.serviceCode,
            quantity: claim.quantity || 1,
            unitPrice: claim.unitPrice || claim.requestedAmount || claim.claimedAmount || 0,
            totalAmount: claim.totalAmount || claim.requestedAmount || claim.claimedAmount || 0
          }
        ]
        : [];

    return {
      claimNumber: claim.claimNumber || `CLM-${claim.id || id}`,
      status: claim.status,
      allowedNextStatuses: Array.isArray(claim.allowedNextStatuses) ? claim.allowedNextStatuses : [],
      memberName: claim.memberName || claim.memberFullName || claim.member?.fullName || claim.member?.name,
      memberCivilId: claim.memberNationalNumber || claim.memberCivilId || claim.member?.nationalId || claim.member?.civilId,
      memberCardNumber: claim.memberCardNumber || claim.member?.cardNumber,
      memberPhone: claim.memberPhone || claim.member?.phone,
      employerName: claim.employerName || claim.employer?.name,
      policyNumber: claim.policyNumber || claim.benefitPackageCode || claim.member?.policyNumber,
      coverageType: claim.coverageType || claim.benefitPackageName || claim.planType || claim.member?.coverageType,
      claimDate: claim.serviceDate || claim.claimDate || claim.submittedDate || claim.submissionDate || claim.createdAt,
      services,
      primaryDiagnosis: claim.diagnosisDescription || claim.primaryDiagnosis || claim.diagnosis || claim.primaryIcdDescription,
      icdCode: claim.diagnosisCode || claim.icdCode || claim.primaryIcdCode,
      secondaryDiagnosis: claim.secondaryDiagnosis,
      claimedAmount: claim.requestedAmount ?? claim.totalAmount ?? claim.claimedAmount ?? 0,
      approvedAmount: claim.approvedAmount ?? 0,
      copayAmount: claim.patientCoPay ?? claim.copayAmount ?? 0,
      medicalNotes: claim.medicalNotes || claim.reviewerComment || '',
      preApprovalReferenceNumber: claim.preApprovalReferenceNumber
    };
  }, [claim, id]);

  // Fetch claim data
  useEffect(() => {
    if (id) {
      fetchClaim();
    }
  }, [id]);

  const fetchClaim = async () => {
    try {
      setLoading(true);
      const data = await claimsService.getById(id);
      setClaim(data);

      const draftPayload = localStorage.getItem(draftStorageKey);
      if (draftPayload) {
        try {
          const parsedDraft = JSON.parse(draftPayload);
          setMedicalNotes(parsedDraft?.notes || data.medicalNotes || data.reviewerComment || '');
          setDraftSavedAt(parsedDraft?.updatedAt || null);
        } catch (draftError) {
          console.warn('Failed to parse draft payload, fallback to claim notes:', draftError);
          setMedicalNotes(data.medicalNotes || data.reviewerComment || '');
        }
      } else {
        setMedicalNotes(data.medicalNotes || data.reviewerComment || '');
      }

      await fetchAttachments(data?.attachments || []);
    } catch (error) {
      console.error('Error fetching claim:', error);
      enqueueSnackbar('فشل في تحميل المطالبة', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    try {
      const existingChat = localStorage.getItem(chatStorageKey);
      if (existingChat) {
        const parsedMessages = JSON.parse(existingChat);
        setChatMessages(Array.isArray(parsedMessages) ? parsedMessages : []);
      } else {
        setChatMessages([]);
      }
    } catch (error) {
      console.warn('Failed to parse chat history:', error);
      setChatMessages([]);
    }
  }, [chatStorageKey]);

  useEffect(() => {
    if (!normalizedClaim?.services?.length) {
      setServiceDecisions({});
      return;
    }

    setServiceDecisions((previousDecisions) => {
      const nextDecisions = {};
      normalizedClaim.services.forEach((service) => {
        const previous = previousDecisions[service.serviceKey];
        nextDecisions[service.serviceKey] = {
          decision: previous?.decision || SERVICE_DECISION.APPROVE,
          reason: previous?.reason || ''
        };
      });
      return nextDecisions;
    });
  }, [normalizedClaim?.services]);

  useEffect(() => {
    if (loading) return;

    const saveTimer = setTimeout(() => {
      const payload = {
        notes: medicalNotes || '',
        updatedAt: new Date().toISOString()
      };
      localStorage.setItem(draftStorageKey, JSON.stringify(payload));
      setDraftSavedAt(payload.updatedAt);
    }, 600);

    return () => clearTimeout(saveTimer);
  }, [medicalNotes, draftStorageKey, loading]);

  useEffect(() => {
    return () => {
      attachments.forEach((attachment) => {
        if (typeof attachment?.url === 'string' && attachment.url.startsWith('blob:')) {
          URL.revokeObjectURL(attachment.url);
        }
      });
    };
  }, [attachments]);

  // Decision handlers
  const ensureClaimUnderReview = useCallback(async () => {
    const latestClaim = await claimsService.getById(id);
    if (latestClaim?.status !== 'SUBMITTED') {
      return latestClaim;
    }

    try {
      await claimsService.startReview(id);
    } catch (error) {
      const isTransitionConflict = error?.status === 409 && error?.errorCode === 'INVALID_CLAIM_TRANSITION';
      if (!isTransitionConflict) {
        throw error;
      }
    }

    const afterStartReview = await claimsService.getById(id);
    if (afterStartReview?.status === 'SUBMITTED') {
      const transitionError = new Error('تعذر نقل المطالبة إلى قيد المراجعة. أعد تشغيل خدمة الباك-إند ثم أعد المحاولة.');
      transitionError.status = 409;
      transitionError.errorCode = 'INVALID_CLAIM_TRANSITION';
      throw transitionError;
    }

    return afterStartReview;
  }, [id]);

  const selectedApprovedAmount = useMemo(() => {
    if (!normalizedClaim?.services?.length) {
      return 0;
    }

    return normalizedClaim.services
      .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE)
      .reduce((sum, service) => sum + Number(service.totalAmount || 0), 0);
  }, [normalizedClaim?.services, serviceDecisions]);

  const selectedServicesCount = useMemo(() => {
    if (!normalizedClaim?.services?.length) return 0;
    return normalizedClaim.services.filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE).length;
  }, [normalizedClaim?.services, serviceDecisions]);

  const claimStatus = normalizedClaim?.status || '';

  const reviewLock = useMemo(() => {
    if (!claimStatus) {
      return {
        locked: false,
        severity: 'info',
        message: ''
      };
    }

    if (claimStatus === 'APPROVAL_IN_PROGRESS') {
      return {
        locked: true,
        severity: 'info',
        message: 'تم إرسال الموافقة مسبقًا، والمطالبة قيد المعالجة. لا يمكن إرسال قرار جديد الآن.'
      };
    }

    if (FINALIZED_STATUSES.has(claimStatus)) {
      const finalizedMessageByStatus = {
        APPROVED: 'تمت الموافقة على المطالبة مسبقًا، لذلك تم إخفاء أزرار الموافقة والرفض.',
        REJECTED: 'تم رفض المطالبة مسبقًا، لذلك لا يمكن تنفيذ موافقة أو رفض جديد.',
        BATCHED: 'المطالبة انتقلت إلى مرحلة الدفعات المالية، ولا يمكن تعديل قرار المراجعة الطبية.',
        SETTLED: 'المطالبة تمت تسويتها ماليًا، ولا يمكن تعديل قرار المراجعة الطبية.'
      };

      return {
        locked: true,
        severity: claimStatus === 'REJECTED' ? 'warning' : 'success',
        message: finalizedMessageByStatus[claimStatus] || 'حالة المطالبة الحالية لا تسمح باتخاذ قرار جديد.'
      };
    }

    if (!REVIEW_ACTION_ALLOWED_STATUSES.has(claimStatus)) {
      return {
        locked: true,
        severity: 'warning',
        message: `لا يمكن تنفيذ قرار جديد في الحالة الحالية (${claimStatus}).`
      };
    }

    return {
      locked: false,
      severity: 'info',
      message: ''
    };
  }, [claimStatus]);

  const hasRejectedServices = useMemo(() => {
    return Object.values(serviceDecisions).some((entry) => entry?.decision === SERVICE_DECISION.REJECT);
  }, [serviceDecisions]);

  const resolveLinkedAttachmentId = useCallback((service) => {
    if (!attachments.length || !service) return null;

    const serviceCode = normalizeText(service.serviceCode);
    const serviceName = normalizeText(service.serviceName);

    const matched = attachments.find((attachment) => {
      const candidate = normalizeText(`${attachment.fileName || ''} ${attachment.name || ''}`);
      return (serviceCode && candidate.includes(serviceCode)) || (serviceName && candidate.includes(serviceName));
    });

    return matched?.id || null;
  }, [attachments]);

  const handleServiceDecision = useCallback((serviceKey, decision) => {
    setServiceDecisions((previous) => ({
      ...previous,
      [serviceKey]: {
        decision,
        reason: decision === SERVICE_DECISION.REJECT ? previous[serviceKey]?.reason || REJECTION_REASONS[0] : ''
      }
    }));
  }, []);

  const handleServiceReason = useCallback((serviceKey, reason) => {
    setServiceDecisions((previous) => ({
      ...previous,
      [serviceKey]: {
        ...(previous[serviceKey] || { decision: SERVICE_DECISION.REJECT }),
        decision: SERVICE_DECISION.REJECT,
        reason
      }
    }));
  }, []);

  const handleServiceRowClick = useCallback(
    (service) => {
      setActiveServiceKey(service.serviceKey);
      const linkedId = resolveLinkedAttachmentId(service);
      if (linkedId) {
        setSelectedAttachmentId(linkedId);
      }
    },
    [resolveLinkedAttachmentId]
  );

  const handleApprove = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن تنفيذ الموافقة في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        if (!selectedServicesCount || selectedApprovedAmount <= 0) {
          enqueueSnackbar('يجب تحديد خدمة واحدة على الأقل للموافقة', { variant: 'warning' });
          return;
        }

        await claimsService.approve(id, {
          notes: notes?.trim() || `تمت الموافقة على ${selectedServicesCount} خدمة`,
          useSystemCalculation: true
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم إرسال الموافقة وجاري المعالجة', { variant: 'success' });
        navigate('/claims');
      } catch (error) {
        console.error('Error approving claim:', error);
        enqueueSnackbar(error.message || 'فشل في الموافقة على المطالبة', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [
      id,
      selectedServicesCount,
      selectedApprovedAmount,
      draftStorageKey,
      navigate,
      enqueueSnackbar
    ]
  );

  const handleReject = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن تنفيذ الرفض في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        const rejectedReasons = (normalizedClaim?.services || [])
          .filter((service) => serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT)
          .map((service) => `${service.serviceName}: ${serviceDecisions[service.serviceKey]?.reason || 'مرفوضة'}`);

        const composedReason = [notes?.trim(), ...rejectedReasons].filter(Boolean).join(' | ');

        await claimsService.reject(id, {
          rejectionReason: composedReason.length >= 10 ? composedReason : 'رفض المطالبة بعد المراجعة الطبية'
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم رفض المطالبة', { variant: 'info' });
        navigate('/claims');
      } catch (error) {
        console.error('Error rejecting claim:', error);
        enqueueSnackbar(error.message || 'فشل في رفض المطالبة', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, normalizedClaim?.services, serviceDecisions, draftStorageKey, navigate, enqueueSnackbar]
  );

  const handleRequestInfo = useCallback(
    async (notes) => {
      setSubmitting(true);
      try {
        if (reviewLock.locked) {
          enqueueSnackbar(reviewLock.message || 'لا يمكن طلب معلومات إضافية في الحالة الحالية.', { variant: 'warning' });
          return;
        }

        await ensureClaimUnderReview();

        await claimsService.returnForInfo(id, {
          reason: notes && notes.trim().length >= 10 ? notes.trim() : 'يرجى استكمال البيانات الطبية والمستندات المطلوبة'
        });

        localStorage.removeItem(draftStorageKey);
        enqueueSnackbar('تم طلب معلومات إضافية', { variant: 'info' });
        navigate('/claims');
      } catch (error) {
        console.error('Error requesting info:', error);
        enqueueSnackbar(error.message || 'فشل في طلب المعلومات', { variant: 'error' });
      } finally {
        setSubmitting(false);
      }
    },
    [id, ensureClaimUnderReview, draftStorageKey, navigate, enqueueSnackbar]
    [id, reviewLock, ensureClaimUnderReview, draftStorageKey, navigate, enqueueSnackbar]
  );

  const handleSendChatMessage = useCallback(() => {
    const text = chatInput.trim();
    if (!text) return;

    const message = {
      id: `${Date.now()}`,
      text,
      senderName: currentUserName,
      senderRole: currentUserRole,
      createdAt: new Date().toISOString()
    };

    setChatMessages((previousMessages) => {
      const updatedMessages = [...previousMessages, message];
      localStorage.setItem(chatStorageKey, JSON.stringify(updatedMessages));
      return updatedMessages;
    });

    setChatInput('');
  }, [chatInput, chatStorageKey, currentUserName, currentUserRole]);

  const handleSaveDraftNow = useCallback(() => {
    const payload = {
      notes: medicalNotes || '',
      updatedAt: new Date().toISOString()
    };
    localStorage.setItem(draftStorageKey, JSON.stringify(payload));
    setDraftSavedAt(payload.updatedAt);
    enqueueSnackbar('تم حفظ المسودة والعودة إلى قائمة المطالبات', { variant: 'success' });
    navigate('/claims');
  }, [medicalNotes, draftStorageKey, navigate, enqueueSnackbar]);

  const handleRestoreDraft = useCallback(() => {
    const draftPayload = localStorage.getItem(draftStorageKey);
    if (!draftPayload) {
      enqueueSnackbar('لا توجد مسودة محفوظة', { variant: 'info' });
      return;
    }

    try {
      const parsedDraft = JSON.parse(draftPayload);
      setMedicalNotes(parsedDraft?.notes || '');
      setDraftSavedAt(parsedDraft?.updatedAt || null);
      enqueueSnackbar('تم استعادة المسودة', { variant: 'success' });
    } catch (error) {
      console.error('Failed to restore draft:', error);
      enqueueSnackbar('تعذر استعادة المسودة', { variant: 'error' });
    }
  }, [draftStorageKey, enqueueSnackbar]);

  const handleClearDraft = useCallback(() => {
    localStorage.removeItem(draftStorageKey);
    setDraftSavedAt(null);
    enqueueSnackbar('تم مسح المسودة', { variant: 'info' });
  }, [draftStorageKey, enqueueSnackbar]);

  const handleDownload = useCallback(
    async (attachment) => {
      try {
        const blob = await downloadClaimAttachment(id, attachment.id);
        const fileName = attachment?.fileName || `attachment-${attachment?.id || 'file'}`;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
      } catch (error) {
        console.error('Error downloading attachment:', error);
        enqueueSnackbar('فشل في تحميل الملف', { variant: 'error' });
      }
    },
    [id, enqueueSnackbar]
  );

  // Loading state
  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '25.0rem' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!normalizedClaim) {
    return <Alert severity="error">لم يتم العثور على المطالبة</Alert>;
  }

  // ═══════════════════════════════════════════════════════════════════════
  // CENTER PANEL - MEDICAL DATA
  // ═══════════════════════════════════════════════════════════════════════
  const centerPanel = (
    <Box sx={{ maxWidth: '65.0rem', mx: 'auto', width: '100%', pb: '7.0rem' }}>
      <Stack spacing={1.5}>
        <Card
          sx={{
            borderRadius: '0.25rem',
            border: 1,
            borderColor: 'divider',
            bgcolor: (theme) => alpha(theme.palette.info.main, 0.08),
            boxShadow: 1
          }}
        >
          <CardContent sx={{ py: '0.75rem', '&:last-child': { pb: '0.75rem' } }}>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1} alignItems={{ xs: 'flex-start', md: 'center' }}>
              <Box>
                <Typography variant="subtitle1" fontWeight={700}>
                  {normalizedClaim.memberName || 'عضو غير معروف'}
                </Typography>
                <Typography variant="body2" color="text.secondary" fontWeight={600}>
                  رقم المطالبة: {normalizedClaim.claimNumber}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<PreviousIcon />}
                  onClick={() => navigate(`/claims/${Math.max(Number(id) - 1, 1)}/medical-review`)}
                  disabled={!Number.isFinite(Number(id)) || Number(id) <= 1}
                >
                  المطالبة السابقة
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  endIcon={<NextIcon />}
                  onClick={() => navigate(`/claims/${Number(id) + 1}/medical-review`)}
                  disabled={!Number.isFinite(Number(id))}
                >
                  المطالبة التالية
                </Button>
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        <Alert severity="info" sx={{ py: 1 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
            <Typography variant="caption" fontWeight={600}>
              {draftSavedAt ? `آخر حفظ للمسودة: ${formatDateTime(draftSavedAt)}` : 'لا توجد مسودة محفوظة'}
            </Typography>
            <Stack direction="row" spacing={0.5}>
              <Button size="small" variant="outlined" onClick={handleSaveDraftNow} disabled={submitting}>
                حفظ وخروج
              </Button>
              <Button size="small" variant="outlined" onClick={handleRestoreDraft} disabled={submitting}>
                استعادة
              </Button>
              <Button size="small" color="error" variant="outlined" onClick={handleClearDraft} disabled={submitting || !draftSavedAt}>
                مسح
              </Button>
            </Stack>
          </Stack>
        </Alert>

        {reviewLock.locked && reviewLock.message && (
          <Alert severity={reviewLock.severity} sx={{ py: 1 }}>
            {reviewLock.message}
          </Alert>
        )}

        {/* Patient Information */}
        <SectionCard title="معلومات المنتفع" icon={PersonIcon}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="الاسم الكامل" value={normalizedClaim.memberName} icon={PersonIcon} />
            </Grid>
            {hasValue(normalizedClaim.memberCivilId) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="الرقم المدني" value={normalizedClaim.memberCivilId} />
              </Grid>
            )}
            {hasValue(normalizedClaim.memberCardNumber) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="رقم البطاقة" value={normalizedClaim.memberCardNumber} />
              </Grid>
            )}
            {hasValue(normalizedClaim.memberPhone) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="رقم الجوال" value={normalizedClaim.memberPhone} />
              </Grid>
            )}
          </Grid>
        </SectionCard>

        {/* Policy & Coverage */}
        <SectionCard title="بيانات التأمين" icon={PolicyIcon}>
          <Grid container spacing={1.5}>
            {hasValue(normalizedClaim.employerName) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="جهة العمل" value={normalizedClaim.employerName} icon={EmployerIcon} />
              </Grid>
            )}
            {hasValue(normalizedClaim.policyNumber) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="رقم البوليصة" value={normalizedClaim.policyNumber} />
              </Grid>
            )}
            {hasValue(normalizedClaim.coverageType) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="نوع التغطية" value={normalizedClaim.coverageType} />
              </Grid>
            )}
            <Grid size={{ xs: 12, md: 6 }}>
              <InfoRow label="تاريخ المطالبة" value={formatDate(normalizedClaim.claimDate)} />
            </Grid>
            {hasValue(normalizedClaim.preApprovalReferenceNumber) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="رقم الموافقة المسبقة" value={normalizedClaim.preApprovalReferenceNumber} />
              </Grid>
            )}
          </Grid>
        </SectionCard>

        {/* Services Requested */}
        <SectionCard title="الخدمات المطلوبة" icon={ServiceIcon}>
          {normalizedClaim.services && normalizedClaim.services.length > 0 ? (
            <Stack spacing={1.25}>
              <Alert severity={selectedServicesCount > 0 ? 'success' : 'warning'} sx={{ py: 0.75 }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
                  <Typography variant="body2" fontWeight={600}>
                    الخدمات المحددة للموافقة: {selectedServicesCount} من {normalizedClaim.services.length}
                  </Typography>
                  <Typography variant="body2" fontWeight={700} color="success.main">
                    الإجمالي المعتمد: {formatCurrency(selectedApprovedAmount || 0)}
                  </Typography>
                </Stack>
              </Alert>

              <TableContainer sx={{ border: 1, borderColor: 'divider', borderRadius: '0.375rem' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: 'grey.100' }}>
                      <TableCell sx={{ fontWeight: 700 }}>الخدمة</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>سقف المنفعة</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>الرصيد المتبقي</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>الحالة السريعة</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>الكمية × السعر</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>الإجمالي</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {normalizedClaim.services.map((service, index) => (
                      <TableRow
                        key={service.serviceKey || index}
                        hover
                        selected={activeServiceKey === service.serviceKey}
                        onClick={() => handleServiceRowClick(service)}
                        sx={{ cursor: 'pointer' }}
                      >
                        <TableCell sx={{ py: 1 }}>
                          <Box>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.25 }}>
                              <Typography variant="body2" fontWeight={600}>
                                {service.serviceName}
                              </Typography>
                              {!service.medicalServiceId && service.pricingItemId && (
                                <Chip label="عقد مباشر" size="small" color="info" variant="outlined" sx={{ height: '1.125rem', fontSize: '0.75rem', fontWeight: 700 }} />
                              )}
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                              كود: {service.serviceCode}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell sx={{ py: 1 }}>
                          <Typography variant="body2" fontWeight={600} color={service.benefitLimit > 0 ? "primary.main" : "text.secondary"}>
                            {service.benefitLimit > 0 ? formatCurrency(service.benefitLimit) : '-'}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ py: 1 }}>
                          <Typography variant="body2" fontWeight={700} color={service.remainingAmount > 0 ? "success.main" : (service.benefitLimit > 0 ? "error.main" : "text.secondary")}>
                            {service.benefitLimit > 0 ? formatCurrency(service.remainingAmount ?? 0) : '-'}
                          </Typography>
                          {service.benefitLimit > 0 && (
                            <Typography variant="caption" display="block" color="text.secondary">
                              مستهلك: {formatCurrency(service.usedAmount ?? 0)}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell sx={{ py: 1 }} onClick={(event) => event.stopPropagation()}>
                          <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mb: serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT ? 0.75 : 0 }}>
                            <IconButton
                              size="small"
                              color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.APPROVE ? 'success' : 'default'}
                              onClick={() => handleServiceDecision(service.serviceKey, SERVICE_DECISION.APPROVE)}
                              disabled={reviewLock.locked || submitting}
                            >
                              <ApproveIcon fontSize="small" />
                            </IconButton>
                            <IconButton
                              size="small"
                              color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT ? 'error' : 'default'}
                              onClick={() => handleServiceDecision(service.serviceKey, SERVICE_DECISION.REJECT)}
                              disabled={reviewLock.locked || submitting}
                            >
                              <RejectIcon fontSize="small" />
                            </IconButton>
                            <IconButton
                              size="small"
                              color={serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.CLARIFY ? 'warning' : 'default'}
                              onClick={() => handleServiceDecision(service.serviceKey, SERVICE_DECISION.CLARIFY)}
                              disabled={reviewLock.locked || submitting}
                            >
                              <ClarifyIcon fontSize="small" />
                            </IconButton>
                          </Stack>
                          {serviceDecisions[service.serviceKey]?.decision === SERVICE_DECISION.REJECT && (
                            <TextField
                              select
                              size="small"
                              fullWidth
                              value={serviceDecisions[service.serviceKey]?.reason || REJECTION_REASONS[0]}
                              onChange={(event) => handleServiceReason(service.serviceKey, event.target.value)}
                              disabled={reviewLock.locked || submitting}
                            >
                              {REJECTION_REASONS.map((reason) => (
                                <MenuItem key={reason} value={reason}>
                                  {reason}
                                </MenuItem>
                              ))}
                            </TextField>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={{ py: 1 }}>
                          <Typography variant="body2" fontWeight={500}>
                            {service.quantity} × {formatCurrency(service.unitPrice)}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" sx={{ py: 1 }}>
                          <Typography variant="body2" fontWeight={600} color="primary">
                            {formatCurrency(service.totalAmount)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Typography variant="caption" color="text.secondary">
                بالنقر على أي خدمة سيتم فتح المستند المرتبط بها تلقائياً عند توفر تطابق بالاسم أو الكود.
              </Typography>
            </Stack>
          ) : (
            <Typography variant="body2" color="text.secondary">
              لا توجد خدمات
            </Typography>
          )}
        </SectionCard>

        {/* Diagnosis */}
        <SectionCard title="التشخيص" icon={DiagnosisIcon}>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: hasValue(normalizedClaim.icdCode) ? 6 : 12 }}>
              <InfoRow label="التشخيص الأساسي" value={normalizedClaim.primaryDiagnosis} />
            </Grid>
            {hasValue(normalizedClaim.icdCode) && (
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow label="ICD Code" value={normalizedClaim.icdCode} />
              </Grid>
            )}
            {normalizedClaim.secondaryDiagnosis && (
              <Grid size={12}>
                <InfoRow label="تشخيص ثانوي" value={normalizedClaim.secondaryDiagnosis} />
              </Grid>
            )}
          </Grid>
        </SectionCard>

        {/* Cost Summary */}
        <SectionCard title="ملخص التكاليف" icon={CostIcon}>
          <Stack spacing={1}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2">المبلغ المطالب به</Typography>
              <Typography variant="body2" fontWeight={600}>
                {formatCurrency(normalizedClaim.claimedAmount)}
              </Typography>
            </Box>
            <Divider />
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2">المبلغ الموافق عليه</Typography>
              <Typography variant="body2" fontWeight={600} color="success.main">
                {formatCurrency(selectedApprovedAmount || normalizedClaim.approvedAmount || 0)}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="caption" color="text.secondary">الخدمات المحددة للموافقة</Typography>
              <Typography variant="caption" color="text.secondary">{selectedServicesCount} خدمة</Typography>
            </Box>
            {normalizedClaim.copayAmount > 0 && (
              <>
                <Divider />
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2">التحمل</Typography>
                  <Typography variant="body2" fontWeight={600} color="warning.main">
                    {formatCurrency(normalizedClaim.copayAmount)}
                  </Typography>
                </Box>
              </>
            )}
          </Stack>
        </SectionCard>

        {/* Chat MVP */}
        <SectionCard title="محادثة المطالبة" icon={ChatIcon} defaultExpanded={false}>
          <Stack spacing={1.5}>
            <Box
              sx={{
                maxHeight: '13.75rem',
                overflowY: 'auto',
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                p: '0.75rem',
                bgcolor: 'background.default'
              }}
            >
              {chatMessages.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  لا توجد رسائل بعد
                </Typography>
              ) : (
                <Stack spacing={1}>
                  {chatMessages.map((message) => (
                    <Box key={message.id} sx={{ p: 1, bgcolor: 'background.paper', borderRadius: 1, border: 1, borderColor: 'divider' }}>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                        <Typography variant="caption" fontWeight={600}>
                          {message.senderName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatDateTime(message.createdAt)}
                        </Typography>
                      </Stack>
                      <Typography variant="body2">{message.text}</Typography>
                    </Box>
                  ))}
                </Stack>
              )}
            </Box>

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField
                fullWidth
                size="small"
                value={chatInput}
                onChange={(event) => setChatInput(event.target.value)}
                placeholder="اكتب رسالة داخل المطالبة..."
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    handleSendChatMessage();
                  }
                }}
              />
              <Button variant="contained" startIcon={<SendIcon />} onClick={handleSendChatMessage} disabled={!chatInput.trim()}>
                إرسال
              </Button>
            </Stack>
          </Stack>
        </SectionCard>

        {(hasRejectedServices || normalizedClaim.status === 'REJECTED') && (
          <SectionCard title="ملاحظات قرار الرفض" icon={RejectIcon} defaultExpanded={false}>
            <TextField
              fullWidth
              multiline
              rows={3}
              value={medicalNotes}
              onChange={(event) => setMedicalNotes(event.target.value)}
              placeholder="تظهر هذه الخانة عند الحاجة فقط (مثل الرفض)"
            />
          </SectionCard>
        )}
      </Stack>
    </Box>
  );

  return (
    <Box
      sx={{
        bgcolor: 'grey.50',
        minHeight: '100vh',
        fontFamily: 'Tajawal, IBM Plex Sans Arabic, Noto Sans Arabic, sans-serif'
      }}
    >
      {/* Page Header */}
      <ModernPageHeader
        title={`مطالبة رقم ${normalizedClaim.claimNumber}`}
        subtitle="مراجعة طبية"
        icon={ClaimIcon}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المطالبات', href: '/claims' }, { label: `#${normalizedClaim.claimNumber}` }]}
      />

      {/* 3-Panel Medical Review Layout */}
      <MedicalReviewLayout
        leftPanel={
          <UnifiedAttachmentViewer
            attachments={attachments}
            loading={false}
            onDownload={handleDownload}
            onRefresh={fetchAttachments}
            selectedAttachmentId={selectedAttachmentId}
            onSelectionChange={setSelectedAttachmentId}
            emptyMessage="لا توجد مستندات مرفقة بهذه المطالبة"
          />
        }
        centerPanel={centerPanel}
        rightPanel={null}
        documentsCount={attachments.length}
        showLeftPanel={true}
        showRightPanel={false}
        collapsible={true}
      />

      <Paper
        elevation={6}
        sx={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          zIndex: (theme) => theme.zIndex.drawer
        }}
      >
        <Box sx={{ maxWidth: '87.5rem', mx: 'auto', px: '1.0rem', py: '0.625rem' }}>
          <Stack direction={{ xs: 'column', md: 'row' }} alignItems={{ xs: 'stretch', md: 'center' }} justifyContent="space-between" spacing={1.5}>
            <Typography variant="body2" fontWeight={700}>
              إجمالي المبلغ الموافق عليه: {formatCurrency(selectedApprovedAmount || 0)}
            </Typography>
            {reviewLock.locked ? (
              <Chip
                color={reviewLock.severity === 'warning' ? 'warning' : 'success'}
                label={reviewLock.message || 'لا يمكن تنفيذ قرار جديد على هذه المطالبة'}
                variant="outlined"
              />
            ) : (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                <Button
                  variant="contained"
                  color="success"
                  startIcon={<ApproveIcon />}
                  onClick={() => handleApprove(medicalNotes)}
                  disabled={submitting || selectedServicesCount <= 0}
                  sx={{ boxShadow: 2 }}
                >
                  موافقة
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  startIcon={<RejectIcon />}
                  onClick={() => handleReject(medicalNotes)}
                  disabled={submitting}
                  sx={{ boxShadow: 2 }}
                >
                  رفض
                </Button>
                <Button
                  variant="contained"
                  color="info"
                  startIcon={<ClarifyIcon />}
                  onClick={() => handleRequestInfo(medicalNotes)}
                  disabled={submitting}
                  sx={{ boxShadow: 2 }}
                >
                  طلب معلومات
                </Button>
              </Stack>
            )}
          </Stack>
        </Box>
      </Paper>
    </Box>
  );
};

export default ClaimViewMedicalReview;




