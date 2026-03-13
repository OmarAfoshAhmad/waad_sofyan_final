/**
 * Claim Batch Detail View
 * Shows a full list of claims (transactions) within a specific batch.
 * Matches Odoo layout but with system visual identity.
 */

import { useState, useMemo, useRef, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Box,
    Stack,
    Typography,
    Button,
    TextField,
    InputAdornment,
    Chip,
    IconButton,
    Tooltip,
    Avatar,
    Divider,
    MenuItem,
    FormControl,
    alpha,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Checkbox
} from '@mui/material';

import {
    Search as SearchIcon,
    Add as AddIcon,
    ArrowBack as ArrowBackIcon,
    Visibility as ViewIcon,
    Print as PrintIcon,
    FilterList as FilterIcon,
    Business as BusinessIcon,
    ReceiptLong as ReceiptIcon,
    FileDownload as ExcelIcon,
    PictureAsPdf as PdfIcon,
    Refresh as RefreshIcon,
    FilterAltOff as FilterAltOffIcon,
    PauseCircle as SuspendIcon
} from '@mui/icons-material';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import ExcelJS from 'exceljs';

// project components
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import GenericDataTable from 'components/GenericDataTable';
import useTableState from 'hooks/useTableState';
import BatchPrintReport from './components/BatchPrintReport';
import RejectedBatchPrintReport from './components/RejectedBatchPrintReport';

// services
import { useReactToPrint } from 'react-to-print';
import claimsService from 'services/api/claims.service';
import employersService from 'services/api/employers.service';
import providersService from 'services/api/providers.service';
import claimBatchesService from 'services/api/claim-batches.service';
import { settlementBatchesService } from 'services/api/settlement.service';

const MONTHS_AR = [
    'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
    'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];

const MONTHS_EN = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];

// ===========================================
// HELPERS
// ===========================================

