import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';

// MUI
import { Box, Stack, Typography, Alert, Button, Chip, TextField, MenuItem, Grid, Tooltip, IconButton } from '@mui/material';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import RefreshIcon from '@mui/icons-material/Refresh';
import PrintIcon from '@mui/icons-material/Print';
import FileDownloadIcon from '@mui/icons-material/FileDownload';

// Project Components
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import GenericDataTable from 'components/GenericDataTable';
import { AccountBalanceWallet as WalletIcon, Payments as PaymentsIcon, TrendingDown as DownIcon, ReceiptLong as ReceiptIcon } from '@mui/icons-material';
import useTableState from 'hooks/useTableState';

// Services
import { providerAccountsService } from 'services/api/settlement.service';

// Utils
import { exportAccountsListToExcel } from 'utils/settlementExcelExport';
import { formatCurrency as formatCurrencyGlobal } from 'utils/currency-formatter';

const formatCurrency = (value) => {
  if (value === null || value === undefined || isNaN(value)) return formatCurrencyGlobal(0);
  return formatCurrencyGlobal(value);
};

const getBalanceColor = (balance) => {
  if (balance > 0) return 'error.main';
  if (balance < 0) return 'success.main';
  return 'text.primary';
};

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'الكل' },
  { value: 'ACTIVE', label: 'نشط' },
  { value: 'SUSPENDED', label: 'موقوف' },
  { value: 'CLOSED', label: 'مغلق' }
];

const STATUS_LABELS = {
  ACTIVE: 'نشط',
  SUSPENDED: 'موقوف',
  CLOSED: 'مغلق'
};

