import React, { useState, useRef, useMemo } from 'react';
import axiosClient from 'utils/axios';
import { useQuery } from '@tanstack/react-query';
import { useReactToPrint } from 'react-to-print';
import { QRCodeCanvas } from 'qrcode.react';
import {
  Box,
  Button,
  Card,
  Grid,
  TextField,
  MenuItem,
  Stack,
  Typography,
  InputAdornment,
  Divider,
  IconButton,
  Tooltip,
  Paper,
  Avatar,
  Chip,
  Alert,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  TablePagination,
  TableSortLabel,
  Skeleton
} from '@mui/material';
import { useTheme } from '@mui/material/styles';

// Icons
import PrintIcon from '@mui/icons-material/Print';
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import FilterAltIcon from '@mui/icons-material/FilterAlt';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import BadgeIcon from '@mui/icons-material/Badge';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import NumbersIcon from '@mui/icons-material/Numbers';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import AssignmentIcon from '@mui/icons-material/Assignment';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import DateRangeIcon from '@mui/icons-material/DateRange';
import AccessibilityNewIcon from '@mui/icons-material/AccessibilityNew';
import PreviewIcon from '@mui/icons-material/Preview';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

// Components
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { CardStatusBadge, MemberTypeIndicator } from 'components/insurance';
import EmployerFilterSelector from 'components/tba/EmployerFilterSelector';
import { MemberAvatar } from 'components/tba';
import DownloadIcon from '@mui/icons-material/Download';
import CircularLoader from 'components/CircularLoader';

// Company Settings - SINGLE SOURCE OF TRUTH for branding
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// API
import { getAllMembers } from 'services/api/unified-members.service';
import { claimsService } from 'services/api/claims.service';
import useTableState from 'hooks/useTableState';
import { formatCurrency } from 'utils/formatters';

// ============================================================================
// HELPER: Highlight Search Matches
// ============================================================================
const HighlightText = ({ text, searchTerm }) => {
  if (!searchTerm || !text) return <>{text || '-'}</>;

  const lowerText = String(text).toLowerCase();
  const lowerSearch = searchTerm.toLowerCase();
  const index = lowerText.indexOf(lowerSearch);

  if (index === -1) return <>{text}</>;

  const before = String(text).substring(0, index);
  const match = String(text).substring(index, index + searchTerm.length);
  const after = String(text).substring(index + searchTerm.length);

  return (
    <>
      {before}
      <Box component="span" sx={{ bgcolor: 'warning.light', px: 0.3, borderRadius: 0.5, fontWeight: 'bold' }}>
        {match}
      </Box>
      {after}
    </>
  );
};

// ============================================================================
// HELPER: Business Icon Component
// ============================================================================
const BusinessIcon = (props) => (
  <svg {...props} viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
    <path d="M12 7V3H2v18h20V7H12zM6 19H4v-2h2v2zm0-4H4v-2h2v2zm0-4H4V9h2v2zm0-4H4V5h2v2zm4 12H8v-2h2v2zm0-4H8v-2h2v2zm0-4H8V9h2v2zm0-4H8V5h2v2zm10 12h-8v-2h2v-2h-2v-2h2v-2h-2V9h8v10zm-2-8h-2v2h2v-2zm0 4h-2v2h2v-2z" />
  </svg>
);

// ============================================================================
// COMPONENT: BeneficiariesReports
// ============================================================================

