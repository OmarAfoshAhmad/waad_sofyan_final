/**
 * صفحة إدخال الدفعة — تخطيط RTL يملأ الشاشة
 * ✅ الجدول والفورم من اليمين لليسار
 * ✅ زر الحفظ مرئي دون scroll
 * ✅ كل النصوص من ar.js (لا hardcode)
 */
import { useState, useMemo, useRef, useCallback, useEffect, Fragment } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Box, Stack, Typography, Button, TextField, Autocomplete,
    Divider, CircularProgress, IconButton, Table, TableBody,
    TableCell, TableContainer, TableHead, TableRow, Chip, Paper,
    Checkbox, FormControlLabel, Radio, RadioGroup,
    Tooltip, alpha, TableFooter,
    InputAdornment, Alert, Dialog, DialogTitle, DialogContent,
    DialogActions, Pagination, Menu, MenuItem, ListItemIcon, ListItemText
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
    Save as SaveIcon, Add as AddIcon, Delete as DeleteIcon,
    Receipt as ReceiptIcon, CheckCircle as DoneIcon,
    ArrowBack as BackIcon, Close as DiscardIcon, History as HistoryIcon,
    Search as SearchIcon, LocalPrintshop as PrintIcon,
    FileDownload as FileDownloadIcon, WarningAmber as WarningIcon,
    VerifiedUser as PolicyIcon, Info as InfoIcon, Block as RejectIcon,
    Cancel as CancelIcon, AttachFile as AttachFileIcon,
    Lock as LockIcon, AddCircleOutline as AddReasonIcon,
    ViewColumn as ViewColumnIcon,
    Edit as EditIcon, Check as CheckIcon, ExpandMore as ExpandMoreIcon
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
import preApprovalsService from 'services/api/pre-approvals.service';
import visitsService from 'services/api/visits.service';
import benefitPoliciesService from 'services/api/benefit-policies.service';
import * as medicalCategoriesService from 'services/api/medical-categories.service';
import providerContractsService from 'services/api/provider-contracts.service';
import claimBatchesService from 'services/api/claim-batches.service';
import { claimRejectionReasonsService } from 'services/api/claim-rejection-reasons.service';
import systemSettingsService from 'services/api/systemSettings.service';
import { normalizeApiError, runWithRetry } from 'utils/api-error';

import { useCalculationLogic } from './hooks/useCalculationLogic';
import { useCoverageLogic } from './hooks/useCoverageLogic';


import { ClaimHeaderFields } from './components/ClaimHeaderFields';
import { ClaimLineRow } from './components/ClaimLineRow';
import { ClaimTotalsFooter } from './components/ClaimTotalsFooter';

// ── أسماء الشهور ─────────────────────────────────────────────────────────────
const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const newLine = () => ({
    id: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15),
    service: null, serviceName: '', serviceCode: '',
    quantity: 1, unitPrice: 0, contractPrice: 0, byCompany: 0, byEmployee: 0,
    refusalTypes: '', total: 0, coveragePercent: null,
    requiresPreApproval: false, notCovered: false,
    rejected: false, rejectionReason: '',
    manualRefusedAmount: 0,
    oldRejected: 0
});

