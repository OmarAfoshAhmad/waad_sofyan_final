/**
 * صفحة إدخال الدفعة — تخطيط RTL يملأ الشاشة
 * ✅ الجدول والفورم من اليمين لليسار
 * ✅ زر الحفظ مرئي دون scroll
 * ✅ كل النصوص من ar.js (لا hardcode)
 */
import { useState, useMemo, useRef, useCallback, useEffect, Fragment } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
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
  FormControlLabel,
  Radio,
  RadioGroup,
  Tooltip,
  alpha,
  TableFooter,
  InputAdornment,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Pagination,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  FormControl,
  InputLabel,
  Select
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  Save as SaveIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Receipt as ReceiptIcon,
  CheckCircle as DoneIcon,
  ArrowBack as BackIcon,
  Close as DiscardIcon,
  History as HistoryIcon,
  Search as SearchIcon,
  LocalPrintshop as PrintIcon,
  FileDownload as FileDownloadIcon,
  WarningAmber as WarningIcon,
  VerifiedUser as PolicyIcon,
  Info as InfoIcon,
  Block as RejectIcon,
  Cancel as CancelIcon,
  AttachFile as AttachFileIcon,
  Lock as LockIcon,
  AddCircleOutline as AddReasonIcon,
  ViewColumn as ViewColumnIcon,
  Edit as EditIcon,
  Check as CheckIcon,
  ExpandMore as ExpandMoreIcon,
  MedicalServices as MedicalServicesIcon,
  LocalHospital as InpatientIcon,
  Healing as OutpatientIcon
} from '@mui/icons-material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import useLocale from 'hooks/useLocale';

import unifiedMembersService from 'services/api/unified-members.service';
import providersService from 'services/api/providers.service';
import employersService from 'services/api/employers.service';
import claimsService from 'services/api/claims.service';
import * as medicalCategoriesService from 'services/api/medical-categories.service';
import claimBatchesService from 'services/api/claim-batches.service';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { claimRejectionReasonsService } from 'services/api/claim-rejection-reasons.service';
import systemSettingsService from 'services/api/systemSettings.service';
import { normalizeApiError, runWithRetry } from 'utils/api-error';
import axiosClient from 'utils/axios';

import { useCalculationLogic } from './hooks/useCalculationLogic';
import { useCoverageLogic } from './hooks/useCoverageLogic';
import { failedCoverageResult } from './hooks/coverageContract.mjs';

import { ClaimHeaderFields } from './components/ClaimHeaderFields';
import { ClaimEntryReadinessAlert } from './components/ClaimEntryReadinessAlert';
import { invalidQuantityLineNumbers } from './claim-entry-validation';
import { ClaimLineRow } from './components/ClaimLineRow';
import { ClaimTotalsFooter } from './components/ClaimTotalsFooter';
import { RecoveryDialog } from './components/RecoveryDialog';
import { RejectClaimDialog } from './components/RejectClaimDialog';
import { ConfirmDeleteClaimDialog } from './components/ConfirmDeleteClaimDialog';
import { ActionConfirmDialog } from './components/ActionConfirmDialog';
import { CustomServiceDialog } from './components/CustomServiceDialog';

const CLAIM_SERVICE_CONTEXTS = new Set(['OUTPATIENT', 'INPATIENT', 'ANY']);

const normalizeClaimServiceContext = (value) => {
  const normalized = String(value || '').trim().toUpperCase();
  return CLAIM_SERVICE_CONTEXTS.has(normalized) ? normalized : 'ANY';
};

const getServiceContext = (service) =>
  normalizeClaimServiceContext(
    service?.encounterType ??
      service?.defaultEncounterType ??
      service?.serviceEncounterType ??
      service?.pricingEncounterType ??
      service?.contextType ??
      service?.context
  );

const isServiceAllowedForClaimContext = (service, claimEncounterType) => {
  const claimContext = normalizeClaimServiceContext(claimEncounterType || 'OUTPATIENT');
  const serviceContext = getServiceContext(service);
  return serviceContext === 'ANY' || serviceContext === claimContext;
};

// ── أسماء الشهور ─────────────────────────────────────────────────────────────
const MONTHS_AR = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];

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

const hasMeaningfulDraftData = (draft) => {
  if (!draft) return false;
  if (draft.member?.id) return true;
  if ((draft.diagnosis || '').trim()) return true;
  if ((draft.complaint || '').trim()) return true;
  if ((draft.notes || '').trim()) return true;
  return Array.isArray(draft.lines) && draft.lines.some((l) => l?.serviceName || l?.serviceCode || l?.service);
};

