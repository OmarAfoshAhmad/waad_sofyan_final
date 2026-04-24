import { useState, useEffect, useRef, useMemo } from 'react';
import { useReactToPrint } from 'react-to-print';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Typography,
  Grid,
  Tabs,
  Tab,
  TextField,
  MenuItem,
  Stack,
  Chip,
  Alert,
  IconButton,
  Tooltip,
  InputAdornment,
  Skeleton,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  TablePagination,
  TableSortLabel,
  Paper
} from '@mui/material';
import {
  AttachMoney as MoneyIcon,
  AccountBalance as BalanceIcon,
  TrendingUp as TrendingIcon,
  Receipt as ReceiptIcon,
  Payment as PaymentIcon,
  TableChart as ExcelIcon,
  Refresh as RefreshIcon,
  Print as PrintIcon,
  Preview as PreviewIcon,
  LocalHospital as ProviderIcon,
  FilterList as FilterIcon,
  ClearAll as ClearAllIcon,
  ErrorOutline as ErrorOutlineIcon,
  ArrowBack as ArrowBackIcon
} from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { claimsService, providersService } from 'services/api';
import { exportToExcel } from 'utils/exportUtils';
import { useAuth } from 'contexts/AuthContext';

/**
 * Financial Reports - التقارير المالية
 *
 * تقارير مالية شاملة للمطالبات والتسويات والمدفوعات
 *
 * Tabs:
 * 1. Financial Summary - الملخص المالي
 * 2. Invoices Report - تقرير الفواتير
 * 3. Payments Report - تقرير المدفوعات
 * 4. Settlements Report - تقرير التسويات
 *
 * Permissions: ACCOUNTANT, MEDICAL_REVIEWER, SUPER_ADMIN (NOT PROVIDER)
 */
