/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║       PROVIDER CLAIMS SUBMISSION - Visit-Centric Canonical Architecture      ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  REBUILD: 2026-01-16                                                         ║
 * ║  REDESIGNED: 2026-01-29 - Desktop-First Professional UX                      ║
 * ║  ARCHITECTURAL LAWS ENFORCED:                                                ║
 * ║  ❌ No claim without Visit (visitId is MANDATORY)                            ║
 * ║  ❌ No free-text service (must select from dropdown)                         ║
 * ║  ❌ No manual price entry (price comes from Provider Contract)               ║
 * ║  ✅ Data Flow: Visit → Member → Contract → Services → Prices → Claim         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Grid,
  Alert,
  Divider,
  LinearProgress,
  Chip,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Autocomplete,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stepper,
  Step,
  StepLabel,
  Tooltip,
  alpha
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { formatCurrency } from 'utils/currency-formatter';
import {
  Send as SendIcon,
  Delete as DeleteIcon,
  Person as PersonIcon,
  MedicalServices as MedicalServicesIcon,
  ArrowBack as ArrowBackIcon,
  Add as AddIcon,
  CreditCard as CardIcon,
  LocalHospital as VisitIcon,
  Lock as LockIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Attachment as AttachmentIcon,
  CloudUpload as UploadIcon,
  Category as CategoryIcon,
  Healing as HealingIcon,
  Description as DiagnosisIcon,
  Notes as NotesIcon,
  Business as BusinessIcon,
  CalendarToday as CalendarIcon,
  Badge as BadgeIcon,
  AccountBalance as LimitIcon,
  Receipt as ReceiptIcon,
  Chat as ChatIcon,
  CloudDone as CloudDoneIcon,
  CloudOff as CloudOffIcon,
  Sync as SyncIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import SuccessDialog from 'components/SuccessDialog';
import { useAuth } from 'contexts/AuthContext';
import axiosClient from 'utils/axios';
import { MEDICAL_COLORS } from 'themes/provider-theme';

// ══════════════════════════════════════════════════════════════════════════════
// CONSTANTS & LABELS
// ══════════════════════════════════════════════════════════════════════════════
const LABELS = {
  pageTitle: 'إنشاء مطالبة',
  pageSubtitle: 'تقديم مطالبة تأمينية من سجل الزيارات',
  visitRequired: 'يجب الوصول لهذه الصفحة من سجل الزيارات',
  visitInfo: 'بيانات الزيارة',
  memberInfo: 'بيانات المؤمن عليه',
  serviceLines: 'الخدمات الطبية المطالب بها',
  addService: 'إضافة خدمة',
  selectCategory: 'التصنيف الطبي',
  selectService: 'الخدمة الطبية',
  quantity: 'الكمية',
  unitPrice: 'سعر الوحدة',
  totalPrice: 'الإجمالي',
  noContract: 'لا يوجد عقد لهذه الخدمة',
  preAuth: 'الموافقة المسبقة (اختياري)',
  selectPreAuth: 'اختر موافقة مسبقة',
  noPreAuth: 'لا توجد موافقات مسبقة متاحة',
  preAuthOptional: 'اختياري - يمكن ربط المطالبة بموافقة مسبقة إن وجدت',
  diagnosis: 'بيانات التشخيص',
  diagnosisCode: 'رمز التشخيص (ICD-10)',
  diagnosisCodeRequired: 'رمز التشخيص (ICD-10) مطلوب',
  diagnosisDescription: 'وصف التشخيص',
  notes: 'ملاحظات طبية',
  saveDraft: 'حفظ كمسودة',
  savingDraft: 'جاري حفظ المسودة...',
  submitFinal: 'تقديم نهائي للمراجعة',
  submittingFinal: 'جاري التقديم النهائي...',
  cancel: 'إلغاء',
  back: 'رجوع',
  totalClaimAmount: 'إجمالي المطالبة',
  remainingLimit: 'الحد المتبقي',
  annualLimit: 'الحد السنوي',
  usedAmount: 'المستخدم',
  attachments: 'المرفقات والمستندات',
  attachmentHint: 'يمكنك إرفاق التقارير الطبية، الفواتير، أو المستندات الداعمة',
  selectFiles: 'اختر ملفات للرفع',
  uploadingFiles: 'جاري رفع الملفات...',
  coverageInfo: 'معلومات التغطية'
};

const VISIT_TYPE_LABELS = {
  OUTPATIENT: 'عيادة خارجية',
  INPATIENT: 'تنويم',
  EMERGENCY: 'طوارئ',
  DENTAL: 'أسنان',
  OPTICAL: 'بصريات',
  DAY_CARE: 'رعاية يومية'
};

const MAX_UPLOAD_SIZE_MB = 10;
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024;
const ALLOWED_FILE_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'gif', 'doc', 'docx'];
const FILE_ACCEPT_ATTR = '.pdf,.jpg,.jpeg,.png,.gif,.doc,.docx';

// ══════════════════════════════════════════════════════════════════════════════
// STYLED COMPONENTS / SECTION COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Section Header Component
 */
const SectionHeader = ({ icon: Icon, title, subtitle, color = 'primary', action }) => (
  <Box sx={{ mb: '1.25rem' }}>
    <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="space-between">
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box
          sx={{
            width: '2.5rem',
            height: '2.5rem',
            borderRadius: '0.25rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: (theme) => alpha(theme.palette[color].main, 0.1),
            color: `${color}.main`
          }}
        >
          <Icon />
        </Box>
        <Box>
          <Typography variant="h6" fontWeight={600} color="text.primary">
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Stack>
      {action}
    </Stack>
  </Box>
);

/**
 * Read-Only Info Field
 */
const ReadOnlyField = ({ icon: Icon, label, value, highlight = false }) => (
  <Box sx={{ mb: '1.0rem' }}>
    <Typography
      variant="caption"
      color="text.secondary"
      sx={{
        display: 'block',
        mb: 0.5,
        fontWeight: 500,
        textTransform: 'uppercase',
        letterSpacing: 0.5,
        fontSize: '0.7rem'
      }}
    >
      {label}
    </Typography>
    <Stack direction="row" spacing={1} alignItems="center">
      {Icon && <Icon fontSize="small" color="action" sx={{ opacity: 0.7 }} />}
      <Typography variant="body1" fontWeight={highlight ? 600 : 400} color={highlight ? 'primary.main' : 'text.primary'}>
        {value || '—'}
      </Typography>
    </Stack>
  </Box>
);

/**
 * Info Card (Read-Only)
 */
const InfoCard = ({ children, bgcolor = 'grey.50' }) => (
  <Card
    variant="outlined"
    sx={{
      height: '100%',
      bgcolor,
      borderColor: 'divider',
      borderRadius: '0.25rem',
      transition: 'box-shadow 0.2s',
      '&:hover': {
        boxShadow: 1
      }
    }}
  >
    <CardContent sx={{ p: '1.5rem' }}>{children}</CardContent>
  </Card>
);

/**
 * Form Section Card
 */
const FormSection = ({ children, highlighted = false }) => (
  <Card
    variant="outlined"
    sx={{
      borderRadius: '0.25rem',
      borderColor: highlighted ? 'primary.main' : 'divider',
      borderWidth: highlighted ? 2 : 1,
      bgcolor: highlighted ? (theme) => alpha(theme.palette.primary.main, 0.02) : 'background.paper'
    }}
  >
    <CardContent sx={{ p: '1.5rem' }}>{children}</CardContent>
  </Card>
);

/**
 * Contract Price Chip
 */
const ContractPriceChip = ({ loading, price, hasContract, error }) => {
  if (loading) return <CircularProgress size={16} />;
  if (error) return <Chip label={error} color="error" size="small" />;
  if (!hasContract) return <Chip label={LABELS.noContract} color="warning" size="small" />;
  return (
    <Chip
      icon={<LockIcon fontSize="small" />}
      label={formatCurrency(price)}
      color="success"
      size="small"
      sx={{ fontWeight: 600 }}
    />
  );
};

// ══════════════════════════════════════════════════════════════════════════════
// BLOCKED ACCESS PAGE
// ══════════════════════════════════════════════════════════════════════════════
const BlockedAccessPage = ({ onBack }) => (
  <Box sx={{ maxWidth: '37.5rem', mx: 'auto', mt: '4.0rem' }}>
    <Card variant="outlined" sx={{ borderRadius: '0.1875rem', textAlign: 'center', p: '2.0rem' }}>
      <Box
        sx={{
          width: '5.0rem',
          height: '5.0rem',
          borderRadius: '50%',
          bgcolor: 'warning.lighter',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          mx: 'auto',
          mb: '1.5rem'
        }}
      >
        <LockIcon sx={{ fontSize: '2.5rem', color: 'warning.main' }} />
      </Box>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        الوصول المباشر غير مسموح
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: '2.0rem' }}>
        {LABELS.visitRequired}
        <br />
        يرجى الانتقال إلى سجل الزيارات واختيار زيارة لإنشاء مطالبة منها.
      </Typography>
      <Button variant="contained" size="large" startIcon={<ArrowBackIcon />} onClick={onBack} sx={{ borderRadius: '0.25rem', px: '2.0rem' }}>
        الذهاب إلى سجل الزيارات
      </Button>
    </Card>
  </Box>
);