// أنماط حقول الجدول القابلة للتعديل
const inlineSx = {
  '& .MuiInput-root::before': { display: 'none' },
  '& .MuiInput-root::after': { borderBottomColor: '#1b5e20', borderBottomWidth: 1 },
  '& input': { fontSize: '0.8rem', fontWeight: 500, textAlign: 'center' }
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
        py: 1,
        px: '0.75rem',
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

  // ── حالة النموذج ─────────────────────────────────────────────────────────
  const [member, setMember] = useState(null);
  const [memberInput, setMemberInput] = useState('');
  const [debouncedMemberInput, setDebouncedMemberInput] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setDebouncedMemberInput(memberInput), 350);
    return () => clearTimeout(t);
  }, [memberInput]);
  const [diagnosis, setDiagnosis] = useState('');
  const [doctorName, setDoctorName] = useState('');
  const [complaint, setComplaint] = useState('');
  const [applyBenefits, setApplyBenefits] = useState(true);
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState([newLine()]);
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
  const [page, setPage] = useState(0);
  const [attachments, setAttachments] = useState([]);
  const [editingClaimId, setEditingClaimId] = useState(initialClaimId);
  const [editHydrationVersion, setEditHydrationVersion] = useState(0);
  const [editCoverageLoading, setEditCoverageLoading] = useState(!!initialClaimId);
  const [preAuthId, setPreAuthId] = useState('');
  const [preAuthSearch, setPreAuthSearch] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [confirmDeleteReason, setConfirmDeleteReason] = useState('');
  const [showValidationErrors, setShowValidationErrors] = useState(false);
  const [autoSaveStatus, setAutoSaveStatus] = useState('idle');
  const [lastSavedAt, setLastSavedAt] = useState(null);
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
  const handleOpenCols = (event) => setAnchorElCols(event.currentTarget);
  const handleCloseCols = () => setAnchorElCols(null);
  const handleToggleColumn = (col) => {
    setVisibleColumns((prev) => ({ ...prev, [col]: !prev[col] }));
  };

  const [encounterType, setEncounterType] = useState('OUTPATIENT');
  const [fullCoverage, setFullCoverage] = useState(false);

  // A batch period is not a service date. Guessing the first day silently
  // creates a financially valid claim on a date the operator never chose.
  const defaultDate = '';

  const [serviceDate, setServiceDate] = useState(defaultDate);

  const memberRef = useRef(null);
  const linesRef = useRef(lines);
  const saveQueueRef = useRef(Promise.resolve());
  const autosaveTimerRef = useRef(null);
  const recoveryCheckedRef = useRef(false);
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

  const rootCategories = useMemo(() => {
    return medicalCategories.filter((c) => !c.parentId);
  }, [medicalCategories]);

  // ── المنطق المالي وتغطية الخدمات (المرحلة 3: Hooks المستخرجة) ─────────────────
  const { recompute } = useCalculationLogic();

  const { fetchCoverage, refetchAllLinesCoverage } = useCoverageLogic({
    policyId,
    member,
    medicalCategories,
    encounterType,
    setLines,
    recompute,
    serviceYear: serviceDate ? new Date(serviceDate).getFullYear() : year || new Date().getFullYear(),
    serviceDate,
    currentClaimId: editingClaimId,
    fullCoverage,
    onCoverageError: (message) => enqueueSnackbar(message, { variant: 'warning' })
  });

  const refetchAllLinesCoverageCallback = useCallback(
    async (newEncounterType, newFullCoverage) => {
      const updated = await refetchAllLinesCoverage(newEncounterType, linesRef.current, newFullCoverage);
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

  const handleOpenCustomServiceDialog = (lineId) => {
    setCustomServiceData({
      categoryId: '',
      serviceName: '',
      serviceCode: '',
      contractPrice: ''
    });
    setCustomServiceError(null);
    setActiveLineIdForCustomService(lineId);
    setCustomServiceDialogOpen(true);
  };

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
    const priceNum = parseFloat(customServiceData.contractPrice);
    if (isNaN(priceNum) || priceNum <= 0) {
      setCustomServiceError('يرجى إدخال سعر تعاقدي صحيح أكبر من صفر');
      return;
    }

    setAddingCustomService(true);
    try {
      if (!providerId || Number.isNaN(Number(providerId))) {
        setCustomServiceError('لا يمكن إضافة خدمة قبل تحديد مقدم الخدمة/العقد بشكل صحيح');
        return;
      }

      const finalCategoryId = customServiceData.categoryId;

      // Auto-generate service code if not provided
      const finalServiceCode = customServiceData.serviceCode.trim() || `SRV-${Date.now().toString().slice(-6)}`;

      const payload = {
        serviceName: customServiceData.serviceName.trim(),
        serviceCode: finalServiceCode,
        medicalCategoryId: Number(finalCategoryId),
        contractPrice: priceNum,
        basePrice: priceNum,
        unit: 'service',
        currency: 'LYD',
        providerId: providerId ? Number(providerId) : null
      };

      // Call the modified backend endpoint, passing the active providerId from search params
      const response = await axiosClient.post(`/provider/my-contract/pricing?providerId=${Number(providerId)}`, payload);
      const createdItem = response.data?.data || response.data;

      const newServiceId = createdItem.medicalServiceId || createdItem.serviceId || createdItem.id;

      const newServiceObject = {
        id: newServiceId,
        pricingItemId: createdItem.pricingItemId || createdItem.id,
        serviceCode: finalServiceCode,
        serviceName: payload.serviceName,
        categoryId: Number(finalCategoryId),
        label: `[${finalServiceCode}] ${payload.serviceName}`,
        contractPrice: priceNum,
        maxContractPrice: priceNum,
        price: priceNum
      };

      // Invalidate queries to refresh contracted services lists
      queryClient.invalidateQueries({ queryKey: ['contracted-services', providerId] });

      // Update the active claim line to select this newly added service
      if (activeLineIdForCustomService) {
        setLines((prev) =>
          prev
            .map((line) => {
              if (line.id !== activeLineIdForCustomService) return line;

              const linePatch = {
                service: newServiceObject,
                serviceName: payload.serviceName,
                serviceCode: finalServiceCode,
                unitPrice: priceNum,
                contractPrice: priceNum,
                maxContractPrice: priceNum
              };

              return {
                ...line,
                ...linePatch
              };
            })
            .map((line, i, arr) => recompute(line, i, arr))
        );
      }

      setCustomServiceDialogOpen(false);
      enqueueSnackbar('تمت إضافة الخدمة وتحديدها بنجاح', { variant: 'success' });
    } catch (err) {
      console.error('Failed to add custom service pricing:', err);
      const apiMessage =
        err?.response?.data?.messageAr ||
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.userMessage ||
        err?.message;
      setCustomServiceError(apiMessage || 'فشل في حفظ الخدمة الجديدة في قائمة أسعار مقدم الخدمة. تأكد من صحة البيانات.');
    } finally {
      setAddingCustomService(false);
    }
  };

  // ── الاستعلامات ──────────────────────────────────────────────────────────
  const { data: employer } = useQuery({
    queryKey: ['employer', employerId],
    queryFn: () => employersService.getById(employerId),
    enabled: !!employerId
  });
  const { data: provider } = useQuery({
    queryKey: ['provider', providerId],
    queryFn: () => providersService.getById(providerId),
    enabled: !!providerId
  });
  const {
    data: entryContext,
    isFetching: loadingEntryContext,
    isError: entryContextError,
    error: entryContextFailure,
    refetch: refetchEntryContext
  } = useQuery({
    queryKey: ['claim-entry-context', member?.id, providerId, employerId, serviceDate],
    queryFn: () => claimsService.getEntryContext({ memberId: member.id, providerId, employerId, serviceDate }),
    enabled: !!member?.id && !!providerId && !!employerId && !!serviceDate,
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

  const { data: batchData, isLoading: loadingBatch } = useQuery({
    queryKey: ['batch-claims-entry', employerId, providerId, month, year, page],
    queryFn: async () => {
      if (!employerId || !providerId || isNaN(month) || isNaN(year)) return null;
      const lastDay = new Date(year, month, 0).getDate();
      return claimsService.list({
        employerId,
        providerId,
        dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
        dateTo: `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`,
        size: 20,
        page,
        sortBy: 'createdAt',
        sortDir: 'desc'
      });
    },
    enabled: !!employerId && !!providerId
  });
  const { data: contractedRaw, isLoading: loadingServices } = useQuery({
    queryKey: ['claim-entry-contract-services', entryContext?.contractId, serviceDate],
    queryFn: () => claimsService.getEntryServices({
      memberId: selectedMemberId,
      providerId: batch?.providerId,
      employerId: batch?.employerId,
      serviceDate,
      size: 500,
      sort: 'serviceName,asc'
    }),
    enabled: !!entryContext?.contractId && !!serviceDate
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
  const { data: summaryData } = useQuery({
    queryKey: ['batch-stats', employerId, providerId, month, year],
    queryFn: () => {
      if (!employerId || !providerId || isNaN(month) || isNaN(year)) return null;
      const lastDay = new Date(year, month, 0).getDate();
      return claimsService.getFinancialSummary({
        employerId,
        providerId,
        dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
        dateTo: `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
      });
    },
    enabled: !!employerId && !!providerId
  });

  const { data: backdatedMonthsSetting } = useQuery({
    queryKey: ['system-setting-backdated-months'],
    queryFn: () =>
      systemSettingsService.getAll().then((settings) => {
        const s = settings?.find((x) => x.settingKey === 'CLAIM_BACKDATED_MONTHS');
        return s ? parseInt(s.settingValue, 10) : 3;
      }),
    staleTime: 5 * 60 * 1000
  });
  const allowedBackdatedMonths = backdatedMonthsSetting ?? 3;

  const isExpiredBatch = useMemo(() => {
    if (!month || !year) return false;
    const now = new Date();
    const currentYM = now.getFullYear() * 12 + now.getMonth();
    const targetYM = year * 12 + (month - 1);
    const diff = currentYM - targetYM;
    if (allowedBackdatedMonths === 0) return diff > 0;
    return diff > allowedBackdatedMonths;
  }, [month, year, allowedBackdatedMonths]);

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
  const financialSummary = entryContext ? {
    annualLimit: entryContext.annualLimit,
    limitConsumedAmount: entryContext.committedAmount,
    reservedAmount: entryContext.reservedAmount,
    actualRemaining: entryContext.actualRemaining,
    reservableAvailable: entryContext.reservableAvailable,
    asOfDate: entryContext.serviceDate,
    readAt: entryContext.balanceReadAt,
    ceilingMode: entryContext.ceilingMode
  } : null;
  // ── Load Existing Claim for Edit ───────────────────────────────────────
  const { data: editingClaim, isLoading: loadingClaim } = useQuery({
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
            label: `${lineCode ? '[' + lineCode + '] ' : ''}${lineName || ''}`,
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
      setIsDirty(false);
      // Signal that edit fields and lines were committed. One dedicated effect
      // recalculates coverage after policy/member are ready as well.
      setEditHydrationVersion((version) => version + 1);
    }
  }, [editingClaim, defaultDate, contractedRaw]);

  const draftPayload = useMemo(
    () => ({
      member,
      diagnosis,
      doctorName,
      complaint,
      notes,
      lines,
      serviceDate,
      preAuthId,
      encounterType,
      fullCoverage,
      applyBenefits,
      isClaimRejected,
      rejectionInput
    }),
    [
      member,
      diagnosis,
      doctorName,
      complaint,
      notes,
      lines,
      serviceDate,
      preAuthId,
      encounterType,
      fullCoverage,
      applyBenefits,
      isClaimRejected,
      rejectionInput
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
      setServiceDate(payload.serviceDate || defaultDate);
      setPreAuthId(payload.preAuthId || '');
      setEncounterType(payload.encounterType || 'OUTPATIENT');
      setFullCoverage(!!payload.fullCoverage);
      setApplyBenefits(payload.applyBenefits ?? true);
      setIsClaimRejected(!!payload.isClaimRejected);
      setRejectionInput(payload.rejectionInput || '');
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
          if (saved?.conflictResolved) {
            enqueueSnackbar('تمت مزامنة المسودة بعد تعارض بسيط', { variant: 'info' });
          }
          setLastSavedAt(new Date());
          setAutoSaveStatus('saved');
        } catch (error) {
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

    recoveryCheckedRef.current = true;

    const runRecoveryCheck = async () => {
      let localDraft = null;
      try {
        const raw = localStorage.getItem(draftStorageKey);
        localDraft = raw ? JSON.parse(raw) : null;
      } catch (_) {
        localDraft = null;
      }

      let serverDraft = null;
      try {
        if (draftBatchId) {
          serverDraft = await claimsService.getDraft(draftBatchId);
        }
      } catch (_) {
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

  const contractedServiceOptionsRaw = useMemo(() => {
    const items = Array.isArray(contractedRaw) ? contractedRaw : contractedRaw?.content || contractedRaw?.items || [];
    return items.map((s) => {
      const code = s.serviceCode || s.code || '';
      const name = s.serviceName || s.name || '';
      const normalizedCategoryId =
        s.categoryId ??
        s.serviceCategoryId ??
        s.medicalCategoryId ??
        s.medicalCategory?.id ??
        s.effectiveCategory?.id ??
        null;
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
      return {
        ...s,
        label: `${code ? '[' + code + '] ' : ''}${name}`,
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
        pricingItemId: s.pricingItemId ?? s.id,
        contractPrice: s.contractPrice || 0,
        maxContractPrice: s.maxContractPrice || s.contractPrice || 0
      };
    });
  }, [contractedRaw]);

  const serviceOptions = useMemo(() => {
    const mappedCodes = new Set(contractedServiceOptionsRaw.map((item) => item.serviceCode).filter(Boolean));


    const generalOptions = [
      {
        id: 'GEN-MEDICATION',
        pricingItemId: null,
        serviceCode: 'GEN-MEDICATION',
        serviceName: 'دواء عام / General Medication',
        label: '[GEN-MEDICATION] دواء عام / General Medication',
        contractPrice: 0,
        maxContractPrice: 0,
        encounterType: 'OUTPATIENT',
        defaultEncounterType: 'OUTPATIENT',
        categoryId: null
      },
      {
        id: 'GEN-MEDICAL-SERVICE',
        pricingItemId: null,
        serviceCode: 'GEN-MEDICAL-SERVICE',
        serviceName: 'خدمة طبية عامة / General Medical Service',
        label: '[GEN-MEDICAL-SERVICE] خدمة طبية عامة / General Medical Service',
        contractPrice: 0,
        maxContractPrice: 0,
        encounterType: 'OUTPATIENT',
        defaultEncounterType: 'OUTPATIENT',
        categoryId: null
      }
    ].filter((item) => !mappedCodes.has(item.serviceCode));


    return [...contractedServiceOptionsRaw, ...generalOptions].filter((item) => isServiceAllowedForClaimContext(item, encounterType));
  }, [contractedServiceOptionsRaw, encounterType]);

  const batchContent = useMemo(
    () => batchData?.data?.items ?? batchData?.items ?? batchData?.data?.content ?? batchData?.content ?? [],
    [batchData]
  );
  const batchTotal = batchData?.data?.total ?? batchData?.total ?? batchData?.data?.totalElements ?? batchData?.totalElements ?? 0;

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
          refetchAllLinesCoverage(encounterType, linesRef.current).then((updated) => {
            if (updated) setLines(updated);
          });
        }, 600);
      }
    },
    [recompute, policyId, member?.id, refetchAllLinesCoverage, encounterType]
  );

  const handleServiceChange = useCallback(
    async (idx, val) => {
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

      const isDuplicate =
        !isGeneralService &&
        lines.some((l, i) => {
          if (i === idx) return false;
          const existingName = l.serviceName || l.service?.serviceName || l.service?.name;
          return newName && existingName && existingName === newName;
        });

      if (isDuplicate) {
        enqueueSnackbar('هذه الخدمة مضافة بالفعل في بند آخر', { variant: 'error' });
        return;
      }

      let cov = failedCoverageResult('الخدمة النصية غير مرتبطة بخدمة معتمدة ولا يمكن احتساب تغطيتها');
      if (!isFreeText) {
        cov = await fetchCoverage(svc, encounterType);
        if (cov?.__stale) {
          return;
        }
      }

      const price = svc?.contractPrice ?? 0;
      const maxPrice = svc?.maxContractPrice ?? price;
      const resolvedCategoryId =
        svc.categoryId ??
        svc.serviceCategoryId ??
        svc.medicalCategoryId ??
        svc.medicalCategory?.id ??
        svc.effectiveCategory?.id ??
        null;
      const resolvedCategoryName =
        svc.categoryName ??
        svc.serviceCategoryName ??
        svc.medicalCategoryName ??
        svc.medicalCategory?.nameAr ??
        svc.medicalCategory?.name ??
        svc.effectiveCategory?.nameAr ??
        svc.effectiveCategory?.name ??
        null;
      updateLine(idx, {
        service: svc,
        medicalServiceId: svc.medicalServiceId || null,
        pricingItemId: svc.pricingItemId || null,
        serviceName: svc.serviceName || (typeof val === 'string' ? val : ''),
        serviceCode: svc.serviceCode || '',
        medicalCategoryId: resolvedCategoryId,
        medicalCategoryName: resolvedCategoryName,
        serviceCategoryId: resolvedCategoryId,
        serviceCategoryName: resolvedCategoryName,
        unitPrice: price,
        contractPrice: maxPrice,
        maxContractPrice: maxPrice,
        ...cov
      });
    },
    [fetchCoverage, updateLine, lines, enqueueSnackbar, encounterType]
  );

  useEffect(() => {
    if (!policyId || !member?.id) return;
    if (editingClaimId) return;

    // Force refetch usage/limits for ALL lines when member or policy changes
    refetchAllLinesCoverage(encounterType, linesRef.current).then((updated) => {
      if (updated) setLines(updated);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [policyId, member?.id, encounterType]);

  // Edit hydration barrier: run only after claim, policy, member, context, date
  // and mapped lines have all reached committed React state.
  useEffect(() => {
    if (!editingClaimId || !editHydrationVersion || !policyId || !member?.id) return;
    if (!linesRef.current.some((line) => line.service)) return;

    let active = true;
    setEditCoverageLoading(true);
    Promise.resolve(refetchCoverageOnEditRef.current(encounterType, fullCoverage)).finally(() => {
      if (active) setEditCoverageLoading(false);
    });
    return () => {
      active = false;
    };
  }, [editingClaimId, editHydrationVersion, policyId, member?.id, encounterType, serviceDate, fullCoverage]);

  const addLine = useCallback(() => {
    setLines((p) => [...p, newLine()]);
    setIsDirty(true);
  }, []);
  const removeLine = useCallback((idx) => {
    const targetLine = lines[idx];
    const serviceLabel = targetLine?.serviceName || targetLine?.service?.serviceName || targetLine?.serviceCode || `البند رقم ${idx + 1}`;
    triggerConfirm('تأكيد حذف البند', `هل تريد حذف بند الخدمة «${serviceLabel}»؟ سيتم إخراجه من حساب المطالبة.`, () => {
      setLines((p) => (p.length === 1 ? [newLine()] : p.filter((_, i) => i !== idx)));
      setIsDirty(true);
    });
  }, [lines]);

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

    updateLine(idx, {
      medicalCategoryId: selected.id,
      serviceCategoryId: selected.id,
      categoryId: selected.id,
      medicalCategoryCode: selected.code,
      medicalCategoryName: selected.name,
      serviceCategoryName: selected.name,
      coveragePending: true
    });

    await sendLineToMedicalDictionary(idx, selected.id);
    closeClassificationReviewDialog();
    enqueueSnackbar('تم اعتماد تصنيف البند لهذه المطالبة فقط، وسُجل كاقتراح دائم ينتظر اعتماد رئيس القسم', { variant: 'success' });
  }, [
    classificationReview.lineIndex,
    classificationReview.selectedCategoryId,
    closeClassificationReviewDialog,
    enqueueSnackbar,
    resolveCategoryLabel,
    sendLineToMedicalDictionary,
    updateLine
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
  const activeClassificationCategory = resolveCategoryLabel(classificationReview.selectedCategoryId || resolveLineCategoryId(activeClassificationLine));
  const currentClassificationCategory = resolveCategoryLabel(resolveLineCategoryId(activeClassificationLine));

  const categoriesForReview = useMemo(
    () => medicalCategories.filter((category) => category.active !== false && category.deleted !== true),
    [medicalCategories]
  );

  const incompatibleContextLines = useMemo(
    () =>
      lines
        .map((line, index) => ({ line, index }))
        .filter(({ line }) => line?.service && !isServiceAllowedForClaimContext(line.service, encounterType)),
    [lines, encounterType]
  );

  const contractedContextCounts = useMemo(() => {
    return contractedServiceOptionsRaw.reduce(
      (acc, service) => {
        acc.total += 1;
        const serviceContext = getServiceContext(service);
        if (serviceContext === 'INPATIENT') {
          acc.inpatient += 1;
        } else if (serviceContext === 'OUTPATIENT') {
          acc.outpatient += 1;
        } else {
          acc.any += 1;
        }
        return acc;
      },
      { total: 0, outpatient: 0, inpatient: 0, any: 0 }
    );
  }, [contractedServiceOptionsRaw]);

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

  const resetForm = useCallback(() => {
    setMember(null);
    setMemberInput('');
    setDiagnosis('');
    setDoctorName('');
    setComplaint('');
    setNotes('');
    setLines([newLine()]);
    setApplyBenefits(true);
    setIsDirty(false);
    setServiceDate(defaultDate);
    setPreAuthId('');
    setEncounterType('OUTPATIENT');
    setFullCoverage(false);
    setIsClaimRejected(false);
    setRejectionInput('');
    setAttachments([]);
    // FIX: resetForm must also clear the editing state
    setEditingClaimId(null);
    setEditCoverageLoading(false);
    setTimeout(() => memberRef.current?.focus(), 120);
  }, [defaultDate]);

  const restoreServerDraft = useCallback(() => {
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

  const dismissRecovery = useCallback(() => {
    setRecoveryDialog({ open: false, serverDraft: null, localDraft: null });
  }, []);

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
        triggerConfirm('تأكيد رفض البند', 'هل تريد رفض هذا البند بالكامل (حصة الشركة ستصبح صفراً)؟', doUpdate);
      } else {
        doUpdate();
      }
      return;
    }
  };

  const handleSave = async (resetAfter = false) => {
    if (isSavingRef.current) return;

    if (member?.id && (!serviceDate || loadingEntryContext || entryContextError || !entryContext)) {
      enqueueSnackbar('لا يمكن الحفظ قبل نجاح التحقق من الوثيقة والعقد والرصيد في تاريخ الخدمة.', { variant: 'error' });
      return;
    }

    // التحقق من الحقول المطلوبة بشكل احترافي
    const missingFields = [];
    if (!member) missingFields.push('المستفيد');
    if (!diagnosis?.trim()) missingFields.push('التشخيص الطبي');
    if (!doctorName?.trim()) missingFields.push('اسم الطبيب');
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
      return;
    }

    const invalidQuantityLines = invalidQuantityLineNumbers(lines);
    if (invalidQuantityLines.length > 0) {
      enqueueSnackbar(
        `الكمية يجب أن تكون عدداً صحيحاً أكبر من صفر في البنود: ${invalidQuantityLines.join('، ')}`,
        { variant: 'error', autoHideDuration: 6000 }
      );
      return;
    }

    if (incompatibleContextLines.length > 0) {
      const lineNumbers = incompatibleContextLines.map(({ index }) => index + 1).join('، ');
      enqueueSnackbar(
        `لا يمكن الحفظ: الخدمات في البنود ${lineNumbers} لا تتوافق مع سياق المطالبة الحالي. احذفها أو أعد اختيار خدمات صالحة لهذا السياق.`,
        { variant: 'error', autoHideDuration: 7000 }
      );
      return;
    }

    if (!isClaimRejected && lines.some((line) => (line.service || line.serviceName) && !line.rejected && line.coveragePending)) {
      enqueueSnackbar('لا يمكن الحفظ أثناء انتظار قرار محرك التغطية. انتظر اكتمال تحديث جميع البنود.', {
        variant: 'warning',
        autoHideDuration: 5000
      });
      return;
    }

    const uncoveredLines = lines.filter(
      (line) => (line.service || line.serviceName) && !line.rejected && (line.notCovered || (Number(line.coveragePercent) || 0) <= 0)
    );
    if (!isClaimRejected && uncoveredLines.length > 0) {
      enqueueSnackbar('لا يمكن اعتماد مطالبة تحتوي خدمات غير مغطاة. غيّر سياق المطالبة أو ارفض البند/المطالبة بسبب واضح.', {
        variant: 'error',
        autoHideDuration: 7000
      });
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

      // المرحلة 2.1: التحقق من مطابقة التاريخ لشهر الدفعة الحالي
      const d = new Date(actualDate);
      if (d.getMonth() + 1 !== month || d.getFullYear() !== year) {
        enqueueSnackbar(
          `⚠️ تاريخ الخدمة (${actualDate}) لا يتبع لشهر الدفعة الحالي (${MONTHS_AR[month - 1]} ${year}). يرجى التأكد من التاريخ أو الانتقال لدفعة الشهر الصحيح.`,
          { variant: 'warning', autoHideDuration: 8000 }
        );
        setSaving(false);
        isSavingRef.current = false;
        return;
      }

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

      // الحالة REJECTED فقط إذا:
      // 1. المستخدم ضغط "رفض المطالبة" صراحة (isClaimRejected)
      // 2. جميع البنود مرفوضة يدوياً (allLinesManuallyRejected)
      // ⚠️ الخصومات الآلية (تجاوز سعر/سقف) لا تجعل المطالبة "مرفوضة" — تبقى "معتمدة" مع مبالغ مرفوضة
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
        // Approval is a server-owned transition. The browser may explicitly
        // request rejection, but a positive claim must enter through DRAFT so
        // ClaimService finalizes the canonical snapshot and state transition.
        status: effectivelyRejected ? 'REJECTED' : null,
        rejectionReason: effectivelyRejected ? effectiveRejectionReason : null,
        preAuthorizationId: preAuthId ? parseInt(preAuthId) : null,
        encounterType,
        fullCoverage: fullCoverage,
        // لا ترسل صفوف الإدخال الفارغة التي يضيفها المستخدم ولم يختر لها خدمة.
        // التحقق أعلاه يعتمد activeLines، ويجب أن يستخدم الحفظ المصدر نفسه حتى
        // لا تصل أسطر بلا medicalServiceId أو pricingItemId إلى الخادم.
        lines: activeLines.map((l) => ({
          id: typeof l.id === 'number' ? l.id : null,
          medicalServiceId: l.medicalServiceId ?? l.service?.medicalServiceId ?? null,
          pricingItemId: l.pricingItemId ?? l.service?.pricingItemId ?? null,
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
          refusedAmount: parseFloat(l.refusedAmount) || 0,
          rejected: isClaimRejected ? true : l.rejected || false,
          rejectionReason: isClaimRejected ? effectiveRejectionReason : l.rejectionReason || null,
          manualRefusedAmount: isClaimRejected ? 0 : parseFloat(l.manualRefusedAmount) || 0
        }))
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
        const claimResponse = await claimsService.createDirectEntry(parseInt(employerId), claimData);
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
      } catch (_) {
        // Non-blocking cleanup
      }
      try {
        localStorage.removeItem(draftStorageKey);
      } catch (_) {
        // ignore local cleanup errors
      }
      setDraftVersion(null);
      setAutoSaveStatus('idle');
      setLastSavedAt(null);

      invalidateBatchData();
      setPage(0);
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

  // ── طباعة وتصدير ─────────────────────────────────────────────────────────
  const handlePrint = () => window.print();

  const handleExport = () => {
    if (!batchContent.length) {
      enqueueSnackbar('لا توجد بيانات للتصدير', { variant: 'warning' });
      return;
    }
    const headers = ['#', 'المؤمن عليه', 'التاريخ', 'المبلغ المطلوب', 'المبلغ المعتمد', 'الحالة'];
    const rows = batchContent.map((c) => [
      c.id,
      c.memberName,
      c.serviceDate,
      c.requestedAmount?.toFixed(2) ?? '0.00',
      c.approvedAmount?.toFixed(2) ?? '0.00',
      c.status
    ]);
    const csvRows = [headers, ...rows].map((r) => r.map((v) => `"${v ?? ''}"`).join(','));
    const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backlog_claims_${monthLabel}_${year}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // ── حذف مطالبة من الشريط الجانبي ─────────────────────────────────────────
  const handleSwitchClaim = useCallback(
    (claimId) => {
      if (isDirty) {
        if (!window.confirm('يوجد تعديلات غير محفوظة. هل تريد الانتقال بدون حفظ؟')) return;
      }
      if (claimId === null) resetForm();
      setEditingClaimId(claimId);
    },
    [isDirty, resetForm]
  );

  const handleDeleteClaim = async (claimId, e) => {
    e.stopPropagation();
    setConfirmDeleteId(claimId);
    setConfirmDeleteReason(''); // Reset reason
  };

  const confirmDeleteClaim = async () => {
    const claimId = confirmDeleteId;
    if (!claimId) return;
    try {
      await claimsService.remove(claimId, confirmDeleteReason || 'تم الإلغاء');
      enqueueSnackbar(`✅ تم إلغاء المطالبة #${claimId}`, { variant: 'success' });
      setConfirmDeleteId(null);
      invalidateBatchData();
      // ✅ FIX: Restore ceiling in current form after deletion
      if (member?.id && policyId) {
        setTimeout(() => refetchCoverageOnEditRef.current(encounterType), 200);
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
          subtitle={`${t('providers.singular')}: ${provider?.name || '...'} | رقم العقد: ${entryContext?.contractNumber || 'بانتظار التحقق'} | المؤمن عليه: ${member?.fullName || '...'} (${member?.cardNumber || '—'})`}
          icon={<ReceiptIcon />}
          actions={
            <Stack direction="row" spacing={1} alignItems="center">
              {autoSaveStatus === 'saving' && (
                <Typography variant="caption" color="warning.main" fontWeight={600}>
                  Saving...
                </Typography>
              )}
              {autoSaveStatus === 'saved' && (
                <Typography variant="caption" color="success.main" fontWeight={600}>
                  Saved{lastSavedAt ? ' just now' : ''}
                </Typography>
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

            {/* ── حقول الرأس (مكون منفصل) ── */}
            <Box sx={{ flexShrink: 0, px: '1.25rem', py: '0.75rem', bgcolor: 'background.paper' }}>
              <ClaimHeaderFields
                member={member}
                setMember={setMember}
                memberOptions={memberOptions}
                searchingMember={searchingMember}
                memberSearchError={memberSearchError}
                onRetryMemberSearch={retryMemberSearch}
                setMemberInput={setMemberInput}
                memberRef={memberRef}
                diagnosis={diagnosis}
                setDiagnosis={setDiagnosis}
                doctorName={doctorName}
                setDoctorName={setDoctorName}
                encounterType={encounterType}
                setEncounterType={setEncounterType}
                fullCoverage={fullCoverage}
                setFullCoverage={setFullCoverage}
                onRefetchAll={refetchAllLinesCoverageCallback}
                preAuthResults={preAuthResults}
                searchingPreAuth={searchingPreAuth}
                preAuthId={preAuthId}
                setPreAuthId={setPreAuthId}
                setPreAuthSearch={setPreAuthSearch}
                serviceDate={serviceDate}
                setServiceDate={setServiceDate}
                setIsDirty={setIsDirty}
                financialSummary={financialSummary}
                currentCompanyCommitment={totals.company}
                editingApprovedAmount={editingClaim?.approvedAmount || 0}
                t={t}
                showValidationErrors={showValidationErrors}
              />
              <Box sx={{ mt: 1 }}>
                <ClaimEntryReadinessAlert
                  member={member}
                  serviceDate={serviceDate}
                  loading={loadingEntryContext}
                  context={entryContext}
                  error={entryContextFailure}
                  onRetry={refetchEntryContext}
                />
              </Box>
            </Box>

            <Divider />

            {incompatibleContextLines.length > 0 && (
              <Alert
                severity="error"
                sx={{
                  mx: '1.25rem',
                  mt: 1,
                  mb: 0,
                  alignItems: 'center',
                  '& .MuiAlert-message': { width: '100%', textAlign: 'right' }
                }}
              >
                لا يمكن الحفظ: الخدمات في البنود{' '}
                {incompatibleContextLines.map(({ index }) => index + 1).join('، ')} لا تتوافق مع سياق المطالبة الحالي.
                احذفها أو أعد اختيار خدمات صالحة لهذا السياق.
              </Alert>
            )}

            <Box
              sx={{
                flexShrink: 0,
                px: '1.25rem',
                py: 0.75,
                bgcolor: alpha(theme.palette.primary.main, 0.04),
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                borderBottom: `1px solid ${theme.palette.divider}`
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="subtitle2" fontWeight={600} color="primary" sx={{ fontSize: '0.85rem' }}>
                  {t('claimEntry.serviceLines')}
                </Typography>
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${lines.length} بند`}
                  sx={{ fontWeight: 400, fontSize: '0.75rem', borderColor: alpha(theme.palette.primary.main, 0.3) }}
                />
                <Chip
                  size="small"
                  icon={<MedicalServicesIcon sx={{ fontSize: '0.95rem !important' }} />}
                  label={`إجمالي الخدمات ${contractedContextCounts.total}`}
                  sx={{
                    fontWeight: 800,
                    fontSize: '0.72rem',
                    color: theme.palette.primary.dark,
                    bgcolor: alpha(theme.palette.primary.main, 0.1),
                    borderColor: alpha(theme.palette.primary.main, 0.4),
                    '& .MuiChip-icon': { color: theme.palette.primary.main }
                  }}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  icon={<OutpatientIcon sx={{ fontSize: '0.95rem !important' }} />}
                  label={`عيادات خارجية ${contractedContextCounts.outpatient}`}
                  sx={{
                    fontWeight: 700,
                    fontSize: '0.72rem',
                    color: theme.palette.success.dark,
                    bgcolor: alpha(theme.palette.success.main, 0.1),
                    borderColor: alpha(theme.palette.success.main, 0.35),
                    '& .MuiChip-icon': { color: theme.palette.success.main }
                  }}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  icon={<InpatientIcon sx={{ fontSize: '0.95rem !important' }} />}
                  label={`إيواء ${contractedContextCounts.inpatient}`}
                  sx={{
                    fontWeight: 700,
                    fontSize: '0.72rem',
                    color: theme.palette.info.dark,
                    bgcolor: alpha(theme.palette.info.main, 0.1),
                    borderColor: alpha(theme.palette.info.main, 0.35),
                    '& .MuiChip-icon': { color: theme.palette.info.main }
                  }}
                  variant="outlined"
                />
                {contractedContextCounts.any > 0 && (
                  <Chip
                    size="small"
                    label={`تغطية عامة ${contractedContextCounts.any}`}
                    sx={{
                      fontWeight: 700,
                      fontSize: '0.72rem',
                      color: theme.palette.warning.dark,
                      bgcolor: alpha(theme.palette.warning.main, 0.12),
                      borderColor: alpha(theme.palette.warning.main, 0.4)
                    }}
                    variant="outlined"
                  />
                )}
              </Stack>
              <Box>
                <Tooltip title="إظهار/إخفاء الأعمدة">
                  <IconButton size="small" onClick={handleOpenCols}>
                    <ViewColumnIcon fontSize="small" color="primary" />
                  </IconButton>
                </Tooltip>
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
                      <ListItemText primary="حصة الشركة" />
                    </MenuItem>
                  )}
                  <MenuItem onClick={() => handleToggleColumn('patientShare')}>
                    <ListItemIcon>
                      <Checkbox checked={visibleColumns.patientShare} size="small" />
                    </ListItemIcon>
                    <ListItemText primary="حصة المشترك" />
                  </MenuItem>
                </Menu>
              </Box>
            </Box>

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
              <Box
                role="status"
                aria-live="polite"
                sx={{
                  position: 'absolute',
                  inset: 0,
                  zIndex: 5,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: alpha(theme.palette.background.paper, 0.94),
                  backdropFilter: 'blur(1px)'
                }}
              >
                <Stack spacing={1.25} alignItems="center">
                  <CircularProgress size={30} thickness={4} />
                  <Typography variant="subtitle2" fontWeight={700} color="text.primary">
                    جارٍ تجهيز الحساب المالي للمطالبة
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    يتم تحديث التغطية والسقوف والأرصدة قبل عرض البنود
                  </Typography>
                </Stack>
              </Box>
              )}

              <Box
                aria-hidden={editCoverageLoading ? 'true' : undefined}
                sx={{
                  flex: 1,
                  minHeight: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  opacity: editCoverageLoading ? 0 : 1,
                  pointerEvents: editCoverageLoading ? 'none' : 'auto',
                  transition: 'opacity 120ms ease-out'
                }}
              >
                {!editCoverageLoading && lines.some((line) => line.coveragePending && !line.rejected) && (
                  <Alert
                    severity="info"
                    role="status"
                    aria-live="polite"
                    sx={{
                      position: 'absolute',
                      top: 8,
                      left: 12,
                      zIndex: 4,
                      width: 'auto',
                      maxWidth: 'min(28rem, calc(100% - 24px))',
                      py: 0,
                      px: 0.75,
                      borderRadius: 1.5,
                      boxShadow: 2,
                      bgcolor: alpha(theme.palette.info.light, 0.96),
                      pointerEvents: 'none',
                      '& .MuiAlert-icon': { py: 0.5, mr: 0.75 },
                      '& .MuiAlert-message': { py: 0.5, fontSize: '0.75rem', whiteSpace: 'nowrap' }
                    }}
                  >
                    جارٍ تحديث الحساب المالي…
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
                    {visibleColumns.companyShare && (
                      <TH align="center" w={105}>
                        حصة الشركة
                      </TH>
                    )}
                    {visibleColumns.patientShare && (
                      <TH align="center" w={105}>
                        حصة المشترك
                      </TH>
                    )}
                    <TH align="center" w={80}>
                      الإجمالي
                    </TH>
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
                      updateLine={updateLine}
                      handleServiceChange={handleServiceChange}
                      removeLine={removeLine}
                      openRejectDialog={openRejectDialog}
                      policyInfo={policyInfo}
                      visibleColumns={visibleColumns}
                      triggerConfirm={triggerConfirm}
                      onOpenClassificationReview={openClassificationReviewDialog}
                    />
                  ))}
                  <TableRow>
                    <TableCell colSpan={12} sx={{ py: 0.5, borderRight: 'none' }}>
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
              coveragePending={lines.some((line) => (line.service || line.serviceName) && !line.rejected && line.coveragePending)}
                financialDataUnavailable={Boolean(member?.id) && (
                  !serviceDate || loadingEntryContext || entryContextError || !entryContext
                )}
              hasUncoveredLines={lines.some(
                (line) => (line.service || line.serviceName) && !line.rejected && (line.notCovered || (Number(line.coveragePercent) || 0) <= 0)
              )}
              setIsClaimRejected={setIsClaimRejected}
              setIsDirty={setIsDirty}
              setRejectionInput={setRejectionInput}
              openRejectDialog={openRejectDialog}
              totals={totals}
              theme={theme}
              lines={lines}
              t={t}
              visibleColumns={visibleColumns}
                />
              </Box>
            </Box>
          </Paper>
        </Box>
      </Box>

      <Dialog open={classificationReview.open} onClose={closeClassificationReviewDialog} fullWidth maxWidth="sm" dir="rtl">
        <DialogTitle sx={{ fontWeight: 900 }}>مراجعة تصنيف بند الخدمة</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            <Alert severity="info">
              هذا القرار يخص التصنيف التأميني للبند فقط. الحساب المالي النهائي يبقى من اختصاص محرك التغطية بعد إعادة الحساب.
            </Alert>

            <Box>
              <Typography variant="caption" color="text.secondary">
                الخدمة
              </Typography>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {activeClassificationLine?.serviceName || activeClassificationLine?.service?.serviceName || activeClassificationLine?.service?.name || '-'}
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
              value={categoriesForReview.find((category) => String(category.id) === String(classificationReview.selectedCategoryId)) || null}
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
                const query = state.inputValue.trim().toLowerCase();
                if (!query) return options;
                return options.filter((category) =>
                  [category.code, category.name, category.nameAr, category.nameEn]
                    .filter(Boolean)
                    .some((value) => String(value).toLowerCase().includes(query))
                );
              }}
              renderInput={(params) => <TextField {...params} label="تغيير التصنيف عند الحاجة" placeholder="ابحث باسم التصنيف أو الكود..." />}
            />

            {activeClassificationCategory && (
              <Alert severity="success" variant="outlined">
                سيتم اعتماد: {activeClassificationCategory.name}
                {activeClassificationCategory.code ? ` (${activeClassificationCategory.code})` : ''}، وتسجيل القرار على سطر المطالبة ثم إعادة احتساب التغطية.
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
            <Button variant="contained" color="primary" onClick={approveClassificationForLine} disabled={!classificationReview.selectedCategoryId}>
              اعتماد التصنيف
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
        onFieldChange={handleCustomServiceDataChange}
        onClearError={() => setCustomServiceError(null)}
        onSubmit={handleSubmitCustomService}
      />
    </Box>
  );
}