const FinancialReports = () => {
  const { companyName, primaryColor } = useCompanySettings();
  const { user } = useAuth();
  const printRef = useRef(null);

  // State
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState(0);
  const [error, setError] = useState(null);
  const [viewMode, setViewMode] = useState('TABLE'); // TABLE, PRINT_PREVIEW

  // Filters
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [status, setStatus] = useState('ALL');

  const effectiveEmployerId = undefined;

  // Pagination & Sorting
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [orderBy, setOrderBy] = useState('');
  const [order, setOrder] = useState('asc');

  // Data
  const [claims, setClaims] = useState([]);
  const [totalRows, setTotalRows] = useState(0);

  // Financial Summary KPIs
  const [summaryData, setSummaryData] = useState({
    totalClaimsAmount: 0,
    totalApprovedAmount: 0,
    totalPaidAmount: 0,
    outstandingAmount: 0,
    claimsCount: 0,
    approvedCount: 0,
    settledCount: 0
  });

  const STATUS_OPTIONS = [
    { value: 'ALL', label: 'جميع الحالات' },
    { value: 'SUBMITTED', label: 'قيد الانتظار' },
    { value: 'UNDER_REVIEW', label: 'قيد المراجعة' },
    { value: 'NEEDS_CORRECTION', label: 'يحتاج تصحيح' },
    { value: 'APPROVED', label: 'معتمد' },
    { value: 'BATCHED', label: 'في دفعة تسوية' },
    { value: 'SETTLED', label: 'مسدد' },
    { value: 'REJECTED', label: 'مرفوض' }
  ];

  const getStatusLabel = (statusValue) => STATUS_OPTIONS.find((option) => option.value === statusValue)?.label || statusValue;

  // Fetch providers list
  const { data: providersData } = useQuery({
    queryKey: ['providers-selector'],
    queryFn: async () => {
      const response = await providersService.getSelector();
      return response?.data || response || [];
    },
    staleTime: 5 * 60 * 1000
  });

  const providers = useMemo(() => providersData || [], [providersData]);

  // Check if user role allows provider filter
  const canFilterByProvider = useMemo(() => {
    const allowedRoles = ['ACCOUNTANT', 'MEDICAL_REVIEWER', 'SUPER_ADMIN'];
    return allowedRoles.includes(user?.role);
  }, [user?.role]);

  // Print handler
  const handlePrintTable = useReactToPrint({
    contentRef: printRef,
    documentTitle: `التقارير_المالية_${new Date().toISOString().split('T')[0]}`,
    pageStyle: `
      @page {
        size: A4 landscape;
        margin: 15mm;
      }
      @media print {
        body { -webkit-print-color-adjust: exact; }
        .no-print { display: none !important; }
        table { page-break-inside: auto; }
        tr { page-break-inside: avoid; }
        thead { display: table-header-group; }
      }
    `
  });

  // Show print preview
  const handleShowPrintPreview = () => {
    setViewMode('PRINT_PREVIEW');
  };

  // Back to table
  const handleBackToTable = () => {
    setViewMode('TABLE');
  };

  // Sorting handler
  const handleRequestSort = (property) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  // Pagination handlers
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  // Fetch data based on active tab
  useEffect(() => {
    fetchData();
  }, [activeTab, effectiveEmployerId, selectedProviderId, page, rowsPerPage, dateFrom, dateTo, status]);

  // Reset pagination when filters change
  useEffect(() => {
    setPage(0);
  }, [effectiveEmployerId, selectedProviderId, status, dateFrom, dateTo]);

  // Auto-refresh when page becomes visible (user returns from another tab/page)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchData();
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  });

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);

      if (activeTab === 0) {
        // Financial Summary
        await fetchFinancialSummary();
      } else if (activeTab === 1) {
        // Invoices Report
        await fetchInvoicesReport();
      } else if (activeTab === 2) {
        // Payments Report
        await fetchPaymentsReport();
      } else if (activeTab === 3) {
        // Settlements Report
        await fetchSettlementsReport();
      }
    } catch (err) {
      console.error('Failed to fetch financial data:', err);
      setError('فشل تحميل البيانات المالية. يرجى المحاولة مرة أخرى.');
    } finally {
      setLoading(false);
    }
  };

  const extractPagedItems = (response) => {
    return response?.items || response?.data?.items || response?.content || response?.data?.content || [];
  };

  const extractPagedTotal = (response, items = []) => {
    if (typeof response?.total === 'number') return response.total;
    if (typeof response?.data?.total === 'number') return response.data.total;
    if (typeof response?.totalElements === 'number') return response.totalElements;
    if (typeof response?.data?.totalElements === 'number') return response.data.totalElements;
    return Array.isArray(items) ? items.length : 0;
  };

  /**
   * ╔═══════════════════════════════════════════════════════════════════════════╗
   * ║              FINANCIAL SUMMARY - SINGLE SOURCE OF TRUTH                  ║
   * ║───────────────────────────────────────────────────────────────────────────║
   * ║ ALL totals come from backend database SUM() queries.                     ║
   * ║ Frontend is FORBIDDEN from calculating totals using .reduce()            ║
   * ╚═══════════════════════════════════════════════════════════════════════════╝
   */
  const fetchFinancialSummary = async () => {
    // Fetch KPIs server-side for accuracy and performance
    const summaryResponse = await claimsService.getFinancialSummary({
      employerId: effectiveEmployerId,
      providerId: selectedProviderId || undefined,
      status: status !== 'ALL' ? status : undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined
    });

    if (summaryResponse) {
      setSummaryData(summaryResponse);
    }

    // Fetch a sample of claims for the preview table
    const response = await claimsService.list({
      employerId: effectiveEmployerId,
      providerId: selectedProviderId || undefined,
      status: status !== 'ALL' ? status : undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      page: 1,
      size: 10
    });

    const claimItems = extractPagedItems(response);
    setClaims(claimItems);
    setTotalRows(extractPagedTotal(response, claimItems));
  };

  const fetchInvoicesReport = async () => {
    // ══════════════════════════════════════════════════════════════════════════
    // INVOICES REPORT - Fetch settled claims from backend (NO LOCAL FILTERING)
    // ══════════════════════════════════════════════════════════════════════════
    const response = await claimsService.getSettledClaims({
      employerId: effectiveEmployerId,
      providerId: selectedProviderId || undefined,
      status: 'SETTLED', // Invoices are for settled claims
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      page: page + 1,
      size: rowsPerPage,
      sortBy: 'settledAt',
      sortDir: 'desc'
    });

    const settledClaims = extractPagedItems(response);

    // Transform to invoice format
    const invoices = settledClaims.map((claim) => ({
      id: claim.id,
      invoiceNo: `INV-${new Date(claim.settledAt || Date.now()).getFullYear()}-${String(claim.id).padStart(6, '0')}`,
      employerName: claim.insuranceCompanyName || '-',
      providerName: claim.providerName || '-',
      claimNumber: claim.id,
      amount: claim.netProviderAmount || claim.approvedAmount || 0,
      settledAt: claim.settledAt,
      paymentReference: claim.paymentReference || '-',
      status: 'PAID'
    }));

    setClaims(invoices);
    setTotalRows(extractPagedTotal(response, settledClaims));
  };

  const fetchPaymentsReport = async () => {
    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENTS REPORT - Fetch settled claims from backend (NO LOCAL FILTERING)
    // ══════════════════════════════════════════════════════════════════════════
    const response = await claimsService.getSettledClaims({
      employerId: effectiveEmployerId,
      providerId: selectedProviderId || undefined,
      status: 'SETTLED',
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      page: page + 1,
      size: rowsPerPage,
      sortBy: 'settledAt',
      sortDir: 'desc'
    });

    const settledClaims = extractPagedItems(response);
    const payments = settledClaims
      .filter((c) => c.paymentReference)
      .map((claim) => ({
        id: claim.id,
        paymentRef: claim.paymentReference,
        employerName: claim.insuranceCompanyName || '-',
        providerName: claim.providerName || '-',
        memberName: claim.memberFullName || '-',
        amount: claim.netProviderAmount || claim.approvedAmount || 0,
        paymentDate: claim.settledAt,
        method: 'تحويل بنكي', // Default
        status: 'مكتمل'
      }));

    setClaims(payments);
    setTotalRows(extractPagedTotal(response, settledClaims));
  };

  const fetchSettlementsReport = async () => {
    // ══════════════════════════════════════════════════════════════════════════
    // SETTLEMENTS REPORT - Fetch settled claims from backend (NO LOCAL FILTERING)
    // ══════════════════════════════════════════════════════════════════════════
    const response = await claimsService.getSettledClaims({
      employerId: effectiveEmployerId,
      providerId: selectedProviderId || undefined,
      status: 'SETTLED',
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      page: page + 1,
      size: rowsPerPage,
      sortBy: 'settledAt',
      sortDir: 'desc'
    });

    const settledClaims = extractPagedItems(response);
    const settlements = settledClaims.map((claim) => ({
      id: claim.id,
      claimNumber: claim.id,
      memberName: claim.memberFullName || '-',
      employerName: claim.insuranceCompanyName || '-',
      providerName: claim.providerName || '-',
      approvedAmount: claim.approvedAmount || 0,
      settledAmount: claim.netProviderAmount || claim.approvedAmount || 0,
      settlementDate: claim.settledAt,
      paymentReference: claim.paymentReference || '-',
      status: 'مسدد'
    }));

    setClaims(settlements);
    setTotalRows(extractPagedTotal(response, settledClaims));
  };

  // Export handlers - using company branding from SSOT
  const handleExportExcel = () => {
    const tabNames = ['Financial_Summary', 'Invoices', 'Payments', 'Settlements'];
    const fileName = `${tabNames[activeTab]}_Report_${new Date().toISOString().split('T')[0]}`;
    exportToExcel(claims, fileName, {
      companyName,
      reportTitle: 'تقرير مالي',
      columnLabels: {
        id: '#',
        claimNumber: 'رقم المطالبة',
        memberName: 'اسم المؤمن عليه',
        employerName: 'الشريك',
        providerName: 'مقدم الخدمة',
        requestedAmount: 'المبلغ المطلوب',
        approvedAmount: 'المبلغ المعتمد',
        settledAmount: 'المبلغ المسدد',
        invoiceDate: 'تاريخ الفاتورة',
        paymentDate: 'تاريخ الدفع',
        settlementDate: 'تاريخ التسوية',
        paymentReference: 'مرجع الدفع',
        status: 'الحالة'
      }
    });
  };

  // Tab change handler
  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
    setPage(0);
  };

  // Reset ALL filters
  const handleResetFilters = () => {
    setSelectedProviderId('');
    setDateFrom('');
    setDateTo('');
    setStatus('ALL');
    setPage(0);
  };

  // Get active filters summary
  const getActiveFiltersSummary = () => {
    const filters = [];
    if (selectedProviderId) {
      const provider = providers.find((p) => String(p.id) === String(selectedProviderId));
      filters.push(`مقدم الخدمة: ${provider?.name || provider?.label || selectedProviderId}`);
    }
    if (dateFrom) filters.push(`من: ${dateFrom}`);
    if (dateTo) filters.push(`إلى: ${dateTo}`);
    if (status !== 'ALL') filters.push(`الحالة: ${getStatusLabel(status)}`);
    return filters;
  };

  const activeFilters = getActiveFiltersSummary();

  // ============================================================================
  // COLUMNS DEFINITIONS (Enhanced with proper formatting)
  // ============================================================================

  const formatAmount = (value) => {
    return formatCurrency(value || 0);
  };

  const formatDate = (value) => {
    if (!value) return '-';
    return new Date(value).toLocaleDateString('en-GB');
  };

  const summaryColumns = [
    { field: 'id', headerName: 'رقم المطالبة', width: '7.5rem', sortable: true },
    { field: 'memberFullName', headerName: 'المنتفع', minWidth: '11.25rem', sortable: true },
    { field: 'providerName', headerName: 'مقدم الخدمة', minWidth: '9.375rem', sortable: true },
    {
      field: 'requestedAmount',
      headerName: 'المبلغ المطلوب',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount
    },
    {
      field: 'approvedAmount',
      headerName: 'المبلغ المعتمد',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '7.5rem',
      sortable: true,
      renderCell: (value) => (
        <Chip
          label={value === 'SETTLED' ? 'مسدد' : value === 'APPROVED' ? 'معتمد' : value}
          size="small"
          color={value === 'SETTLED' ? 'success' : value === 'APPROVED' ? 'primary' : 'default'}
        />
      )
    }
  ];

  const invoicesColumns = [
    { field: 'invoiceNo', headerName: 'رقم الفاتورة', width: '9.375rem', sortable: true },
    { field: 'employerName', headerName: 'الشركة', minWidth: '9.375rem', sortable: true },
    { field: 'providerName', headerName: 'مقدم الخدمة', minWidth: '9.375rem', sortable: true },
    { field: 'claimNumber', headerName: 'رقم المطالبة', width: '7.5rem', sortable: true },
    {
      field: 'amount',
      headerName: 'المبلغ',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount,
      highlight: true
    },
    {
      field: 'settledAt',
      headerName: 'تاريخ التسوية',
      width: '8.125rem',
      sortable: true,
      format: formatDate
    },
    { field: 'paymentReference', headerName: 'مرجع الدفع', width: '8.75rem', sortable: true },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '6.25rem',
      renderCell: () => <Chip label="مدفوع" size="small" color="success" />
    }
  ];

  const paymentsColumns = [
    { field: 'paymentRef', headerName: 'مرجع الدفع', width: '9.375rem', sortable: true },
    { field: 'employerName', headerName: 'الشركة', minWidth: '9.375rem', sortable: true },
    { field: 'providerName', headerName: 'مقدم الخدمة', minWidth: '9.375rem', sortable: true },
    { field: 'memberName', headerName: 'المنتفع', minWidth: '9.375rem', sortable: true },
    {
      field: 'amount',
      headerName: 'المبلغ',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount,
      highlight: true
    },
    {
      field: 'paymentDate',
      headerName: 'تاريخ الدفع',
      width: '8.125rem',
      sortable: true,
      format: formatDate
    },
    { field: 'method', headerName: 'طريقة الدفع', width: '7.5rem' },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '6.25rem',
      renderCell: () => <Chip label="مكتمل" size="small" color="success" />
    }
  ];

  const settlementsColumns = [
    { field: 'claimNumber', headerName: 'رقم المطالبة', width: '8.125rem', sortable: true },
    { field: 'memberName', headerName: 'المنتفع', minWidth: '9.375rem', sortable: true },
    { field: 'employerName', headerName: 'الشركة', minWidth: '9.375rem', sortable: true },
    { field: 'providerName', headerName: 'مقدم الخدمة', minWidth: '9.375rem', sortable: true },
    {
      field: 'approvedAmount',
      headerName: 'المبلغ المعتمد',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount
    },
    {
      field: 'settledAmount',
      headerName: 'المبلغ المسدد',
      width: '8.75rem',
      align: 'left',
      sortable: true,
      format: formatAmount,
      highlight: true
    },
    {
      field: 'settlementDate',
      headerName: 'تاريخ التسوية',
      width: '8.125rem',
      sortable: true,
      format: formatDate
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '6.25rem',
      renderCell: () => <Chip label="مسدد" size="small" color="success" />
    }
  ];

  const getColumns = () => {
    switch (activeTab) {
      case 0:
        return summaryColumns;
      case 1:
        return invoicesColumns;
      case 2:
        return paymentsColumns;
      case 3:
        return settlementsColumns;
      default:
        return summaryColumns;
    }
  };

  const columns = getColumns();

  // ============================================================================
  // RENDER
  // ============================================================================

  // Tab names for display
  const tabNames = ['الملخص المالي', 'تقرير الفواتير', 'تقرير المدفوعات', 'تقرير التسويات'];

  return (
    <>
      <Box>
        <ModernPageHeader title="التقارير المالية" subtitle="تقارير شاملة للمطالبات والتسويات والمدفوعات" icon={<MoneyIcon />} />

        {/* Enhanced Filters Card */}
        <MainCard sx={{ mb: '1.5rem' }}>
          <Stack spacing={2}>
            {/* Filters Row 1: Provider (Primary) + Status */}
            <Grid container spacing={2} alignItems="center">
              {canFilterByProvider && (
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    select
                    fullWidth
                    label="مقدم الخدمة"
                    value={selectedProviderId}
                    onChange={(e) => setSelectedProviderId(e.target.value)}
                    size="small"
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <ProviderIcon color="action" fontSize="small" />
                        </InputAdornment>
                      )
                    }}
                  >
                    <MenuItem value="">جميع مقدمي الخدمة</MenuItem>
                    {providers.map((provider) => (
                      <MenuItem key={provider.id} value={provider.id}>
                        {provider.name || provider.label}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
              )}

              <Grid size={{ xs: 12, md: 6 }}>
                <TextField select fullWidth label="الحالة" value={status} onChange={(e) => setStatus(e.target.value)} size="small">
                  {STATUS_OPTIONS.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
            </Grid>

            {/* Filters Row 2: Dates + Actions */}
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  fullWidth
                  label="من تاريخ"
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  size="small"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  fullWidth
                  label="إلى تاريخ"
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  size="small"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button variant="outlined" color="inherit" size="small" onClick={handleResetFilters} startIcon={<ClearAllIcon />}>
                    مسح الفلاتر
                  </Button>
                  <Button variant="contained" size="small" onClick={fetchData} startIcon={<RefreshIcon />}>
                    تحديث
                  </Button>
                </Stack>
              </Grid>
            </Grid>

            {/* Active Filters Summary */}
            {activeFilters.length > 0 && (
              <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
                <FilterIcon fontSize="small" color="primary" />
                <Typography variant="body2" color="text.secondary">
                  الفلاتر النشطة:
                </Typography>
                {activeFilters.map((filter, idx) => (
                  <Chip key={idx} label={filter} size="small" variant="outlined" color="primary" />
                ))}
              </Stack>
            )}
          </Stack>
        </MainCard>

        {/* Error Alert */}
        {error && (
          <Alert severity="error" sx={{ mb: '1.0rem' }} icon={<ErrorOutlineIcon />}>
            {error}
          </Alert>
        )}

        {/* Main Content */}
        {viewMode === 'TABLE' ? (
          <MainCard>
            {/* Tabs */}
            <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: '1.5rem' }}>
              <Tabs value={activeTab} onChange={handleTabChange}>
                <Tab label="الملخص المالي" icon={<TrendingIcon />} iconPosition="start" />
                <Tab label="تقرير الفواتير" icon={<ReceiptIcon />} iconPosition="start" />
                <Tab label="تقرير المدفوعات" icon={<PaymentIcon />} iconPosition="start" />
                <Tab label="تقرير التسويات" icon={<BalanceIcon />} iconPosition="start" />
              </Tabs>
            </Box>

            {/* Action Buttons */}
            <Stack direction="row" spacing={2} sx={{ mb: '1.5rem' }} flexWrap="wrap">
              <Button variant="outlined" startIcon={<ExcelIcon />} onClick={handleExportExcel} disabled={loading || claims.length === 0}>
                تصدير Excel
              </Button>
              <Tooltip title="معاينة الطباعة">
                <span>
                  <IconButton color="primary" onClick={handleShowPrintPreview} disabled={loading || claims.length === 0}>
                    <PreviewIcon />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="طباعة">
                <span>
                  <IconButton color="primary" onClick={handlePrintTable} disabled={loading || claims.length === 0}>
                    <PrintIcon />
                  </IconButton>
                </span>
              </Tooltip>

              <Box sx={{ flexGrow: 1 }} />

              <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center' }}>
                إجمالي السجلات: <strong>{totalRows}</strong>
                {claims.length !== totalRows && ` | معروض: ${claims.length}`}
              </Typography>
            </Stack>

            {/* Financial Summary KPIs (Tab 0) */}
            {activeTab === 0 && (
              <Grid container spacing={3} sx={{ mb: '1.5rem' }}>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Card elevation={2} sx={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        إجمالي المطالبات
                      </Typography>
                      <Typography variant="h4" fontWeight={700}>
                        {formatAmount(summaryData.totalClaimsAmount)}
                      </Typography>
                      <Typography variant="caption" sx={{ opacity: 0.9 }}>
                        {summaryData.claimsCount} مطالبة
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Card elevation={2} sx={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', color: 'white' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        إجمالي المعتمد
                      </Typography>
                      <Typography variant="h4" fontWeight={700}>
                        {formatAmount(summaryData.totalApprovedAmount)}
                      </Typography>
                      <Typography variant="caption" sx={{ opacity: 0.9 }}>
                        {summaryData.approvedCount} مطالبة معتمدة
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Card elevation={2} sx={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', color: 'white' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        إجمالي المدفوع
                      </Typography>
                      <Typography variant="h4" fontWeight={700}>
                        {formatAmount(summaryData.totalPaidAmount)}
                      </Typography>
                      <Typography variant="caption" sx={{ opacity: 0.9 }}>
                        {summaryData.settledCount} مطالبة مسددة
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Card elevation={2} sx={{ background: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)', color: 'white' }}>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        المبلغ المعلق
                      </Typography>
                      <Typography variant="h4" fontWeight={700}>
                        {formatAmount(summaryData.outstandingAmount)}
                      </Typography>
                      <Typography variant="caption" sx={{ opacity: 0.9 }}>
                        معتمد لم يُسدد بعد
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            )}

            {/* Data Table */}
            <Box sx={{ width: '100%' }}>
              {loading ? (
                <Box sx={{ p: '1.0rem' }}>
                  {[...Array(5)].map((_, i) => (
                    <Skeleton key={i} variant="rectangular" height={50} sx={{ mb: 1, borderRadius: 1 }} />
                  ))}
                </Box>
              ) : claims.length === 0 ? (
                <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" py={8} gap={2}>
                  <ReceiptIcon sx={{ fontSize: '5.0rem', color: 'text.secondary', opacity: 0.5 }} />
                  <Typography variant="h5" color="text.secondary">
                    لا توجد بيانات مالية
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    جرب تغيير الفلاتر أو الفترة الزمنية
                  </Typography>
                </Box>
              ) : (
                <>
                  <TableContainer component={Paper} ref={printRef} sx={{ maxHeight: '31.25rem' }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow sx={{ '& th': { fontWeight: 'bold', bgcolor: 'grey.100' } }}>
                          {columns.map((col) => (
                            <TableCell key={col.field} align={col.align || 'right'} sx={{ minWidth: col.minWidth, width: col.width }}>
                              {col.sortable ? (
                                <TableSortLabel
                                  active={orderBy === col.field}
                                  direction={orderBy === col.field ? order : 'asc'}
                                  onClick={() => handleRequestSort(col.field)}
                                >
                                  {col.headerName}
                                </TableSortLabel>
                              ) : (
                                col.headerName
                              )}
                            </TableCell>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {claims.map((row, idx) => (
                          <TableRow
                            key={row.id || idx}
                            hover
                            sx={{
                              '&:nth-of-type(odd)': { bgcolor: 'grey.50' },
                              '&:hover': { bgcolor: 'action.hover' }
                            }}
                          >
                            {columns.map((col) => {
                              const value = row[col.field];
                              let displayValue = value;

                              if (col.format) {
                                displayValue = col.format(value);
                              } else if (col.renderCell) {
                                displayValue = col.renderCell(value);
                              }

                              return (
                                <TableCell
                                  key={col.field}
                                  align={col.align || 'right'}
                                  sx={{
                                    fontWeight: col.highlight ? 'bold' : 'normal',
                                    color: col.highlight ? 'primary.main' : 'inherit'
                                  }}
                                >
                                  {displayValue ?? '-'}
                                </TableCell>
                              );
                            })}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>

                  <TablePagination
                    component="div"
                    count={totalRows}
                    page={page}
                    onPageChange={handleChangePage}
                    rowsPerPage={rowsPerPage}
                    onRowsPerPageChange={handleChangeRowsPerPage}
                    rowsPerPageOptions={[10, 20, 50, 100]}
                    labelRowsPerPage="عدد الصفوف:"
                    labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`}
                    sx={{ borderTop: '1px solid', borderColor: 'divider' }}
                  />
                </>
              )}
            </Box>
          </MainCard>
        ) : (
          /* Print Preview Mode */
          <MainCard
            title={`معاينة الطباعة - ${tabNames[activeTab]}`}
            secondary={
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={handleBackToTable}>
                  عودة
                </Button>
                <Button variant="contained" startIcon={<PrintIcon />} onClick={handlePrintTable}>
                  طباعة
                </Button>
              </Stack>
            }
          >
            <Box ref={printRef} sx={{ p: '1.0rem' }}>
              {/* Print Header */}
              <Box sx={{ textAlign: 'center', mb: '1.5rem', pb: '1.0rem', borderBottom: '2px solid', borderColor: 'primary.main' }}>
                <Typography variant="h5" fontWeight="bold" color="primary">
                  {tabNames[activeTab]}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  تاريخ الطباعة: {new Date().toLocaleDateString('en-GB')}
                </Typography>
                {selectedProviderId && (
                  <Typography variant="body2" color="text.secondary">
                    مقدم الخدمة: {providers.find((p) => String(p.id) === String(selectedProviderId))?.name || providers.find((p) => String(p.id) === String(selectedProviderId))?.label || selectedProviderId}
                  </Typography>
                )}
                <Typography variant="body2" color="text.secondary">
                  إجمالي السجلات: {claims.length}
                </Typography>
              </Box>

              {/* KPI Summary for Tab 0 */}
              {activeTab === 0 && (
                <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
                  <Grid size={3}>
                    <Paper sx={{ p: '1.0rem', textAlign: 'center' }}>
                      <Typography variant="caption">إجمالي المطالبات</Typography>
                      <Typography variant="h6" fontWeight="bold">
                        {formatAmount(summaryData.totalClaimsAmount)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={3}>
                    <Paper sx={{ p: '1.0rem', textAlign: 'center' }}>
                      <Typography variant="caption">إجمالي المعتمد</Typography>
                      <Typography variant="h6" fontWeight="bold">
                        {formatAmount(summaryData.totalApprovedAmount)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={3}>
                    <Paper sx={{ p: '1.0rem', textAlign: 'center' }}>
                      <Typography variant="caption">إجمالي المدفوع</Typography>
                      <Typography variant="h6" fontWeight="bold">
                        {formatAmount(summaryData.totalPaidAmount)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid size={3}>
                    <Paper sx={{ p: '1.0rem', textAlign: 'center' }}>
                      <Typography variant="caption">المعلق</Typography>
                      <Typography variant="h6" fontWeight="bold">
                        {formatAmount(summaryData.outstandingAmount)}
                      </Typography>
                    </Paper>
                  </Grid>
                </Grid>
              )}

              {/* Print Table */}
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ '& th': { fontWeight: 'bold', bgcolor: 'grey.100', border: '1px solid #ddd' } }}>
                      {columns.map((col) => (
                        <TableCell key={col.field} align={col.align || 'right'} sx={{ fontSize: '0.85rem' }}>
                          {col.headerName}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {claims.map((row, idx) => (
                      <TableRow
                        key={row.id || idx}
                        sx={{
                          '& td': { border: '1px solid #ddd', fontSize: '0.8rem' },
                          pageBreakInside: 'avoid'
                        }}
                      >
                        {columns.map((col) => {
                          const value = row[col.field];
                          let displayValue = value;
                          if (col.format) displayValue = col.format(value);
                          else if (col.renderCell) displayValue = col.renderCell(value);
                          return (
                            <TableCell key={col.field} align={col.align || 'right'}>
                              {displayValue ?? '-'}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              {/* Print Footer */}
              <Box sx={{ mt: '1.5rem', pt: '1.0rem', borderTop: '1px solid #ddd', textAlign: 'center' }}>
                <Typography variant="caption" color="text.secondary">
                  نظام إدارة التأمين الصحي - تقرير مُنشأ آلياً
                </Typography>
              </Box>
            </Box>
          </MainCard>
        )}
      </Box>
    </>
  );
};

export default FinancialReports;

