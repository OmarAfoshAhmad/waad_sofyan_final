import { useState, useEffect, useMemo } from 'react';
import useEmployerScope from 'hooks/useEmployerScope';
import useClaimsReport, { DEFAULT_FILTERS, CLAIM_STATUS_LABELS } from 'hooks/useClaimsReport';
import { formatNumber } from 'utils/formatters';
import { providersService } from 'services/api/providers.service';
import { exportToExcel } from 'utils/exportUtils';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// MUI Components
import { Box, Stack, Typography, IconButton, Tooltip, Alert, Chip, Button, Paper, Divider } from '@mui/material';

// MUI Icons
import RefreshIcon from '@mui/icons-material/Refresh';
import WarningIcon from '@mui/icons-material/Warning';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import AssignmentIcon from '@mui/icons-material/Assignment';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { ClaimsFilters, ClaimsTable } from 'components/reports/claims';

/**
 * Claims Operational Report
 *
 * READ-ONLY operational view of all finalized claims.
 */
const ClaimsReport = () => {
  const { companyName } = useCompanySettings();

  const [selectedEmployerId, setSelectedEmployerId] = useState(null);
  const { canSelectEmployer, effectiveEmployerId, employers, isEmployerLocked, userEmployerId } = useEmployerScope(selectedEmployerId);

  useEffect(() => {
    if (isEmployerLocked && userEmployerId && !selectedEmployerId) {
      setSelectedEmployerId(userEmployerId);
    }
  }, [isEmployerLocked, userEmployerId, selectedEmployerId]);

  const [selectedProviderId, setSelectedProviderId] = useState(null);
  const [providers, setProviders] = useState([]);

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

  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  const { claims, totalFetched, loading, error, pagination, refetch } = useClaimsReport({
    employerId: effectiveEmployerId,
    providerId: selectedProviderId,
    filters
  });

  const totalCount = claims.length;
  const hasPartialData = pagination.totalElements > totalFetched;

  const reportSummary = useMemo(() => {
    const requested = claims.reduce((sum, claim) => sum + (Number(claim.requestedAmount) || 0), 0);
    const approved = claims.reduce((sum, claim) => sum + (Number(claim.approvedAmount) || 0), 0);
    const rejected = Math.max(requested - approved, 0);
    const approvedClaims = claims.filter((claim) => ['APPROVED', 'BATCHED', 'SETTLED'].includes(claim.status)).length;
    const rejectedClaims = claims.filter((claim) => claim.status === 'REJECTED').length;
    const approvalRate = requested > 0 ? (approved / requested) * 100 : 0;

    return {
      requested,
      approved,
      rejected,
      approvedClaims,
      rejectedClaims,
      approvalRate
    };
  }, [claims]);

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

  const handlePageChange = (newPage) => setPage(newPage);
  const handleRowsPerPageChange = (newSize) => {
    setRowsPerPage(newSize);
    setPage(0);
  };

  const handleExportExcel = () => {
    try {
      const exportData = claims.map((claim) => ({
        'رقم المطالبة': claim.paperReference || claim._raw?.paperReference || claim._raw?.claimNumber || claim.id,
        'اسم المؤمن عليه': claim.memberName,
        الشريك: claim.employerName,
        'مقدم الخدمة': claim.providerName,
        الحالة: CLAIM_STATUS_LABELS[claim.status] || claim.status,
        'المبلغ المطلوب': claim.requestedAmount,
        'المبلغ المعتمد': claim._raw?.approvedAmount || '-',
        'تاريخ الخدمة': claim.serviceDate || '-',
        'آخر تحديث': claim.updatedAt ? new Date(claim.updatedAt).toLocaleDateString('en-GB') : '-'
      }));
      const timestamp = new Date().toISOString().slice(0, 10);
      exportToExcel(exportData, `تقرير_المطالبات_${timestamp}`, { companyName });
    } catch (err) {
      console.error('Failed to export Excel:', err);
    }
  };

  return (
    <MainCard sx={{ '& .MuiCardContent-root': { p: { xs: 1.5, md: 2.25 } } }}>
      <ModernPageHeader
        titleKey="تقرير المطالبات"
        titleIcon={<AssignmentIcon color="primary" />}
        subtitleKey="ملخص تشغيلي ومالي للمطالبات مع فلاتر وتحليل سريع"
        actions={
          <Stack direction="row" spacing={2} alignItems="center">
            <Chip label={`${totalCount} مطالبة`} size="small" color="primary" variant="outlined" />
            <Tooltip title="تصدير النتائج المحملة حاليًا إلى Excel">
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
            <Tooltip title="تحديث البيانات">
              <IconButton onClick={refetch} disabled={loading} color="primary">
                <RefreshIcon sx={{ fontSize: '1.25rem', animation: loading ? 'spin 1s linear infinite' : 'none' }} />
              </IconButton>
            </Tooltip>
          </Stack>
        }
      />

      <Paper
        variant="outlined"
        sx={{
          mt: 1,
          mb: 1.25,
          px: 1.5,
          py: 1,
          borderRadius: 1.5,
          bgcolor: '#fff',
          borderColor: 'divider'
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
          gap={1}
        >
          <Typography variant="body2" sx={{ fontWeight: 900, color: 'text.primary' }}>
            ملخص النتائج الحالية
          </Typography>
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            <Chip size="small" variant="outlined" label={`${formatNumber(totalCount)} مطالبة`} />
            <Chip size="small" variant="outlined" color="info" label={`المطلوب ${formatNumber(reportSummary.requested)} د.ل`} />
            <Chip size="small" variant="outlined" color="success" label={`المعتمد ${formatNumber(reportSummary.approved)} د.ل`} />
            <Chip size="small" variant="outlined" color="error" label={`الفرق ${formatNumber(reportSummary.rejected)} د.ل`} />
            <Chip size="small" variant="outlined" color="secondary" label={`اعتماد ${reportSummary.approvalRate.toFixed(1)}%`} />
          </Stack>
        </Stack>
      </Paper>

      {error && (
        <Alert severity="error" icon={<WarningIcon />} sx={{ mb: '1.0rem' }}>
          {error}
        </Alert>
      )}

      {hasPartialData && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          <Typography variant="body2">
            تم تحميل {formatNumber(totalFetched)} سجل من أصل {formatNumber(pagination.totalElements)} سجل.
          </Typography>
        </Alert>
      )}

      <Box sx={{ mt: '1.0rem' }}>
        <ClaimsFilters
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
      </Box>

      {!loading && totalFetched > 0 && (
        <Paper
          variant="outlined"
          sx={{
            mb: 0,
            px: 1.5,
            py: 0.8,
            borderRadius: '6px 6px 0 0',
            bgcolor: '#F8FBFA',
            borderColor: 'divider',
            borderBottom: 0
          }}
        >
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            gap={1}
          >
            <Typography variant="body2" color="text.secondary">
              إجمالي السجلات: <strong>{totalFetched}</strong>
            </Typography>
            <Divider flexItem orientation="vertical" sx={{ display: { xs: 'none', sm: 'block' } }} />
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              التفاصيل من أيقونة العين أو من سهم الصف.
            </Typography>
          </Stack>
        </Paper>
      )}

      <ClaimsTable
        claims={claims}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={handlePageChange}
        onRowsPerPageChange={handleRowsPerPageChange}
      />

      <style>{`@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }`}</style>
    </MainCard>
  );
};

export default ClaimsReport;
