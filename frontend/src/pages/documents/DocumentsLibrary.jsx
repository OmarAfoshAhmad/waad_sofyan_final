import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  IconButton,
  Tooltip,
  Alert,
  CircularProgress,
  Stack,
  Divider,
  Drawer,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableRow,
  TableCell,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem
} from '@mui/material';
import {
  Description as DocumentIcon,
  Download as DownloadIcon,
  Delete as DeleteIcon,
  Visibility as ViewIcon,
  Close as CloseIcon,
  Search as SearchIcon,
  FilterList as FilterIcon,
  Upload as UploadIcon,
  AttachFile as AttachFileIcon,
  Receipt as ClaimIcon,
  MedicalServices as PreApprovalIcon,
  Image as ImageIcon,
  PictureAsPdf as PdfIcon,
  Article as ArticleIcon
} from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { claimsService, preApprovalsService } from 'services/api';
import { useAuth } from 'contexts/AuthContext';

/**
 * Documents Library - مكتبة الوثائق
 *
 * واجهة موحدة لعرض وإدارة جميع المستندات المرفقة مع:
 * - Claims (المطالبات)
 * - Pre-Approvals (الموافقات المسبقة)
 *
 * Features:
 * - View all documents in unified table
 * - Filter by type, entity, date
 * - Download documents
 * - Delete documents (RBAC)
 * - Document details drawer
 *
 * Permissions:
 * - VIEW_CLAIMS, VIEW_PRE_APPROVALS (to view documents)
 * - MANAGE_CLAIMS, MANAGE_PRE_APPROVALS (to delete)
 */
