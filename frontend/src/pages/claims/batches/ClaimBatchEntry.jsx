/**
 * صفحة إدخال الدفعة — تخطيط RTL يملأ الشاشة
 * ✅ الجدول والفورم من اليمين لليسار
 * ✅ زر الحفظ مرئي دون scroll
 * ✅ كل النصوص من ar.js (لا hardcode)
 */
import { useState, useMemo, useRef, useCallback, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { keyframes } from '@mui/system';
import {
  Box,
  Stack,
  Typography,
  Button,
  TextField,
  Autocomplete,
  Divider,
  CircularProgress,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Paper,
  Checkbox,
  Tooltip,
  alpha,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Collapse
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  Add as AddIcon,
  Receipt as ReceiptIcon,
  ArrowBack as BackIcon,
  Close as DiscardIcon,
  VerifiedUser as PolicyIcon,
  Info as InfoIcon,
  Block as RejectIcon,
  ViewColumn as ViewColumnIcon,
  UnfoldLess as CompactIcon,
  UnfoldMore as ExpandIcon
} from '@mui/icons-material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';

import { ModernPageHeader } from 'components/tba';
import useLocale from 'hooks/useLocale';

import unifiedMembersService from 'services/api/unified-members.service';
import providersService from 'services/api/providers.service';
import claimsService from 'services/api/claims.service';
import * as medicalCategoriesService from 'services/api/medical-categories.service';
import { getActiveClaimContexts } from 'services/api/claim-contexts.service';
import claimBatchesService from 'services/api/claim-batches.service';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { claimRejectionReasonsService } from 'services/api/claim-rejection-reasons.service';
import { normalizeApiError, runWithRetry } from 'utils/api-error';
import axiosClient from 'utils/axios';

import { useCalculationLogic } from './hooks/useCalculationLogic';
import { useCoverageLogic } from './hooks/useCoverageLogic';
import { failedCoverageResult } from './hooks/coverageContract.mjs';

import { ClaimHeaderFields } from './components/ClaimHeaderFields';
import { ClaimAdditionalDetails } from './components/ClaimAdditionalDetails';
import { invalidQuantityLineNumbers } from './claim-entry-validation';
import { ClaimLineRow } from './components/ClaimLineRow';
import { ClaimTotalsFooter } from './components/ClaimTotalsFooter';
import { RecoveryDialog } from './components/RecoveryDialog';
import { RejectClaimDialog } from './components/RejectClaimDialog';
import { ConfirmDeleteClaimDialog } from './components/ConfirmDeleteClaimDialog';
import { ActionConfirmDialog } from './components/ActionConfirmDialog';
import { CustomServiceDialog } from './components/CustomServiceDialog';
import { getServiceContext } from './claim-context.mjs';

// ── أسماء الشهور ─────────────────────────────────────────────────────────────
const MONTHS_AR = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];

const draftPulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.3; transform: scale(0.75); }
`;

const newLine = () => ({
  id: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15),
  service: null,
  serviceName: '',
  serviceCode: '',
  quantity: 1,
  unitPrice: 0,
  contractPrice: 0,
  maxContractPrice: 0,
  byCompany: 0,
  byEmployee: 0,
  refusalTypes: '',
  total: 0,
  coveragePercent: null,
  requiresPreApproval: false,
  notCovered: false,
  rejected: false,
  rejectionReason: '',
  manualRefusedAmount: 0,
  oldRejected: 0
});

const newDirectEntryKey = () => globalThis.crypto?.randomUUID?.() || `claim-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const normalizeArabicSearch = (value = '') =>
  String(value)
    .normalize('NFKD')
    .replace(/[\u064B-\u065F\u0670]/g, '')
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .toLowerCase();

const GENERATED_SERVICE_CODE_PATTERN = /^(PL-|SYS-)/i;

const isGeneratedServiceCode = (code = '') => GENERATED_SERVICE_CODE_PATTERN.test(String(code).trim());

const buildServiceDisplayLabel = ({ code = '', name = '' } = {}) => {
  const cleanCode = String(code || '').trim();
  const cleanName = String(name || '').trim();

  if (!cleanCode || isGeneratedServiceCode(cleanCode)) return cleanName;
  if (cleanName && normalizeArabicSearch(cleanName).includes(normalizeArabicSearch(cleanCode))) return cleanName;
  return cleanName ? `${cleanCode} — ${cleanName}` : cleanCode;
};

const hasMeaningfulDraftData = (draft) => {
  if (!draft) return false;
  if (draft.member?.id) return true;
  if ((draft.diagnosis || '').trim()) return true;
  if ((draft.complaint || '').trim()) return true;
  if ((draft.notes || '').trim()) return true;
  return Array.isArray(draft.lines) && draft.lines.some((l) => l?.serviceName || l?.serviceCode || l?.service);
};

const TH = ({ children, align = 'center', w, sx: sxOver = {} }) => {
  const theme = useTheme();
  return (
    <TableCell
      align={align}
      sx={{
        bgcolor: theme.palette.mode === 'dark' ? theme.palette.grey[900] : '#f8f9fa',
        color: theme.palette.primary.dark,
        fontWeight: 700,
        fontSize: '0.8rem',
        py: 0.65,
        px: '0.55rem',
        whiteSpace: 'nowrap',
        borderBottom: `2px solid ${alpha(theme.palette.primary.main, 0.3)}`,
        borderRight: `1px solid ${alpha(theme.palette.primary.main, 0.1)}`,
        '&:last-child': { borderRight: 'none' },
        position: 'sticky',
        top: 0,
        zIndex: 10,
        ...(w && { width: w, minWidth: w }),
        ...sxOver
      }}
    >
      {children}
    </TableCell>
  );
};

