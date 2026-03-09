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
import { checkServiceCoverage, checkServiceUsageLimit, getCoverageForService } from 'services/api/benefit-policy-rules.service';

// ── أسماء الشهور ─────────────────────────────────────────────────────────────
const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const newLine = () => ({
    id: Math.random(), service: null, serviceName: '', serviceCode: '',
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

    // ✅ Claim Category Context (Manual Rule Selection)
    const [manualCategoryEnabled, setManualCategoryEnabled] = useState(true);
    const [primaryCategoryCode, setPrimaryCategoryCode] = useState('CAT-OUTPAT');

    const defaultDate = useMemo(
        () => (month && year) ? `${year}-${String(month).padStart(2, '0')}-01` : new Date().toISOString().split('T')[0],
        [month, year]
    );

    const [serviceDate, setServiceDate] = useState(defaultDate);

    const memberRef = useRef(null);


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

    const { data: rootCategories } = useQuery({
        queryKey: ['medical-categories-root'],
        queryFn: () => medicalCategoriesService.getAllMedicalCategories(),
        select: (data) => data.filter(c => !c.parentId),
        staleTime: Infinity
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
                    id: l.id || Math.random(),
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

    // ── التحقق من التغطية التأمينية ──────────────────────────────────────────
    const fetchCoverage = useCallback(async (service, categoryCodeOverride) => {
        const sid = service?.medicalServiceId || 0;
        let categoryId = service?.categoryId || null;
        const fallbackPercent = policyInfo?.defaultCoveragePercent ?? 100;

        if (!policyId || !applyBenefits)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        if (!sid && !categoryId && !categoryCodeOverride)
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };

        try {
            if (categoryCodeOverride) {
                const cat = rootCategories?.find(c => c.code === categoryCodeOverride);
                if (cat) categoryId = cat.id;
            }

            // Fetch coverage check (quick) and full rule (for limits) in parallel
            const [r, fullRule] = await Promise.all([
                checkServiceCoverage(policyId, sid, categoryId),
                getCoverageForService(policyId, sid, categoryId).catch(() => null)
            ]);

            // timesLimit / amountLimit: prefer /check response (new backend), fallback to full rule
            const timesLimit = r?.timesLimit ?? fullRule?.timesLimit ?? null;
            const amountLimit = r?.amountLimit != null
                ? parseFloat(r.amountLimit)
                : (fullRule?.amountLimit != null ? parseFloat(fullRule.amountLimit) : null);

            console.log('@@@ fetchCoverage call:', { sid, categoryId, checkResult: r, fullRuleResult: fullRule, decidedTimes: timesLimit, decidedAmount: amountLimit });

            // Build base coverage info (includes limits from rule)
            const hasLimits = (timesLimit != null) || (amountLimit != null);
            let baseLimitDetails = hasLimits ? {
                hasLimit: true,
                timesLimit,
                amountLimit,
                usedCount: 0,
                usedAmount: 0,
                exceeded: false,
                timesExceeded: false,
                amountExceeded: false
            } : null;

            // If member is selected, also fetch actual usage data
            if (member?.id) {
                const memberId = member.id;
                if (memberId) {
                    const usage = await checkServiceUsageLimit(policyId, sid, memberId, categoryId);
                    if (usage && usage.hasLimit) {
                        baseLimitDetails = { ...baseLimitDetails, ...usage };
                    }
                }
            }

            return {
                coveragePercent: r?.coveragePercent ?? fallbackPercent,
                requiresPreApproval: r?.requiresPreApproval ?? false,
                notCovered: r?.covered === false,
                usageExceeded: baseLimitDetails?.exceeded ?? false,
                usageDetails: baseLimitDetails
            };
        } catch (err) {
            console.error('[fetchCoverage] error:', err);
            return { coveragePercent: fallbackPercent, requiresPreApproval: false, notCovered: false };
        }
    }, [policyId, policyInfo?.defaultCoveragePercent, applyBenefits, member?.id, rootCategories]);

    // ── منطق الجدول ──────────────────────────────────────────────────────────
    const recompute = useCallback((line, idx = null, currentBatch = null) => {
        if (!line) return line;

        const qty = Math.max(0, parseInt(line.quantity) || 0);
        const enteredPrice = Math.max(0, parseFloat(line.unitPrice) || 0);
        const total = parseFloat((enteredPrice * qty).toFixed(2));

        if (line.rejected) {
            return { ...line, byCompany: 0, byEmployee: 0, total, refusedAmount: total };
        }

        const contractPrice = parseFloat(line.contractPrice || 0);

        // Calculate the amount that is "authorized" by the contract
        // If price exceeds contract, the "Excess" is refused.
        const effectivePrice = (contractPrice > 0 && enteredPrice > contractPrice) ? contractPrice : enteredPrice;
        const effectiveTotal = parseFloat((effectivePrice * qty).toFixed(2));

        // The difference is "Refused" because of price ceiling
        const priceRefused = Math.max(0, total - effectiveTotal);

        // --- Usage Limit Enforcement (PHASE 2.2) ---
        let limitRefused = 0;
        let usageExceeded = false;
        const usage = line.usageDetails;

        let batchUsedTimes = 0;
        let batchUsedAmount = 0;
        let totalUsedCount = 0;
        let totalUsedAmount = 0;

        if (usage && usage.hasLimit) {
            // New logic: Check other lines in the same batch that share the same rule
            const currentRuleId = usage.ruleId;
            if (currentRuleId && currentBatch && idx !== null) {
                currentBatch.forEach((l, i) => {
                    // Only count lines BEFORE this one
                    if (i < idx && !l.rejected && l.service && l.usageDetails?.ruleId === currentRuleId) {
                        batchUsedTimes += (parseInt(l.quantity) || 0);
                        const lQty = parseInt(l.quantity) || 0;
                        const lPrice = Math.min(parseFloat(l.unitPrice || 0), parseFloat(l.contractPrice || 0) || Infinity);
                        batchUsedAmount += (lPrice * lQty);
                    }
                });
            }

            totalUsedCount = (usage?.usedCount || 0) + batchUsedTimes;
            totalUsedAmount = (usage?.usedAmount || 0) + batchUsedAmount;

            if (usage.timesLimit > 0 && totalUsedCount >= usage.timesLimit) {
                limitRefused = effectiveTotal;
                usageExceeded = true;
            } else if (usage.amountLimit > 0) {
                const remaining = Math.max(0, usage.amountLimit - totalUsedAmount);
                if (effectiveTotal > remaining) {
                    limitRefused = Math.max(limitRefused, effectiveTotal - remaining);
                    usageExceeded = true;
                }
            }
        }

        const approvedTotalForCoverage = Math.max(0, effectiveTotal - limitRefused);

        let byCompany, byEmployee;
        const cov = (line.coveragePercent !== null && line.coveragePercent !== undefined) ? line.coveragePercent : (policyInfo?.defaultCoveragePercent ?? 100);

        if (applyBenefits) {
            byCompany = parseFloat((approvedTotalForCoverage * cov / 100).toFixed(2));
            byEmployee = parseFloat((effectiveTotal - byCompany).toFixed(2));
        } else {
            byEmployee = Math.max(0, parseFloat(line.byEmployee) || 0);
            byCompany = parseFloat(Math.max(0, approvedTotalForCoverage - byEmployee).toFixed(2));
        }

        return {
            ...line,
            total,
            byCompany,
            byEmployee,
            refusedAmount: parseFloat((priceRefused + limitRefused).toFixed(2)),
            usageExceeded: usageExceeded || (usage && usage.exceeded),
            usageExhausted: limitRefused >= effectiveTotal && effectiveTotal > 0,
            usageDetails: usage ? {
                ...usage,
                totalUsedCount,
                totalUsedAmount,
                remainingAmount: usage.amountLimit > 0 ? Math.max(0, usage.amountLimit - totalUsedAmount - approvedTotalForCoverage) : null
            } : null
        };
    }, [applyBenefits, policyInfo?.defaultCoveragePercent]);

    const updateLine = useCallback((idx, patch) => {
        setLines(prev => {
            const n = [...prev];
            n[idx] = { ...n[idx], ...patch };
            // Cascade recompute to all lines to ensure cross-limit consistency
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

        const hasContractPrice = svc.hasContract !== false && svc.price !== undefined && svc.price !== null;
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

    // جلب التغطية عند اختيار المريض أو تغيير البوليصة أو تغيير السياق
    const refetchAllLinesCoverage = useCallback(async (newCategoryCode) => {
        if (!policyId || !member?.id) return;
        const catCode = newCategoryCode !== undefined ? newCategoryCode : primaryCategoryCode;
        const updated = await Promise.all(
            lines.map(async (line, idx) => {
                if (!line.service) return line;
                const cov = await fetchCoverage(line.service, catCode);
                return { ...line, ...cov };
            })
        );
        setLines(prev => updated.map((line, i) => recompute(line, i, updated)));
    }, [policyId, member?.id, lines, primaryCategoryCode, fetchCoverage, recompute]);

    useEffect(() => {
        if (!policyId || !member?.id) return;
        lines.forEach((line, idx) => {
            if (!line.service || line.usageDetails) return;
            fetchCoverage(line.service, primaryCategoryCode).then(cov =>
                updateLine(idx, cov)
            );
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [policyId, applyBenefits, primaryCategoryCode, member?.id, lines.length]);

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
        if (!member) { enqueueSnackbar(t('claimEntry.validationMember'), { variant: 'error' }); return; }
        if (lines.some(l => !l.service && !l.serviceName)) { enqueueSnackbar(t('claimEntry.validationService'), { variant: 'error' }); return; }
        if (!isClaimRejected && lines.some(l => !l.rejected && (parseFloat(l.unitPrice) || 0) <= 0)) {
            enqueueSnackbar('يجب أن يكون سعر الوحدة أكبر من صفر لكل بند غير مرفوض', { variant: 'error' }); return;
        }
        setSaving(true);
        try {
            const actualDate = serviceDate || defaultDate;

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
        } finally { setSaving(false); }
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
        if (!window.confirm(`هل تريد إلغاء المطالبة #${claimId}؟`)) return;
        try {
            await claimsService.remove(claimId);
            enqueueSnackbar(`✅ تم إلغاء المطالبة #${claimId}`, { variant: 'success' });
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
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'row', gap: 2, overflow: 'hidden', minHeight: 0 }}>

                {/* ── الشريط الجانبي — يسار ── */}
                <Box sx={{ width: 280, flexShrink: 0, display: 'flex', flexDirection: 'column', order: -1 }}>
                    <Paper variant="outlined" sx={{
                        flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                        borderRadius: 2.5, p: 1.5,
                        boxShadow: '0 2px 8px rgba(0,0,0,0.04)'
                    }}>
                        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mb: 1.5 }}>
                            <HistoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                            <Typography variant="subtitle2" fontWeight={900} sx={{ fontSize: '0.9rem', flex: 1 }}>
                                {t('claimEntry.batchHistory')}
                            </Typography>
                            <Button size="small" variant="contained" color="primary" startIcon={<AddIcon sx={{ fontSize: 13 }} />}
                                onClick={() => { resetForm(); setEditingClaimId(null); }}
                                sx={{ height: 24, fontSize: '0.75rem', fontWeight: 800, borderRadius: 1.2 }}>
                                جديد
                            </Button>
                        </Stack>
                        <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.65rem', mb: 1, display: 'block' }}>
                            {monthLabel} {year}
                        </Typography>
                        <Divider sx={{ mb: 1 }} />

                        {loadingBatch ? (
                            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                                <CircularProgress size={20} thickness={4} />
                            </Box>
                        ) : (
                            <Stack spacing={0.5} sx={{ flex: 1, overflowY: 'auto' }}>
                                {batchContent.map(c => (
                                    <Paper key={c.id} variant="outlined"
                                        onClick={() => setEditingClaimId(c.id)}
                                        sx={{
                                            p: 0.75, borderRadius: 1.5, cursor: 'pointer', flexShrink: 0,
                                            transition: 'all 0.15s',
                                            bgcolor: editingClaimId === c.id ? alpha(theme.palette.primary.main, 0.08) : 'transparent',
                                            borderColor: editingClaimId === c.id ? 'primary.main' : 'divider',
                                            '&:hover': {
                                                bgcolor: alpha(theme.palette.primary.main, 0.05),
                                                borderColor: 'primary.light',
                                                transform: 'translateX(2px)'
                                            }
                                        }}>
                                        <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={0.5}>
                                            <Typography variant="caption" fontWeight={800}
                                                sx={{ fontSize: '0.75rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {c.memberName}
                                            </Typography>
                                            <Stack direction="row" alignItems="center" spacing={0.25}>
                                                <Typography variant="caption" fontWeight={900} sx={{
                                                    color: 'success.dark', bgcolor: alpha('#2e7d32', 0.09),
                                                    px: 0.7, py: 0.3, borderRadius: 1, fontSize: '0.85rem'
                                                }}>
                                                    {(c.approvedAmount || 0).toFixed(2)}
                                                </Typography>
                                                {(c.refusedAmount > 0) && (
                                                    <Typography variant="caption" fontWeight={900} sx={{
                                                        color: '#d32f2f', bgcolor: alpha('#d32f2f', 0.09),
                                                        px: 0.7, py: 0.3, borderRadius: 1, fontSize: '0.85rem'
                                                    }}>
                                                        {(c.refusedAmount || 0).toFixed(2)}
                                                    </Typography>
                                                )}
                                                <Tooltip title="إلغاء المطالبة">
                                                    <IconButton size="small" color="error"
                                                        onClick={(e) => handleDeleteClaim(c.id, e)}
                                                        sx={{ p: 0.25, opacity: 0.5, '&:hover': { opacity: 1 } }}>
                                                        <DeleteIcon sx={{ fontSize: 11 }} />
                                                    </IconButton>
                                                </Tooltip>
                                            </Stack>
                                        </Stack>
                                        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.3 }}>
                                            <Typography variant="caption" color="text.disabled"
                                                sx={{ fontFamily: 'monospace', fontSize: '0.6rem' }}>
                                                #{c.id}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.6rem' }}>
                                                {c.serviceDate}
                                            </Typography>
                                        </Stack>
                                    </Paper>
                                ))}
                                {batchData?.totalPages > 1 && (
                                    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1, py: 1, borderTop: '1px solid #eee', mb: 1 }}>
                                        <Pagination
                                            count={batchData.totalPages}
                                            page={page + 1}
                                            onChange={(e, v) => setPage(v - 1)}
                                            size="small"
                                            siblingCount={0}
                                            boundaryCount={1}
                                            shape="rounded"
                                            color="primary"
                                        />
                                    </Box>
                                )}
                                {!batchContent.length && (
                                    <Box sx={{ textAlign: 'center', py: 3, opacity: 0.3 }}>
                                        <ReceiptIcon sx={{ fontSize: 28, mb: 0.5 }} />
                                        <Typography variant="caption" display="block" fontWeight={700} sx={{ fontSize: '0.75rem' }}>
                                            {t('claimEntry.noHistoryYet')}
                                        </Typography>
                                    </Box>
                                )}
                            </Stack>
                        )}

                        <Divider sx={{ mt: 1, mb: 1 }} />
                        <Button fullWidth variant="text" size="small" color="primary"
                            onClick={() => navigate(detailUrl)} sx={{ fontWeight: 800, fontSize: '0.75rem' }}>
                            {t('claimEntry.viewAllBatch')}
                        </Button>
                    </Paper>
                </Box>

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

                        {/* ── حقول الرأس (مضغوطة) ── */}
                        <Box sx={{ flexShrink: 0, px: 2.5, py: 1.5, bgcolor: 'background.paper' }}>
                            <Grid container spacing={2}>
                                {/* Row 1: Patient, Diagnosis, Context */}
                                <Grid size={{ xs: 12, sm: 3 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        {t('claimEntry.patient')} <Typography component="span" color="error.main">*</Typography>
                                    </Typography>
                                    <Autocomplete size="small" fullWidth options={memberOptions} loading={searchingMember}
                                        value={member}
                                        onChange={(_, v) => { setMember(v); setIsDirty(true); }}
                                        onInputChange={(_, v) => setMemberInput(v)}
                                        getOptionLabel={o => `${o.fullName || ''} · ${o.cardNumber || ''}`}
                                        isOptionEqualToValue={(o, v) => o.id === v?.id}
                                        renderInput={params => (
                                            <TextField {...params} inputRef={memberRef} variant="standard" autoFocus
                                                placeholder={t('claimEntry.searchPatient')} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                                        )}
                                    />
                                </Grid>

                                <Grid size={{ xs: 12, sm: 3 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        {t('claimEntry.diagnosis')}
                                    </Typography>
                                    <TextField fullWidth size="small" variant="standard" value={diagnosis}
                                        onChange={e => { setDiagnosis(e.target.value); setIsDirty(true); }} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                                </Grid>

                                <Grid size={{ xs: 12, sm: 6 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 900, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        سياق التغطية (Context)
                                    </Typography>
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        <FormControlLabel
                                            control={
                                                <Checkbox
                                                    size="small"
                                                    checked={primaryCategoryCode === 'CAT-OUTPAT'}
                                                    onChange={(e) => {
                                                        const checked = e.target.checked;
                                                        const newCode = checked ? 'CAT-OUTPAT' : '';
                                                        setPrimaryCategoryCode(newCode);
                                                        setManualCategoryEnabled(true);
                                                        setIsDirty(true);
                                                        // إعادة جلب السقف لجميع البنود بالسياق الجديد
                                                        refetchAllLinesCoverage(newCode);
                                                    }}
                                                />
                                            }
                                            label={<Typography sx={{ fontSize: '0.75rem', fontWeight: 900, whiteSpace: 'nowrap' }}>عيادات خارجية</Typography>}
                                        />
                                        {primaryCategoryCode !== 'CAT-OUTPAT' && (
                                            <Autocomplete
                                                size="small"
                                                sx={{ flexGrow: 1 }}
                                                options={rootCategories?.filter(c => c.code !== 'CAT-OUTPAT') || []}
                                                getOptionLabel={(o) => o.name || o.nameAr || ''}
                                                value={rootCategories?.find(c => c.code === primaryCategoryCode) || null}
                                                onChange={(_, v) => {
                                                    const newCode = v?.code || '';
                                                    setPrimaryCategoryCode(newCode);
                                                    setManualCategoryEnabled(!!v);
                                                    setIsDirty(true);
                                                    // إعادة جلب السقف لجميع البنود بالسياق الجديد
                                                    refetchAllLinesCoverage(newCode);
                                                }}
                                                renderInput={(params) => (
                                                    <TextField {...params} variant="standard" placeholder="اختر التصنيف..."
                                                        sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                                                )}
                                            />
                                        )}
                                    </Stack>
                                </Grid>

                                {/* Row 2: Pre-approval, Complaint, Notes */}
                                <Grid size={{ xs: 12, sm: 4 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        رقم الموافقة المسبقة (PA Selection)
                                    </Typography>
                                    <Autocomplete
                                        size="small"
                                        fullWidth
                                        options={preAuthResults?.items || []}
                                        loading={searchingPreAuth}
                                        value={preAuthResults?.items?.find(pa => pa.id === parseInt(preAuthId)) || null}
                                        onInputChange={(_, v) => setPreAuthSearch(v)}
                                        onChange={(_, v) => {
                                            setPreAuthId(v?.id || '');
                                            setIsDirty(true);
                                        }}
                                        getOptionLabel={o => `[${o.preAuthNumber || o.referenceNumber || o.id}] ${o.medicalServiceName || ''}`}
                                        renderInput={params => (
                                            <TextField {...params} variant="standard"
                                                placeholder="ابحث برقم الموافقة..."
                                                sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
                                            />
                                        )}
                                        noOptionsText="لا توجد موافقات مسبقة"
                                    />
                                </Grid>

                                <Grid size={{ xs: 12, sm: 4 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        {t('claimEntry.complaint')}
                                    </Typography>
                                    <TextField fullWidth size="small" variant="standard" value={complaint}
                                        onChange={e => { setComplaint(e.target.value); setIsDirty(true); }} sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                                </Grid>

                                <Grid size={{ xs: 12, sm: 4 }}>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5, fontSize: '0.7rem' }}>
                                        {t('claimEntry.notes')}
                                    </Typography>
                                    <TextField fullWidth size="small" variant="standard" value={notes}
                                        onChange={e => { setNotes(e.target.value); setIsDirty(true); }}
                                        sx={{ ...inlineSx, '& .MuiInputBase-input': { fontSize: '0.8rem' } }} />
                                </Grid>
                            </Grid>
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
                                        <Fragment key={line.id}>
                                            <TableRow hover sx={{
                                                '& td': { py: 0.6, px: 1.5 },
                                                ...(line.rejected && { bgcolor: alpha(theme.palette.error.main, 0.05) })
                                            }}>
                                                <TableCell align="right">
                                                    <Stack spacing={0.5} alignItems="flex-start">
                                                        <Autocomplete
                                                            size="small"
                                                            freeSolo
                                                            fullWidth
                                                            options={serviceOptions}
                                                            loading={loadingServices}
                                                            value={line.service}
                                                            onInputChange={(_, v) => {
                                                                if (typeof v === 'string') {
                                                                    handleServiceChange(idx, v);
                                                                }
                                                            }}
                                                            onChange={(_, v) => handleServiceChange(idx, v)}
                                                            groupBy={(option) => option?.categoryName || 'أخرى'}
                                                            getOptionLabel={o => typeof o === 'string' ? o : (o?.label || o?.serviceName || '')}
                                                            isOptionEqualToValue={(option, value) => {
                                                                if (!option || !value) return false;
                                                                if (typeof value === 'string') return option.serviceName === value;
                                                                const optId = option?.medicalServiceId || option?.pricingItemId || option?.id;
                                                                const valId = value?.medicalServiceId || value?.pricingItemId || value?.id;
                                                                return optId && valId && optId === valId;
                                                            }}
                                                            renderInput={params => (
                                                                <TextField
                                                                    {...params}
                                                                    variant="standard"
                                                                    sx={{ ...inlineSx, '& input': { textAlign: 'right', fontSize: '0.8rem', fontWeight: 500 } }}
                                                                    placeholder={loadingServices ? "جاري التحميل..." : "ابحث عن خدمة..."}
                                                                    inputProps={{ ...params.inputProps, style: { textAlign: 'right' } }}
                                                                />
                                                            )}
                                                            noOptionsText={loadingServices ? "جاري تحميل خدمات العقد..." : "لم يتم العثور على خدمات في العقد"}
                                                        />
                                                        {line.service && !line.service.medicalServiceId && line.service.pricingItemId && (
                                                            <Chip label="عقد مباشر" size="small" color="info" variant="outlined" sx={{ height: 16, fontSize: '0.65rem', fontWeight: 700 }} />
                                                        )}
                                                    </Stack>
                                                </TableCell>
                                                <TableCell align="center">
                                                    <TextField variant="standard" type="number" value={line.quantity}
                                                        onChange={e => updateLine(idx, { quantity: e.target.value })} sx={inlineSx} />
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Tooltip title={line.contractPrice > 0 && line.unitPrice > line.contractPrice ? `السعر يتجاوز العقد (${line.contractPrice})` : ''} arrow>
                                                        <TextField variant="standard" type="number" value={line.unitPrice}
                                                            onChange={e => updateLine(idx, { unitPrice: e.target.value })}
                                                            sx={{
                                                                ...inlineSx,
                                                                '& input': {
                                                                    ...inlineSx['& input'],
                                                                    color: line.contractPrice > 0 && line.unitPrice > line.contractPrice ? 'error.main' : 'inherit',
                                                                    fontWeight: line.contractPrice > 0 && line.unitPrice > line.contractPrice ? 900 : 'inherit'
                                                                }
                                                            }}
                                                        />
                                                    </Tooltip>
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 700, color: 'text.secondary' }}>
                                                        {line.coveragePercent !== null ? `${line.coveragePercent}%` : `${policyInfo?.defaultCoveragePercent ?? 100}%`}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="center">
                                                    {line.usageDetails && (
                                                        <Stack spacing={0.3} alignItems="center" justifyContent="center">
                                                            {line.usageDetails.timesLimit > 0 && (
                                                                <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.timesExceeded || line.usageDetails.totalUsedCount > line.usageDetails.timesLimit ? 'error.main' : 'text.secondary', fontWeight: 900, whiteSpace: 'nowrap' }}>
                                                                    مرات: {line.usageDetails.totalUsedCount}/{line.usageDetails.timesLimit}
                                                                </Typography>
                                                            )}
                                                            {line.usageDetails.amountLimit > 0 && (
                                                                <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.amountExceeded || line.usageDetails.totalUsedAmount > line.usageDetails.amountLimit ? 'error.main' : 'text.secondary', fontWeight: 900, whiteSpace: 'nowrap' }}>
                                                                    د.ل: {(line.usageDetails.totalUsedAmount ?? 0).toFixed(2)}/{line.usageDetails.amountLimit}
                                                                </Typography>
                                                            )}
                                                        </Stack>
                                                    )}
                                                </TableCell>
                                                <TableCell align="center">
                                                    {line.usageDetails && (
                                                        <Stack spacing={0.3} alignItems="center" justifyContent="center">
                                                            {line.usageDetails.timesLimit > 0 && (() => {
                                                                const remaining = Math.max(0, line.usageDetails.timesLimit - line.usageDetails.totalUsedCount);
                                                                return (
                                                                    <Typography variant="caption" sx={{
                                                                        fontSize: '0.75rem',
                                                                        color: remaining === 0 ? 'error.main' : 'primary.main',
                                                                        fontWeight: 900, whiteSpace: 'nowrap'
                                                                    }}>
                                                                        مرات: {remaining}
                                                                    </Typography>
                                                                );
                                                            })()}
                                                            {line.usageDetails.amountLimit > 0 && (() => {
                                                                // Use remainingAmount from recompute (amountLimit - totalUsedInBatch - currentLineEffective)
                                                                const remaining = line.usageDetails.remainingAmount != null
                                                                    ? line.usageDetails.remainingAmount
                                                                    : Math.max(0, line.usageDetails.amountLimit - (line.usageDetails.totalUsedAmount ?? 0));
                                                                return (
                                                                    <Typography variant="caption" sx={{
                                                                        fontSize: '0.75rem',
                                                                        color: remaining <= 0 ? 'error.main' : 'primary.main',
                                                                        fontWeight: 900, whiteSpace: 'nowrap'
                                                                    }}>
                                                                        د.ل: {remaining.toFixed(2)}
                                                                    </Typography>
                                                                );
                                                            })()}
                                                        </Stack>
                                                    )}
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 800, color: 'error.main' }}>
                                                        {(line.rejected ? line.total : line.refusedAmount)?.toFixed(2)}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Stack spacing={0} alignItems="center">
                                                        <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 900, color: 'success.main', lineHeight: 1.2 }}>
                                                            {line.byCompany?.toFixed(2)}
                                                        </Typography>
                                                        <Typography variant="caption" sx={{ fontSize: '0.75rem', fontWeight: 900, color: 'warning.dark', lineHeight: 1.2 }}>
                                                            {line.byEmployee?.toFixed(2)}
                                                        </Typography>
                                                    </Stack>
                                                </TableCell>
                                                <TableCell align="center">
                                                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 900, color: 'primary.main' }}>
                                                        {line.total?.toFixed(2)}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="left">
                                                    <Stack direction="row" spacing={0} justifyContent="flex-start" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
                                                        <IconButton size="small" color={line.rejected ? "error" : "default"}
                                                            onClick={() => line.rejected ? updateLine(idx, { rejected: false }) : openRejectDialog('line', idx)}>
                                                            <RejectIcon sx={{ fontSize: 15 }} />
                                                        </IconButton>
                                                        <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
                                                            <DeleteIcon sx={{ fontSize: 15 }} />
                                                        </IconButton>
                                                    </Stack>
                                                </TableCell>
                                            </TableRow>
                                            {line.rejected && (
                                                <TableRow sx={{ bgcolor: alpha(theme.palette.error.main, 0.02) }}>
                                                    <TableCell colSpan={10} sx={{ py: 0.5 }}>
                                                        <Typography variant="caption" color="error" fontWeight={800} sx={{ fontSize: '0.75rem', px: 2 }}>
                                                            سبب الرفض: {line.rejectionReason}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                            {line.usageExceeded && !line.rejected && (
                                                <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.05) }}>
                                                    <TableCell colSpan={10} sx={{ py: 0.5 }}>
                                                        <Typography variant="caption" color={line.usageExhausted ? "error.main" : "warning.dark"} fontWeight={900} sx={{ fontSize: '0.75rem', px: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            {line.usageExhausted ? <RejectIcon sx={{ fontSize: 14 }} /> : <WarningIcon sx={{ fontSize: 14 }} />}
                                                            {line.usageExhausted ? "⚠️ رصيد المنفعة استنفذ بالكامل: " : "⚠️ تجاوز سقف المنفعة المحدد: "}
                                                            {line.usageDetails?.timesLimit > 0 && `(تم استهلاك ${line.usageDetails.usedCount} من ${line.usageDetails.timesLimit} مرّة)`}
                                                            {line.usageDetails?.amountLimit > 0 && ` (تم استهلاك ${line.usageDetails.usedAmount} من ${line.usageDetails.amountLimit} ريال)`}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </Fragment>
                                    ))}
                                    <TableRow>
                                        <TableCell colSpan={10} sx={{ py: 1, textAlign: 'center' }}>
                                            <Button size="small" startIcon={<AddIcon />} onClick={addLine} sx={{ fontWeight: 800 }}>
                                                {t('claimEntry.addLine')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                </TableBody>
                            </Table>
                        </TableContainer>

                        <Box sx={{ flexShrink: 0, px: 2.5, py: 1.5, borderTop: `1px solid ${theme.palette.divider}`, display: 'flex', gap: 2, alignItems: 'center', bgcolor: alpha(theme.palette.primary.main, 0.02) }}>
                            <Button variant="contained" color={isClaimRejected ? "error" : "primary"}
                                onClick={handleSave} disabled={saving || !isDirty} sx={{ px: 4, fontWeight: 900, borderRadius: 2 }}>
                                {saving ? t('claimEntry.saving') : (isClaimRejected ? "حفظ (مرفوض)" : t('claimEntry.saveAndAdd'))}
                            </Button>

                            {!isClaimRejected ? (
                                <Button variant="outlined" color="error" startIcon={<RejectIcon />}
                                    onClick={() => openRejectDialog('claim')} sx={{ fontWeight: 800, borderRadius: 2 }}>
                                    رفض المطالبة
                                </Button>
                            ) : (
                                <Button variant="text" onClick={() => setIsClaimRejected(false)} sx={{ fontWeight: 800 }}>
                                    تغيير للقبول
                                </Button>
                            )}

                            <Box sx={{ mr: 'auto', display: 'flex', gap: 4 }}>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>حصة الشركة</Typography>
                                    <Typography variant="subtitle2" fontWeight={900} color="success.main" sx={{ fontSize: '0.9rem' }}>{totals.company.toFixed(2)}</Typography>
                                </Box>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>حصة المشترك</Typography>
                                    <Typography variant="subtitle2" fontWeight={900} color="warning.dark" sx={{ fontSize: '0.9rem' }}>{totals.employee.toFixed(2)}</Typography>
                                </Box>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>المرفوضات</Typography>
                                    <Typography variant="subtitle2" fontWeight={900} color="error.main" sx={{ fontSize: '0.9rem' }}>{totals.refused.toFixed(2)}</Typography>
                                </Box>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>الإجمالي</Typography>
                                    <Typography variant="h6" fontWeight={900} color="primary.main" sx={{ fontSize: '1.1rem' }}>{totals.total.toFixed(2)}</Typography>
                                </Box>
                            </Box>
                        </Box>
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
        </Box>
    );
}
