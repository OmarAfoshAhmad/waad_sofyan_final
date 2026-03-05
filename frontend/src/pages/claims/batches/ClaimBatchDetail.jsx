/**
 * Claim Batch Detail View
 * Shows a full list of claims (transactions) within a specific batch.
 * Matches Odoo layout but with system visual identity.
 */

import { useState, useMemo } from 'react';
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
    FormControl
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
    FilterAltOff as FilterAltOffIcon
} from '@mui/icons-material';

import { useQuery } from '@tanstack/react-query';
import ExcelJS from 'exceljs';

// project components
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { UnifiedMedicalTable } from 'components/common';

// services
import claimsService from 'services/api/claims.service';
import employersService from 'services/api/employers.service';
import providersService from 'services/api/providers.service';

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
const generateBatchCode = (employer, providerId, month, year) => {
    // 1. Get Employer Symbol (Code)
    const symbol = employer?.code || 'EMP';

    // 2. Year suffix
    const yy = String(year).substring(2);

    // 3. Serial - derived from IDs for demo stability
    const serialNum = (parseInt(providerId) * 11 + (month * 3)) % 100000;
    const serial = String(serialNum).padStart(5, '0');

    return `${symbol}${yy}-${serial}`;
};

export default function ClaimBatchDetail() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const employerId = searchParams.get('employerId');
    const providerId = searchParams.get('providerId');
    const month = parseInt(searchParams.get('month'));
    const year = parseInt(searchParams.get('year'));

    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10);

    // 1. Fetch Context
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
        let items = claimsResponse?.content || [];

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

    // Paginated Data for the table
    const paginatedClaims = useMemo(() => {
        const start = page * rowsPerPage;
        return claims.slice(start, start + rowsPerPage);
    }, [claims, page, rowsPerPage]);

    // Batch Code (Generated same as card)
    const batchCode = useMemo(() =>
        generateBatchCode(employer, providerId, month, year),
        [employer, providerId, month, year]
    );

    // -------------------------------------------------------------------------
    // EXPORT HANDLERS
    // -------------------------------------------------------------------------

    const handleExportExcel = async () => {
        const workbook = new ExcelJS.Workbook();
        const worksheet = workbook.addWorksheet('المطالبات');

        worksheet.columns = [
            { header: '#', key: 'index', width: 10 },
            { header: 'المرجع', key: 'ref', width: 25 },
            { header: 'مقدم الخدمة', key: 'provider', width: 30 },
            { header: 'المريض', key: 'patient', width: 30 },
            { header: 'الحالة', key: 'status', width: 15 },
            { header: 'المبلغ الإجمالي', key: 'amount', width: 20 },
            { header: 'التغطية المعتمدة', key: 'covered', width: 20 },
            { header: 'المبلغ المستحق', key: 'paid', width: 20 }
        ];

        worksheet.views = [{ rightToLeft: true }];

        claims.forEach((c, idx) => {
            worksheet.addRow({
                index: idx + 1,
                ref: `${batchCode}/${String(idx + 1).padStart(4, '0')}`,
                provider: provider?.name || '-',
                patient: c.memberName || '-',
                status: c.status || 'APPROVED',
                amount: c.requestedAmount || 0,
                covered: c.approvedAmount || 0,
                paid: (c.requestedAmount || 0) - (c.approvedAmount || 0)
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

    const handleExportPDF = () => {
        // Since Arabic font support in jsPDF is complex, 
        // we'll trigger the browser print which the user prefers for PDF tables
        window.print();
    };

    // Table Columns
    const columns = [
        { id: 'index', label: '#', minWidth: 50, align: 'center' },
        { id: 'ref', label: 'المرجع', minWidth: 150 },
        { id: 'provider', label: 'مقدم الخدمة', minWidth: 180 },
        { id: 'patient', label: 'الاسم (المريض)', minWidth: 200 },
        { id: 'status', label: 'الحالة', minWidth: 120, align: 'center' },
        { id: 'amount', label: 'المبلغ الإجمالي', minWidth: 130, align: 'right' },
        { id: 'covered', label: 'التغطية المعتمدة', minWidth: 130, align: 'right' },
        { id: 'paid', label: 'المبلغ المستحق', minWidth: 130, align: 'right' },
        { id: 'actions', label: 'إجراءات', minWidth: 100, align: 'center' }
    ];

    const getStatusChip = (status) => {
        const config = {
            'APPROVED': { label: 'معتمدة', color: 'success', bgcolor: '#f6ffed', border: '#b7eb8f' },
            'PENDING': { label: 'قيد الانتظار', color: 'warning', bgcolor: '#fffbe6', border: '#ffe58f' },
            'REJECTED': { label: 'مرفوضة', color: 'error', bgcolor: '#fff1f0', border: '#ffa39e' },
            'UNDER_REVIEW': { label: 'تحت المراجعة', color: 'info', bgcolor: '#e6f7ff', border: '#91d5ff' }
        };

        const s = config[status] || { label: status, color: 'default' };

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
            case 'status':
                return getStatusChip(claim.status || 'APPROVED');
            case 'amount':
                return <Typography variant="body2" fontWeight={700}>{claim.requestedAmount?.toFixed(2)}</Typography>;
            case 'covered':
                return <Typography variant="body2" color="success.main" fontWeight={700}>{claim.approvedAmount?.toFixed(2) || (claim.requestedAmount * 0.8).toFixed(2)}</Typography>;
            case 'paid':
                return <Typography variant="body2" color="secondary.main" fontWeight={900}>{(claim.requestedAmount - (claim.approvedAmount || claim.requestedAmount * 0.8)).toFixed(2)}</Typography>;
            case 'actions':
                return (
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                        <Tooltip title="عرض التفاصيل">
                            <IconButton size="small" color="primary">
                                <ViewIcon fontSize="inherit" />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="طباعة">
                            <IconButton size="small">
                                <PrintIcon fontSize="inherit" />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                );
            default:
                return null;
        }
    };

    return (
        <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', px: { xs: 2, sm: 3 } }}>
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
                            startIcon={<PrintIcon />}
                            onClick={handleExportPDF}
                            sx={{ borderRadius: 1.5, height: 40 }}
                        >
                            طباعة
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
                            variant="outlined"
                            color="error"
                            startIcon={<PdfIcon />}
                            onClick={handleExportPDF}
                            sx={{ borderRadius: 1.5, height: 40 }}
                        >
                            PDF
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
                                onChange={(e) => setSearchTerm(e.target.value)}
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
                                onChange={(e) => setStatusFilter(e.target.value)}
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
                                }}
                                sx={{ minWidth: 120, height: 40, borderRadius: 1 }}
                            >
                                إعادة ضبط
                            </Button>
                        </Stack>
                    </MainCard>

                    {/* Table View */}
                    <UnifiedMedicalTable
                        columns={columns}
                        rows={paginatedClaims}
                        totalCount={claims.length}
                        page={page}
                        rowsPerPage={rowsPerPage}
                        onPageChange={setPage}
                        onRowsPerPageChange={setRowsPerPage}
                        loading={isLoading}
                        renderCell={renderCell}
                        emptyIcon={ReceiptIcon}
                        emptyMessage="لا توجد مطالبات في هذا الباتش حالياً."
                        sx={{ flexGrow: 1, mb: 1 }}
                    />
                </Stack>
            </Box>
        </Box>
    );
}
