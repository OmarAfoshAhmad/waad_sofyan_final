/**
 * صفحة إدخال الدفعة — تخطيط RTL يملأ الشاشة
 * ✅ الجدول والفورم من اليمين لليسار
 * ✅ الشريط الجانبي (المطالبات) من اليسار
 * ✅ زر الحفظ مرئي دون scroll
 * ✅ كل النصوص من ar.js (لا hardcode)
 */
import { useState, useMemo, useRef, useCallback, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Box, Grid, Stack, Typography, Button, TextField, Autocomplete,
    Divider, CircularProgress, IconButton, Table, TableBody,
    TableCell, TableContainer, TableHead, TableRow, Chip, Paper,
    Checkbox, FormControlLabel, Tooltip, alpha, TableFooter,
    InputAdornment, Alert
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
    Save as SaveIcon, Add as AddIcon, Delete as DeleteIcon,
    Receipt as ReceiptIcon, CheckCircle as DoneIcon,
    ArrowBack as BackIcon, Close as DiscardIcon, History as HistoryIcon,
    Search as SearchIcon, LocalPrintshop as PrintIcon,
    FileDownload as FileDownloadIcon, WarningAmber as WarningIcon,
    VerifiedUser as PolicyIcon, Info as InfoIcon
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
import backlogService from 'services/api/backlog.service';
import providerContractsService from 'services/api/provider-contracts.service';
import benefitPoliciesService from 'services/api/benefit-policies.service';
import { checkServiceCoverage } from 'services/api/benefit-policy-rules.service';

// ── أسماء الشهور ─────────────────────────────────────────────────────────────
const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const newLine = () => ({
    id: Math.random(), service: null, description: '', quantity: 1,
    serviceDate: '', unitPrice: 0, byCompany: 0, byEmployee: 0,
    refusalTypes: '', total: 0, coveragePercent: null,
    requiresPreApproval: false, notCovered: false
});

// أنماط حقول الجدول القابلة للتعديل
const inlineSx = {
    '& .MuiInput-root::before': { display: 'none' },
    '& .MuiInput-root::after': { borderBottomColor: '#1b5e20', borderBottomWidth: 1 },
    '& input': { fontSize: '0.85rem' }
};

// رأس عمود الجدول
const TH = ({ children, align = 'center', w, sx: sxOver = {} }) => (
    <TableCell align={align} sx={{
        bgcolor: '#E8F5F1', color: '#0D4731', fontWeight: 700,
        fontSize: '0.8rem', py: 1, px: 1.5, whiteSpace: 'nowrap',
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

    // ── حالة النموذج ─────────────────────────────────────────────────────────
    const [member, setMember] = useState(null);
    const [memberInput, setMemberInput] = useState('');
    const [diagnosis, setDiagnosis] = useState('');
    const [complaint, setComplaint] = useState('');
    const [applyBenefits, setApplyBenefits] = useState(true);
    const [notes, setNotes] = useState('');
    const [lines, setLines] = useState([newLine(), newLine()]);
    const [saving, setSaving] = useState(false);
    const [isDirty, setIsDirty] = useState(false);
    const [policyId, setPolicyId] = useState(null);
    const [policyInfo, setPolicyInfo] = useState(null);
    const memberRef = useRef(null);

    const defaultDate = useMemo(
        () => (month && year) ? `${year}-${String(month).padStart(2, '0')}-01` : '',
        [month, year]
    );

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
        queryKey: ['batch-claims-entry', employerId, providerId, month, year],
        queryFn: async () => {
            if (!employerId || !providerId || isNaN(month) || isNaN(year)) return null;
            return claimsService.list({
                employerId, providerId,
                dateFrom: `${year}-${String(month).padStart(2, '0')}-01`,
                dateTo: `${year}-${String(month).padStart(2, '0')}-31`,
                size: 100, sortBy: 'createdAt', sortDirection: 'DESC'
            });
        },
        enabled: !!employerId && !!providerId
    });
    const { data: contractedRaw, isLoading: loadingServices } = useQuery({
        queryKey: ['contracted-services', providerId],
        queryFn: () => providerContractsService.getAllContractedServices(providerId),
        enabled: !!providerId
    });
    const { data: memberResults, isFetching: searchingMember } = useQuery({
        queryKey: ['member-search', memberInput, employerId],
        queryFn: () => unifiedMembersService.searchMembers({ fullName: memberInput, employerId, status: 'ACTIVE', size: 20 }),
        enabled: memberInput.length >= 2,
        staleTime: 5000
    });

    // الوثيقة التأمينية
    useEffect(() => {
        if (!member || !employerId) { setPolicyId(null); setPolicyInfo(null); return; }
        benefitPoliciesService.getEffectiveBenefitPolicy(employerId)
            .then(p => { if (p) { setPolicyId(p.id); setPolicyInfo(p); } else { setPolicyId(null); setPolicyInfo(null); } })
            .catch(() => { setPolicyId(null); setPolicyInfo(null); });
    }, [member, employerId]);

    const memberOptions = useMemo(() => {
        const c = memberResults?.data?.content ?? memberResults?.content;
        return Array.isArray(c) ? c : [];
    }, [memberResults]);

    const serviceOptions = useMemo(() =>
        (contractedRaw || []).map(s => ({
            ...s,
            label: `${s.serviceCode ? '[' + s.serviceCode + '] ' : ''}${s.serviceName || ''}`
        })), [contractedRaw]);

    const batchContent = useMemo(() =>
        batchData?.data?.content ?? batchData?.content ?? [], [batchData]);
    const batchTotal = batchData?.data?.totalElements ?? batchData?.totalElements ?? 0;

    // ── التحقق من التغطية التأمينية ──────────────────────────────────────────
    const fetchCoverage = useCallback(async (service) => {
        if (!policyId || !service?.serviceId || !applyBenefits)
            return { coveragePercent: null, requiresPreApproval: false, notCovered: false };
        try {
            const r = await checkServiceCoverage(policyId, service.serviceId);
            return {
                coveragePercent: r?.coveragePercent ?? null,
                requiresPreApproval: r?.requiresPreApproval ?? false,
                notCovered: r?.covered === false
            };
        } catch { return { coveragePercent: null, requiresPreApproval: false, notCovered: false }; }
    }, [policyId, applyBenefits]);

    // ── منطق الجدول ──────────────────────────────────────────────────────────
    const recompute = useCallback((line) => {
        const qty = Math.max(0, parseInt(line.quantity) || 0);
        const price = Math.max(0, parseFloat(line.unitPrice) || 0);
        const total = parseFloat((price * qty).toFixed(2));
        let byCompany, byEmployee;
        if (line.coveragePercent !== null && line.coveragePercent !== undefined && applyBenefits) {
            byCompany = parseFloat((total * line.coveragePercent / 100).toFixed(2));
            byEmployee = parseFloat((total - byCompany).toFixed(2));
        } else {
            byEmployee = Math.max(0, parseFloat(line.byEmployee) || 0);
            byCompany = parseFloat(Math.max(0, total - byEmployee).toFixed(2));
        }
        return { ...line, total, byCompany, byEmployee };
    }, [applyBenefits]);

    const updateLine = useCallback((idx, patch) => {
        setLines(prev => { const n = [...prev]; n[idx] = recompute({ ...n[idx], ...patch }); return n; });
        setIsDirty(true);
    }, [recompute]);

    const handleServiceChange = useCallback(async (idx, svc) => {
        const cov = await fetchCoverage(svc);
        updateLine(idx, { service: svc, description: svc?.serviceName || '', unitPrice: svc?.price || 0, ...cov });
    }, [fetchCoverage, updateLine]);

    useEffect(() => {
        if (!policyId) return;
        lines.forEach((line, idx) => {
            if (!line.service) return;
            fetchCoverage(line.service).then(cov =>
                setLines(prev => { const n = [...prev]; n[idx] = recompute({ ...n[idx], ...cov }); return n; })
            );
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [policyId, applyBenefits]);

    const addLine = useCallback(() => { setLines(p => [...p, newLine()]); setIsDirty(true); }, []);
    const removeLine = useCallback((idx) => {
        setLines(p => p.length === 1 ? [newLine()] : p.filter((_, i) => i !== idx));
        setIsDirty(true);
    }, []);

    const totals = useMemo(() => ({
        total: lines.reduce((s, l) => s + (l.total || 0), 0),
        company: lines.reduce((s, l) => s + (l.byCompany || 0), 0),
        employee: lines.reduce((s, l) => s + (parseFloat(l.byEmployee) || 0), 0)
    }), [lines]);

    const resetForm = useCallback(() => {
        setMember(null); setMemberInput(''); setDiagnosis('');
        setComplaint(''); setNotes(''); setLines([newLine(), newLine()]);
        setApplyBenefits(true); setIsDirty(false);
        setTimeout(() => memberRef.current?.focus(), 120);
    }, []);

    const handleSave = async () => {
        if (!member) { enqueueSnackbar(t('claimEntry.validationMember'), { variant: 'error' }); return; }
        if (lines.some(l => !l.service)) { enqueueSnackbar(t('claimEntry.validationService'), { variant: 'error' }); return; }
        setSaving(true);
        try {
            await backlogService.createManual({
                memberId: member.id, providerId: parseInt(providerId),
                serviceDate: defaultDate, diagnosis, complaint, applyBenefits, policyId, notes,
                lines: lines.map(l => ({
                    serviceId: l.service?.serviceId,
                    description: l.description,
                    quantity: l.quantity,
                    serviceDate: l.serviceDate || defaultDate,
                    requestedAmount: l.total,
                    byCompany: l.byCompany,
                    byEmployee: l.byEmployee,
                    coveragePercent: l.coveragePercent,
                    refusalTypes: l.refusalTypes
                }))
            });
            enqueueSnackbar(t('claimEntry.savedSuccess'), { variant: 'success' });
            queryClient.invalidateQueries({ queryKey: ['batch-claims-entry'] });
            resetForm();
        } catch (err) {
            enqueueSnackbar(err.message || t('claimEntry.saveFailed'), { variant: 'error' });
        } finally { setSaving(false); }
    };

    const detailUrl = `/claims/batches/detail?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`;
    const monthLabel = MONTHS_AR[(month || 1) - 1];

    // ══════════════════════════════════════════════════════════════════════════
    // الواجهة
    // ══════════════════════════════════════════════════════════════════════════
    return (
        <Box dir="rtl" sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 130px)', overflow: 'hidden' }}>

            {/* ═══ رأس الصفحة المضغوط ═══ */}
            <Box sx={{ flexShrink: 0, mb: 1 }}>
                <ModernPageHeader
                    title={`${t('claimEntry.pageTitle')} — ${monthLabel} ${year || ''}`}
                    subtitle={`${t('providers.singular')}: ${provider?.name || '...'} | ${t('employers.singular')}: ${employer?.name || '...'}`}
                    icon={<ReceiptIcon />}
                    actions={
                        <Stack direction="row" spacing={1} alignItems="center">
                            <Button variant="outlined" size="small" startIcon={<FileDownloadIcon />}
                                sx={{ color: '#1b5e20', borderColor: '#1b5e20', fontWeight: 700, borderRadius: 1.5, '&:hover': { bgcolor: '#1b5e2012' } }}>
                                {t('claimEntry.exportExcel')}
                            </Button>
                            <Button variant="outlined" size="small" color="info" startIcon={<PrintIcon />}
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
                <Box sx={{ width: 220, flexShrink: 0, display: 'flex', flexDirection: 'column', order: -1 }}>
                    <Paper variant="outlined" sx={{
                        flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                        borderRadius: 2.5, p: 1.5,
                        boxShadow: '0 2px 8px rgba(0,0,0,0.04)'
                    }}>
                        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mb: 1 }}>
                            <HistoryIcon sx={{ fontSize: 15, color: 'text.secondary' }} />
                            <Typography variant="subtitle2" fontWeight={900} sx={{ fontSize: '0.77rem', flex: 1 }}>
                                {t('claimEntry.batchHistory')}
                            </Typography>
                            <Chip label={batchTotal} size="small" color="secondary" variant="outlined"
                                sx={{ fontWeight: 800, fontSize: '0.64rem', height: 18, '& .MuiChip-label': { px: 0.75 } }} />
                        </Stack>
                        <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.63rem', mb: 1, display: 'block' }}>
                            {monthLabel} {year}
                        </Typography>
                        <Divider sx={{ mb: 1 }} />

                        {loadingBatch ? (
                            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                                <CircularProgress size={22} thickness={5} />
                            </Box>
                        ) : (
                            <Stack spacing={0.75} sx={{ flex: 1, overflowY: 'auto' }}>
                                {batchContent.map(c => (
                                    <Paper key={c.id} variant="outlined" sx={{
                                        p: 1, borderRadius: 1.5, cursor: 'pointer', flexShrink: 0,
                                        transition: 'all 0.15s',
                                        '&:hover': {
                                            bgcolor: alpha(theme.palette.primary.main, 0.05),
                                            borderColor: 'primary.light',
                                            transform: 'translateX(2px)' // حركة لليمين
                                        }
                                    }}>
                                        <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={0.5}>
                                            <Typography variant="caption" fontWeight={800}
                                                sx={{ fontSize: '0.7rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {c.memberName}
                                            </Typography>
                                            <Typography variant="caption" fontWeight={900} sx={{
                                                color: 'success.dark', bgcolor: alpha('#2e7d32', 0.09),
                                                px: 0.6, py: 0.1, borderRadius: 1, fontSize: '0.63rem', whiteSpace: 'nowrap'
                                            }}>
                                                {(c.requestedAmount || 0).toFixed(2)}
                                            </Typography>
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
                                {!batchContent.length && (
                                    <Box sx={{ textAlign: 'center', py: 3, opacity: 0.3 }}>
                                        <ReceiptIcon sx={{ fontSize: 28, mb: 0.5 }} />
                                        <Typography variant="caption" display="block" fontWeight={700} sx={{ fontSize: '0.68rem' }}>
                                            {t('claimEntry.noHistoryYet')}
                                        </Typography>
                                    </Box>
                                )}
                            </Stack>
                        )}

                        <Divider sx={{ mt: 1, mb: 1 }} />
                        <Button fullWidth variant="text" size="small" color="primary"
                            onClick={() => navigate(detailUrl)} sx={{ fontWeight: 800, fontSize: '0.68rem' }}>
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
                                    sx={{ fontWeight: 800, fontSize: '0.7rem' }}
                                />
                                {policyInfo && (
                                    <Chip icon={<PolicyIcon sx={{ fontSize: 12 }} />} size="small"
                                        label={`${t('claimEntry.benefitPolicy')}: ${policyInfo.policyNumber || policyInfo.name || 'مفعّلة'}`}
                                        color="success" variant="outlined"
                                        sx={{ fontWeight: 700, fontSize: '0.66rem' }}
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
                                    sx={{ fontWeight: 800, borderRadius: 1.5, fontSize: '0.72rem', py: 0.4 }}>
                                    {saving ? t('claimEntry.saving') : t('claimEntry.tempSave')}
                                </Button>
                            </Stack>
                        </Box>

                        {/* ── حقول الرأس (4 أعمدة مضغوطة) ── */}
                        <Box sx={{ flexShrink: 0, px: 2.5, py: 2.5, bgcolor: 'background.paper' }}>
                            <Box sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(4, 1fr)' },
                                gap: 3.5,
                                alignItems: 'flex-start'
                            }}>
                                {/* عمود ١: جهة الخدمة + رقم البطاقة */}
                                <Box>
                                    <Stack spacing={2.5}>
                                        <Box>
                                            <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                                {t('claimEntry.provider')}
                                            </Typography>
                                            <Typography variant="body2" fontWeight={600} color={provider?.name ? 'text.primary' : 'text.disabled'}>
                                                {provider?.name || '—'}
                                            </Typography>
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                                {t('claimEntry.cardNumber')}
                                            </Typography>
                                            <Typography variant="body2" fontWeight={600} sx={{ fontFamily: 'monospace' }}
                                                color={member?.cardNumber ? 'text.primary' : 'text.disabled'}>
                                                {member?.cardNumber || '—'}
                                            </Typography>
                                        </Box>
                                    </Stack>
                                </Box>

                                {/* عمود ٢: المستفيد + الوثيقة */}
                                <Box>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                        {t('claimEntry.patient')}
                                        <Typography component="span" color="error.main"> *</Typography>
                                    </Typography>
                                    <Autocomplete size="small" fullWidth options={memberOptions} loading={searchingMember}
                                        value={member}
                                        onChange={(_, v) => { setMember(v); setIsDirty(true); }}
                                        onInputChange={(_, v) => setMemberInput(v)}
                                        getOptionLabel={o => `${o.fullName || ''} · ${o.cardNumber || ''}`}
                                        isOptionEqualToValue={(o, v) => o.id === v?.id}
                                        noOptionsText={memberInput.length < 2 ? 'أدخل حرفين للبحث' : 'لا توجد نتائج'}
                                        renderInput={params => (
                                            <TextField {...params} inputRef={memberRef} variant="standard" autoFocus
                                                placeholder={t('claimEntry.searchPatient')} sx={inlineSx}
                                                InputProps={{
                                                    ...params.InputProps,
                                                    startAdornment: (
                                                        <InputAdornment position="start">
                                                            <SearchIcon sx={{ color: 'text.disabled', fontSize: 15, ml: 0.5 }} />
                                                        </InputAdornment>
                                                    )
                                                }}
                                            />
                                        )}
                                    />
                                    {policyInfo && (
                                        <Box sx={{ mt: 1.5, p: 1, borderRadius: 1.5, bgcolor: alpha('#2e7d32', 0.06), border: '1px solid', borderColor: alpha('#2e7d32', 0.25) }}>
                                            <Stack direction="row" spacing={1} alignItems="center">
                                                <PolicyIcon sx={{ color: 'success.main', fontSize: 16 }} />
                                                <Box>
                                                    <Typography variant="caption" fontWeight={800} color="success.dark" display="block" sx={{ lineHeight: 1.2, fontSize: '0.75rem', mb: 0.2 }}>
                                                        {policyInfo.policyNumber || policyInfo.name}
                                                    </Typography>
                                                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>
                                                        {t('claimEntry.annualLimit')}: {policyInfo.annualLimit?.toLocaleString() || '—'}
                                                    </Typography>
                                                </Box>
                                            </Stack>
                                        </Box>
                                    )}
                                    {member && !policyInfo && (
                                        <Alert severity="warning" icon={false} sx={{ py: 0.2, mt: 1, fontSize: '0.65rem', borderRadius: 1 }}>
                                            {t('claimEntry.noPolicyFound')}
                                        </Alert>
                                    )}
                                </Box>

                                {/* عمود ٣: التشخيص والشكوى */}
                                <Box>
                                    <Stack spacing={2.5}>
                                        <Box>
                                            <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                                {t('claimEntry.diagnosis')}
                                            </Typography>
                                            <TextField fullWidth size="small" variant="standard"
                                                placeholder={t('claimEntry.diagnosisPlaceholder')}
                                                value={diagnosis}
                                                onChange={e => { setDiagnosis(e.target.value); setIsDirty(true); }}
                                                sx={inlineSx}
                                            />
                                        </Box>
                                        <Box>
                                            <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                                {t('claimEntry.complaint')}
                                            </Typography>
                                            <TextField fullWidth size="small" variant="standard"
                                                placeholder={t('claimEntry.complaintPlaceholder')}
                                                value={complaint}
                                                onChange={e => { setComplaint(e.target.value); setIsDirty(true); }}
                                                sx={inlineSx}
                                            />
                                        </Box>
                                    </Stack>
                                </Box>

                                {/* عمود ٤: الملاحظات */}
                                <Box>
                                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 700, display: 'block', mb: 0.5 }}>
                                        {t('claimEntry.notes')}
                                    </Typography>
                                    <TextField fullWidth size="small" variant="outlined" multiline rows={3}
                                        placeholder={t('claimEntry.notesPlaceholder')}
                                        value={notes}
                                        onChange={e => { setNotes(e.target.value); setIsDirty(true); }}
                                        sx={{ '& .MuiOutlinedInput-root': { borderRadius: 1.5, fontSize: '0.8rem' } }}
                                    />
                                    <FormControlLabel sx={{ mt: 1, mr: 0 }}
                                        control={<Checkbox size="small" checked={applyBenefits} color="success"
                                            onChange={e => { setApplyBenefits(e.target.checked); setIsDirty(true); }} />}
                                        label={<Typography variant="caption" fontWeight={700} color="text.secondary">
                                            {t('claimEntry.applyBenefits')}
                                        </Typography>}
                                    />
                                </Box>
                            </Box>
                        </Box>

                        <Divider />

                        {/* ── رأس قسم الجدول ── */}
                        <Box sx={{
                            flexShrink: 0, px: 2.5, py: 0.75,
                            bgcolor: alpha('#E8F5F1', 0.55),
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            borderBottom: `1px solid ${theme.palette.divider}`
                        }}>
                            <Typography variant="subtitle2" fontWeight={900} color="#0D4731" sx={{ fontSize: '0.82rem' }}>
                                {t('claimEntry.serviceLines')}
                            </Typography>
                            <Chip size="small" variant="outlined"
                                label={`${lines.length} بند — ${t('common.total')}: ${totals.total.toFixed(2)}`}
                                sx={{ fontWeight: 700, borderRadius: 1.5, borderColor: '#1b5e20', color: '#1b5e20', fontSize: '0.68rem' }}
                            />
                        </Box>

                        {/* ── الجدول (إلزام قراءة الجدول من اليمين لليسار باستخدام dir="rtl" صريح) ── */}
                        <TableContainer dir="rtl" sx={{ flex: 1, overflow: 'auto' }}>
                            <Table dir="rtl" size="small" stickyHeader sx={{ minWidth: 760 }}>
                                <TableHead>
                                    <TableRow>
                                        <TH align="center" w={185}>الخدمة الطبية</TH>
                                        <TH align="center" w={130}>الوصف</TH>
                                        <TH align="center" w={52}>الكمية</TH>
                                        <TH align="center" w={110}>التاريخ</TH>
                                        <TH align="center" w={80}>سعر الوحدة</TH>
                                        <TH align="center" w={80} sxOver={{ color: '#1b5e20' }}>حصة الشركة</TH>
                                        <TH align="center" w={80} sxOver={{ color: '#b45309' }}>حصة المشترك</TH>
                                        <TH align="center" w={85}>الإجمالي</TH>
                                        <TH align="center" w={36}></TH>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {lines.map((line, idx) => (
                                        <TableRow key={line.id} hover sx={{
                                            '& td': { py: 0.6, px: 1.5, borderBottom: `1px solid ${alpha(theme.palette.divider, 0.4)}` },
                                            ...(line.notCovered && { bgcolor: alpha(theme.palette.error.main, 0.03) })
                                        }}>
                                            <TableCell align="center">
                                                <Autocomplete size="small" options={serviceOptions} loading={loadingServices}
                                                    value={line.service}
                                                    onChange={(_, v) => handleServiceChange(idx, v)}
                                                    getOptionLabel={o => o.label || ''}
                                                    isOptionEqualToValue={(o, v) => o.serviceId === v?.serviceId}
                                                    noOptionsText={t('claimEntry.noActiveContract')}
                                                    renderInput={params => (
                                                        <TextField {...params} variant="standard" sx={inlineSx}
                                                            placeholder={t('claimEntry.selectService')} />
                                                    )}
                                                    renderOption={(props, o) => (
                                                        <li {...props}>
                                                            <Box>
                                                                <Typography variant="body2" fontWeight={600} sx={{ fontSize: '0.78rem' }}>{o.serviceName}</Typography>
                                                                <Typography variant="caption" color="text.secondary">{o.serviceCode}</Typography>
                                                            </Box>
                                                        </li>
                                                    )}
                                                />
                                                {line.service && line.notCovered && (
                                                    <Typography variant="caption" color="error"
                                                        sx={{ display: 'flex', alignItems: 'center', gap: 0.3, fontSize: '0.62rem', mt: 0.25 }}>
                                                        <WarningIcon sx={{ fontSize: 10 }} />
                                                        {t('claimEntry.notCovered')}
                                                    </Typography>
                                                )}
                                                {line.service && !line.notCovered && line.coveragePercent !== null && (
                                                    <Typography variant="caption" color="success.main"
                                                        sx={{ display: 'flex', alignItems: 'center', gap: 0.3, fontSize: '0.62rem', mt: 0.25 }}>
                                                        <InfoIcon sx={{ fontSize: 10 }} />
                                                        تغطية {line.coveragePercent}%
                                                        {line.requiresPreApproval && ' · موافقة مسبقة'}
                                                    </Typography>
                                                )}
                                            </TableCell>
                                            <TableCell align="center">
                                                <TextField fullWidth variant="standard" size="small" value={line.description}
                                                    onChange={e => updateLine(idx, { description: e.target.value })} sx={{ ...inlineSx, '& input': { textAlign: 'center' } }} />
                                            </TableCell>
                                            <TableCell align="center">
                                                <TextField variant="standard" size="small" type="number" value={line.quantity}
                                                    onChange={e => updateLine(idx, { quantity: Math.max(1, parseInt(e.target.value) || 1) })}
                                                    sx={{ ...inlineSx, width: 38 }}
                                                    inputProps={{ style: { textAlign: 'center', fontWeight: 700 }, min: 1 }} />
                                            </TableCell>
                                            <TableCell align="center">
                                                <TextField variant="standard" size="small" type="date"
                                                    value={line.serviceDate || defaultDate}
                                                    onChange={e => updateLine(idx, { serviceDate: e.target.value })}
                                                    sx={{ ...inlineSx, '& input': { fontSize: '0.85rem' } }} />
                                            </TableCell>
                                            <TableCell align="center">
                                                <TextField variant="standard" size="small" type="number" value={line.unitPrice}
                                                    onChange={e => updateLine(idx, { unitPrice: parseFloat(e.target.value) || 0 })}
                                                    sx={{ ...inlineSx, width: 62 }}
                                                    inputProps={{ style: { textAlign: 'center', fontWeight: 700 }, min: 0 }} />
                                            </TableCell>
                                            <TableCell align="center">
                                                <Typography variant="body2" fontWeight={800} color="success.main" sx={{ fontSize: '0.8rem' }}>
                                                    {(line.byCompany || 0).toFixed(2)}
                                                </Typography>
                                            </TableCell>
                                            <TableCell align="center">
                                                {(line.coveragePercent !== null && applyBenefits) ? (
                                                    <Typography variant="body2" fontWeight={700} color="warning.dark" sx={{ fontSize: '0.8rem' }}>
                                                        {(line.byEmployee || 0).toFixed(2)}
                                                    </Typography>
                                                ) : (
                                                    <TextField variant="standard" size="small" type="number" value={line.byEmployee}
                                                        onChange={e => updateLine(idx, { byEmployee: parseFloat(e.target.value) || 0 })}
                                                        sx={{ ...inlineSx, width: 58 }}
                                                        inputProps={{ style: { textAlign: 'center', color: theme.palette.warning.dark, fontWeight: 700 }, min: 0 }} />
                                                )}
                                            </TableCell>
                                            <TableCell align="center">
                                                <Typography variant="body2" fontWeight={900} color="primary.main" sx={{ fontSize: '0.8rem' }}>
                                                    {(line.total || 0).toFixed(2)}
                                                </Typography>
                                            </TableCell>
                                            <TableCell align="center">
                                                <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
                                                    <DeleteIcon sx={{ fontSize: 13 }} />
                                                </IconButton>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                    <TableRow>
                                        <TableCell colSpan={9} sx={{ py: 1, textAlign: 'center', bgcolor: alpha('#E8F5F1', 0.25) }}>
                                            <Button size="small" startIcon={<AddIcon />} onClick={addLine}
                                                sx={{
                                                    color: '#1b5e20', fontWeight: 800, borderRadius: 2, px: 4,
                                                    border: '1px dashed #1b5e2055', fontSize: '0.76rem',
                                                    '&:hover': { bgcolor: '#1b5e200d' }
                                                }}>
                                                {t('claimEntry.addLine')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                </TableBody>
                            </Table>
                        </TableContainer>

                        {/* ── شريط الحفظ السفلي ── */}
                        <Box sx={{
                            flexShrink: 0, px: 2.5, py: 1.25,
                            bgcolor: alpha(theme.palette.primary.main, 0.03),
                            borderTop: `1px solid ${theme.palette.divider}`,
                            display: 'flex', justifyContent: 'flex-start', gap: 1.5, alignItems: 'center'
                        }}>
                            <Button variant="contained"
                                startIcon={saving ? <CircularProgress size={13} color="inherit" /> : <SaveIcon sx={{ fontSize: 15 }} />}
                                onClick={handleSave} disabled={saving || !isDirty}
                                sx={{
                                    borderRadius: 2, px: 4.5, fontWeight: 900, fontSize: '0.83rem',
                                    boxShadow: `0 4px 12px ${alpha(theme.palette.primary.main, 0.22)}`,
                                    '&:hover': { transform: 'translateY(-1px)', boxShadow: `0 6px 16px ${alpha(theme.palette.primary.main, 0.3)}` },
                                    transition: 'all 0.18s'
                                }}>
                                {saving ? t('claimEntry.saving') : t('claimEntry.saveAndAdd')}
                            </Button>
                            <Button variant="text" color="inherit"
                                startIcon={<DiscardIcon sx={{ ml: 1, mr: 0 }} />}
                                onClick={resetForm} disabled={!isDirty}
                                sx={{ borderRadius: 2, color: 'text.secondary', fontWeight: 700, fontSize: '0.8rem' }}>
                                {t('claimEntry.discardChanges')}
                            </Button>

                            <Box sx={{ mr: 'auto', display: 'flex', gap: 3, alignItems: 'center' }}>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.62rem', display: 'block' }}>
                                        حصة الشركة
                                    </Typography>
                                    <Typography variant="body2" fontWeight={900} color="success.main" sx={{ fontSize: '0.82rem' }}>
                                        {totals.company.toFixed(2)}
                                    </Typography>
                                </Box>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.62rem', display: 'block' }}>
                                        حصة المشترك
                                    </Typography>
                                    <Typography variant="body2" fontWeight={900} color="warning.dark" sx={{ fontSize: '0.82rem' }}>
                                        {totals.employee.toFixed(2)}
                                    </Typography>
                                </Box>
                                <Box sx={{ textAlign: 'center' }}>
                                    <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.62rem', display: 'block' }}>
                                        الإجمالي الكلي
                                    </Typography>
                                    <Typography variant="subtitle1" fontWeight={900} color="primary.main" sx={{ fontSize: '0.95rem' }}>
                                        {totals.total.toFixed(2)}
                                    </Typography>
                                </Box>
                            </Box>
                        </Box>
                    </Paper>
                </Box>
            </Box>
        </Box>
    );
}
