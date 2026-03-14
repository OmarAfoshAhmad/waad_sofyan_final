import { useState, useMemo, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  Grid,
  IconButton,
  LinearProgress,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  ClearAll as ClearAllIcon,
  Download as DownloadIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  FilterList as FilterListIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  VerifiedUser as PreAuthIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers';
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import PermissionGuard from 'components/PermissionGuard';
import axiosClient from 'utils/axios';
import { formatCurrency, formatDate } from 'utils/formatters';

/**
 * تقرير الموافقات المسبقة - بوابة مقدم الخدمة
 */
const ProviderPreAuthReport = () => {
  // ========================================
  // STATE
  // ========================================
  const [showFilters, setShowFilters] = useState(true);
  const [filters, setFilters] = useState({
    fromDate: null,
    toDate: null,
    status: '',
    memberBarcode: ''
  });

  const [paginationModel, setPaginationModel] = useState({
    page: 0,
    pageSize: 20
  });
  const [isExporting, setIsExporting] = useState(false);

  // ========================================
  // DATA FETCHING
  // ========================================
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-preauth-report', filters, paginationModel],
    queryFn: async () => {
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        page: paginationModel.page,
        size: paginationModel.pageSize,
        sortBy: 'requestDate',
        sortDir: 'DESC',
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };
      const response = await axiosClient.get('/api/v1/provider/reports/pre-auth', { params });
      return response?.data?.data ?? response?.data ?? { content: [], totalElements: 0 };
    }
  });

  const preAuthData = useMemo(() => data?.content || [], [data]);
  const totalElements = data?.totalElements || 0;

  // ========================================
  // HANDLERS
  // ========================================
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setPaginationModel((prev) => ({ ...prev, page: 0 }));
  };

  const handleClearFilters = () => {
    setFilters({
      fromDate: null,
      toDate: null,
      status: '',
      memberBarcode: ''
    });
    setPaginationModel((prev) => ({ ...prev, page: 0 }));
  };

  const hasActiveFilters = useMemo(() => {
    return filters.fromDate || filters.toDate || filters.status || filters.memberBarcode;
  }, [filters]);

  const handleExportExcel = async () => {
    try {
      setIsExporting(true);
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };

      const response = await axiosClient.get('/api/v1/provider/reports/pre-auth/export', {
        params,
        responseType: 'blob'
      });

      const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
      const link = window.document.createElement('a');
      link.href = blobUrl;
      link.setAttribute('download', `preauth_report_${new Date().toISOString().slice(0, 10)}.xlsx`);
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (exportError) {
      console.error('Export pre-auth report failed:', exportError);
      alert('فشل تصدير تقرير الموافقات المسبقة');
    } finally {
      setIsExporting(false);
    }
  };

  // ========================================
  // TABLE COLUMNS
  // ========================================
  const columns = useMemo(
    () => [
      {
        id: 'preAuthNumber',
        label: 'رقم الموافقة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'requestDate',
        label: 'تاريخ الطلب',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'memberName',
        label: 'اسم المنتفع',
        minWidth: '11.25rem',
        sortable: false
      },
      {
        id: 'memberBarcode',
        label: 'الباركود',
        minWidth: '8.125rem',
        sortable: false
      },
      {
        id: 'serviceName',
        label: 'الخدمة',
        minWidth: '12.5rem',
        sortable: false
      },
      {
        id: 'sessionsRequested',
        label: 'الجلسات المطلوبة',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'sessionsApproved',
        label: 'الجلسات الموافقة',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'sessionsUsed',
        label: 'المستخدم',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'requestedAmount',
        label: 'المبلغ المطلوب',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'approvedAmount',
        label: 'المبلغ الموافق',
        minWidth: '8.125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '8.75rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================
  const getStatusChip = (status, label) => {
    const colors = {
      PENDING: 'warning',
      APPROVED: 'success',
      REJECTED: 'error',
      EXPIRED: 'default',
      CANCELLED: 'default'
    };

    return <Chip label={label} color={colors[status] || 'default'} size="small" sx={{ fontWeight: 600 }} />;
  };

  const renderCell = useCallback((preAuth, column) => {
    if (!preAuth) return null;

    switch (column.id) {
      case 'preAuthNumber':
        return (
          <Typography variant="body2" fontWeight={600}>
            {preAuth.preAuthNumber || '-'}
          </Typography>
        );

      case 'requestDate':
        return formatDate(preAuth.requestDate);

      case 'memberName':
        return preAuth.memberName || '-';

      case 'memberBarcode':
        return preAuth.memberBarcode || '-';

      case 'serviceName':
        return preAuth.serviceName || '-';

      case 'sessionsRequested':
        return preAuth.sessionsRequested || 0;

      case 'sessionsApproved':
        return (
          <Typography variant="body2" color="success.main" fontWeight={600}>
            {preAuth.sessionsApproved || 0}
          </Typography>
        );

      case 'sessionsUsed':
        const used = preAuth.sessionsUsed || 0;
        const approved = preAuth.sessionsApproved || 0;
        const percentage = approved > 0 ? (used / approved) * 100 : 0;

        return (
          <Box sx={{ width: '100%' }}>
            <Typography variant="caption" display="block" align="center">
              {used} / {approved}
            </Typography>
            <LinearProgress variant="determinate" value={percentage} sx={{ height: '0.375rem', borderRadius: '0.125rem' }} />
          </Box>
        );

      case 'requestedAmount':
        return formatCurrency(preAuth.requestedAmount);

      case 'approvedAmount':
        return (
          <Typography variant="body2" color="success.main" fontWeight={600}>
            {formatCurrency(preAuth.approvedAmount)}
          </Typography>
        );

      case 'status':
        return getStatusChip(preAuth.status, preAuth.statusLabel);

      default:
        return '-';
    }
  }, []);

  // ========================================
  // BREADCRUMBS
  // ========================================
  const breadcrumbs = [
    { label: 'بوابة مقدم الخدمة', path: '/provider' },
    { label: 'التقارير', path: '/provider/reports' },
    { label: 'الموافقات المسبقة' }
  ];

  // ========================================
  // PAGE ACTIONS
  // ========================================
  const pageActions = (
    <Stack direction="row" spacing={1}>
      <Button variant="outlined" startIcon={<DownloadIcon />} onClick={handleExportExcel} disabled={isExporting || isLoading}>
        {isExporting ? 'جاري التصدير...' : 'تصدير Excel'}
      </Button>
      <Tooltip title="تحديث">
        <IconButton onClick={() => refetch()} color="primary" disabled={isLoading}>
          <RefreshIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  );

  // ========================================
  // RENDER
  // ========================================
  return (
    <PermissionGuard resource="provider_portal" action="view" fallback={<Alert severity="error">ليس لديك صلاحية لعرض هذه الصفحة</Alert>}>
      <Box>
        {/* Page Header */}
        <UnifiedPageHeader
          title="تقرير الموافقات المسبقة"
          subtitle="جميع طلبات الموافقات المسبقة"
          breadcrumbs={breadcrumbs}
          icon={PreAuthIcon}
          actions={pageActions}
        />

        {/* Error Alert */}
        {isError && (
          <Alert severity="error" sx={{ mb: '1.0rem' }}>
            {error?.message || 'حدث خطأ أثناء تحميل البيانات'}
          </Alert>
        )}

        {/* Filters */}
        <MainCard sx={{ mb: '1.5rem' }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: showFilters ? 2 : 0 }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <FilterListIcon color="action" />
              <Typography variant="h6">البحث والفلترة</Typography>
            </Stack>
            <IconButton onClick={() => setShowFilters(!showFilters)} size="small">
              {showFilters ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            </IconButton>
          </Stack>

          <Collapse in={showFilters}>
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, md: 3 }}>
                <DatePicker
                  label="من تاريخ"
                  value={filters.fromDate}
                  onChange={(value) => handleFilterChange('fromDate', value)}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'small'
                    }
                  }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 3 }}>
                <DatePicker
                  label="إلى تاريخ"
                  value={filters.toDate}
                  onChange={(value) => handleFilterChange('toDate', value)}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'small'
                    }
                  }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <TextField
                  fullWidth
                  select
                  label="الحالة"
                  value={filters.status}
                  onChange={(e) => handleFilterChange('status', e.target.value)}
                  size="small"
                >
                  <MenuItem value="">الكل</MenuItem>
                  <MenuItem value="PENDING">قيد الانتظار</MenuItem>
                  <MenuItem value="APPROVED">موافق عليها</MenuItem>
                  <MenuItem value="REJECTED">مرفوضة</MenuItem>
                  <MenuItem value="EXPIRED">منتهية الصلاحية</MenuItem>
                  <MenuItem value="CANCELLED">ملغاة</MenuItem>
                </TextField>
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <TextField
                  fullWidth
                  label="الباركود"
                  value={filters.memberBarcode}
                  onChange={(e) => handleFilterChange('memberBarcode', e.target.value)}
                  size="small"
                  placeholder="اكتب باركود المنتفع"
                />
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<SearchIcon />}
                    onClick={() => refetch()}
                    disabled={isLoading}
                    fullWidth
                  >
                    بحث
                  </Button>
                  <Button variant="outlined" startIcon={<ClearAllIcon />} onClick={handleClearFilters} disabled={!hasActiveFilters}>
                    مسح
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </Collapse>
        </MainCard>

        {/* Data Table */}
        <MainCard>
          <UnifiedMedicalTable
            columns={columns}
            rows={preAuthData}
            loading={isLoading}
            renderCell={renderCell}
            totalCount={totalElements}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(newPageSize) => setPaginationModel({ page: 0, pageSize: newPageSize })}
            emptyIcon={PreAuthIcon}
            emptyMessage="لا توجد طلبات موافقات مسبقة مسجلة حالياً"
          />
        </MainCard>
      </Box>
    </PermissionGuard>
  );
};

export default ProviderPreAuthReport;