export default function ProviderPaymentsList() {
  const navigate = useNavigate();
  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: { field: 'providerName', direction: 'asc' }
  });

  const [filters, setFilters] = useState({
    search: '',
    status: 'ALL',
    hasBalance: false,
    providerType: 'ALL',
    dateFrom: '',
    dateTo: ''
  });

  const [appliedFilters, setAppliedFilters] = useState({
    search: '',
    status: 'ALL',
    hasBalance: false,
    providerType: 'ALL',
    dateFrom: '',
    dateTo: ''
  });

  const { data: accountsRaw, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-accounts-list', appliedFilters.status, appliedFilters.hasBalance],
    queryFn: () =>
      providerAccountsService.getAll({
        status: appliedFilters.status !== 'ALL' ? appliedFilters.status : undefined,
        hasBalance: appliedFilters.hasBalance
      }),
    staleTime: 1000 * 60 * 5
  });

  const accountsData = useMemo(() => {
    if (!accountsRaw) return [];
    const list = Array.isArray(accountsRaw) ? accountsRaw : accountsRaw.content || accountsRaw.items || [];

    const search = (appliedFilters.search || '').trim().toLowerCase();
    const fromDate = appliedFilters.dateFrom ? dayjs(appliedFilters.dateFrom).startOf('day') : null;
    const toDate = appliedFilters.dateTo ? dayjs(appliedFilters.dateTo).endOf('day') : null;

    return list.filter((row) => {
      const providerName = String(row.providerName || '').toLowerCase();
      const providerCode = String(row.providerCode || '').toLowerCase();
      const providerId = String(row.providerId || '');

      const typeMatches =
        appliedFilters.providerType === 'ALL' ||
        String(row.providerType || '').toUpperCase() === appliedFilters.providerType;

      const searchMatches =
        !search || providerName.includes(search) || providerCode.includes(search) || providerId.includes(search);

      const lastActivity = dayjs(row.updatedAt || row.createdAt || null);
      const dateMatches =
        !lastActivity.isValid() ||
        ((!fromDate || !lastActivity.isBefore(fromDate)) && (!toDate || !lastActivity.isAfter(toDate)));

      return typeMatches && searchMatches && dateMatches;
    });
  }, [accountsRaw, appliedFilters]);

  const withComputed = useMemo(() => {
    return accountsData.map((row) => {
      const approved = Number(row.totalApproved) || 0;
      const paid = Number(row.totalPaid) || 0;
      const gap = Math.max(approved - paid, 0);
      const coveragePercent = approved > 0 ? (paid / approved) * 100 : 0;
      return {
        ...row,
        gapAmount: gap,
        coveragePercent,
        lastActivityAt: row.updatedAt || row.createdAt || null
      };
    });
  }, [accountsData]);

  const totals = useMemo(
    () => ({
      approved: withComputed.reduce((acc, curr) => acc + (Number(curr.totalApproved) || 0), 0),
      outstanding: withComputed.reduce((acc, curr) => acc + (Number(curr.runningBalance) || 0), 0),
      paid: withComputed.reduce((acc, curr) => acc + (Number(curr.totalPaid) || 0), 0),
      gap: withComputed.reduce((acc, curr) => acc + (Number(curr.gapAmount) || 0), 0),
      coveragePercent:
        withComputed.reduce((acc, curr) => acc + (Number(curr.totalApproved) || 0), 0) > 0
          ? (withComputed.reduce((acc, curr) => acc + (Number(curr.totalPaid) || 0), 0) /
              withComputed.reduce((acc, curr) => acc + (Number(curr.totalApproved) || 0), 0)) *
            100
          : 0
    }),
    [withComputed]
  );

  const sortedRows = useMemo(() => {
    const sorting = tableState.sorting?.[0];
    if (!sorting?.id) return withComputed;

    const dir = sorting.desc ? -1 : 1;
    const field = sorting.id;

    const valueOf = (row) => {
      if (field === 'lastActivityAt') return dayjs(row.lastActivityAt).valueOf() || 0;
      if (field === 'providerName') return String(row.providerName || '').toLowerCase();
      return row[field] ?? 0;
    };

    return [...withComputed].sort((a, b) => {
      const av = valueOf(a);
      const bv = valueOf(b);
      if (av < bv) return -1 * dir;
      if (av > bv) return 1 * dir;
      return 0;
    });
  }, [withComputed, tableState.sorting]);

  const paginatedRows = useMemo(() => {
    const start = tableState.page * tableState.pageSize;
    const end = start + tableState.pageSize;
    return sortedRows.slice(start, end);
  }, [sortedRows, tableState.page, tableState.pageSize]);

  const tableRows = useMemo(() => paginatedRows, [paginatedRows]);

  const applyFilterNow = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setAppliedFilters((prev) => ({ ...prev, [key]: value }));
    tableState.setPage(0);
  };

  const handleApplyFilters = () => {
    tableState.setPage(0);
    setAppliedFilters((prev) => ({ ...prev, dateFrom: filters.dateFrom, dateTo: filters.dateTo }));
  };

  const handleClearFilters = () => {
    const reset = { search: '', status: 'ALL', hasBalance: false, providerType: 'ALL', dateFrom: '', dateTo: '' };
    setFilters(reset);
    setAppliedFilters(reset);
    tableState.setPage(0);
  };

  const handleViewTransactions = (row) => {
    const providerId = row.providerId || row.id;
    if (providerId) {
      navigate(`/settlement/provider-payments/${providerId}`);
    }
  };

  const handleExportExcel = () => {
    exportAccountsListToExcel({ accounts: withComputed });
  };

  const handlePrint = () => {
    window.print();
  };

  const renderSummaryCard = (title, value, icon, borderColor = 'primary.main') => (
    <Box
      sx={{
        minWidth: '10.0rem',
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

  const providerTypeOptions = useMemo(() => {
    const types = [...new Set((accountsData || []).map((r) => String(r.providerType || '').toUpperCase()).filter(Boolean))];
    return ['ALL', ...types];
  }, [accountsData]);

  const columns = useMemo(
    () => [
      {
        id: 'providerName',
        label: 'مقدم الخدمة',
        minWidth: '11.25rem',
        renderCell: ({ row }) => (
          row.isTotalsRow ? (
            <Typography variant="body2" fontWeight={800} color="primary.main">الإجمالي</Typography>
          ) : (
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <Box sx={{ width: '0.375rem', height: '0.375rem', borderRadius: '50%', bgcolor: 'primary.main' }} />
            <Typography variant="body2" fontWeight={600}>
              {row.providerName || `مقدم خدمة #${row.providerId || row.id}`}
            </Typography>
          </Stack>
          )
        )
      },
      {
        id: 'providerType',
        label: 'النوع',
        minWidth: '5.625rem',
        renderCell: ({ row }) => row.providerType || 'غير متوفر'
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '5.625rem',
        align: 'center',
        renderCell: ({ row }) => (row.isTotalsRow ? '-' : STATUS_LABELS[row.status] || row.status || '-')
      },
      {
        id: 'runningBalance',
        label: 'الرصيد الحالي',
        minWidth: '8.125rem',
        align: 'right',
        renderCell: ({ row }) => {
          const balance = Number(row.runningBalance) || 0;
          return (
            <Typography variant="subtitle2" fontWeight={700} sx={{ color: getBalanceColor(balance) }}>
              {formatCurrency(balance)}
            </Typography>
          );
        }
      },
      {
        id: 'totalApproved',
        label: 'إجمالي المعتمد',
        minWidth: '7.5rem',
        align: 'right',
        renderCell: ({ row }) => formatCurrency(row.totalApproved)
      },
      {
        id: 'totalPaid',
        label: 'إجمالي المدفوع',
        minWidth: '7.5rem',
        align: 'right',
        renderCell: ({ row }) => formatCurrency(row.totalPaid)
      },
      {
        id: 'gapAmount',
        label: 'فجوة السداد',
        minWidth: '6.875rem',
        align: 'right',
        renderCell: ({ row }) => {
          const gap = Number(row.gapAmount || 0);
          return <Typography color={gap > 0 ? 'error.main' : 'success.main'} fontWeight={700}>{formatCurrency(gap)}</Typography>;
        }
      },
      {
        id: 'coveragePercent',
        label: 'نسبة السداد',
        minWidth: '5.9375rem',
        align: 'center',
        renderCell: ({ row }) => {
          const value = Number(row.coveragePercent || 0);
          return <Chip size="small" color={value >= 100 ? 'success' : value >= 50 ? 'warning' : 'error'} label={`${value.toFixed(1)}%`} />;
        }
      },
      {
        id: 'lastActivityAt',
        label: 'آخر حركة',
        minWidth: '6.875rem',
        align: 'center',
        renderCell: ({ row }) => {
          if (row.isTotalsRow) return '-';
          const d = dayjs(row.lastActivityAt);
          return d.isValid() ? d.format('YYYY/MM/DD') : '-';
        }
      },
      {
        id: 'pendingClaimsCount',
        label: 'مطالبات معلقة',
        minWidth: '6.25rem',
        align: 'center',
        renderCell: ({ row }) => row.pendingClaimsCount || 0
      },
      {
        id: 'actions',
        label: 'إجراءات',
        minWidth: '6.5625rem',
        align: 'center',
        sortable: false,
        renderCell: ({ row }) => (
          row.isTotalsRow ? '-' : (
          <Button
            size="small"
            variant="contained"
            color="primary"
            startIcon={<AccountBalanceWalletIcon />}
            onClick={() => handleViewTransactions(row)}
            sx={{ borderRadius: '0.25rem' }}
          >
            الدفعات
          </Button>
          )
        )
      }
    ],
    [navigate]
  );

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 1 }}>
      <ModernPageHeader
        title="الدفعات المالية لمقدمي الخدمة"
        subtitle="متابعة الأرصدة وتسجيل الدفعات المالية للمحاسبين"
        icon={<WalletIcon />}
        breadcrumbs={[
          { label: 'الرئيسية', href: '/' },
          { label: 'التسويات المالية', href: '/settlement' },
          { label: 'الدفعات المالية' }
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
            {renderSummaryCard('إجمالي المرافق', String(withComputed.length), <ReceiptIcon fontSize="small" color="primary" />, 'primary.main')}
            {renderSummaryCard('إجمالي المعتمد', formatCurrency(totals.approved), <PaymentsIcon fontSize="small" color="primary" />, 'primary.main')}
            {renderSummaryCard('إجمالي المدفوع', formatCurrency(totals.paid), <PaymentsIcon fontSize="small" color="success" />, 'success.main')}
            {renderSummaryCard('إجمالي المستحق', formatCurrency(totals.outstanding), <DownIcon fontSize="small" color="error" />, 'error.main')}
            {renderSummaryCard('فجوة السداد', formatCurrency(totals.gap), <DownIcon fontSize="small" color="warning" />, 'warning.main')}
            {renderSummaryCard('نسبة السداد', `${totals.coveragePercent.toFixed(1)}%`, <PaymentsIcon fontSize="small" color="info" />, 'info.main')}
          </Box>
        }
      />

      <MainCard sx={{ mt: -1.25 }}>
        <Grid container spacing={1.5} alignItems="center">
          <Grid item xs={12} sm={6} md={3} lg={2.5}>
            <TextField
              fullWidth
              label="بحث باسم مقدم الخدمة أو الرقم"
              value={filters.search}
              onChange={(e) => applyFilterNow('search', e.target.value)}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={2} lg={1.8}>
            <TextField
              select
              fullWidth
              label="حالة الحساب"
              value={filters.status}
              onChange={(e) => applyFilterNow('status', e.target.value)}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            >
              {STATUS_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6} md={2} lg={1.6}>
            <TextField
              select
              fullWidth
              label="الرصيد"
              value={filters.hasBalance ? 'HAS_BALANCE' : 'ALL'}
              onChange={(e) => applyFilterNow('hasBalance', e.target.value === 'HAS_BALANCE')}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            >
              <MenuItem value="ALL">الكل</MenuItem>
              <MenuItem value="HAS_BALANCE">فقط بحسابات مستحقة</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6} md={3} lg={2}>
            <TextField
              select
              fullWidth
              label="نوع مقدم الخدمة"
              value={filters.providerType}
              onChange={(e) => applyFilterNow('providerType', e.target.value)}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            >
              {providerTypeOptions.map((type) => (
                <MenuItem key={type} value={type}>
                  {type === 'ALL' ? 'الكل' : type}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6} md={2} lg={1.6}>
            <TextField
              fullWidth
              type="date"
              label="من تاريخ آخر حركة"
              value={filters.dateFrom}
              onChange={(e) => setFilters((prev) => ({ ...prev, dateFrom: e.target.value }))}
              InputLabelProps={{ shrink: true }}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={2} lg={1.6}>
            <TextField
              fullWidth
              type="date"
              label="إلى تاريخ آخر حركة"
              value={filters.dateTo}
              onChange={(e) => setFilters((prev) => ({ ...prev, dateTo: e.target.value }))}
              InputLabelProps={{ shrink: true }}
              sx={{ '& .MuiInputBase-root': { height: '2.5rem' } }}
            />
          </Grid>
          <Grid item xs={12} md={2} lg={1.8}>
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button variant="contained" startIcon={<SearchIcon />} onClick={handleApplyFilters} sx={{ height: '2.5rem', minHeight: '2.5rem' }}>
                بحث
              </Button>
              <Tooltip title="مسح الفلاتر">
                <IconButton color="default" onClick={handleClearFilters} sx={{ height: '2.5rem', width: '2.5rem', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                  <ClearIcon />
                </IconButton>
              </Tooltip>
            </Stack>
          </Grid>

          <Grid item xs={12} md={3} lg={1.7}>
            <Stack direction="row" spacing={1} justifyContent={{ xs: 'flex-start', md: 'flex-start' }} sx={{ direction: 'ltr' }}>
              <Tooltip title="تحديث">
                <IconButton onClick={refetch} color="primary" disabled={isLoading} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}>
                  <RefreshIcon />
                </IconButton>
              </Tooltip>

              <Button variant="outlined" color="primary" startIcon={<PrintIcon />} onClick={handlePrint} sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1 }}>
                طباعة
              </Button>

              <Button
                variant="outlined"
                color="success"
                startIcon={<FileDownloadIcon />}
                onClick={handleExportExcel}
                disabled={!withComputed.length}
                sx={{ height: '2.5rem', minHeight: '2.5rem', whiteSpace: 'nowrap', px: '0.75rem', borderRadius: 1 }}
              >
                تصدير
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </MainCard>

      <MainCard content={false} sx={{ flexGrow: 1, overflow: 'hidden' }}>
        <Box sx={{ p: 0, height: '100%' }}>
          {isError && (
            <Box sx={{ p: '1.0rem' }}>
              <Alert severity="error" sx={{ borderRadius: '0.25rem' }}>
                {error?.message || 'فشل جلب بيانات الدفعات'}
              </Alert>
            </Box>
          )}

          <GenericDataTable
            columns={columns.map((c) => ({
              accessorKey: c.id,
              header: c.label,
              minWidth: c.minWidth,
              align: c.align,
              enableSorting: c.sortable !== false,
              cell: ({ row }) => c.renderCell?.({ row: row.original }) ?? row.original?.[c.id] ?? '-'
            }))}
            data={tableRows}
            totalCount={withComputed.length}
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
            emptyMessage="لا توجد حسابات تطابق الفلاتر الحالية"
            rowsPerPageOptions={[10, 25, 50, 100]}
          />
        </Box>
      </MainCard>
    </Box>
  );
}