// ══════════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ══════════════════════════════════════════════════════════════════════════════
export default function ProviderClaimsSubmission() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  const parseNumericParam = useCallback(
    (stateValue, queryKey) => {
      const candidate = stateValue ?? searchParams.get(queryKey);
      if (candidate === null || candidate === undefined || candidate === '') return null;
      const parsed = Number(candidate);
      return Number.isNaN(parsed) ? null : parsed;
    },
    [searchParams]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // THEME (MEDICAL THEME)
  // ═══════════════════════════════════════════════════════════════════════════
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const tableHeaderBg = isDark ? '#70c470ef' : MEDICAL_COLORS.primary.main;
  const tableHeaderColor = '#FFFFFF';

  // ═══════════════════════════════════════════════════════════════════════════
  // VISIT CONTEXT (FROM STATE OR URL PARAMS - supports refresh)
  // ═══════════════════════════════════════════════════════════════════════════
  const fromVisitLog = location.state?.fromVisitLog || searchParams.get('fromVisitLog') === 'true';
  const linkedVisitId = parseNumericParam(location.state?.visitId, 'visitId');
  const linkedMemberId = parseNumericParam(location.state?.memberId, 'memberId');
  const draftClaimId = parseNumericParam(location.state?.claimId, 'claimId');
  const linkedMemberName = location.state?.memberName || searchParams.get('memberName') || null;
  const linkedMemberCivilId = location.state?.memberCivilId || searchParams.get('memberCivilId') || null;
  const linkedMemberCardNumber = location.state?.memberCardNumber || searchParams.get('cardNumber') || null;
  const linkedEmployerName = location.state?.employerName || searchParams.get('employer') || null;
  const linkedMemberPhone = location.state?.memberPhone || searchParams.get('phone') || null;
  const linkedVisitDate = location.state?.visitDate || searchParams.get('visitDate') || null;
  const linkedVisitTime = location.state?.visitTime || searchParams.get('visitTime') || null;
  const linkedVisitType = location.state?.visitType || searchParams.get('visitType') || null;
  const linkedProviderName = location.state?.providerName || searchParams.get('providerName') || null;

  // SUPER_ADMIN check
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN');

  // ARCHITECTURAL ENFORCEMENT: Block direct access (SUPER_ADMIN can bypass).
  // Editing an existing draft claim is explicitly allowed without visit params.
  const accessBlocked = !linkedVisitId && !draftClaimId && !isSuperAdmin;

  // Provider from user session
  const userProviderId = user?.providerId || null;
  const userProviderName = user?.providerName || null;

  // ═══════════════════════════════════════════════════════════════════════════
  // STATE
  // ═══════════════════════════════════════════════════════════════════════════
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitMode, setSubmitMode] = useState(null);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Visit details loaded from backend
  const [visitDetails, setVisitDetails] = useState(null);

  // Member remaining limit
  const [memberLimit, setMemberLimit] = useState(null);

  // Medical services from Provider Contract
  const [availableServices, setAvailableServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(false);

  // Medical Categories
  const [medicalCategories, setMedicalCategories] = useState([]);
  const [loadingCategories, setLoadingCategories] = useState(false);

  // Claim Lines
  const [claimLines, setClaimLines] = useState([]);
  const [lineIdCounter, setLineIdCounter] = useState(1);

  // Pre-Authorizations (PHASE 5)
  const [availablePreAuths, setAvailablePreAuths] = useState([]);
  const [loadingPreAuths, setLoadingPreAuths] = useState(false);

  // Form Data
  const [formData, setFormData] = useState({
    diagnosisCode: '',
    diagnosisDescription: '',
    doctorName: '',
    notes: '',
    preAuthorizationId: ''
  });

  // Attachments State
  const [pendingFiles, setPendingFiles] = useState([]);
  const [existingAttachments, setExistingAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [activeClaimId, setActiveClaimId] = useState(draftClaimId);
  const [draftLoaded, setDraftLoaded] = useState(false);
  const [autosaveStatus, setAutosaveStatus] = useState('idle');
  const [autosaveAt, setAutosaveAt] = useState(null);
  const [localDraftRestored, setLocalDraftRestored] = useState(false);
  const [providerChatMessages, setProviderChatMessages] = useState([]);
  const [providerChatInput, setProviderChatInput] = useState('');

  const providerChatStorageKey = useMemo(
    () => `provider-claim-chat-${activeClaimId || linkedVisitId || 'new'}`,
    [activeClaimId, linkedVisitId]
  );

  const localDraftStorageKey = useMemo(
    () => `provider-claim-local-draft-${activeClaimId || draftClaimId || linkedVisitId || 'new'}`,
    [activeClaimId, draftClaimId, linkedVisitId]
  );

  const providerSenderName = user?.fullName || user?.name || user?.username || 'مقدم الخدمة';

  const normalizeId = useCallback((value) => {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }, []);

  const normalizeText = useCallback((value) => (value || '').toString().trim().toLowerCase(), []);

  useEffect(() => {
    try {
      const existingChat = localStorage.getItem(providerChatStorageKey);
      if (existingChat) {
        const parsed = JSON.parse(existingChat);
        setProviderChatMessages(Array.isArray(parsed) ? parsed : []);
      } else {
        setProviderChatMessages([]);
      }
    } catch (error) {
      console.warn('Failed to parse provider chat history:', error);
      setProviderChatMessages([]);
    }
  }, [providerChatStorageKey]);

  const handleSendProviderChatMessage = useCallback(() => {
    const text = providerChatInput.trim();
    if (!text) return;

    const message = {
      id: `${Date.now()}`,
      text,
      senderName: providerSenderName,
      senderRole: 'PROVIDER',
      createdAt: new Date().toISOString()
    };

    setProviderChatMessages((previousMessages) => {
      const updatedMessages = [...previousMessages, message];
      localStorage.setItem(providerChatStorageKey, JSON.stringify(updatedMessages));
      return updatedMessages;
    });

    setProviderChatInput('');
  }, [providerChatInput, providerSenderName, providerChatStorageKey]);

  const doesServiceMatchCategory = useCallback(
    (service, category) => {
      if (!service || !category) return false;

      const serviceCategoryId = normalizeId(service.categoryId || service.serviceCategoryId || service.medicalCategoryId);
      const selectedCategoryId = normalizeId(category.id);
      const byId = serviceCategoryId !== null && selectedCategoryId !== null && serviceCategoryId === selectedCategoryId;

      const serviceCategoryCode = normalizeText(service.categoryCode || service.category);
      const selectedCategoryCode = normalizeText(category.code);
      const byCode = !!serviceCategoryCode && !!selectedCategoryCode && serviceCategoryCode === selectedCategoryCode;

      const serviceCategoryName = normalizeText(service.categoryName || service.category);
      const selectedCategoryName = normalizeText(category.name);
      const byName = !!serviceCategoryName && !!selectedCategoryName && serviceCategoryName === selectedCategoryName;

      return byId || byCode || byName;
    },
    [normalizeId, normalizeText]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // INITIALIZATION
  // ═══════════════════════════════════════════════════════════════════════════
  useEffect(() => {
    if (linkedVisitId && !accessBlocked) {
      initializePage();
    }
  }, [linkedVisitId, accessBlocked]);

  useEffect(() => {
    if (!accessBlocked && draftClaimId && !draftLoaded && !loadingServices && !loadingCategories) {
      loadDraftClaim();
    }
  }, [accessBlocked, draftClaimId, draftLoaded, loadingServices, loadingCategories]);

  useEffect(() => {
    if (accessBlocked || draftClaimId || localDraftRestored || loadingServices || loadingCategories) return;

    try {
      const raw = localStorage.getItem(localDraftStorageKey);
      if (!raw) {
        setLocalDraftRestored(true);
        return;
      }

      const parsed = JSON.parse(raw);

      if (parsed?.formData) {
        setFormData((prev) => ({ ...prev, ...parsed.formData }));
      }

      if (Array.isArray(parsed?.claimLines) && parsed.claimLines.length > 0) {
        setClaimLines(parsed.claimLines);
        const maxId = parsed.claimLines.reduce((max, line) => Math.max(max, Number(line?.id || 0)), 0);
        setLineIdCounter(Math.max(maxId + 1, 1));
      }

      if (parsed?.savedAt) {
        setAutosaveAt(parsed.savedAt);
        setAutosaveStatus('saved');
      }
    } catch (restoreError) {
      console.warn('Failed to restore local provider claim draft:', restoreError);
    } finally {
      setLocalDraftRestored(true);
    }
  }, [accessBlocked, draftClaimId, localDraftRestored, loadingServices, loadingCategories, localDraftStorageKey]);

  useEffect(() => {
    if (accessBlocked || !localDraftRestored || submitting || success) return;

    const hasDraftContent =
      claimLines.length > 0 ||
      !!formData.diagnosisCode ||
      !!formData.diagnosisDescription ||
      !!formData.notes ||
      !!formData.preAuthorizationId;

    if (!hasDraftContent) return;

    setAutosaveStatus('saving');

    const timer = setTimeout(() => {
      try {
        const payload = {
          formData,
          claimLines,
          visitId: linkedVisitId,
          memberId: linkedMemberId,
          savedAt: new Date().toISOString()
        };
        localStorage.setItem(localDraftStorageKey, JSON.stringify(payload));
        setAutosaveAt(payload.savedAt);
        setAutosaveStatus('saved');
      } catch (saveError) {
        console.warn('Failed to autosave provider claim draft:', saveError);
        setAutosaveStatus('error');
      }
    }, 1200);

    return () => clearTimeout(timer);
  }, [
    accessBlocked,
    localDraftRestored,
    submitting,
    success,
    claimLines,
    formData,
    linkedVisitId,
    linkedMemberId,
    localDraftStorageKey
  ]);

  const initializePage = async () => {
    setLoading(true);
    try {
      const results = await Promise.allSettled([
        fetchVisitDetails(),
        fetchAvailableServices(),
        fetchMemberLimit(),
        fetchMedicalCategories(),
        fetchAvailablePreAuths()
      ]);

      results.forEach((result, index) => {
        const names = ['Visit Details', 'Services', 'Member Limit', 'Medical Categories', 'Pre-Authorizations'];
        if (result.status === 'rejected') {
          console.warn(`Failed to load ${names[index]}:`, result.reason);
        }
      });
    } catch (err) {
      console.error('Initialization error:', err);
      setError('فشل في تحميل البيانات');
    } finally {
      setLoading(false);
    }
  };

  const loadDraftClaim = async () => {
    if (!draftClaimId) return;

    try {
      const response = await axiosClient.get(`/claims/${draftClaimId}`);
      const claim = response.data?.data || response.data;

      if (!claim) {
        throw new Error('المطالبة غير موجودة');
      }

      if (claim.status && !['DRAFT', 'NEEDS_CORRECTION'].includes(claim.status)) {
        setError('هذه المطالبة ليست في حالة مسودة ولا يمكن تعديلها من بوابة مقدم الخدمة');
        setDraftLoaded(true);
        setActiveClaimId(claim.id || draftClaimId);
        return;
      }

      const claimLinesFromApi = claim.lines || claim.claimLines || [];
      const mappedLines = claimLinesFromApi.map((line, index) => {
        const serviceId = normalizeId(line.medicalServiceId ?? line.serviceId ?? line.medicalService?.id ?? null);
        const selectedService =
          availableServices.find((s) => normalizeId(s.id) === serviceId) ||
          availableServices.find((s) => s.code && (s.code === line.serviceCode || s.code === line.medicalServiceCode));

        const rawCategoryId =
          line.serviceCategoryId ??
          line.medicalCategoryId ??
          line.categoryId ??
          line.medicalService?.categoryId ??
          selectedService?.categoryId ??
          null;
        const resolvedCategoryId = normalizeId(rawCategoryId);
        const matchedCategory = medicalCategories.find((c) => normalizeId(c.id) === resolvedCategoryId) || null;
        const categoryServices = matchedCategory
          ? availableServices.filter((s) => doesServiceMatchCategory(s, matchedCategory))
          : resolvedCategoryId
            ? availableServices.filter((s) => normalizeId(s.categoryId) === resolvedCategoryId)
            : selectedService
              ? availableServices.filter((s) => normalizeId(s.categoryId) === normalizeId(selectedService.categoryId))
              : [];

        const unitPrice =
          line.unitPrice ?? line.requestedUnitPrice ?? line.priceAtSubmission ?? line.netAmount ?? line.totalAmount ?? selectedService?.price ?? 0;

        return {
          id: index + 1,
          medicalCategoryId: resolvedCategoryId || normalizeId(selectedService?.categoryId),
          medicalCategoryName:
            line.serviceCategoryName ||
            line.medicalCategoryName ||
            line.medicalService?.categoryName ||
            matchedCategory?.name ||
            selectedService?.category ||
            '',
          medicalServiceId: serviceId || normalizeId(selectedService?.id),
          serviceName: line.serviceName || line.medicalServiceName || line.medicalService?.name || selectedService?.name || '',
          serviceCode: line.serviceCode || line.medicalServiceCode || line.medicalService?.code || selectedService?.code || '',
          quantity: line.quantity || 1,
          unitPrice,
          hasContract: true,
          loadingPrice: false,
          priceError: null,
          requiresPA: selectedService?.requiresPA || false,
          filteredServices: categoryServices
        };
      });

      setFormData((prev) => ({
        ...prev,
        diagnosisCode: claim.diagnosisCode || '',
        diagnosisDescription: claim.diagnosisDescription || '',
        doctorName: claim.doctorName || '',
        notes: claim.notes || '',
        preAuthorizationId: claim.preAuthorizationId || claim.preAuthId || ''
      }));

      setClaimLines(mappedLines);
      setPendingFiles([]);
      setExistingAttachments(
        (claim.attachments || []).map((att) => ({
          id: att.id,
          fileName: att.fileName,
          fileType: att.fileType,
          attachmentType: att.description || att.attachmentType || 'OTHER',
          fileUrl: att.fileUrl,
          uploadedAt: att.uploadedAt || att.createdAt
        }))
      );
      setLineIdCounter((mappedLines?.length || 0) + 1);
      setDraftLoaded(true);
      const loadedClaimId = claim.id || draftClaimId;
      setActiveClaimId(loadedClaimId);
      if (loadedClaimId) {
        await fetchClaimAttachments(loadedClaimId);
      }
    } catch (err) {
      console.error('Failed to load draft claim:', err);
      setError(err?.response?.data?.message || 'فشل في تحميل المطالبة المحفوظة كمسودة');
      setDraftLoaded(true);
    }
  };

  const fetchMedicalCategories = async () => {
    setLoadingCategories(true);
    try {
      const response = await axiosClient.get('/provider/medical-categories');
      const categories = response.data?.data || response.data || [];
      setMedicalCategories(
        categories.map((category) => ({
          ...category,
          id: normalizeId(category.id),
          code: category.code || category.categoryCode || '',
          name: category.name || category.nameAr || category.nameEn || category.code || '—'
        }))
      );
    } catch (err) {
      console.error('Failed to fetch medical categories:', err);
      setMedicalCategories([]);
    } finally {
      setLoadingCategories(false);
    }
  };

  const fetchVisitDetails = async () => {
    if (!linkedVisitId) return;
    try {
      const response = await axiosClient.get(`/visits/${linkedVisitId}`);
      setVisitDetails(response.data?.data || response.data);
    } catch (err) {
      console.error('Failed to fetch visit:', err);
    }
  };

  const fetchAvailableServices = async () => {
    setLoadingServices(true);
    try {
      const response = await axiosClient.get('/provider/my-contract/services', {
        params: { size: 2000 }
      });

      const data = response.data?.data || response.data;
      const items = data?.content || data?.items || data || [];

      if (items.length === 0) {
        setAvailableServices([]);
        return;
      }

      setAvailableServices(
        items.map((item) => {
          const serviceId = normalizeId(item.medicalServiceId || item.serviceId || item.id);
          const requiresPreApproval =
            item.requiresPA ||
            item.requiresPreAuth ||
            item.requiresPreApproval ||
            item.requires_pre_auth ||
            false;
          return {
            id: serviceId,
            code: item.serviceCode,
            name: item.serviceName,
            categoryId: normalizeId(item.categoryId || item.serviceCategoryId || item.medicalCategoryId || item.effectiveCategory?.id),
            category: item.categoryName || item.effectiveCategory?.name || item.medicalCategory?.name || '',
            categoryCode: item.categoryCode || item.effectiveCategory?.code || item.medicalCategory?.code || '',
            requiresPA: requiresPreApproval,
            price: item.contractPrice,
            basePrice: item.basePrice,
            contractId: item.contractId,
            hasContract: item.hasContract !== false
          };
        })
      );
    } catch (err) {
      console.error('Failed to fetch services:', err);

      try {
        const response = await axiosClient.get('/provider/my-services');
        const services = response.data?.data || response.data || [];
        setAvailableServices(
          services.map((s) => ({
            id: normalizeId(s.serviceId || s.id),
            code: s.service_code || s.serviceCode || s.code,
            name: s.service_name || s.serviceName || s.name,
            categoryId: normalizeId(s.category_id || s.categoryId || s.serviceCategoryId),
            category: s.category_name || s.categoryName || s.category || '',
            categoryCode: s.category_code || s.categoryCode || '',
            requiresPA: s.requires_pre_auth ?? s.requiresPreAuth ?? s.requiresPA ?? false,
            hasContract: true
          }))
        );
      } catch (fallbackErr) {
        setAvailableServices([]);
      }
    } finally {
      setLoadingServices(false);
    }
  };

  // Filter services by category (NO LONGER EXCLUDE PA-required services)
  // ALL services are shown with a Badge indicator if they require pre-approval
  const filteredServices = useMemo(() => {
    return availableServices; // Show ALL services
  }, [availableServices]);

  const fetchMemberLimit = async () => {
    if (!linkedMemberId) return;
    try {
      const response = await axiosClient.get(`/members/${linkedMemberId}/remaining-limit`);
      setMemberLimit(response.data?.data || response.data);
    } catch (err) {
      console.error('Failed to fetch member limit:', err);
      setMemberLimit(null);
    }
  };

  /**
   * Fetch available pre-authorizations for the member (PHASE 5)
   * Only show APPROVED or ACKNOWLEDGED pre-auths
   */
  const fetchAvailablePreAuths = async () => {
    if (!linkedMemberId) return;
    setLoadingPreAuths(true);
    try {
      const response = await axiosClient.get(`/pre-authorizations/member/${linkedMemberId}`);
      const payload = response.data?.data ?? response.data ?? [];
      const preAuths = Array.isArray(payload)
        ? payload
        : Array.isArray(payload?.content)
          ? payload.content
          : Array.isArray(payload?.items)
            ? payload.items
            : [];

      // Filter only APPROVED or ACKNOWLEDGED (backend will handle validation)
      const usablePreAuths = preAuths.filter((pa) => pa.status === 'APPROVED' || pa.status === 'ACKNOWLEDGED');

      setAvailablePreAuths(usablePreAuths);
    } catch (err) {
      console.error('Failed to fetch pre-authorizations:', err);
      setAvailablePreAuths([]);
    } finally {
      setLoadingPreAuths(false);
    }
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // CONTRACT PRICE RESOLUTION
  // ═══════════════════════════════════════════════════════════════════════════
  const fetchContractPrice = useCallback(
    async (serviceCode, lineId) => {
      if (!userProviderId || !serviceCode) return;

      const cachedService = availableServices.find((s) => s.code === serviceCode);
      if (cachedService && cachedService.hasContract && cachedService.price !== undefined) {
        setClaimLines((prev) =>
          prev.map((line) =>
            line.id === lineId
              ? {
                ...line,
                unitPrice: cachedService.price,
                hasContract: true,
                loadingPrice: false,
                priceError: null
              }
              : line
          )
        );
        return;
      }

      setClaimLines((prev) => prev.map((line) => (line.id === lineId ? { ...line, loadingPrice: true, priceError: null } : line)));

      try {
        const response = await axiosClient.get(`/provider/my-services/${serviceCode}/price`);
        const priceData = response.data?.data || response.data;

        if (priceData.hasContract && priceData.contractPrice != null) {
          setClaimLines((prev) =>
            prev.map((line) =>
              line.id === lineId
                ? {
                  ...line,
                  unitPrice: priceData.contractPrice,
                  hasContract: true,
                  loadingPrice: false
                }
                : line
            )
          );
        } else {
          setClaimLines((prev) =>
            prev.map((line) =>
              line.id === lineId
                ? {
                  ...line,
                  unitPrice: 0,
                  hasContract: false,
                  loadingPrice: false,
                  priceError: LABELS.noContract
                }
                : line
            )
          );
        }
      } catch (err) {
        setClaimLines((prev) =>
          prev.map((line) =>
            line.id === lineId
              ? {
                ...line,
                unitPrice: 0,
                hasContract: false,
                loadingPrice: false,
                priceError: LABELS.noContract
              }
              : line
          )
        );
      }
    },
    [userProviderId, availableServices]
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // CLAIM LINE MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════
  const addClaimLine = () => {
    setClaimLines((prev) => [
      ...prev,
      {
        id: lineIdCounter,
        medicalCategoryId: null,
        medicalCategoryName: '',
        medicalServiceId: null,
        serviceName: '',
        serviceCode: '',
        quantity: 1,
        unitPrice: 0,
        hasContract: false,
        loadingPrice: false,
        priceError: null,
        requiresPA: false,
        filteredServices: []
      }
    ]);
    setLineIdCounter((prev) => prev + 1);
  };

  const removeClaimLine = (lineId) => {
    setClaimLines((prev) => prev.filter((line) => line.id !== lineId));
  };

  const updateClaimLine = (lineId, field, value) => {
    setClaimLines((prev) => prev.map((line) => (line.id === lineId ? { ...line, [field]: value } : line)));
  };

  const handleLineCategoryChange = async (lineId, category) => {
    if (!category) {
      setClaimLines((prev) =>
        prev.map((line) =>
          line.id === lineId
            ? {
              ...line,
              medicalCategoryId: null,
              medicalCategoryName: '',
              medicalServiceId: null,
              serviceName: '',
              serviceCode: '',
              unitPrice: 0,
              hasContract: false,
              filteredServices: [],
              priceError: null
            }
            : line
        )
      );
      return;
    }

    const categoryServices = availableServices.filter((s) => doesServiceMatchCategory(s, category));

    setClaimLines((prev) =>
      prev.map((line) =>
        line.id === lineId
          ? {
            ...line,
            medicalCategoryId: normalizeId(category.id),
            medicalCategoryName: category.name,
            medicalServiceId: null,
            serviceName: '',
            serviceCode: '',
            unitPrice: 0,
            hasContract: false,
            filteredServices: categoryServices,
            priceError: null
          }
          : line
      )
    );
  };

  const handleServiceSelect = (lineId, service) => {
    if (!service) {
      updateClaimLine(lineId, 'medicalServiceId', null);
      updateClaimLine(lineId, 'serviceName', '');
      updateClaimLine(lineId, 'serviceCode', '');
      updateClaimLine(lineId, 'unitPrice', 0);
      updateClaimLine(lineId, 'hasContract', false);
      updateClaimLine(lineId, 'requiresPA', false);
      return;
    }

    const currentLine = claimLines.find((l) => l.id === lineId);
    if (!currentLine?.medicalCategoryId) {
      return;
    }

    const requiresPreApproval = service.requiresPreApproval || service.requiresPreAuth || service.requiresPA || false;
    if (requiresPreApproval) {
      setError('هذه الخدمة تتطلب موافقة مسبقة ولا يمكن إضافتها مباشرة في المطالبة. يرجى إنشاء موافقة مسبقة أولاً.');
      return;
    }

    const isDuplicate = claimLines.some(l => l.id !== lineId && l.medicalServiceId === service.id);
    if (isDuplicate) {
      setError('هذه الخدمة مضافة بالفعل في بند آخر');
      return;
    }

    const hasContractPrice = service.hasContract !== false && service.price !== undefined && service.price !== null;

    setClaimLines((prev) =>
      prev.map((line) =>
        line.id === lineId
          ? {
            ...line,
            medicalServiceId: service.id,
            serviceName: service.name,
            serviceCode: service.code,
            unitPrice: hasContractPrice ? service.price : 0,
            hasContract: hasContractPrice,
            loadingPrice: false,
            priceError: hasContractPrice ? null : LABELS.noContract,
            requiresPA: service.requiresPA || false
          }
          : line
      )
    );

    if (!hasContractPrice) {
      fetchContractPrice(service.code, lineId);
    }
  };

  // Calculate totals
  /**
   * ⚠️ UX-ONLY CALCULATION - NOT SENT TO BACKEND
   *
   * These calculations are for DISPLAY PURPOSES ONLY during claim creation.
   * The backend RECALCULATES all amounts from database pricing when processing claims.
   *
   * SAFETY NOTES:
   * - These values help providers preview expected amounts
   * - Backend validates against actual contract pricing (may differ)
   * - Real amounts come from backend after claim submission
   * - This is NOT settlement calculation (settlement uses backend-approved amounts)
   */
  const calculateLineTotal = (line) => (line.unitPrice || 0) * (line.quantity || 1);
  const totalClaimAmount = claimLines.reduce((sum, line) => sum + calculateLineTotal(line), 0);

  // Validation checks
  const linesWithoutCategory = claimLines.filter((line) => !line.medicalCategoryId);
  const hasCategoryViolation = linesWithoutCategory.length > 0;
  const isFormValid = claimLines.length > 0 && !hasCategoryViolation && claimLines.every((l) => l.medicalServiceId && l.hasContract);

  const hasVisitAndDiagnosis = !!linkedVisitId && !!formData.diagnosisCode?.trim();
  const hasServicesReady = isFormValid;
  const hasAttachmentsReady = true; // Attachments are optional now
  const workflowSteps = ['بيانات المطالبة', 'الخدمات الطبية', 'المرفقات'];
  const workflowActiveStep = !hasVisitAndDiagnosis ? 0 : !hasServicesReady ? 1 : 2;

  // ═══════════════════════════════════════════════════════════════════════════
  // ATTACHMENT HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════
  const handleFileSelect = (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    const validFiles = [];
    const invalidMessages = [];

    files.forEach((file) => {
      const extension = (file.name.split('.').pop() || '').toLowerCase();

      if (!ALLOWED_FILE_EXTENSIONS.includes(extension)) {
        invalidMessages.push(`الملف ${file.name}: امتداد غير مدعوم`);
        return;
      }

      if (file.size > MAX_UPLOAD_SIZE_BYTES) {
        invalidMessages.push(`الملف ${file.name}: الحجم أكبر من ${MAX_UPLOAD_SIZE_MB}MB`);
        return;
      }

      validFiles.push(file);
    });

    if (invalidMessages.length > 0) {
      setError(`بعض الملفات مرفوضة:\n${invalidMessages.join('\n')}`);
    }

    if (validFiles.length === 0) {
      event.target.value = '';
      return;
    }

    const newFiles = validFiles.map((file) => ({
      file,
      type: 'MEDICAL_REPORT'
    }));
    setPendingFiles((prev) => [...prev, ...newFiles]);
    event.target.value = '';
  };

  const handleRemoveFile = (index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleFileTypeChange = (index, type) => {
    setPendingFiles((prev) => prev.map((f, i) => (i === index ? { ...f, type } : f)));
  };

  const fetchClaimAttachments = useCallback(async (claimId) => {
    if (!claimId) return;

    try {
      const response = await axiosClient.get(`/claims/${claimId}/attachments`);
      const payload = response.data?.data ?? response.data ?? [];
      const attachments = Array.isArray(payload) ? payload : [];

      setExistingAttachments(
        attachments.map((att) => ({
          id: att.id,
          fileName: att.fileName,
          fileType: att.fileType,
          attachmentType: att.attachmentType || att.description || 'OTHER',
          fileUrl: att.fileUrl,
          uploadedAt: att.createdAt || att.uploadedAt
        }))
      );
    } catch (err) {
      console.error('Failed to fetch claim attachments:', err);
      setExistingAttachments([]);
    }
  }, []);

  const handleDeleteExistingAttachment = async (attachmentId) => {
    if (!activeClaimId || !attachmentId) return;

    try {
      await axiosClient.delete(`/claims/${activeClaimId}/attachments/${attachmentId}`);
      setExistingAttachments((prev) => prev.filter((att) => att.id !== attachmentId));
    } catch (err) {
      console.error('Failed to delete attachment:', err);
      setError('تعذر حذف المرفق الحالي');
    }
  };

  const uploadAttachments = async (claimId) => {
    if (pendingFiles.length === 0) return;

    setUploading(true);
    let uploaded = 0;

    for (const { file, type } of pendingFiles) {
      try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('attachmentType', type);

        await axiosClient.post(`/claims/${claimId}/attachments`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        uploaded++;
        setUploadProgress(Math.round((uploaded / pendingFiles.length) * 100));
      } catch (err) {
        console.error('Failed to upload file:', file.name, err);
      }
    }

    setUploading(false);
    setUploadProgress(0);
    setPendingFiles([]);
    await fetchClaimAttachments(claimId);
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // FORM HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════
  const handleFormChange = (field) => (event) => {
    setFormData((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const validateDraftForm = () => {
    if (!linkedVisitId) {
      setError('لا يوجد رقم زيارة مرتبط');
      return false;
    }

    if (claimLines.length === 0) {
      setError('يجب إضافة خدمة واحدة على الأقل');
      return false;
    }

    const linesWithoutCategory = claimLines.filter((line) => !line.medicalCategoryId);
    if (linesWithoutCategory.length > 0) {
      setError('🚫 يجب اختيار التصنيف الطبي لجميع الخدمات');
      return false;
    }

    const invalidLines = claimLines.filter((line) => !line.medicalServiceId || !line.hasContract);
    if (invalidLines.length > 0) {
      setError('بعض الخدمات غير صالحة أو غير موجودة في العقد');
      return false;
    }

    if (!formData.diagnosisCode || !formData.diagnosisCode.trim()) {
      setError(LABELS.diagnosisCodeRequired);
      return false;
    }

    return true;
  };

  const validateFinalForm = () => {
    if (!validateDraftForm()) {
      return false;
    }

    if (claimLines.some((l) => l.requiresPA) && !formData.preAuthorizationId) {
      setError('يجب إدخال رقم الموافقة المسبقة لأن المطالبة تحتوي على خدمات تتطلب موافقة');
      return false;
    }

    return true;
  };

  const handleSubmit = async (finalSubmit) => {
    setError(null);
    setAttemptedSubmit(true);
    const isValid = finalSubmit ? validateFinalForm() : validateDraftForm();
    if (!isValid) return;

    setSubmitting(true);
    setSubmitMode(finalSubmit ? 'final' : 'draft');
    try {
      const payload = {
        visitId: parseInt(linkedVisitId),
        memberId: parseInt(linkedMemberId),
        providerId: userProviderId,
        preAuthorizationId: formData.preAuthorizationId || null,
        diagnosisCode: formData.diagnosisCode || null,
        diagnosisDescription: formData.diagnosisDescription || null,
        doctorName: formData.doctorName || null,
        serviceDate: linkedVisitDate || visitDetails?.visitDate || null,
        notes: formData.notes || null,
        lines: claimLines.map((line) => ({
          medicalServiceId: line.medicalServiceId,
          serviceCategoryId: line.medicalCategoryId,
          serviceCategoryName: line.medicalCategoryName,
          quantity: line.quantity || 1
        }))
      };

      const response = activeClaimId
        ? await axiosClient.put(`/claims/${activeClaimId}/data`, payload)
        : await axiosClient.post('/claims', payload);
      const result = response.data?.data || response.data;
      const claimId = result.id || activeClaimId;
      setActiveClaimId(claimId);

      if (pendingFiles.length > 0 && claimId) {
        await uploadAttachments(claimId);
      }

      if (finalSubmit) {
        await axiosClient.post(`/claims/${claimId}/submit`);
      }

      localStorage.removeItem(localDraftStorageKey);
      setAutosaveStatus('idle');
      setAttemptedSubmit(false);

      setSuccess({
        message: finalSubmit ? 'تم تقديم المطالبة للمراجعة بنجاح' : 'تم حفظ المطالبة كمسودة بنجاح',
        claimId: claimId,
        referenceNumber: result.claimNumber || result.referenceNumber,
        attachmentsCount: pendingFiles.length + existingAttachments.length
      });
    } catch (err) {
      console.error('Submit error:', err);
      const errorData = err.response?.data;
      let errorMsg = 'فشل في تقديم المطالبة';

      if (errorData) {
        if (errorData.message) errorMsg = errorData.message;
        else if (errorData.error) errorMsg = errorData.error;
        else if (typeof errorData === 'string') errorMsg = errorData;
      }

      setError(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleBack = useCallback(() => {
    navigate('/provider/visits');
  }, [navigate]);

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER - BLOCKED ACCESS
  // ═══════════════════════════════════════════════════════════════════════════
  if (accessBlocked) {
    return <BlockedAccessPage onBack={handleBack} />;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER - MAIN PAGE (Desktop-First Layout)
  // ═══════════════════════════════════════════════════════════════════════════
  return (
    <Box
      sx={{
        maxWidth: '100.0rem',
        mx: 'auto',
        px: { xs: 1, md: 2 },
        py: 1,
        bgcolor: '#F9FAFB',
        fontFamily: 'Tajawal, Readex Pro, IBM Plex Sans Arabic, sans-serif'
      }}
    >
      {/* ═══════════════════════ PAGE HEADER ═══════════════════════ */}
      <ModernPageHeader
        title={LABELS.pageTitle}
        subtitle={LABELS.pageSubtitle}
        icon={ReceiptIcon}
        breadcrumbs={[{ label: 'بوابة مقدم الخدمة' }, { label: 'سجل الزيارات', href: '/provider/visits' }, { label: LABELS.pageTitle }]}
      />

      <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: 'common.white', mb: '1.0rem' }}>
        <Stepper activeStep={workflowActiveStep} alternativeLabel>
          {workflowSteps.map((step, index) => {
            const completed = (index === 0 && hasVisitAndDiagnosis) || (index === 1 && hasServicesReady) || (index === 2 && hasAttachmentsReady);
            return (
              <Step key={step} completed={completed}>
                <StepLabel>{step}</StepLabel>
              </Step>
            );
          })}
        </Stepper>
      </Paper>

      {/* ═══════════════════════ LOADING BAR ═══════════════════════ */}
      {loading && <LinearProgress sx={{ mb: '1.0rem', borderRadius: 1 }} />}

      {/* ═══════════════════════ ERROR ALERT ═══════════════════════ */}
      {error && (
        <Alert severity="error" sx={{ mb: '1.5rem', borderRadius: '0.25rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Success Dialog */}
      <SuccessDialog
        open={!!success}
        type="claim"
        title={submitMode === 'draft' ? 'تم حفظ المطالبة كمسودة بنجاح' : 'تم تقديم المطالبة بنجاح! 🎉'}
        subtitle={submitMode === 'draft' ? 'يمكنك إكمال التعديلات ثم التقديم النهائي لاحقاً' : 'تم إرسال المطالبة للمراجعة من قبل فريق التأمين'}
        referenceNumber={success?.referenceNumber || success?.claimId}
        attachmentsCount={success?.attachmentsCount || 0}
        redirectPath="/provider/visits"
        redirectLabel="العودة لسجل الزيارات"
        countdownSeconds={5}
        viewDetailsPath={success?.claimId ? `/provider/claims/submit?claimId=${success.claimId}` : null}
        additionalInfo={[
          { label: 'المؤمَّن عليه', value: linkedMemberName || '—' },
          { label: 'عدد الخدمات', value: `${claimLines.length} خدمة` }
        ]}
      />

      <Grid container spacing={3} alignItems="flex-start">
        <Grid size={{ xs: 12, lg: 8.5 }}>
          <Stack spacing={3}>
            {/* ═══════════════════════ ROW 1: COMPACT READ-ONLY HEADER ═══════════════════════ */}
            <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: 'common.white' }}>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ xs: 'flex-start', md: 'center' }} flexWrap="wrap">
                <Chip label={`المؤمن عليه: ${linkedMemberName || '—'}`} color="primary" variant="outlined" />
                <Chip label={`الرقم المدني: ${linkedMemberCivilId || '—'}`} variant="outlined" />
                <Chip label={`رقم البطاقة: ${linkedMemberCardNumber || '—'}`} variant="outlined" />
                <Chip label={`رقم الزيارة: #${linkedVisitId || '—'}`} color="info" variant="outlined" />
                <Chip label={`تاريخ الزيارة: ${linkedVisitDate || visitDetails?.visitDate || '—'}`} variant="outlined" />
                <Chip label={`نوع الزيارة: ${VISIT_TYPE_LABELS[linkedVisitType] || linkedVisitType || 'غير محدد'}`} color="info" />
                <Chip label={`مقدم الخدمة: ${linkedProviderName || userProviderName || visitDetails?.providerName || '—'}`} variant="outlined" />
              </Stack>
            </Paper>

            {/* ═══════════════════════ ROW 2: SERVICE LINES ═══════════════════════ */}
            <FormSection highlighted>
              <SectionHeader
                icon={MedicalServicesIcon}
                title={LABELS.serviceLines}
                subtitle="اختر التصنيف أولاً ثم الخدمة الطبية لكل سطر"
                color="primary"
                action={
                  <Button
                    variant="contained"
                    size="small"
                    startIcon={<AddIcon />}
                    onClick={addClaimLine}
                    disabled={submitting || success}
                    sx={{ borderRadius: '0.25rem' }}
                  >
                    {LABELS.addService}
                  </Button>
                }
              />

              {/* 💡 Diagnosis Context Banner (New) */}
              {(formData.diagnosisCode || formData.preAuthorizationId) && (
                <Alert
                  severity="info"
                  icon={<DiagnosisIcon />}
                  sx={{
                    mb: '1.25rem',
                    borderRadius: '0.25rem',
                    border: '1px solid',
                    borderColor: 'info.light',
                    bgcolor: (theme) => alpha(theme.palette.info.main, 0.02),
                    '& .MuiAlert-message': { width: '100%' }
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                    <Typography variant="subtitle2" fontWeight={700} color="info.main">
                      الارتباط الطبي الحالي:
                    </Typography>
                    {formData.diagnosisCode && (
                      <Chip
                        label={`التشخيص: ${formData.diagnosisCode}`}
                        size="small"
                        color="primary"
                        variant="outlined"
                        sx={{ fontWeight: 600 }}
                      />
                    )}
                    {formData.preAuthorizationId && (
                      <Chip
                        icon={<ApprovalIcon style={{ fontSize: '1rem' }} />}
                        label={`مرتبط بموافقة: #${formData.preAuthorizationId}`}
                        size="small"
                        color="success"
                        sx={{ fontWeight: 600 }}
                      />
                    )}
                    {formData.diagnosisDescription && (
                      <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', ml: 1 }}>
                        "{formData.diagnosisDescription}"
                      </Typography>
                    )}
                  </Stack>
                </Alert>
              )}

              <Divider sx={{ mb: '1.5rem' }} />

              {/* Category Violation Warning */}
              {hasCategoryViolation && claimLines.length > 0 && (
                <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
                  ⚠️ يجب اختيار التصنيف الطبي لكل خدمة قبل اختيار الخدمة نفسها
                </Alert>
              )}

              {claimLines.length === 0 ? (
                <Box sx={{ minHeight: '0.375rem' }} />
              ) : (
                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: '0.25rem' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: tableHeaderBg }}>
                        <TableCell width="25%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          <Stack direction="row" alignItems="center" spacing={0.5}>
                            <Chip
                              label="1"
                              size="small"
                              sx={{
                                bgcolor: 'white',
                                color: MEDICAL_COLORS.primary.main,
                                width: '1.375rem',
                                height: '1.375rem',
                                fontSize: '0.7rem',
                                fontWeight: 700
                              }}
                            />
                            <span>{LABELS.selectCategory}</span>
                          </Stack>
                        </TableCell>
                        <TableCell width="25%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          <Stack direction="row" alignItems="center" spacing={0.5}>
                            <Chip
                              label="2"
                              size="small"
                              sx={{
                                bgcolor: 'rgba(255,255,255,0.3)',
                                color: 'white',
                                width: '1.375rem',
                                height: '1.375rem',
                                fontSize: '0.7rem',
                                fontWeight: 700
                              }}
                            />
                            <span>{LABELS.selectService}</span>
                          </Stack>
                        </TableCell>
                        <TableCell width="10%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          {LABELS.quantity}
                        </TableCell>
                        <TableCell width="15%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5}>
                            <LockIcon fontSize="small" />
                            <span>{LABELS.unitPrice}</span>
                          </Stack>
                        </TableCell>
                        <TableCell width="12%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          الارتباط الطبي
                        </TableCell>
                        <TableCell width="15%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                          {LABELS.totalPrice}
                        </TableCell>
                        <TableCell width="10%" align="center" sx={{ color: tableHeaderColor }}></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {claimLines.map((line) => (
                        <TableRow key={line.id} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
                          {/* Category Selector (Step 1) */}
                          <TableCell>
                            <Autocomplete
                              size="small"
                              options={medicalCategories}
                              getOptionLabel={(option) => option?.name || ''}
                              value={medicalCategories.find((c) => normalizeId(c.id) === normalizeId(line.medicalCategoryId)) || null}
                              loading={loadingCategories}
                              onChange={(_, value) => handleLineCategoryChange(line.id, value)}
                              disabled={submitting || success}
                              isOptionEqualToValue={(option, value) => normalizeId(option?.id) === normalizeId(value?.id)}
                              renderInput={(params) => (
                                <TextField
                                  {...params}
                                  placeholder="اختر التصنيف أولاً..."
                                  error={attemptedSubmit && !line.medicalCategoryId && claimLines.length > 0}
                                  sx={{
                                    '& .MuiOutlinedInput-root': {
                                      bgcolor: line.medicalCategoryId
                                        ? (theme) => alpha(theme.palette.success.main, 0.1)
                                        : (theme) => alpha(theme.palette.warning.main, 0.1)
                                    }
                                  }}
                                />
                              )}
                              renderOption={(props, option) => {
                                const { key, ...otherProps } = props;
                                const serviceCount = availableServices.filter((s) => doesServiceMatchCategory(s, option)).length;
                                return (
                                  <li key={key} {...otherProps}>
                                    <Stack direction="row" alignItems="center" spacing={1} sx={{ width: '100%' }}>
                                      <CategoryIcon fontSize="small" color="primary" />
                                      <Typography variant="body2" fontWeight="medium">
                                        {option.name}
                                      </Typography>
                                      <Chip label={`${serviceCount} خدمة`} size="small" color="primary" variant="outlined" />
                                    </Stack>
                                  </li>
                                );
                              }}
                            />
                          </TableCell>

                          {/* Service Selector (Step 2) */}
                          <TableCell>
                            <Autocomplete
                              size="small"
                              options={line.filteredServices || []}
                              getOptionDisabled={(option) => !!(option.requiresPreApproval || option.requiresPreAuth || option.requiresPA)}
                              getOptionLabel={(option) => {
                                const code = option.code ? `[${option.code}] ` : '';
                                return `${code}${option.name || ''}`;
                              }}
                              filterOptions={(options, { inputValue }) => {
                                const search = inputValue.toLowerCase();
                                return options.filter(
                                  (opt) =>
                                    (opt.code && opt.code.toLowerCase().includes(search)) ||
                                    (opt.name && opt.name.toLowerCase().includes(search))
                                );
                              }}
                              value={
                                line.medicalServiceId
                                  ?
                                  (line.filteredServices || []).find((s) => normalizeId(s.id) === normalizeId(line.medicalServiceId)) ||
                                  availableServices.find((s) => normalizeId(s.id) === normalizeId(line.medicalServiceId)) ||
                                  null
                                  : null
                              }
                              loading={loadingServices}
                              onChange={(_, value) => handleServiceSelect(line.id, value)}
                              disabled={submitting || success || !line.medicalCategoryId}
                              renderInput={(params) => (
                                <TextField
                                  {...params}
                                  placeholder={line.medicalCategoryId ? 'ابحث برمز الخدمة أو اسمها...' : '⚠️ اختر التصنيف أولاً'}
                                  error={attemptedSubmit && line.medicalCategoryId && !line.medicalServiceId}
                                  sx={{
                                    '& .MuiOutlinedInput-root': {
                                      bgcolor: !line.medicalCategoryId ? 'grey.100' : undefined
                                    }
                                  }}
                                />
                              )}
                              renderOption={(props, option) => {
                                const { key, ...otherProps } = props;
                                const requiresPA = option.requiresPreApproval || option.requiresPreAuth || option.requiresPA || false;

                                return (
                                  <li key={key} {...otherProps}>
                                    <Stack spacing={0.5} sx={{ width: '100%' }}>
                                      <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                        <Chip
                                          label={option.code}
                                          size="small"
                                          color="primary"
                                          variant="outlined"
                                          sx={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '0.75rem' }}
                                        />
                                        <Typography variant="body2" fontWeight="medium">
                                          {option.name}
                                        </Typography>
                                        {requiresPA && (
                                          <Tooltip title="يتم التحكم في هذه الخدمة عبر قواعد الوثيقة وتتطلب رقم موافقة مسبقة صالح">
                                            <Chip
                                              icon={<LockIcon sx={{ fontSize: '0.75rem !important' }} />}
                                              label="مقفلة - تتطلب موافقة مسبقة"
                                              size="small"
                                              color="error"
                                              variant="outlined"
                                              sx={{ fontSize: '0.75rem', height: '1.25rem' }}
                                            />
                                          </Tooltip>
                                        )}
                                      </Stack>
                                      {option.price && (
                                        <Typography variant="caption" color="success.main">
                                          💰 سعر العقد: {formatCurrency(option.price)}
                                        </Typography>
                                      )}
                                    </Stack>
                                  </li>
                                );
                              }}
                            />
                          </TableCell>

                          {/* 💡 Diagnosis Association column (New) */}
                          <TableCell align="center">
                            {line.requiresPA ? (
                              <Tooltip title={formData.preAuthorizationId ? `مرتبطة بالموافقة رقم #${formData.preAuthorizationId}` : 'هذه الخدمة تتطلب اختيار موافقة مسبقة'}>
                                <Chip
                                  icon={<ApprovalIcon style={{ fontSize: '0.9rem' }} />}
                                  label="عبر موافقة"
                                  size="small"
                                  color="success"
                                  variant={formData.preAuthorizationId ? 'filled' : 'outlined'}
                                  sx={{ fontWeight: 600 }}
                                />
                              </Tooltip>
                            ) : (
                              <Tooltip title={formData.diagnosisCode ? `مرتبطة بالتشخيص: ${formData.diagnosisCode}` : 'سيتم ربطها بالتشخيص المكتوب أعلاه'}>
                                <Chip
                                  icon={<HealingIcon style={{ fontSize: '0.9rem' }} />}
                                  label="تشخيص مباشر"
                                  size="small"
                                  color="info"
                                  variant="outlined"
                                  sx={{ fontWeight: 500, opacity: formData.diagnosisCode ? 1 : 0.6 }}
                                />
                              </Tooltip>
                            )}
                          </TableCell>

                          {/* Quantity */}
                          <TableCell align="center">
                            <TextField
                              type="number"
                              size="small"
                              value={line.quantity}
                              onChange={(e) => updateClaimLine(line.id, 'quantity', Math.max(1, parseInt(e.target.value) || 1))}
                              disabled={submitting || success}
                              inputProps={{ min: 1, style: { textAlign: 'center' } }}
                              sx={{ width: '4.375rem' }}
                            />
                          </TableCell>

                          {/* Unit Price */}
                          <TableCell align="center">
                            <ContractPriceChip
                              loading={line.loadingPrice}
                              price={line.unitPrice}
                              hasContract={line.hasContract}
                              error={line.priceError}
                            />
                          </TableCell>

                          {/* Line Total */}
                          <TableCell align="center">
                            <Typography fontWeight="bold" color="primary.main">
                              {formatCurrency(calculateLineTotal(line))}
                            </Typography>
                          </TableCell>

                          {/* Delete */}
                          <TableCell align="center">
                            <Stack direction="row" spacing={0.5} justifyContent="center">
                              <IconButton size="small" color="primary" onClick={addClaimLine} disabled={submitting || success}>
                                <AddIcon fontSize="small" />
                              </IconButton>
                              <IconButton size="small" color="error" onClick={() => removeClaimLine(line.id)} disabled={submitting || success}>
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}

                      {/* Total Row */}
                      <TableRow sx={{ bgcolor: isDark ? alpha(MEDICAL_COLORS.primary.main, 0.15) : alpha(MEDICAL_COLORS.primary.main, 0.1) }}>
                        <TableCell colSpan={4} align="left">
                          <Typography variant="h6" fontWeight={700}>
                            {LABELS.totalClaimAmount}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">
                          <Typography variant="h5" color="primary.main" fontWeight={700}>
                            {formatCurrency(totalClaimAmount)}
                          </Typography>
                        </TableCell>
                        <TableCell />
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              )}

            </FormSection>

            {/* ═══════════════════════ ROW 3: CLINICAL DATA (DIAGNOSIS + PRE-AUTH) ═══════════════════════ */}
            <FormSection>
              <SectionHeader icon={DiagnosisIcon} title="البيانات السريرية" subtitle="التشخيص وربط الموافقة المسبقة" color="info" />
              <Divider sx={{ mb: '1.5rem' }} />

              <Grid container spacing={3}>
                <Grid size={{ xs: 12, lg: 7 }}>
                  <Typography variant="subtitle2" fontWeight={700} sx={{ mb: '0.75rem' }}>
                    {LABELS.diagnosis}
                  </Typography>
                  <Stack spacing={2.5}>
                    <TextField
                      fullWidth
                      label={LABELS.diagnosisCode}
                      value={formData.diagnosisCode}
                      onChange={handleFormChange('diagnosisCode')}
                      disabled={submitting || success}
                      required
                      error={attemptedSubmit && !formData.diagnosisCode?.trim()}
                      helperText={attemptedSubmit && !formData.diagnosisCode?.trim() ? LABELS.diagnosisCodeRequired : 'أدخل رمز التشخيص حسب تصنيف ICD-10'}
                      placeholder="مثال: J06.9"
                      InputProps={{
                        sx: { fontFamily: 'monospace', fontWeight: 600 }
                      }}
                    />
                    <TextField
                      fullWidth
                      label={LABELS.diagnosisDescription}
                      value={formData.diagnosisDescription}
                      onChange={handleFormChange('diagnosisDescription')}
                      disabled={submitting || success}
                      placeholder="وصف التشخيص الطبي..."
                      multiline
                      rows={2}
                    />
                    <TextField
                      fullWidth
                      multiline
                      rows={2}
                      label={LABELS.notes}
                      value={formData.notes}
                      onChange={handleFormChange('notes')}
                      disabled={submitting || success}
                      placeholder="أدخل أي ملاحظات طبية إضافية..."
                      InputProps={{
                        startAdornment: <NotesIcon color="action" sx={{ mr: 1, mt: 1, alignSelf: 'flex-start' }} />
                      }}
                    />
                  </Stack>
                </Grid>

                <Grid size={{ xs: 12, lg: 5 }}>
                  <Typography variant="subtitle2" fontWeight={700} sx={{ mb: '0.75rem' }}>
                    {LABELS.preAuth}
                  </Typography>
                  <Alert severity="info" sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
                    {LABELS.preAuthOptional}
                  </Alert>

                  <Autocomplete
                    fullWidth
                    options={availablePreAuths}
                    loading={loadingPreAuths}
                    value={availablePreAuths.find((pa) => pa.id === formData.preAuthorizationId) || null}
                    onChange={(event, newValue) => {
                      setFormData((prev) => ({
                        ...prev,
                        preAuthorizationId: newValue ? newValue.id : ''
                      }));
                    }}
                    getOptionLabel={(option) => `${option.referenceNumber} - ${option.serviceName}`}
                    renderOption={(props, option) => (
                      <Box component="li" {...props}>
                        <Stack sx={{ width: '100%' }}>
                          <Typography variant="body2" fontWeight={500}>
                            {option.referenceNumber}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {option.serviceName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            الحالة: {option.status === 'APPROVED' ? 'موافق عليه' : 'تم الاطلاع'}
                          </Typography>
                        </Stack>
                      </Box>
                    )}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label={LABELS.selectPreAuth}
                        placeholder={loadingPreAuths ? 'جاري التحميل...' : LABELS.noPreAuth}
                        InputProps={{
                          ...params.InputProps,
                          endAdornment: (
                            <>
                              {loadingPreAuths ? <CircularProgress color="inherit" size={20} /> : null}
                              {params.InputProps.endAdornment}
                            </>
                          )
                        }}
                      />
                    )}
                    disabled={submitting || success}
                    noOptionsText={LABELS.noPreAuth}
                  />

                  {formData.preAuthorizationId && (
                    <Alert severity="success" sx={{ mt: '1.0rem', borderRadius: '0.25rem' }}>
                      تم اختيار موافقة مسبقة - سيتم ربطها بالمطالبة وتحديث حالتها إلى "مستخدم" تلقائياً
                    </Alert>
                  )}
                </Grid>
              </Grid>
            </FormSection>

            {/* ═══════════════════════ ROW 4: ATTACHMENTS + CLAIM CHAT ═══════════════════════ */}
            <FormSection>
              <SectionHeader icon={AttachmentIcon} title={LABELS.attachments} subtitle="المستندات الداعمة للمطالبة" color="warning" />
              <Divider sx={{ mb: '1.5rem' }} />

              <Alert severity="info" sx={{ mb: '1.0rem', borderRadius: '0.25rem' }}>
                {LABELS.attachmentHint}
                <br />
                <strong>الامتدادات المسموحة:</strong> PDF, JPG, JPEG, PNG, GIF, DOC, DOCX — <strong>الحد الأقصى:</strong> {MAX_UPLOAD_SIZE_MB}MB لكل ملف.
              </Alert>

              <Button
                variant="outlined"
                component="label"
                fullWidth
                startIcon={<UploadIcon />}
                disabled={submitting || success}
                sx={{
                  height: '5.0rem',
                  borderStyle: 'dashed',
                  borderWidth: 2,
                  borderColor: attemptedSubmit && !hasAttachmentsReady ? 'error.main' : undefined,
                  borderRadius: '0.25rem',
                  mb: '1.0rem'
                }}
              >
                {LABELS.selectFiles}
                <input type="file" hidden multiple accept={FILE_ACCEPT_ATTR} onChange={handleFileSelect} />
              </Button>

              {existingAttachments.length > 0 && (
                <Stack spacing={1} sx={{ mb: '1.0rem' }}>
                  <Typography variant="subtitle2" fontWeight={600} color="text.secondary">
                    المرفقات المحفوظة في المسودة ({existingAttachments.length})
                  </Typography>
                  {existingAttachments.map((item) => (
                    <Paper key={item.id} variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem', bgcolor: (theme) => alpha(theme.palette.success.main, 0.04) }}>
                      <Stack direction="row" alignItems="center" spacing={2}>
                        <AttachmentIcon color="success" />
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography variant="body2" noWrap fontWeight={500}>
                            {item.fileName || 'مرفق'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {item.attachmentType || item.fileType || 'OTHER'}
                          </Typography>
                        </Box>
                        <IconButton size="small" color="error" onClick={() => handleDeleteExistingAttachment(item.id)} disabled={submitting || success}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              )}

              {pendingFiles.length > 0 && (
                <Stack spacing={1} sx={{ mb: '1.0rem' }}>
                  {pendingFiles.map((item, index) => (
                    <Paper key={index} variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem' }}>
                      <Stack direction="row" alignItems="center" spacing={2}>
                        <AttachmentIcon color="action" />
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography variant="body2" noWrap fontWeight={500}>
                            {item.file.name}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {(item.file.size / 1024).toFixed(1)} KB
                          </Typography>
                        </Box>
                        <TextField
                          select
                          size="small"
                          value={item.type}
                          onChange={(e) => handleFileTypeChange(index, e.target.value)}
                          SelectProps={{ native: true }}
                          sx={{ width: '8.125rem' }}
                        >
                          <option value="MEDICAL_REPORT">تقرير طبي</option>
                          <option value="INVOICE">فاتورة</option>
                          <option value="LAB_RESULT">نتائج مختبر</option>
                          <option value="XRAY">أشعة</option>
                          <option value="PRESCRIPTION">وصفة طبية</option>
                          <option value="OTHER">أخرى</option>
                        </TextField>
                        <IconButton size="small" color="error" onClick={() => handleRemoveFile(index)} disabled={submitting || success}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                    </Paper>
                  ))}
                  <Typography variant="caption" color="text.secondary" textAlign="center">
                    📎 سيتم رفع {pendingFiles.length} ملف عند تقديم المطالبة
                  </Typography>
                </Stack>
              )}

              {uploading && (
                <Box sx={{ mt: '1.0rem' }}>
                  <LinearProgress variant="determinate" value={uploadProgress} sx={{ borderRadius: 1 }} />
                  <Typography variant="caption" color="text.secondary" textAlign="center" display="block" mt={0.5}>
                    {LABELS.uploadingFiles} {uploadProgress}%
                  </Typography>
                </Box>
              )}

              <Divider sx={{ my: '1.5rem' }} />

              <SectionHeader icon={ChatIcon} title="محادثة المطالبة" subtitle="تواصل داخلي حول المطالبة" color="info" />
              <Box
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: '0.25rem',
                  p: '0.75rem',
                  bgcolor: 'background.default',
                  maxHeight: '13.75rem',
                  overflowY: 'auto',
                  mb: '1.0rem'
                }}
              >
                {providerChatMessages.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    لا توجد رسائل بعد
                  </Typography>
                ) : (
                  <Stack spacing={1}>
                    {providerChatMessages.map((message) => (
                      <Paper key={message.id} variant="outlined" sx={{ p: '0.625rem', borderRadius: '0.25rem' }}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
                          <Typography variant="caption" fontWeight={600}>
                            {message.senderName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {new Date(message.createdAt).toLocaleString('ar-LY')}
                          </Typography>
                        </Stack>
                        <Typography variant="body2">{message.text}</Typography>
                      </Paper>
                    ))}
                  </Stack>
                )}
              </Box>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                <TextField
                  fullWidth
                  size="small"
                  value={providerChatInput}
                  onChange={(event) => setProviderChatInput(event.target.value)}
                  placeholder="اكتب رسالة داخل المطالبة..."
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' && !event.shiftKey) {
                      event.preventDefault();
                      handleSendProviderChatMessage();
                    }
                  }}
                />
                <Button variant="contained" onClick={handleSendProviderChatMessage} disabled={!providerChatInput.trim()}>
                  إرسال
                </Button>
              </Stack>
            </FormSection>

          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 3.5 }}>
          <Stack spacing={1.5} sx={{ position: { lg: 'sticky' }, top: { lg: 85 }, zIndex: 5 }}>
            <InfoCard bgcolor={(theme) => alpha(theme.palette.success.main, 0.05)} sx={{ p: '0.75rem' }}>
              <SectionHeader icon={LimitIcon} title={LABELS.coverageInfo} subtitle="تغطية المؤمن عليه" color="success" />
              <Divider sx={{ mb: '0.75rem', mt: 1 }} />
              {memberLimit ? (
                <Stack spacing={1}>
                  <Box sx={{ p: 1, borderRadius: '0.375rem', bgcolor: (theme) => alpha(theme.palette.warning.main, 0.08) }}>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {LABELS.annualLimit}
                    </Typography>
                    <Typography variant="body2" fontWeight={700}>
                      {formatCurrency(memberLimit.annualLimit || 0)}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1, borderRadius: '0.375rem', bgcolor: (theme) => alpha(theme.palette.error.main, 0.08) }}>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {LABELS.usedAmount}
                    </Typography>
                    <Typography variant="body2" fontWeight={700} color="error.main">
                      {formatCurrency(memberLimit.usedAmount || 0)}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1, borderRadius: '0.375rem', bgcolor: (theme) => alpha(theme.palette.success.main, 0.1) }}>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {LABELS.remainingLimit}
                    </Typography>
                    <Typography variant="body2" fontWeight={800} color="success.main">
                      {formatCurrency(memberLimit.remainingLimit || 0)}
                    </Typography>
                  </Box>
                  <Box sx={{ p: 1, borderRadius: '0.375rem', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1) }}>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {LABELS.totalClaimAmount}
                    </Typography>
                    <Typography variant="subtitle1" fontWeight={900} color="primary.main">
                      {formatCurrency(totalClaimAmount)}
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={memberLimit.usagePercentage || 0}
                    color={memberLimit.usagePercentage >= 80 ? 'error' : 'success'}
                    sx={{ mt: 0.5, height: '0.375rem', borderRadius: 1 }}
                  />
                </Stack>
              ) : (
                <Alert severity="info" sx={{ borderRadius: '0.25rem' }}>
                  لا تتوفر بيانات التغطية حالياً
                </Alert>
              )}
            </InfoCard>

            <Paper variant="outlined" sx={{ p: '0.75rem', borderRadius: '0.25rem', bgcolor: 'common.white' }}>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 1 }}>
                حالة الجاهزية
              </Typography>
              <Stack spacing={0.75}>
                <Chip size="small" label={hasVisitAndDiagnosis ? '✓ بيانات الزيارة مكتملة' : '• أكمل التشخيص'} color={hasVisitAndDiagnosis ? 'success' : 'default'} />
                <Chip size="small" label={hasServicesReady ? '✓ الخدمات مكتملة' : '• أضف الخدمات المطلوبة'} color={hasServicesReady ? 'success' : 'default'} />
                <Chip size="small" label="✓ المرفقات اختيارية" color="success" />
              </Stack>
            </Paper>
          </Stack>
        </Grid>
      </Grid>

      {/* ═══════════════════════ ROW 6: ACTION BUTTONS (Sticky Footer) ═══════════════════════ */}
      <Paper
        elevation={3}
        sx={{
          p: '1.25rem',
          borderRadius: '0.25rem',
          bgcolor: 'background.paper',
          position: 'sticky',
          bottom: '6.0rem',
          zIndex: 10,
          border: '1px solid',
          borderColor: 'divider'
        }}
      >
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems="center">
          {/* Left: Form Status */}
          <Stack direction="row" spacing={2} alignItems="center">
            {claimLines.length === 0 && (
              <Alert severity="warning" sx={{ py: 0.5 }}>
                <Typography variant="body2">⚠️ أضف خدمة واحدة على الأقل</Typography>
              </Alert>
            )}
            {claimLines.length > 0 && !isFormValid && (
              <Alert severity="warning" sx={{ py: 0.5 }}>
                <Typography variant="body2">⚠️ أكمل بيانات الخدمات</Typography>
              </Alert>
            )}
            {claimLines.length > 0 && isFormValid && pendingFiles.length + existingAttachments.length === 0 && (
              <Alert severity="warning" sx={{ py: 0.5 }}>
                <Typography variant="body2">⚠️ أرفق ملف واحد على الأقل</Typography>
              </Alert>
            )}
            {isFormValid && pendingFiles.length + existingAttachments.length > 0 && (
              <Alert severity="success" sx={{ py: 0.5 }}>
                <Typography variant="body2">
                  ✅ جاهز للتقديم • {claimLines.length} خدمة • {formatCurrency(totalClaimAmount)} • {pendingFiles.length + existingAttachments.length}{' '}
                  مرفق
                </Typography>
              </Alert>
            )}

            <Chip
              size="small"
              color={autosaveStatus === 'error' ? 'error' : autosaveStatus === 'saved' ? 'success' : 'default'}
              icon={
                autosaveStatus === 'saving' ? <SyncIcon fontSize="small" /> : autosaveStatus === 'saved' ? <CloudDoneIcon fontSize="small" /> : <CloudOffIcon fontSize="small" />
              }
              label={
                autosaveStatus === 'saving'
                  ? 'جاري الحفظ التلقائي...'
                  : autosaveStatus === 'saved'
                    ? `تم الحفظ تلقائياً ${autosaveAt ? new Date(autosaveAt).toLocaleTimeString('ar-LY', { hour: '2-digit', minute: '2-digit' }) : ''}`
                    : autosaveStatus === 'error'
                      ? 'تعذر الحفظ التلقائي'
                      : 'الحفظ التلقائي جاهز'
              }
              variant="outlined"
            />
          </Stack>

          {/* Right: Action Buttons */}
          <Stack direction={{ xs: 'column', sm: 'row-reverse' }} spacing={2}>
            {!success && (
              <>
                <Button
                  variant="contained"
                  color="primary"
                  size="large"
                  startIcon={submitting && submitMode === 'final' ? <CircularProgress size={20} color="inherit" /> : <SendIcon />}
                  onClick={() => handleSubmit(true)}
                  disabled={submitting || claimLines.length === 0}
                  sx={{
                    borderRadius: '0.25rem',
                    px: '2.5rem',
                    minWidth: '13.125rem',
                    boxShadow: 4,
                    fontWeight: 800,
                    '&:hover': { boxShadow: 6 }
                  }}
                >
                  {submitting && submitMode === 'final' ? LABELS.submittingFinal : LABELS.submitFinal}
                </Button>
                <Button
                  variant="outlined"
                  color="primary"
                  size="large"
                  startIcon={submitting && submitMode === 'draft' ? <CircularProgress size={20} color="inherit" /> : <NotesIcon />}
                  onClick={() => handleSubmit(false)}
                  disabled={submitting || claimLines.length === 0}
                  sx={{ borderRadius: '0.25rem', px: '1.5rem' }}
                >
                  {submitting && submitMode === 'draft' ? LABELS.savingDraft : LABELS.saveDraft}
                </Button>
              </>
            )}
            <Button
              variant="outlined"
              size="large"
              startIcon={<ArrowBackIcon />}
              onClick={handleBack}
              disabled={submitting}
              sx={{ borderRadius: '0.25rem', px: '1.5rem' }}
            >
              {LABELS.cancel}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
}