export default function ClaimBatchDetail() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const employerId = searchParams.get('employerId');
    const providerId = searchParams.get('providerId');
    const month = parseInt(searchParams.get('month'));
    const year = parseInt(searchParams.get('year'));

    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [printClaims, setPrintClaims] = useState([]);
    const [selectedClaimIds, setSelectedClaimIds] = useState([]);
    const [singleClaimForPrint, setSingleClaimForPrint] = useState(null);
    const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
    const [suspendComment, setSuspendComment] = useState('');
    const [suspendingClaimId, setSuspendingClaimId] = useState(null);
    const tableState = useTableState({
        initialPageSize: 10,
        defaultSort: { field: 'serviceDate', direction: 'desc' }
    });
    const batchReportRef = useRef(null);
    const rejectedReportRef = useRef(null);
    const singleClaimPrintRef = useRef(null);
    const { enqueueSnackbar } = useSnackbar();
    const queryClient = useQueryClient();

    // Detect superadmin / reviewer role from session storage
    const currentUserRole = (() => {
        try {
            const u = localStorage.getItem('user_details');
            if (u) {
                const parsed = JSON.parse(u);
                return parsed?.role || (Array.isArray(parsed?.roles) ? parsed.roles[0] : null) || '';
            }
        } catch { /* ignore */ }
        return '';
    })();
    const canSuspend = currentUserRole === 'SUPER_ADMIN' || currentUserRole === 'MEDICAL_REVIEWER' || currentUserRole === 'ACCOUNTANT';

    const suspendMutation = useMutation({
        mutationFn: ({ claimId, comment }) =>
            claimsService.updateReview(claimId, { status: 'NEEDS_CORRECTION', reviewerComment: comment }),
        onSuccess: () => {
            enqueueSnackbar('تم تعليق المطالبة بنجاح', { variant: 'success' });
            setSuspendDialogOpen(false);
            setSuspendComment('');
            setSuspendingClaimId(null);
            queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
        },
        onError: (err) => {
            enqueueSnackbar(err?.response?.data?.message || 'حدث خطأ أثناء تعليق المطالبة', { variant: 'error' });
        }
    });

    const handleOpenSuspend = (claimId) => {
        setSuspendingClaimId(claimId);
        setSuspendComment('');
        setSuspendDialogOpen(true);
    };

    const handleConfirmSuspend = () => {
        if (!suspendComment.trim()) {
            enqueueSnackbar('يجب إدخال سبب التعليق', { variant: 'warning' });
            return;
        }
        suspendMutation.mutate({ claimId: suspendingClaimId, comment: suspendComment });
    };

    // 0. Fetch real batch info
    const { data: realBatch } = useQuery({
        queryKey: ['claim-batch-detail', providerId, employerId, year, month],
        queryFn: () => claimBatchesService.getCurrentBatch(providerId, employerId, year, month),
        enabled: !!providerId && !!employerId
    });
    const { data: employer } = useQuery({
        queryKey: ['employer-detail', employerId],
        queryFn: () => employersService.getById(employerId),
        enabled: !!employerId
    });

    const { data: provider } = useQuery({
        queryKey: ['provider-detail', providerId],
        queryFn: () => providersService.getById(providerId),
        enabled: !!providerId
    });

    // 2. Fetch Claims in this Batch
    const { data: claimsResponse, isLoading } = useQuery({
        queryKey: ['batch-claims-detail', employerId, providerId, month, year],
        queryFn: async () => {
            const dateFrom = `${year}-${String(month).padStart(2, '0')}-01`;
            const dateTo = `${year}-${String(month).padStart(2, '0')}-31`;
            return await claimsService.list({
                employerId,
                providerId,
                dateFrom,
                dateTo,
                size: 100
            });
        }
    });

    const claims = useMemo(() => {
        let items = claimsResponse?.items || claimsResponse?.content || [];

        // 1. Search Filter
        if (searchTerm) {
            const lowerSearch = searchTerm.toLowerCase();
            items = items.filter(c =>
                c.memberName?.toLowerCase().includes(lowerSearch) ||
                c.memberCardNumber?.includes(searchTerm) ||
                c.claimNumber?.includes(searchTerm)
            );
        }

        // 2. Status Filter
        if (statusFilter) {
            items = items.filter(c => c.status === statusFilter);
        }

        return items;
    }, [claimsResponse, searchTerm, statusFilter]);

    // Keep print payload synced with current list until user requests detailed refresh.
    useEffect(() => {
        setPrintClaims(claims || []);
    }, [claims]);

    const sortedClaims = useMemo(() => {
        const sorting = tableState.sorting?.[0];
        if (!sorting?.id) return claims;

        const direction = sorting.desc ? -1 : 1;
        const claimsWithOrder = claims.map((claim, idx) => ({ claim, idx }));

        const getSortValue = (claim, idx) => {
            switch (sorting.id) {
                case 'patient':
                    return String(claim.memberName || '').toLowerCase();
                case 'serviceDate':
                    return new Date(claim.serviceDate || 0).getTime() || 0;
                case 'status':
                    return String(claim.status || '').toLowerCase();
                case 'amount':
                    return Number(claim.requestedAmount) || 0;
                case 'covered':
                    return Number(claim.approvedAmount) || 0;
                case 'refused': {
                    const refused = (claim.status === 'REJECTED' && (!claim.refusedAmount || claim.refusedAmount === 0))
                        ? claim.requestedAmount
                        : (claim.refusedAmount || 0);
                    return Number(refused) || 0;
                }
                case 'copay':
                    return Number(claim.patientCoPay) || 0;
                case 'paid':
                    return Number(claim.netProviderAmount) || 0;
                case 'index':
                    return idx;
                default:
                    return String(claim[sorting.id] || '').toLowerCase();
            }
        };

        return claimsWithOrder
            .sort((a, b) => {
                const av = getSortValue(a.claim, a.idx);
                const bv = getSortValue(b.claim, b.idx);

                if (typeof av === 'number' && typeof bv === 'number') {
                    return (av - bv) * direction;
                }

                if (av < bv) return -1 * direction;
                if (av > bv) return 1 * direction;
                return 0;
            })
            .map((entry) => entry.claim);
    }, [claims, tableState.sorting]);

    // Paginated Data for the table
    const paginatedClaims = useMemo(() => {
        const start = tableState.page * tableState.pageSize;
        return sortedClaims.slice(start, start + tableState.pageSize);
    }, [sortedClaims, tableState.page, tableState.pageSize]);

    const tableRows = useMemo(() => paginatedClaims, [paginatedClaims]);

    // Batch Code (Real or Fallback)
    const batchCode = useMemo(() => {
        if (realBatch) return realBatch.batchCode;
        if (employer) return `${employer.code || 'EMP'}${String(year).substring(2)}-BATCH`;
        return '...';
    }, [realBatch, employer, year]);

    // -------------------------------------------------------------------------
    // EXPORT HANDLERS
    // -------------------------------------------------------------------------

    const handleExportExcel = async () => {
        const workbook = new ExcelJS.Workbook();
        const worksheet = workbook.addWorksheet('المطالبات');

        worksheet.columns = [
            { header: '#', key: 'index', width: 10 },
            { header: 'المرجع', key: 'ref', width: 25 },
            { header: 'مقدم الخدمة', key: 'provider', width: 50 },
            { header: 'المريض', key: 'patient', width: 30 },
            { header: 'تاريخ الخدمة', key: 'serviceDate', width: 18 },
            { header: 'الحالة', key: 'status', width: 15 },
            { header: 'المبلغ الإجمالي', key: 'amount', width: 20 },
            { header: 'المعتمد', key: 'covered', width: 20 },
            { header: 'المرفوض', key: 'refused', width: 20 },
            { header: 'نصيب المؤمن عليه', key: 'copay', width: 22 },
            { header: 'المستحق للمزود', key: 'paid', width: 20 }
        ];

        worksheet.views = [{ rightToLeft: true }];

        claims.forEach((c, idx) => {
            worksheet.addRow({
                index: idx + 1,
                ref: `${batchCode}/${String(idx + 1).padStart(4, '0')}`,
                provider: provider?.name || '-',
                patient: c.memberName || '-',
                serviceDate: c.serviceDate || '-',
                status: c.status || 'APPROVED',
                amount: c.requestedAmount || 0,
                covered: c.approvedAmount || 0,
                refused: c.refusedAmount || 0,
                copay: c.patientCoPay || 0,
                paid: c.netProviderAmount || 0
            });
        });

        const buffer = await workbook.xlsx.writeBuffer();

        // Native browser download (no file-saver needed)
        const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `Batch_${batchCode}_${new Date().toISOString().split('T')[0]}.xlsx`);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    };

    const loadDetailedClaimsForPrint = async () => {
        if (!claims || claims.length === 0) {
            setPrintClaims([]);
            return;
        }

        const detailed = await Promise.all(
            claims.map(async (claim) => {
                try {
                    if (!claim?.id) return claim;
                    const fresh = await claimsService.getById(claim.id);
                    return { ...claim, ...fresh };
                } catch {
                    return claim;
                }
            })
        );

        setPrintClaims(detailed);
    };

    const handlePrint = useReactToPrint({
        contentRef: batchReportRef,
        documentTitle: `تقرير_المطالبات_${year}_${String(month).padStart(2, '0')}`,
        pageStyle: `@page { size: A4 portrait; margin: 15mm; } body { direction: rtl; -webkit-print-color-adjust: exact; print-color-adjust: exact; }`,
        onBeforePrint: async () => {
            await loadDetailedClaimsForPrint();
        }
    });

    const handlePrintSingle = useReactToPrint({
        contentRef: singleClaimPrintRef,
        documentTitle: `مطالبة_${batchCode}`,
        pageStyle: `@page { size: A4 portrait; margin: 15mm; } body { direction: rtl; -webkit-print-color-adjust: exact; print-color-adjust: exact; }`,
    });

    const handleDownloadPdf = async () => {
        // Validate batchId: extract only numeric/UUID-safe chars to prevent injection
        const rawBatchId = realBatch?.id;
        const batchId = rawBatchId != null ? String(rawBatchId).replace(/[^a-zA-Z0-9\-_]/g, '') : null;
        if (!batchId) {
            enqueueSnackbar('لم يتم العثور على معرف الدفعة', { variant: 'error' });
            return;
        }
        try {
            enqueueSnackbar('جاري تحضير ملف PDF...', { variant: 'info' });
            const { blob, filename } = await settlementBatchesService.downloadOfficialPdf(batchId);
            // Sanitize filename: allow only letters, digits, dash, underscore, dot
            const safeFilename = String(filename || 'report.pdf').replace(/[^a-zA-Z0-9_\-\.]/g, '_');
            // Build a safe blob URL — createObjectURL always returns 'blob:origin/...'
            // We use setAttribute to avoid direct property XSS sink
            const objectUrl = URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob]));
            const link = document.createElement('a');
            link.setAttribute('href', objectUrl);
            link.setAttribute('download', safeFilename);
            link.style.display = 'none';
            document.body.appendChild(link);
            link.click();
            setTimeout(() => {
                document.body.removeChild(link);
                URL.revokeObjectURL(objectUrl);
            }, 100);
            enqueueSnackbar('تم تحميل التقرير بنجاح', { variant: 'success' });
        } catch (err) {
            enqueueSnackbar('فشل تحميل التقرير: ' + (err?.message || ''), { variant: 'error' });
        }
    };

    const handleRejectedReport = useReactToPrint({
        contentRef: rejectedReportRef,
        documentTitle: `تقرير_المرفوضات_${batchCode}`,
        pageStyle: `@page { size: A4 portrait; margin: 15mm; } body { direction: rtl; -webkit-print-color-adjust: exact; print-color-adjust: exact; }`,
        onBeforePrint: async () => {
            await loadDetailedClaimsForPrint();
        }
    });

    // Row selection helpers
    const allCurrentIds = useMemo(() => sortedClaims.map(c => c.id), [sortedClaims]);
    const allSelected = allCurrentIds.length > 0 && allCurrentIds.every(id => selectedClaimIds.includes(id));
    const someSelected = allCurrentIds.some(id => selectedClaimIds.includes(id)) && !allSelected;

    const handleToggleAll = () => {
        if (allSelected) {
            setSelectedClaimIds([]);
        } else {
            setSelectedClaimIds(allCurrentIds);
        }
    };

    const handleToggleClaim = (id) => {
        setSelectedClaimIds(prev =>
            prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
        );
    };

    // Table Columns
    const columns = [
        { id: 'select', label: '', minWidth: 40, align: 'center', sortable: false },
        { id: 'index', label: '#', minWidth: 30, align: 'center', sortable: false },
        { id: 'ref', label: 'المرجع', minWidth: 50, sortable: false },
        { id: 'provider', label: 'مقدم الخدمة', minWidth: 150, sortable: false },
        { id: 'patient', label: 'الاسم (المريض)', minWidth: 250, sortable: true },
        { id: 'serviceDate', label: 'تاريخ الخدمة', minWidth: 80, align: 'center', sortable: true },
        { id: 'status', label: 'الحالة', minWidth: 80, align: 'center', sortable: true },
        { id: 'amount', label: 'الإجمالي', minWidth: 110, align: 'right', sortable: true },
        { id: 'covered', label: 'المعتمد', minWidth: 110, align: 'right', sortable: true },
        { id: 'refused', label: 'المرفوض', minWidth: 100, align: 'right', sortable: true },
        { id: 'copay', label: 'نصيب المؤمن', minWidth: 100, align: 'right', sortable: true },
        { id: 'paid', label: 'المستحق', minWidth: 110, align: 'right', sortable: true },
        { id: 'actions', label: 'إجراءات', minWidth: 110, align: 'center', sortable: false }
    ];

    // Totals for footer
    const totals = useMemo(() => {
        return {
            amount:  claims.reduce((s, c) => s + (c.requestedAmount || 0), 0),
            covered: claims.reduce((s, c) => s + (c.approvedAmount || 0), 0),
            refused: claims.reduce((s, c) => {
                const r = (c.status === 'REJECTED' && (!c.refusedAmount || c.refusedAmount === 0))
                    ? c.requestedAmount : (c.refusedAmount || 0);
                return s + r;
            }, 0),
            copay:   claims.reduce((s, c) => s + (c.patientCoPay || 0), 0),
            paid:    claims.reduce((s, c) => s + (c.netProviderAmount || 0), 0)
        };
    }, [claims]);

    const getStatusChip = (status) => {
        const config = {
            'APPROVED': { label: 'معتمدة', color: 'success', bgcolor: '#f6ffed', border: '#b7eb8f' },
            'SETTLED': { label: 'تمت التسوية', color: 'success', bgcolor: '#f6ffed', border: '#b7eb8f' },
            'PAID': { label: 'مدفوعة', color: 'success', bgcolor: '#f6ffed', border: '#b7eb8f' },
            'BATCHED': { label: 'في دفعة', color: 'info', bgcolor: '#e6f7ff', border: '#91d5ff' },
            'NEEDS_CORRECTION': { label: 'معلقة للمراجعة', color: 'warning', bgcolor: '#fffbe6', border: '#ffe58f' },
            'PENDING': { label: 'قيد الانتظار', color: 'warning', bgcolor: '#fffbe6', border: '#ffe58f' },
            'REJECTED': { label: 'مرفوضة', color: 'error', bgcolor: '#fff1f0', border: '#ffa39e' },
            'UNDER_REVIEW': { label: 'تحت المراجعة', color: 'info', bgcolor: '#e6f7ff', border: '#91d5ff' },
            'DRAFT': { label: 'مسودة', color: 'default', bgcolor: '#fafafa', border: '#d9d9d9' },
            'SUBMITTED': { label: 'مقدمة', color: 'info', bgcolor: '#e6f7ff', border: '#91d5ff' }
        };

        const s = config[status] || config['SETTLED'];

        return (
            <Chip
                label={s.label}
                size="small"
                sx={{
                    fontWeight: 700,
                    fontSize: '0.75rem',
                    bgcolor: s.bgcolor || 'action.selected',
                    color: `${s.color}.main`,
                    border: '1px solid',
                    borderColor: s.border || 'divider'
                }}
            />
        );
    };

    const renderCell = (claim, column, index) => {
        switch (column.id) {
            case 'select':
                return (
                    <Checkbox
                        size="small"
                        checked={selectedClaimIds.includes(claim.id)}
                        onChange={() => handleToggleClaim(claim.id)}
                        onClick={(e) => e.stopPropagation()}
                    />
                );
            case 'index':
                return <Typography variant="body2" sx={{ color: 'text.disabled' }}>{index + 1}</Typography>;
            case 'ref':
                return (
                    <Typography variant="body2" fontWeight={700} color="primary.main" dir="ltr">
                        {batchCode}/{String(index + 1).padStart(4, '0')}
                    </Typography>
                );
            case 'provider':
                return (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <BusinessIcon sx={{ fontSize: 16, color: 'text.disabled' }} />
                        <Typography variant="body2">{provider?.name}</Typography>
                    </Stack>
                );
            case 'patient':
                return (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <Avatar sx={{ width: 24, height: 24, fontSize: '0.7rem', bgcolor: 'secondary.light' }}>
                            {claim.memberName?.charAt(0)}
                        </Avatar>
                        <Box>
                            <Typography variant="body2" fontWeight={600}>{claim.memberName}</Typography>
                            <Typography variant="caption" color="text.secondary">{claim.memberCardNumber}</Typography>
                        </Box>
                    </Stack>
                );
            case 'serviceDate':
                return (
                    <Typography variant="body2" color="text.secondary" dir="ltr">
                        {claim.serviceDate || '—'}
                    </Typography>
                );
            case 'status':
                return getStatusChip(claim.status || 'APPROVED');
            case 'amount':
                return <Typography variant="body2" fontWeight={700}>{claim.requestedAmount?.toFixed(2)}</Typography>;
            case 'covered':
                return <Typography variant="body2" color="success.main" fontWeight={700}>{(claim.approvedAmount || 0).toFixed(2)}</Typography>;
            case 'refused':
                const displayRefused = (claim.status === 'REJECTED' && (!claim.refusedAmount || claim.refusedAmount === 0))
                    ? claim.requestedAmount
                    : (claim.refusedAmount || 0);
                return (
                    <Tooltip title={claim.rejectionReason || ''} arrow placement="top">
                        <Typography variant="body2" color="error.main" fontWeight={700}>
                            {displayRefused.toFixed(2)}
                        </Typography>
                    </Tooltip>
                );
            case 'copay':
                return (
                    <Typography variant="body2" color="info.main" fontWeight={600}>
                        {(claim.patientCoPay || 0).toFixed(2)}
                    </Typography>
                );
            case 'paid':
                // For providers, paid is netProviderAmount (approved - patient share)
                return <Typography variant="body2" color="secondary.main" fontWeight={900}>{(claim.netProviderAmount || 0).toFixed(2)}</Typography>;
            case 'actions':
                return (
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                        <Tooltip title="عرض / تعديل">
                            <IconButton
                                color="primary"
                                onClick={() => navigate(`/claims/batches/entry?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}&claimId=${claim.id}`)}
                            >
                                <ViewIcon fontSize="small" sx={{ fontSize: '1.2rem' }} />
                            </IconButton>
                        </Tooltip>
                        {canSuspend && claim.status === 'APPROVED' && (
                            <Tooltip title="تعليق للمراجعة">
                                <IconButton
                                    color="warning"
                                    onClick={() => handleOpenSuspend(claim.id)}
                                >
                                    <SuspendIcon fontSize="small" sx={{ fontSize: '1.2rem' }} />
                                </IconButton>
                            </Tooltip>
                        )}
                        <Tooltip title="طباعة مطالبة واحدة">
                            <IconButton
                                color="info"
                                onClick={async () => {
                                    try {
                                        const fresh = await claimsService.getById(claim.id);
                                        setSingleClaimForPrint({ ...claim, ...fresh });
                                        // wait for state + DOM update then trigger print
                                        setTimeout(() => handlePrintSingle(), 300);
                                    } catch {
                                        setSingleClaimForPrint(claim);
                                        setTimeout(() => handlePrintSingle(), 300);
                                    }
                                }}
                            >
                                <PrintIcon fontSize="small" sx={{ fontSize: '1.2rem' }} />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                );
            default:
                return null;
        }
    };

    return (
        <>
        <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', px: { xs: 2, sm: 3 } }}>

            {/* INVISIBLE PRINT COMPONENT - Batch (all or selected) */}
            <BatchPrintReport
                ref={batchReportRef}
                claims={selectedClaimIds.length > 0
                    ? (printClaims.length ? printClaims : claims).filter(c => selectedClaimIds.includes(c.id))
                    : (printClaims.length ? printClaims : claims)
                }
                batchCode={batchCode}
                employer={employer}
                provider={provider}
                month={month}
                year={year}
            />

            <RejectedBatchPrintReport
                ref={rejectedReportRef}
                claims={printClaims.length ? printClaims : claims}
                batchCode={batchCode}
                employer={employer}
                provider={provider}
                month={month}
                year={year}
            />

            {/* INVISIBLE PRINT COMPONENT - Single claim */}
            {singleClaimForPrint && (
                <BatchPrintReport
                    ref={singleClaimPrintRef}
                    claims={[singleClaimForPrint]}
                    batchCode={batchCode}
                    employer={employer}
                    provider={provider}
                    month={month}
                    year={year}
                />
            )}

            <ModernPageHeader
                title={<span dir="ltr">{batchCode}</span>}
                subtitle={`دفعة لشهر ${MONTHS_AR[month - 1]} ${year} - ${provider?.name || '...'}`}
                icon={ReceiptIcon}
                breadcrumbs={[
                    { label: 'الرئيسية', path: '/' },
                    { label: 'نظام الدفعات', path: '/claims/batches' },
                    { label: batchCode }
                ]}
                actions={
                    <Stack direction="row" spacing={1.5}>
                        <Button
                            variant="outlined"
                            color="secondary"
                            startIcon={<ArrowBackIcon />}
                            onClick={() => navigate('/claims/batches')}
                            sx={{ borderRadius: 1.5, height: 40 }}
                        >
                            العودة
                        </Button>

                        <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

                        <Button
                            variant="outlined"
                            color="info"
                            startIcon={<ViewIcon />}
                            onClick={() => {
                                if (selectedClaimIds.length === 0) {
                                    enqueueSnackbar('الرجاء تحديد مطالبة واحدة على الأقل للمعاينة', { variant: 'warning' });
                                    return;
                                }
                                navigate(`/reports/claims/statement-preview?ids=${selectedClaimIds.join(',')}`);
                            }}
                            sx={{ borderRadius: 1.5, height: 40 }}
                        >
                            معاينة المحددة
                        </Button>

                        <Button
                            variant="outlined"
                            color="info"
                            startIcon={<PrintIcon />}
                            onClick={async () => {
                                if (selectedClaimIds.length > 0) {
                                    await loadDetailedClaimsForPrint();
                                }
                                handlePrint();
                            }}
                            sx={{ borderRadius: 1.5, height: 40 }}
                        >
                            {selectedClaimIds.length > 0
                                ? `طباعة (${selectedClaimIds.length})`
                                : 'طباعة الكل'}
                        </Button>

                        <Button
                            variant="outlined"
                            color="error"
                            startIcon={<PdfIcon />}
                            onClick={handleRejectedReport}
                            sx={{ borderRadius: 1.5, height: 40, borderColor: 'error.main', color: 'error.main' }}
                        >
                            تقرير المرفوضات
                        </Button>

                        <Button
                            variant="outlined"
                            sx={{
                                color: '#1b5e20',
                                borderColor: '#1b5e20',
                                borderRadius: 1.5,
                                height: 40,
                                '&:hover': { backgroundColor: '#1b5e2010', borderColor: '#1b5e20' }
                            }}
                            startIcon={<ExcelIcon />}
                            onClick={handleExportExcel}
                        >
                            إكسل
                        </Button>


                        <Button
                            variant="contained"
                            color="primary"
                            startIcon={<AddIcon />}
                            onClick={() => navigate(`/claims/batches/entry?employerId=${employerId}&providerId=${providerId}&month=${month}&year=${year}`)}
                            sx={{
                                borderRadius: 1.5,
                                height: 40,
                                px: 3,
                                boxShadow: '0 4px 12px rgba(var(--mui-palette-primary-mainChannel), 0.2)'
                            }}
                        >
                            إضافة مطالبة
                        </Button>
                    </Stack>
                }
            />

            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, mt: -1 }}>
                <Stack spacing={1.5} sx={{ height: '100%' }}>
                    {/* Filter Bar - Matches Beneficiaries standard */}
                    <MainCard sx={{ p: '8px !important', flexShrink: 0 }}>
                        <Stack direction="row" spacing={1.5} alignItems="center">
                            <Tooltip title="تحديث">
                                <IconButton
                                    onClick={() => window.location.reload()}
                                    color="primary"
                                    sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: 40, height: 40 }}
                                >
                                    <RefreshIcon />
                                </IconButton>
                            </Tooltip>

                            <Chip
                                icon={<ReceiptIcon fontSize="small" />}
                                label={`${claims.length} مطالبة`}
                                variant="outlined"
                                color="primary"
                                sx={{ height: 40, borderRadius: 1, fontWeight: 'bold', fontSize: '14px', px: 1.5 }}
                            />

                            <TextField
                                fullWidth
                                size="small"
                                placeholder="بحث بالاسم، رقم البطاقة، أو المرجع..."
                                value={searchTerm}
                                onChange={(e) => {
                                    setSearchTerm(e.target.value);
                                    tableState.setPage(0);
                                }}
                                sx={{ flexGrow: 1 }}
                                InputProps={{
                                    startAdornment: (
                                        <InputAdornment position="start">
                                            <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
                                        </InputAdornment>
                                    ),
                                    sx: { height: 40, borderRadius: 1, bgcolor: 'background.paper' }
                                }}
                            />

                            <TextField
                                select
                                size="small"
                                label="الحالة"
                                value={statusFilter}
                                onChange={(e) => {
                                    setStatusFilter(e.target.value);
                                    tableState.setPage(0);
                                }}
                                sx={{ minWidth: 130, bgcolor: 'background.paper' }}
                                InputProps={{ sx: { height: 40, borderRadius: 1 } }}
                                InputLabelProps={{ shrink: true }}
                            >
                                <MenuItem value=""><em>الكل</em></MenuItem>
                                <MenuItem value="APPROVED">معتمدة</MenuItem>
                                <MenuItem value="PENDING">قيد الانتظار</MenuItem>
                                <MenuItem value="UNDER_REVIEW">تحت المراجعة</MenuItem>
                                <MenuItem value="REJECTED">مرفوضة</MenuItem>
                            </TextField>

                            <Button
                                variant="outlined"
                                color="secondary"
                                startIcon={<FilterAltOffIcon />}
                                onClick={() => {
                                    setSearchTerm('');
                                    setStatusFilter('');
                                    tableState.setPage(0);
                                }}
                                sx={{ minWidth: 120, height: 40, borderRadius: 1 }}
                            >
                                إعادة ضبط
                            </Button>

                            {/* Select-all control */}
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, px: 1, borderRadius: 1, border: '1px solid', borderColor: 'divider', height: 40, bgcolor: 'background.paper' }}>
                                <Checkbox
                                    size="small"
                                    checked={allSelected}
                                    indeterminate={someSelected}
                                    onChange={handleToggleAll}
                                />
                                <Typography variant="caption" fontWeight={700} sx={{ whiteSpace: 'nowrap', color: selectedClaimIds.length > 0 ? 'primary.main' : 'text.secondary' }}>
                                    {selectedClaimIds.length > 0 ? `${selectedClaimIds.length} محددة` : 'تحديد الكل'}
                                </Typography>
                            </Box>
                        </Stack>
                    </MainCard>

                    {/* Table View */}
                    <GenericDataTable
                        columns={columns.map((c) => ({
                            accessorKey: c.id,
                            header: c.label,
                            minWidth: c.minWidth,
                            align: c.align,
                            enableSorting: c.sortable !== false,
                            cell: ({ row }) => {
                                const rowIndex = row.index + tableState.page * tableState.pageSize;
                                return renderCell(row.original, c, rowIndex) ?? '-';
                            }
                        }))}
                        data={tableRows}
                        totalCount={claims.length}
                        isLoading={isLoading}
                        tableState={tableState}
                        enableFiltering={false}
                        enableSorting={true}
                        enablePagination={true}
                        compact={true}
                        tableSize="small"
                        stickyHeader={false}
                        disableInternalScroll={true}
                        minHeight="auto"
                        maxHeight="none"
                        emptyMessage="لا توجد مطالبات في هذا الباتش حالياً."
                        rowsPerPageOptions={[10, 25, 50, 100]}
                    />

                    {/* Totals Footer */}
                    {claims.length > 0 && (
                        <MainCard sx={{ p: '10px 16px !important', flexShrink: 0, bgcolor: 'grey.50', borderTop: '2px solid', borderColor: 'divider' }}>
                            <Stack direction="row" spacing={2} justifyContent="flex-end" alignItems="center" flexWrap="wrap">
                                <Typography variant="caption" color="text.secondary" fontWeight={700} sx={{ mr: 'auto' }}>
                                    الإجماليات ({claims.length} مطالبة)
                                </Typography>
                                <Chip label={`الإجمالي: ${totals.amount.toFixed(2)}`} size="small" sx={{ fontWeight: 700 }} />
                                <Chip label={`المعتمد: ${totals.covered.toFixed(2)}`} color="success" size="small" sx={{ fontWeight: 700 }} />
                                <Chip label={`المرفوض: ${totals.refused.toFixed(2)}`} color="error" size="small" sx={{ fontWeight: 700 }} />
                                <Chip label={`نصيب المؤمن: ${totals.copay.toFixed(2)}`} color="info" size="small" sx={{ fontWeight: 700 }} />
                                <Chip label={`المستحق: ${totals.paid.toFixed(2)}`} color="secondary" size="small" sx={{ fontWeight: 900, fontSize: '0.8rem' }} />
                            </Stack>
                        </MainCard>
                    )}
                </Stack>
            </Box>
        </Box>

        {/* Suspend Dialog */}
        <Dialog open={suspendDialogOpen} onClose={() => setSuspendDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 700, borderBottom: '1px solid', borderColor: 'divider' }}>
                تعليق المطالبة للمراجعة
            </DialogTitle>
            <DialogContent sx={{ pt: 2 }}>
                <Typography variant="body2" color="text.secondary" mb={2}>
                    سيتم تغيير حالة المطالبة إلى «يحتاج تصحيح». يجب إدخال سبب التعليق.
                </Typography>
                <TextField
                    fullWidth
                    multiline
                    rows={3}
                    label="سبب التعليق"
                    value={suspendComment}
                    onChange={(e) => setSuspendComment(e.target.value)}
                    placeholder="اكتب سبب التعليق أو الخلل الذي وجدته..."
                    autoFocus
                />
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2, gap: 1 }}>
                <Button variant="outlined" onClick={() => setSuspendDialogOpen(false)}>إلغاء</Button>
                <Button
                    variant="contained"
                    color="warning"
                    onClick={handleConfirmSuspend}
                    disabled={suspendMutation.isPending}
                >
                    تعليق المطالبة
                </Button>
            </DialogActions>
        </Dialog>
        </>
    );
}
