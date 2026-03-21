/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║     PROVIDER PRE-APPROVAL SUBMISSION - Visit-Centric Canonical Architecture  ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  CREATED: 2026-01-29                                                         ║
 * ║  REDESIGNED: 2026-01-29 - Desktop-First Professional UX                      ║
 * ║  ARCHITECTURAL LAWS ENFORCED:                                                ║
 * ║  ❌ No pre-approval without Visit (visitId is MANDATORY)                     ║
 * ║  ❌ No free-text service (must select from dropdown)                         ║
 * ║  ❌ No manual price entry (price comes from Provider Contract)               ║
 * ║  ✅ Data Flow: Visit → Member → Contract → Service → Pre-Approval            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
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
  Paper,
  Autocomplete,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  alpha,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Tooltip
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { formatCurrency } from 'utils/currency-formatter';
import {
  Send as SendIcon,
  Person as PersonIcon,
  MedicalServices as MedicalServicesIcon,
  ArrowBack as ArrowBackIcon,
  CreditCard as CardIcon,
  LocalHospital as VisitIcon,
  Lock as LockIcon,
  CheckCircle as ApprovalIcon,
  Category as CategoryIcon,
  Healing as HealingIcon,
  Description as DiagnosisIcon,
  PriorityHigh as PriorityIcon,
  Notes as NotesIcon,
  Business as BusinessIcon,
  CalendarToday as CalendarIcon,
  Badge as BadgeIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  AccountBalance as LimitIcon,
  AttachFile as AttachFileIcon,
  UploadFile as UploadFileIcon
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
  pageTitle: 'إنشاء موافقة مسبقة',
  pageSubtitle: 'طلب موافقة مسبقة للخدمات الطبية',
  visitRequired: 'يجب الوصول لهذه الصفحة من سجل الزيارات',
  visitInfo: 'بيانات الزيارة',
  memberInfo: 'بيانات المؤمن عليه',
  serviceSelection: 'التصنيف والخدمة الطبية',
  selectCategory: 'التصنيف الطبي',
  selectService: 'الخدمة الطبية',
  noContract: 'لا يوجد عقد ساري لمقدم الخدمة',
  diagnosis: 'بيانات التشخيص',
  diagnosisCode: 'رمز التشخيص (ICD-10)',
  diagnosisDescription: 'وصف التشخيص',
  requestDetails: 'تفاصيل الطلب',
  notes: 'ملاحظات طبية',
  priority: 'أولوية الطلب',
  saveDraft: 'حفظ كمسودة',
  savingDraft: 'جاري حفظ المسودة...',
  submitFinal: 'تقديم نهائي للمراجعة',
  submittingFinal: 'جاري التقديم النهائي...',
  cancel: 'إلغاء',
  back: 'رجوع',
  contractPrice: 'سعر الخدمة حسب العقد',
  successTitleDraft: 'تم حفظ الطلب كمسودة بنجاح',
  successMessageDraft: 'يمكنك تعديل الطلب لاحقاً قبل التقديم النهائي للمراجعة',
  successTitleFinal: 'تم تقديم طلب الموافقة المسبقة بنجاح',
  successMessageFinal: 'تم إرسال الطلب للمراجعة من قبل فريق التأمين',
  coverageInfo: 'معلومات التغطية',
  requiresPA: 'تتطلب موافقة مسبقة'
};

const PRIORITY_OPTIONS = [
  { value: 'EMERGENCY', label: 'طوارئ', color: 'error', description: 'حالة طارئة تحتاج موافقة فورية' },
  { value: 'URGENT', label: 'عاجل', color: 'warning', description: 'يحتاج موافقة خلال 24 ساعة' },
  { value: 'NORMAL', label: 'عادي', color: 'info', description: 'المعالجة الاعتيادية' },
  { value: 'LOW', label: 'منخفض', color: 'default', description: 'غير مستعجل' }
];

const VISIT_TYPE_LABELS = {
  OUTPATIENT: 'عيادة خارجية',
  INPATIENT: 'تنويم',
  EMERGENCY: 'طوارئ',
  DAY_CARE: 'رعاية يومية'
};

const MAX_UPLOAD_SIZE_MB = 10;
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024;
const ALLOWED_FILE_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'gif', 'doc', 'docx'];
const FILE_ACCEPT_ATTR = '.pdf,.jpg,.jpeg,.png,.gif,.doc,.docx';

