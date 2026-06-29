import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import {
  Box,
  Stack,
  Button,
  TextField,
  MenuItem,
  Typography,
  Chip,
  Tooltip,
  IconButton,
  CircularProgress,
  Alert
} from '@mui/material';
import {
  ReceiptLong as ReceiptIcon,
  TrendingUp as UpIcon,
  Payments as PaymentsIcon,
  FileDownload as FileDownloadIcon,
  Search as SearchIcon,
  Clear as ClearIcon,
  Refresh as RefreshIcon,
  ListAlt as ListAltIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import PermissionGuard from 'components/PermissionGuard';
import GenericDataTable from 'components/GenericDataTable';
import { ModernPageHeader } from 'components/tba';
import useTableState from 'hooks/useTableState';

import paymentsService from 'services/api/payments.service';
import { getEmployers } from 'services/api/employers.service';
import { providersService } from 'services/api';
import { exportToExcel } from 'utils/exportUtils';
import { formatCurrency as formatCurrencyGlobal } from 'utils/currency-formatter';

import PaymentDetailsModal from './components/PaymentDetailsModal';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'الكل' },
  { value: 'UNPAID', label: 'غير مدفوع' },
  { value: 'PARTIALLY_PAID', label: 'مدفوع جزئياً' },
  { value: 'FULLY_PAID', label: 'مدفوع بالكامل' }
];

const STATUS_COLORS = {
  UNPAID: 'error',
  PARTIALLY_PAID: 'warning',
  FULLY_PAID: 'success'
};

const formatCurrency = (value) => {
  if (value === null || value === undefined || isNaN(value)) return formatCurrencyGlobal(0);
  return formatCurrencyGlobal(value);
};

