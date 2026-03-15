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
    const [selectedClaimIds, setSelectedClaimIds] = useState([]);
    const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
    const [suspendComment, setSuspendComment] = useState('');
    const [suspendingClaimId, setSuspendingClaimId] = useState(null);
    const tableState = useTableState({
        initialPageSize: 10,
        defaultSort: { field: 'serviceDate', direction: 'desc' }
    });
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
            { header: '#', key: 'index', width: '0.625rem' },
            { header: 'المرجع', key: 'ref', width: '1.5625rem' },
            { header: 'مقدم الخدمة', key: 'provider', width: '3.125rem' },
            { header: 'المستفيد', key: 'patient', width: '1.875rem' },
            { header: 'تاريخ الخدمة', key: 'serviceDate', width: '1.125rem' },
            { header: 'الحالة', key: 'status', width: '0.9375rem' },
            { header: 'المبلغ الإجمالي', key: 'amount', width: '1.25rem' },
            { header: 'المعتمد', key: 'covered', width: '1.25rem' },
            { header: 'المرفوض', key: 'refused', width: '1.25rem' },
            { header: 'نصيب المؤمن عليه', key: 'copay', width: '1.375rem' },
            { header: 'المستحق للمزود', key: 'paid', width: '1.25rem' }
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

    // فتح التقرير الموحد (كل المطالبات أو المحددة)
    const handlePrint = () => {
        const ids = selectedClaimIds.length > 0
            ? selectedClaimIds
            : claims.map(c => c.id);
        if (ids.length === 0) {
            enqueueSnackbar('لا توجد مطالبات للطباعة', { variant: 'warning' });
            return;
        }
        navigate(`/reports/claims/statement-preview?ids=${ids.join(',')}`);
    };

    const handlePrintSingle = (claimId) => {
        navigate(`/reports/claims/statement-preview?ids=${claimId}`);
    };

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

    // فتح تقرير المرفوضات - يجلب التفاصيل ويفلتر المطالبات التي فيها بند واحد مرفوض على الأقل
    const handleRejectedReport = async () => {
        if (!claims || claims.length === 0) {
            enqueueSnackbar('لا توجد مطالبات', { variant: 'warning' });
            return;
        }
        enqueueSnackbar('جاري تحميل البيانات...', { variant: 'info' });
        const detailed = await Promise.all(
            claims.map(async (c) => {
                try { return { ...c, ...await claimsService.getById(c.id) }; }
                catch { return c; }
            })
        );
        const rejectedIds = detailed
            .filter(c => {
                if (c.lines && c.lines.length > 0) {
                    return c.lines.some(l =>
                        l.rejected === true ||
                        (l.refusedAmount != null && parseFloat(l.refusedAmount) > 0)
                    );
                }
                return (
                    (c.rejectedAmount != null && parseFloat(c.rejectedAmount) > 0) ||
                    (c.totalRejected  != null && parseFloat(c.totalRejected)  > 0)
                );
            })
            .map(c => c.id);
        if (rejectedIds.length === 0) {
            enqueueSnackbar('لا توجد مطالبات مرفوضة في هذه الدفعة', { variant: 'warning' });
            return;
        }
        navigate(`/reports/claims/statement-preview?ids=${rejectedIds.join(',')}`);
    };

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
        { id: 'select',      label: '',                minWidth: '2.5rem',  align: 'center', sortable: false },
        { id: 'index',       label: '#',               minWidth: '2.5rem',  align: 'center', sortable: false },
        { id: 'ref',         label: 'المرجع',          minWidth: '8rem',  align: 'center',       sortable: false },
        { id: 'provider',    label: 'مقدم الخدمة',    minWidth: '7rem',   align: 'center' ,    sortable: false },
        { id: 'patient',     label: 'الاسم (المستفيد)', minWidth: '10rem',   align: 'cenetr'  ,               sortable: true  },
        { id: 'serviceDate', label: 'تاريخ الخدمة',  minWidth: '7rem',    align: 'center', sortable: true  },
        { id: 'status',      label: 'الحالة',          minWidth: '6rem',    align: 'center', sortable: true  },
        { id: 'amount',      label: 'الإجمالي',        minWidth: '5rem',  align: 'center',  sortable: true  },
        { id: 'covered',     label: 'المعتمد',         minWidth: '5rem',  align: 'center',  sortable: true  },
        { id: 'refused',     label: 'المرفوض',         minWidth: '5.5rem',  align: 'center',  sortable: true  },
        { id: 'copay',       label: 'نصيب المستفيد',    minWidth: '5rem',    align: 'center',  sortable: true  },
        { id: 'actions',     label: 'إجراءات',         minWidth: '5rem',  align: 'center', sortable: false }
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
                    fontWeight: 400,
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
                    <Typography variant="body2" fontWeight={400} color="primary.main" dir="ltr">
                        {batchCode}/{String(index + 1).padStart(4, '0')}
                    </Typography>
                );
            case 'provider':
                return (
                    <Stack direction="row" spacing={1} alignItems="center">
                        <BusinessIcon sx={{ fontSize: '1.0rem', color: 'text.disabled' }} />
                        <Typography variant="body2">{provider?.name}</Typography>
                    </Stack>
                );
            case 'patient':
                return (
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ overflow: 'hidden', minWidth: 0 }}>
                        <Avatar sx={{ width: '1.5rem', height: '1.5rem', fontSize: '0.7rem', bgcolor: 'secondary.light', flexShrink: 0 }}>
                            {claim.memberName?.charAt(0)}
                        </Avatar>
                        <Box sx={{ overflow: 'hidden', minWidth: 0 }}>
                            <Typography variant="body2" fontWeight={600} noWrap>{claim.memberName}</Typography>
                            <Typography variant="caption" color="text.secondary" noWrap>{claim.memberCardNumber}</Typography>
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
                return <Typography variant="body2" fontWeight={400}>{claim.requestedAmount?.toFixed(2)}</Typography>;
            case 'covered':
                return <Typography variant="body2" color="success.main" fontWeight={400}>{(claim.approvedAmount || 0).toFixed(2)}</Typography>;
            case 'refused':
                const displayRefused = (claim.status === 'REJECTED' && (!claim.refusedAmount || claim.refusedAmount === 0))
                    ? claim.requestedAmount
                    : (claim.refusedAmount || 0);
                return (
                    <Tooltip title={claim.rejectionReason || ''} arrow placement="top">
                        <Typography variant="body2" color="error.main" fontWeight={400}>
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
                return <Typography variant="body2" color="secondary.main" fontWeight={600}>{(claim.netProviderAmount || 0).toFixed(2)}</Typography>;
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
                                onClick={() => handlePrintSingle(claim.id)}
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
        <Box sx={{ display: 'flex', flexDirection: 'column', px: { xs: 2, sm: 3 }, pb: 2 }}>

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
                            sx={{ borderRadius: '0.375rem', height: '2.5rem' }}
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
                            sx={{ borderRadius: '0.375rem', height: '2.5rem' }}
                        >
                            معاينة المحددة
                        </Button>

                        <Button
                            variant="outlined"
                            color="info"
                            startIcon={<PrintIcon />}
                            onClick={handlePrint}
                            sx={{ borderRadius: '0.375rem', height: '2.5rem' }}
                        >
                            {selectedClaimIds.length > 0
                                ? `طباعة (${selectedClaimIds.length})`
                                : 'طباعة الكل'}
                        </Button>

                        <Button
                            variant="outlined"
                            color="error"
                            startIcon={<PrintIcon />}
                            onClick={handleRejectedReport}
                            sx={{ borderRadius: '0.375rem', height: '2.5rem', borderColor: 'error.main', color: 'error.main' }}
                        >
                            تقرير المرفوضات
                        </Button>

                        <Button
                            variant="outlined"
                            sx={{
                                color: '#1b5e20',
                                borderColor: '#1b5e20',
                                borderRadius: '0.375rem',
                                height: '2.5rem',
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
                                borderRadius: '0.375rem',
                                height: '2.5rem',
                                px: '1.5rem',
                                boxShadow: '0 4px 12px rgba(var(--mui-palette-primary-mainChannel), 0.2)'
                            }}
                        >
                            إضافة مطالبة
                        </Button>
                    </Stack>
                }
            />

            <Box sx={{ mt: -1 }}>
                <Stack spacing={1.5}>
                    {/* Filter Bar - Matches Beneficiaries standard */}
                    <MainCard sx={{ p: '8px !important', flexShrink: 0 }}>
                        <Stack direction="row" spacing={1.5} alignItems="center">
                            <Tooltip title="تحديث">
                                <IconButton
                                    onClick={() => window.location.reload()}
                                    color="primary"
                                    sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
                                >
                                    <RefreshIcon />
                                </IconButton>
                            </Tooltip>

                            <Chip
                                icon={<ReceiptIcon fontSize="small" />}
                                label={`${claims.length} مطالبة`}
                                variant="outlined"
                                color="primary"
                                sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: '0.75rem' }}
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
                                    sx: { height: '2.5rem', borderRadius: 1, bgcolor: 'background.paper' }
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
                                sx={{ minWidth: '8.125rem', bgcolor: 'background.paper' }}
                                InputProps={{ sx: { height: '2.5rem', borderRadius: 1 } }}
                                InputLabelProps={{ shrink: true }}
                            >
                                <MenuItem value=""><em>الكل</em></MenuItem>
                                <MenuItem value="APPROVED">معتمدة</MenuItem>
                                <MenuItem value="NEEDS_CORRECTION">معلقة للمراجعة</MenuItem>
                                <MenuItem value="PENDING">قيد الانتظار</MenuItem>
                                <MenuItem value="UNDER_REVIEW">تحت المراجعة</MenuItem>
                                <MenuItem value="DRAFT">مسودة</MenuItem>
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
                                sx={{ minWidth: '7.5rem', height: '2.5rem', borderRadius: 1 }}
                            >
                                إعادة ضبط
                            </Button>

                            {selectedClaimIds.length > 0 && (
                                <Chip
                                    label={`${selectedClaimIds.length} محددة`}
                                    size="small"
                                    color="primary"
                                    variant="outlined"
                                    onDelete={() => setSelectedClaimIds([])}
                                    sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 600, fontSize: '0.8rem' }}
                                />
                            )}
                        </Stack>
                    </MainCard>

                    {/* Table View */}
                    <GenericDataTable
                        columns={columns.map((c) => ({
                            accessorKey: c.id,
                            header: c.id === 'select'
                                ? () => (
                                    <Checkbox
                                        size="small"
                                        checked={allSelected}
                                        indeterminate={someSelected}
                                        onChange={handleToggleAll}
                                        onClick={(e) => e.stopPropagation()}
                                    />
                                  )
                                : c.label,
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
                            <Stack direction="row" spacing={2} justifyContent="flex-start" alignItems="center" flexWrap="wrap">
                                <Typography variant="caption" color="text.secondary" fontWeight={400} sx={{ mr: 'auto' }}>
                                    الإجماليات ({claims.length} مطالبة)
                                </Typography>
                                <Chip label={`الإجمالي: ${totals.amount.toFixed(2)}`} size="small" sx={{ fontWeight: 400 }} />
                                <Chip label={`المعتمد: ${totals.covered.toFixed(2)}`} color="success" size="small" sx={{ fontWeight: 400 }} />
                                <Chip label={`المرفوض: ${totals.refused.toFixed(2)}`} color="error" size="small" sx={{ fontWeight: 400 }} />
                                <Chip label={`نصيب المستفيد: ${totals.copay.toFixed(2)}`} color="info" size="small" sx={{ fontWeight: 400 }} />
                            </Stack>
                        </MainCard>
                    )}
                </Stack>
            </Box>
        </Box>

        {/* Suspend Dialog */}
        <Dialog open={suspendDialogOpen} onClose={() => setSuspendDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 400, borderBottom: '1px solid', borderColor: 'divider' }}>
                تعليق المطالبة للمراجعة
            </DialogTitle>
            <DialogContent sx={{ pt: '1.0rem' }}>
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
            <DialogActions sx={{ px: '1.5rem', pb: '1.0rem', gap: 1 }}>
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