const createEmptyServiceRow = () => ({
  rowId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  category: null,
  service: null,
  quantity: 1
});

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
  if (!hasContract) return <Chip label="لا يوجد عقد" color="warning" size="small" />;
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
// MAIN COMPONENT
// ══════════════════════════════════════════════════════════════════════════════
const ProviderPreApprovalSubmission = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  // ═══════════════════════════════════════════════════════════════════════════
  // THEME (MEDICAL THEME)
  // ═══════════════════════════════════════════════════════════════════════════
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const tableHeaderBg = isDark ? '#1E3A5F' : MEDICAL_COLORS.primary.main;
  const tableHeaderColor = '#FFFFFF';

  // ── Visit Data from URL ──
  const visitData = useMemo(
    () => ({
      visitId: searchParams.get('visitId'),
      memberId: searchParams.get('memberId'),
      memberName: searchParams.get('memberName') || '',
      memberCivilId: searchParams.get('memberCivilId') || '',
      cardNumber: searchParams.get('cardNumber') || '',
      employer: searchParams.get('employer') || '',
      visitDate: searchParams.get('visitDate') || '',
      visitTime: searchParams.get('visitTime') || '',
      visitType: searchParams.get('visitType') || 'OUTPATIENT',
      providerId: searchParams.get('providerId') || '',
      providerName: searchParams.get('providerName') || '',
      fromVisitLog: searchParams.get('fromVisitLog') === 'true'
    }),
    [searchParams]
  );

  // ── State ──
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [successDialogOpen, setSuccessDialogOpen] = useState(false);
  const [createdPreApprovalId, setCreatedPreApprovalId] = useState(null);
  const [submitMode, setSubmitMode] = useState(null);

  // Contract & Services
  const [contract, setContract] = useState(null);
  const [categories, setCategories] = useState([]);
  const [services, setServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(false);

  // Form Data
  const [serviceRows, setServiceRows] = useState([createEmptyServiceRow()]);
  const [attachments, setAttachments] = useState([]);
  const [diagnosisCode, setDiagnosisCode] = useState('');
  const [diagnosisDescription, setDiagnosisDescription] = useState('');
  const [priority, setPriority] = useState('NORMAL');
  const [notes, setNotes] = useState('');

  const validateSelectedFiles = useCallback((selectedFiles) => {
    const validFiles = [];
    const invalidMessages = [];

    selectedFiles.forEach((file) => {
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

    return { validFiles, invalidMessages };
  }, []);

  const normalizeId = useCallback((value) => {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }, []);

  const normalizeText = useCallback((value) => (value || '').toString().trim().toLowerCase(), []);

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

  // ══════════════════════════════════════════════════════════════════════════════
  // LOAD PROVIDER CONTRACT & CATEGORIES
  // ══════════════════════════════════════════════════════════════════════════════
  useEffect(() => {
    const loadContractData = async () => {
      if (!visitData.fromVisitLog) {
        console.warn('[PRE-AUTH] Not from visit log, skipping contract load');
        return;
      }

      try {
        setLoading(true);
        setError(null);

        console.log('[PRE-AUTH] Loading contract and categories...');

        const contractRes = await axiosClient.get('/provider/my-contract');
        if (contractRes.data?.data) {
          setContract(contractRes.data.data);
          console.log('[PRE-AUTH] Contract loaded:', contractRes.data.data);
        }

        const categoriesRes = await axiosClient.get('/provider/medical-categories');
        if (categoriesRes.data?.data) {
          const normalizedCategories = categoriesRes.data.data.map((category) => ({
            ...category,
            id: normalizeId(category.id),
            code: category.code || category.categoryCode || '',
            name: category.name || category.nameAr || category.nameEn || category.code || '—'
          }));
          setCategories(normalizedCategories);
          console.log('[PRE-AUTH] Categories loaded:', normalizedCategories.length);
        }
      } catch (err) {
        console.error('[PRE-AUTH] Error loading contract data:', err);
        setError('فشل في تحميل بيانات العقد');
      } finally {
        setLoading(false);
      }
    };

    loadContractData();
  }, [visitData.fromVisitLog, normalizeId]);

  // ══════════════════════════════════════════════════════════════════════════════
  // LOAD CONTRACT SERVICES THAT REQUIRE PRE-APPROVAL ONLY
  // ══════════════════════════════════════════════════════════════════════════════
  useEffect(() => {
    const loadServices = async () => {
      if (!visitData.fromVisitLog) {
        console.warn('[PRE-AUTH] Not from visit log, skipping services load');
        return;
      }

      try {
        setLoadingServices(true);
        console.log('[PRE-AUTH] Loading services...');

        // Get contract services
        const res = await axiosClient.get('/provider/my-contract/services', {
          params: { page: 0, size: 500 }
        });

        let rawServices = [];
        if (res.data?.data?.content) {
          rawServices = res.data.data.content;
          console.log('[PRE-AUTH] Services loaded (from content):', rawServices.length);
        } else if (res.data?.data) {
          rawServices = res.data.data;
          console.log('[PRE-AUTH] Services loaded (from data):', rawServices.length);
        } else {
          console.warn('[PRE-AUTH] No services found in response');
        }

        // Normalize service data for consistent access
        const normalizedServices = rawServices.map((item) => {
          const requiresPreApproval =
            item.requiresPreApproval ||
            item.requiresPreAuth ||
            item.requiresPA ||
            item.requires_pre_auth ||
            false;

          return {
            // IDs
            id: normalizeId(item.medicalServiceId || item.serviceId || item.id),
            medicalServiceId: normalizeId(item.medicalServiceId || item.serviceId || item.id),

            // Service info
            serviceCode: item.serviceCode || item.code,
            serviceName: item.serviceName || item.name,
            code: item.serviceCode || item.code,
            name: item.serviceName || item.name,

            // Category info - MULTIPLE FIELDS FOR ROBUSTNESS
            categoryId: normalizeId(item.categoryId || item.serviceCategoryId || item.medicalCategoryId || item.effectiveCategory?.id),
            categoryName: item.categoryName || item.effectiveCategory?.name || item.medicalCategory?.name || item.category || null,
            categoryCode: item.categoryCode || item.effectiveCategory?.code || item.medicalCategory?.code || null,
            category: item.categoryName || item.effectiveCategory?.name || item.medicalCategory?.name || item.category || null,

            // Pricing
            contractPrice: item.contractPrice || item.price,
            price: item.contractPrice || item.price,

            // Pre-approval flags
            requiresPreApproval,
            requiresPreAuth: requiresPreApproval,

            // Contract
            hasContract: item.hasContract !== false
          };
        });

        const paOnlyServices = normalizedServices.filter((s) => s.requiresPreApproval || s.requiresPreAuth);

        setServices(paOnlyServices);
        console.log('[PRE-AUTH] Services normalized:', {
          count: paOnlyServices.length,
          sample: paOnlyServices.slice(0, 2).map((s) => ({
            id: s.id,
            name: s.name,
            categoryId: s.categoryId,
            categoryName: s.categoryName,
            requiresPA: s.requiresPreApproval
          }))
        });
      } catch (err) {
        console.error('[PRE-AUTH] Error loading services:', err);
        setServices([]);
      } finally {
        setLoadingServices(false);
      }
    };

    loadServices();
  }, [visitData.fromVisitLog, normalizeId]);

  // ══════════════════════════════════════════════════════════════════════════════
  // FILTERED SERVICES BY SELECTED CATEGORY
  // Show ALL services in the category (with Badge for PA requirement)
  // ══════════════════════════════════════════════════════════════════════════════
  const getFilteredServicesForCategory = useCallback((category) => {
    if (!category) {
      return [];
    }

    return services.filter((s) => doesServiceMatchCategory(s, category));
  }, [services, doesServiceMatchCategory]);

  // ══════════════════════════════════════════════════════════════════════════════
  // FORM VALIDATION
  // ══════════════════════════════════════════════════════════════════════════════
  const isFormValid = useMemo(() => {
    return visitData.visitId && serviceRows.length > 0 && serviceRows.every((row) => !!row.service);
  }, [visitData.visitId, serviceRows]);

  // ══════════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ══════════════════════════════════════════════════════════════════════════════
  const handleCategoryChange = useCallback((rowId, newValue) => {
    setServiceRows((prev) =>
      prev.map((row) =>
        row.rowId === rowId
          ? {
            ...row,
            category: newValue,
            service: null
          }
          : row
      )
    );
  }, []);

  const handleServiceChange = useCallback((rowId, newValue) => {
    setServiceRows((prev) => prev.map((row) => (row.rowId === rowId ? { ...row, service: newValue } : row)));
  }, []);

  const handleQuantityChange = useCallback((rowId, value) => {
    const parsed = Number(value);
    const quantity = Number.isNaN(parsed) ? 1 : Math.max(1, Math.min(99, parsed));

    setServiceRows((prev) => prev.map((row) => (row.rowId === rowId ? { ...row, quantity } : row)));
  }, []);

  const handleAddServiceRow = useCallback(() => {
    setServiceRows((prev) => [...prev, createEmptyServiceRow()]);
  }, []);

  const handleRemoveServiceRow = useCallback((rowId) => {
    setServiceRows((prev) => (prev.length === 1 ? prev : prev.filter((row) => row.rowId !== rowId)));
  }, []);

  const handleAttachmentChange = useCallback((event) => {
    const selectedFiles = Array.from(event.target.files || []);
    if (!selectedFiles.length) return;

    const { validFiles, invalidMessages } = validateSelectedFiles(selectedFiles);

    if (invalidMessages.length) {
      setError(`بعض الملفات مرفوضة:\n${invalidMessages.join('\n')}`);
    }

    if (validFiles.length) {
      setAttachments((prev) => [...prev, ...validFiles]);
    }

    event.target.value = '';
  }, [validateSelectedFiles]);

  const handleRemoveAttachment = useCallback((index) => {
    setAttachments((prev) => prev.filter((_, currentIndex) => currentIndex !== index));
  }, []);

  const handleBack = useCallback(() => {
    navigate('/provider/visits');
  }, [navigate]);

  const handleCreatePreApproval = useCallback(async (finalSubmit) => {
    setAttemptedSubmit(true);

    if (!visitData.visitId) {
      setError('معرف الزيارة مطلوب');
      return;
    }
    if (!serviceRows.every((row) => row.service)) {
      setError('يجب اختيار خدمة طبية لكل صف خدمة');
      return;
    }

    try {
      setSubmitting(true);
      setSubmitMode(finalSubmit ? 'final' : 'draft');
      setError(null);

      const createdIds = [];

      for (const row of serviceRows) {
        const quantity = Number(row.quantity) > 0 ? Number(row.quantity) : 1;
        const quantityNote = quantity > 1 ? `\nالكمية المطلوبة: ${quantity}` : '';

        const payload = {
          visitId: parseInt(visitData.visitId),
          memberId: visitData.memberId ? parseInt(visitData.memberId) : null,
          medicalServiceId: row.service.medicalServiceId || row.service.serviceId || row.service.id,
          serviceCategoryId: row.category?.id || row.service.categoryId || null,
          diagnosisCode: diagnosisCode || null,
          diagnosisDescription: diagnosisDescription || null,
          priority: priority,
          notes: `${notes || ''}${quantityNote}`.trim() || null,
          currency: 'LYD'
        };

        const response = await axiosClient.post('/pre-authorizations', payload);
        const preAuthId = response.data?.data?.id;

        if (!preAuthId) continue;
        createdIds.push(preAuthId);

        if (attachments.length > 0) {
          for (const file of attachments) {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('attachmentType', 'MEDICAL_REPORT');

            await axiosClient.post(`/pre-authorizations/${preAuthId}/attachments`, formData, {
              headers: { 'Content-Type': 'multipart/form-data' }
            });
          }
        }

        if (finalSubmit) {
          await axiosClient.post(`/pre-authorizations/${preAuthId}/submit`);
        }
      }

      if (createdIds.length > 0) {
        setCreatedPreApprovalId(createdIds[0]);
        setSuccessDialogOpen(true);
        setAttemptedSubmit(false);
      } else {
        setError('فشل في إنشاء الموافقة المسبقة');
      }
    } catch (err) {
      console.error('Error creating pre-approval:', err);
      const errorMessage = err.response?.data?.message || err.message || 'فشل في إنشاء الموافقة المسبقة';
      setError(errorMessage);
    } finally {
      setSubmitting(false);
    }
  }, [visitData, serviceRows, diagnosisCode, diagnosisDescription, priority, notes, attachments]);

  const handleSuccessClose = useCallback(() => {
    setSuccessDialogOpen(false);
    navigate('/provider/visits');
  }, [navigate]);

  const handleViewPreApproval = useCallback(() => {
    setSuccessDialogOpen(false);
    if (createdPreApprovalId) {
      navigate(`/pre-approvals/${createdPreApprovalId}`);
    }
  }, [navigate, createdPreApprovalId]);

  // ══════════════════════════════════════════════════════════════════════════════
  // RENDER - NO VISIT DATA (ACCESS BLOCKED)
  // ══════════════════════════════════════════════════════════════════════════════
  if (!visitData.fromVisitLog || !visitData.visitId) {
    return (
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
            يرجى الانتقال إلى سجل الزيارات واختيار زيارة لإنشاء موافقة مسبقة منها.
          </Typography>
          <Button
            variant="contained"
            size="large"
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/provider/visits')}
            sx={{ borderRadius: '0.25rem', px: '2.0rem' }}
          >
            الذهاب إلى سجل الزيارات
          </Button>
        </Card>
      </Box>
    );
  }

  // ══════════════════════════════════════════════════════════════════════════════
  // RENDER - MAIN FORM (Desktop-First Layout)
  // ══════════════════════════════════════════════════════════════════════════════
  return (
    <Box sx={{ maxWidth: '87.5rem', mx: 'auto' }}>
      {/* ═══════════════════════ PAGE HEADER ═══════════════════════ */}
      <ModernPageHeader
        title={LABELS.pageTitle}
        subtitle={LABELS.pageSubtitle}
        icon={ApprovalIcon}
        breadcrumbs={[{ label: 'بوابة مقدم الخدمة' }, { label: 'سجل الزيارات', href: '/provider/visits' }, { label: LABELS.pageTitle }]}
      />

      {/* ═══════════════════════ LOADING BAR ═══════════════════════ */}
      {loading && <LinearProgress sx={{ mb: '1.0rem', borderRadius: 1 }} />}

      {/* ═══════════════════════ ERROR ALERT ═══════════════════════ */}
      {error && (
        <Alert severity="error" sx={{ mb: '1.5rem', borderRadius: '0.25rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* ═══════════════════════ CONTRACT WARNING ═══════════════════════ */}
      {!contract && !loading && (
        <Alert severity="warning" sx={{ mb: '1.5rem', borderRadius: '0.25rem' }}>
          <Typography fontWeight={600}>{LABELS.noContract}</Typography>
          <Typography variant="body2">تواصل مع إدارة النظام للتحقق من حالة عقد مقدم الخدمة.</Typography>
        </Alert>
      )}

      <Stack spacing={3}>
        {/* ═══════════════════════ ROW 1: VISIT & MEMBER INFO ═══════════════════════ */}
        <Grid container spacing={3}>
          {/* Visit Info Card */}
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoCard bgcolor={(theme) => alpha(theme.palette.info.main, 0.04)}>
              <SectionHeader icon={VisitIcon} title={LABELS.visitInfo} subtitle="معلومات الزيارة المرتبطة (للقراءة فقط)" color="info" />
              <Divider sx={{ mb: '1.25rem' }} />

              <Grid container spacing={2}>
                <Grid size={6}>
                  <ReadOnlyField icon={BadgeIcon} label="رقم الزيارة" value={`#${visitData.visitId}`} highlight />
                </Grid>
                <Grid size={6}>
                  <ReadOnlyField icon={CalendarIcon} label="تاريخ الزيارة" value={visitData.visitDate} />
                </Grid>
                <Grid size={6}>
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
                      نوع الزيارة
                    </Typography>
                    <Chip
                      label={VISIT_TYPE_LABELS[visitData.visitType] || visitData.visitType}
                      size="small"
                      color="info"
                      variant="filled"
                      sx={{ fontWeight: 500 }}
                    />
                  </Box>
                </Grid>
                <Grid size={6}>
                  <ReadOnlyField
                    icon={BusinessIcon}
                    label="مقدم الخدمة"
                    value={
                      visitData.providerName && visitData.providerName !== '—'
                        ? visitData.providerName
                        : contract?.provider?.name || user?.name || '—'
                    }
                  />
                </Grid>
              </Grid>
            </InfoCard>
          </Grid>

          {/* Member Info Card */}
          <Grid size={{ xs: 12, md: 6 }}>
            <InfoCard bgcolor={(theme) => alpha(theme.palette.success.main, 0.04)}>
              <SectionHeader icon={PersonIcon} title={LABELS.memberInfo} subtitle="بيانات المؤمن عليه (للقراءة فقط)" color="success" />
              <Divider sx={{ mb: '1.25rem' }} />

              <Grid container spacing={2}>
                <Grid size={12}>
                  <ReadOnlyField icon={PersonIcon} label="اسم المؤمن عليه" value={visitData.memberName} highlight />
                </Grid>
                <Grid size={6}>
                  <ReadOnlyField label="الرقم المدني" value={visitData.memberCivilId} />
                </Grid>
                <Grid size={6}>
                  <ReadOnlyField icon={CardIcon} label="رقم البطاقة التأمينية" value={visitData.cardNumber} />
                </Grid>
                <Grid size={12}>
                  <ReadOnlyField icon={BusinessIcon} label="جهة العمل / الوثيقة" value={visitData.employer} />
                </Grid>
              </Grid>
            </InfoCard>
          </Grid>
        </Grid>

        {/* ═══════════════════════ ROW 2: SERVICE SELECTION TABLE ═══════════════════════ */}
        <FormSection highlighted>
          <SectionHeader
            icon={MedicalServicesIcon}
            title="الخدمة الطبية المطلوبة"
            subtitle="اختر التصنيف أولاً ثم الخدمة الطبية"
            color="primary"
            action={
              <Button
                size="small"
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={handleAddServiceRow}
                disabled={submitting}
                sx={{ borderRadius: '0.25rem' }}
              >
                إضافة خدمة
              </Button>
            }
          />
          <Divider sx={{ mb: '1.5rem' }} />

          {/* 💡 Diagnosis Context Banner (New) */}
          {(diagnosisCode || diagnosisDescription) && (
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
                  طلب موافقة للارتباط الطبي التالي:
                </Typography>
                {diagnosisCode && (
                  <Chip
                    label={`التشخيص: ${diagnosisCode}`}
                    size="small"
                    color="primary"
                    variant="outlined"
                    sx={{ fontWeight: 600 }}
                  />
                )}
                {diagnosisDescription && (
                  <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', ml: 1 }}>
                    "{diagnosisDescription}"
                  </Typography>
                )}
              </Stack>
            </Alert>
          )}

          {!loadingServices && services.length === 0 && (
            <Alert severity="warning" sx={{ mb: '1.0rem' }}>
              لا توجد خدمات في عقد مقدم الخدمة تتطلب موافقة مسبقة حالياً.
            </Alert>
          )}

          {/* Service Selection Table */}
          <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: '0.25rem' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: tableHeaderBg }}>
                  <TableCell width="35%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
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
                  <TableCell width="45%" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
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
                  <TableCell width="12%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                    الكمية
                  </TableCell>
                  <TableCell width="13%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                    <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5}>
                      <LockIcon fontSize="small" />
                      <span>السعر</span>
                    </Stack>
                  </TableCell>
                  <TableCell width="12%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                    الارتباط
                  </TableCell>
                  <TableCell width="10%" align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                    إجراء
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {serviceRows.map((row) => (
                  <TableRow key={row.rowId} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
                    {/* Category Selector (Step 1) */}
                    <TableCell>
                      <Autocomplete
                        size="small"
                        options={categories}
                        getOptionLabel={(option) => option?.name || option?.code || ''}
                        value={row.category ?? null}
                        loading={loading}
                        onChange={(event, newValue) => handleCategoryChange(row.rowId, newValue)}
                        disabled={submitting}
                        isOptionEqualToValue={(option, value) => option?.id === value?.id}
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            placeholder="اختر التصنيف أولاً..."
                            error={attemptedSubmit && !row.category}
                            sx={{
                              '& .MuiOutlinedInput-root': {
                                bgcolor: row.category
                                  ? (theme) => alpha(theme.palette.success.main, 0.1)
                                  : (theme) => alpha(theme.palette.warning.main, 0.1)
                              }
                            }}
                          />
                        )}
                        renderOption={(props, option) => {
                          const { key, ...otherProps } = props;
                          // Count services with multiple matching strategies for robustness
                          const serviceCount = services.filter((s) => doesServiceMatchCategory(s, option)).length;

                          return (
                            <li key={key} {...otherProps}>
                              <Stack direction="row" alignItems="center" spacing={1} sx={{ width: '100%' }}>
                                <CategoryIcon fontSize="small" color="primary" />
                                <Typography variant="body2" fontWeight="medium">
                                  {option.name || option.code}
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
                        options={getFilteredServicesForCategory(row.category)}
                        getOptionLabel={(option) => {
                          const code = option.serviceCode || option.code ? `[${option.serviceCode || option.code}] ` : '';
                          return `${code}${option.serviceName || option.name || ''}`;
                        }}
                        filterOptions={(options, { inputValue }) => {
                          const search = inputValue.toLowerCase();
                          return options.filter(
                            (opt) =>
                              ((opt.serviceCode || opt.code) && (opt.serviceCode || opt.code).toLowerCase().includes(search)) ||
                              ((opt.serviceName || opt.name) && (opt.serviceName || opt.name).toLowerCase().includes(search))
                          );
                        }}
                        value={row.service ?? null}
                        loading={loadingServices}
                        onChange={(event, newValue) => handleServiceChange(row.rowId, newValue)}
                        disabled={submitting || !row.category}
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            placeholder={row.category ? 'ابحث برمز الخدمة أو اسمها...' : '⚠️ اختر التصنيف أولاً'}
                            error={attemptedSubmit && row.category && !row.service}
                            sx={{
                              '& .MuiOutlinedInput-root': {
                                bgcolor: !row.category ? 'grey.100' : undefined
                              }
                            }}
                          />
                        )}
                        renderOption={(props, option) => {
                          const { key, ...otherProps } = props;
                          const requiresPA = option.requiresPreApproval || option.requiresPreAuth || false;

                          return (
                            <li key={key} {...otherProps}>
                              <Stack spacing={0.5} sx={{ width: '100%' }}>
                                <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
                                  <Chip
                                    label={option.serviceCode || option.code}
                                    size="small"
                                    color="primary"
                                    variant="outlined"
                                    sx={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '0.75rem' }}
                                  />
                                  <Typography variant="body2" fontWeight="medium">
                                    {option.serviceName || option.name}
                                  </Typography>
                                  {requiresPA && (
                                    <Tooltip title={`هذه الخدمة سيتم طلبها للارتباط بتشخيص: ${diagnosisCode || 'غير محدد بعد'}`}>
                                      <Chip
                                        label="🟡 طلب موافقة"
                                        size="small"
                                        color="warning"
                                        variant="outlined"
                                        sx={{ fontSize: '0.75rem', height: '1.25rem' }}
                                      />
                                    </Tooltip>
                                  )}
                                </Stack>
                                {(option.contractPrice || option.price) && (
                                  <Typography variant="caption" color="success.main">
                                    💰 سعر العقد: {formatCurrency(option.contractPrice || option.price)}
                                  </Typography>
                                )}
                              </Stack>
                            </li>
                          );
                        }}
                      />
                    </TableCell>

                    {/* Price */}
                    <TableCell align="center">
                      <TextField
                        size="small"
                        type="number"
                        inputProps={{ min: 1, max: 99 }}
                        value={row.quantity ?? 1}
                        onChange={(event) => handleQuantityChange(row.rowId, event.target.value)}
                        sx={{ width: '5.625rem' }}
                        disabled={submitting}
                      />
                    </TableCell>

                    <TableCell align="center">
                      <ContractPriceChip
                        loading={loadingServices}
                        price={row.service?.contractPrice || row.service?.price || 0}
                        hasContract={!!row.service}
                        error={null}
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title={diagnosisCode ? `سيرتبط بتشخيص: ${diagnosisCode}` : 'يرجى كتابة التشخيص أعلاه'}>
                        <Chip
                          icon={<DiagnosisIcon style={{ fontSize: '0.9rem' }} />}
                          label="للتشخيص"
                          size="small"
                          color="info"
                          variant="outlined"
                          sx={{ fontWeight: 500, opacity: diagnosisCode ? 1 : 0.6 }}
                        />
                      </Tooltip>
                    </TableCell>
                    <TableCell align="center">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleRemoveServiceRow(row.rowId)}
                        disabled={submitting || serviceRows.length === 1}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

        </FormSection>

        {/* ═══════════════════════ ROW 3: DIAGNOSIS & REQUEST DETAILS ═══════════════════════ */}
        <Grid container spacing={3}>
          {/* Diagnosis Section */}
          <Grid size={{ xs: 12, md: 6 }}>
            <FormSection>
              <SectionHeader icon={DiagnosisIcon} title={LABELS.diagnosis} subtitle="رمز ووصف التشخيص الطبي" />
              <Divider sx={{ mb: '1.5rem' }} />

              <Stack spacing={2.5}>
                <TextField
                  fullWidth
                  label={LABELS.diagnosisCode}
                  value={diagnosisCode}
                  onChange={(e) => setDiagnosisCode(e.target.value)}
                  placeholder="مثال: J06.9"
                  helperText="أدخل رمز التشخيص حسب تصنيف ICD-10"
                  InputProps={{
                    sx: { fontFamily: 'monospace', fontWeight: 600 }
                  }}
                />
                <TextField
                  fullWidth
                  label={LABELS.diagnosisDescription}
                  value={diagnosisDescription}
                  onChange={(e) => setDiagnosisDescription(e.target.value)}
                  placeholder="وصف التشخيص الطبي..."
                  multiline
                  rows={2}
                />
              </Stack>
            </FormSection>
          </Grid>

          {/* Request Details Section */}
          <Grid size={{ xs: 12, md: 6 }}>
            <FormSection>
              <SectionHeader icon={PriorityIcon} title={LABELS.requestDetails} subtitle="أولوية الطلب والملاحظات الطبية" />
              <Divider sx={{ mb: '1.5rem' }} />

              <Stack spacing={2.5}>
                <FormControl fullWidth>
                  <InputLabel>{LABELS.priority}</InputLabel>
                  <Select value={priority} onChange={(e) => setPriority(e.target.value)} label={LABELS.priority}>
                    {PRIORITY_OPTIONS.map((opt) => (
                      <MenuItem key={opt.value} value={opt.value}>
                        <Stack direction="row" spacing={2} alignItems="center" sx={{ width: '100%' }}>
                          <Chip label={opt.label} size="small" color={opt.color} sx={{ minWidth: '4.375rem' }} />
                          <Typography variant="body2" color="text.secondary">
                            {opt.description}
                          </Typography>
                        </Stack>
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  fullWidth
                  multiline
                  rows={3}
                  label={LABELS.notes}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="أدخل أي ملاحظات طبية إضافية تساعد في مراجعة الطلب..."
                  InputProps={{
                    startAdornment: <NotesIcon color="action" sx={{ mr: 1, mt: 1, alignSelf: 'flex-start' }} />
                  }}
                />
              </Stack>
            </FormSection>
          </Grid>
        </Grid>

        <FormSection>
          <SectionHeader icon={AttachFileIcon} title="المستندات المرفقة" subtitle="يمكن رفع ملفات طبية داعمة للطلب" color="secondary" />
          <Divider sx={{ mb: '1.0rem' }} />
          <Stack spacing={2}>
            <Alert severity="info" sx={{ borderRadius: '0.25rem' }}>
              الامتدادات المسموحة: PDF, JPG, JPEG, PNG, GIF, DOC, DOCX — الحد الأقصى للحجم: {MAX_UPLOAD_SIZE_MB}MB لكل ملف.
            </Alert>
            <Button component="label" variant="outlined" startIcon={<UploadFileIcon />} disabled={submitting} sx={{ width: 'fit-content' }}>
              إضافة مستندات
              <input hidden type="file" multiple accept={FILE_ACCEPT_ATTR} onChange={handleAttachmentChange} />
            </Button>

            {attachments.length > 0 ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {attachments.map((file, index) => (
                  <Chip
                    key={`${file.name}-${index}`}
                    label={file.name}
                    onDelete={() => handleRemoveAttachment(index)}
                    deleteIcon={<DeleteIcon />}
                    variant="outlined"
                    color="info"
                  />
                ))}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                لا توجد مستندات مرفقة حالياً.
              </Typography>
            )}
          </Stack>
        </FormSection>

        {/* ═══════════════════════ ROW 4: ACTION BUTTONS (Sticky Footer) ═══════════════════════ */}
        <Paper
          elevation={3}
          sx={{
            p: '1.25rem',
            borderRadius: '0.25rem',
            bgcolor: 'background.paper',
            position: 'sticky',
            bottom: '8.0rem',
            zIndex: 10,
            border: '1px solid',
            borderColor: 'divider'
          }}
        >
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems="center">
            {/* Left: Form Status */}
            <Stack direction="row" spacing={2} alignItems="center">
              {!isFormValid && (
                <Alert severity="warning" sx={{ py: 0.5 }}>
                  <Typography variant="body2">⚠️ يجب اختيار خدمة طبية لكل صف قبل تقديم الطلب</Typography>
                </Alert>
              )}
              {isFormValid && (
                <Alert severity="success" sx={{ py: 0.5 }}>
                  <Typography variant="body2">✅ جاهز للتقديم</Typography>
                </Alert>
              )}
            </Stack>

            {/* Right: Action Buttons */}
            <Stack direction="row" spacing={2}>
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
              <Button
                variant="outlined"
                color="primary"
                size="large"
                startIcon={submitting && submitMode === 'draft' ? <CircularProgress size={20} color="inherit" /> : <NotesIcon />}
                onClick={() => handleCreatePreApproval(false)}
                disabled={submitting || !isFormValid}
                sx={{ borderRadius: '0.25rem', px: '1.5rem' }}
              >
                {submitting && submitMode === 'draft' ? LABELS.savingDraft : LABELS.saveDraft}
              </Button>
              <Button
                variant="contained"
                color="primary"
                size="large"
                startIcon={submitting && submitMode === 'final' ? <CircularProgress size={20} color="inherit" /> : <SendIcon />}
                onClick={() => handleCreatePreApproval(true)}
                disabled={submitting || !isFormValid}
                sx={{
                  borderRadius: '0.25rem',
                  px: '2.0rem',
                  boxShadow: 2,
                  '&:hover': { boxShadow: 4 }
                }}
              >
                {submitting && submitMode === 'final' ? LABELS.submittingFinal : LABELS.submitFinal}
              </Button>
            </Stack>
          </Stack>
        </Paper>
      </Stack>

      {/* ═══════════════════════ SUCCESS DIALOG ═══════════════════════ */}
      <SuccessDialog
        open={successDialogOpen}
        onClose={handleSuccessClose}
        title={submitMode === 'final' ? LABELS.successTitleFinal : LABELS.successTitleDraft}
        message={submitMode === 'final' ? LABELS.successMessageFinal : LABELS.successMessageDraft}
        primaryAction={{
          label: 'عرض الطلب',
          onClick: handleViewPreApproval
        }}
        secondaryAction={{
          label: 'العودة لسجل الزيارات',
          onClick: handleSuccessClose
        }}
      />
    </Box>
  );
};

export default ProviderPreApprovalSubmission;