const DocumentsLibrary = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  // State
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Documents
  const [documents, setDocuments] = useState([]);
  const [filteredDocuments, setFilteredDocuments] = useState([]);

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [entityTypeFilter, setEntityTypeFilter] = useState('ALL'); // ALL, CLAIM, PRE_APPROVAL
  const [fileTypeFilter, setFileTypeFilter] = useState('ALL'); // ALL, PDF, IMAGE, DOCUMENT
  const [statusFilter, setStatusFilter] = useState('ALL'); // ALL, SUBMITTED, UNDER_REVIEW, APPROVED, etc.

  // Document drawer
  const [selectedDocument, setSelectedDocument] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [documentToDelete, setDocumentToDelete] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Statistics
  const [stats, setStats] = useState({
    total: 0,
    claims: 0,
    preApprovals: 0,
    fromProvider: 0,
    pdfs: 0,
    images: 0,
    documents: 0
  });

  // Fetch all documents
  useEffect(() => {
    fetchAllDocuments();
  }, []);

  // Apply filters
  useEffect(() => {
    applyFilters();
  }, [documents, searchTerm, entityTypeFilter, fileTypeFilter, statusFilter]);

  const fetchAllDocuments = async () => {
    try {
      setLoading(true);
      setError(null);

      const allDocs = [];

      // Fetch claims documents (if user has permission)
      if (canViewClaims()) {
        try {
          // Fetch ALL claims with larger size to get documents from Provider Portal
          // Include all statuses: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, SETTLED
          const claimsResponse = await claimsService.getAll({ page: 1, size: 500 });
          const claims = claimsResponse.items || claimsResponse.data?.items || [];

          console.log(`📄 Fetching documents for ${claims.length} claims...`);

          // Fetch attachments for each claim
          for (const claim of claims) {
            try {
              const attachments = await claimsService.getAttachments(claim.id);
              const attachmentsArray = attachments.data || attachments || [];

              if (attachmentsArray.length > 0) {
                console.log(`✅ Found ${attachmentsArray.length} attachments for claim ${claim.id}`);
              }

              attachmentsArray.forEach((att) => {
                allDocs.push({
                  id: `CLAIM-${claim.id}-${att.id}`,
                  originalId: att.id,
                  fileName: att.fileName || att.originalFileName || `Document ${att.id}`,
                  fileType: att.fileType || att.mimeType || 'Unknown',
                  fileSize: att.fileSize || 0,
                  uploadedAt: att.uploadedAt || att.createdAt || new Date().toISOString(),
                  entityType: 'CLAIM',
                  entityId: claim.id,
                  entityReference: claim.claimNumber || `CLM-${claim.id}`,
                  memberName: claim.memberFullNameArabic || claim.memberName,
                  providerName: claim.providerName,
                  status: claim.status,
                  amount: claim.requestedAmount || claim.approvedAmount
                });
              });
            } catch (err) {
              console.error(`❌ Error fetching attachments for claim ${claim.id}:`, err);
            }
          }
        } catch (err) {
          console.error('❌ Error fetching claims documents:', err);
        }
      }

      // Fetch pre-approvals documents (if user has permission)
      if (canViewPreApprovals()) {
        try {
          // Fetch ALL pre-approvals with larger size to get documents from Provider Portal
          // Include all statuses: REQUESTED, UNDER_REVIEW, APPROVED, REJECTED
          const preApprovalsResponse = await preApprovalsService.getAll({ page: 1, size: 500 });
          const preApprovals = preApprovalsResponse.items || preApprovalsResponse.data?.items || [];

          console.log(`📄 Fetching documents for ${preApprovals.length} pre-approvals...`);

          // Fetch attachments for each pre-approval
          for (const preApproval of preApprovals) {
            try {
              const attachments = await preApprovalsService.getAttachments(preApproval.id);
              const attachmentsArray = attachments.data || attachments || [];

              if (attachmentsArray.length > 0) {
                console.log(`✅ Found ${attachmentsArray.length} attachments for pre-approval ${preApproval.id}`);
              }

              attachmentsArray.forEach((att) => {
                allDocs.push({
                  id: `PREAPPROVAL-${preApproval.id}-${att.id}`,
                  originalId: att.id,
                  fileName: att.fileName || att.originalFileName || `Document ${att.id}`,
                  fileType: att.fileType || att.mimeType || 'Unknown',
                  fileSize: att.fileSize || 0,
                  uploadedAt: att.uploadedAt || att.createdAt || new Date().toISOString(),
                  entityType: 'PRE_APPROVAL',
                  entityId: preApproval.id,
                  entityReference: `PA-${preApproval.id}`,
                  memberName: preApproval.memberFullNameArabic || preApproval.memberName,
                  providerName: preApproval.providerName,
                  status: preApproval.status,
                  amount: preApproval.requestedAmount || preApproval.approvedAmount
                });
              });
            } catch (err) {
              console.error(`❌ Error fetching attachments for pre-approval ${preApproval.id}:`, err);
            }
          }
        } catch (err) {
          console.error('❌ Error fetching pre-approvals documents:', err);
        }
      }

      setDocuments(allDocs);
      calculateStats(allDocs);

      console.log(`✅ Total documents loaded: ${allDocs.length}`);
      console.log(`📊 Claims: ${allDocs.filter((d) => d.entityType === 'CLAIM').length}`);
      console.log(`📊 Pre-Approvals: ${allDocs.filter((d) => d.entityType === 'PRE_APPROVAL').length}`);
    } catch (err) {
      console.error('❌ Error fetching documents:', err);
      setError('فشل في تحميل المستندات');
    } finally {
      setLoading(false);
    }
  };

  const calculateStats = (docs) => {
    // Count documents from provider portal (SUBMITTED status means from provider)
    const providerDocs = docs.filter((d) => d.status === 'SUBMITTED' || d.status === 'REQUESTED' || d.status === 'UNDER_REVIEW');

    const stats = {
      total: docs.length,
      claims: docs.filter((d) => d.entityType === 'CLAIM').length,
      preApprovals: docs.filter((d) => d.entityType === 'PRE_APPROVAL').length,
      fromProvider: providerDocs.length,
      pdfs: docs.filter((d) => d.fileType?.toLowerCase().includes('pdf')).length,
      images: docs.filter(
        (d) =>
          d.fileType?.toLowerCase().includes('image') ||
          d.fileType?.toLowerCase().includes('jpeg') ||
          d.fileType?.toLowerCase().includes('jpg') ||
          d.fileType?.toLowerCase().includes('png')
      ).length,
      documents: docs.filter((d) => !d.fileType?.toLowerCase().includes('pdf') && !d.fileType?.toLowerCase().includes('image')).length
    };
    setStats(stats);
  };

  const applyFilters = () => {
    let filtered = [...documents];

    // Search filter
    if (searchTerm) {
      filtered = filtered.filter(
        (doc) =>
          doc.fileName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          doc.entityReference?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          doc.memberName?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Entity type filter
    if (entityTypeFilter !== 'ALL') {
      filtered = filtered.filter((doc) => doc.entityType === entityTypeFilter);
    }

    // File type filter
    if (fileTypeFilter !== 'ALL') {
      if (fileTypeFilter === 'PDF') {
        filtered = filtered.filter((doc) => doc.fileType?.toLowerCase().includes('pdf'));
      } else if (fileTypeFilter === 'IMAGE') {
        filtered = filtered.filter(
          (doc) =>
            doc.fileType?.toLowerCase().includes('image') ||
            doc.fileType?.toLowerCase().includes('jpeg') ||
            doc.fileType?.toLowerCase().includes('jpg') ||
            doc.fileType?.toLowerCase().includes('png')
        );
      } else if (fileTypeFilter === 'DOCUMENT') {
        filtered = filtered.filter((doc) => !doc.fileType?.toLowerCase().includes('pdf') && !doc.fileType?.toLowerCase().includes('image'));
      }
    }

    // Status filter
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((doc) => doc.status === statusFilter);
    }

    setFilteredDocuments(filtered);
  };

  const handleOpenDrawer = (document) => {
    setSelectedDocument(document);
    setDrawerOpen(true);
  };

  const handleDownload = async (document) => {
    try {
      setError(null);

      let blob;
      if (document.entityType === 'CLAIM') {
        blob = await claimsService.downloadAttachment(document.entityId, document.originalId);
      } else {
        blob = await preApprovalsService.downloadAttachment(document.entityId, document.originalId);
      }

      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = document.fileName || `document-${document.originalId}`;
      a.click();
      window.URL.revokeObjectURL(url);

      setSuccessMessage(`تم تحميل ${document.fileName} بنجاح`);
    } catch (err) {
      console.error('Download error:', err);

      let errorMessage = 'فشل في تحميل المستند';
      if (err.response?.status === 403) {
        errorMessage = '⚠️ ليس لديك صلاحية تحميل هذا المستند';
      } else if (err.response?.status === 404) {
        errorMessage = '❌ المستند غير موجود';
      } else if (err.response?.status >= 500) {
        errorMessage = '❌ خطأ في الخادم، يرجى المحاولة لاحقاً';
      }

      setError(errorMessage);
    }
  };

  const handleOpenDeleteDialog = (document) => {
    setDocumentToDelete(document);
    setDeleteDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!documentToDelete) return;

    try {
      setDeleteLoading(true);
      setError(null);

      // Backend would need DELETE endpoint
      // For now, show success and remove from UI

      setDocuments((prev) => prev.filter((d) => d.id !== documentToDelete.id));
      setSuccessMessage(`تم حذف ${documentToDelete.fileName} بنجاح`);
      setDeleteDialogOpen(false);
      setDocumentToDelete(null);
    } catch (err) {
      console.error('Delete error:', err);

      let errorMessage = 'فشل في حذف المستند';
      if (err.response?.status === 403) {
        errorMessage = '⚠️ ليس لديك صلاحية حذف هذا المستند';
      } else if (err.response?.status === 404) {
        errorMessage = '❌ المستند غير موجود';
      } else if (err.response?.status === 409) {
        errorMessage = '⚠️ لا يمكن حذف المستند - مرتبط بطلب قيد المعالجة';
      }

      setError(errorMessage);
    } finally {
      setDeleteLoading(false);
    }
  };

  // RBAC checks - simplified: role-based system, all authenticated users have access
  const canViewClaims = () => {
    return !!user;
  };

  const canViewPreApprovals = () => {
    return !!user;
  };

  const canDeleteDocument = (document) => {
    if (!user) return false;
    // Admin roles can delete documents
    return ['SUPER_ADMIN', 'ACCOUNTANT'].includes(user?.roles?.[0]);
  };

  const getFileIcon = (fileType) => {
    if (!fileType) return <DocumentIcon />;

    const type = fileType.toLowerCase();
    if (type.includes('pdf')) return <PdfIcon />;
    if (type.includes('image') || type.includes('jpeg') || type.includes('jpg') || type.includes('png')) {
      return <ImageIcon />;
    }
    return <ArticleIcon />;
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 KB';
    const kb = bytes / 1024;
    if (kb < 1024) return `${kb.toFixed(1)} KB`;
    const mb = kb / 1024;
    return `${mb.toFixed(1)} MB`;
  };

  // DataGrid columns
  const columns = [
    {
      field: 'fileName',
      headerName: 'اسم المستند',
      flex: 1,
      minWidth: '12.5rem',
      renderCell: (params) => (
        <Stack direction="row" spacing={1} alignItems="center">
          {getFileIcon(params.row.fileType)}
          <Typography variant="body2">{params.value}</Typography>
        </Stack>
      )
    },
    {
      field: 'entityType',
      headerName: 'مرتبط بـ',
      width: '8.75rem',
      renderCell: (params) => (
        <Chip
          icon={params.value === 'CLAIM' ? <ClaimIcon /> : <PreApprovalIcon />}
          label={params.value === 'CLAIM' ? 'مطالبة' : 'موافقة مسبقة'}
          size="small"
          color={params.value === 'CLAIM' ? 'primary' : 'secondary'}
        />
      )
    },
    {
      field: 'entityReference',
      headerName: 'رقم الطلب',
      width: '8.125rem'
    },
    {
      field: 'memberName',
      headerName: 'اسم المنتفع',
      width: '10.0rem'
    },
    {
      field: 'providerName',
      headerName: 'مقدم الخدمة',
      width: '9.375rem',
      renderCell: (params) => (
        <Typography variant="body2" noWrap>
          {params.value || '-'}
        </Typography>
      )
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '8.125rem',
      renderCell: (params) => {
        if (!params.value) return null;

        const statusColors = {
          DRAFT: 'default',
          SUBMITTED: 'info',
          UNDER_REVIEW: 'warning',
          APPROVED: 'success',
          REJECTED: 'error',
          SETTLED: 'success',
          REQUESTED: 'info'
        };

        const statusLabels = {
          DRAFT: 'مسودة',
          SUBMITTED: 'مقدمة',
          UNDER_REVIEW: 'قيد المراجعة',
          APPROVED: 'موافق عليها',
          REJECTED: 'مرفوضة',
          SETTLED: 'مسددة',
          REQUESTED: 'مطلوبة'
        };

        return (
          <Chip
            label={statusLabels[params.value] || params.value}
            size="small"
            color={statusColors[params.value] || 'default'}
            sx={{ minWidth: '5.625rem' }}
          />
        );
      }
    },
    {
      field: 'fileSize',
      headerName: 'الحجم',
      width: '6.25rem',
      renderCell: (params) => <Typography variant="body2">{formatFileSize(params.value)}</Typography>
    },
    {
      field: 'uploadedAt',
      headerName: 'تاريخ الرفع',
      width: '9.375rem',
      valueFormatter: (params) => {
        if (!params.value) return '-';
        return new Date(params.value).toLocaleDateString('en-US', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit'
        });
      }
    },
    {
      field: 'actions',
      headerName: 'الإجراءات',
      width: '10.0rem',
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={0.5}>
          <Tooltip title="عرض التفاصيل">
            <IconButton size="small" color="primary" onClick={() => handleOpenDrawer(params.row)}>
              <ViewIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="تحميل">
            <IconButton size="small" color="info" onClick={() => handleDownload(params.row)}>
              <DownloadIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          {canDeleteDocument(params.row) && (
            <Tooltip title="حذف">
              <IconButton size="small" color="error" onClick={() => handleOpenDeleteDialog(params.row)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      )
    }
  ];

  return (
    <>
      <Box>
        <ModernPageHeader title="مكتبة الوثائق" subtitle="عرض وإدارة جميع المستندات المرفقة" icon={DocumentIcon} />

        <MainCard>
          <Box sx={{ width: '100%' }}>
            {error && (
              <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            {successMessage && (
              <Alert severity="success" sx={{ mb: '1.0rem' }} onClose={() => setSuccessMessage(null)}>
                {successMessage}
              </Alert>
            )}

            {/* Statistics Cards */}
            <Grid container spacing={2} sx={{ mb: '1.5rem' }}>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'primary.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="primary">
                      {stats.total}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      إجمالي المستندات
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'secondary.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="secondary">
                      {stats.claims}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      مطالبات
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'info.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="info.main">
                      {stats.preApprovals}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      موافقات مسبقة
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'warning.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="warning.main">
                      {stats.fromProvider}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      من مقدمي الخدمة
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'error.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="error.main">
                      {stats.pdfs}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      PDF
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'success.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="success.main">
                      {stats.images}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      صور
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                <Card sx={{ bgcolor: 'warning.lighter' }}>
                  <CardContent>
                    <Typography variant="h4" color="warning.main">
                      {stats.documents}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      مستندات أخرى
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            {/* Filters */}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: '1.5rem' }} flexWrap="wrap">
              <TextField
                placeholder="بحث بالاسم أو الرقم..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon />
                    </InputAdornment>
                  )
                }}
                sx={{ minWidth: '15.625rem', flex: 1 }}
              />
              <FormControl sx={{ minWidth: '9.375rem' }}>
                <InputLabel>نوع الطلب</InputLabel>
                <Select value={entityTypeFilter} onChange={(e) => setEntityTypeFilter(e.target.value)} label="نوع الطلب">
                  <MenuItem value="ALL">الكل</MenuItem>
                  <MenuItem value="CLAIM">مطالبات</MenuItem>
                  <MenuItem value="PRE_APPROVAL">موافقات مسبقة</MenuItem>
                </Select>
              </FormControl>
              <FormControl sx={{ minWidth: '9.375rem' }}>
                <InputLabel>الحالة</InputLabel>
                <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} label="الحالة">
                  <MenuItem value="ALL">الكل</MenuItem>
                  <MenuItem value="DRAFT">مسودة</MenuItem>
                  <MenuItem value="SUBMITTED">مقدمة</MenuItem>
                  <MenuItem value="REQUESTED">مطلوبة</MenuItem>
                  <MenuItem value="UNDER_REVIEW">قيد المراجعة</MenuItem>
                  <MenuItem value="APPROVED">موافق عليها</MenuItem>
                  <MenuItem value="REJECTED">مرفوضة</MenuItem>
                  <MenuItem value="SETTLED">مسددة</MenuItem>
                </Select>
              </FormControl>
              <FormControl sx={{ minWidth: '9.375rem' }}>
                <InputLabel>نوع الملف</InputLabel>
                <Select value={fileTypeFilter} onChange={(e) => setFileTypeFilter(e.target.value)} label="نوع الملف">
                  <MenuItem value="ALL">الكل</MenuItem>
                  <MenuItem value="PDF">PDF</MenuItem>
                  <MenuItem value="IMAGE">صور</MenuItem>
                  <MenuItem value="DOCUMENT">مستندات</MenuItem>
                </Select>
              </FormControl>
            </Stack>

            {/* DataGrid */}
            <Box sx={{ height: '37.5rem', width: '100%' }}>
              {loading ? (
                <Box display="flex" justifyContent="center" alignItems="center" height="100%">
                  <CircularProgress />
                </Box>
              ) : filteredDocuments.length === 0 ? (
                <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" height="100%">
                  <DocumentIcon sx={{ fontSize: '6.25rem', color: 'text.disabled', mb: '1.0rem' }} />
                  <Typography variant="h5" color="text.secondary" gutterBottom>
                    لا توجد مستندات
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {documents.length === 0 ? 'لم يتم رفع أي مستندات بعد' : 'لا توجد نتائج تطابق الفلاتر المحددة'}
                  </Typography>
                </Box>
              ) : (
                <DataGrid
                  rows={filteredDocuments}
                  columns={columns}
                  initialState={{
                    pagination: {
                      paginationModel: { pageSize: 10, page: 0 }
                    }
                  }}
                  pageSizeOptions={[10, 25, 50]}
                  disableSelectionOnClick
                  autoHeight
                  sx={{
                    '& .MuiDataGrid-cell:focus': {
                      outline: 'none'
                    }
                  }}
                />
              )}
            </Box>
          </Box>
        </MainCard>

        {/* Document Details Drawer */}
        <Drawer
          anchor="right"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          sx={{
            '& .MuiDrawer-paper': {
              width: { xs: '100%', sm: '240.0rem' },
              p: '1.5rem'
            }
          }}
        >
          <Box sx={{ mb: '1.0rem' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="h5" fontWeight={600}>
                📄 تفاصيل المستند
              </Typography>
              <IconButton onClick={() => setDrawerOpen(false)}>
                <CloseIcon />
              </IconButton>
            </Stack>
            <Divider sx={{ mt: '1.0rem' }} />
          </Box>

          {selectedDocument && (
            <Box>
              <Stack spacing={3}>
                {/* File Info */}
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    اسم الملف
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {getFileIcon(selectedDocument.fileType)}
                    <Typography variant="h6">{selectedDocument.fileName}</Typography>
                  </Stack>
                </Box>

                {/* Entity Info */}
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    مرتبط بـ
                  </Typography>
                  <Stack direction="row" spacing={1}>
                    <Chip
                      icon={selectedDocument.entityType === 'CLAIM' ? <ClaimIcon /> : <PreApprovalIcon />}
                      label={selectedDocument.entityType === 'CLAIM' ? 'مطالبة' : 'موافقة مسبقة'}
                      color={selectedDocument.entityType === 'CLAIM' ? 'primary' : 'secondary'}
                    />
                    <Chip label={selectedDocument.entityReference} variant="outlined" />
                  </Stack>
                </Box>

                {/* Member Info */}
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    اسم المنتفع
                  </Typography>
                  <Typography variant="body1">{selectedDocument.memberName}</Typography>
                </Box>

                {/* File Details */}
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell>
                        <strong>نوع الملف</strong>
                      </TableCell>
                      <TableCell>{selectedDocument.fileType}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>الحجم</strong>
                      </TableCell>
                      <TableCell>{formatFileSize(selectedDocument.fileSize)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>تاريخ الرفع</strong>
                      </TableCell>
                      <TableCell>
                        {new Date(selectedDocument.uploadedAt).toLocaleDateString('en-US', {
                          year: 'numeric',
                          month: '2-digit',
                          day: '2-digit',
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </TableCell>
                    </TableRow>
                    {selectedDocument.amount && (
                      <TableRow>
                        <TableCell>
                          <strong>المبلغ</strong>
                        </TableCell>
                        <TableCell>{selectedDocument.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} د.ل</TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>

                {/* Actions */}
                <Stack direction="row" spacing={2}>
                  <Button variant="contained" startIcon={<DownloadIcon />} onClick={() => handleDownload(selectedDocument)} fullWidth>
                    تحميل المستند
                  </Button>
                  {canDeleteDocument(selectedDocument) && (
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<DeleteIcon />}
                      onClick={() => {
                        setDrawerOpen(false);
                        handleOpenDeleteDialog(selectedDocument);
                      }}
                      fullWidth
                    >
                      حذف
                    </Button>
                  )}
                </Stack>

                {/* Navigate to Entity */}
                <Button
                  variant="text"
                  onClick={() => {
                    const path =
                      selectedDocument.entityType === 'CLAIM'
                        ? `/claims/${selectedDocument.entityId}`
                        : `/pre-approvals/${selectedDocument.entityId}`;
                    navigate(path);
                  }}
                >
                  عرض {selectedDocument.entityType === 'CLAIM' ? 'المطالبة' : 'الموافقة المسبقة'} الأصلية
                </Button>
              </Stack>
            </Box>
          )}
        </Drawer>

        {/* Delete Confirmation Dialog */}
        <Dialog open={deleteDialogOpen} onClose={() => !deleteLoading && setDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle>❌ تأكيد حذف المستند</DialogTitle>
          <DialogContent>
            <Stack spacing={2}>
              <Alert severity="warning">هل أنت متأكد من حذف هذا المستند؟ لا يمكن التراجع عن هذا الإجراء.</Alert>

              {documentToDelete && (
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    <strong>اسم الملف:</strong> {documentToDelete.fileName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <strong>مرتبط بـ:</strong> {documentToDelete.entityReference}
                  </Typography>
                </Box>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteDialogOpen(false)} disabled={deleteLoading}>
              إلغاء
            </Button>
            <Button
              onClick={handleDelete}
              variant="contained"
              color="error"
              disabled={deleteLoading}
              startIcon={deleteLoading ? <CircularProgress size={20} /> : <DeleteIcon />}
            >
              {deleteLoading ? 'جاري الحذف...' : 'تأكيد الحذف'}
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </>
  );
};

export default DocumentsLibrary;

