import { useState, useEffect } from 'react';
import useEmployerScope from 'hooks/useEmployerScope';
import usePreApprovalsReport, { DEFAULT_FILTERS, PREAUTH_STATUS_LABELS } from 'hooks/usePreApprovalsReport';
import { formatNumber } from 'utils/formatters';
import { providersService } from 'services/api/providers.service';
import { exportToExcel } from 'utils/exportUtils';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// MUI Components
import { Box, Stack, Typography, IconButton, Tooltip, Alert, Chip, AlertTitle, Button } from '@mui/material';

// MUI Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import FileDownloadIcon from '@mui/icons-material/FileDownload';

// Components
import MainCard from 'components/MainCard';
import { PreApprovalsFilters, PreApprovalsTable } from 'components/reports/pre-approvals';

/**
 * Pre-Approvals Operational Report
 *
 * READ-ONLY operational view of pre-approvals with client-side filtering.
 *
 * Architecture: Employer → Member → Pre-Authorization
 *
 * RBAC:
 * - SUPER_ADMIN / ADMIN → All partners, partner selector enabled
 * - EMPLOYER_ADMIN / REVIEWER → Own partner only, selector disabled
 * - PROVIDER → No access (blocked by route guard)
 */
const PreApprovalsReport = () => {
  // Company branding from SSOT
  const { companyName } = useCompanySettings();

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

  // Fetch pre-approvals data
  const { preApprovals, totalCount, totalFetched, loading, error, pagination, refetch } = usePreApprovalsReport({
    employerId: effectiveEmployerId,
    providerId: selectedProviderId,
    filters
  });

  // Check if we have partial data
  const hasPartialData = pagination.totalElements > totalFetched;

  // Handlers
  const handleEmployerChange = (employerId) => {
    if (canSelectEmployer) {
      setSelectedEmployerId(employerId);
      setPage(0);
    }
  };

  const handleProviderChange = (providerId) => {
    setSelectedProviderId(providerId);
    setPage(0);
  };

  const handleFilterChange = (newFilters) => {
    setFilters(newFilters);
    setPage(0);
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (newSize) => {
    setRowsPerPage(newSize);
    setPage(0);
  };

  /**
    * Export pre-approvals to Excel (XLSX format with Arabic support)
    * Uses the unified XLSX export utility.
   */
  const handleExportExcel = () => {
    try {
      // Prepare data for export
      const exportData = preApprovals.map((pa) => ({
        'رقم المرجع': pa.referenceNumber,
        'اسم المؤمن عليه': pa.memberName,
        الشريك: pa.employerName,
        'مقدم الخدمة': pa.providerName,
        'الخدمة الطبية': pa.serviceName,
        الحالة: PREAUTH_STATUS_LABELS[pa.status] || pa.status,
        'المبلغ المطلوب': pa.requestedAmount,
        'المبلغ المعتمد': pa.approvedAmount || '-',
        'تاريخ الطلب': pa.requestDate || '-',
        'صالح حتى': pa.validUntil || '-',
        'آخر تحديث': pa.updatedAt ? new Date(pa.updatedAt).toLocaleDateString('en-GB') : '-'
      }));

      // Generate filename with timestamp
      const timestamp = new Date().toISOString().slice(0, 10);
      const filename = `تقرير_الموافقات_المسبقة_${timestamp}`;

      // Export using unified utility with company branding
      exportToExcel(exportData, filename, { companyName });
    } catch (error) {
      console.error('Failed to export Excel:', error);
    }
  };

  return (
    <MainCard
      title="تقرير الموافقات المسبقة التشغيلي"
      secondary={
        <Stack direction="row" spacing={2} alignItems="center">
          {/* Record count badge */}
          <Chip label={`${totalCount} موافقة مسبقة`} size="small" color="primary" variant="outlined" />

          {/* Export Excel Button */}
          <Tooltip title="تصدير Excel">
            <Button
              variant="outlined"
              size="small"
              color="success"
              onClick={handleExportExcel}
              disabled={loading || totalCount === 0}
              startIcon={<FileDownloadIcon />}
            >
              Excel
            </Button>
          </Tooltip>

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

      {/* Filters */}
      <PreApprovalsFilters
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

      {/* Pre-Approvals Table */}
      <PreApprovalsTable
        preApprovals={preApprovals}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={handlePageChange}
        onRowsPerPageChange={handleRowsPerPageChange}
      />

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

export default PreApprovalsReport;
