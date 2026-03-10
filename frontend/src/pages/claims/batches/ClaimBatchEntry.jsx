/**
 * صفحة إدخال الدفعة — تخطيط RTL يملأ الشاشة
 * ✅ الجدول والفورم من اليمين لليسار
 * ✅ الشريط الجانبي (المطالبات) من اليسار
 * ✅ زر الحفظ مرئي دون scroll
 * ✅ كل النصوص من ar.js (لا hardcode)
 */
import { useState, useMemo, useRef, useCallback, useEffect, Fragment } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Box, Grid, Stack, Typography, Button, TextField, Autocomplete,
    Divider, CircularProgress, IconButton, Table, TableBody,
    TableCell, TableContainer, TableHead, TableRow, Chip, Paper,
    Checkbox, FormControlLabel, Tooltip, alpha, TableFooter,
    InputAdornment, Alert, Dialog, DialogTitle, DialogContent,
    DialogActions, Pagination
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
    Save as SaveIcon, Add as AddIcon, Delete as DeleteIcon,
    Receipt as ReceiptIcon, CheckCircle as DoneIcon,
    ArrowBack as BackIcon, Close as DiscardIcon, History as HistoryIcon,
    Search as SearchIcon, LocalPrintshop as PrintIcon,
    FileDownload as FileDownloadIcon, WarningAmber as WarningIcon,
    VerifiedUser as PolicyIcon, Info as InfoIcon, Block as RejectIcon,
    Cancel as CancelIcon, AttachFile as AttachFileIcon
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

import { useCalculationLogic } from './hooks/useCalculationLogic';
import { useCoverageLogic } from './hooks/useCoverageLogic';

import { BatchHistorySidebar } from './components/BatchHistorySidebar';
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
    rejected: false, rejectionReason: ''
});

// أنماط حقول الجدول القابلة للتعديل
const inlineSx = {
    '& .MuiInput-root::before': { display: 'none' },
    '& .MuiInput-root::after': { borderBottomColor: '#1b5e20', borderBottomWidth: 1 },
    '& input': { fontSize: '0.8rem', fontWeight: 500, textAlign: 'center' }
};