const BeneficiariesReports = () => {
  const theme = useTheme();
  const printRef = useRef();

  // --- State ---
  const [selectedEmployerId, setSelectedEmployerId] = useState(null);

  const [filters, setFilters] = useState({
    search: '', // Smart search (Name, Card, National ID)
    cardStatus: 'ALL',
    memberType: 'ALL',
    startDate: '', // Join Date From
    endDate: '' // Join Date To
  });

  const [activeFilters, setActiveFilters] = useState({});
  const [liveSearch, setLiveSearch] = useState(''); // Live search for instant filtering

  // Single View State
  const [viewMode, setViewMode] = useState('TABLE'); // 'TABLE' | 'SINGLE' | 'PRINT_PREVIEW'
  const [selectedMember, setSelectedMember] = useState(null);
  const [financialStats, setFinancialStats] = useState(null);
  const [loadingStats, setLoadingStats] = useState(false);

  // Pagination state (client-side)
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  // Sorting state
  const [orderBy, setOrderBy] = useState('fullName');
  const [order, setOrder] = useState('asc');

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: { field: 'createdAt', direction: 'desc' }
  });

  // --- Handlers ---

  const handleFilterChange = (field) => (event) => {
    setFilters((prev) => ({
      ...prev,
      [field]: event.target.value
    }));
  };

  // Live search handler - instant filtering
  const handleLiveSearchChange = (event) => {
    setLiveSearch(event.target.value);
    setPage(0); // Reset pagination on search change
  };

  const handleApplyFilters = () => {
    const newActiveFilters = {};
    if (filters.search) newActiveFilters.search = filters.search;
    if (filters.cardStatus && filters.cardStatus !== 'ALL') newActiveFilters.cardStatus = filters.cardStatus;
    if (filters.memberType && filters.memberType !== 'ALL') newActiveFilters.memberType = filters.memberType;
    if (filters.startDate) newActiveFilters.startDate = filters.startDate;
    if (filters.endDate) newActiveFilters.endDate = filters.endDate;
    if (selectedEmployerId) newActiveFilters.employerId = selectedEmployerId;

    setActiveFilters(newActiveFilters);
    setLiveSearch(filters.search); // Sync live search with applied search
    setPage(0);
    tableState.setPage(0);
    setViewMode('TABLE'); // Reset to table view on new search
  };

  const handleResetFilters = () => {
    setFilters({
      search: '',
      cardStatus: 'ALL',
      memberType: 'ALL',
      startDate: '',
      endDate: ''
    });
    setSelectedEmployerId(null);
    setActiveFilters({});
    setLiveSearch('');
    setPage(0);
    tableState.setPage(0);
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

  const handleViewSingleReport = async (member) => {
    setSelectedMember(member);
    setViewMode('SINGLE');
    setLoadingStats(true);
    try {
      const stats = await claimsService.getMemberStatement(member.id);
      setFinancialStats(stats);
    } catch (error) {
      console.error('Failed to load financial stats', error);
      // Fallback with empty stats to still show the page
      setFinancialStats({ totalClaimsAmount: 0, totalApprovedAmount: 0, totalCopayAmount: 0, remainingBalance: 0 });
    } finally {
      setLoadingStats(false);
    }
  };

  const handleBackToTable = () => {
    setViewMode('TABLE');
    setSelectedMember(null);
    setFinancialStats(null);
  };

  // --- Print Handlers ---

  // Print the table with proper header/footer
  const handlePrintTable = useReactToPrint({
    contentRef: printRef,
    documentTitle: `Members_Report_${new Date().toISOString().split('T')[0]}`,
    pageStyle: `
      @page { 
        size: A4 landscape; 
        margin: 15mm;
      }
      @media print {
        body { 
          -webkit-print-color-adjust: exact; 
          print-color-adjust: exact;
        }
        .print-hide { display: none !important; }
        .print-header { 
          position: running(header);
          display: block !important;
        }
        .print-footer {
          position: running(footer);
          display: block !important;
        }
        table { page-break-inside: auto; }
        tr { page-break-inside: avoid; page-break-after: auto; }
        thead { display: table-header-group; }
        tfoot { display: table-footer-group; }
      }
    `
  });

  // Show print preview
  const handleShowPrintPreview = () => {
    setViewMode('PRINT_PREVIEW');
  };

  const handleExportExcel = async () => {
    const params = new URLSearchParams();
    if (activeFilters.employerId) params.append('employerId', activeFilters.employerId);
    if (activeFilters.cardStatus && activeFilters.cardStatus !== 'ALL') params.append('status', activeFilters.cardStatus);
    if (activeFilters.memberType && activeFilters.memberType !== 'ALL') params.append('type', activeFilters.memberType);
    if (activeFilters.search) params.append('searchQuery', activeFilters.search);

    try {
      const response = await axiosClient.get(`/unified-members/export/excel?${params.toString()}`, {
        responseType: 'blob'
      });
      const url = window.URL.createObjectURL(new Blob([response.data || response]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `Beneficiaries_Report_${new Date().toISOString().split('T')[0]}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Excel Export failed", error);
    }
  };

  // --- Data Fetching ---

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['beneficiaries-report', page, rowsPerPage, orderBy, order, activeFilters, liveSearch],
    queryFn: async () => {
      const params = {
        page: page, // Backend uses 0-based pagination
        size: rowsPerPage // server-side sizing
      };

      // Map Sort (from UI state)
      if (orderBy) {
        let sortField = orderBy;

        // Map frontend ID to backend field if necessary
        if (sortField === 'memberType') sortField = 'type';
        if (sortField === 'cardStatus') sortField = 'status';
        if (sortField === 'joinDate') sortField = 'createdAt';

        params.sort = sortField;
        params.direction = order.toUpperCase();
      } else {
        params.sort = 'createdAt';
        params.direction = 'DESC';
      }

      // Map Filters
      if (activeFilters.employerId) {
        params.organizationId = activeFilters.employerId;
      }
      if (activeFilters.cardStatus && activeFilters.cardStatus !== 'ALL') {
        params.status = activeFilters.cardStatus;
      }
      if (activeFilters.memberType && activeFilters.memberType !== 'ALL') {
        params.type = activeFilters.memberType;
      }
      if (liveSearch && liveSearch.trim()) {
        params.fullName = liveSearch.trim();
      }

      const response = await getAllMembers(params);

      // Map Spring Page response to expected format
      return {
        items: response?.content || [],
        total: response?.totalElements || 0,
        page: response?.number || 0,
        size: response?.size || params.size
      };
    },
    keepPreviousData: true
  });

  // For BeneficiariesReports, we use the server-side pagination entirely
  const paginatedData = data?.items || [];

  // Total counts from server
  const totalFetched = data?.total || 0;
  const totalFiltered = data?.total || 0;

  // --- Columns ---

  const columns = useMemo(
    () => [
      {
        id: 'index',
        header: '#',
        enableSorting: false,
        width: '3.125rem',
        align: 'center',
        cell: ({ row }) => page * rowsPerPage + row.index + 1
      },
      {
        accessorKey: 'fullName',
        header: 'اسم المنتفع',
        minWidth: '12.5rem',
        cell: ({ row }) => (
          <Stack direction="row" alignItems="center" spacing={1}>
            <MemberAvatar member={row.original} size={32} />
            <Box>
              <Typography variant="subtitle2" fontWeight="600">
                <HighlightText text={row.original.fullName} searchTerm={liveSearch} />
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {row.original.gender === 'MALE' ? 'ذكر' : 'أنثى'} - {row.original.age || '?'} سنة
              </Typography>
            </Box>
          </Stack>
        )
      },
      {
        accessorKey: 'barcode',
        header: 'المعرف البصري (QR)',
        minWidth: '10.0rem',
        cell: ({ row }) => (
          <Stack spacing={0.5}>
            <Stack direction="row" spacing={0.5} alignItems="center">
              <QrCode2Icon sx={{ fontSize: '1.0rem', color: theme.palette.primary.main }} />
              <Typography variant="body2" fontFamily="monospace" fontWeight="bold" color="primary">
                <HighlightText text={row.original.barcode} searchTerm={liveSearch} />
              </Typography>
            </Stack>
            <Stack direction="row" spacing={0.5} alignItems="center">
              <BusinessIcon sx={{ fontSize: '0.875rem', color: 'text.secondary' }} />
              <Typography variant="caption" color="text.secondary" noWrap>
                {row.original.employerName || row.original.partnerNane || '-'}
              </Typography>
            </Stack>
          </Stack>
        )
      },
      {
        accessorKey: 'cardNumber',
        header: 'رقم البطاقة',
        minWidth: '8.75rem',
        cell: ({ row }) => (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <CreditCardIcon sx={{ fontSize: '0.875rem', color: theme.palette.warning.main }} />
            <Typography variant="body2" fontFamily="monospace" fontWeight="bold">
              <HighlightText text={row.original.cardNumber} searchTerm={liveSearch} />
            </Typography>
          </Stack>
        )
      },
      {
        accessorKey: 'nationalNumber',
        header: 'رقم الهوية',
        minWidth: '8.125rem',
        cell: ({ row }) => (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <BadgeIcon sx={{ fontSize: '0.875rem', color: 'text.secondary' }} />
            <Typography variant="body2" fontFamily="monospace">
              <HighlightText text={row.original.nationalNumber} searchTerm={liveSearch} />
            </Typography>
          </Stack>
        )
      },
      {
        accessorKey: 'memberType',
        header: 'نوع العضوية',
        minWidth: '7.5rem',
        cell: ({ row }) => <MemberTypeIndicator type={row.original.type} size="small" />
      },
      {
        accessorKey: 'joinDate',
        header: 'تاريخ الانضمام',
        minWidth: '7.5rem',
        cell: ({ getValue }) => getValue() || '-'
      },
      {
        accessorKey: 'cardStatus',
        header: 'الحالة',
        minWidth: '6.25rem',
        align: 'center',
        cell: ({ row }) => <CardStatusBadge status={row.original.cardStatus || 'ACTIVE'} size="small" language="ar" />
      },
      {
        id: 'actions',
        header: 'عرض',
        align: 'center',
        cell: ({ row }) => (
          <Tooltip title="تقرير تفصيلي">
            <IconButton size="small" color="primary" onClick={() => handleViewSingleReport(row.original)}>
              <AssignmentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )
      }
    ],
    [page, rowsPerPage, liveSearch, theme]
  );

  // ========================================================================
  // VIEW RENDER
  // ========================================================================

  return (
    <Box>
      <UnifiedPageHeader
        title="تقارير المنتفعين"
        subtitle="تحليل بيانات المنتفعين والسجل المالي"
        icon={MedicalServicesIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'التقارير' },
          { label: viewMode === 'SINGLE' ? 'تفاصيل المنتفع' : 'المنتفعين' }
        ]}
        additionalActions={
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleExportExcel}
              color="primary"
            >
              تصدير إكسيل
            </Button>
            {viewMode === 'SINGLE' && (
              <Button variant="contained" startIcon={<ArrowBackIcon />} onClick={handleBackToTable} color="secondary">
                عودة للقائمة
              </Button>
            )}
          </Stack>
        }
      />

      {/* --- Filter Section (Only in Table Mode) --- */}
      {viewMode === 'TABLE' && (
        <Card sx={{ mb: '1.5rem', p: '1.0rem', bgcolor: 'background.paper', borderRadius: '0.25rem', border: '1px solid', borderColor: 'divider' }}>
          <Stack direction="row" alignItems="center" spacing={1} mb={2}>
            <FilterAltIcon color="primary" />
            <Typography variant="h6" fontWeight="bold">
              خيارات الفلترة المتقدمة
            </Typography>
          </Stack>

          <Grid container spacing={2}>
            {/* 1. Partner Filter */}
            <Grid size={{ xs: 12, md: 4 }}>
              <EmployerFilterSelector
                selectedEmployerId={selectedEmployerId}
                onEmployerChange={(emp) => {
                  const newEmployerId = emp?.id || null;
                  setSelectedEmployerId(newEmployerId);
                  // Auto-apply employer filter immediately
                  setActiveFilters((prev) => ({
                    ...prev,
                    employerId: newEmployerId
                  }));
                  setPage(0);
                  tableState.setPage(0);
                }}
                showAllOption={true}
              />
            </Grid>

            {/* 2. Smart Search */}
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="بحث ذكي (الاسم، البطاقة، الهوية)"
                value={filters.search}
                onChange={handleFilterChange('search')}
                placeholder="ابحث هنا..."
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon color="action" />
                    </InputAdornment>
                  )
                }}
                size="small"
              />
            </Grid>

            {/* 3. Member Type */}
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                select
                fullWidth
                label="نوع العضوية"
                value={filters.memberType}
                onChange={handleFilterChange('memberType')}
                size="small"
              >
                <MenuItem value="ALL">الكل</MenuItem>
                <MenuItem value="PRINCIPAL">مشترك أساسي</MenuItem>
                <MenuItem value="DEPENDENT">تابع</MenuItem>
              </TextField>
            </Grid>

            {/* 4. Status */}
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                select
                fullWidth
                label="الحالة"
                value={filters.cardStatus}
                onChange={handleFilterChange('cardStatus')}
                size="small"
              >
                <MenuItem value="ALL">الكل</MenuItem>
                <MenuItem value="ACTIVE">نشط</MenuItem>
                <MenuItem value="EXPIRED">منتهي الصلاحية</MenuItem>
                <MenuItem value="SUSPENDED">موقوف</MenuItem>
              </TextField>
            </Grid>

            {/* 5. Date From */}
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                type="date"
                fullWidth
                label="تاريخ الانضمام (من)"
                value={filters.startDate}
                onChange={handleFilterChange('startDate')}
                InputLabelProps={{ shrink: true }}
                size="small"
              />
            </Grid>

            {/* 6. Date To */}
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                type="date"
                fullWidth
                label="تاريخ الانضمام (إلى)"
                value={filters.endDate}
                onChange={handleFilterChange('endDate')}
                InputLabelProps={{ shrink: true }}
                size="small"
              />
            </Grid>

            <Grid sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, alignItems: 'center' }} size={{ xs: 12, md: 6 }}>
              <Button variant="outlined" color="inherit" onClick={handleResetFilters} startIcon={<RefreshIcon />}>
                إعادة تعيين
              </Button>
              <Button variant="contained" color="primary" onClick={handleApplyFilters} startIcon={<SearchIcon />}>
                تطبيق البحث
              </Button>
            </Grid>
          </Grid>
        </Card>
      )}

      {/* --- Main Content Switcher --- */}

      {viewMode === 'TABLE' ? (
        <>
          <MainCard content={false}>
            {/* Live Search Bar with Stats */}
            <Box sx={{ p: '1.0rem', borderBottom: '1px solid', borderColor: 'divider' }}>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                <TextField
                  size="small"
                  placeholder="بحث فوري بالاسم، رقم البطاقة، الهوية، الباركود..."
                  value={liveSearch}
                  onChange={handleLiveSearchChange}
                  sx={{ minWidth: '20.0rem' }}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon color="primary" />
                      </InputAdornment>
                    ),
                    endAdornment: liveSearch && (
                      <InputAdornment position="end">
                        <IconButton
                          size="small"
                          onClick={() => {
                            setLiveSearch('');
                            setPage(0);
                          }}
                        >
                          <RefreshIcon fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    )
                  }}
                />

                <Box sx={{ flexGrow: 1 }} />

                <Stack direction="row" spacing={2} alignItems="center">
                  <Typography variant="body2" color="text.secondary">
                    الإجمالي: <strong>{totalFetched}</strong>
                    {liveSearch && ` | المطابق: `}
                    {liveSearch && <strong style={{ color: totalFiltered === 0 ? 'red' : 'green' }}>{totalFiltered}</strong>}
                  </Typography>

                  <Tooltip title="معاينة الطباعة">
                    <IconButton color="primary" onClick={handleShowPrintPreview}>
                      <PreviewIcon />
                    </IconButton>
                  </Tooltip>

                  <Tooltip title="طباعة">
                    <IconButton color="primary" onClick={handlePrintTable}>
                      <PrintIcon />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Stack>
            </Box>

            {/* Error State */}
            {!isLoading && !isFetching && totalFetched === 0 && (
              <Alert severity="info" sx={{ m: '1.0rem' }} icon={<ErrorOutlineIcon />}>
                لا توجد بيانات مطابقة للفلاتر المحددة. جرّب تغيير معايير البحث.
              </Alert>
            )}

            {/* No Search Results */}
            {liveSearch && totalFiltered === 0 && totalFetched > 0 && (
              <Alert severity="warning" sx={{ m: '1.0rem' }}>
                لم يتم العثور على نتائج مطابقة لـ "{liveSearch}". جرّب كلمات بحث مختلفة.
              </Alert>
            )}

            {/* Loading Skeleton */}
            {(isLoading || isFetching) && (
              <Box sx={{ p: '1.0rem' }}>
                {[...Array(5)].map((_, i) => (
                  <Skeleton key={i} variant="rectangular" height={60} sx={{ mb: 1, borderRadius: 1 }} />
                ))}
              </Box>
            )}

            {/* Data Table */}
            {!isLoading && !isFetching && paginatedData.length > 0 && (
              <>
                <TableContainer ref={printRef}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow sx={{ '& th': { fontWeight: 'bold', bgcolor: 'grey.100' } }}>
                        {columns.map((col) => (
                          <TableCell
                            key={col.id || col.accessorKey}
                            align={col.align || 'right'}
                            sx={{ minWidth: col.minWidth, width: col.width }}
                          >
                            {col.accessorKey && col.enableSorting !== false ? (
                              <TableSortLabel
                                active={orderBy === col.accessorKey}
                                direction={orderBy === col.accessorKey ? order : 'asc'}
                                onClick={() => handleRequestSort(col.accessorKey)}
                              >
                                {col.header}
                              </TableSortLabel>
                            ) : (
                              col.header
                            )}
                          </TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {paginatedData.map((member, idx) => (
                        <TableRow
                          key={member.id || member.barcode || idx}
                          hover
                          sx={{
                            '&:nth-of-type(odd)': { bgcolor: 'grey.50' },
                            '&:hover': { bgcolor: 'action.hover' }
                          }}
                        >
                          {columns.map((col) => (
                            <TableCell key={col.id || col.accessorKey} align={col.align || 'right'}>
                              {col.cell
                                ? col.cell({ row: { original: member, index: idx }, getValue: () => member[col.accessorKey] })
                                : member[col.accessorKey] || '-'}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>

                <TablePagination
                  component="div"
                  count={data?.total || 0}
                  page={page}
                  onPageChange={handleChangePage}
                  rowsPerPage={rowsPerPage}
                  onRowsPerPageChange={handleChangeRowsPerPage}
                  rowsPerPageOptions={[10, 25, 50, 100]}
                  labelRowsPerPage="عدد الصفوف:"
                  labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
                  sx={{ borderTop: '1px solid', borderColor: 'divider' }}
                />
              </>
            )}
          </MainCard>
        </>
      ) : viewMode === 'PRINT_PREVIEW' ? (
        /* --- Print Preview Mode --- */
        <MainCard
          title="معاينة الطباعة"
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
                تقرير المنتفعين
              </Typography>
              <Typography variant="body2" color="text.secondary">
                تاريخ الطباعة: {new Date().toLocaleDateString('en-GB')}
              </Typography>
              {liveSearch && (
                <Typography variant="body2" color="primary">
                  نتائج البحث عن: "{liveSearch}"
                </Typography>
              )}
              <Typography variant="body2" color="text.secondary">
                إجمالي السجلات: {totalFiltered}
              </Typography>
            </Box>

            {/* Print Table - Show ALL current page data */}
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ '& th': { fontWeight: 'bold', bgcolor: 'grey.100', border: '1px solid #ddd' } }}>
                    {columns
                      .filter((c) => c.id !== 'actions')
                      .map((col) => (
                        <TableCell
                          key={col.id || col.accessorKey}
                          align={col.align || 'right'}
                          sx={{ minWidth: col.minWidth, fontSize: '0.85rem' }}
                        >
                          {col.header}
                        </TableCell>
                      ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginatedData.map((member, idx) => (
                    <TableRow
                      key={member.id || member.barcode || idx}
                      sx={{
                        '& td': { border: '1px solid #ddd', fontSize: '0.8rem' },
                        pageBreakInside: 'avoid'
                      }}
                    >
                      {columns
                        .filter((c) => c.id !== 'actions')
                        .map((col) => (
                          <TableCell key={col.id || col.accessorKey} align={col.align || 'right'}>
                            {col.cell
                              ? col.cell({ row: { original: member, index: idx }, getValue: () => member[col.accessorKey] })
                              : member[col.accessorKey] || '-'}
                          </TableCell>
                        ))}
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
      ) : (
        /* --- Single Report View --- */
        <SingleBeneficiaryReport
          member={selectedMember}
          financialStats={financialStats}
          loadingStats={loadingStats}
          onBack={handleBackToTable}
        />
      )}

      {/* --- Hidden Components --- */}
    </Box>
  );
};

// --- Sub-components ---

const InfoRow = ({ icon, label, value, isMono }) => (
  <Box display="flex" justifyContent="space-between" alignItems="center" py={1} borderBottom="1px solid #f0f0f0">
    <Stack direction="row" spacing={1} alignItems="center">
      {React.cloneElement(icon, { color: 'action', fontSize: 'small' })}
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Stack>
    <Typography variant="body2" fontWeight="medium" fontFamily={isMono ? 'monospace' : 'inherit'}>
      {value || '-'}
    </Typography>
  </Box>
);

const SummaryCard = ({ title, value, icon, color }) => (
  <Paper sx={{ p: '1.0rem', borderLeft: `4px solid`, borderLeftColor: `${color}.main` }}>
    <Stack spacing={1}>
      <Box display="flex" justifyContent="space-between">
        <Typography variant="caption" color="text.secondary">
          {title}
        </Typography>
        {React.cloneElement(icon, { color: color, fontSize: 'small' })}
      </Box>
      <Typography variant="h5" fontWeight="bold">
        {value}
      </Typography>
    </Stack>
  </Paper>
);

// --- Single Report Component ---
const SingleBeneficiaryReport = ({ member, financialStats, loadingStats, onBack }) => {
  const theme = useTheme();
  const componentRef = useRef();

  // Get company branding from SSOT
  const { getLogoSrc, companyName, hasLogo } = useCompanySettings();

  const handlePrint = useReactToPrint({
    contentRef: componentRef,
    documentTitle: `Member_Report_${member?.cardNumber || 'Unknown'}`,
    pageStyle: `
      @page { size: A4 portrait; margin: 10mm; }
      @media print { body { -webkit-print-color-adjust: exact; } }
    `
  });

  if (loadingStats) {
    return (
      <Stack alignItems="center" justifyContent="center" height={300}>
        <CircularLoader />
        <Typography mt={2}>جاري تحميل البيانات المالية...</Typography>
      </Stack>
    );
  }

  return (
    <Box>
      {/* Printable Area */}
      <Box ref={componentRef} sx={{ p: '1.0rem', bgcolor: 'background.paper', borderRadius: '0.25rem' }}>
        {/* Report Header */}
        <Box sx={{ borderBottom: '2px solid #eee', mb: '1.5rem', pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Box>
            <Typography variant="h4" color="primary.main" fontWeight="bold">
              تقرير تفاصيل المنتفع
            </Typography>
            <Typography variant="body2" color="text.secondary">
              تاريخ التقرير: {new Date().toLocaleDateString('en-GB')}
            </Typography>
          </Box>
          {hasLogo() ? (
            <img src={getLogoSrc()} alt={companyName} style={{ height: '3.125rem', opacity: 0.8 }} />
          ) : (
            <Typography variant="h6" color="primary.main" fontWeight="bold">
              {companyName}
            </Typography>
          )}
        </Box>

        <Grid container spacing={4}>
          {/* Left Col: Member Info */}
          <Grid size={{ xs: 12, md: 5 }}>
            <MainCard title="البيانات الشخصية والتعريفية">
              <Stack spacing={2}>
                <Box display="flex" alignItems="center" gap={2}>
                  <MemberAvatar member={member} size={64} />
                  <Box>
                    <Typography variant="h6">{member?.fullName}</Typography>
                    <MemberTypeIndicator type={member?.type} />
                  </Box>
                </Box>
                <Divider />

                <Box
                  display="flex"
                  flexDirection="column"
                  alignItems="center"
                  mb={3}
                  p={2}
                  border="1px solid #e0e0e0"
                  borderRadius={2}
                  bgcolor="#f8f9fa"
                >
                  <Typography variant="caption" fontWeight="bold" color="text.secondary" gutterBottom>
                    SCAN MEMBER IDENTITY
                  </Typography>
                  <Box p={1.5} bgcolor="white" borderRadius={2} border="1px solid #eee" boxShadow={2} mb={2}>
                    {member?.barcode ? (
                      <QRCodeCanvas value={member.barcode} size={150} level={'H'} includeMargin={true} />
                    ) : (
                      <Typography variant="caption" color="error">
                        BARCODE MISSING
                      </Typography>
                    )}
                  </Box>

                  <Stack spacing={1} width="100%">
                    <Box display="flex" justifyContent="space-between" alignItems="center" borderBottom="1px dashed #ddd" pb={0.5}>
                      <Typography variant="caption" color="text.secondary">
                        BARCODE (Ref)
                      </Typography>
                      <Typography variant="body2" fontFamily="monospace" fontWeight="bold" letterSpacing={1}>
                        {member?.barcode || '-'}
                      </Typography>
                    </Box>

                    <Box display="flex" justifyContent="space-between" alignItems="center" borderBottom="1px dashed #ddd" pb={0.5}>
                      <Typography variant="caption" color="text.secondary">
                        CARD NUMBER
                      </Typography>
                      <Typography variant="body2" fontFamily="monospace" fontWeight="bold" color="primary">
                        {member?.cardNumber || '-'}
                      </Typography>
                    </Box>

                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <Typography variant="caption" color="text.secondary">
                        NATIONAL ID
                      </Typography>
                      <Typography variant="body2" fontFamily="monospace">
                        {member?.nationalNumber || '-'}
                      </Typography>
                    </Box>
                  </Stack>
                </Box>
                <Divider sx={{ mb: '1.0rem' }} />
                <InfoRow icon={<CreditCardIcon />} label="رقم البطاقة" value={member?.cardNumber} isMono />
                <InfoRow icon={<BusinessIcon />} label="الشركة/الشريك" value={member?.employerName} />
                <InfoRow icon={<DateRangeIcon />} label="تاريخ الانضمام" value={member?.joinDate} />
                <InfoRow
                  icon={<AccessibilityNewIcon />}
                  label="الجنس والعمر"
                  value={`${member?.gender === 'MALE' ? 'ذكر' : 'أنثى'} / ${member?.age || '-'} سنة`}
                />

                <Box mt={2}>
                  <Typography variant="subtitle2" gutterBottom>
                    حالة البطاقة
                  </Typography>
                  <CardStatusBadge status={member?.cardStatus} />
                </Box>
              </Stack>
            </MainCard>
          </Grid>

          {/* Right Col: Financial Summary */}
          <Grid size={{ xs: 12, md: 7 }}>
            <MainCard title="الملخص المالي (للسنة الحالية)" secondary={<Chip label="للقراءة فقط" size="small" variant="outlined" />}>
              <Grid container spacing={2}>
                <Grid size={6}>
                  <SummaryCard
                    title="إجمالي المطالبات"
                    value={formatCurrency(financialStats?.totalRequested || 0)}
                    icon={<AssignmentIcon />}
                    color="primary"
                  />
                </Grid>
                <Grid size={6}>
                  <SummaryCard
                    title="المبالغ المعتمدة"
                    value={formatCurrency(financialStats?.totalNetPayable || 0)}
                    icon={<AttachMoneyIcon />}
                    color="success"
                  />
                </Grid>
                <Grid size={6}>
                  <SummaryCard
                    title="مبلغ التحمل (Co-Pay)"
                    value={formatCurrency(financialStats?.totalPatientCoPay || 0)}
                    icon={<AccountBalanceWalletIcon />}
                    color="warning"
                  />
                </Grid>
                <Grid size={6}>
                  <SummaryCard
                    title="المتبقي"
                    value={formatCurrency(financialStats?.remainingBalance || 0)}
                    icon={<NumbersIcon />} // Use Numbers or similar
                    color="info"
                  />
                </Grid>
              </Grid>

              {/* Placeholder Table for recent claims */}
              <Box mt={3}>
                <Divider sx={{ mb: '1.0rem' }} />
                <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                  آخر 5 مطالبات
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  لا توجد مطالبات حديثة للعرض في هذا الموجز.
                </Typography>
              </Box>
            </MainCard>
          </Grid>
        </Grid>
      </Box>

      {/* Footer Actions (Outside Print Area) */}
      <Box mt={2} display="flex" justifyContent="flex-end" gap={2} className="print-hide">
        <Button variant="outlined" color="inherit" onClick={onBack} startIcon={<ArrowBackIcon />}>
          عودة
        </Button>
        <Button variant="contained" color="secondary" startIcon={<PrintIcon />} onClick={handlePrint}>
          طباعة التقرير الفردي
        </Button>
      </Box>
    </Box>
  );
};

export default BeneficiariesReports;