// ══════════════════════════════════════════════════════════════════════════════
export default function ClaimBatchEntry() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();
  const theme = useTheme();
  const { t } = useLocale();

  const employerId = searchParams.get('employerId');
  const providerId = searchParams.get('providerId');
  const month = parseInt(searchParams.get('month'));
  const year = parseInt(searchParams.get('year'));
  const initialClaimId = searchParams.get('claimId');
  const initialMemberId = searchParams.get('memberId');
  const initialMemberName = searchParams.get('memberName');
  const initialMemberCardNumber = searchParams.get('cardNumber');
  const initialServiceDate = searchParams.get('serviceDate') || searchParams.get('visitDate');
  const initialVisitType = searchParams.get('visitType');

  // ── حالة النموذج ─────────────────────────────────────────────────────────
  const [member, setMember] = useState(null);
  const [memberInput, setMemberInput] = useState('');
  const [debouncedMemberInput, setDebouncedMemberInput] = useState('');
  const [serviceSearchInput, setServiceSearchInput] = useState('');
  const [debouncedServiceSearch, setDebouncedServiceSearch] = useState('');
  const [debouncedServiceDate, setDebouncedServiceDate] = useState('');
  const [diagnosis, setDiagnosis] = useState('');
  const [doctorName, setDoctorName] = useState('');
  const [complaint, setComplaint] = useState('');
  const [applyBenefits, setApplyBenefits] = useState(true);
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState([newLine()]);
  const [beneficiaryPaidAmount, setBeneficiaryPaidAmount] = useState('');
  const [saving, setSaving] = useState(false);
  const [isDirty, setIsDirty] = useState(false);
  const [policyId, setPolicyId] = useState(null);
  const [policyInfo, setPolicyInfo] = useState(null);

  // Rejection State
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectType, setRejectType] = useState('claim'); // 'claim' or 'line'
  const [rejectIdx, setRejectIdx] = useState(null);
  const [rejectionInput, setRejectionInput] = useState('');
  const [rejectionMode, setRejectionMode] = useState('full'); // 'full' | 'partial'
  const [manualRefusedAmountInput, setManualRefusedAmountInput] = useState('');
  const [isClaimRejected, setIsClaimRejected] = useState(false);
  // Rejection reasons list management
  const [editingReasonId, setEditingReasonId] = useState(null);
  const [editingReasonText, setEditingReasonText] = useState('');
  const [isDeletingReasonId, setIsDeletingReasonId] = useState(null);
  const [showReasonsList, setShowReasonsList] = useState(false);
  const [attachments, setAttachments] = useState([]);
  const [editingClaimId, setEditingClaimId] = useState(initialClaimId);
  const [editHydrationVersion, setEditHydrationVersion] = useState(0);
  const [editCoverageLoading, setEditCoverageLoading] = useState(!!initialClaimId);
  const [preAuthId, setPreAuthId] = useState('');
  const [directEntryKey, setDirectEntryKey] = useState(newDirectEntryKey);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [showValidationErrors, setShowValidationErrors] = useState(false);
  const [autoSaveStatus, setAutoSaveStatus] = useState('idle');
  const [draftVersion, setDraftVersion] = useState(null);
  const [draftBatchId, setDraftBatchId] = useState(null);
  const [recoveryDialog, setRecoveryDialog] = useState({ open: false, serverDraft: null, localDraft: null });
  const [classificationReview, setClassificationReview] = useState({
    open: false,
    lineIndex: null,
    selectedCategoryId: ''
  });

  // Generic confirmation dialog
  const [actionConfirm, setActionConfirm] = useState({ open: false, title: '', message: '', onConfirm: null, severity: 'warning' });
  const closeActionConfirm = () => setActionConfirm((prev) => ({ ...prev, open: false }));
  const triggerConfirm = (title, message, onConfirm, severity = 'error') =>
    setActionConfirm({ open: true, title, message, onConfirm, severity });

  const currentUserRole = (() => {
    try {
      const rolesStr = localStorage.getItem('userRoles');
      if (rolesStr) {
        const roles = JSON.parse(rolesStr);
        return Array.isArray(roles) ? roles[0] : '';
      }
    } catch {
      /* ignore */
    }
    return '';
  })();
  const isReviewer = currentUserRole === 'MEDICAL_REVIEWER';

  // Column Visibility State (Clutter Reduction)
  const [visibleColumns, setVisibleColumns] = useState({
    coverage: true,
    benefitLimit: true,
    remainingLimit: true,
    refused: true,
    companyShare: !isReviewer,
    patientShare: true
  });
  const [anchorElCols, setAnchorElCols] = useState(null);
  const [headerExpanded, setHeaderExpanded] = useState(true);
  const handleOpenCols = (event) => setAnchorElCols(event.currentTarget);
  const handleCloseCols = () => setAnchorElCols(null);
  const handleToggleColumn = (col) => {
    setVisibleColumns((prev) => ({ ...prev, [col]: !prev[col] }));
  };

  const initialEncounterType = initialVisitType === 'INPATIENT' ? 'INPATIENT' : 'OUTPATIENT';
  const [encounterType, setEncounterType] = useState(initialEncounterType);
  const [claimContextCode, setClaimContextCode] = useState(initialEncounterType);
  const [fullCoverage, setFullCoverage] = useState(false);

  // A batch period is not a service date. Guessing the first day silently
  // creates a financially valid claim on a date the operator never chose.
  const defaultDate = '';

  const [serviceDate, setServiceDate] = useState(initialServiceDate || defaultDate);

  useEffect(() => {
    if (!initialMemberId || member?.id) return;
    const hydratedMember = {
      id: Number(initialMemberId),
      fullName: initialMemberName || '',
      cardNumber: initialMemberCardNumber || ''
    };
    setMember(hydratedMember);
    setMemberInput(initialMemberName || initialMemberCardNumber || initialMemberId);
  }, [initialMemberCardNumber, initialMemberId, initialMemberName, member?.id]);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedMemberInput(memberInput), 350);
    return () => clearTimeout(t);
  }, [memberInput]);
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedServiceSearch(serviceSearchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [serviceSearchInput]);
  useEffect(() => {
    // MUI's segmented date field emits intermediate but valid years while the
    // user is typing (for example 2020 on the way to 2026). Do not send those
    // transient dates to the financial resolver.
    const timer = setTimeout(() => setDebouncedServiceDate(serviceDate), 450);
    return () => clearTimeout(timer);
  }, [serviceDate]);

  const memberRef = useRef(null);
  const diagnosisRef = useRef(null);
  const serviceDateRef = useRef(null);
  const linesRef = useRef(lines);
  const saveQueueRef = useRef(Promise.resolve());
  const autosaveTimerRef = useRef(null);
  const recoveryCheckedRef = useRef(false);
  const recoveryDismissedRef = useRef(false);
  const skipAutosaveRef = useRef(false);

  const draftStorageKey = useMemo(
    () => `claim-draft:${employerId || 'none'}:${providerId || 'none'}:${year || 'none'}:${month || 'none'}`,
    [employerId, providerId, year, month]
  );

  // Keep linesRef in sync
  useEffect(() => {
    linesRef.current = lines;
  }, [lines]);

  // ── الاستعلامات الأساسية اللازمة للمنطق ──────────────────────────────────────
  const { data: allCategories } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: () => medicalCategoriesService.getAllMedicalCategories(),
    staleTime: Infinity
  });

  const medicalCategories = useMemo(() => allCategories || [], [allCategories]);
  const { data: claimContexts = [] } = useQuery({
    queryKey: ['claim-contexts'],
    queryFn: getActiveClaimContexts,
    staleTime: 5 * 60 * 1000
  });

  // ── المنطق المالي وتغطية الخدمات (المرحلة 3: Hooks المستخرجة) ─────────────────
  const { recompute } = useCalculationLogic();

  const { refetchAllLinesCoverage } = useCoverageLogic({
    policyId,
    member,
    medicalCategories,
    encounterType,
    claimContextCode,
    setLines,
    recompute,
    serviceYear: debouncedServiceDate ? new Date(debouncedServiceDate).getFullYear() : year || new Date().getFullYear(),
    serviceDate: debouncedServiceDate,
    currentClaimId: editingClaimId,
    fullCoverage,
    onCoverageError: (message) => enqueueSnackbar(message, { variant: 'warning' })
  });

  const refetchAllLinesCoverageCallback = useCallback(
    async (newEncounterType, newFullCoverage, newClaimContextCode) => {
      const updated = await refetchAllLinesCoverage(newEncounterType, linesRef.current, newFullCoverage, newClaimContextCode);
      if (updated) setLines(updated);
      return updated;
    },
    [refetchAllLinesCoverage]
  );

  // ✅ FIX: Ref that always points to the LATEST refetchAllLinesCoverageCallback
  // This prevents stale-closure bugs in setTimeout calls
  const refetchCoverageOnEditRef = useRef(refetchAllLinesCoverageCallback);
  useEffect(() => {
    refetchCoverageOnEditRef.current = refetchAllLinesCoverageCallback;
  }, [refetchAllLinesCoverageCallback]);

  const isSavingRef = useRef(false);

  // Custom Service Addition States
  const [customServiceDialogOpen, setCustomServiceDialogOpen] = useState(false);
  const [activeLineIdForCustomService, setActiveLineIdForCustomService] = useState(null);
  const [customServiceData, setCustomServiceData] = useState({
    categoryId: '',
    serviceName: '',
    serviceCode: '',
    contractPrice: ''
  });
  const [customServiceError, setCustomServiceError] = useState(null);
  const [addingCustomService, setAddingCustomService] = useState(false);

  const handleCloseCustomServiceDialog = () => {
    setCustomServiceDialogOpen(false);
    setActiveLineIdForCustomService(null);
  };

  const handleCustomServiceDataChange = (field, value) => {
    setCustomServiceData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmitCustomService = async () => {
    setCustomServiceError(null);

    // Validation
    if (!customServiceData.categoryId) {
      setCustomServiceError('يرجى اختيار التصنيف الطبي الموحد');
      return;
    }
    if (!customServiceData.serviceName.trim()) {
      setCustomServiceError('يرجى إدخال اسم الخدمة');
      return;
    }
    if (!entryContext?.contractId) {
      setCustomServiceError('لا يوجد عقد مقدم خدمة فعّال لإضافة هذه الخدمة إليه بتاريخ المطالبة');
      return;
    }
    const priceNum = customServiceData.contractPrice === '' ? 0 : parseFloat(customServiceData.contractPrice);
    if (customServiceData.contractPrice !== '' && (isNaN(priceNum) || priceNum <= 0)) {
      setCustomServiceError('إذا أدخلت سعراً، يجب أن يكون سعر وحدة صحيحاً أكبر من صفر');
      return;
    }

    setAddingCustomService(true);
    try {
      const finalCategoryId = customServiceData.categoryId;

      // Auto-generate service code if not provided
      const finalServiceCode = customServiceData.serviceCode.trim() || `SYS-CLAIM-${Date.now().toString().slice(-8)}`;

      const payload = {
        serviceCode: finalServiceCode,
        serviceName: customServiceData.serviceName.trim(),
        medicalCategoryId: Number(finalCategoryId),
        pricingMode: 'CLAIM_UNIT_PRICE',
        basePrice: 0,
        contractPrice: 0,
        maxContractPrice: null,
        effectiveFrom: serviceDate || undefined,
        notes: `أضيفت من شاشة إدخال المطالبات بسعر مفتوح لكل مطالبة. السياق الافتراضي عند الإضافة: ${claimContextCode || encounterType || 'OUTPATIENT'}`
      };

      // Add the service to the active provider contract, but with an open
      // per-claim unit price. It will appear for this provider later, yet the
      // clerk may enter 100 in one claim and 250 in another without creating
      // a price-excess refusal.
      const response = await axiosClient.post(`/provider-contracts/${entryContext.contractId}/pricing`, payload);
      const createdService = response.data?.data || response.data;

      const newServiceObject = {
        id: createdService.id,
        medicalServiceId: null,
        pricingItemId: createdService.id,
        pricingMode: 'CLAIM_UNIT_PRICE',
        directPrice: false,
        serviceCode: finalServiceCode,
        serviceName: payload.serviceName,
        categoryId: Number(finalCategoryId),
        serviceCategoryId: Number(finalCategoryId),
        medicalCategoryId: Number(finalCategoryId),
        categoryName: createdService.categoryName || '',
        serviceCategoryName: createdService.categoryName || '',
        medicalCategoryName: createdService.categoryName || '',
        claimContextCode: claimContextCode || encounterType || 'OUTPATIENT',
        label: buildServiceDisplayLabel({ code: finalServiceCode, name: payload.serviceName }),
        contractPrice: 0,
        maxContractPrice: 0,
        price: priceNum
      };

      // Invalidate the service search this screen actually queries. The old key
      // here ('contracted-services') matched nothing this file ever fetches with
      // -- the real one is 'claim-entry-contract-services' -- so a service just
      // added would not appear if the clerk searched again afterward, even once
      // the endpoint above was reachable.
      queryClient.invalidateQueries({ queryKey: ['claim-entry-contract-services'] });

      // Update the active claim line through the same path used by the normal
      // service picker. This keeps manual-amount services, category metadata
      // and backend coverage refresh in one code path.
      if (activeLineIdForCustomService) {
        const currentLines = linesRef.current || lines;
        const selectedLineIndex = currentLines.findIndex((line) => String(line.id) === String(activeLineIdForCustomService));
        if (selectedLineIndex < 0) {
          throw new Error('تعذر تحديد السطر المطلوب. أعد اختيار الخدمة في السطر نفسه.');
        }
        await handleServiceChange(selectedLineIndex, newServiceObject);
      }

      setCustomServiceDialogOpen(false);
      enqueueSnackbar('تمت إضافة الخدمة وتحديدها بنجاح', { variant: 'success' });
    } catch (err) {
      console.error('Failed to add custom service pricing:', err);
      const apiMessage =
        err?.response?.data?.messageAr || err?.response?.data?.message || err?.response?.data?.error || err?.userMessage || err?.message;
      setCustomServiceError(apiMessage || 'فشل في حفظ الخدمة العامة الجديدة. تأكد من صحة البيانات.');
    } finally {
      setAddingCustomService(false);
    }
  };

  // ── الاستعلامات ──────────────────────────────────────────────────────────
  const { data: provider } = useQuery({
    queryKey: ['provider', providerId],
    queryFn: () => providersService.getById(providerId),
    enabled: !!providerId
  });
  const {
    data: entryContext,
    isFetching: loadingEntryContext,
    isError: entryContextError,
    error: entryContextFailure
  } = useQuery({
    queryKey: ['claim-entry-context', member?.id, providerId, employerId, debouncedServiceDate],
    queryFn: ({ signal }) =>
      claimsService.getEntryContext({
        memberId: member.id,
        providerId,
        employerId,
        serviceDate: debouncedServiceDate,
        signal
      }),
    enabled: !!member?.id && !!providerId && !!employerId && !!debouncedServiceDate,
    retry: false,
    staleTime: 30000
  });
  const {
    data: currentBatch,
    isLoading: loadingBatchMeta,
    error: batchError
  } = useQuery({
    queryKey: ['claim-batch-current', providerId, employerId, month, year],
    // FIX: Read-only GET — does NOT auto-create a batch on page load
    queryFn: () => claimBatchesService.getCurrentBatch(providerId, employerId, year, month),
    enabled: !!providerId && !!employerId && !isNaN(month) && !isNaN(year),
    retry: false
  });

  useEffect(() => {
    if (currentBatch?.id) {
      setDraftBatchId(currentBatch.id);
    }
  }, [currentBatch]);

  const {
    data: contractedRaw,
    isLoading: loadingServices,
    isError: servicesError,
    error: servicesFailure,
    refetch: refetchServices
  } = useQuery({
    queryKey: [
      'claim-entry-contract-services',
      member?.id,
      providerId,
      employerId,
      entryContext?.contractId,
      debouncedServiceDate,
      debouncedServiceSearch
    ],
    queryFn: () =>
      claimsService.getEntryServices({
        memberId: member.id,
        providerId,
        employerId,
        serviceDate: debouncedServiceDate,
        q: debouncedServiceSearch || undefined,
        size: 100,
        sort: 'serviceName,asc'
      }),
    enabled:
      !!member?.id &&
      !!providerId &&
      !!employerId &&
      !!entryContext?.contractId &&
      !!debouncedServiceDate &&
      debouncedServiceDate === serviceDate &&
      entryContext?.serviceDate === serviceDate,
    retry: false
  });
  const normalizedMemberSearchValue = useMemo(() => debouncedMemberInput.trim(), [debouncedMemberInput]);

  // Search logic is handled automatically by the backend UnifiedSearchService

  const {
    data: memberResults,
    isFetching: searchingMember,
    isError: memberSearchError,
    error: memberSearchQueryError,
    refetch: retryMemberSearch
  } = useQuery({
    queryKey: ['member-search', normalizedMemberSearchValue, employerId],
    queryFn: () => runWithRetry(() => unifiedMembersService.unifiedSearch(normalizedMemberSearchValue, employerId), { maxRetries: 1 }),
    enabled: true, // No character restriction
    staleTime: 10000
  });

  useEffect(() => {
    if (!memberSearchError || !memberSearchQueryError) {
      return;
    }

    const normalized = normalizeApiError(memberSearchQueryError);
    enqueueSnackbar(normalized.message || 'فشل تحميل نتائج البحث', { variant: 'error' });
  }, [memberSearchError, memberSearchQueryError, enqueueSnackbar]);

  useEffect(() => {
    if (!entryContextError || !entryContextFailure || !member?.id || !debouncedServiceDate || debouncedServiceDate !== serviceDate) return;
    const normalized = normalizeApiError(entryContextFailure);
    const shortId = normalized.trackingId ? String(normalized.trackingId).split('-')[0] : null;
    const message =
      normalized.code === 'MEMBER_NOT_COVERED_AT_SERVICE_DATE'
        ? `المستفيد غير مغطى تأمينياً بتاريخ ${dayjs(debouncedServiceDate).format('DD/MM/YYYY')}.`
        : normalized.message || 'تعذر التحقق من تغطية المستفيد في تاريخ الخدمة المحدد.';
    const toastKey = `${normalized.code}:${member.id}:${debouncedServiceDate}`;
    enqueueSnackbar(shortId ? `${message} (مرجع: ${shortId})` : message, {
      key: toastKey,
      preventDuplicate: true,
      variant: 'error',
      autoHideDuration: 6000
    });
  }, [debouncedServiceDate, enqueueSnackbar, entryContextError, entryContextFailure, member?.id, serviceDate]);

  const entryContextBlockReason = useMemo(() => {
    if (!member?.id) return null;
    if (!serviceDate) return 'اختر تاريخ الخدمة أولاً حتى نتحقق من وثيقة المستفيد وعقد مقدم الخدمة والسقف.';
    if (serviceDate !== debouncedServiceDate) return 'انتظر لحظة حتى يكتمل إدخال تاريخ الخدمة.';
    if (loadingEntryContext) return 'انتظر اكتمال التحقق من الوثيقة والعقد والسقف لهذا التاريخ.';
    if (entryContextError) {
      const normalized = normalizeApiError(entryContextFailure);
      return normalized.message || 'تعذر التحقق من الوثيقة أو العقد أو السقف. اضغط إعادة التحقق.';
    }
    if (!entryContext || entryContext.serviceDate !== serviceDate) return 'لم تكتمل نتيجة التحقق لهذا التاريخ بعد.';
    return null;
  }, [debouncedServiceDate, entryContext, entryContextError, entryContextFailure, loadingEntryContext, member?.id, serviceDate]);

  const coveragePending = useMemo(
    () => lines.some((line) => (line.service || line.serviceName) && !line.rejected && line.coveragePending),
    [lines]
  );

  // Eligible pre-authorizations are part of the same dated context response.
  // A separate request used to resolve the member, policy, contract and
  // balance a second time whenever member/date changed.
  const preAuthResults = entryContext?.eligiblePreAuthorizations || [];
  const searchingPreAuth = loadingEntryContext;

  // ── Helper to refresh all batch related views ───────────────────────────
  const invalidateBatchData = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['batch-claims-entry'] });
    queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
    queryClient.invalidateQueries({ queryKey: ['batch-stats'] });
    queryClient.invalidateQueries({ queryKey: ['batch-global-stats'] });
    queryClient.invalidateQueries({ queryKey: ['claim-batch-current'] });
    queryClient.invalidateQueries({ queryKey: ['member-financial-summary'] });
    // Invalidate cached claim detail so re-opening a claim always triggers a fresh
    // coverage/usage fetch (ensures سقف المنفعة reflects the latest consumed amounts)
    queryClient.invalidateQueries({ queryKey: ['claim'] });
    // Invalidate provider account queries so الدفعات المالية reflects the reversal
    // that fires synchronously on the backend after claim soft-delete
    queryClient.invalidateQueries({ queryKey: ['provider-accounts-list'] });
    queryClient.invalidateQueries({ queryKey: ['provider-account'] });
    queryClient.invalidateQueries({ queryKey: ['settlement-claims-summary'] });
    queryClient.invalidateQueries({ queryKey: ['settlement-claims'] });
  }, [queryClient]);

  // The dated context is the only source for the policy and contract shown by
  // this screen. Employer-current/provider-current lookups are not equivalent
  // to the member context on a historical service date.
  useEffect(() => {
    if (!entryContext?.policyId) {
      setPolicyId(null);
      setPolicyInfo(null);
      return;
    }
    setPolicyId(entryContext.policyId);
    setPolicyInfo({
      id: entryContext.policyId,
      policyCode: entryContext.policyCode,
      name: entryContext.policyName,
      status: entryContext.policyStatus,
      startDate: entryContext.policyStartDate,
      endDate: entryContext.policyEndDate
    });
  }, [entryContext]);

  // The claim header must use the same dated policy/balance snapshot as the
  // contract and its prices. A separate "current summary" request would mix
  // today's balance into a historical claim and add an unnecessary request.
  const financialSummary = entryContext
    ? {
        annualLimit: entryContext.annualLimit,
        limitConsumedAmount: entryContext.committedAmount,
        reservedAmount: entryContext.reservedAmount,
        actualRemaining: entryContext.actualRemaining,
        reservableAvailable: entryContext.reservableAvailable,
        asOfDate: entryContext.serviceDate,
        readAt: entryContext.balanceReadAt,
        ceilingMode: entryContext.ceilingMode
      }
    : null;

  const contractedServiceOptionsRaw = useMemo(() => {
    const items = Array.isArray(contractedRaw) ? contractedRaw : contractedRaw?.content || contractedRaw?.items || [];
    return items.map((s) => {
      const code = s.serviceCode || s.code || '';
      const name = s.serviceName || s.name || '';
      const normalizedCategoryId =
        s.categoryId ?? s.serviceCategoryId ?? s.medicalCategoryId ?? s.medicalCategory?.id ?? s.effectiveCategory?.id ?? null;
      const normalizedCategoryName =
        s.categoryName ??
        s.serviceCategoryName ??
        s.medicalCategoryName ??
        s.medicalCategory?.nameAr ??
        s.medicalCategory?.name ??
        s.effectiveCategory?.nameAr ??
        s.effectiveCategory?.name ??
        null;
      const normalizedEncounterType = getServiceContext(s);
      const isManualAmount = s.pricingMode === 'MANUAL_AMOUNT';
      return {
        ...s,
        label: buildServiceDisplayLabel({ code, name }),
        serviceName: name,
        serviceCode: code,
        encounterType: normalizedEncounterType,
        defaultEncounterType: normalizedEncounterType,
        categoryId: normalizedCategoryId,
        serviceCategoryId: normalizedCategoryId,
        medicalCategoryId: normalizedCategoryId,
        categoryName: normalizedCategoryName,
        serviceCategoryName: normalizedCategoryName,
        medicalCategoryName: normalizedCategoryName,
        medicalServiceId: s.medicalServiceId ?? s.serviceId ?? (isManualAmount ? s.id : null),
        pricingItemId: isManualAmount ? null : (s.pricingItemId ?? s.id),
        contractPrice: s.contractPrice || 0,
        maxContractPrice: s.maxContractPrice || s.contractPrice || 0
      };
    });
  }, [contractedRaw]);

  const serviceOptions = useMemo(() => {
    // Claim entry is contract-priced and fail-closed. Synthetic generic items
    // have neither a pricingItemId nor a classified coverage source and mask a
    // failed contract-services request as if the contract contained services.
    //
    // السياق المالي يُطبّق على المطالبة كاملة، لا على قائمة الخدمات.
    // Do not filter services by claim context here. A service classification
    // belongs to the provider contract/catalog; the selected claim context
    // belongs to the whole claim and is validated by the coverage rule engine.
    return contractedServiceOptionsRaw;
  }, [contractedServiceOptionsRaw]);

  const noEffectiveContractServicesForDate =
    Boolean(entryContext?.contractId) &&
    Boolean(serviceDate) &&
    !loadingServices &&
    !servicesError &&
    !debouncedServiceSearch.trim() &&
    contractedServiceOptionsRaw.length === 0;

  // ── Load Existing Claim for Edit ───────────────────────────────────────
  const { data: editingClaim } = useQuery({
    queryKey: ['claim', editingClaimId],
    queryFn: () => claimsService.getById(editingClaimId),
    enabled: !!editingClaimId,
    staleTime: 0
  });

  useEffect(() => {
    if (editingClaim) {
      setEditCoverageLoading(true);
      setMember({ id: editingClaim.memberId, fullName: editingClaim.memberName, cardNumber: editingClaim.memberNationalNumber });
      setDiagnosis(editingClaim.diagnosisDescription || editingClaim.diagnosisCode || '');
      setDoctorName(editingClaim.doctorName || '');
      setComplaint(editingClaim.complaint || '');
      setIsClaimRejected(editingClaim.status === 'REJECTED');
      setRejectionInput(editingClaim.reviewerComment || '');

      setLines(
        editingClaim.lines.map((l) => {
          // المطابقة: 1) pricingItemId (الأدق)
          //             2) serviceCode أو medicalServiceCode كاحتياط
          const lineCode = l.medicalServiceCode || l.serviceCode;
          const lineName = l.medicalServiceName || l.serviceName;
          const svc = serviceOptions.find(
            (s) =>
              (s.pricingItemId != null && l.pricingItemId != null && s.pricingItemId === l.pricingItemId) ||
              (s.serviceCode && lineCode && s.serviceCode === lineCode)
          );
          // سعر العقد الحي من بيانات العقد — 65 بدلاً من 70 المدخل
          const cp = svc ? svc.contractPrice || 0 : 0;
          const maxCp = svc ? svc.maxContractPrice || cp : l.maxContractPrice || l.contractPrice || cp || 0;

          // السعر المُدخل = requestedUnitPrice إذا متوفر، وإلا unitPrice
          const enteredPrice = l.requestedUnitPrice != null ? parseFloat(l.requestedUnitPrice) || 0 : parseFloat(l.unitPrice) || 0;

          const serviceObj = svc || {
            pricingItemId: l.pricingItemId || null,
            medicalServiceId: l.medicalServiceId || null,
            serviceCode: lineCode,
            serviceName: lineName,
            categoryId: l.appliedCategoryId ?? l.serviceCategoryId ?? null,
            serviceCategoryId: l.appliedCategoryId ?? l.serviceCategoryId ?? null,
            serviceCategoryName: l.appliedCategoryName ?? l.serviceCategoryName ?? null,
            label: buildServiceDisplayLabel({ code: lineCode, name: lineName || '' }),
            contractPrice: cp,
            maxContractPrice: maxCp
          };
          const line = {
            id:
              l.id ||
              (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15)),
            service: serviceObj,
            medicalServiceId: l.medicalServiceId || serviceObj.medicalServiceId || null,
            pricingItemId: l.pricingItemId || serviceObj.pricingItemId || null,
            // Re-derived from the live standard-service list, not from the
            // saved line: a service's pricing mode is a catalog property,
            // and l.amountSource (fixed at save time) is what the
            // already-settled financial split reflects, not what a further
            // edit should be validated against.
            pricingMode: serviceObj.pricingMode === 'MANUAL_AMOUNT' ? 'MANUAL_AMOUNT' : 'CONTRACT_PRICE',
            serviceName: lineName || serviceObj.serviceName || '',
            serviceCode: lineCode || serviceObj.serviceCode || '',
            serviceCategoryId: l.appliedCategoryId ?? l.serviceCategoryId ?? serviceObj.serviceCategoryId ?? null,
            serviceCategoryName: l.appliedCategoryName ?? l.serviceCategoryName ?? serviceObj.serviceCategoryName ?? null,
            quantity: l.quantity ?? l.requestedQuantity ?? l.approvedQuantity ?? 1,
            unitPrice: enteredPrice,
            contractPrice: maxCp,
            maxContractPrice: maxCp,
            coveragePercent: l.coveragePercent,
            usageDetails:
              Number(l.benefitLimit) > 0 || Number(l.timesLimit) > 0
                ? {
                    amountLimit: Number(l.benefitLimit) > 0 ? Number(l.benefitLimit) : null,
                    timesLimit: Number(l.timesLimit) > 0 ? Number(l.timesLimit) : null,
                    usedAmount: Number(l.usedAmount || 0),
                    remainingAmount: l.remainingAmount != null ? Number(l.remainingAmount) : null,
                    exceeded: false
                  }
                : null,
            rejected: l.rejected,
            rejectionReason: l.rejectionReason,
            manualRefusedAmount: parseFloat(l.manualRefusedAmount) || 0,
            oldRejected: l.rejected ? 1 : 0
          };
          return recompute(line);
        })
      );
      setServiceDate(editingClaim.serviceDate || defaultDate);
      setPreAuthId(editingClaim.preAuthorizationId || '');
      setEncounterType(editingClaim.encounterType || 'OUTPATIENT');
      setFullCoverage(!!editingClaim.fullCoverage);
      setClaimContextCode(
        editingClaim.claimContextCode || (editingClaim.fullCoverage ? 'FULL_COVERAGE' : editingClaim.encounterType || 'OUTPATIENT')
      );
      setBeneficiaryPaidAmount(
        editingClaim.beneficiaryPaidAmount != null || editingClaim.patientPaidAmount != null
          ? String(editingClaim.beneficiaryPaidAmount ?? editingClaim.patientPaidAmount)
          : ''
      );
      setIsDirty(false);
      // Signal that edit fields and lines were committed. One dedicated effect
      // recalculates coverage after policy/member are ready as well.
      setEditHydrationVersion((version) => version + 1);
    }
  }, [editingClaim, defaultDate, recompute, serviceOptions]);

  const draftPayload = useMemo(
    () => ({
      member,
      diagnosis,
      doctorName,
      complaint,
      notes,
      lines,
      beneficiaryPaidAmount,
      serviceDate,
      preAuthId,
      encounterType,
      claimContextCode,
      fullCoverage,
      applyBenefits,
      isClaimRejected,
      rejectionInput,
      directEntryKey
    }),
    [
      member,
      diagnosis,
      doctorName,
      complaint,
      notes,
      lines,
      beneficiaryPaidAmount,
      serviceDate,
      preAuthId,
      encounterType,
      claimContextCode,
      fullCoverage,
      applyBenefits,
      isClaimRejected,
      rejectionInput,
      directEntryKey
    ]
  );

  const applyRecoveredDraft = useCallback(
    (payload) => {
      if (!payload) return;
      setMember(payload.member || null);
      setDiagnosis(payload.diagnosis || '');
      setDoctorName(payload.doctorName || '');
      setComplaint(payload.complaint || '');
      setNotes(payload.notes || '');
      setLines(Array.isArray(payload.lines) && payload.lines.length ? payload.lines : [newLine()]);
      setBeneficiaryPaidAmount(payload.beneficiaryPaidAmount || '');
      setServiceDate(payload.serviceDate || defaultDate);
      setPreAuthId(payload.preAuthId || '');
      setEncounterType(payload.encounterType || 'OUTPATIENT');
      setFullCoverage(!!payload.fullCoverage);
      setClaimContextCode(payload.claimContextCode || (payload.fullCoverage ? 'FULL_COVERAGE' : payload.encounterType || 'OUTPATIENT'));
      setApplyBenefits(payload.applyBenefits ?? true);
      setIsClaimRejected(!!payload.isClaimRejected);
      setRejectionInput(payload.rejectionInput || '');
      setDirectEntryKey(payload.directEntryKey || newDirectEntryKey());
      setIsDirty(true);
    },
    [defaultDate]
  );

  useEffect(() => {
    if (editingClaimId) return;
    if (skipAutosaveRef.current) return;
    if (!hasMeaningfulDraftData(draftPayload)) return;
    try {
      localStorage.setItem(
        draftStorageKey,
        JSON.stringify({
          updatedAt: new Date().toISOString(),
          data: draftPayload
        })
      );
    } catch (error) {
      console.warn('Failed to write local draft backup', error);
    }
  }, [draftPayload, draftStorageKey, editingClaimId]);

  useEffect(() => {
    if (editingClaimId) return;
    if (skipAutosaveRef.current) return;
    if (!hasMeaningfulDraftData(draftPayload)) return;
    if (!providerId || !employerId || !month || !year) return;

    if (autosaveTimerRef.current) {
      clearTimeout(autosaveTimerRef.current);
    }

    autosaveTimerRef.current = setTimeout(() => {
      saveQueueRef.current = saveQueueRef.current.then(async () => {
        try {
          setAutoSaveStatus('saving');

          let resolvedBatchId = draftBatchId;
          if (!resolvedBatchId) {
            const batch = await claimBatchesService.openOrGetBatch(providerId, employerId, year, month);
            resolvedBatchId = batch?.id;
            if (resolvedBatchId) {
              setDraftBatchId(resolvedBatchId);
              queryClient.setQueryData(['claim-batch-current', providerId, employerId, month, year], batch);
            }
          }

          if (!resolvedBatchId) {
            setAutoSaveStatus('error');
            return;
          }

          const saved = await claimsService.saveDraft({
            batchId: resolvedBatchId,
            data: draftPayload,
            version: draftVersion
          });

          setDraftVersion(saved?.version ?? null);
          // Draft autosave is intentionally quiet. Repeated "conflict resolved"
          // snackbars trained users to distrust the form even when no action was
          // required; the compact autosave dot is enough for normal background
          // synchronization.
          setAutoSaveStatus('saved');
        } catch {
          if (typeof navigator !== 'undefined' && navigator.onLine === false) {
            setAutoSaveStatus('offline');
          } else {
            setAutoSaveStatus('error');
          }
        }
      });
    }, 700);

    return () => {
      if (autosaveTimerRef.current) clearTimeout(autosaveTimerRef.current);
    };
  }, [draftPayload, draftBatchId, draftVersion, editingClaimId, providerId, employerId, year, month, queryClient, enqueueSnackbar]);

  useEffect(() => {
    if (editingClaimId) return;
    if (loadingBatchMeta) return;
    if (recoveryCheckedRef.current) return;
    if (recoveryDismissedRef.current) return;

    recoveryCheckedRef.current = true;

    const runRecoveryCheck = async () => {
      let localDraft = null;
      try {
        const raw = localStorage.getItem(draftStorageKey);
        localDraft = raw ? JSON.parse(raw) : null;
      } catch {
        localDraft = null;
      }

      let serverDraft = null;
      try {
        if (draftBatchId) {
          serverDraft = await claimsService.getDraft(draftBatchId);
        }
      } catch {
        serverDraft = null;
      }

      const hasServer = !!serverDraft?.data;
      const hasLocal = !!localDraft?.data;
      if (hasServer || hasLocal) {
        setRecoveryDialog({ open: true, serverDraft, localDraft });
      }
    };

    runRecoveryCheck();
  }, [editingClaimId, loadingBatchMeta, draftStorageKey, draftBatchId]);

  const memberOptions = useMemo(() => {
    const c = Array.isArray(memberResults) ? memberResults : (memberResults?.data?.content ?? memberResults?.content);
    const list = Array.isArray(c) ? c : [];
    // Always include the currently selected member (for edit mode where no search is active)
    if (member?.id && !list.find((m) => m.id === member.id)) {
      return [member, ...list];
    }
    return list;
  }, [memberResults, member]);

  // ── المنطق المالي وتغطية الخدمات (مطبق في الأعلى) ───────────────────────────

  // Debounce ref for quantity/price changes triggering backend coverage re-fetch
  const coverageRefetchTimerRef = useRef(null);

  const updateLine = useCallback(
    (idx, patch) => {
      const affectsCoverage =
        patch.coveragePending !== false &&
        [
          'quantity',
          'unitPrice',
          'rejected',
          'manualRefusedAmount',
          'medicalCategoryId',
          'serviceCategoryId',
          'categoryId',
          'medicalCategoryName',
          'serviceCategoryName'
        ].some((key) => key in patch);
      setLines((prev) => {
        const n = [...prev];
        n[idx] = {
          ...n[idx],
          ...patch,
          ...(affectsCoverage ? { coveragePending: true } : {})
        };
        return n.map((line, i) => recompute(line, i, n));
      });
      setIsDirty(true);

      // Re-fetch coverage from backend when quantity or price changes (affects usageDetails)
      const needsBackendRefresh = affectsCoverage;
      if (needsBackendRefresh && policyId && member?.id) {
        if (coverageRefetchTimerRef.current) clearTimeout(coverageRefetchTimerRef.current);
        coverageRefetchTimerRef.current = setTimeout(() => {
          refetchAllLinesCoverage(encounterType, linesRef.current, fullCoverage, claimContextCode).then((updated) => {
            if (updated) setLines(updated);
          });
        }, 600);
      }
    },
    [recompute, policyId, member?.id, refetchAllLinesCoverage, encounterType, fullCoverage, claimContextCode]
  );

  const handleServiceChange = useCallback(
    async (idx, val, options = {}) => {
      if (!val) {
        updateLine(idx, { service: null, serviceName: '', serviceCode: '', unitPrice: 0, contractPrice: 0, maxContractPrice: 0 });
        return;
      }

      let svc = val;
      let isFreeText = false;
      if (typeof val === 'string') {
        svc = { serviceName: val, label: val, mapped: false, isFreeText: true };
        isFreeText = true;
      }

      const newName = svc.serviceName || svc.name;

      const code = svc?.serviceCode || svc?.code;
      const isGeneralService = code === 'GEN-MEDICATION' || code === 'GEN-MEDICAL-SERVICE';

      const currentLines = linesRef.current || lines;

      const isDuplicate =
        !isGeneralService &&
        currentLines.some((l, i) => {
          if (i === idx) return false;
          const existingName = l.serviceName || l.service?.serviceName || l.service?.name;
          return newName && existingName && existingName === newName;
        });

      if (isDuplicate) {
        enqueueSnackbar('هذه الخدمة مضافة بالفعل في بند آخر', { variant: 'error' });
        return;
      }

      const currentLine = currentLines[idx] || {};
      const price = svc?.contractPrice ?? 0;
      const maxPrice = svc?.maxContractPrice ?? price;
      const resolvedCategoryId =
        svc.categoryId ?? svc.serviceCategoryId ?? svc.medicalCategoryId ?? svc.medicalCategory?.id ?? svc.effectiveCategory?.id ?? null;
      const resolvedCategoryName =
        svc.categoryName ??
        svc.serviceCategoryName ??
        svc.medicalCategoryName ??
        svc.medicalCategory?.nameAr ??
        svc.medicalCategory?.name ??
        svc.effectiveCategory?.nameAr ??
        svc.effectiveCategory?.name ??
        null;

      // Pharmacy/optics-style services: no contract price list exists at
      // all, the clerk enters the invoice amount directly. quantity is
      // fixed at 1 -- an invoice is one line, not N units of a unit price --
      // and the contract-price bounds (price/maxPrice) stay 0 so the
      // per-row bounds-check tooltip never fires for a price that was never
      // a contract price to begin with.
      const isManualAmount = svc.pricingMode === 'MANUAL_AMOUNT';
      const isClaimUnitPrice = svc.pricingMode === 'CLAIM_UNIT_PRICE';
      const manualAmount = Number(options.manualAmount);
      const hasManualAmountOverride = Number.isFinite(manualAmount) && manualAmount > 0;

      const nextPatch = {
        service: svc,
        medicalServiceId: svc.medicalServiceId || null,
        pricingItemId: isManualAmount ? null : svc.pricingItemId || null,
        pricingMode: isManualAmount ? 'MANUAL_AMOUNT' : isClaimUnitPrice ? 'CLAIM_UNIT_PRICE' : 'CONTRACT_PRICE',
        serviceName: svc.serviceName || (typeof val === 'string' ? val : ''),
        serviceCode: svc.serviceCode || '',
        medicalCategoryId: resolvedCategoryId,
        medicalCategoryName: resolvedCategoryName,
        serviceCategoryId: resolvedCategoryId,
        serviceCategoryName: resolvedCategoryName,
        quantity: isManualAmount ? 1 : currentLine.quantity || 1,
        unitPrice: isManualAmount ? (hasManualAmountOverride ? manualAmount : currentLine.unitPrice || 0) : isClaimUnitPrice ? (svc.price || currentLine.unitPrice || 0) : price,
        contractPrice: isManualAmount || isClaimUnitPrice ? 0 : maxPrice,
        maxContractPrice: isManualAmount || isClaimUnitPrice ? 0 : maxPrice,
        ...(isFreeText ? failedCoverageResult('الخدمة النصية غير مرتبطة بخدمة معتمدة ولا يمكن احتساب تغطيتها') : { coveragePending: true })
      };

      const nextLines = currentLines.map((line, lineIdx) => (lineIdx === idx ? { ...currentLine, ...nextPatch } : line));
      setLines(nextLines.map((line, lineIdx) => recompute(line, lineIdx, nextLines)));
      setIsDirty(true);

      if (!isFreeText && policyId && member?.id) {
        // Selecting a service changes the same row that the clerk can already
        // have edited (quantity/price). Ask the backend from the committed
        // draft state that is now visible on screen, not from a transitional
        // Autocomplete event snapshot. Otherwise a service-only change can
        // calculate limits for quantity=1 while the row displays quantity=15.
        await new Promise((resolve) => setTimeout(resolve, 0));
        const updated = await refetchAllLinesCoverage(encounterType, nextLines, fullCoverage, claimContextCode);
        if (updated) setLines(updated);
      }
    },
    [
      updateLine,
      lines,
      enqueueSnackbar,
      encounterType,
      claimContextCode,
      policyId,
      member?.id,
      refetchAllLinesCoverage,
      fullCoverage,
      recompute
    ]
  );

  useEffect(() => {
    if (!policyId || !member?.id) return;
    if (editingClaimId) return;

    // Force refetch usage/limits for ALL lines when member or policy changes
    refetchAllLinesCoverage(encounterType, linesRef.current, fullCoverage, claimContextCode).then((updated) => {
      if (updated) setLines(updated);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [policyId, member?.id, encounterType, fullCoverage, claimContextCode]);

  // Edit hydration barrier: run only after claim, policy, member, context, date
  // and mapped lines have all reached committed React state.
  useEffect(() => {
    if (!editingClaimId || !editHydrationVersion || !policyId || !member?.id) return;
    if (!linesRef.current.some((line) => line.service)) return;

    let active = true;
    setEditCoverageLoading(true);
    Promise.resolve(refetchCoverageOnEditRef.current(encounterType, fullCoverage, claimContextCode)).finally(() => {
      if (active) setEditCoverageLoading(false);
    });
    return () => {
      active = false;
    };
  }, [editingClaimId, editHydrationVersion, policyId, member?.id, encounterType, serviceDate, fullCoverage, claimContextCode]);

  const addLine = useCallback(() => {
    setLines((p) => [...p, newLine()]);
    setIsDirty(true);
  }, []);
  const removeLine = useCallback(
    (idx) => {
      const targetLine = lines[idx];
      const serviceLabel = targetLine?.serviceName || targetLine?.service?.serviceName || targetLine?.serviceCode || `البند رقم ${idx + 1}`;
      triggerConfirm('تأكيد حذف البند', `هل تريد حذف بند الخدمة «${serviceLabel}»؟ سيتم إخراجه من حساب المطالبة.`, () => {
        setLines((p) => (p.length === 1 ? [newLine()] : p.filter((_, i) => i !== idx)));
        setIsDirty(true);
      });
    },
    [lines]
  );

  const resolveLineCategoryId = useCallback((line) => {
    return (
      line?.appliedCategoryId ??
      line?.serviceCategoryId ??
      line?.medicalCategoryId ??
      line?.categoryId ??
      line?.service?.serviceCategoryId ??
      line?.service?.categoryId ??
      line?.service?.medicalCategoryId ??
      line?.service?.medicalCategory?.id ??
      line?.service?.effectiveCategory?.id ??
      null
    );
  }, []);

  const resolveCategoryLabel = useCallback(
    (categoryId) => {
      const category = medicalCategories.find((entry) => String(entry.id) === String(categoryId));
      if (!category) return null;
      return {
        id: category.id,
        code: category.code || '',
        name: category.nameAr || category.name || category.nameEn || ''
      };
    },
    [medicalCategories]
  );

  const openClassificationReviewDialog = useCallback(
    (idx) => {
      const line = lines[idx];
      const currentCategoryId = resolveLineCategoryId(line);
      setClassificationReview({
        open: true,
        lineIndex: idx,
        selectedCategoryId: currentCategoryId ? String(currentCategoryId) : ''
      });
    },
    [lines, resolveLineCategoryId]
  );

  const closeClassificationReviewDialog = useCallback(() => {
    setClassificationReview({ open: false, lineIndex: null, selectedCategoryId: '' });
  }, []);

  const sendLineToMedicalDictionary = useCallback(
    async (idx, categoryIdOverride = null) => {
      const line = lines[idx];
      const serviceName = (line?.serviceName || line?.service?.serviceName || line?.service?.name || '').trim();
      const categoryId = categoryIdOverride || resolveLineCategoryId(line);

      if (!serviceName) {
        enqueueSnackbar('لا يمكن إرسال بند بلا اسم خدمة إلى القاموس الطبي', { variant: 'warning' });
        return;
      }

      if (!categoryId) {
        enqueueSnackbar('لا يمكن إرسال البند للقاموس قبل توفر تصنيف طبي مقترح', { variant: 'warning' });
        return;
      }

      try {
        await medicalDictionaryService.createDictionarySuggestion({
          originalText: serviceName,
          suggestedCategoryId: Number(categoryId),
          source: 'CLAIM_REVIEW',
          confidence: 90,
          sourceReference: `claim:${editingClaimId || 'draft'};line:${idx + 1}`
        });

        enqueueSnackbar('تم إرسال التصنيف المعدّل إلى سجل مراجعة القاموس للاعتماد الدائم لاحقاً', { variant: 'success' });
      } catch (err) {
        enqueueSnackbar(err?.response?.data?.message || 'تعذر إرسال البند للقاموس الطبي', { variant: 'error' });
      }
    },
    [editingClaimId, enqueueSnackbar, lines, resolveLineCategoryId]
  );

  const approveClassificationForLine = useCallback(async () => {
    const idx = classificationReview.lineIndex;
    if (idx == null || idx < 0) return;
    const selected = resolveCategoryLabel(classificationReview.selectedCategoryId);
    if (!selected?.id) {
      enqueueSnackbar('اختر التصنيف الطبي قبل الاعتماد', { variant: 'warning' });
      return;
    }

    await sendLineToMedicalDictionary(idx, selected.id);
    closeClassificationReviewDialog();
    enqueueSnackbar('تم إرسال اقتراح التصنيف للمراجعة. لن يتغير حساب هذه المطالبة حتى يُعتمد التصنيف في مصدر الخدمة.', {
      variant: 'success'
    });
  }, [
    classificationReview.lineIndex,
    classificationReview.selectedCategoryId,
    closeClassificationReviewDialog,
    enqueueSnackbar,
    resolveCategoryLabel,
    sendLineToMedicalDictionary
  ]);

  const sendClassificationToReviewQueue = useCallback(async () => {
    const idx = classificationReview.lineIndex;
    if (idx == null || idx < 0) return;
    const selectedCategoryId = classificationReview.selectedCategoryId || resolveLineCategoryId(lines[idx]);
    await sendLineToMedicalDictionary(idx, selectedCategoryId);
    closeClassificationReviewDialog();
  }, [
    classificationReview.lineIndex,
    classificationReview.selectedCategoryId,
    closeClassificationReviewDialog,
    lines,
    resolveLineCategoryId,
    sendLineToMedicalDictionary
  ]);

  const activeClassificationLine = classificationReview.lineIndex != null ? lines[classificationReview.lineIndex] : null;
  const activeClassificationCategory = resolveCategoryLabel(
    classificationReview.selectedCategoryId || resolveLineCategoryId(activeClassificationLine)
  );
  const currentClassificationCategory = resolveCategoryLabel(resolveLineCategoryId(activeClassificationLine));

  const categoriesForReview = useMemo(
    () => medicalCategories.filter(medicalCategoriesService.isCanonicalCoverageCategory),
    [medicalCategories]
  );

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, l) => ({
        total: acc.total + (parseFloat(l.total) || 0),
        company: acc.company + (parseFloat(l.byCompany) || 0),
        employee: acc.employee + (parseFloat(l.byEmployee) || 0),
        // refusedAmount دائماً يمثّل ما رُفض من حصة الشركة (سواء رفض كلي أو جزئي)
        refused: acc.refused + (parseFloat(l.refusedAmount) || 0)
      }),
      { total: 0, company: 0, employee: 0, refused: 0 }
    );
  }, [lines]);

  const beneficiarySettlement = useMemo(() => {
    const paid = Math.max(0, parseFloat(beneficiaryPaidAmount) || 0);
    const baseBeneficiaryShare = Math.max(0, totals.employee || 0);
    const refused = Math.max(0, totals.refused || 0);
    const appliedToBaseShare = Math.min(paid, baseBeneficiaryShare);
    const extraPaid = Math.max(0, paid - appliedToBaseShare);
    const appliedToRefused = Math.min(extraPaid, refused);
    const remainingBeneficiaryShare = Math.max(0, baseBeneficiaryShare - appliedToBaseShare);
    const providerRefusedBalance = Math.max(0, refused - appliedToRefused);
    const excessPayment = Math.max(0, extraPaid - appliedToRefused);

    return {
      paid,
      appliedToBaseShare,
      appliedToRefused,
      remainingBeneficiaryShare,
      providerRefusedBalance,
      excessPayment
    };
  }, [beneficiaryPaidAmount, totals.employee, totals.refused]);

  const saveDisabledReason = useMemo(() => {
    if (saving) return 'جارٍ حفظ المطالبة.';
    if (editingClaimId && editingClaim?.status && !['DRAFT', 'NEEDS_CORRECTION'].includes(editingClaim.status)) {
      return `هذه المطالبة حالتها ${editingClaim.status} ولا يمكن تعديلها من شاشة الإدخال. أرجعها للتصحيح أو أنشئ مطالبة جديدة.`;
    }
    if (!isDirty) return 'لا توجد تغييرات جديدة للحفظ.';
    if (entryContextBlockReason) return entryContextBlockReason;
    if (coveragePending) return 'انتظر اكتمال حساب التغطية والسقوف لكل البنود قبل الحفظ.';
    if (beneficiarySettlement.excessPayment > 0) {
      return 'المبلغ المدفوع من المستفيد أكبر من التزامه والمبلغ المرفوض.';
    }
    return null;
  }, [beneficiarySettlement.excessPayment, coveragePending, editingClaim?.status, editingClaimId, entryContextBlockReason, isDirty, saving]);

  const resetForm = useCallback(() => {
    setMember(null);
    setMemberInput('');
    setDiagnosis('');
    setDoctorName('');
    setComplaint('');
    setNotes('');
    setLines([newLine()]);
    setBeneficiaryPaidAmount('');
    setApplyBenefits(true);
    setIsDirty(false);
    setServiceDate(defaultDate);
    setPreAuthId('');
    setEncounterType('OUTPATIENT');
    setFullCoverage(false);
    setIsClaimRejected(false);
    setRejectionInput('');
    setDirectEntryKey(newDirectEntryKey());
    setAttachments([]);
    // FIX: resetForm must also clear the editing state
    setEditingClaimId(null);
    setEditCoverageLoading(false);
    setTimeout(() => memberRef.current?.focus(), 120);
  }, [defaultDate]);

  const restoreServerDraft = useCallback(() => {
    recoveryDismissedRef.current = true;
    const payload = recoveryDialog.serverDraft?.data;
    if (payload) {
      skipAutosaveRef.current = true;
      applyRecoveredDraft(payload);
      setTimeout(() => {
        skipAutosaveRef.current = false;
      }, 0);
    }
    setRecoveryDialog({ open: false, serverDraft: null, localDraft: null });
  }, [recoveryDialog.serverDraft, applyRecoveredDraft]);

  const restoreLocalDraft = useCallback(() => {
    recoveryDismissedRef.current = true;
    const payload = recoveryDialog.localDraft?.data;
    if (payload) {
      skipAutosaveRef.current = true;
      applyRecoveredDraft(payload);
      setTimeout(() => {
        skipAutosaveRef.current = false;
      }, 0);
    }
    setRecoveryDialog({ open: false, serverDraft: null, localDraft: null });
  }, [recoveryDialog.localDraft, applyRecoveredDraft]);

  const dismissRecovery = useCallback(async () => {
    recoveryDismissedRef.current = true;
    skipAutosaveRef.current = true;
    try {
      localStorage.removeItem(draftStorageKey);
    } catch {
      // Non-blocking local cleanup
    }
    try {
      const batchIdForDelete = draftBatchId || currentBatch?.id;
      if (batchIdForDelete) {
        await claimsService.deleteDraft(batchIdForDelete);
      }
    } catch {
      // تجاهل المسودة يجب ألا يفشل بسبب تعذر تنظيف نسخة الخادم.
    }
    setRecoveryDialog({ open: false, serverDraft: null, localDraft: null });
    setDraftVersion(null);
    setAutoSaveStatus('idle');
    setTimeout(() => {
      skipAutosaveRef.current = false;
    }, 0);
  }, [currentBatch?.id, draftBatchId, draftStorageKey]);

  // ── أسباب الرفض من قاعدة البيانات ─────────────────────────────────────
  const { data: rejectionReasons = [], refetch: refetchReasons } = useQuery({
    queryKey: ['claim-rejection-reasons'],
    queryFn: claimRejectionReasonsService.getAll,
    staleTime: 60000
  });
  const [isSavingNewReason, setIsSavingNewReason] = useState(false);

  const openRejectDialog = (type, idx = null) => {
    setRejectType(type);
    setRejectIdx(idx);

    if (type === 'line' && idx !== null) {
      const line = lines[idx];
      const isPartial = line.manualRefusedAmount > 0 && !line.rejected;
      setRejectionMode(isPartial ? 'partial' : 'full');
      setManualRefusedAmountInput(isPartial ? String(line.manualRefusedAmount) : '');
      setRejectionInput(line.rejectionReason || '');
    } else {
      setRejectionMode('full');
      setManualRefusedAmountInput('');
      setRejectionInput(type === 'line' ? lines[idx]?.rejectionReason || '' : rejectionInput || '');
    }

    setEditingReasonId(null);
    setEditingReasonText('');
    setShowReasonsList(false);
    setRejectDialogOpen(true);
  };

  const saveNewReason = async () => {
    if (!rejectionInput?.trim()) return;
    const alreadyExists = rejectionReasons.some((r) => r.reasonText === rejectionInput.trim());
    if (alreadyExists) return;
    setIsSavingNewReason(true);
    try {
      await claimRejectionReasonsService.create(rejectionInput.trim());
      await refetchReasons();
      enqueueSnackbar('✅ تم حفظ السبب الجديد في القائمة', { variant: 'success' });
    } catch {
      enqueueSnackbar('فشل حفظ السبب الجديد', { variant: 'error' });
    } finally {
      setIsSavingNewReason(false);
    }
  };

  const saveEditedReason = async () => {
    if (!editingReasonText?.trim() || !editingReasonId) return;
    try {
      const updated = await claimRejectionReasonsService.update(editingReasonId, editingReasonText.trim());
      await refetchReasons();
      // if the current input matches the old text, update it
      const oldReason = rejectionReasons.find((r) => r.id === editingReasonId);
      if (oldReason && rejectionInput === oldReason.reasonText) {
        setRejectionInput(updated.reasonText);
      }
      setEditingReasonId(null);
      setEditingReasonText('');
      enqueueSnackbar('✅ تم تعديل السبب', { variant: 'success' });
    } catch {
      enqueueSnackbar('فشل تعديل السبب', { variant: 'error' });
    }
  };

  const deleteReason = async (id) => {
    setIsDeletingReasonId(id);
    try {
      await claimRejectionReasonsService.delete(id);
      await refetchReasons();
      enqueueSnackbar('✅ تم حذف السبب', { variant: 'success' });
    } catch {
      enqueueSnackbar('فشل حذف السبب', { variant: 'error' });
    } finally {
      setIsDeletingReasonId(null);
    }
  };

  const confirmRejection = () => {
    if (rejectType === 'claim') {
      if (!rejectionInput?.trim()) {
        enqueueSnackbar('يجب إدخال سبب الرفض', { variant: 'warning' });
        return;
      }
      triggerConfirm('تأكيد رفض المطالبة', 'أنت على وشك رفض هذه المطالبة بالكامل. سيتم تصفير جميع حصص الشركة. هل تريد الاستمرار؟', () => {
        setIsClaimRejected(true);
        setIsDirty(true);
        setRejectDialogOpen(false);
      });
      return; // Don't close dialog yet
    } else {
      if (!rejectionInput?.trim()) {
        enqueueSnackbar('يجب إدخال سبب رفض البند', { variant: 'warning' });
        return;
      }
      const doUpdate = () => {
        if (rejectionMode === 'partial') {
          const amount = parseFloat(manualRefusedAmountInput) || 0;
          const maxAmount = lines[rejectIdx]?.byCompany ?? 0;
          if (amount <= 0 || amount > maxAmount + 0.001) {
            enqueueSnackbar(`مبلغ الرفض الجزئي يجب أن يكون بين 0.01 و ${maxAmount.toFixed(2)} د.ل`, { variant: 'warning' });
            return;
          }
          updateLine(rejectIdx, {
            manualRefusedAmount: parseFloat(amount.toFixed(2)),
            rejectionReason: rejectionInput,
            rejected: false,
            oldRejected: 0
          });
        } else {
          updateLine(rejectIdx, {
            rejected: true,
            rejectionReason: rejectionInput,
            manualRefusedAmount: 0,
            oldRejected: 1
          });
        }
        setRejectDialogOpen(false);
      };

      if (rejectionMode === 'full') {
        triggerConfirm('تأكيد رفض البند', 'هل تريد رفض هذا البند بالكامل (التزام الشركة سيصبح صفراً)؟', doUpdate);
      } else {
        doUpdate();
      }
      return;
    }
  };

  // Scrolls the offending row into view and gives it a brief highlight, so a
  // toast that names "line 3" of a long table actually gets the reader to
  // line 3 instead of leaving them to scroll and count rows themselves.
  const scrollToLine = (zeroBasedIndex) => {
    const row = document.getElementById(`claim-line-row-${zeroBasedIndex}`);
    if (!row) return;
    row.scrollIntoView({ behavior: 'smooth', block: 'center' });
    row.style.transition = 'background-color 0.3s ease';
    const previousBg = row.style.backgroundColor;
    row.style.backgroundColor = 'rgba(211, 47, 47, 0.18)';
    setTimeout(() => {
      row.style.backgroundColor = previousBg;
    }, 1600);
  };

  const handleSave = async (resetAfter = false, saveMode = 'submit') => {
    if (isSavingRef.current) return;

    if (entryContextBlockReason) {
      enqueueSnackbar(entryContextBlockReason, { variant: entryContextError ? 'error' : 'warning', autoHideDuration: 6500 });
      return;
    }

    // التحقق من الحقول المطلوبة بشكل احترافي
    const missingFields = [];
    if (!member) missingFields.push('المستفيد');
    if (!diagnosis?.trim()) missingFields.push('التشخيص الطبي');
    // doctorName is deliberately absent: the server accepts a claim without it
    // (ClaimCreateDto lists it under optional fields, the column is nullable),
    // so blocking submission on it here invented a rule the system does not have.
    if (!serviceDate) missingFields.push('تاريخ الخدمة');

    // التحقق من وجود خدمات صحيحة
    const hasValidLines = lines.some((l) => l.service || l.serviceName);
    if (!hasValidLines) missingFields.push('بند خدمة طبي واحد على الأقل');

    if (missingFields.length > 0) {
      setShowValidationErrors(true);
      enqueueSnackbar(`⚠️ لا يمكن الحفظ. يرجى إدخال الحقول التالية: ${missingFields.join('، ')}`, {
        variant: 'error',
        autoHideDuration: 5000
      });
      // Focus the first missing field, in the same order it was checked
      // above, so the person reading the error lands on the field to fix
      // instead of having to hunt for it across the form.
      if (!member) memberRef.current?.focus();
      else if (!diagnosis?.trim()) diagnosisRef.current?.focus();
      else if (!serviceDate) serviceDateRef.current?.focus();
      return;
    }

    const invalidQuantityLines = invalidQuantityLineNumbers(lines);
    if (invalidQuantityLines.length > 0) {
      enqueueSnackbar(`الكمية يجب أن تكون عدداً صحيحاً أكبر من صفر في البنود: ${invalidQuantityLines.join('، ')}`, {
        variant: 'error',
        autoHideDuration: 6000
      });
      scrollToLine(invalidQuantityLines[0] - 1);
      return;
    }

    if (!isClaimRejected && coveragePending) {
      enqueueSnackbar('لا يمكن الحفظ أثناء انتظار قرار محرك التغطية. انتظر اكتمال تحديث جميع البنود.', {
        variant: 'warning',
        autoHideDuration: 5000
      });
      return;
    }

    if (beneficiarySettlement.excessPayment > 0) {
      enqueueSnackbar('المبلغ المدفوع من المستفيد أكبر من التزامه والمبلغ المرفوض. عدّل المبلغ قبل الحفظ.', {
        variant: 'error',
        autoHideDuration: 7000
      });
      return;
    }

    const uncoveredLineIndex = lines.findIndex(
      (line) => (line.service || line.serviceName) && !line.rejected && (line.notCovered || (Number(line.coveragePercent) || 0) <= 0)
    );
    if (!isClaimRejected && uncoveredLineIndex !== -1) {
      enqueueSnackbar('لا يمكن اعتماد مطالبة تحتوي خدمات غير مغطاة. غيّر سياق المطالبة أو ارفض البند/المطالبة بسبب واضح.', {
        variant: 'error',
        autoHideDuration: 7000
      });
      scrollToLine(uncoveredLineIndex);
      return;
    }

    setShowValidationErrors(false);

    // تحققات إضافية لأسعار الخدمات
    if (!isClaimRejected && lines.some((l) => (l.service || l.serviceName) && !l.rejected && (parseFloat(l.unitPrice) || 0) <= 0)) {
      enqueueSnackbar('يجب أن يكون سعر الوحدة أكبر من صفر لكل بند غير مرفوض', { variant: 'error' });
      return;
    }

    isSavingRef.current = true;
    setSaving(true);
    try {
      const actualDate = serviceDate || defaultDate;

      // التحقق: تاريخ الخدمة لا يجوز أن يكون في المستقبل
      if (actualDate && new Date(actualDate) > new Date()) {
        enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) في المستقبل — يجب إدخال تاريخ صحيح`, { variant: 'error', autoHideDuration: 6000 });
        setSaving(false);
        isSavingRef.current = false;
        return;
      }

      // المرحلة 2.2: التحقق من انتهاء صلاحية الوثيقة
      if (policyInfo?.endDate && new Date(actualDate) > new Date(policyInfo.endDate)) {
        enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) يتجاوز نهاية الوثيقة المحددة (${policyInfo.endDate}) — لا يمكن الحفظ`, {
          variant: 'error',
          autoHideDuration: 6000
        });
        setSaving(false);
        isSavingRef.current = false;
        return;
      }

      // حالة قاعدة البيانات REJECTED فقط إذا:
      // 1. المستخدم ضغط "رفض المطالبة" صراحة (isClaimRejected)
      // 2. جميع البنود مرفوضة يدوياً (allLinesManuallyRejected)
      // أما وجود مبلغ مرفوض جزئي بسبب سعر/سقف فيُعرض للمستخدم كمرفوضة، مع إبقاء الجزء المقبول قابلاً للصرف.
      const activeLines = lines.filter((l) => l.service || l.serviceName);
      const allLinesManuallyRejected = activeLines.length > 0 && activeLines.every((l) => l.rejected);

      const effectivelyRejected = isClaimRejected || allLinesManuallyRejected;

      // إذا كانت المطالبة مرفوضة كلياً — يجب إدخال سبب رفض
      let effectiveRejectionReason = rejectionInput?.trim() || null;
      if (isClaimRejected && !effectiveRejectionReason) {
        enqueueSnackbar('يجب إدخال سبب رفض المطالبة قبل الحفظ', { variant: 'error' });
        setSaving(false);
        isSavingRef.current = false;
        return;
      }
      // للبنود المرفوضة يدوياً فقط (دون رفض كلي) — نأخذ أول سبب من البنود
      if (effectivelyRejected && !effectiveRejectionReason) {
        const autoReason = activeLines.find((l) => l.rejectionReason)?.rejectionReason;
        effectiveRejectionReason = autoReason || 'جميع البنود مرفوضة';
      }

      const claimData = {
        memberId: member.id,
        providerId: parseInt(providerId),
        claimBatchId: currentBatch?.id, // Phase 11 Link
        serviceDate: actualDate,
        diagnosisDescription: diagnosis,
        doctorName: doctorName.trim(),
        complaint,
        notes,
        // Draft-first workflow:
        // - "حفظ كمسودة" persists DRAFT and keeps the claim editable.
        // - "إرسال" persists SUBMITTED for review/next workflow step.
        // - explicit full rejection remains REJECTED.
        // Do not send null here: null is the legacy direct-approval path.
        status: effectivelyRejected ? 'REJECTED' : saveMode === 'draft' ? 'DRAFT' : 'SUBMITTED',
        rejectionReason: effectivelyRejected ? effectiveRejectionReason : null,
        preAuthorizationId: preAuthId ? parseInt(preAuthId) : null,
        encounterType,
        claimContextCode,
        fullCoverage: fullCoverage,
        beneficiaryPaidAmount: beneficiarySettlement.paid,
        // لا ترسل صفوف الإدخال الفارغة التي يضيفها المستخدم ولم يختر لها خدمة.
        // التحقق أعلاه يعتمد activeLines، ويجب أن يستخدم الحفظ المصدر نفسه حتى
        // لا تصل أسطر بلا medicalServiceId أو pricingItemId إلى الخادم.
        lines: activeLines.map((l) => {
          const isManualAmountLine = (l.pricingMode || l.service?.pricingMode) === 'MANUAL_AMOUNT';
          const isClaimUnitPriceLine = (l.pricingMode || l.service?.pricingMode) === 'CLAIM_UNIT_PRICE';
          return {
            id: typeof l.id === 'number' ? l.id : null,
            medicalServiceId: l.medicalServiceId || l.service?.medicalServiceId || l.service?.serviceId || null,
            pricingItemId: isManualAmountLine ? null : (l.pricingItemId ?? l.service?.pricingItemId ?? null),
            serviceName: l.serviceName || l.service?.serviceName || '',
            serviceCode: l.serviceCode || l.service?.serviceCode || '',
            serviceCategoryId:
              l.serviceCategoryId ??
              l.medicalCategoryId ??
              l.service?.serviceCategoryId ??
              l.service?.categoryId ??
              l.service?.medicalCategoryId ??
              null,
            serviceCategoryName:
              l.serviceCategoryName ??
              l.medicalCategoryName ??
              l.service?.serviceCategoryName ??
              l.service?.categoryName ??
              l.service?.medicalCategoryName ??
              null,
            quantity: Number(l.quantity),
            unitPrice: parseFloat(l.unitPrice) || 0,
            manualAmount: isManualAmountLine ? parseFloat(l.unitPrice) || 0 : null,
            rejected: isClaimRejected ? true : l.rejected || false,
            rejectionReason: isClaimRejected ? effectiveRejectionReason : l.rejectionReason || null,
            // refusedAmount on the rendered line includes price/benefit-limit
            // refusals calculated by the server. Sending that aggregate back as
            // a manual refusal makes the financial engine subtract the same
            // ceiling excess twice (and can exceed the insurer gross share).
            // Only the user's explicit refusal is command input; the backend
            // must recalculate every automatic refusal at save time.
            manualRefusedAmount: isClaimRejected ? 0 : parseFloat(l.manualRefusedAmount) || 0
          };
        })
      };

      let resultClaimId;
      if (editingClaimId) {
        await claimsService.update(editingClaimId, claimData);
        resultClaimId = editingClaimId;
      } else {
        // FIX: Open/create batch here (on first save), NOT on page load
        // This ensures GET /current is truly read-only
        let batchForSave = currentBatch;
        if (!batchForSave) {
          try {
            batchForSave = await claimBatchesService.openOrGetBatch(providerId, employerId, year, month);
            // Update the query cache so the UI reflects the new batch
            queryClient.setQueryData(['claim-batch-current', providerId, employerId, month, year], batchForSave);
          } catch (batchErr) {
            enqueueSnackbar(`فشل فتح الدفعة: ${batchErr?.response?.data?.message || batchErr?.message}`, { variant: 'error' });
            setSaving(false);
            isSavingRef.current = false;
            return;
          }
          claimData.claimBatchId = batchForSave?.id;
        }

        // The backend owns the transaction: either both visit and claim exist,
        // or neither does. Browser-side compensating DELETE was not atomic and
        // could itself fail, leaving an orphan visit that blocks later care.
        const claimResponse = await claimsService.createDirectEntry(parseInt(employerId), claimData, directEntryKey);
        resultClaimId = claimResponse.id;
      }

      // Upload attachments if any exist
      if (resultClaimId && attachments.length > 0) {
        for (const file of attachments) {
          const fd = new FormData();
          fd.append('file', file);
          fd.append('attachmentType', 'MEDICAL_REPORT');
          try {
            await claimsService.uploadAttachment(resultClaimId, fd);
          } catch (attErr) {
            console.error('Failed to upload attachment', attErr);
            enqueueSnackbar(`فشل رفع المرفق: ${file.name}`, { variant: 'warning' });
          }
        }
      }

      enqueueSnackbar(`✅ ${t('claimEntry.savedSuccess')} — #${resultClaimId}`, { variant: 'success' });

      try {
        const batchIdForDelete = draftBatchId || currentBatch?.id;
        if (batchIdForDelete) {
          await claimsService.deleteDraft(batchIdForDelete);
        }
      } catch {
        // Non-blocking cleanup
      }
      try {
        localStorage.removeItem(draftStorageKey);
      } catch {
        // ignore local cleanup errors
      }
      setDraftVersion(null);
      setAutoSaveStatus('idle');

      invalidateBatchData();
      if (resetAfter) {
        resetForm();
        setEditingClaimId(null);
      } else {
        setEditingClaimId(resultClaimId);
        // Keep isDirty as false after save
        setIsDirty(false);
      }
    } catch (err) {
      // Extract the Arabic backend message if available (400 validation, 409 conflict, etc.)
      const apiMsg = err.response?.data?.messageAr || err.response?.data?.message || err.userMessage || err.message;
      enqueueSnackbar(apiMsg || t('claimEntry.saveFailed'), { variant: 'error', autoHideDuration: 7000 });
    } finally {
      setSaving(false);
      isSavingRef.current = false;
    }
  };

  const confirmDeleteClaim = async () => {
    const claimId = confirmDeleteId;
    if (!claimId) return;
    try {
      await claimsService.remove(claimId, 'تم الإلغاء');
      enqueueSnackbar(`✅ تم إلغاء المطالبة #${claimId}`, { variant: 'success' });
      setConfirmDeleteId(null);
      invalidateBatchData();
      // ✅ FIX: Restore ceiling in current form after deletion
      if (member?.id && policyId) {
        setTimeout(() => refetchCoverageOnEditRef.current(encounterType, fullCoverage, claimContextCode), 200);
      }
    } catch (err) {
      enqueueSnackbar(err.message || 'فشل إلغاء المطالبة', { variant: 'error' });
    }
  };

  const detailUrl = `/claims/batches/detail?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`;
  const monthLabel = MONTHS_AR[(month || 1) - 1];

  return (
    <Box dir="rtl" sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 105px)', overflow: 'hidden' }}>
      {/* ═══ رأس الصفحة المضغوط ═══ */}
      <Box sx={{ flexShrink: 0, mb: 0.5 }}>
        <ModernPageHeader
          title={`${t('claimEntry.pageTitle')} — ${monthLabel} ${year || ''}`}
          titleExtras={
            <Stack direction="row" spacing={1} alignItems="center">
              <Chip
                size="small"
                variant="filled"
                label={isDirty ? t('claimEntry.statusDraft') : t('claimEntry.statusNew')}
                color={isDirty ? 'warning' : 'primary'}
                sx={{ fontWeight: 600, fontSize: '0.85rem' }}
              />
              {entryContext && policyInfo && (
                <Chip
                  icon={<PolicyIcon sx={{ fontSize: '0.85rem' }} />}
                  size="small"
                  label={`${t('claimEntry.benefitPolicy')}: ${policyInfo.policyCode || policyInfo.name} (${policyInfo.startDate} — ${policyInfo.endDate || 'مفتوحة'})`}
                  color="success"
                  variant="outlined"
                  sx={{ fontWeight: 600, fontSize: '0.85rem', borderColor: 'success.main', color: 'success.main' }}
                />
              )}
              {isClaimRejected && (
                <Chip
                  icon={<RejectIcon sx={{ fontSize: '0.85rem' }} />}
                  size="small"
                  label="مطالبة مرفوضة"
                  color="error"
                  variant="filled"
                  sx={{ fontWeight: 600, fontSize: '0.85rem' }}
                />
              )}
            </Stack>
          }
          /* The policy is named, not just numbered. A code identifies a row in a
             table; a data-entry clerk checking they are billing the right cover
             recognises "وثيقة المنطقة الحرة جليانة", not "POL-2026-014". The
             contract number stays because it is what appears on paperwork. */
          subtitle={[
            `${t('providers.singular')}: ${provider?.name || '...'}`,
            `الوثيقة: ${entryContext?.policyName || 'بانتظار التحقق'}`,
            `رقم العقد: ${entryContext?.contractNumber || 'بانتظار التحقق'}`,
            `المؤمن عليه: ${member?.fullName || '...'} (${member?.cardNumber || '—'})`
          ].join(' | ')}
          icon={<ReceiptIcon />}
          actions={
            <Stack direction="row" spacing={1} alignItems="center">
              {/* Draft autosave, shown as a dot rather than a word. It was two
                  English phrases on an Arabic screen, and it reported something
                  the clerk never asked for and cannot act on -- the draft saves
                  itself either way. A dot that pulses while writing and settles
                  when written says the same thing without taking a line of the
                  header. The tooltip and the label carry the meaning for anyone
                  who cannot read a colour. */}
              {autoSaveStatus !== 'idle' && (
                <Tooltip title={autoSaveStatus === 'saving' ? 'جارٍ حفظ المسودة' : 'المسودة محفوظة'}>
                  <Box
                    role="status"
                    aria-label={autoSaveStatus === 'saving' ? 'جارٍ حفظ المسودة' : 'المسودة محفوظة'}
                    sx={{
                      width: 9,
                      height: 9,
                      borderRadius: '50%',
                      flexShrink: 0,
                      bgcolor: autoSaveStatus === 'saving' ? 'warning.main' : 'success.main',
                      // Through the keyframes helper, not a bare name declared
                      // inside sx: the helper emits the rule and hands back the
                      // generated name, so the animation cannot reference a
                      // name that was never injected.
                      animation: autoSaveStatus === 'saving' ? `${draftPulse} 1.1s ease-in-out infinite` : 'none',
                      '@media (prefers-reduced-motion: reduce)': { animation: 'none' }
                    }}
                  />
                </Tooltip>
              )}

              <Tooltip title={t('claimEntry.discardChanges')}>
                <span>
                  <IconButton size="small" onClick={resetForm} disabled={!isDirty} color="error">
                    <DiscardIcon sx={{ fontSize: '1.2rem' }} />
                  </IconButton>
                </span>
              </Tooltip>

              <Button
                variant="outlined"
                size="small"
                color="secondary"
                startIcon={<BackIcon sx={{ ml: 1, mr: 0 }} />}
                onClick={() => navigate(detailUrl)}
                sx={{}}
              >
                {t('claimEntry.backToList')}
              </Button>
            </Stack>
          }
        />
      </Box>

      {/* FIX: Show batch error as visible alert (not silent) */}
      {batchError && (
        <Alert severity="warning" variant="filled" sx={{ mx: '1.0rem', mb: 0.5 }}>
          ⚠️ تعذّر تحميل بيانات الدفعة: {batchError?.response?.data?.message || batchError?.message || 'خطأ غير معروف'}
          {batchError?.response?.status === 403 && ' — لا تملك صلاحية الوصول.'}
        </Alert>
      )}

      {/* ═══ المحتوى ═══ */}
      <Box sx={{ flex: 1, display: 'flex', minHeight: 0, px: '1.0rem', pb: '0.4rem' }}>
        {/* ── النموذج الرئيسي ── */}
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 }}>
          <Paper
            variant="outlined"
            sx={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
              boxShadow: '0 2px 10px rgba(0,0,0,0.05)'
            }}
          >
            {/* ── لوحة معلومات التعديل ── */}
            {editingClaimId && (
              <Box
                sx={{
                  px: '1.25rem',
                  py: '0.6rem',
                  bgcolor: alpha(theme.palette.info.main, 0.08),
                  borderBottom: `1.5px solid ${alpha(theme.palette.info.main, 0.3)}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem'
                }}
              >
                <InfoIcon sx={{ color: 'info.main', fontSize: '1.25rem' }} />
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2" fontWeight={600} color="info.dark">
                    أنت الآن في وضع التعديل (مطالبة #{editingClaimId})
                  </Typography>
                  <Typography variant="caption" color="info.main" fontWeight={400}>
                    جاري تعديل بيانات المطالبة المختارة من الشريط الجانبي.
                  </Typography>
                </Box>
                <Button
                  size="small"
                  color="info"
                  variant="outlined"
                  onClick={() => {
                    resetForm();
                    setEditingClaimId(null);
                  }}
                  sx={{}}
                >
                  إلغاء وتعديل جديد
                </Button>
              </Box>
            )}

            {!headerExpanded && (
              <Box
                sx={{
                  flexShrink: 0,
                  px: '1.25rem',
                  py: 0.5,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 1,
                  bgcolor: alpha(theme.palette.primary.main, 0.035)
                }}
              >
                <Typography variant="caption" noWrap sx={{ minWidth: 0 }}>
                  {member?.fullName || 'لم يحدد مستفيد'} · {serviceDate || 'بلا تاريخ'} · {diagnosis?.trim() || 'بلا تشخيص'}
                </Typography>
                <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flexShrink: 0 }}>
                  <Chip
                    size="small"
                    variant="outlined"
                    label={`${lines.length} بند`}
                    sx={{ height: 24, fontWeight: 700, fontSize: '0.72rem', borderColor: alpha(theme.palette.primary.main, 0.3) }}
                  />
                  <Tooltip title="إظهار/إخفاء الأعمدة">
                    <IconButton size="small" onClick={handleOpenCols}>
                      <ViewColumnIcon fontSize="small" color="primary" />
                    </IconButton>
                  </Tooltip>
                  <Button size="small" startIcon={<ExpandIcon />} onClick={() => setHeaderExpanded(true)} sx={{ flexShrink: 0 }}>
                    إظهار بيانات المطالبة
                  </Button>
                </Stack>
              </Box>
            )}
            <Collapse in={headerExpanded} timeout={160} unmountOnExit>
              <Box sx={{ flexShrink: 0, px: '1.25rem', pt: '0.5rem', pb: '0.4rem', bgcolor: 'background.paper' }}>
                <ClaimHeaderFields
                  member={member}
                  setMember={setMember}
                  memberOptions={memberOptions}
                  searchingMember={searchingMember}
                  memberSearchError={memberSearchError}
                  onRetryMemberSearch={retryMemberSearch}
                  setMemberInput={setMemberInput}
                  memberRef={memberRef}
                  diagnosisRef={diagnosisRef}
                  serviceDateRef={serviceDateRef}
                  diagnosis={diagnosis}
                  setDiagnosis={setDiagnosis}
                  encounterType={encounterType}
                  claimContextCode={claimContextCode}
                  claimContexts={claimContexts}
                  setClaimContextCode={setClaimContextCode}
                  setEncounterType={setEncounterType}
                  fullCoverage={fullCoverage}
                  setFullCoverage={setFullCoverage}
                  onRefetchAll={refetchAllLinesCoverageCallback}
                  setPreAuthId={setPreAuthId}
                  serviceDate={serviceDate}
                  setServiceDate={setServiceDate}
                  setIsDirty={setIsDirty}
                  financialSummary={financialSummary}
                  currentCompanyCommitment={totals.company}
                  editingApprovedAmount={editingClaim?.approvedAmount || 0}
                  t={t}
                  showValidationErrors={showValidationErrors}
                />
                <Box
                  sx={{
                    mt: 0.5,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 1,
                    flexWrap: 'wrap'
                  }}
                >
                  <Stack direction="row" spacing={1} alignItems="center">
                    <ClaimAdditionalDetails
                      complaint={complaint}
                      setComplaint={setComplaint}
                      setIsDirty={setIsDirty}
                      preAuthResults={preAuthResults}
                      searchingPreAuth={searchingPreAuth}
                      preAuthId={preAuthId}
                      setPreAuthId={setPreAuthId}
                      doctorName={doctorName}
                      setDoctorName={setDoctorName}
                    />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`${lines.length} بند`}
                      sx={{ height: 26, fontWeight: 700, fontSize: '0.75rem', borderColor: alpha(theme.palette.primary.main, 0.3) }}
                    />
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Button size="small" startIcon={<CompactIcon />} onClick={() => setHeaderExpanded(false)}>
                      توسيع مساحة إدخال البنود
                    </Button>
                    <Tooltip title="إظهار/إخفاء الأعمدة">
                      <IconButton size="small" onClick={handleOpenCols}>
                        <ViewColumnIcon fontSize="small" color="primary" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </Box>
              </Box>
            </Collapse>
            <Menu anchorEl={anchorElCols} open={Boolean(anchorElCols)} onClose={handleCloseCols}>
              <MenuItem onClick={() => handleToggleColumn('coverage')}>
                <ListItemIcon>
                  <Checkbox checked={visibleColumns.coverage} size="small" />
                </ListItemIcon>
                <ListItemText primary="التحمل %" />
              </MenuItem>
              <MenuItem onClick={() => handleToggleColumn('benefitLimit')}>
                <ListItemIcon>
                  <Checkbox checked={visibleColumns.benefitLimit} size="small" />
                </ListItemIcon>
                <ListItemText primary="سقف المنفعة" />
              </MenuItem>
              <MenuItem onClick={() => handleToggleColumn('remainingLimit')}>
                <ListItemIcon>
                  <Checkbox checked={visibleColumns.remainingLimit} size="small" />
                </ListItemIcon>
                <ListItemText primary="المتبقي من السقف" />
              </MenuItem>
              <MenuItem onClick={() => handleToggleColumn('refused')}>
                <ListItemIcon>
                  <Checkbox checked={visibleColumns.refused} size="small" />
                </ListItemIcon>
                <ListItemText primary="المرفوض" />
              </MenuItem>
              {!isReviewer && (
                <MenuItem onClick={() => handleToggleColumn('companyShare')}>
                  <ListItemIcon>
                    <Checkbox checked={visibleColumns.companyShare} size="small" />
                  </ListItemIcon>
                  <ListItemText primary="التزام الشركة" />
                </MenuItem>
              )}
              <MenuItem onClick={() => handleToggleColumn('patientShare')}>
                <ListItemIcon>
                  <Checkbox checked={visibleColumns.patientShare} size="small" />
                </ListItemIcon>
                <ListItemText primary="التزام المستفيد" />
              </MenuItem>
            </Menu>

            <Divider />

            <Box
              sx={{
                position: 'relative',
                flex: 1,
                minHeight: 0,
                display: 'flex',
                flexDirection: 'column'
              }}
            >
              {editCoverageLoading && (
                <Alert
                  role="status"
                  aria-live="polite"
                  severity="info"
                  icon={<CircularProgress size={16} thickness={5} />}
                  sx={{
                    mb: 1,
                    alignItems: 'center',
                    border: `1px solid ${alpha(theme.palette.primary.main, 0.18)}`,
                    bgcolor: alpha(theme.palette.primary.main, 0.05),
                    '& .MuiAlert-message': { width: '100%' }
                  }}
                >
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }}>
                    <Typography variant="body2" fontWeight={800} color="primary.dark">
                      جارٍ تحديث الحساب المالي
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      يمكنك مراجعة البنود، وسيُفتح الحفظ بعد اكتمال التغطية والسقوف.
                    </Typography>
                  </Stack>
                </Alert>
              )}

              <Box
                sx={{
                  flex: 1,
                  minHeight: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  transition: 'opacity 120ms ease-out'
                }}
              >
                {/* The per-line recalculation used to announce itself with a
                    floating banner over the service column -- covering the very
                    field being typed into, on every keystroke that changed an
                    amount. The pending state still lives on each line, so a
                    line that has not settled is visible where it happens rather
                    than through a notice parked on top of the table. */}
                {noEffectiveContractServicesForDate && (
                  <Alert severity="warning" sx={{ m: 1.5, alignItems: 'center' }}>
                    العقد والوثيقة صالحان لهذا التاريخ، لكن لا توجد أسعار خدمات فعالة في العقد بتاريخ الخدمة. راجع فترة سريان أسعار خدمات
                    العقد.
                  </Alert>
                )}
                <TableContainer dir="rtl" sx={{ flex: 1, overflow: 'auto' }}>
                  <Table
                    dir="rtl"
                    size="small"
                    stickyHeader
                    sx={{
                      minWidth: '60rem',
                      '& .MuiTableCell-body': {
                        borderRight: '1px solid #e0e0e0',
                        borderBottom: '1px solid #e0e0e0',
                        '&:last-child': { borderRight: 'none' }
                      }
                    }}
                  >
                    <TableHead>
                      <TableRow>
                        <TH align="center" w={40}>
                          #
                        </TH>
                        <TH align="center" w={280}>
                          الخدمة الطبية
                        </TH>
                        <TH align="center" w={45}>
                          الكمية
                        </TH>
                        <TH align="center" w={70}>
                          سعر الوحدة
                        </TH>
                        {visibleColumns.coverage && (
                          <TH align="center" w={60}>
                            التحمل %
                          </TH>
                        )}
                        {visibleColumns.benefitLimit && (
                          <TH align="center" w={110}>
                            سقف المنفعة
                          </TH>
                        )}
                        {visibleColumns.remainingLimit && (
                          <TH align="center" w={110}>
                            {' '}
                            المتبقي من السقف{' '}
                          </TH>
                        )}
                        {visibleColumns.refused && (
                          <TH align="center" w={75}>
                            المرفوض
                          </TH>
                        )}
                        <TH align="center" w={75}>
                          المقبول
                        </TH>
                        {visibleColumns.companyShare && (
                          <TH align="center" w={105}>
                            التزام الشركة
                          </TH>
                        )}
                        {visibleColumns.patientShare && (
                          <TH align="center" w={105}>
                            التزام المستفيد
                          </TH>
                        )}
                        <TH align="left" w={40}></TH>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {lines.map((line, idx) => (
                        <ClaimLineRow
                          key={line.id}
                          line={line}
                          idx={idx}
                          theme={theme}
                          serviceOptions={serviceOptions}
                          loadingServices={loadingServices}
                          servicesError={servicesError}
                          servicesErrorMessage={servicesFailure?.userMessage || servicesFailure?.message}
                          onRetryServices={refetchServices}
                          onServiceSearchChange={setServiceSearchInput}
                          updateLine={updateLine}
                          handleServiceChange={handleServiceChange}
                          removeLine={removeLine}
                          openRejectDialog={openRejectDialog}
                          policyInfo={policyInfo}
                          visibleColumns={visibleColumns}
                          triggerConfirm={triggerConfirm}
                          onOpenCustomServiceDialog={() => {
                            setActiveLineIdForCustomService(line.id);
                            setCustomServiceData({ categoryId: '', serviceName: '', serviceCode: '', contractPrice: '' });
                            setCustomServiceError(null);
                            setCustomServiceDialogOpen(true);
                          }}
                          onOpenClassificationReview={openClassificationReviewDialog}
                        />
                      ))}
                      <TableRow>
                        <TableCell colSpan={12} sx={{ py: 0.35, borderRight: 'none' }}>
                          <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                            <Button
                              size="small"
                              startIcon={<AddIcon />}
                              onClick={addLine}
                              sx={{ fontWeight: 700, color: 'primary.main', px: 0 }}
                            >
                              {t('claimEntry.addLine')}
                            </Button>
                          </Box>
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>

                {/* ── ذيل المطالبة والمجاميع (مكون منفصل) ── */}
                <ClaimTotalsFooter
                  isClaimRejected={isClaimRejected}
                  handleSave={handleSave}
                  saving={saving}
                  isDirty={isDirty}
                  coveragePending={coveragePending}
                  financialDataUnavailable={
                    Boolean(member?.id) && (!serviceDate || loadingEntryContext || entryContextError || !entryContext)
                  }
                  hasUncoveredLines={lines.some(
                    (line) => {
                      const hasService = line.service || line.serviceName;
                      const hasAmountForCoverage = Number(line.unitPrice || 0) > 0 && Number(line.quantity || 0) > 0;
                      return (
                        hasService &&
                        hasAmountForCoverage &&
                        !line.rejected &&
                        (line.notCovered || (Number(line.coveragePercent) || 0) <= 0)
                      );
                    }
                  )}
                  setIsClaimRejected={setIsClaimRejected}
                  setIsDirty={setIsDirty}
                  setRejectionInput={setRejectionInput}
                  openRejectDialog={openRejectDialog}
                  totals={totals}
                  beneficiaryPaidAmount={beneficiaryPaidAmount}
                  beneficiarySettlement={beneficiarySettlement}
                  onBeneficiaryPaidAmountChange={(value) => {
                    setBeneficiaryPaidAmount(value);
                    setIsDirty(true);
                  }}
                  theme={theme}
                  lines={lines}
                  t={t}
                  visibleColumns={visibleColumns}
                  saveDisabledReason={saveDisabledReason}
                />
              </Box>
            </Box>
          </Paper>
        </Box>
      </Box>

      <Dialog open={classificationReview.open} onClose={closeClassificationReviewDialog} fullWidth maxWidth="sm" dir="rtl">
        <DialogTitle sx={{ fontWeight: 900 }}>إبلاغ عن تصنيف خدمة غير دقيق</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            <Alert severity="info">
              التصنيف المالي لهذه المطالبة يؤخذ من مصدر الخدمة المعتمد في العقد أو القاموس النظامي. هذا الإجراء يرسل اقتراحاً للمراجعة ولا
              يغيّر حساب المطالبة الحالية.
            </Alert>

            <Box>
              <Typography variant="caption" color="text.secondary">
                الخدمة
              </Typography>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {activeClassificationLine?.serviceName ||
                  activeClassificationLine?.service?.serviceName ||
                  activeClassificationLine?.service?.name ||
                  '-'}
              </Typography>
              {(activeClassificationLine?.serviceCode || activeClassificationLine?.service?.serviceCode) && (
                <Typography variant="caption" color="text.secondary">
                  كود المرفق: {activeClassificationLine?.serviceCode || activeClassificationLine?.service?.serviceCode}
                </Typography>
              )}
            </Box>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Chip
                variant="outlined"
                color="primary"
                label={`التصنيف الحالي: ${
                  currentClassificationCategory
                    ? `${currentClassificationCategory.name}${currentClassificationCategory.code ? ` (${currentClassificationCategory.code})` : ''}`
                    : 'غير محدد'
                }`}
                sx={{ justifyContent: 'flex-start', fontWeight: 700 }}
              />
              {activeClassificationLine?.classificationReviewed && (
                <Chip color="success" variant="outlined" label="تمت مراجعته داخل المطالبة" sx={{ fontWeight: 700 }} />
              )}
            </Stack>

            <Autocomplete
              options={categoriesForReview}
              value={
                categoriesForReview.find((category) => String(category.id) === String(classificationReview.selectedCategoryId)) || null
              }
              onChange={(_, category) =>
                setClassificationReview((prev) => ({
                  ...prev,
                  selectedCategoryId: category?.id ? String(category.id) : ''
                }))
              }
              getOptionLabel={(category) =>
                category ? `${category.nameAr || category.name || category.nameEn || ''}${category.code ? ` (${category.code})` : ''}` : ''
              }
              isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
              filterOptions={(options, state) => {
                const query = normalizeArabicSearch(state.inputValue.trim());
                if (!query) return options;
                return options.filter((category) =>
                  [category.code, category.name, category.nameAr, category.nameEn]
                    .filter(Boolean)
                    .some((value) => normalizeArabicSearch(value).includes(query))
                );
              }}
              renderInput={(params) => (
                <TextField {...params} label="التصنيف المقترح للمراجعة" placeholder="ابحث باسم التصنيف أو الكود..." />
              )}
            />

            {activeClassificationCategory && (
              <Alert severity="success" variant="outlined">
                سيتم إرسال اقتراح: {activeClassificationCategory.name}
                {activeClassificationCategory.code ? ` (${activeClassificationCategory.code})` : ''} إلى قائمة مراجعة القاموس.
              </Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ justifyContent: 'space-between', px: 3, py: 2 }}>
          <Button onClick={closeClassificationReviewDialog}>إبقاء كما هو</Button>
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" color="warning" onClick={sendClassificationToReviewQueue}>
              إرسال لقائمة المراجعة
            </Button>
            <Button
              variant="contained"
              color="primary"
              onClick={approveClassificationForLine}
              disabled={!classificationReview.selectedCategoryId}
            >
              إرسال الاقتراح
            </Button>
          </Stack>
        </DialogActions>
      </Dialog>

      <RecoveryDialog
        recoveryDialog={recoveryDialog}
        onRestoreServer={restoreServerDraft}
        onRestoreLocal={restoreLocalDraft}
        onDismiss={dismissRecovery}
      />

      <RejectClaimDialog
        open={rejectDialogOpen}
        onClose={() => setRejectDialogOpen(false)}
        rejectType={rejectType}
        rejectIdx={rejectIdx}
        lines={lines}
        rejectionMode={rejectionMode}
        onRejectionModeChange={(value) => {
          setRejectionMode(value);
          setManualRefusedAmountInput('');
        }}
        manualRefusedAmountInput={manualRefusedAmountInput}
        onManualRefusedAmountChange={setManualRefusedAmountInput}
        rejectionReasons={rejectionReasons}
        rejectionInput={rejectionInput}
        onRejectionInputChange={setRejectionInput}
        isSavingNewReason={isSavingNewReason}
        onSaveNewReason={saveNewReason}
        editingReasonId={editingReasonId}
        editingReasonText={editingReasonText}
        onEditingReasonTextChange={setEditingReasonText}
        onStartEditReason={(reason) => {
          setEditingReasonId(reason.id);
          setEditingReasonText(reason.reasonText);
        }}
        onSaveEditedReason={saveEditedReason}
        onCancelEditReason={() => {
          setEditingReasonId(null);
          setEditingReasonText('');
        }}
        isDeletingReasonId={isDeletingReasonId}
        onDeleteReason={deleteReason}
        showReasonsList={showReasonsList}
        onToggleReasonsList={() => setShowReasonsList((v) => !v)}
        onConfirm={confirmRejection}
      />

      <ConfirmDeleteClaimDialog
        confirmDeleteId={confirmDeleteId}
        onCancel={() => setConfirmDeleteId(null)}
        onConfirm={confirmDeleteClaim}
      />

      <ActionConfirmDialog actionConfirm={actionConfirm} onClose={closeActionConfirm} />

      <CustomServiceDialog
        open={customServiceDialogOpen}
        onClose={handleCloseCustomServiceDialog}
        medicalCategories={medicalCategories}
        customServiceData={customServiceData}
        customServiceError={customServiceError}
        addingCustomService={addingCustomService}
        claimContextCode={claimContextCode}
        onFieldChange={handleCustomServiceDataChange}
        onClearError={() => setCustomServiceError(null)}
        onSubmit={handleSubmitCustomService}
      />
    </Box>
  );
}