// رأس عمود الجدول
const TH = ({ children, align = 'center', w, sx: sxOver = {} }) => (
    <TableCell align={align} sx={{
        bgcolor: '#E8F5F1', color: '#0D4731', fontWeight: 800,
        fontSize: '0.75rem', py: 0.8, px: 1.2, whiteSpace: 'nowrap',
        borderBottom: '2px solid #c8e6c9',
        ...(w && { width: w, minWidth: w }),
        ...sxOver
    }}>
        {children}
    </TableCell>
);

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
    const [diagnosis, setDiagnosis] = useState('');
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
    const [isClaimRejected, setIsClaimRejected] = useState(false);
    const [page, setPage] = useState(0);
    const [attachments, setAttachments] = useState([]);
    const [editingClaimId, setEditingClaimId] = useState(initialClaimId);
    const [preAuthId, setPreAuthId] = useState('');
    const [preAuthSearch, setPreAuthSearch] = useState('');
    const [confirmDeleteId, setConfirmDeleteId] = useState(null);

    // ✅ Claim Category Context (Manual Rule Selection)
    const [manualCategoryEnabled, setManualCategoryEnabled] = useState(true);
    const [primaryCategoryCode, setPrimaryCategoryCode] = useState('CAT-OUTPAT');

    const defaultDate = useMemo(
        () => (month && year) ? `${year}-${String(month).padStart(2, '0')}-01` : new Date().toISOString().split('T')[0],
        [month, year]
    );

    const [serviceDate, setServiceDate] = useState(defaultDate);

    const memberRef = useRef(null);
    const linesRef = useRef(lines);

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
        currentClaimId: editingClaimId
    });

    const refetchAllLinesCoverageCallback = useCallback(async (newCategoryCode) => {
        const updated = await refetchAllLinesCoverage(newCategoryCode, linesRef.current);
        if (updated) setLines(updated);
    }, [refetchAllLinesCoverage]);
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
    const { data: batchData, isLoading: loadingBatch } = useQuery({
        queryKey: ['batch-claims-entry', employerId, providerId, month, year, page],
        queryFn: async () => {
            if (!employerId || !providerId || isNaN(month) || isNaN(year)) return null;
            return claimsService.list({
                employerId, providerId,
                dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
                dateTo: `${year}-${String(month).padStart(2, '0')}-31`,
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
    const { data: memberResults, isFetching: searchingMember } = useQuery({
        queryKey: ['member-search', memberInput, employerId, policyId],
        queryFn: () => unifiedMembersService.searchMembers({
            fullName: memberInput,
            employerId,
            benefitPolicyId: policyId,
            status: 'ACTIVE',
            size: 20
        }),
        enabled: memberInput.length >= 2,
        staleTime: 5000
    });
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
            setDiagnosis(editingClaim.diagnosisCode || '');
            setComplaint(editingClaim.diagnosisDescription || '');
            setIsClaimRejected(editingClaim.status === 'REJECTED');
            setRejectionInput(editingClaim.reviewerComment || '');

            setLines(editingClaim.lines.map(l => {
                const items = Array.isArray(contractedRaw) ? contractedRaw : (contractedRaw?.items || []);
                const svc = items.find(s => s.id === l.medicalServiceId || s.medicalService?.id === l.medicalServiceId);
                const cp = svc?.contractPrice || svc?.price || l.unitPrice;

                // Restore entered price: if not rejected, refusedAmount represents price excess
                let enteredPrice = l.unitPrice;
                if (!l.rejected && (l.refusedAmount || 0) > 0) {
                    enteredPrice = (parseFloat(l.unitPrice) || 0) + ((parseFloat(l.refusedAmount) || 0) / (parseInt(l.quantity) || 1));
                }

                const serviceObj = svc
                    ? { ...svc, label: `${svc.serviceCode ? '[' + svc.serviceCode + '] ' : ''}${svc.serviceName || ''}` }
                    : {
                        id: l.medicalServiceId, serviceCode: l.medicalServiceCode, serviceName: l.medicalServiceName,
                        label: `${l.medicalServiceCode ? '[' + l.medicalServiceCode + '] ' : ''}${l.medicalServiceName || ''}`
                    };
                const line = {
                    id: l.id || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15)),
                    service: serviceObj,
                    quantity: l.quantity,
                    unitPrice: enteredPrice,
                    contractPrice: cp,
                    coveragePercent: l.coveragePercent,
                    rejected: l.rejected,
                    rejectionReason: l.rejectionReason
                };
                return recompute(line);
            }));
            setServiceDate(editingClaim.serviceDate || defaultDate);
            setPreAuthId(editingClaim.preAuthorizationId || '');
            setManualCategoryEnabled(editingClaim.manualCategoryEnabled ?? true);
            setPrimaryCategoryCode(editingClaim.primaryCategoryCode || 'CAT-OUTPAT');
            setIsDirty(false);

            // المرحلة 1.3: إعادة جلب التغطية والسقوف للمطالبة المحملة لضمان دقة العرض
            if (policyId && editingClaim.memberId) {
                setTimeout(() => {
                    refetchAllLinesCoverage(editingClaim.primaryCategoryCode || 'CAT-OUTPAT');
                }, 300);
            }
        }
    }, [editingClaim, defaultDate, contractedRaw]);

    const memberOptions = useMemo(() => {
        const c = memberResults?.data?.content ?? memberResults?.content;
        return Array.isArray(c) ? c : [];
    }, [memberResults]);

    const serviceOptions = useMemo(() => {
        const items = Array.isArray(contractedRaw) ? contractedRaw : (contractedRaw?.items || []);
        return items.map(s => ({
            ...s,
            label: `${s.code ? '[' + s.code + '] ' : (s.serviceCode ? '[' + s.serviceCode + '] ' : '')}${s.name || s.serviceName || ''}`,
            serviceName: s.serviceName || s.name || '',
            serviceCode: s.serviceCode || s.code || '',
            medicalServiceId: s.medicalServiceId,
            pricingItemId: s.pricingItemId,
            price: s.contractPrice || 0
        }));
    }, [contractedRaw]);

    const batchContent = useMemo(() =>
        batchData?.data?.items ?? batchData?.items ?? batchData?.data?.content ?? batchData?.content ?? [], [batchData]);
    const batchTotal = batchData?.data?.total ?? batchData?.total ?? batchData?.data?.totalElements ?? batchData?.totalElements ?? 0;

    // ── المنطق المالي وتغطية الخدمات (مطبق في الأعلى) ───────────────────────────

    const updateLine = useCallback((idx, patch) => {
        setLines(prev => {
            const n = [...prev];
            n[idx] = { ...n[idx], ...patch };
            return n.map((line, i) => recompute(line, i, n));
        });
        setIsDirty(true);
    }, [recompute]);

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
        }

        const price = svc?.contractPrice ?? svc?.basePrice ?? svc?.price ?? 0;
        updateLine(idx, {
            service: svc,
            serviceName: svc.serviceName || svc.name || (typeof val === 'string' ? val : ''),
            serviceCode: svc.serviceCode || svc.code || '',
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
            refused: acc.refused + (l.rejected ? (parseFloat(l.total) || 0) : (parseFloat(l.refusedAmount) || 0))
        }), { total: 0, company: 0, employee: 0, refused: 0 });
    }, [lines]);

    const resetForm = useCallback(() => {
        setMember(null); setMemberInput(''); setDiagnosis('');
        setComplaint(''); setNotes(''); setLines([newLine()]);
        setApplyBenefits(true); setIsDirty(false);
        setServiceDate(defaultDate); setPreAuthId('');
        setManualCategoryEnabled(true); setPrimaryCategoryCode('CAT-OUTPAT');
        setIsClaimRejected(false); setRejectionInput('');
        setAttachments([]);
        setTimeout(() => memberRef.current?.focus(), 120);
    }, [defaultDate]);

    const openRejectDialog = (type, idx = null) => {
        setRejectType(type);
        setRejectIdx(idx);
        setRejectionInput(type === 'line' ? lines[idx].rejectionReason : rejectionInput);
        setRejectDialogOpen(true);
    };

    const confirmRejection = () => {
        if (rejectType === 'claim') {
            setIsClaimRejected(true);
            setIsDirty(true);
        } else {
            updateLine(rejectIdx, { rejected: true, rejectionReason: rejectionInput });
        }
        setRejectDialogOpen(false);
    };

    const handleSave = async () => {
        if (isSavingRef.current) return;
        if (!member) { enqueueSnackbar(t('claimEntry.validationMember'), { variant: 'error' }); return; }
        if (lines.some(l => !l.service && !l.serviceName)) { enqueueSnackbar(t('claimEntry.validationService'), { variant: 'error' }); return; }
        if (!isClaimRejected && lines.some(l => !l.rejected && (parseFloat(l.unitPrice) || 0) <= 0)) {
            enqueueSnackbar('يجب أن يكون سعر الوحدة أكبر من صفر لكل بند غير مرفوض', { variant: 'error' }); return;
        }

        isSavingRef.current = true;
        setSaving(true);
        try {
            const actualDate = serviceDate || defaultDate;

            // المرحلة 2.2: التحقق من انتهاء صلاحية الوثيقة
            if (policyInfo?.endDate && new Date(actualDate) > new Date(policyInfo.endDate)) {
                enqueueSnackbar(`⚠️ تاريخ الخدمة (${actualDate}) يتجاوز نهاية الوثيقة المحددة (${policyInfo.endDate})`,
                    { variant: 'warning', autoHideDuration: 6000 });
                // لا نوقف الحفظ تماماً في حالة السماح بالعمليات اللاحقة، ولكن نعطيه تحذيراً صارماً
            }

            const claimData = {
                memberId: member.id,
                providerId: parseInt(providerId),
                serviceDate: actualDate,
                diagnosis,
                complaint,
                notes,
                status: isClaimRejected ? 'REJECTED' : 'SETTLED',
                rejectionReason: isClaimRejected ? rejectionInput : null,
                preAuthorizationId: preAuthId ? parseInt(preAuthId) : null,
                manualCategoryEnabled,
                primaryCategoryCode: manualCategoryEnabled ? primaryCategoryCode : null,
                lines: lines.map(l => ({
                    medicalServiceId: l.service?.medicalServiceId || null,
                    pricingItemId: l.service?.pricingItemId || null,
                    serviceName: l.serviceName || l.service?.serviceName || l.service?.name || '',
                    serviceCode: l.serviceCode || l.service?.serviceCode || l.service?.code || '',
                    quantity: parseInt(l.quantity) || 1,
                    unitPrice: parseFloat(l.unitPrice) || 0,
                    refusedAmount: parseFloat(l.refusedAmount) || 0,
                    rejected: l.rejected || false,
                    rejectionReason: l.rejectionReason || null
                }))
            };

            let resultClaimId;
            if (editingClaimId) {
                await claimsService.update(editingClaimId, claimData);
                resultClaimId = editingClaimId;
            } else {
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
                const visitId = visitResponse.id; // visitResponse is already unwrapped

                // 2. Link Claim to this Visit
                claimData.visitId = visitId;

                // Map frontend 'diagnosis' to backend 'diagnosisDescription'
                if (diagnosis) {
                    claimData.diagnosisDescription = diagnosis;
                    delete claimData.diagnosis;
                }

                const claimResponse = await claimsService.create(claimData);
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
            invalidateBatchData();
            setPage(0);
            resetForm();
            setEditingClaimId(null);
        } catch (err) {
            enqueueSnackbar(err.message || t('claimEntry.saveFailed'), { variant: 'error' });
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
    const handleDeleteClaim = async (claimId, e) => {
        e.stopPropagation();
        setConfirmDeleteId(claimId);
    };

    const confirmDeleteClaim = async () => {
        const claimId = confirmDeleteId;
        if (!claimId) return;
        try {
            await claimsService.remove(claimId);
            enqueueSnackbar(`✅ تم إلغاء المطالبة #${claimId}`, { variant: 'success' });
            setConfirmDeleteId(null);
            invalidateBatchData();
        } catch (err) {
            enqueueSnackbar(err.message || 'فشل إلغاء المطالبة', { variant: 'error' });
        }
    };

    const detailUrl = `/claims/batches/detail?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`;
    const monthLabel = MONTHS_AR[(month || 1) - 1];

    return (
        <Box dir="rtl" sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 130px)', overflow: 'hidden' }}>

            {/* ═══ رأس الصفحة المضغوط ═══ */}
            <Box sx={{ flexShrink: 0, mb: 1 }}>
                <ModernPageHeader
                    title={`${t('claimEntry.pageTitle')} — ${monthLabel} ${year || ''}`}
                    subtitle={`${t('providers.singular')}: ${provider?.name || '...'} | الوثيقة: ${policyInfo?.name || policyInfo?.policyNumber || '...'} | رقم العقد: ${activeContract?.contractNumber || '—'} | المؤمن عليه: ${member?.fullName || '...'} (${member?.cardNumber || '—'})`}
                    icon={<ReceiptIcon />}
                    actions={
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Button variant="outlined" size="small" startIcon={<FileDownloadIcon />}
                                onClick={handleExport} disabled={!batchContent.length}
                                sx={{ color: '#1b5e20', borderColor: '#1b5e20', fontWeight: 700, borderRadius: 1.5, '&:hover': { bgcolor: '#1b5e2012' } }}>
                                {t('claimEntry.exportExcel')}
                            </Button>
                            <Button variant="outlined" size="small" color="info" startIcon={<PrintIcon />}
                                onClick={handlePrint}
                                sx={{ fontWeight: 700, borderRadius: 1.5 }}>
                                {t('claimEntry.printTable')}
                            </Button>
                            <Divider orientation="vertical" flexItem />
                            <Button variant="contained" size="small" color="success" startIcon={<DoneIcon />}
                                onClick={() => navigate(detailUrl)} disabled={!batchContent.length}
                                sx={{ fontWeight: 700, borderRadius: 1.5 }}>
                                {t('claimEntry.finishBatch')}
                            </Button>
                            <Button variant="outlined" size="small" color="secondary"
                                startIcon={<BackIcon sx={{ ml: 1, mr: 0 }} />}
                                onClick={() => navigate(detailUrl)} sx={{ borderRadius: 1.5 }}>
                                {t('claimEntry.backToList')}
                            </Button>
                        </Stack>
                    }
                />
            </Box>

            {/* ═══ المحتوى ═══ */}
            <Box sx={{ flex: 1, display: 'flex', minHeight: 0, gap: 2, px: 2, pb: 2 }}>
                
                {/* ── الشريط الجانبي — يسار (سجل الدفعة) ── */}
                <BatchHistorySidebar
                    loadingBatch={loadingBatch}
                    batchContent={batchContent}
                    editingClaimId={editingClaimId}
                    setEditingClaimId={setEditingClaimId}
                    handleDeleteClaim={handleDeleteClaim}
                    resetForm={resetForm}
                    batchData={batchData}
                    page={page}
                    setPage={setPage}
                    monthLabel={monthLabel}
                    year={year}
                    theme={theme}
                    navigate={navigate}
                    detailUrl={detailUrl}
                    t={t}
                />

                {/* ── النموذج الرئيسي (يمين الشاشة) ── */}
                <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 }}>
                    <Paper variant="outlined" sx={{
                        flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                        borderRadius: 2.5, boxShadow: '0 2px 10px rgba(0,0,0,0.05)'
                    }}>

                        {/* ── لوحة معلومات التعديل ── */}
                        {editingClaimId && (
                            <Box sx={{
                                px: 2.5, py: 1.2,
                                bgcolor: alpha(theme.palette.info.main, 0.08),
                                borderBottom: `1.5px solid ${alpha(theme.palette.info.main, 0.3)}`,
                                display: 'flex', alignItems: 'center', gap: 1.5
                            }}>
                                <InfoIcon sx={{ color: 'info.main', fontSize: 20 }} />
                                <Box sx={{ flex: 1 }}>
                                    <Typography variant="subtitle2" fontWeight={900} color="info.dark">
                                        أنت الآن في وضع التعديل (مطالبة #{editingClaimId})
                                    </Typography>
                                    <Typography variant="caption" color="info.main" fontWeight={700}>
                                        جاري تعديل بيانات المطالبة المختارة من الشريط الجانبي.
                                    </Typography>
                                </Box>
                                <Button size="small" color="info" variant="outlined"
                                    onClick={() => { resetForm(); setEditingClaimId(null); }}
                                    sx={{ fontWeight: 800, borderRadius: 1.5 }}>
                                    إلغاء وتعديل جديد
                                </Button>
                            </Box>
                        )}

                        {/* ── شريط الحالة ── */}
                        <Box sx={{
                            flexShrink: 0, px: 2.5, py: 0.75,
                            bgcolor: isDirty ? alpha(theme.palette.warning.main, 0.07) : alpha(theme.palette.primary.main, 0.04),
                            borderBottom: `1px solid ${theme.palette.divider}`,
                            display: 'flex', alignItems: 'center', justifyContent: 'space-between'
                        }}>
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Chip size="small" variant="filled"
                                    label={isDirty ? t('claimEntry.statusDraft') : t('claimEntry.statusNew')}
                                    color={isDirty ? 'warning' : 'primary'}
                                    sx={{ fontWeight: 800, fontSize: '0.75rem' }}
                                />
                                {policyInfo && (
                                    <Chip icon={<PolicyIcon sx={{ fontSize: 12 }} />} size="small"
                                        label={`${t('claimEntry.benefitPolicy')}: ${policyInfo.policyNumber || policyInfo.name || 'مفعّلة'}`}
                                        color="success" variant="outlined"
                                        sx={{ fontWeight: 700, fontSize: '0.7rem' }}
                                    />
                                )}
                                {isClaimRejected && (
                                    <Chip icon={<RejectIcon sx={{ fontSize: 12 }} />} size="small"
                                        label="مطالبة مرفوضة" color="error" variant="filled"
                                        sx={{ fontWeight: 800, fontSize: '0.75rem' }}
                                    />
                                )}
                            </Stack>
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Tooltip title={t('claimEntry.discardChanges')}>
                                    <span>
                                        <IconButton size="small" onClick={resetForm} disabled={!isDirty} color="error">
                                            <DiscardIcon sx={{ fontSize: 15 }} />
                                        </IconButton>
                                    </span>
                                </Tooltip>
                                <Button variant="contained" size="small"
                                    startIcon={saving ? <CircularProgress size={11} color="inherit" /> : <SaveIcon sx={{ fontSize: 13 }} />}
                                    onClick={handleSave} disabled={saving || !isDirty}
                                    sx={{ fontWeight: 800, borderRadius: 1.5, fontSize: '0.75rem', py: 0.4 }}>
                                    {saving ? t('claimEntry.saving') : t('claimEntry.tempSave')}
                                </Button>
                            </Stack>
                        </Box>

                        {/* ── حقول الرأس (مكون منفصل) ── */}
                        <Box sx={{ flexShrink: 0, px: 2.5, py: 1.5, bgcolor: 'background.paper' }}>
                            <ClaimHeaderFields
                                member={member}
                                setMember={setMember}
                                memberOptions={memberOptions}
                                searchingMember={searchingMember}
                                setMemberInput={setMemberInput}
                                memberRef={memberRef}
                                diagnosis={diagnosis}
                                setDiagnosis={setDiagnosis}
                                primaryCategoryCode={primaryCategoryCode}
                                setPrimaryCategoryCode={setPrimaryCategoryCode}
                                setManualCategoryEnabled={setManualCategoryEnabled}
                                rootCategories={rootCategories}
                                refetchAllLinesCoverage={refetchAllLinesCoverage}
                                preAuthResults={preAuthResults}
                                searchingPreAuth={searchingPreAuth}
                                preAuthId={preAuthId}
                                setPreAuthId={setPreAuthId}
                                setPreAuthSearch={setPreAuthSearch}
                                complaint={complaint}
                                setComplaint={setComplaint}
                                setIsDirty={setIsDirty}
                                t={t}
                            />
                        </Box>

                        <Divider />

                        <Box sx={{
                            flexShrink: 0, px: 2.5, py: 0.75, bgcolor: alpha('#E8F5F1', 0.55),
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            borderBottom: `1px solid ${theme.palette.divider}`
                        }}>
                            <Typography variant="subtitle2" fontWeight={900} color="#0D4731" sx={{ fontSize: '0.85rem' }}>
                                {t('claimEntry.serviceLines')}
                            </Typography>
                            <Chip size="small" variant="outlined" label={`${lines.length} بند`} sx={{ fontWeight: 700, fontSize: '0.75rem' }} />
                        </Box>

                        <TableContainer dir="rtl" sx={{ flex: 1, overflow: 'auto' }}>
                            <Table dir="rtl" size="small" stickyHeader sx={{ minWidth: 760 }}>
                                <TableHead>
                                    <TableRow>
                                        <TH align="center" w={40}>#</TH>
                                        <TH align="center" w={280}>الخدمة الطبية</TH>
                                        <TH align="center" w={45}>الكمية</TH>
                                        <TH align="center" w={70}>سعر الوحدة</TH>
                                        <TH align="center" w={60}>التحمل %</TH>
                                        <TH align="center" w={110}>سقف المنفعة</TH>
                                        <TH align="center" w={110}> المتبقي من السقف </TH>
                                        <TH align="center" w={75}>المرفوض</TH>
                                        <TH align="center" w={100}>شركة / مشترك</TH>
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
                                        />
                                    ))}
                                    <TableRow>
                                        <TableCell colSpan={11} sx={{ py: 1, textAlign: 'center' }}>
                                            <Button size="small" startIcon={<AddIcon />} onClick={addLine} sx={{ fontWeight: 800 }}>
                                                {t('claimEntry.addLine')}
                                            </Button>
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
                            openRejectDialog={openRejectDialog}
                            totals={totals}
                            theme={theme}
                            t={t}
                        />
                    </Paper>
                </Box>
            </Box>

            <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="xs" fullWidth
                PaperProps={{ sx: { borderRadius: 3 } }}>
                <DialogTitle sx={{ fontWeight: 900, color: 'error.main' }}>تحديد سبب الرفض</DialogTitle>
                <DialogContent>
                    <TextField fullWidth autoFocus multiline rows={3} sx={{ mt: 1 }}
                        value={rejectionInput} onChange={e => setRejectionInput(e.target.value)}
                        placeholder="أدخل سبب الرفض..." />
                </DialogContent>
                <DialogActions sx={{ p: 2 }}>
                    <Button onClick={() => setRejectDialogOpen(false)} color="inherit">إلغاء</Button>
                    <Button onClick={confirmRejection} variant="contained" color="error" disabled={!rejectionInput.trim()}>
                        تأكيد الرفض
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog open={!!confirmDeleteId} onClose={() => setConfirmDeleteId(null)}>
                <DialogTitle sx={{ fontWeight: 900 }}>تأكيد إلغاء المطالبة</DialogTitle>
                <DialogContent>
                    هل أنت متأكد من رغبتك في إلغاء المطالبة رقم #{confirmDeleteId}؟ هذا الإجراء لا يمكن التراجع عنه.
                </DialogContent>
                <DialogActions sx={{ p: 2 }}>
                    <Button onClick={() => setConfirmDeleteId(null)} color="inherit">تراجع</Button>
                    <Button onClick={confirmDeleteClaim} variant="contained" color="error">تأكيد الإلغاء</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
