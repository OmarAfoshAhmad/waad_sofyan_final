import { useState, useMemo, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Badge,
  Box,
  Button,
  Chip,
  Collapse,
  Grid,
  IconButton,
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
  LocalHospital as VisitIcon,
  Receipt as ReceiptIcon,
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
 * تقرير سجل الزيارات - بوابة مقدم الخدمة
 */
const ProviderVisitsReport = () => {
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
    queryKey: ['provider-visits-report', filters, paginationModel],
    queryFn: async () => {
      const fromDate = filters.fromDate?.format ? filters.fromDate.format('YYYY-MM-DD') : filters.fromDate;
      const toDate = filters.toDate?.format ? filters.toDate.format('YYYY-MM-DD') : filters.toDate;
      const status = typeof filters.status === 'string' ? filters.status.trim().toUpperCase() : filters.status;

      const params = {
        page: paginationModel.page,
        size: paginationModel.pageSize,
        sortBy: 'visitDate',
        sortDir: 'DESC',
        ...(fromDate && { fromDate }),
        ...(toDate && { toDate }),
        ...(status && { status }),
        ...(filters.memberBarcode && { memberBarcode: filters.memberBarcode })
      };
      const response = await axiosClient.get('/api/v1/provider/reports/visits', { params });
      return response?.data?.data ?? response?.data ?? { content: [], totalElements: 0 };
    }
  });

  const visitsData = useMemo(() => data?.content || [], [data]);
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

      const response = await axiosClient.get('/api/v1/provider/reports/visits/export', {
        params,
        responseType: 'blob'
      });

      const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
      const link = window.document.createElement('a');
      link.href = blobUrl;
      link.setAttribute('download', `visits_report_${new Date().toISOString().slice(0, 10)}.xlsx`);
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (exportError) {
      console.error('Export visits report failed:', exportError);
      alert('فشل تصدير تقرير الزيارات');
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
        id: 'visitNumber',
        label: 'رقم الزيارة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'visitDate',
        label: 'تاريخ الزيارة',
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
        id: 'employerName',
        label: 'الشركة',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'visitType',
        label: 'نوع الزيارة',
        minWidth: '8.75rem',
        sortable: false
      },
      {
        id: 'chiefComplaint',
        label: 'الشكوى الرئيسية',
        minWidth: '12.5rem',
        sortable: false
      },
      {
        id: 'claimCount',
        label: 'المطالبات',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'preAuthCount',
        label: 'الموافقات',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'totalAmount',
        label: 'إجمالي المبلغ',
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
      REGISTERED: 'info',
      IN_PROGRESS: 'warning',
      COMPLETED: 'success',
      CANCELLED: 'default'
    };

    return <Chip label={label} color={colors[status] || 'default'} size="small" sx={{ fontWeight: 600 }} />;
  };

  const getVisitTypeChip = (type, label) => {
    const colors = {
      EMERGENCY: 'error',
      OUTPATIENT: 'primary',
      INPATIENT: 'secondary',
      FOLLOWUP: 'info'
    };

    return <Chip label={label} color={colors[type] || 'default'} size="small" variant="outlined" />;
  };

  const renderCell = useCallback((visit, column) => {
    if (!visit) return null;

    switch (column.id) {
      case 'visitNumber':
        return (
          <Typography variant="body2" fontWeight={600}>
            {visit.visitNumber || '-'}
          </Typography>
        );

      case 'visitDate':
        return formatDate(visit.visitDate);

      case 'memberName':
        return visit.memberName || '-';

      case 'memberBarcode':
        return visit.memberBarcode || '-';

      case 'employerName':
        return visit.employerName || '-';

      case 'visitType':
        return getVisitTypeChip(visit.visitType, visit.visitTypeLabel);

      case 'chiefComplaint':
        return (
          <Typography variant="body2" noWrap>
            {visit.chiefComplaint || '-'}
          </Typography>
        );

      case 'claimCount':
        return (
          <Badge badgeContent={visit.claimCount || 0} color="primary">
            <ReceiptIcon fontSize="small" />
          </Badge>
        );

      case 'preAuthCount':
        return (
          <Badge badgeContent={visit.preAuthCount || 0} color="secondary">
            <PreAuthIcon fontSize="small" />
          </Badge>
        );

      case 'totalAmount':
        return formatCurrency(visit.totalAmount);

      case 'status':
        return getStatusChip(visit.status, visit.statusLabel);

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
    { label: 'سجل الزيارات' }
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
          title="تقرير سجل الزيارات"
          subtitle="جميع الزيارات المسجلة في مقدم الخدمة"
          breadcrumbs={breadcrumbs}
          icon={VisitIcon}
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
                  <MenuItem value="REGISTERED">مسجلة</MenuItem>
                  <MenuItem value="IN_PROGRESS">قيد الإجراء</MenuItem>
                  <MenuItem value="COMPLETED">مكتملة</MenuItem>
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
            rows={visitsData}
            loading={isLoading}
            renderCell={renderCell}
            totalCount={totalElements}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(newPageSize) => setPaginationModel({ page: 0, pageSize: newPageSize })}
            emptyIcon={VisitIcon}
            emptyMessage="لا توجد زيارات مسجلة حالياً"
          />
        </MainCard>
      </Box>
    </PermissionGuard>
  );
};

export default ProviderVisitsReport;