export default function PaymentsManagement() {
  const [isExporting, setIsExporting] = useState(false);
  const [selectedSummary, setSelectedSummary] = useState(null);
  const [detailsOpen, setDetailsOpen] = useState(false);

  const [filters, setFilters] = useState({
    employerId: '',
    providerId: '',
    status: 'ALL',
    year: dayjs().year(),
    month: ''
  });

  const [appliedFilters, setAppliedFilters] = useState({ ...filters });

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: { field: 'targetYear', direction: 'desc' }
  });

  const { data: employersRaw, isLoading: isEmployersLoading } = useQuery({
    queryKey: ['employers-selector'],
    queryFn: () => getEmployers(),
    staleTime: 5 * 60 * 1000
  });

  const { data: providersRaw, isLoading: isProvidersLoading } = useQuery({
    queryKey: ['providers-selector'],
    queryFn: () => providersService.getSelector(),
    staleTime: 5 * 60 * 1000
  });

  const employerOptions = useMemo(() => Array.isArray(employersRaw) ? employersRaw : (employersRaw?.content || []), [employersRaw]);
  const providerOptions = useMemo(() => Array.isArray(providersRaw) ? providersRaw : (providersRaw?.content || providersRaw?.items || []), [providersRaw]);

  const { data: summaries, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['payments-summaries', appliedFilters],
    queryFn: () => paymentsService.getMonthlySettlementSummaries({
      employerId: appliedFilters.employerId || undefined,
      providerId: appliedFilters.providerId || undefined,
      year: appliedFilters.year || undefined,
      month: appliedFilters.month || undefined,
      status: appliedFilters.status !== 'ALL' ? appliedFilters.status : undefined
    }),
    staleTime: 0,
    refetchOnWindowFocus: 'always'
  });

  const totals = useMemo(() => {
    if (!summaries) return { totalAmount: 0, paidAmount: 0, remainingAmount: 0 };
    return summaries.reduce((acc, row) => ({
      totalAmount: acc.totalAmount + Number(row.totalAmount || 0),
      paidAmount: acc.paidAmount + Number(row.paidAmount || 0),
      remainingAmount: acc.remainingAmount + Number(row.remainingAmount || 0)
    }), { totalAmount: 0, paidAmount: 0, remainingAmount: 0 });
  }, [summaries]);

  const applyFilterNow = (field, value) => {
    setFilters(prev => ({ ...prev, [field]: value }));
    setAppliedFilters(prev => ({ ...prev, [field]: value }));
    tableState.setPage(0);
  };

  const applyFilters = () => {
    setAppliedFilters({ ...filters });
    tableState.setPage(0);
  };

  const clearFilters = () => {
    const reset = { employerId: '', providerId: '', status: 'ALL', year: '', month: '' };
    setFilters(reset);
    setAppliedFilters(reset);
    tableState.setPage(0);
  };

  const handleExport = () => {
    if (!summaries || summaries.length === 0) return;
    setIsExporting(true);
    try {
      const exportData = summaries.map(s => ({
        'الشركة / جهة العمل': s.employerName,
        'مزود الخدمة': s.providerName,
        'السنة': s.targetYear,
        'الشهر': s.targetMonth,
        'إجمالي المطالبات': Number(s.totalAmount) || 0,
        'المدفوع': Number(s.paidAmount) || 0,
        'المتبقي': Number(s.remainingAmount) || 0,
        'تاريخ آخر دفعة': s.lastPaymentDate ? dayjs(s.lastPaymentDate).format('YYYY-MM-DD') : '-',
        'حالة السداد': s.paymentStatusLabel
      }));
      exportToExcel(exportData, `سجل_الدفعات_${dayjs().format('YYYY-MM-DD')}`);
    } finally {
      setIsExporting(false);
    }
  };

  const handleViewDetails = (row) => {
    setSelectedSummary(row);
    setDetailsOpen(true);
  };

  const columns = useMemo(() => [
    {
      accessorKey: 'employerName',
      header: 'الشركة / جهة العمل',
      minWidth: '12rem',
      cell: ({ row }) => <Typography variant="body2" fontWeight="bold">{row.original.employerName}</Typography>
    },
    {
      accessorKey: 'providerName',
      header: 'مزود الخدمة',
      minWidth: '12rem',
      cell: ({ row }) => row.original.providerName
    },
    {
      accessorKey: 'period',
      header: 'الفترة (شهر/سنة)',
      minWidth: '8rem',
      align: 'center',
      cell: ({ row }) => `${row.original.targetMonth} / ${row.original.targetYear}`
    },
    {
      accessorKey: 'totalAmount',
      header: 'إجمالي المطالبات',
      minWidth: '9rem',
      align: 'center',
      cell: ({ row }) => formatCurrency(row.original.totalAmount)
    },
    {
      accessorKey: 'paidAmount',
      header: 'المدفوع',
      minWidth: '8rem',
      align: 'center',
      cell: ({ row }) => <Typography color="success.main" fontWeight="bold">{formatCurrency(row.original.paidAmount)}</Typography>
    },
    {
      accessorKey: 'remainingAmount',
      header: 'المتبقي',
      minWidth: '8rem',
      align: 'center',
      cell: ({ row }) => <Typography color="error.main" fontWeight="bold">{formatCurrency(row.original.remainingAmount)}</Typography>
    },
    {
      accessorKey: 'paymentStatus',
      header: 'حالة السداد',
      minWidth: '8rem',
      align: 'center',
      cell: ({ row }) => (
        <Chip 
          label={row.original.paymentStatusLabel} 
          color={STATUS_COLORS[row.original.paymentStatus] || 'default'} 
          size="small" 
        />
      )
    },
    {
      id: 'actions',
      header: 'التفاصيل الدفعات',
      minWidth: '8rem',
      align: 'center',
      cell: ({ row }) => (
        <Tooltip title="عرض الدفعات وتعديلها">
          <Button size="small" variant="outlined" startIcon={<ListAltIcon />} onClick={() => handleViewDetails(row.original)}>
            الدفعات
          </Button>
        </Tooltip>
      )
    }
  ], []);

  const renderSummaryCard = (title, value, icon, borderColor) => (
    <Box sx={{ minWidth: '12rem', height: '3.5rem', px: 2, py: 0.5, border: 1, borderColor, borderRadius: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', bgcolor: 'background.paper' }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>{title}</Typography>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        {isLoading ? <CircularProgress size={16} /> : <Typography variant="body1" fontWeight="bold">{value}</Typography>}
        {icon}
      </Stack>
    </Box>
  );

  return (
    <PermissionGuard requiredRole={['SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER']}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <ModernPageHeader
          title="إدارة الدفعات والتسديدات"
          subtitle="تتبع مبالغ التسويات، المدفوع، والمتبقي لكل شركة ومزود خدمة"
          icon={<PaymentsIcon />}
          breadcrumbs={[
            { label: 'الرئيسية', href: '/' },
            { label: 'التسويات المالية', href: '/settlement' },
            { label: 'الدفعات' }
          ]}
          actions={
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'nowrap', overflowX: 'auto', pb: 0.5 }}>
              {renderSummaryCard('إجمالي المطالبات', formatCurrency(totals.totalAmount), <UpIcon color="primary" />, 'primary.main')}
              {renderSummaryCard('إجمالي المدفوع', formatCurrency(totals.paidAmount), <PaymentsIcon color="success" />, 'success.main')}
              {renderSummaryCard('إجمالي المتبقي', formatCurrency(totals.remainingAmount), <ReceiptIcon color="error" />, 'error.main')}
            </Box>
          }
        />

        <MainCard sx={{ mt: -1 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ rowGap: 1.5 }}>
            <TextField
              select
              size="small"
              label="الشركة (جهة العمل)"
              value={filters.employerId}
              onChange={(e) => applyFilterNow('employerId', e.target.value)}
              sx={{ minWidth: '12rem' }}
              disabled={isEmployersLoading}
            >
              <MenuItem value="">الكل</MenuItem>
              {employerOptions.map((e) => (
                <MenuItem key={e.id || e.value} value={e.id || e.value}>{e.name || e.nameAr || e.label || `وثيقة #${e.id || e.value}`}</MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="مقدم الخدمة"
              value={filters.providerId}
              onChange={(e) => applyFilterNow('providerId', e.target.value)}
              sx={{ minWidth: '12rem' }}
              disabled={isProvidersLoading}
            >
              <MenuItem value="">الكل</MenuItem>
              {providerOptions.map((p) => (
                <MenuItem key={p.id} value={p.id}>{p.name || `مزود #${p.id}`}</MenuItem>
              ))}
            </TextField>

            <TextField
              type="number"
              size="small"
              label="السنة"
              value={filters.year}
              onChange={(e) => applyFilterNow('year', e.target.value)}
              sx={{ width: '6rem' }}
            />

            <TextField
              type="number"
              size="small"
              label="الشهر"
              value={filters.month}
              onChange={(e) => applyFilterNow('month', e.target.value)}
              sx={{ width: '6rem' }}
              inputProps={{ min: 1, max: 12 }}
            />

            <TextField
              select
              size="small"
              label="حالة السداد"
              value={filters.status}
              onChange={(e) => applyFilterNow('status', e.target.value)}
              sx={{ minWidth: '10rem' }}
            >
              {STATUS_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>

            <Button variant="contained" startIcon={<SearchIcon />} onClick={applyFilters} sx={{ height: '2.5rem' }}>بحث</Button>
            <Tooltip title="مسح الفلاتر">
              <IconButton onClick={clearFilters} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}><ClearIcon /></IconButton>
            </Tooltip>
            
            <Box sx={{ flexGrow: 1 }} />
            
            <Tooltip title="تحديث">
              <IconButton color="primary" onClick={refetch} disabled={isLoading} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                <RefreshIcon />
              </IconButton>
            </Tooltip>

            <Button
              variant="outlined"
              color="success"
              startIcon={isExporting ? <CircularProgress size={16} color="inherit" /> : <FileDownloadIcon />}
              onClick={handleExport}
              disabled={isExporting || !summaries?.length}
            >
              تصدير إكسل
            </Button>
          </Stack>
        </MainCard>

        {isError && <Alert severity="error">{error?.message || 'تعذر جلب البيانات. يرجى المحاولة لاحقاً.'}</Alert>}

        <MainCard content={false}>
          <GenericDataTable
            columns={columns}
            data={summaries || []}
            isLoading={isLoading}
            tableState={tableState}
            enableFiltering={false}
            enablePagination={true}
            compact={true}
            emptyMessage="لا توجد بيانات تطابق معايير البحث"
          />
        </MainCard>
      </Box>

      {detailsOpen && selectedSummary && (
        <PaymentDetailsModal
          open={detailsOpen}
          onClose={() => setDetailsOpen(false)}
          summary={selectedSummary}
          onPaymentChanged={refetch}
        />
      )}
    </PermissionGuard>
  );
}
