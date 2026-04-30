import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';

// MUI
import { Box, Chip, Typography, Stack, Button, Alert, Tooltip, IconButton, TextField, MenuItem, Grid, CircularProgress } from '@mui/material';
import {
  ReceiptLong as ReceiptIcon,
  TrendingUp as UpIcon,
  Payments as PaymentsIcon,
  FileDownload as FileDownloadIcon,
  Print as PrintIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  Clear as ClearIcon
} from '@mui/icons-material';

// Project Components
import MainCard from 'components/MainCard';
import PermissionGuard from 'components/PermissionGuard';
import GenericDataTable from 'components/GenericDataTable';
import { ModernPageHeader } from 'components/tba';

// Hooks
import useTableState from 'hooks/useTableState';

// Services
import { claimsService } from 'services/api/claims.service';
import { providersService } from 'services/api';
import { getActiveContractByProvider } from 'services/api/provider-contracts.service';
import { getEmployers } from 'services/api/employers.service';

// Utils
import { exportToExcel } from 'utils/exportUtils';
import { formatCurrency as formatCurrencyGlobal } from 'utils/currency-formatter';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'الكل' },
  { value: 'APPROVED', label: 'موافق عليها' },
  { value: 'REJECTED', label: 'مرفوضة' }
];

const STATUS_LABELS = {
  APPROVED: 'موافق عليها',
  REJECTED: 'مرفوضة',
  BATCHED: 'مدرجة في دفعة',
  SETTLED: 'تمت التسوية'
};

const STATUS_COLORS = {
  APPROVED: 'success',
  REJECTED: 'error',
  BATCHED: 'info',
  SETTLED: 'primary'
};

const COMPANY_SHARE_PERCENT = 10;

const formatCurrency = (value) => {
  if (value === null || value === undefined || isNaN(value)) return formatCurrencyGlobal(0);
  return formatCurrencyGlobal(value);
};

const formatDateParam = (value) => {
  if (!value) return undefined;
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD') : undefined;
};

const getRefusedAmount = (row) => {
  const refused = Number(row?.refusedAmount);
  return Number.isFinite(refused) && refused > 0 ? refused : 0;
};

const getPayableAmount = (row) => {
  // إذا كان approvedAmount موجوداً صراحةً (حتى لو صفر = مرفوضة بالكامل) نستخدمه
  if (row?.approvedAmount !== null && row?.approvedAmount !== undefined) {
    const approved = Number(row.approvedAmount);
    return Number.isFinite(approved) && approved >= 0 ? approved : 0;
  }
  // فولباك: المبلغ المطلوب بعد حذف المرفوض
  const requested = Number(row?.requestedAmount);
  if (Number.isFinite(requested) && requested > 0) {
    const payableAfterRefused = requested - getRefusedAmount(row);
    return payableAfterRefused > 0 ? payableAfterRefused : 0;
  }
  return 0;
};

const sortFieldMap = {
  claimNumber: 'id',
  serviceDate: 'serviceDate',
  providerName: 'providerName',
  requestedAmount: 'requestedAmount',
  payableAmount: 'approvedAmount',
  providerDiscountPercent: 'approvedAmount',
  companyShare: 'approvedAmount',
  facilityShare: 'approvedAmount',
  status: 'status',
  createdAt: 'createdAt'
};

