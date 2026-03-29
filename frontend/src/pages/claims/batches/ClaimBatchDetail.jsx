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
    FilterAltOff as FilterAltOffIcon,
    PauseCircle as SuspendIcon,
    DeleteOutline as DeleteOutlineIcon,
    RestoreFromTrash as RestoreIcon,
    DeleteForever as DeleteForeverIcon,
    History as HistoryIcon
} from '@mui/icons-material';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import ExcelJS from 'exceljs';

// project components
import MainCard from 'components/MainCard';
import { ModernPageHeader, SoftDeleteToggle } from 'components/tba';
import { UnifiedMedicalTable } from 'components/common';
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

    // Soft Delete / Restore / Hard Delete
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [deletingClaim, setDeletingClaim] = useState(null);
    const [showDeleted, setShowDeleted] = useState(false);
    const [hardDeleteDialogOpen, setHardDeleteDialogOpen] = useState(false);
    const [hardDeletingClaim, setHardDeletingClaim] = useState(null);
    const [restoreDialogOpen, setRestoreDialogOpen] = useState(false);
    const [restoringClaim, setRestoringClaim] = useState(null);
    const tableState = useTableState({
        initialPageSize: 10,
        defaultSort: { field: 'serviceDate', direction: 'desc' }
    });
    const { enqueueSnackbar } = useSnackbar();
    const queryClient = useQueryClient();

    // Detect superadmin / reviewer role from session storage
    const currentUserRole = (() => {
        try {
            const rolesStr = localStorage.getItem('userRoles');
            if (rolesStr) {
                const roles = JSON.parse(rolesStr);
                return Array.isArray(roles) ? roles[0] : '';
            }
        } catch { /* ignore */ }
        return '';
    })();
    const canSuspend = currentUserRole === 'SUPER_ADMIN' || currentUserRole === 'MEDICAL_REVIEWER' || currentUserRole === 'ACCOUNTANT';
    const canDelete = currentUserRole === 'SUPER_ADMIN' || currentUserRole === 'DATA_ENTRY' || currentUserRole === 'MEDICAL_REVIEWER' || currentUserRole === 'PROVIDER_STAFF';
    const canHardDelete = currentUserRole === 'SUPER_ADMIN';

    const softDeleteMutation = useMutation({
        mutationFn: (claimId) => claimsService.softDelete(claimId),
        onSuccess: () => {
            enqueueSnackbar('تم حذف المطالبة — تمت استعادة السقف تلقائياً', { variant: 'success' });
            setDeleteDialogOpen(false);
            setDeletingClaim(null);
            queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
            queryClient.invalidateQueries({ queryKey: ['batch-stats'] });
        },
        onError: (err) => {
            enqueueSnackbar(err?.response?.data?.messageAr || err?.response?.data?.message || 'حدث خطأ أثناء الحذف', { variant: 'error' });
        }
    });

    const restoreMutation = useMutation({
        mutationFn: (claimId) => claimsService.restore(claimId),
        onSuccess: () => {
            enqueueSnackbar('تمت استعادة المطالبة بنجاح', { variant: 'success' });
            queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
            queryClient.invalidateQueries({ queryKey: ['deleted-claims'] });
            queryClient.invalidateQueries({ queryKey: ['batch-stats'] });
        },
        onError: (err) => {
            enqueueSnackbar(err?.response?.data?.messageAr || err?.response?.data?.message || 'حدث خطأ أثناء الاستعادة', { variant: 'error' });
        }
    });

    const hardDeleteMutation = useMutation({
        mutationFn: (claimId) => claimsService.hardDelete(claimId),
        onSuccess: () => {
            enqueueSnackbar('تم الحذف النهائي للمطالبة', { variant: 'warning' });
            setHardDeleteDialogOpen(false);
            setHardDeletingClaim(null);
            queryClient.invalidateQueries({ queryKey: ['deleted-claims'] });
            queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
            queryClient.invalidateQueries({ queryKey: ['batch-stats'] });
        },
        onError: (err) => {
            enqueueSnackbar(err?.response?.data?.messageAr || err?.response?.data?.message || 'حدث خطأ أثناء الحذف النهائي', { variant: 'error' });
        }
    });

    const suspendMutation = useMutation({
        mutationFn: ({ claimId, comment }) =>
            claimsService.updateReview(claimId, { status: 'NEEDS_CORRECTION', reviewerComment: comment }),
        onSuccess: () => {
            enqueueSnackbar('تم تعليق المطالبة بنجاح', { variant: 'success' });
            setSuspendDialogOpen(false);
            setSuspendComment('');
            setSuspendingClaimId(null);
            queryClient.invalidateQueries({ queryKey: ['batch-claims-detail'] });
            queryClient.invalidateQueries({ queryKey: ['batch-stats'] });
            queryClient.invalidateQueries({ queryKey: ['claim'] });
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

    // Fetch deleted claims for this batch
    const { data: deletedClaimsResponse, isLoading: deletedLoading } = useQuery({
        queryKey: ['deleted-claims', employerId, providerId, year, month],
        queryFn: async () => {
            const dateFrom = `${year}-${String(month).padStart(2, '0')}-01`;
            const dateTo = `${year}-${String(month).padStart(2, '0')}-31`;
            return await claimsService.listDeleted({ employerId, providerId, dateFrom, dateTo, size: 100 });
        },
        enabled: showDeleted && !!providerId && !!employerId
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
        },
        refetchOnWindowFocus: true,
        refetchOnMount: 'always',
        staleTime: 0
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
            { header: '#', key: 'index', width: 6 },
            { header: 'المرجع', key: 'ref', width: 22 },
            { header: 'مقدم الخدمة', key: 'provider', width: 25 },
            { header: 'المستفيد', key: 'patient', width: 28 },
            { header: 'تاريخ الخدمة', key: 'serviceDate', width: 16 },
            { header: 'الحالة', key: 'status', width: 14 },
            { header: 'المبلغ الإجمالي', key: 'amount', width: 16 },
            { header: 'المعتمد', key: 'covered', width: 14 },
            { header: 'المرفوض', key: 'refused', width: 14 },
            { header: 'نصيب المؤمن عليه', key: 'copay', width: 18 },
            { header: 'المستحق للمزود', key: 'paid', width: 16 }
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
        { id: 'select',      label: <Checkbox size="small" checked={allSelected} indeterminate={someSelected} onChange={handleToggleAll} onClick={(e) => e.stopPropagation()} />, minWidth: '2.5rem',  align: 'center', sortable: false },
        { id: 'index',       label: '#',               minWidth: '2.5rem',  align: 'center', sortable: false },
        { id: 'ref',         label: 'المرجع',          minWidth: '8rem',    align: 'center', sortable: false },
        { id: 'employer',    label: 'الوثيقة',          minWidth: '9rem',    align: 'center', sortable: false },
        { id: 'provider',    label: 'مقدم الخدمة',    minWidth: '7rem',    align: 'center', sortable: false },
        { id: 'patient',     label: 'الاسم (المستفيد)', minWidth: '10rem',  align: 'right',  sortable: true  },
        { id: 'serviceDate', label: 'تاريخ الخدمة',   minWidth: '7rem',    align: 'center', sortable: true  },
        { id: 'status',      label: 'الحالة',           minWidth: '6rem',    align: 'center', sortable: true  },
        { id: 'amount',      label: 'الإجمالي',         minWidth: '5rem',    align: 'center', sortable: true  },
        { id: 'covered',     label: 'المعتمد',          minWidth: '5rem',    align: 'center', sortable: true  },
        { id: 'refused',     label: 'المرفوض',          minWidth: '5.5rem',  align: 'center', sortable: true  },
        { id: 'copay',       label: 'نصيب المستفيد',    minWidth: '5rem',    align: 'center', sortable: true  },
        { id: 'actions',     label: 'إجراءات',          minWidth: '5rem',    align: 'center', sortable: false }
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

    const getStatusChip = (status, refusedAmount = 0) => {
        const config = {
            'APPROVED': refusedAmount > 0
                ? { label: 'مرفوضة', color: 'error', bgcolor: '#fff1f0', border: '#ffa39e' }
                : { label: 'معتمدة', color: 'success', bgcolor: '#f6ffed', border: '#b7eb8f' },
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

    const renderCell = (claim, column, rowIndex) => {
        const index = tableState.page * tableState.pageSize + rowIndex;
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
            case 'employer':
                return (
                    <Typography variant="body2" noWrap>
                        {claim.employerName || employer?.name || '-'}
                    </Typography>
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
                return getStatusChip(claim.status || 'APPROVED', claim.refusedAmount || 0);
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
                        {canDelete && !showDeleted && claim.status !== 'BATCHED' && claim.status !== 'SETTLED' && (
                            <Tooltip title="حذف المطالبة">
                                <IconButton
                                    color="error"
                                    onClick={() => { setDeletingClaim(claim); setDeleteDialogOpen(true); }}
                                >
                                    <DeleteOutlineIcon fontSize="small" sx={{ fontSize: '1.2rem' }} />
                                </IconButton>
                            </Tooltip>
                        )}
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
                title={provider?.name || '...'}
                subtitle={`دفعة لشهر ${MONTHS_AR[month - 1]} ${year} - ${batchCode}`}
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
                            color="primary"
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
                            طباعة المحددة
                        </Button>

                        <Button
                            variant="outlined"
                            color="primary"
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
                            color="primary"
                            sx={{ borderRadius: '0.375rem', height: '2.5rem' }}
                            startIcon={<ExcelIcon />}
                            onClick={handleExportExcel}
                        >
                            تصدير إكسل
                        </Button>


                        {canDelete && (
                            <SoftDeleteToggle
                                showDeleted={showDeleted}
                                onToggle={() => setShowDeleted(!showDeleted)}
                            />
                        )}

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
                    {showDeleted ? (
                        /* ── DELETED RECORDS VIEW ── */
                        <MainCard sx={{ p: 0 }}>
                            <Box sx={{ p: '12px 16px', borderBottom: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
                                <HistoryIcon color="error" fontSize="small" />
                                <Typography variant="subtitle2" color="error.main" fontWeight={600}>
                                    سجل المطالبات المحذوفة
                                </Typography>
                                {deletedLoading && (
                                    <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>جاري التحميل...</Typography>
                                )}
                            </Box>
                            {(() => {
                                const deletedItems = deletedClaimsResponse?.items || deletedClaimsResponse?.content || [];
                                if (!deletedLoading && deletedItems.length === 0) {
                                    return (
                                        <Typography color="text.secondary" textAlign="center" py={5}>
                                            لا توجد مطالبات محذوفة في هذه الدفعة
                                        </Typography>
                                    );
                                }
                                return (
                                    <Box sx={{ overflowX: 'auto' }}>
                                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem', direction: 'rtl' }}>
                                            <thead>
                                                <tr style={{ background: '#fdecea', borderBottom: '2px solid #e0e0e0' }}>
                                                    <th style={{ padding: '10px 14px', textAlign: 'right', fontWeight: 600 }}>#</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'right', fontWeight: 600 }}>المستفيد</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'center', fontWeight: 600 }}>تاريخ الخدمة</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'center', fontWeight: 600 }}>الإجمالي</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'center', fontWeight: 600 }}>الحالة</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'center', fontWeight: 600 }}>حُذف بواسطة</th>
                                                    <th style={{ padding: '10px 14px', textAlign: 'center', fontWeight: 600 }}>إجراءات</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {deletedItems.map((c, i) => (
                                                    <tr key={c.id} style={{ borderBottom: '1px solid #e0e0e0', background: i % 2 === 0 ? '#fff' : '#fafafa' }}>
                                                        <td style={{ padding: '8px 14px', color: '#888' }}>{i + 1}</td>
                                                        <td style={{ padding: '8px 14px', fontWeight: 600 }}>{c.memberName}</td>
                                                        <td style={{ padding: '8px 14px', textAlign: 'center', direction: 'ltr' }}>{c.serviceDate}</td>
                                                        <td style={{ padding: '8px 14px', textAlign: 'center' }}>{(c.requestedAmount || 0).toFixed(2)}</td>
                                                        <td style={{ padding: '8px 14px', textAlign: 'center', color: '#888' }}>{c.status}</td>
                                                        <td style={{ padding: '8px 14px', textAlign: 'center', color: '#888', fontSize: '0.75rem' }}>{c.deletedBy || '—'}</td>
                                                        <td style={{ padding: '8px 14px', textAlign: 'center' }}>
                                                            <Stack direction="row" spacing={0.5} justifyContent="center">
                                                                <Tooltip title="استعادة المطالبة">
                                                                    <IconButton color="success" size="small"
                                                                        onClick={() => { setRestoringClaim(c); setRestoreDialogOpen(true); }}
                                                                        disabled={restoreMutation.isPending}
                                                                    >
                                                                        <RestoreIcon fontSize="small" />
                                                                    </IconButton>
                                                                </Tooltip>
                                                                {canHardDelete && (
                                                                    <Tooltip title="حذف نهائي">
                                                                        <IconButton color="error" size="small"
                                                                            onClick={() => { setHardDeletingClaim(c); setHardDeleteDialogOpen(true); }}
                                                                        >
                                                                            <DeleteForeverIcon fontSize="small" />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                            </Stack>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </Box>
                                );
                            })()}
                        </MainCard>
                    ) : (
                        <UnifiedMedicalTable
                            columns={columns}
                            rows={tableRows}
                            loading={isLoading}
                            totalCount={sortedClaims.length}
                            page={tableState.page}
                            rowsPerPage={tableState.pageSize}
                            onPageChange={(newPage) => tableState.setPage(newPage)}
                            onRowsPerPageChange={(newSize) => { tableState.setPageSize(newSize); tableState.setPage(0); }}
                            sortBy={tableState.sorting?.[0]?.id}
                            sortDirection={tableState.sorting?.[0]?.desc ? 'desc' : 'asc'}
                            onSort={(col, dir) => { tableState.setSorting([{ id: col, desc: dir === 'desc' }]); tableState.setPage(0); }}
                            renderCell={renderCell}
                            getRowKey={(claim) => claim.id}
                            emptyMessage="لا توجد مطالبات في هذا الباتش حالياً."
                            rowsPerPageOptions={[10, 25, 50, 100]}
                            size="small"
                            stickyHeader={false}
                        />
                    )}

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

        {/* Soft Delete Confirmation Dialog */}
        <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 600, borderBottom: '1px solid', borderColor: 'divider', color: 'error.main' }}>
                تأكيد حذف المطالبة
            </DialogTitle>
            <DialogContent sx={{ pt: '1.25rem' }}>
                <Typography variant="body2" mb={1}>
                    هل أنت متأكد من حذف المطالبة الخاصة بـ <strong>{deletingClaim?.memberName}</strong>؟
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    • سيتم إخفاء المطالبة من القوائم<br />
                    • ستعود الأموال المحجوزة إلى السقف السنوي تلقائياً<br />
                    • يمكن استعادتها لاحقاً من زر «سجل المحذوفات»
                </Typography>
            </DialogContent>
            <DialogActions sx={{ px: '1.5rem', pb: '1.0rem', gap: 1 }}>
                <Button variant="outlined" onClick={() => setDeleteDialogOpen(false)}>إلغاء</Button>
                <Button
                    variant="contained"
                    color="error"
                    startIcon={<DeleteOutlineIcon />}
                    onClick={() => softDeleteMutation.mutate(deletingClaim?.id)}
                    disabled={softDeleteMutation.isPending}
                >
                    حذف المطالبة
                </Button>
            </DialogActions>
        </Dialog>

        {/* Restore Confirmation Dialog */}
        <Dialog open={restoreDialogOpen} onClose={() => setRestoreDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 600, borderBottom: '1px solid', borderColor: 'divider', color: 'success.dark' }}>
                تأكيد استعادة المطالبة
            </DialogTitle>
            <DialogContent sx={{ pt: '1.25rem' }}>
                <Typography variant="body2" color="text.secondary">
                    هل أنت متأكد من استعادة مطالبة <strong>{restoringClaim?.memberName}</strong> بمبلغ <strong>{(restoringClaim?.requestedAmount || 0).toFixed(2)}</strong>؟
                </Typography>
                <Typography variant="body2" color="text.secondary" mt={1}>
                    سيتم إعادة المطالبة إلى قائمة المطالبات النشطة.
                </Typography>
            </DialogContent>
            <DialogActions sx={{ px: '1.5rem', pb: '1.0rem', gap: 1 }}>
                <Button variant="outlined" onClick={() => setRestoreDialogOpen(false)}>إلغاء</Button>
                <Button
                    variant="contained"
                    color="success"
                    startIcon={<RestoreIcon />}
                    onClick={() => { restoreMutation.mutate(restoringClaim?.id); setRestoreDialogOpen(false); setRestoringClaim(null); }}
                    disabled={restoreMutation.isPending}
                >
                    استعادة
                </Button>
            </DialogActions>
        </Dialog>

        {/* Hard Delete Confirmation Dialog */}
        <Dialog open={hardDeleteDialogOpen} onClose={() => setHardDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 600, borderBottom: '1px solid', borderColor: 'divider', color: 'error.dark' }}>
                ⚠️ حذف نهائي — لا يمكن التراجع
            </DialogTitle>
            <DialogContent sx={{ pt: '1.25rem' }}>
                <Typography variant="body2" color="error.main" fontWeight={600} mb={1}>
                    هذا الإجراء غير قابل للتراجع نهائياً!
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    سيتم حذف مطالبة <strong>{hardDeletingClaim?.memberName}</strong> من قاعدة البيانات بشكل دائم مع جميع بياناتها.
                </Typography>
            </DialogContent>
            <DialogActions sx={{ px: '1.5rem', pb: '1.0rem', gap: 1 }}>
                <Button variant="outlined" onClick={() => setHardDeleteDialogOpen(false)}>إلغاء</Button>
                <Button
                    variant="contained"
                    color="error"
                    startIcon={<DeleteForeverIcon />}
                    onClick={() => hardDeleteMutation.mutate(hardDeletingClaim?.id)}
                    disabled={hardDeleteMutation.isPending}
                >
                    حذف نهائي
                </Button>
            </DialogActions>
        </Dialog>


        </>
    );
}





