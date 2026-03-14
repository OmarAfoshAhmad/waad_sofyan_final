/**
 * Pre-Approvals List Page - Phase D2.4 (TbaDataTable Pattern)
 * Prior Authorization / Pre-Approval Requests Management
 *
 * ⚠️ Pattern: ModernPageHeader → MainCard → TbaDataTable
 *
 * Rules Applied:
 * 1. icon={Component} - NEVER JSX
 * 2. Arabic only - No English labels
 * 3. Defensive optional chaining
 * 4. TbaDataTable for server-side pagination/sorting/filtering
 * 5. TableRefreshContext for post-create/edit refresh (Phase D2.3)
 *
 * CANONICAL 2026-01-28:
 * - NO Edit/Delete actions - Pre-approvals managed ONLY from Provider Portal
 * - Creation only via Provider Portal (visit-based flow)
 * - This page is READ-ONLY for insurance admins and reviewers
 */

import { useMemo, useCallback, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// MUI Components
import { Box, Button, IconButton, Tooltip, Typography, Alert, Stack, TextField, InputAdornment } from '@mui/material';

// MUI Icons - Always as Component, NEVER as JSX
import VisibilityIcon from '@mui/icons-material/Visibility';
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import TableErrorBoundary from 'components/TableErrorBoundary';

// Insurance UX Components - Phase B2
import { CardStatusBadge, PriorityBadge } from 'components/insurance';

// Contexts
import { useTableRefresh } from 'contexts/TableRefreshContext';

// Services
import { preApprovalsService, providersService } from 'services/api';

// ============================================================================
// CONSTANTS
// ============================================================================

// Pre-Approval Status Mapping for CardStatusBadge
const PREAPPROVAL_STATUS_MAP = {
  PENDING: 'PENDING',
  UNDER_REVIEW: 'PENDING',
  APPROVAL_IN_PROGRESS: 'PENDING',
  NEEDS_CORRECTION: 'SUSPENDED',
  APPROVED: 'ACTIVE',
  ACKNOWLEDGED: 'ACTIVE',
  REJECTED: 'BLOCKED',
  EXPIRED: 'EXPIRED',
  CANCELLED: 'INACTIVE',
  USED: 'INACTIVE'
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format currency with LYD
 */
const formatCurrency = (value) => {
  if (value === null || value === undefined) return '-';
  return `${Number(value).toLocaleString('en-US')} د.ل`;
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const PreApprovalsList = () => {
  const navigate = useNavigate();
  const [providerOptions, setProviderOptions] = useState([]);

  // ========================================
  // TABLE STATE
  // ========================================

  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalItems, setTotalItems] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');

  // ========================================
  // TABLE REFRESH CONTEXT (Phase D2.3)
  // ========================================

  const { refreshKey, triggerRefresh } = useTableRefresh();

  // Load Providers for Filter
  useEffect(() => {
    const loadProviders = async () => {
      try {
        const data = await providersService.getSelector(); // Uses /api/providers/selector
        // Map to format strings or objects as needed by TbaDataTable/MRT
        // Assuming data is [{id, name}, ...]
        const options = data.map((p) => p.name || p.providerName);
        setProviderOptions(options);
      } catch (err) {
        console.error('Failed to load providers for filter', err);
      }
    };
    loadProviders();
  }, []);

  // ========================================
  // DATA FETCHING
  // ========================================

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        page: page + 1,
        size: rowsPerPage,
        ...(searchTerm && { search: searchTerm })
      };

      const result = await preApprovalsService.getAll(params);

      // Handle different response formats
      let items = [];
      let total = 0;

      if (Array.isArray(result)) {
        items = result;
        total = result.length;
      } else if (result?.content) {
        items = result.content;
        total = result.totalElements || result.content.length;
      } else if (result?.items) {
        items = result.items;
        total = result.total || result.items.length;
      }

      setData(items);
      setTotalItems(total);
    } catch (err) {
      console.error('Failed to fetch pre-approvals:', err);
      setError('فشل في تحميل البيانات');
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, searchTerm]);

  useEffect(() => {
    fetchData();
  }, [fetchData, refreshKey]);

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================

  const handleNavigateView = useCallback(
    (id) => {
      navigate(`/pre-approvals/${id}`);
    },
    [navigate]
  );

  // ========================================
  // PAGINATION HANDLERS
  // ========================================

  const handlePageChange = useCallback((event, newPage) => {
    setPage(newPage);
  }, []);

  const handleRowsPerPageChange = useCallback((event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  }, []);

  const handleSearch = useCallback(() => {
    setPage(0);
    fetchData();
  }, [fetchData]);

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================

  const columns = useMemo(
    () => [
      {
        id: 'referenceNumber',
        label: '#',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'member',
        label: 'المؤمَّن عليه',
        minWidth: '11.25rem',
        icon: <PersonIcon fontSize="small" />,
        sortable: false
      },
      {
        id: 'provider',
        label: 'مقدم الخدمة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'service',
        label: 'الخدمة',
        minWidth: '9.375rem',
        icon: <MedicalServicesIcon fontSize="small" />,
        sortable: false
      },
      {
        id: 'priority',
        label: 'الأولوية',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'contractPrice',
        label: 'سعر العقد',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'approvedAmount',
        label: 'المبلغ الموافق عليه',
        minWidth: '8.75rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'actions',
        label: 'الإجراءات',
        minWidth: '5.0rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================

  const renderCell = useCallback(
    (preApproval, column) => {
      if (!preApproval) return null;

      switch (column.id) {
        case 'referenceNumber':
          return <Typography variant="subtitle2">{preApproval?.referenceNumber ?? `PA-${preApproval?.id}` ?? '-'}</Typography>;

        case 'member':
          return (
            <Stack direction="row" spacing={1} alignItems="center">
              <PersonIcon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />
              <Typography variant="body2">{preApproval?.memberName ?? preApproval?.memberFullName ?? '-'}</Typography>
            </Stack>
          );

        case 'provider':
          return <Typography variant="body2">{preApproval?.providerName ?? '-'}</Typography>;

        case 'service':
          return <Typography variant="body2">{preApproval?.serviceName ?? preApproval?.serviceCode ?? '-'}</Typography>;

        case 'priority':
          return <PriorityBadge priority={preApproval?.priority ?? 'ROUTINE'} size="small" variant="chip" language="ar" />;

        case 'contractPrice':
          return (
            <Typography variant="body2" fontWeight={500}>
              {formatCurrency(preApproval?.contractPrice ?? preApproval?.requestedAmount)}
            </Typography>
          );

        case 'approvedAmount':
          return (
            <Typography variant="body2" fontWeight={500} color="success.main">
              {formatCurrency(preApproval?.approvedAmount)}
            </Typography>
          );

        case 'status':
          const status = preApproval?.status;
          const mappedStatus = PREAPPROVAL_STATUS_MAP[status] || status || 'PENDING';
          return <CardStatusBadge status={mappedStatus} size="small" language="ar" />;

        case 'actions':
          return (
            <Tooltip title="عرض">
              <IconButton size="small" color="primary" onClick={() => handleNavigateView(preApproval?.id)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          );

        default:
          return null;
      }
    },
    [handleNavigateView]
  );

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box>
      {/* ====== PAGE HEADER ====== */}
      <ModernPageHeader
        title="الموافقات المسبقة"
        subtitle="إدارة ومتابعة طلبات الموافقات المسبقة"
        icon={AssignmentTurnedInIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'الموافقات المسبقة' }]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={triggerRefresh}>
              تحديث
            </Button>
          </Stack>
        }
      />

      {/* ====== MAIN CARD WITH TABLE ====== */}
      <MainCard
        title="قائمة الموافقات المسبقة"
        secondary={
          <Alert severity="info" variant="outlined" sx={{ py: 0.5, px: '0.75rem', border: 'none', bgcolor: 'transparent' }} icon={false}>
            💡 لإنشاء موافقة جديدة، استخدم <strong>سجل الزيارات</strong> في بوابة مقدم الخدمة
          </Alert>
        }
      >
        {/* Search Bar */}
        <Stack direction="row" spacing={2} sx={{ mb: '1.5rem' }} alignItems="center">
          <TextField
            placeholder="بحث..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              )
            }}
            sx={{ minWidth: '18.75rem' }}
          />
          <Button variant="contained" startIcon={<SearchIcon />} onClick={handleSearch}>
            بحث
          </Button>
        </Stack>

        <TableErrorBoundary>
          <UnifiedMedicalTable
            columns={columns}
            data={data}
            loading={loading}
            error={error}
            onErrorClose={() => setError(null)}
            renderCell={renderCell}
            totalItems={totalItems}
            page={page}
            rowsPerPage={rowsPerPage}
            onPageChange={handlePageChange}
            onRowsPerPageChange={handleRowsPerPageChange}
            emptyStateConfig={{
              icon: AssignmentTurnedInIcon,
              title: 'لا توجد موافقات مسبقة',
              description: 'لا توجد موافقات مسبقة مسجلة حالياً'
            }}
          />
        </TableErrorBoundary>
      </MainCard>
    </Box>
  );
};

export default PreApprovalsList;