const hasMeaningfulDraftData = (draft) => {
    if (!draft) return false;
    if (draft.member?.id) return true;
    if ((draft.diagnosis || '').trim()) return true;
    if ((draft.complaint || '').trim()) return true;
    if ((draft.notes || '').trim()) return true;
    return Array.isArray(draft.lines) && draft.lines.some((l) => (l?.serviceName || l?.serviceCode || l?.service));
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
        <TableCell align={align} sx={{
            bgcolor: theme.palette.mode === 'dark' ? theme.palette.grey[900] : '#f8f9fa',
            color: theme.palette.primary.dark,
            fontWeight: 700,
            fontSize: '0.8rem', py: 1, px: '0.75rem', whiteSpace: 'nowrap',
            borderBottom: `2px solid ${alpha(theme.palette.primary.main, 0.3)}`,
            borderRight: `1px solid ${alpha(theme.palette.primary.main, 0.1)}`,
            '&:last-child': { borderRight: 'none' },
            position: 'sticky', top: 0, zIndex: 10,
            ...(w && { width: w, minWidth: w }),
            ...sxOver
        }}>
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
    const [complaint, setComplaint] = useState('');
    const [applyBenefits, setApplyBenefits] = useState(true);
    const [notes, setNotes] = useState('');
    const [lines, setLines] = useState([newLine()]);
    const [saving, setSaving] = useState(false);
    const [isDirty, setIsDirty] = useState(false);
    const [policyId, setPolicyId] = useState(null);
    const [policyInfo, setPolicyInfo] = useState(null);
    const [memberFinancialSummary, setMemberFinancialSummary] = useState(null);

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

    // Generic confirmation dialog
    const [actionConfirm, setActionConfirm] = useState({ open: false, title: '', message: '', onConfirm: null });
    const closeActionConfirm = () => setActionConfirm(prev => ({ ...prev, open: false }));
    const triggerConfirm = (title, message, onConfirm) => setActionConfirm({ open: true, title, message, onConfirm });

    // Column Visibility State (Clutter Reduction)
    const [visibleColumns, setVisibleColumns] = useState({
        coverage: true,
        benefitLimit: true,
        remainingLimit: true,
        refused: true,
        companyShare: true,
        patientShare: true
    });
    const [anchorElCols, setAnchorElCols] = useState(null);
    const handleOpenCols = (event) => setAnchorElCols(event.currentTarget);
    const handleCloseCols = () => setAnchorElCols(null);
    const handleToggleColumn = (col) => {
        setVisibleColumns(prev => ({ ...prev, [col]: !prev[col] }));
    };


    // ✅ Claim Category Context (Manual Rule Selection)
    const [manualCategoryEnabled, setManualCategoryEnabled] = useState(true);
    const [primaryCategoryCode, setPrimaryCategoryCode] = useState('CAT-OP');
    const [fullCoverage, setFullCoverage] = useState(false);

    const defaultDate = useMemo(
        () => (month && year) ? `${year}-${String(month).padStart(2, '0')}-01` : new Date().toISOString().split('T')[0],
        [month, year]
    );

    const [serviceDate, setServiceDate] = useState(defaultDate);

    const memberRef = useRef(null);
    const linesRef = useRef(lines);
    const saveQueueRef = useRef(Promise.resolve());
    const autosaveTimerRef = useRef(null);
    const recoveryCheckedRef = useRef(false);
    const skipAutosaveRef = useRef(false);

    const draftStorageKey = useMemo(() =>
        `claim-draft:${employerId || 'none'}:${providerId || 'none'}:${year || 'none'}:${month || 'none'}`,
        [employerId, providerId, year, month]
    );

    // Keep linesRef in sync
    useEffect(() => {
        linesRef.current = lines;
    }, [lines]);

    // ── الاستعلامات الأساسية اللازمة للمنطق ──────────────────────────────────────
    const { data: rootCategories } = useQuery({
        queryKey: ['medical-categories-root'],
        queryFn: () => medicalCategoriesService.getAllMedicalCategories(),
        select: (data) => data.filter(c => !c.parentId),
        staleTime: Infinity
    });

    // ── المنطق المالي وتغطية الخدمات (المرحلة 3: Hooks المستخرجة) ─────────────────
    const { recompute } = useCalculationLogic({ applyBenefits, policyInfo });

    const { fetchCoverage, refetchAllLinesCoverage } = useCoverageLogic({
        policyId, policyInfo, member, applyBenefits, rootCategories, primaryCategoryCode,
        setLines, recompute,
        serviceYear: serviceDate ? new Date(serviceDate).getFullYear() : (year || new Date().getFullYear()),
        serviceDate,
        currentClaimId: editingClaimId,
        fullCoverage,
        onCoverageError: (message) => enqueueSnackbar(message, { variant: 'warning' })
    });

    const refetchAllLinesCoverageCallback = useCallback(async (newCategoryCode, newFullCoverage) => {
        const updated = await refetchAllLinesCoverage(newCategoryCode, linesRef.current, newFullCoverage);
        if (updated) setLines(updated);
    }, [refetchAllLinesCoverage]);

    // ✅ FIX: Ref that always points to the LATEST refetchAllLinesCoverageCallback
    // This prevents stale-closure bugs in setTimeout calls
    const refetchCoverageOnEditRef = useRef(refetchAllLinesCoverageCallback);
    useEffect(() => {
        refetchCoverageOnEditRef.current = refetchAllLinesCoverageCallback;
    }, [refetchAllLinesCoverageCallback]);

    const isSavingRef = useRef(false);

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
    const { data: currentBatch, isLoading: loadingBatchMeta, error: batchError } = useQuery({
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
                employerId, providerId,
                dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
                dateTo: `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`,
                size: 20, page, sortBy: 'createdAt', sortDir: 'desc'
            });
        },
        enabled: !!employerId && !!providerId
    });
    const { data: contractedRaw, isLoading: loadingServices } = useQuery({
        queryKey: ['contracted-services', providerId],
        queryFn: () => providerContractsService.getAllContractedServices(providerId),
        enabled: !!providerId
    });
    const { data: activeContract, isLoading: loadingContract } = useQuery({
        queryKey: ['provider-active-contract', providerId],
        queryFn: () => providerContractsService.getActiveContractByProvider(providerId),
        enabled: !!providerId
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
        queryFn: () => runWithRetry(() => unifiedMembersService.unifiedSearch(normalizedMemberSearchValue), { maxRetries: 1 }),
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
        queryFn: () => systemSettingsService.getAll().then(settings => {
            const s = settings?.find(x => x.settingKey === 'CLAIM_BACKDATED_MONTHS');
            return s ? parseInt(s.settingValue, 10) : 3;
        }),
        staleTime: 5 * 60 * 1000,
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

    const { data: preAuthResults, isFetching: searchingPreAuth } = useQuery({
        queryKey: ['preauth-search', preAuthSearch, member?.id],
        queryFn: () => preApprovalsService.search({ q: preAuthSearch, size: 20 }),
        enabled: preAuthSearch.length >= 1,
        staleTime: 5000
    });


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

    // الوثيقة التأمينية والمنافع (PHASE 5.6 - Decoupled from Member)
    useEffect(() => {
        if (!employerId) {
            setPolicyId(null);
            setPolicyInfo(null);
            return;
        }
        // Load the effective policy for this employer as soon as we have the ID
        // This ensures the "Document" context is set before searching for members
        benefitPoliciesService.getEffectiveBenefitPolicy(employerId)
            .then(p => {
                if (p) {
                    setPolicyId(p.id);
                    setPolicyInfo(p);
                } else {
                    setPolicyId(null);
                    setPolicyInfo(null);
                }
            })
            .catch(() => {
                setPolicyId(null);
                setPolicyInfo(null);
            });
    }, [employerId]);

    // ── Member Financial Summary (PHASE 1 - Single Source of Truth) ──────
    const { data: financialSummary, isFetching: loadingSummary, refetch: refetchFinancialSummary } = useQuery({
        queryKey: ['member-financial-summary', member?.id],
        queryFn: () => unifiedMembersService.getFinancialSummary(member.id),
        enabled: !!member?.id,
        staleTime: 30000 // 30 seconds
    });

    useEffect(() => {
        if (financialSummary) {
            setMemberFinancialSummary(financialSummary);
        }
    }, [financialSummary]);

    // ── Load Existing Claim for Edit ───────────────────────────────────────
    const { data: editingClaim, isLoading: loadingClaim } = useQuery({
        queryKey: ['claim', editingClaimId],
        queryFn: () => claimsService.getById(editingClaimId),
        enabled: !!editingClaimId,
        staleTime: 0
    });

    useEffect(() => {
        if (editingClaim) {
            setMember({ id: editingClaim.memberId, fullName: editingClaim.memberName, cardNumber: editingClaim.memberNationalNumber });
            setDiagnosis(editingClaim.diagnosisDescription || editingClaim.diagnosisCode || '');
            setComplaint(editingClaim.complaint || '');
            setIsClaimRejected(editingClaim.status === 'REJECTED');
            setRejectionInput(editingClaim.reviewerComment || '');

            setLines(editingClaim.lines.map(l => {
                // المطابقة: 1) pricingItemId (الأدق)
                //             2) serviceCode أو medicalServiceCode كاحتياط
                const lineCode = l.medicalServiceCode || l.serviceCode;
                const lineName = l.medicalServiceName || l.serviceName;
                const svc = serviceOptions.find(s =>
                    (s.pricingItemId != null && l.pricingItemId != null && s.pricingItemId === l.pricingItemId) ||
                    (s.serviceCode && lineCode && s.serviceCode === lineCode)
                );
                // سعر العقد الحي من بيانات العقد — 65 بدلاً من 70 المدخل
                const cp = svc ? (svc.contractPrice || 0) : 0;

                // السعر المُدخل = requestedUnitPrice إذا متوفر، وإلا unitPrice
                const enteredPrice = l.requestedUnitPrice != null
                    ? parseFloat(l.requestedUnitPrice) || 0
                    : parseFloat(l.unitPrice) || 0;

                const serviceObj = svc || {
                    serviceCode: lineCode,
                    serviceName: lineName,
                    categoryId: l.appliedCategoryId ?? l.serviceCategoryId ?? null,
                    label: `${lineCode ? '[' + lineCode + '] ' : ''}${lineName || ''}`
                };
                const line = {
                    id: l.id || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15)),
                    service: serviceObj,
                    quantity: l.quantity,
                    unitPrice: enteredPrice,
                    contractPrice: cp,
                    coveragePercent: l.coveragePercent,
                    rejected: l.rejected,
                    rejectionReason: l.rejectionReason,
                    manualRefusedAmount: parseFloat(l.manualRefusedAmount) || 0,
                    oldRejected: l.rejected ? 1 : 0
                };
                return recompute(line);
            }));
            setServiceDate(editingClaim.serviceDate || defaultDate);
            setPreAuthId(editingClaim.preAuthorizationId || '');
            setManualCategoryEnabled(editingClaim.manualCategoryEnabled ?? true);
            setPrimaryCategoryCode(editingClaim.primaryCategoryCode || 'CAT-OP');
            setFullCoverage(!!editingClaim.fullCoverage);
            setIsDirty(false);

            // المرحلة 1.3: إعادة جلب التغطية والسقوف للمطالبة المحملة لضمان دقة العرض
            // يستخدم الـ ref لضمان استخدام النسخة الأحدث دائماً (تجنّب stale closure)
            if (policyId && editingClaim.memberId) {
                setTimeout(() => {
                    refetchCoverageOnEditRef.current(editingClaim.primaryCategoryCode || 'CAT-OP');
                }, 300);
            }
        }
    }, [editingClaim, defaultDate, contractedRaw]);

    const draftPayload = useMemo(() => ({
        member,
        diagnosis,
        complaint,
        notes,
        lines,
        serviceDate,
        preAuthId,
        manualCategoryEnabled,
        primaryCategoryCode,
        fullCoverage,
        applyBenefits,
        isClaimRejected,
        rejectionInput
    }), [
        member,
        diagnosis,
        complaint,
        notes,
        lines,
        serviceDate,
        preAuthId,
        manualCategoryEnabled,
        primaryCategoryCode,
        fullCoverage,
        applyBenefits,
        isClaimRejected,
        rejectionInput
    ]);

    const applyRecoveredDraft = useCallback((payload) => {
        if (!payload) return;
        setMember(payload.member || null);
        setDiagnosis(payload.diagnosis || '');
        setComplaint(payload.complaint || '');
        setNotes(payload.notes || '');
        setLines(Array.isArray(payload.lines) && payload.lines.length ? payload.lines : [newLine()]);
        setServiceDate(payload.serviceDate || defaultDate);
        setPreAuthId(payload.preAuthId || '');
        setManualCategoryEnabled(payload.manualCategoryEnabled ?? true);
        setPrimaryCategoryCode(payload.primaryCategoryCode || 'CAT-OP');
        setFullCoverage(!!payload.fullCoverage);
        setApplyBenefits(payload.applyBenefits ?? true);
        setIsClaimRejected(!!payload.isClaimRejected);
        setRejectionInput(payload.rejectionInput || '');
        setIsDirty(true);
    }, [defaultDate]);

    useEffect(() => {
        if (editingClaimId) return;
        if (skipAutosaveRef.current) return;
        if (!hasMeaningfulDraftData(draftPayload)) return;
        try {
            localStorage.setItem(draftStorageKey, JSON.stringify({
                updatedAt: new Date().toISOString(),
                data: draftPayload
            }));
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
                            queryClient.setQueryData(
                                ['claim-batch-current', providerId, employerId, month, year],
                                batch
                            );
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
    }, [
        draftPayload,
        draftBatchId,
        draftVersion,
        editingClaimId,
        providerId,
        employerId,
        year,
        month,
        queryClient,
        enqueueSnackbar
    ]);

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
        if (member?.id && !list.find(m => m.id === member.id)) {
            return [member, ...list];
        }
        return list;
    }, [memberResults, member]);

    const serviceOptions = useMemo(() => {
        const items = Array.isArray(contractedRaw) ? contractedRaw : (contractedRaw?.items || []);
        return items.map(s => {
            const code = s.serviceCode || s.code || '';
            const name = s.serviceName || s.name || '';
            const normalizedCategoryId = s.categoryId ?? s.medicalCategoryId ?? s.medicalCategory?.id ?? null;
            return {
                ...s,
                label: `${code ? '[' + code + '] ' : ''}${name}`,
                serviceName: name,
                serviceCode: code,
                categoryId: normalizedCategoryId,
                pricingItemId: s.pricingItemId,
                contractPrice: s.contractPrice || 0
            };
        });
    }, [contractedRaw]);

    const batchContent = useMemo(() =>
        batchData?.data?.items ?? batchData?.items ?? batchData?.data?.content ?? batchData?.content ?? [], [batchData]);
    const batchTotal = batchData?.data?.total ?? batchData?.total ?? batchData?.data?.totalElements ?? batchData?.totalElements ?? 0;

    // ── المنطق المالي وتغطية الخدمات (مطبق في الأعلى) ───────────────────────────

    // Debounce ref for quantity/price changes triggering backend coverage re-fetch
    const coverageRefetchTimerRef = useRef(null);

    const updateLine = useCallback((idx, patch) => {
        setLines(prev => {
            const n = [...prev];
            n[idx] = { ...n[idx], ...patch };
            return n.map((line, i) => recompute(line, i, n));
        });
        setIsDirty(true);

        // Re-fetch coverage from backend when quantity or price changes (affects usageDetails)
        const needsBackendRefresh = 'quantity' in patch || 'unitPrice' in patch;
        if (needsBackendRefresh && policyId && member?.id) {
            if (coverageRefetchTimerRef.current) clearTimeout(coverageRefetchTimerRef.current);
            coverageRefetchTimerRef.current = setTimeout(() => {
                refetchAllLinesCoverage(primaryCategoryCode, linesRef.current).then(updated => {
                    if (updated) setLines(updated);
                });
            }, 600);
        }
    }, [recompute, policyId, member?.id, refetchAllLinesCoverage, primaryCategoryCode]);

    const handleServiceChange = useCallback(async (idx, val) => {
        if (!val) {
            updateLine(idx, { service: null, serviceName: '', serviceCode: '', unitPrice: 0, contractPrice: 0 });
            return;
        }

        let svc = val;
        let isFreeText = false;
        if (typeof val === 'string') {
            svc = { serviceName: val, label: val, mapped: false, isFreeText: true };
            isFreeText = true;
        }

        const newName = svc.serviceName || svc.name;

        const isDuplicate = lines.some((l, i) => {
            if (i === idx) return false;
            const existingName = l.serviceName || l.service?.serviceName || l.service?.name;
            return newName && existingName && existingName === newName;
        });

        if (isDuplicate) {
            enqueueSnackbar('هذه الخدمة مضافة بالفعل في بند آخر', { variant: 'error' });
            return;
        }

        let cov = { coveragePercent: policyInfo?.defaultCoveragePercent ?? 100, requiresPreApproval: false, notCovered: false };
        if (!isFreeText) {
            cov = await fetchCoverage(svc, primaryCategoryCode);
            if (cov?.__stale) {
                return;
            }
        }

        const price = svc?.contractPrice ?? 0;
        updateLine(idx, {
            service: svc,
            serviceName: svc.serviceName || (typeof val === 'string' ? val : ''),
            serviceCode: svc.serviceCode || '',
            unitPrice: price,
            contractPrice: price,
            ...cov
        });
    }, [fetchCoverage, updateLine, lines, enqueueSnackbar, primaryCategoryCode, policyInfo]);

    useEffect(() => {
        if (!policyId || !member?.id) return;

        // Force refetch usage/limits for ALL lines when member or policy changes
        refetchAllLinesCoverage(primaryCategoryCode, linesRef.current).then(updated => {
            if (updated) setLines(updated);
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [policyId, member?.id, primaryCategoryCode]);

    // ✅ FIX: Refetch coverage when editing a DIFFERENT claim of the SAME member
    // The member useEffect above won't fire if member?.id didn't change, so we need this
    useEffect(() => {
        if (!editingClaimId || !policyId || !member?.id) return;
        const timer = setTimeout(() => {
            refetchCoverageOnEditRef.current(primaryCategoryCode);
        }, 350);
        return () => clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [editingClaimId]);

    const addLine = useCallback(() => { setLines(p => [...p, newLine()]); setIsDirty(true); }, []);
    const removeLine = useCallback((idx) => {
        setLines(p => p.length === 1 ? [newLine()] : p.filter((_, i) => i !== idx));
        setIsDirty(true);
    }, []);

    const totals = useMemo(() => {
        return lines.reduce((acc, l) => ({
            total: acc.total + (parseFloat(l.total) || 0),
            company: acc.company + (parseFloat(l.byCompany) || 0),
            employee: acc.employee + (parseFloat(l.byEmployee) || 0),
            // refusedAmount دائماً يمثّل ما رُفض من حصة الشركة (سواء رفض كلي أو جزئي)
            refused: acc.refused + (parseFloat(l.refusedAmount) || 0)
        }), { total: 0, company: 0, employee: 0, refused: 0 });
    }, [lines]);

    const resetForm = useCallback(() => {
        setMember(null); setMemberInput(''); setDiagnosis('');
        setComplaint(''); setNotes(''); setLines([newLine()]);
        setApplyBenefits(true); setIsDirty(false);
        setServiceDate(defaultDate); setPreAuthId('');
        setManualCategoryEnabled(true); setPrimaryCategoryCode('CAT-OP');
        setIsClaimRejected(false); setRejectionInput('');
        setAttachments([]);
        // FIX: resetForm must also clear the editing state
        setEditingClaimId(null);
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
            const isPartial = (line.manualRefusedAmount > 0 && !line.rejected);
            setRejectionMode(isPartial ? 'partial' : 'full');
            setManualRefusedAmountInput(isPartial ? String(line.manualRefusedAmount) : '');
            setRejectionInput(line.rejectionReason || '');
        } else {
            setRejectionMode('full');
            setManualRefusedAmountInput('');
            setRejectionInput(type === 'line' ? (lines[idx]?.rejectionReason || '') : (rejectionInput || ''));
        }

        setEditingReasonId(null);
        setEditingReasonText('');
        setShowReasonsList(false);
        setRejectDialogOpen(true);
    };

    const saveNewReason = async () => {
        if (!rejectionInput?.trim()) return;
        const alreadyExists = rejectionReasons.some(r => r.reasonText === rejectionInput.trim());
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
            const oldReason = rejectionReasons.find(r => r.id === editingReasonId);
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
            triggerConfirm(
                'تأكيد رفض المطالبة',
                'أنت على وشك رفض هذه المطالبة بالكامل. سيتم تصفير جميع حصص الشركة. هل تريد الاستمرار؟',
                () => {
                    setIsClaimRejected(true);
                    setIsDirty(true);
                    setRejectDialogOpen(false);
                }
            );
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
                        enqueueSnackbar(
                            `مبلغ الرفض الجزئي يجب أن يكون بين 0.01 و ${maxAmount.toFixed(2)} د.ل`,
                            { variant: 'warning' }
                        );
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
                triggerConfirm(
                    'تأكيد رفض البند',
                    'هل تريد رفض هذا البند بالكامل (حصة الشركة ستصبح صفراً)؟',
                    doUpdate
                );
            } else {
                doUpdate();
            }
            return;
        }
    };

    const handleSave = async (resetAfter = false) => {
        if (isSavingRef.current) return;

        // التحقق من الحقول المطلوبة بشكل احترافي
        const missingFields = [];
        if (!member) missingFields.push("المستفيد");
        if (!diagnosis?.trim()) missingFields.push("التشخيص الطبي");
        if (!serviceDate) missingFields.push("تاريخ الخدمة");

        // التحقق من وجود خدمات صحيحة
        const hasValidLines = lines.some(l => l.service || l.serviceName);
        if (!hasValidLines) missingFields.push("بند خدمة طبي واحد على الأقل");

        if (missingFields.length > 0) {
            setShowValidationErrors(true);
            enqueueSnackbar(`⚠️ لا يمكن الحفظ. يرجى إدخال الحقول التالية: ${missingFields.join('، ')}`, {
                variant: 'error',
                autoHideDuration: 5000
            });
            return;
        }

        setShowValidationErrors(false);

        // تحققات إضافية لأسعار الخدمات
        if (!isClaimRejected && lines.some(l => (l.service || l.serviceName) && !l.rejected && (parseFloat(l.unitPrice) || 0) <= 0)) {
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
                enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) لا يتبع لشهر الدفعة الحالي (${MONTHS_AR[month - 1]} ${year}). يرجى التأكد من التاريخ أو الانتقال لدفعة الشهر الصحيح.`, 
                    { variant: 'warning', autoHideDuration: 8000 });
                setSaving(false);
                isSavingRef.current = false;
                return;
            }

            // التحقق: تاريخ الخدمة لا يجوز أن يكون في المستقبل
            if (actualDate && new Date(actualDate) > new Date()) {
                enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) في المستقبل — يجب إدخال تاريخ صحيح`,
                    { variant: 'error', autoHideDuration: 6000 });
                setSaving(false);
                isSavingRef.current = false;
                return;
            }

            // المرحلة 2.2: التحقق من انتهاء صلاحية الوثيقة
            if (policyInfo?.endDate && new Date(actualDate) > new Date(policyInfo.endDate)) {
                enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) يتجاوز نهاية الوثيقة المحددة (${policyInfo.endDate}) — لا يمكن الحفظ`,
                    { variant: 'error', autoHideDuration: 6000 });
                setSaving(false);
                isSavingRef.current = false;
                return;
            }

            // الحالة REJECTED فقط إذا:
            // 1. المستخدم ضغط "رفض المطالبة" صراحة (isClaimRejected)
            // 2. جميع البنود مرفوضة يدوياً (allLinesManuallyRejected)
            // ⚠️ الخصومات الآلية (تجاوز سعر/سقف) لا تجعل المطالبة "مرفوضة" — تبقى "معتمدة" مع مبالغ مرفوضة
            const activeLines = lines.filter(l => l.service || l.serviceName);
            const allLinesManuallyRejected = activeLines.length > 0
                && activeLines.every(l => l.rejected);

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
                const autoReason = activeLines.find(l => l.rejectionReason)?.rejectionReason;
                effectiveRejectionReason = autoReason || 'جميع البنود مرفوضة';
            }

            const claimData = {
                memberId: member.id,
                providerId: parseInt(providerId),
                claimBatchId: currentBatch?.id, // Phase 11 Link
                serviceDate: actualDate,
                diagnosisDescription: diagnosis,
                complaint,
                notes,
                status: effectivelyRejected ? 'REJECTED' : 'APPROVED',
                rejectionReason: effectivelyRejected ? effectiveRejectionReason : null,
                preAuthorizationId: preAuthId ? parseInt(preAuthId) : null,
                manualCategoryEnabled,
                // Always send context category so backend can set appliedCategoryId on unmapped services
                primaryCategoryCode: primaryCategoryCode,
                fullCoverage: fullCoverage,
                lines: lines.map(l => ({
                    pricingItemId: l.service?.pricingItemId || null,
                    serviceName: l.serviceName || l.service?.serviceName || '',
                    serviceCode: l.serviceCode || l.service?.serviceCode || '',
                    quantity: parseInt(l.quantity) || 1,
                    unitPrice: parseFloat(l.unitPrice) || 0,
                    refusedAmount: parseFloat(l.refusedAmount) || 0,
                    rejected: l.rejected || false,
                    rejectionReason: l.rejectionReason || null,
                    manualRefusedAmount: parseFloat(l.manualRefusedAmount) || 0
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
                        batchForSave = await claimBatchesService.openOrGetBatch(
                            providerId, employerId, year, month
                        );
                        // Update the query cache so the UI reflects the new batch
                        queryClient.setQueryData(
                            ['claim-batch-current', providerId, employerId, month, year],
                            batchForSave
                        );
                    } catch (batchErr) {
                        enqueueSnackbar(`فشل فتح الدفعة: ${batchErr?.response?.data?.message || batchErr?.message}`, { variant: 'error' });
                        setSaving(false);
                        isSavingRef.current = false;
                        return;
                    }
                    claimData.claimBatchId = batchForSave?.id;
                }

                // 1. Create a Visit automatically for this manual entry (Backlog Flow)
                const visitData = {
                    memberId: member.id,
                    providerId: parseInt(providerId),
                    visitDate: actualDate,
                    doctorName: 'طبيب مناوب', // Mandatory for visit creation
                    visitType: 'LEGACY_BACKLOG', // Correct type for manual entry
                    notes: 'إنشاء تلقائي لمطالبة قديمة (Backlog)'
                };

                const visitResponse = await visitsService.create(visitData);
                const visitId = visitResponse.id;

                // 2. Link Claim to this Visit
                claimData.visitId = visitId;

                let claimResponse;
                try {
                    claimResponse = await claimsService.create(claimData);
                } catch (claimErr) {
                    // Rollback orphan visit if claim creation fails
                    try { await visitsService.remove(visitId); } catch (_) { }
                    throw claimErr;
                }
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

            enqueueSnackbar(
                `✅ ${t('claimEntry.savedSuccess')} — #${resultClaimId}`,
                { variant: 'success' }
            );

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
            const apiMsg = err.response?.data?.messageAr
                || err.response?.data?.message
                || err.userMessage
                || err.message;
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
        const rows = batchContent.map(c => [
            c.id, c.memberName, c.serviceDate,
            c.requestedAmount?.toFixed(2) ?? '0.00',
            c.approvedAmount?.toFixed(2) ?? '0.00',
            c.status
        ]);
        const csvRows = [headers, ...rows].map(r => r.map(v => `"${v ?? ''}"`).join(','));
        const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `backlog_claims_${monthLabel}_${year}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    // ── حذف مطالبة من الشريط الجانبي ─────────────────────────────────────────
    const handleSwitchClaim = useCallback((claimId) => {
        if (isDirty) {
            if (!window.confirm('يوجد تعديلات غير محفوظة. هل تريد الانتقال بدون حفظ؟')) return;
        }
        if (claimId === null) resetForm();
        setEditingClaimId(claimId);
    }, [isDirty, resetForm]);

    const handleDeleteClaim = async (claimId, e) => {
        e.stopPropagation();
        setConfirmDeleteId(claimId);
        setConfirmDeleteReason(''); // Reset reason
    };

    const confirmDeleteClaim = async () => {
        const claimId = confirmDeleteId;
        if (!claimId) return;
        if (!confirmDeleteReason?.trim()) {
            enqueueSnackbar('يرجى إدخال سبب الإلغاء', { variant: 'warning' });
            return;
        }
        try {
            await claimsService.remove(claimId, confirmDeleteReason);
            enqueueSnackbar(`✅ تم إلغاء المطالبة #${claimId}`, { variant: 'success' });
            setConfirmDeleteId(null);
            invalidateBatchData();
            // ✅ FIX: Restore ceiling in current form after deletion
            if (member?.id && policyId) {
                setTimeout(() => refetchCoverageOnEditRef.current(primaryCategoryCode), 200);
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
                            <Chip size="small" variant="filled"
                                label={isDirty ? t('claimEntry.statusDraft') : t('claimEntry.statusNew')}
                                color={isDirty ? 'warning' : 'primary'}
                                sx={{ fontWeight: 600, fontSize: '0.85rem' }}
                            />
                            {policyInfo && (
                                <Chip icon={<PolicyIcon sx={{ fontSize: '0.85rem' }} />} size="small"
                                    label={`${t('claimEntry.benefitPolicy')}: ${policyInfo.policyNumber || policyInfo.name || 'مفعّلة'}`}
                                    color="success" variant="outlined"
                                    sx={{ fontWeight: 600, fontSize: '0.85rem', borderColor: 'success.main', color: 'success.main' }}
                                />
                            )}
                            {isClaimRejected && (
                                <Chip icon={<RejectIcon sx={{ fontSize: '0.85rem' }} />} size="small"
                                    label="مطالبة مرفوضة" color="error" variant="filled"
                                    sx={{ fontWeight: 600, fontSize: '0.85rem' }}
                                />
                            )}
                        </Stack>
                    }
                    subtitle={`${t('providers.singular')}: ${provider?.name || '...'} | رقم العقد: ${activeContract?.contractNumber || '—'} | المؤمن عليه: ${member?.fullName || '...'} (${member?.cardNumber || '—'})`}
                    icon={<ReceiptIcon />}
                    actions={
                        <Stack direction="row" spacing={1} alignItems="center">
                            {autoSaveStatus === 'saving' && (
                                <Typography variant="caption" color="warning.main" fontWeight={600}>Saving...</Typography>
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

                            <Button variant="outlined" size="small" color="secondary"
                                startIcon={<BackIcon sx={{ ml: 1, mr: 0 }} />}
                                onClick={() => navigate(detailUrl)} sx={{}}>
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
                    <Paper variant="outlined" sx={{
                        flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                        boxShadow: '0 2px 10px rgba(0,0,0,0.05)'
                    }}>

                        {/* ── لوحة معلومات التعديل ── */}
                        {editingClaimId && (
                            <Box sx={{
                                px: '1.25rem', py: '0.6rem',
                                bgcolor: alpha(theme.palette.info.main, 0.08),
                                borderBottom: `1.5px solid ${alpha(theme.palette.info.main, 0.3)}`,
                                display: 'flex', alignItems: 'center', gap: '0.75rem'
                            }}>
                                <InfoIcon sx={{ color: 'info.main', fontSize: '1.25rem' }} />
                                <Box sx={{ flex: 1 }}>
                                    <Typography variant="subtitle2" fontWeight={600} color="info.dark">
                                        أنت الآن في وضع التعديل (مطالبة #{editingClaimId})
                                    </Typography>
                                    <Typography variant="caption" color="info.main" fontWeight={400}>
                                        جاري تعديل بيانات المطالبة المختارة من الشريط الجانبي.
                                    </Typography>
                                </Box>
                                <Button size="small" color="info" variant="outlined"
                                    onClick={() => { resetForm(); setEditingClaimId(null); }}
                                    sx={{}}>
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
                                primaryCategoryCode={primaryCategoryCode}
                                setPrimaryCategoryCode={setPrimaryCategoryCode}
                                fullCoverage={fullCoverage}
                                setFullCoverage={setFullCoverage}
                                setManualCategoryEnabled={setManualCategoryEnabled}
                                rootCategories={rootCategories}
                                onRefetchAll={refetchAllLinesCoverageCallback}
                                linesRef={linesRef}
                                preAuthResults={preAuthResults}
                                searchingPreAuth={searchingPreAuth}
                                preAuthId={preAuthId}
                                setPreAuthId={setPreAuthId}
                                setPreAuthSearch={setPreAuthSearch}
                                serviceDate={serviceDate}
                                setServiceDate={setServiceDate}
                                setIsDirty={setIsDirty}
                                financialSummary={memberFinancialSummary}
                                loadingSummary={loadingSummary}
                                t={t}
                                showValidationErrors={showValidationErrors}
                            />
                        </Box>

                        <Divider />

                        <Box sx={{
                            flexShrink: 0, px: '1.25rem', py: 0.75, bgcolor: alpha(theme.palette.primary.main, 0.04),
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            borderBottom: `1px solid ${theme.palette.divider}`
                        }}>
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Typography variant="subtitle2" fontWeight={600} color="primary" sx={{ fontSize: '0.85rem' }}>
                                    {t('claimEntry.serviceLines')}
                                </Typography>
                                <Chip size="small" variant="outlined" label={`${lines.length} بند`} sx={{ fontWeight: 400, fontSize: '0.75rem', borderColor: alpha(theme.palette.primary.main, 0.3) }} />
                            </Stack>
                            <Box>
                                <Tooltip title="إظهار/إخفاء الأعمدة">
                                    <IconButton size="small" onClick={handleOpenCols}>
                                        <ViewColumnIcon fontSize="small" color="primary" />
                                    </IconButton>
                                </Tooltip>
                                <Menu
                                    anchorEl={anchorElCols}
                                    open={Boolean(anchorElCols)}
                                    onClose={handleCloseCols}
                                >
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
                                        <ListItemText primary="المتبقي" />
                                    </MenuItem>
                                    <MenuItem onClick={() => handleToggleColumn('refused')}>
                                        <ListItemIcon>
                                            <Checkbox checked={visibleColumns.refused} size="small" />
                                        </ListItemIcon>
                                        <ListItemText primary="المرفوض" />
                                    </MenuItem>
                                    <MenuItem onClick={() => handleToggleColumn('companyShare')}>
                                        <ListItemIcon>
                                            <Checkbox checked={visibleColumns.companyShare} size="small" />
                                        </ListItemIcon>
                                        <ListItemText primary="حصة الشركة" />
                                    </MenuItem>
                                    <MenuItem onClick={() => handleToggleColumn('patientShare')}>
                                        <ListItemIcon>
                                            <Checkbox checked={visibleColumns.patientShare} size="small" />
                                        </ListItemIcon>
                                        <ListItemText primary="حصة المشترك" />
                                    </MenuItem>
                                </Menu>
                            </Box>
                        </Box>

                        <TableContainer dir="rtl" sx={{ flex: 1, overflow: 'auto' }}>
                            <Table dir="rtl" size="small" stickyHeader sx={{
                                minWidth: '60rem',
                                '& .MuiTableCell-body': {
                                    borderRight: '1px solid #e0e0e0',
                                    borderBottom: '1px solid #e0e0e0',
                                    '&:last-child': { borderRight: 'none' }
                                }
                            }}>
                                <TableHead>
                                    <TableRow>
                                        <TH align="center" w={40}>#</TH>
                                        <TH align="center" w={280}>الخدمة الطبية</TH>
                                        <TH align="center" w={45}>الكمية</TH>
                                        <TH align="center" w={70}>سعر الوحدة</TH>
                                        {visibleColumns.coverage && <TH align="center" w={60}>التحمل %</TH>}
                                        {visibleColumns.benefitLimit && <TH align="center" w={110}>سقف المنفعة</TH>}
                                        {visibleColumns.remainingLimit && <TH align="center" w={110}> المتبقي من السقف </TH>}
                                        {visibleColumns.refused && <TH align="center" w={75}>المرفوض</TH>}
                                        {visibleColumns.companyShare && <TH align="center" w={105}>حصة الشركة</TH>}
                                        {visibleColumns.patientShare && <TH align="center" w={105}>حصة المشترك</TH>}
                                        <TH align="center" w={80}>الإجمالي</TH>
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
                                        />
                                    ))}
                                    <TableRow>
                                        <TableCell colSpan={12} sx={{ py: 0.5, borderRight: 'none' }}>
                                            <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                                                <Button size="small" startIcon={<AddIcon />} onClick={addLine} 
                                                    sx={{ fontWeight: 700, color: 'primary.main', px: 0 }}>
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
                    </Paper>
                </Box>
            </Box>

            <Dialog open={recoveryDialog.open} onClose={dismissRecovery} maxWidth="sm" fullWidth>
                <DialogTitle sx={{ fontWeight: 700 }}>استرجاع المسودة</DialogTitle>
                <DialogContent>
                    <Typography variant="body2" sx={{ mb: 1 }}>
                        تم العثور على بيانات محفوظة لهذه الدفعة. هل تريد استكمال الإدخال من المسودة؟
                    </Typography>
                    {recoveryDialog.serverDraft?.data && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            توجد مسودة محفوظة على الخادم.
                        </Typography>
                    )}
                    {recoveryDialog.localDraft?.data && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            توجد نسخة احتياطية على هذا الجهاز.
                        </Typography>
                    )}
                </DialogContent>
                <DialogActions>
                    {recoveryDialog.serverDraft?.data && (
                        <Button onClick={restoreServerDraft} variant="contained">استكمال من المسودة</Button>
                    )}
                    {recoveryDialog.localDraft?.data && (
                        <Button onClick={restoreLocalDraft} variant="outlined">استرجاع من الجهاز</Button>
                    )}
                    <Button onClick={dismissRecovery} color="inherit">تجاهل</Button>
                </DialogActions>
            </Dialog>

            <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth
                PaperProps={{ sx: { borderRadius: '0.375rem' } }}>
                <DialogTitle sx={{ fontWeight: 700, color: 'error.main', pb: 1 }}>
                    {rejectType === 'claim' ? 'رفض المطالبة — تحديد السبب' : 'رفض البند — تحديد السبب'}
                </DialogTitle>
                <DialogContent sx={{ pt: '0.75rem !important' }}>

                    {/* نوع الرفض — للبند فقط */}
                    {rejectType === 'line' && (
                        <Box sx={{ mb: 2 }}>
                            <RadioGroup
                                row
                                value={rejectionMode}
                                onChange={e => { setRejectionMode(e.target.value); setManualRefusedAmountInput(''); }}
                            >
                                <FormControlLabel
                                    value="full"
                                    control={<Radio size="small" color="error" />}
                                    label={<Typography sx={{ fontSize: '0.85rem', fontWeight: 600 }}>رفض كلي (حصة الشركة كاملاً)</Typography>}
                                />
                                <FormControlLabel
                                    value="partial"
                                    control={<Radio size="small" color="warning" />}
                                    label={<Typography sx={{ fontSize: '0.85rem', fontWeight: 600 }}>رفض جزئي (تحديد مبلغ)</Typography>}
                                />
                            </RadioGroup>

                            {rejectionMode === 'partial' && (
                                <TextField
                                    fullWidth size="small" type="number"
                                    label={`مبلغ الرفض من حصة الشركة (الحد الأقصى: ${(lines[rejectIdx]?.byCompany ?? 0).toFixed(2)} د.ل)`}
                                    value={manualRefusedAmountInput}
                                    onChange={e => setManualRefusedAmountInput(e.target.value)}
                                    inputProps={{ min: 0.01, max: lines[rejectIdx]?.byCompany ?? 0, step: 0.01 }}
                                    helperText="يُطبَّق على حصة الشركة فقط — حصة المستفيد لا تتأثر"
                                    error={parseFloat(manualRefusedAmountInput) > (lines[rejectIdx]?.byCompany ?? 0)}
                                    sx={{ mt: 1.5 }}
                                    autoFocus
                                />
                            )}
                        </Box>
                    )}

                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                        اختر سبباً من القائمة أو اكتب سبباً جديداً
                    </Typography>
                    <Autocomplete
                        freeSolo
                        options={rejectionReasons.map(r => r.reasonText)}
                        value={rejectionInput}
                        onChange={(_, val) => setRejectionInput(val || '')}
                        onInputChange={(_, val) => setRejectionInput(val)}
                        renderInput={(params) => (
                            <TextField
                                {...params}
                                autoFocus={rejectType === 'claim' || rejectionMode === 'full'}
                                fullWidth
                                size="small"
                                label="سبب الرفض"
                                placeholder="اختر أو اكتب سبباً..."
                                error={!rejectionInput?.trim()}
                            />
                        )}
                        noOptionsText="لا توجد أسباب — اكتب سبباً جديداً"
                    />
                    {rejectionInput?.trim() && !rejectionReasons.some(r => r.reasonText === rejectionInput.trim()) && (
                        <Box sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="caption" color="text.secondary">
                                سبب جديد — يمكنك حفظه في القائمة:
                            </Typography>
                            <Button
                                size="small"
                                startIcon={isSavingNewReason ? <CircularProgress size={12} /> : <AddReasonIcon sx={{ fontSize: '0.9rem' }} />}
                                onClick={saveNewReason}
                                disabled={isSavingNewReason}
                                sx={{ fontSize: '0.75rem', textTransform: 'none' }}
                            >
                                حفظ في القائمة
                            </Button>
                        </Box>
                    )}

                    {/* قائمة الأسباب المحفوظة مع تعديل وحذف */}
                    <Box sx={{ mt: 2, borderTop: '1px solid', borderColor: 'divider', pt: 1.5 }}>
                        <Button
                            size="small"
                            endIcon={<ExpandMoreIcon sx={{ fontSize: '0.9rem', transform: showReasonsList ? 'rotate(180deg)' : 'none', transition: '0.2s' }} />}
                            onClick={() => setShowReasonsList(v => !v)}
                            sx={{ fontSize: '0.75rem', textTransform: 'none', color: 'text.secondary', p: 0 }}
                        >
                            إدارة قائمة الأسباب المحفوظة ({rejectionReasons.length})
                        </Button>
                        {showReasonsList && (
                            <Box sx={{ mt: 1, maxHeight: '13rem', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                                {rejectionReasons.map(r => (
                                    <Box key={r.id} sx={{
                                        display: 'flex', alignItems: 'center', gap: 0.5,
                                        px: 1, py: 0.4, borderRadius: '0.25rem',
                                        bgcolor: editingReasonId === r.id ? 'action.selected' : 'action.hover'
                                    }}>
                                        {editingReasonId === r.id ? (
                                            <>
                                                <TextField
                                                    size="small" variant="standard" fullWidth
                                                    value={editingReasonText}
                                                    onChange={e => setEditingReasonText(e.target.value)}
                                                    onKeyDown={e => { if (e.key === 'Enter') saveEditedReason(); if (e.key === 'Escape') { setEditingReasonId(null); setEditingReasonText(''); } }}
                                                    autoFocus
                                                    inputProps={{ style: { fontSize: '0.8rem', textAlign: 'right' } }}
                                                />
                                                <Tooltip title="حفظ التعديل" arrow>
                                                    <IconButton size="small" color="success" onClick={saveEditedReason}>
                                                        <CheckIcon sx={{ fontSize: '0.9rem' }} />
                                                    </IconButton>
                                                </Tooltip>
                                                <Tooltip title="إلغاء" arrow>
                                                    <IconButton size="small" onClick={() => { setEditingReasonId(null); setEditingReasonText(''); }}>
                                                        <CancelIcon sx={{ fontSize: '0.9rem' }} />
                                                    </IconButton>
                                                </Tooltip>
                                            </>
                                        ) : (
                                            <>
                                                <Typography variant="caption" sx={{ flexGrow: 1, fontSize: '0.8rem', cursor: 'pointer' }}
                                                    onClick={() => setRejectionInput(r.reasonText)}>
                                                    {r.reasonText}
                                                </Typography>
                                                <Tooltip title="تعديل" arrow>
                                                    <IconButton size="small" onClick={() => { setEditingReasonId(r.id); setEditingReasonText(r.reasonText); }}>
                                                        <EditIcon sx={{ fontSize: '0.85rem', color: 'text.secondary' }} />
                                                    </IconButton>
                                                </Tooltip>
                                                <Tooltip title="حذف" arrow>
                                                    <IconButton size="small" color="error"
                                                        disabled={isDeletingReasonId === r.id}
                                                        onClick={() => deleteReason(r.id)}>
                                                        {isDeletingReasonId === r.id
                                                            ? <CircularProgress size={12} />
                                                            : <DeleteIcon sx={{ fontSize: '0.85rem' }} />}
                                                    </IconButton>
                                                </Tooltip>
                                            </>
                                        )}
                                    </Box>
                                ))}
                                {rejectionReasons.length === 0 && (
                                    <Typography variant="caption" color="text.disabled" sx={{ px: 1 }}>لا توجد أسباب محفوظة</Typography>
                                )}
                            </Box>
                        )}
                    </Box>
                </DialogContent>
                <DialogActions sx={{ p: '1.0rem', gap: 1 }}>
                    <Button onClick={() => setRejectDialogOpen(false)} color="inherit">إلغاء</Button>
                    <Button onClick={confirmRejection} variant="contained"
                        color={rejectionMode === 'partial' ? 'warning' : 'error'}
                        disabled={
                            !rejectionInput?.trim() ||
                            (rejectionMode === 'partial' && rejectType === 'line' && (
                                !manualRefusedAmountInput ||
                                parseFloat(manualRefusedAmountInput) <= 0 ||
                                parseFloat(manualRefusedAmountInput) > (lines[rejectIdx]?.byCompany ?? 0) + 0.001
                            ))
                        }>
                        {rejectionMode === 'partial' && rejectType === 'line' ? 'تأكيد الرفض الجزئي' : 'تأكيد الرفض'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={!!confirmDeleteId} onClose={() => setConfirmDeleteId(null)} maxWidth="sm" fullWidth>
                <DialogTitle sx={{ fontWeight: 600, color: 'error.main' }}>
                    تأكيد إلغاء المطالبة
                </DialogTitle>
                <DialogContent>
                    <Typography sx={{ mb: 2 }}>
                        هل أنت متأكد من رغبتك في إلغاء المطالبة رقم #{confirmDeleteId}؟ هذا الإجراء سيؤدي إلى استرجاع الأموال لسقف العضو.
                    </Typography>
                    <TextField
                        fullWidth
                        size="small"
                        multiline
                        rows={2}
                        label="سبب الإلغاء (إجباري)"
                        placeholder="اكتب سبب الإلغاء هنا..."
                        value={confirmDeleteReason}
                        onChange={(e) => setConfirmDeleteReason(e.target.value)}
                        autoFocus
                        error={!confirmDeleteReason?.trim()}
                    />
                </DialogContent>
                <DialogActions sx={{ p: '1.0rem' }}>
                    <Button onClick={() => setConfirmDeleteId(null)} color="inherit">تراجع</Button>
                    <Button
                        onClick={confirmDeleteClaim}
                        variant="contained"
                        color="error"
                        disabled={!confirmDeleteReason?.trim()}
                    >
                        تأكيد الإلغاء
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Generic Action Confirmation */}
            <Dialog open={actionConfirm.open} onClose={closeActionConfirm}>
                <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <WarningIcon color="warning" />
                    {actionConfirm.title}
                </DialogTitle>
                <DialogContent>
                    <Typography>{actionConfirm.message}</Typography>
                </DialogContent>
                <DialogActions sx={{ p: 2, bgcolor: '#f8f9fa' }}>
                    <Button onClick={closeActionConfirm} color="inherit">تراجع</Button>
                    <Button
                        onClick={() => { actionConfirm.onConfirm(); closeActionConfirm(); }}
                        variant="contained"
                        color="primary"
                    >
                        متابعة العملية
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}