export default function ProviderAccountsList() {
  const [isExporting, setIsExporting] = useState(false);
  const [filters, setFilters] = useState({
    status: 'ALL',
    providerId: '',
    employerId: '',
    dateFrom: '',
    dateTo: '',
    serviceDateFrom: '',
    serviceDateTo: ''
  });

  const [appliedFilters, setAppliedFilters] = useState({
    status: 'ALL',
    providerId: '',
    employerId: '',
    dateFrom: '',
    dateTo: '',
    serviceDateFrom: '',
    serviceDateTo: ''
  });

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: { field: 'createdAt', direction: 'desc' }
  });

  const { data: providersRaw, isLoading: isProvidersLoading } = useQuery({
    queryKey: ['providers-selector'],
    queryFn: () => providersService.getSelector(),
    staleTime: 5 * 60 * 1000
  });

  const { data: employersRaw, isLoading: isEmployersLoading } = useQuery({
    queryKey: ['employers-selector'],
    queryFn: () => getEmployers(),
    staleTime: 5 * 60 * 1000
  });

  const employerOptions = useMemo(() => {
    if (!employersRaw) return [];
    if (Array.isArray(employersRaw)) return employersRaw;
    if (Array.isArray(employersRaw?.content)) return employersRaw.content;
    return [];
  }, [employersRaw]);

  const providerOptions = useMemo(() => {
    if (!providersRaw) return [];
    if (Array.isArray(providersRaw)) return providersRaw;
    if (Array.isArray(providersRaw?.content)) return providersRaw.content;
    if (Array.isArray(providersRaw?.items)) return providersRaw.items;
    return [];
  }, [providersRaw]);

  const currentSort = tableState.sorting?.[0] || null;
  const sortBy = currentSort ? sortFieldMap[currentSort.id] || 'createdAt' : 'createdAt';
  const sortDir = currentSort?.desc ? 'desc' : 'asc';

  // ─── إجماليات الخلفية (دقيقة وشاملة لكل السجلات المفلترة) ───
  const { data: summaryData, isLoading: isSummaryLoading } = useQuery({
    queryKey: ['settlement-claims-summary', appliedFilters],
    queryFn: () =>
      claimsService.getFinancialSummary({
        status: appliedFilters.status !== 'ALL' ? appliedFilters.status : undefined,
        providerId: appliedFilters.providerId || undefined,
        employerId: appliedFilters.employerId || undefined,
        // ملاحظة: financial-summary لا يدعم createdDateFrom/To (قيد الـ backend)
        // نُرسل فقط فلتر تاريخ الخدمة
        dateFrom: formatDateParam(appliedFilters.serviceDateFrom),
        dateTo: formatDateParam(appliedFilters.serviceDateTo)
      }),
    staleTime: 0,
    refetchOnWindowFocus: 'always',
    refetchOnMount: 'always',
    keepPreviousData: true
  });

  const { data: claimsData, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['settlement-claims', appliedFilters, tableState.page, tableState.pageSize, sortBy, sortDir],
    queryFn: () => {
      const params = {
        page: tableState.page + 1,
        size: tableState.pageSize,
        sortBy,
        sortDir,
        status: appliedFilters.status !== 'ALL' ? appliedFilters.status : undefined,
        providerId: appliedFilters.providerId || undefined,
        employerId: appliedFilters.employerId || undefined,
        createdDateFrom: formatDateParam(appliedFilters.dateFrom),
        createdDateTo: formatDateParam(appliedFilters.dateTo),
        dateFrom: formatDateParam(appliedFilters.serviceDateFrom),
        dateTo: formatDateParam(appliedFilters.serviceDateTo)
      };
      return claimsService.list(params);
    },
    staleTime: 0,
    refetchOnWindowFocus: 'always',
    refetchOnMount: 'always',
    keepPreviousData: true
  });

  const claims = claimsData?.items || claimsData?.content || [];
  const totalElements = claimsData?.total ?? claimsData?.totalElements ?? 0;

  const providerIdsInPage = useMemo(() => {
    const ids = claims.map((row) => Number(row.providerId)).filter((id) => Number.isFinite(id) && id > 0);
    return [...new Set(ids)];
  }, [claims]);

  const { data: providerDiscountMap = {} } = useQuery({
    queryKey: ['active-contract-discounts', providerIdsInPage],
    enabled: providerIdsInPage.length > 0,
    staleTime: 5 * 60 * 1000,
    queryFn: async () => {
      const pairs = await Promise.all(
        providerIdsInPage.map(async (providerId) => {
          try {
            const contract = await getActiveContractByProvider(providerId);
            const discount = Number(contract?.discountPercent);
            return [providerId, Number.isFinite(discount) ? discount : 0];
          } catch {
            return [providerId, 0];
          }
        })
      );

      return Object.fromEntries(pairs);
    }
  });

  const getDiscountPercent = (row) => {
    const fromRow = Number(row?.providerDiscountPercent);
    if (Number.isFinite(fromRow) && fromRow > 0) return fromRow;

    const providerId = Number(row?.providerId);
    if (!Number.isFinite(providerId)) return 0;

    const fromContract = Number(providerDiscountMap[providerId]);
    return Number.isFinite(fromContract) ? fromContract : 0;
  };

  const getFacilityShareAmount = (row) => {
    const payable = getPayableAmount(row);
    const discount = getDiscountPercent(row);
    const facilityShare = payable - (payable * discount) / 100;
    return facilityShare > 0 ? facilityShare : 0;
  };

  const getCompanyShareAmount = (row) => {
    const payable = getPayableAmount(row);
    return (payable * COMPANY_SHARE_PERCENT) / 100;
  };

  // الإجماليات: العدد من الـ pagination (دقيق لكل الفلاتر)، المبالغ من financial-summary API
  const totals = useMemo(() => {
    const s = summaryData || {};
    const payable = Number(s.totalApprovedAmount) || 0;
    const paid = Number(s.totalPaidAmount) || 0;
    const companyShare = (payable * COMPANY_SHARE_PERCENT) / 100;
    return {
      count: totalElements,
      gross: Number(s.totalClaimsAmount) || 0,
      refused: Number(s.totalRefusedAmount) || 0,
      payable,
      companyShare,                    // 10% من إجمالي المستحق = حصة الشركة
      facilityShare: payable - companyShare  // 90% من إجمالي المستحق = حصة المرفق
    };
  }, [summaryData, totalElements]);

  const renderSummaryCard = (title, value, icon, borderColor = 'primary.main', loading = false) => (
    <Box
      sx={{
        minWidth: '10.625rem',
        height: '3.0rem',
        px: '0.625rem',
        py: 0.5,
        border: 1,
        borderColor,
        borderRadius: 1,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        gap: 0.25,
        bgcolor: 'background.paper'
      }}
    >
      <Typography variant="caption" sx={{ lineHeight: 1.1, color: 'text.secondary', fontWeight: 600, whiteSpace: 'nowrap' }}>
        {title}
      </Typography>
      <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between">
        {loading ? (
          <Box sx={{ width: '5rem', height: '1rem', borderRadius: 0.5, bgcolor: 'action.hover', animation: 'pulse 1.5s ease-in-out infinite', '@keyframes pulse': { '0%,100%': { opacity: 1 }, '50%': { opacity: 0.4 } } }} />
        ) : (
          <Typography variant="body2" sx={{ lineHeight: 1.1, fontWeight: 700, whiteSpace: 'nowrap' }}>
            {value}
          </Typography>
        )}
        {icon}
      </Stack>
    </Box>
  );

  const applyFilters = () => {
    tableState.setPage(0);
    setAppliedFilters({ ...filters });
  };

  // للفلاتر غير التاريخية: تُطبَّق فوراً دون الحاجة لزر البحث
  const applyFilterNow = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setAppliedFilters((prev) => ({ ...prev, [field]: value }));
    tableState.setPage(0);
  };

  const clearFilters = () => {
    const reset = { status: 'ALL', providerId: '', employerId: '', dateFrom: '', dateTo: '', serviceDateFrom: '', serviceDateTo: '' };
    setFilters(reset);
    setAppliedFilters(reset);
    tableState.setPage(0);
  };

  const handleExport = async () => {
    if (isExporting) return;
    setIsExporting(true);
    try {
      const allData = await claimsService.list({
        status: appliedFilters.status !== 'ALL' ? appliedFilters.status : undefined,
        providerId: appliedFilters.providerId || undefined,
        employerId: appliedFilters.employerId || undefined,
        createdDateFrom: formatDateParam(appliedFilters.dateFrom),
        createdDateTo: formatDateParam(appliedFilters.dateTo),
        dateFrom: formatDateParam(appliedFilters.serviceDateFrom),
        dateTo: formatDateParam(appliedFilters.serviceDateTo),
        page: 1,
        size: 5000,
        sortBy,
        sortDir
      });
      const allClaims = allData?.items || allData?.content || [];
      if (!allClaims.length) return;
      const exportRows = allClaims.map((item) => ({
        'رقم المطالبة': item.claimNumber || `CLM-${item.id}`,
        'الوثيقة (جهة العمل)': item.employerName || '',
        'تاريخ الخدمة': item.visitDate || item.serviceDate || '',
        'مقدم الخدمة': item.providerName || '',
        'المبلغ الإجمالي (قبل)': Number(item.requestedAmount) || 0,
        'نسبة التخفيض (%)': Number(item.providerDiscountPercent) || 0,
        'المبلغ المرفوض': getRefusedAmount(item),
        'القيمة المستحقة': getPayableAmount(item),
        'حصة الشركة (10%)': getCompanyShareAmount(item),
        'نصيب المرفق': getFacilityShareAmount(item),
        'الحالة': STATUS_LABELS[item.status] || item.status || ''
      }));
      exportToExcel(exportRows, `مطالبات_مقدمي_الخدمة_${dayjs().format('YYYY-MM-DD')}`);
    } catch (err) {
      console.error('فشل التصدير:', err);
    } finally {
      setIsExporting(false);
    }
  };

  const handlePrint = () => {
    const printRows = claims.map((row, idx) => {
      const discount = getDiscountPercent(row);
      const payable = getPayableAmount(row);
      const facilityShare = getFacilityShareAmount(row);
      const companyShare = getCompanyShareAmount(row);
      const status = STATUS_LABELS[row.status] || row.status || '';
      return `<tr>
        <td>${idx + 1}</td>
        <td>${row.claimNumber || `CLM-${row.id}`}</td>
        <td>${row.employerName || '-'}</td>
        <td>${row.visitDate || row.serviceDate || '-'}</td>
        <td>${row.providerName || '-'}</td>
        <td>${formatCurrency(row.requestedAmount)}</td>
        <td>${discount}%</td>
        <td style="color:#cf1322">${formatCurrency(getRefusedAmount(row))}</td>
        <td><b>${formatCurrency(payable)}</b></td>
        <td style="color:#d46b08">${formatCurrency(companyShare)}</td>
        <td style="color:#389e0d">${formatCurrency(facilityShare)}</td>
        <td>${status}</td>
      </tr>`;
    }).join('');

    const win = window.open('', '_blank', 'width=1200,height=800');
    win.document.write(`<!DOCTYPE html>
<html dir="rtl" lang="ar">
<head>
  <meta charset="UTF-8" />
  <title>مطالبات مقدمي الخدمة</title>
  <style>
    body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; direction: rtl; margin: 1.5rem; font-size: 0.8rem; color: #222; }
    h2 { font-size: 1.1rem; margin-bottom: 0.5rem; }
    p { margin: 0.2rem 0; color: #555; font-size: 0.75rem; }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th { background: #1677ff; color: #fff; padding: 0.4rem 0.5rem; text-align: center; font-weight: 600; font-size: 0.72rem; white-space: nowrap; }
    td { padding: 0.35rem 0.5rem; text-align: center; border-bottom: 1px solid #f0f0f0; white-space: nowrap; font-size: 0.72rem; }
    tr:nth-child(even) td { background: #fafafa; }
    tfoot td { font-weight: bold; background: #f0f7ff; border-top: 2px solid #1677ff; }
    @media print { body { margin: 0.5rem; } }
  </style>
</head>
<body>
  <h2>قائمة مطالبات مقدمي الخدمة</h2>
  <p>تاريخ الطباعة: ${new Date().toLocaleDateString('ar-LY')}</p>
  ${appliedFilters.status !== 'ALL' ? `<p>الحالة: ${STATUS_LABELS[appliedFilters.status] || appliedFilters.status}</p>` : ''}
  <table>
    <thead>
      <tr>
        <th>#</th><th>رقم المطالبة</th><th>الوثيقة</th><th>تاريخ الخدمة</th>
        <th>مقدم الخدمة</th><th>الإجمالي (قبل)</th><th>نسبة الخصم</th>
        <th>المرفوض</th><th>المستحق</th><th>حصة الشركة</th><th>نصيب المرفق</th><th>الحالة</th>
      </tr>
    </thead>
    <tbody>${printRows}</tbody>
    <tfoot>
      <tr>
        <td colspan="5"><b>الإجمالي (${totals.count} مطالبة)</b></td>
        <td>${formatCurrency(totals.gross)}</td>
        <td>-</td>
        <td style="color:#cf1322">${formatCurrency(totals.refused)}</td>
        <td>${formatCurrency(totals.payable)}</td>
        <td style="color:#d46b08">${formatCurrency(totals.companyShare)}</td>
        <td style="color:#389e0d">${formatCurrency(totals.facilityShare)}</td>
        <td></td>
      </tr>
    </tfoot>
  </table>
</body>
</html>`);
    win.document.close();
    win.focus();
    setTimeout(() => win.print(), 400);
  };

  const columns = useMemo(
    () => [
      {
        accessorKey: 'claimNumber',
        header: 'رقم المطالبة',
        minWidth: '8.125rem',
        align: 'center',
        cell: ({ row }) => <Typography fontWeight="bold">{row.original.claimNumber || `CLM-${row.original.id}`}</Typography>
      },
      {
        accessorKey: 'employerName',
        header: 'الوثيقة',
        minWidth: '10rem',
        align: 'center',
        cell: ({ row }) => (
          <Typography variant="body2" noWrap>{row.original.employerName || '-'}</Typography>
        )
      },
      {
        accessorKey: 'serviceDate',
        header: 'تاريخ الخدمة',
        minWidth: '7.8125rem',
        align: 'center',
        cell: ({ row }) => {
          const value = row.original.visitDate || row.original.serviceDate;
          return value ? dayjs(value).format('DD/MM/YYYY') : '-';
        }
      },
      {
        accessorKey: 'providerName',
        header: 'مقدم الخدمة',
        minWidth: '11.25rem',
        align: 'center',
        cell: ({ row }) => row.original.providerName || '-'
      },
      {
        accessorKey: 'requestedAmount',
        header: 'المبلغ الإجمالي (قبل)',
        minWidth: '9.375rem',
        align: 'center',
        cell: ({ row }) => formatCurrency(row.original.requestedAmount)
      },
      {
        accessorKey: 'providerDiscountPercent',
        header: 'نسبة التخفيض',
        minWidth: '7.5rem',
        align: 'center',
        cell: ({ row }) => {
          const discount = getDiscountPercent(row.original);
          return <Chip label={`${discount}%`} size="small" color={discount > 0 ? 'primary' : 'default'} variant="outlined" />;
        }
      },
      {
        accessorKey: 'refusedAmount',
        header: 'المبلغ المرفوض',
        minWidth: '8.4375rem',
        align: 'center',
        cell: ({ row }) => (
          <Typography color="error.main" fontWeight="bold">
            {formatCurrency(getRefusedAmount(row.original))}
          </Typography>
        )
      },
      {
        accessorKey: 'payableAmount',
        header: 'القيمة المستحقة',
        minWidth: '8.75rem',
        align: 'center',
        cell: ({ row }) => <Typography fontWeight="bold">{formatCurrency(getPayableAmount(row.original))}</Typography>
      },
      {
        accessorKey: 'companyShare',
        header: 'حصة الشركة',
        minWidth: '7.5rem',
        align: 'center',
        cell: ({ row }) => (
          <Typography color="warning.main" fontWeight="bold">
            {formatCurrency(getCompanyShareAmount(row.original))}
          </Typography>
        )
      },
      {
        accessorKey: 'facilityShare',
        header: 'نصيب المرفق',
        minWidth: '8.125rem',
        align: 'center',
        cell: ({ row }) => (
          <Typography color="success.main" fontWeight="bold">
            {formatCurrency(getFacilityShareAmount(row.original))}
          </Typography>
        )
      },
      {
        accessorKey: 'status',
        header: 'الحالة',
        minWidth: '7.5rem',
        align: 'center',
        cell: ({ row }) => {
          const status = row.original.status || 'DRAFT';
          return (
            <Chip
              label={STATUS_LABELS[status] || status}
              color={STATUS_COLORS[status] || 'default'}
              size="small"
              sx={{ minWidth: '7rem', justifyContent: 'center' }}
            />
          );
        }
      }
    ],
    [providerDiscountMap]
  );

  return (
    <PermissionGuard requiredRole={['SUPER_ADMIN', 'FINANCE_MANAGER', 'INSURANCE_ADMIN', 'ACCOUNTANT']}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <ModernPageHeader
          title="مطالبات مقدمي الخدمة"
          subtitle="قائمة تفصيلية بمطالبات مقدمي الخدمة"
          icon={<ReceiptIcon />}
          breadcrumbs={[
            { label: 'الرئيسية', href: '/' },
            { label: 'التسويات المالية', href: '/settlement' },
            { label: 'المطالبات' }
          ]}
          actions={
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'nowrap',
                gap: 1,
                justifyContent: { xs: 'flex-start', md: 'flex-end' },
                alignItems: 'stretch',
                overflowX: 'auto',
                pb: 0.25,
                '&::-webkit-scrollbar': { height: '0.375rem' }
              }}
            >
              {renderSummaryCard('إجمالي المطالبات', String(totals.count), <ReceiptIcon fontSize="small" color="primary" />, 'primary.main')}
              {renderSummaryCard('إجمالي قبل', formatCurrency(totals.gross), <UpIcon fontSize="small" color="info" />, 'info.main', isSummaryLoading)}
              {renderSummaryCard('إجمالي المرفوض', formatCurrency(totals.refused), <ClearIcon fontSize="small" color="error" />, 'error.main', isSummaryLoading)}
              {renderSummaryCard('إجمالي المستحق', formatCurrency(totals.payable), <PaymentsIcon fontSize="small" color="secondary" />, 'secondary.main', isSummaryLoading)}
              {renderSummaryCard('حصة الشركة', formatCurrency(totals.companyShare), <PaymentsIcon fontSize="small" color="warning" />, 'warning.main', isSummaryLoading)}
              {renderSummaryCard('حصة المرفق', formatCurrency(totals.facilityShare), <PaymentsIcon fontSize="small" color="success" />, 'success.main', isSummaryLoading)}
            </Box>
          }
        />

        <MainCard sx={{ mt: -1.25 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="nowrap" sx={{ overflowX: 'auto', pb: 0.5 }}>
            <TextField
              select
              size="small"
              label="حالة المطالبة"
              value={filters.status}
              onChange={(e) => applyFilterNow('status', e.target.value)}
              SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: '20.0rem' } } } }}
              sx={{ minWidth: '9rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
            >
              {STATUS_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="الوثيقة (جهة العمل)"
              value={filters.employerId}
              onChange={(e) => applyFilterNow('employerId', e.target.value)}
              disabled={isEmployersLoading}
              SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: '20.0rem' } } } }}
              sx={{ minWidth: '10rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
            >
              <MenuItem value="">الكل</MenuItem>
              {employerOptions.map((e) => (
                <MenuItem key={e.id} value={e.id}>
                  {e.name || `وثيقة #${e.id}`}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="مقدم الخدمة"
              value={filters.providerId}
              onChange={(e) => applyFilterNow('providerId', e.target.value)}
              disabled={isProvidersLoading}
              SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: '20.0rem' } } } }}
              sx={{ minWidth: '10rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
            >
              <MenuItem value="">الكل</MenuItem>
              {providerOptions.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name || `مقدم خدمة #${p.id}`}
                </MenuItem>
              ))}
            </TextField>

            <DatePicker
              label="من إدخال المطالبة"
              value={filters.dateFrom ? dayjs(filters.dateFrom) : null}
              onChange={(newValue) =>
                setFilters((prev) => ({ ...prev, dateFrom: newValue?.isValid() ? newValue.format('YYYY-MM-DD') : '' }))
              }
              format="DD/MM/YYYY"
              slotProps={{
                textField: {
                  size: 'small',
                  sx: { minWidth: '8.5rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }
                }
              }}
            />

            <DatePicker
              label="إلى إدخال المطالبة"
              value={filters.dateTo ? dayjs(filters.dateTo) : null}
              onChange={(newValue) =>
                setFilters((prev) => ({ ...prev, dateTo: newValue?.isValid() ? newValue.format('YYYY-MM-DD') : '' }))
              }
              format="DD/MM/YYYY"
              slotProps={{
                textField: {
                  size: 'small',
                  sx: { minWidth: '8.5rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }
                }
              }}
            />

            <DatePicker
              label="تاريخ الخدمة من"
              value={filters.serviceDateFrom ? dayjs(filters.serviceDateFrom) : null}
              onChange={(newValue) =>
                setFilters((prev) => ({ ...prev, serviceDateFrom: newValue?.isValid() ? newValue.format('YYYY-MM-DD') : '' }))
              }
              format="DD/MM/YYYY"
              slotProps={{
                textField: {
                  size: 'small',
                  sx: { minWidth: '8.5rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }
                }
              }}
            />

            <DatePicker
              label="تاريخ الخدمة إلى"
              value={filters.serviceDateTo ? dayjs(filters.serviceDateTo) : null}
              onChange={(newValue) =>
                setFilters((prev) => ({ ...prev, serviceDateTo: newValue?.isValid() ? newValue.format('YYYY-MM-DD') : '' }))
              }
              format="DD/MM/YYYY"
              slotProps={{
                textField: {
                  size: 'small',
                  sx: { minWidth: '8.5rem', '& .MuiInputLabel-root': { fontSize: '0.75rem' }, '& .MuiInputBase-input': { fontSize: '0.75rem' } }
                }
              }}
            />

            <Button variant="contained" startIcon={<SearchIcon />} onClick={applyFilters} sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap' }}>

              بحث
            </Button>
            <Tooltip title="مسح الفلاتر">
              <IconButton color="default" onClick={clearFilters} sx={{ height: '2.5rem', width: '2.5rem', border: '1px solid', borderColor: 'divider', borderRadius: 1, flexShrink: 0 }}>
                <ClearIcon />
              </IconButton>
            </Tooltip>

            <Box sx={{ flexGrow: 1 }} />

            <Tooltip title="تحديث">
              <IconButton
                onClick={refetch}
                color="primary"
                disabled={isLoading}
                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem', flexShrink: 0 }}
              >
                <RefreshIcon />
              </IconButton>
            </Tooltip>

            <Button
              variant="outlined"
              color="primary"
              startIcon={<PrintIcon />}
              onClick={handlePrint}
              sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1, flexShrink: 0 }}
            >
              طباعة
            </Button>

            <Button
              variant="outlined"
              color="success"
              startIcon={isExporting ? <CircularProgress size="0.9rem" color="inherit" /> : <FileDownloadIcon />}
              onClick={handleExport}
              disabled={isExporting || totalElements === 0}
              sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1, flexShrink: 0 }}
            >
              {isExporting ? 'جارٍ التصدير...' : 'تصدير'}
            </Button>
          </Stack>
        </MainCard>

        {(appliedFilters.dateFrom || appliedFilters.dateTo) && (
          <Alert severity="warning" icon={false} sx={{ py: 0.25, px: 1.5, fontSize: '0.72rem', '& .MuiAlert-message': { lineHeight: 1.5 } }}>
            ملاحظة: الإجماليات المالية (قبل / مرفوض / مستحق / مدفوع / غير مسوى) تعكس فلتر تاريخ الخدمة فقط — فلتر تاريخ الإدخال لا يُطبَّق على الإجماليات (قيد الـ backend). العدد الكلي للمطالبات دقيق لجميع الفلاتر.
          </Alert>
        )}
        {isError && <Alert severity="error">{error?.message || 'تعذر جلب البيانات. يرجى المحاولة مجدداً.'}</Alert>}

        <MainCard content={false}>
          <GenericDataTable
            columns={columns}
            data={claims}
            totalCount={totalElements}
            isLoading={isLoading}
            tableState={tableState}
            enableFiltering={false}
            enableSorting={true}
            enablePagination={true}
            compact={true}
            tableSize="small"
            stickyHeader={false}
            minHeight={0}
            maxHeight="auto"
            emptyMessage="لا توجد مطالبات تطابق معايير البحث"
            rowsPerPageOptions={[10, 25, 50, 100]}
          />
        </MainCard>
      </Box>
    </PermissionGuard>
  );
}


