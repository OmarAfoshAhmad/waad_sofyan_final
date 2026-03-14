import { useState, useEffect, useCallback, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
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
  CloudUpload as UploadIcon,
  Description as DocumentIcon,
  Download as DownloadIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  FilterList as FilterListIcon,
  Folder as FolderIcon,
  Print as PrintIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  Visibility as ViewIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';

import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import PermissionGuard from 'components/PermissionGuard';
import { DocumentPreviewDrawer } from 'components/tba/documents';
import axiosClient from 'utils/axios';

// ==================== CONSTANTS ====================

const REFERENCE_TYPES = [
  { value: '', label: 'الكل' },
  { value: 'VISIT', label: 'زيارة' },
  { value: 'PRE_AUTH', label: 'موافقة مسبقة' },
  { value: 'CLAIM', label: 'مطالبة' }
];

const STATUSES = [
  { value: '', label: 'الكل', color: 'default' },
  { value: 'REQUIRED', label: 'مطلوب', color: 'warning' },
  { value: 'UPLOADED', label: 'مرفوع', color: 'info' },
  { value: 'APPROVED', label: 'مقبول', color: 'success' },
  { value: 'REJECTED', label: 'مرفوض', color: 'error' }
];

// ==================== STATUS CHIP COMPONENT ====================

const StatusChip = ({ status }) => {
  const statusConfig = STATUSES.find((s) => s.value === status) || { label: status, color: 'default' };
  return <Chip label={statusConfig.label} color={statusConfig.color} size="small" sx={{ minWidth: '4.375rem', fontWeight: 500 }} />;
};

// ==================== REFERENCE TYPE CHIP ====================

const ReferenceTypeChip = ({ type }) => {
  const typeConfig = REFERENCE_TYPES.find((t) => t.value === type) || { label: type };
  const colors = {
    VISIT: 'primary',
    PRE_AUTH: 'secondary',
    CLAIM: 'warning'
  };
  return <Chip label={typeConfig.label || type} color={colors[type] || 'default'} size="small" variant="outlined" sx={{ minWidth: '5.0rem' }} />;
};

// ==================== MAIN COMPONENT ====================

/**
 * مستندات مقدم الخدمة - Provider Documents
 * عرض جميع المستندات المرفوعة والمستلمة مع مرجعياتها (زيارات، موافقات، مطالبات)
 */
const ProviderDocuments = () => {
  // ========================================
  // STATE
  // ========================================
  const [showFilters, setShowFilters] = useState(true);
  const [filters, setFilters] = useState({
    referenceType: '',
    status: '',
    fromDate: null,
    toDate: null
  });

  const [paginationModel, setPaginationModel] = useState({
    page: 0,
    pageSize: 20
  });

  // Preview Drawer State
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewDocument, setPreviewDocument] = useState(null);

  // ========================================
  // DATA FETCHING
  // ========================================
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['provider-documents', filters, paginationModel],
    queryFn: async () => {
      const params = {
        page: paginationModel.page,
        size: paginationModel.pageSize,
        ...(filters.referenceType && { referenceType: filters.referenceType }),
        ...(filters.status && { status: filters.status }),
        ...(filters.fromDate && { fromDate: dayjs(filters.fromDate).format('YYYY-MM-DD') }),
        ...(filters.toDate && { toDate: dayjs(filters.toDate).format('YYYY-MM-DD') })
      };
      const response = await axiosClient.get('/api/v1/provider/documents', { params });
      return response.data.data;
    }
  });

  // Fetch Statistics
  const { data: stats } = useQuery({
    queryKey: ['provider-documents-stats'],
    queryFn: async () => {
      const response = await axiosClient.get('/api/v1/provider/documents/stats');
      return response.data.data;
    }
  });

  const documentsData = useMemo(() => data?.content || [], [data]);
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
      referenceType: '',
      status: '',
      fromDate: null,
      toDate: null
    });
    setPaginationModel((prev) => ({ ...prev, page: 0 }));
  };

  const hasActiveFilters = useMemo(() => {
    return filters.referenceType || filters.status || filters.fromDate || filters.toDate;
  }, [filters]);

  // ========================================
  // TABLE COLUMNS
  // ========================================
  const columns = useMemo(
    () => [
      {
        id: 'referenceType',
        label: 'نوع المرجع',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'referenceNumber',
        label: 'رقم المرجع',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'documentType',
        label: 'نوع المستند',
        minWidth: '11.25rem',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '6.875rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'uploadedAt',
        label: 'تاريخ الرفع',
        minWidth: '9.375rem',
        sortable: false
      },
      {
        id: 'rejectionReason',
        label: 'سبب الرفض',
        minWidth: '11.25rem',
        sortable: false
      },
      {
        id: 'actions',
        label: 'إجراءات',
        minWidth: '9.375rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================
  const getStatusChip = (status) => {
    const statusConfig = STATUSES.find((s) => s.value === status) || { label: status, color: 'default' };
    return <Chip label={statusConfig.label} color={statusConfig.color} size="small" sx={{ minWidth: '4.375rem', fontWeight: 500 }} />;
  };

  const getReferenceTypeChip = (type) => {
    const typeConfig = REFERENCE_TYPES.find((t) => t.value === type) || { label: type };
    const colors = {
      VISIT: 'primary',
      PRE_AUTH: 'secondary',
      CLAIM: 'warning'
    };
    return (
      <Chip label={typeConfig.label || type} color={colors[type] || 'default'} size="small" variant="outlined" sx={{ minWidth: '5.0rem' }} />
    );
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    return dayjs(dateStr).format('YYYY-MM-DD HH:mm');
  };

  const resolveDocumentUrl = (downloadUrl) => {
    if (!downloadUrl) return null;
    if (/^https?:\/\//i.test(downloadUrl)) return downloadUrl;

    const baseUrl = (axiosClient?.defaults?.baseURL || '').replace(/\/+$/, '');
    const cleanPath = String(downloadUrl)
      .replace(/^https?:\/\/[^/]+/i, '')
      .replace(/^\/+/, '/');

    const normalizedPath = cleanPath
      .replace(/^\/api\/v1\//, '/')
      .replace(/^\/api\//, '/');

    return `${baseUrl}${normalizedPath}`;
  };

  const handleDownload = async (document) => {
    try {
      const downloadRequestUrl = resolveDocumentUrl(document.downloadUrl);
      if (!downloadRequestUrl) {
        alert('رابط تحميل المستند غير متوفر');
        return;
      }

      const response = await axiosClient.get(downloadRequestUrl, {
        responseType: 'blob'
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = window.document.createElement('a');
      link.href = url;
      link.setAttribute('download', document.fileName || 'document');
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Download error:', err);
      alert('فشل تحميل الملف');
    }
  };

  const handleView = (document) => {
    // Open side preview drawer instead of new tab
    setPreviewDocument(document);
    setPreviewOpen(true);
  };

  const handleClosePreview = () => {
    setPreviewOpen(false);
    setPreviewDocument(null);
  };

  const handlePrint = (document) => {
    const printFile = async () => {
      try {
        const downloadRequestUrl = resolveDocumentUrl(document.downloadUrl);
        if (!downloadRequestUrl) {
          alert('رابط طباعة المستند غير متوفر');
          return;
        }

        const response = await axiosClient.get(downloadRequestUrl, {
          responseType: 'blob'
        });

        const blob = new Blob([response.data], { type: document.fileType || 'application/pdf' });
        const blobUrl = window.URL.createObjectURL(blob);
        const printWindow = window.open(blobUrl, '_blank');

        if (!printWindow) {
          window.URL.revokeObjectURL(blobUrl);
          alert('تعذر فتح نافذة الطباعة. تحقق من السماح بالنوافذ المنبثقة.');
          return;
        }

        const cleanup = () => window.URL.revokeObjectURL(blobUrl);
        printWindow.addEventListener('beforeunload', cleanup, { once: true });

        setTimeout(() => {
          try {
            printWindow.focus();
            printWindow.print();
          } catch (e) {
            console.error('Print error:', e);
          }
        }, 500);
      } catch (err) {
        console.error('Print error:', err);
        alert('فشل طباعة الملف');
      }
    };

    printFile();
  };

  const renderCell = useCallback((doc, column) => {
    if (!doc) return null;

    switch (column.id) {
      case 'referenceType':
        return getReferenceTypeChip(doc.referenceType);

      case 'referenceNumber':
        return (
          <Box>
            <Typography variant="body2" fontWeight={500}>
              {doc.referenceNumber}
            </Typography>
            {doc.memberName && (
              <Typography variant="caption" color="textSecondary">
                {doc.memberName}
              </Typography>
            )}
          </Box>
        );

      case 'documentType':
        return (
          <Stack direction="row" alignItems="center" spacing={0.5}>
            <DocumentIcon fontSize="small" color="action" />
            <Box>
              <Typography variant="body2">{doc.documentTypeLabel || doc.documentType}</Typography>
              {doc.fileName && (
                <Typography variant="caption" color="textSecondary" noWrap sx={{ maxWidth: '9.375rem', display: 'block' }}>
                  {doc.fileName}
                </Typography>
              )}
            </Box>
          </Stack>
        );

      case 'status':
        return getStatusChip(doc.status);

      case 'uploadedAt':
        return (
          <Box>
            <Typography variant="body2">{formatDate(doc.uploadedAt)}</Typography>
            {doc.fileSize && (
              <Typography variant="caption" color="textSecondary">
                {formatFileSize(doc.fileSize)}
              </Typography>
            )}
          </Box>
        );

      case 'rejectionReason':
        return doc.rejectionReason ? (
          <Tooltip title={doc.rejectionReason}>
            <Typography
              variant="body2"
              color="error"
              sx={{
                maxWidth: '11.25rem',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap'
              }}
            >
              {doc.rejectionReason}
            </Typography>
          </Tooltip>
        ) : (
          <Typography variant="body2" color="textSecondary">
            -
          </Typography>
        );

      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            {doc.status === 'UPLOADED' || doc.status === 'APPROVED' ? (
              <>
                <Tooltip title="عرض">
                  <IconButton size="small" color="primary" onClick={() => handleView(doc)}>
                    <ViewIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="تحميل">
                  <IconButton size="small" color="info" onClick={() => handleDownload(doc)}>
                    <DownloadIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                {doc.fileType === 'application/pdf' && (
                  <Tooltip title="طباعة">
                    <IconButton size="small" color="secondary" onClick={() => handlePrint(doc)}>
                      <PrintIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </>
            ) : doc.status === 'REQUIRED' || doc.status === 'REJECTED' ? (
              <Tooltip title="رفع مستند">
                <Button size="small" variant="contained" color="primary" startIcon={<UploadIcon />} sx={{ whiteSpace: 'nowrap' }}>
                  رفع
                </Button>
              </Tooltip>
            ) : null}
          </Stack>
        );

      default:
        return '-';
    }
  }, []);

  // ========================================
  // BREADCRUMBS
  // ========================================
  const breadcrumbs = [{ label: 'بوابة مقدم الخدمة', path: '/provider' }, { label: 'المستندات' }];

  // ========================================
  // PAGE ACTIONS
  // ========================================
  const pageActions = (
    <Stack direction="row" spacing={1}>
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
    <PermissionGuard resource="documents" action="view" fallback={<Alert severity="error">ليس لديك صلاحية لعرض هذه الصفحة</Alert>}>
      <Box>
        {/* Page Header */}
        <UnifiedPageHeader
          title="مستندات مقدم الخدمة"
          subtitle="جميع المستندات المرفوعة والمستلمة المرتبطة بالزيارات والموافقات والمطالبات"
          breadcrumbs={breadcrumbs}
          icon={FolderIcon}
          actions={pageActions}
        />

        {/* Error Alert */}
        {isError && (
          <Alert severity="error" sx={{ mb: '1.0rem' }}>
            {error?.message || 'حدث خطأ أثناء تحميل البيانات'}
          </Alert>
        )}

        {/* Statistics Cards */}
        {stats && (
          <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center', py: '1.0rem' }}>
                  <Typography variant="h4" color="primary">
                    {stats.totalDocuments || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    إجمالي المستندات
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center', py: '1.0rem' }}>
                  <Typography variant="h4" color="info.main">
                    {stats.visitDocuments || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    مستندات الزيارات
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center', py: '1.0rem' }}>
                  <Typography variant="h4" color="secondary.main">
                    {stats.preAuthDocuments || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    مستندات الموافقات
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center', py: '1.0rem' }}>
                  <Typography variant="h4" color="warning.main">
                    {stats.claimDocuments || 0}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    مستندات المطالبات
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
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
                <TextField
                  fullWidth
                  select
                  label="نوع المرجع"
                  value={filters.referenceType}
                  onChange={(e) => handleFilterChange('referenceType', e.target.value)}
                  size="small"
                >
                  {REFERENCE_TYPES.map((type) => (
                    <MenuItem key={type.value} value={type.value}>
                      {type.label}
                    </MenuItem>
                  ))}
                </TextField>
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
                  {STATUSES.map((s) => (
                    <MenuItem key={s.value} value={s.value}>
                      {s.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>

              <Grid size={{ xs: 12, md: 2 }}>
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

              <Grid size={{ xs: 12, md: 2 }}>
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

              <Grid size={{ xs: 12, md: 3 }}>
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
            rows={documentsData}
            loading={isLoading}
            error={isError ? error : null}
            onErrorClose={() => {}}
            renderCell={renderCell}
            totalCount={totalElements}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(newSize) => setPaginationModel({ page: 0, pageSize: newSize })}
            emptyStateConfig={{
              icon: DocumentIcon,
              title: 'لا توجد مستندات',
              description: 'لا توجد مستندات مسجلة حالياً'
            }}
          />
        </MainCard>

        {/* Document Preview Drawer */}
        <DocumentPreviewDrawer
          open={previewOpen}
          onClose={handleClosePreview}
          documentUrl={previewDocument?.downloadUrl ? resolveDocumentUrl(previewDocument.downloadUrl) : null}
          fileName={previewDocument?.fileName}
          mimeType={previewDocument?.fileType}
          fileSize={previewDocument?.fileSize}
          documentTitle={previewDocument?.documentTypeLabel || previewDocument?.documentType}
          onDownload={() => previewDocument && handleDownload(previewDocument)}
          showDownload={true}
        />
      </Box>
    </PermissionGuard>
  );
};

export default ProviderDocuments;

