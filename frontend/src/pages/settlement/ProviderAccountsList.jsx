import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';

// MUI
import { Box, Chip, Typography, Stack, Button, Alert, Tooltip, IconButton, TextField, MenuItem, Grid } from '@mui/material';
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

// Utils
import { exportToExcel } from 'utils/exportUtils';
import { formatCurrency as formatCurrencyGlobal } from 'utils/currency-formatter';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'الكل' },
  { value: 'APPROVED', label: 'موافق عليها' },
  { value: 'BATCHED', label: 'مدرجة في دفعة' },
  { value: 'SETTLED', label: 'تمت التسوية' },
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
  const approved = Number(row?.approvedAmount);
  if (Number.isFinite(approved) && approved > 0) return approved;

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
  providerDiscountPercent: 'createdAt',
  companyShare: 'createdAt',
  facilityShare: 'createdAt',
  status: 'status',
  createdAt: 'createdAt'
};

export default function ProviderAccountsList() {
  const [filters, setFilters] = useState({
    status: 'ALL',
    providerId: '',
    dateFrom: '',
    dateTo: ''
  });

  const [appliedFilters, setAppliedFilters] = useState({
    status: 'ALL',
    providerId: '',
    dateFrom: '',
    dateTo: ''
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
        createdDateFrom: formatDateParam(appliedFilters.dateFrom),
        createdDateTo: formatDateParam(appliedFilters.dateTo)
      };
      return claimsService.list(params);
    },
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

  const totals = useMemo(() => {
    const totalPayable = claims.reduce((acc, curr) => acc + getPayableAmount(curr), 0);
    const totalCompanyShare = claims.reduce((acc, curr) => acc + getCompanyShareAmount(curr), 0);
    const totalRefused = claims.reduce((acc, curr) => acc + getRefusedAmount(curr), 0);

    return {
      count: totalElements,
      gross: claims.reduce((acc, curr) => acc + (Number(curr.requestedAmount) || 0), 0),
      refused: totalRefused,
      payable: totalPayable,
      companyShare: totalCompanyShare,
      facilityShare: claims.reduce((acc, curr) => acc + getFacilityShareAmount(curr), 0)
    };
  }, [claims, totalElements, providerDiscountMap]);

  const renderSummaryCard = (title, value, icon, borderColor = 'primary.main') => (
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
        <Typography variant="body2" sx={{ lineHeight: 1.1, fontWeight: 700, whiteSpace: 'nowrap' }}>
          {value}
        </Typography>
        {icon}
      </Stack>
    </Box>
  );

  const applyFilters = () => {
    tableState.setPage(0);
    setAppliedFilters({ ...filters });
  };

  const clearFilters = () => {
    const reset = { status: 'ALL', providerId: '', dateFrom: '', dateTo: '' };
    setFilters(reset);
    setAppliedFilters(reset);
    tableState.setPage(0);
  };

  const handleExport = () => {
    if (!claims.length) return;
    const exportRows = claims.map((item) => ({
      'رقم المطالبة': item.claimNumber || `CLM-${item.id}`,
      'تاريخ الخدمة': item.visitDate || item.serviceDate || '',
      'مقدم الخدمة': item.providerName || '',
      'المبلغ الإجمالي (قبل)': Number(item.requestedAmount) || 0,
      'المبلغ المرفوض': getRefusedAmount(item),
      'القيمة المستحقة': getPayableAmount(item),
      'نسبة التخفيض (%)': getDiscountPercent(item),
      'حصة الشركة (10%)': getCompanyShareAmount(item),
      'نصيب المرفق': getFacilityShareAmount(item),
      'الحالة': STATUS_LABELS[item.status] || item.status || ''
    }));

    exportToExcel(exportRows, `مطالبات_مقدمي_الخدمة_${dayjs().format('YYYY-MM-DD')}`);
  };

  const handlePrint = () => {
    window.print();
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
        accessorKey: 'serviceDate',
        header: 'تاريخ الخدمة',
        minWidth: '7.8125rem',
        align: 'center',
        cell: ({ row }) => {
          const value = row.original.visitDate || row.original.serviceDate;
          return value ? dayjs(value).format('YYYY/MM/DD') : '-';
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
          return <Chip label={STATUS_LABELS[status] || status} color={STATUS_COLORS[status] || 'default'} size="small" />;
        }
      }
    ],
    [providerDiscountMap]
  );

  return (
    <PermissionGuard requiredRole={['SUPER_ADMIN', 'FINANCE_MANAGER', 'INSURANCE_ADMIN']}>
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
              {renderSummaryCard('إجمالي قبل', formatCurrency(totals.gross), <UpIcon fontSize="small" color="info" />, 'info.main')}
              {renderSummaryCard('إجمالي المرفوض', formatCurrency(totals.refused), <ClearIcon fontSize="small" color="error" />, 'error.main')}
              {renderSummaryCard('إجمالي القيمة المستحقة', formatCurrency(totals.payable), <PaymentsIcon fontSize="small" color="secondary" />, 'secondary.main')}
              {renderSummaryCard('حصة الشركة', formatCurrency(totals.companyShare), <PaymentsIcon fontSize="small" color="warning" />, 'warning.main')}
              {renderSummaryCard('نصيب المرفق', formatCurrency(totals.facilityShare), <PaymentsIcon fontSize="small" color="success" />, 'success.main')}
            </Box>
          }
        />

        <MainCard sx={{ mt: -1.25 }}>
          <Grid container spacing={1.5} alignItems="center">
            <Grid item xs={12}>
              <Grid container spacing={1.5} alignItems="center">
                <Grid item xs={12} sm={6} md={3} lg={2}>
                  <TextField
                    select
                    fullWidth
                    label="حالة المطالبة"
                    value={filters.status}
                    onChange={(e) => setFilters((prev) => ({ ...prev, status: e.target.value }))}
                    SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: '20.0rem' } } } }}
                    sx={{ minWidth: '10.625rem', '& .MuiInputBase-root': { height: '2.5rem' } }}
                  >
                    {STATUS_OPTIONS.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>

                <Grid item xs={12} sm={6} md={3} lg={2}>
                  <TextField
                    select
                    fullWidth
                    label="مقدم الخدمة"
                    value={filters.providerId}
                    onChange={(e) => setFilters((prev) => ({ ...prev, providerId: e.target.value }))}
                    disabled={isProvidersLoading}
                    SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: '20.0rem' } } } }}
                    sx={{ minWidth: '11.25rem', '& .MuiInputBase-root': { height: '2.5rem' } }}
                  >
                    <MenuItem value="">الكل</MenuItem>
                    {providerOptions.map((p) => (
                      <MenuItem key={p.id} value={p.id}>
                        {p.name || `مقدم خدمة #${p.id}`}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>

                <Grid item xs={12} sm={6} md={2} lg={2}>
                  <TextField
                    fullWidth
                    type="date"
                    label="من إدخال المطالبة"
                    value={filters.dateFrom}
                    onChange={(e) => setFilters((prev) => ({ ...prev, dateFrom: e.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    sx={{ minWidth: '9.375rem', '& .MuiInputBase-root': { height: '2.5rem' } }}
                  />
                </Grid>

                <Grid item xs={12} sm={6} md={2} lg={2}>
                  <TextField
                    fullWidth
                    type="date"
                    label="إلى إدخال المطالبة"
                    value={filters.dateTo}
                    onChange={(e) => setFilters((prev) => ({ ...prev, dateTo: e.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    sx={{ minWidth: '9.375rem', '& .MuiInputBase-root': { height: '2.5rem' } }}
                  />
                </Grid>

                <Grid item xs={12} md={2} lg={2}>
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button variant="contained" startIcon={<SearchIcon />} onClick={applyFilters} sx={{ height: '2.5rem', minHeight: '2.5rem' }}>
                      بحث
                    </Button>
                    <Tooltip title="مسح الفلاتر">
                      <IconButton color="default" onClick={clearFilters} sx={{ height: '2.5rem', width: '2.5rem', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <ClearIcon />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </Grid>

                <Grid item xs={12} md={3} lg={2}>
                  <Stack direction="row" spacing={1} justifyContent={{ xs: 'flex-start', md: 'flex-start' }} sx={{ direction: 'ltr' }}>
                    <Tooltip title="تحديث">
                      <IconButton
                        onClick={refetch}
                        color="primary"
                        disabled={isLoading}
                        sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
                      >
                        <RefreshIcon />
                      </IconButton>
                    </Tooltip>

                    <Button
                      variant="outlined"
                      color="primary"
                      startIcon={<PrintIcon />}
                      onClick={handlePrint}
                      sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1 }}
                    >
                      طباعة
                    </Button>

                    <Button
                      variant="outlined"
                      color="success"
                      startIcon={<FileDownloadIcon />}
                      onClick={handleExport}
                      disabled={!claims.length}
                      sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1 }}
                    >
                      تصدير
                    </Button>
                  </Stack>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </MainCard>

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


