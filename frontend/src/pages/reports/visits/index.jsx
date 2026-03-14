import { useState, useEffect } from 'react';
import useEmployerScope from 'hooks/useEmployerScope';
import useVisitsReport, { DEFAULT_FILTERS } from 'hooks/useVisitsReport';
import { formatNumber } from 'utils/formatters';
import { providersService } from 'services/api/providers.service';

// MUI Components
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Tooltip,
  Alert,
  AlertTitle,
  Chip,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider
} from '@mui/material';

// MUI Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

// Components
import MainCard from 'components/MainCard';
import { VisitsKPIs, VisitsFilters, VisitsTable, VisitsInsights } from 'components/reports/visits';

/**
 * Visits Operational Report
 *
 * READ-ONLY operational view of visits.
 * All filtering is handled server-side for data integrity.
 *
 * Architecture: Partner → Member → Visit
 *
 * RBAC:
 * - SUPER_ADMIN / ADMIN → All partners, partner selector enabled
 * - EMPLOYER_ADMIN / REVIEWER → Own partner only, selector disabled
 * - PROVIDER → No access (blocked by route guard)
 */
const VisitsReport = () => {
  // Use centralized employer scope hook (RBAC enforcement)
  const [selectedEmployerId, setSelectedEmployerId] = useState(null);
  const { canSelectEmployer, effectiveEmployerId, employers, isEmployerLocked, userEmployerId } = useEmployerScope(selectedEmployerId);

  // Initialize selected employer for locked roles
  useEffect(() => {
    if (isEmployerLocked && userEmployerId && !selectedEmployerId) {
      setSelectedEmployerId(userEmployerId);
    }
  }, [isEmployerLocked, userEmployerId, selectedEmployerId]);

  // State: Provider filter
  const [selectedProviderId, setSelectedProviderId] = useState(null);
  const [providers, setProviders] = useState([]);

  // Fetch providers list on mount
  useEffect(() => {
    const fetchProviders = async () => {
      try {
        const data = await providersService.getSelector();
        const providersList = data ?? [];
        setProviders(Array.isArray(providersList) ? providersList : []);
      } catch (err) {
        console.error('Failed to fetch providers:', err);
        setProviders([]);
      }
    };
    fetchProviders();
  }, []);

  // State: Filters
  const [filters, setFilters] = useState(DEFAULT_FILTERS);

  // State: Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  // Fetch visits data with both employer and provider filters
  const { visits, totalCount, totalFetched, kpis, insights, loading, error, pagination, refetch } = useVisitsReport({
    employerId: effectiveEmployerId,
    providerId: selectedProviderId,
    filters
  });

  // Check if we have partial data (large dataset warning)
  const hasPartialData = pagination.totalElements > totalFetched;

  // Handlers
  const handleEmployerChange = (employerId) => {
    if (canSelectEmployer) {
      setSelectedEmployerId(employerId);
      setPage(0); // Reset pagination on employer change
    }
  };

  const handleProviderChange = (providerId) => {
    setSelectedProviderId(providerId);
    setPage(0); // Reset pagination on provider change
  };

  const handleFilterChange = (newFilters) => {
    setFilters(newFilters);
    setPage(0); // Reset pagination on filter change
  };

  const handlePageChange = (event, newPage) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <MainCard
      title="تقرير الزيارات التشغيلي"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          {/* Record count badge */}
          <Chip label={`${totalCount} زيارة`} size="small" color="primary" variant="outlined" />

          {/* Refresh Button */}
          <Tooltip title="تحديث البيانات">
            <IconButton onClick={refetch} disabled={loading} color="primary">
              <RefreshIcon
                sx={{
                  fontSize: '1.25rem',
                  animation: loading ? 'spin 1s linear infinite' : 'none'
                }}
              />
            </IconButton>
          </Tooltip>
        </Stack>
      }
    >
      {/* Error Alert */}
      {error && (
        <Alert severity="error" icon={<WarningIcon />} sx={{ mb: '1.0rem' }}>
          {error}
        </Alert>
      )}

      {/* Large Dataset Warning */}
      {hasPartialData && (
        <Alert severity="warning" icon={<ErrorOutlineIcon />} sx={{ mb: '1.0rem' }}>
          <AlertTitle>تحذير: بيانات جزئية</AlertTitle>
          <Typography variant="body2">
            تم تحميل {formatNumber(totalFetched)} سجل من أصل {formatNumber(pagination.totalElements)} سجل. الفلاتر تطبق على البيانات
            المحمّلة فقط. النتائج قد تكون غير شاملة.
          </Typography>
        </Alert>
      )}

      {/* KPIs Section */}
      <VisitsKPIs kpis={kpis} loading={loading} />

      {/* Filters - Single unified filter panel with Employer, Provider, and other filters */}
      <VisitsFilters
        filters={filters}
        onFilterChange={handleFilterChange}
        employers={employers}
        canSelectEmployer={canSelectEmployer}
        selectedEmployerId={selectedEmployerId}
        onEmployerChange={handleEmployerChange}
        providers={providers}
        selectedProviderId={selectedProviderId}
        onProviderChange={handleProviderChange}
      />

      {/* Data Summary */}
      {!loading && totalFetched > 0 && (
        <Box sx={{ mb: '1.0rem', display: 'flex', gap: '1.0rem', flexWrap: 'wrap' }}>
          <Typography variant="body2" color="text.secondary">
            إجمالي السجلات: <strong>{totalFetched}</strong>
          </Typography>
          {totalCount !== totalFetched && (
            <Typography variant="body2" color="text.secondary">
              | بعد الفلترة: <strong>{totalCount}</strong>
            </Typography>
          )}
        </Box>
      )}

      {/* Visits Table */}
      <VisitsTable
        visits={visits}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={handlePageChange}
        onRowsPerPageChange={handleRowsPerPageChange}
      />

      {/* Insights Section */}
      <Divider sx={{ my: '1.5rem' }} />
      <Typography variant="h6" sx={{ mb: '1.0rem' }}>
        📊 إحصائيات وتحليلات
      </Typography>
      <VisitsInsights insights={insights} loading={loading} />

      {/* CSS for spinner animation */}
      <style>
        {`
          @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}
      </style>
    </MainCard>
  );
};

export default VisitsReport;
