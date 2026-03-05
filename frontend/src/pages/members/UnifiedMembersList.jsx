/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNIFIED MEMBERS LIST - FINAL STANDARD TABLE DESIGN
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Displays all members (Principals and Dependents) with pagination, sorting, and filtering.
 * Based on: "Visits Log" table reference design
 *
 * MANDATORY STANDARDS:
 * ✅ UnifiedMedicalTable component
 * ✅ Soft medical green header (#E8F5F1)
 * ✅ Full-width table (100%)
 * ✅ Filters ABOVE table only
 * ✅ Sort arrows in header cells
 * ✅ Desktop-first professional design
 * ✅ No MUI DataGrid
 *
 * @module UnifiedMembersList
 * @version 2.0.0 - Final UI Standard
 * @since 2026-02-08
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
  Tooltip,
  InputAdornment,
  Collapse,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  DialogContentText
} from '@mui/material';
import {
  Add as AddIcon,
  Visibility as VisibilityIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  FilterList as FilterListIcon,
  FilterAltOff as FilterAltOffIcon,
  Person as PersonIcon,
  CreditCard as CreditCardIcon,
  Business as BusinessIcon,
  Star as VIPIcon,
  Bolt as FlashIcon,
  CheckCircle as CheckCircleIcon,
  Search as SearchIcon,
  Close as CloseIcon,
  UploadFile as UploadFileIcon,
  Download as DownloadIcon,
  FileDownload as FileDownloadIcon,
  Undo as UndoIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import { ModernPageHeader, MemberAvatar, SoftDeleteToggle } from 'components/tba';
import { UnifiedMedicalTable } from 'components/common';
import MembersBulkUploadDialog from 'components/members/MembersBulkUploadDialog';
import DataExportWizard from 'components/tba/DataExportWizard';
import {
  searchMembers,
  downloadTemplate,
  exportMembers,
  deleteMember,
  restoreMember,
  hardDeleteMember,
  MEMBER_TYPES,
  MEMBER_STATUSES
} from 'services/api/unified-members.service';
import axiosClient from 'utils/axios';
import { RELATIONSHIP_CONFIG } from 'components/insurance/MemberTypeIndicator';

/**
 * Unified Members List Component
 */
const UnifiedMembersList = () => {
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  // ════════════════════════════════════════════════════════════════════════
  // STATE
  // ════════════════════════════════════════════════════════════════════════
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalCount, setTotalCount] = useState(0);

  // Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Sorting
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDirection, setSortDirection] = useState('desc');

  // Filters
  const [showDeleted, setShowDeleted] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filters, setFilters] = useState({
    organizationId: '',
    type: '',
    status: ''
  });

  // Import/Export Dialogs
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [exportWizardOpen, setExportWizardOpen] = useState(false);

  // Confirmation Dialog
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    title: '',
    content: '',
    severity: 'warning',
    confirmText: 'نعم',
    cancelText: 'إلغاء',
    onConfirm: null
  });

  // Lookups
  const [employers, setEmployers] = useState([]);

  // ════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ════════════════════════════════════════════════════════════════════════
  const fetchMembers = async () => {
    setLoading(true);
    try {
      const hasSearch = !!searchTerm.trim();
      const params = {
        page,
        size: rowsPerPage,
        sort: sortBy,
        direction: sortDirection.toUpperCase(),
        deleted: showDeleted,
        ...(filters.organizationId && { employerId: filters.organizationId }),
        ...(filters.type && { type: filters.type }),
        ...(filters.status && { status: filters.status }),
        ...(hasSearch && { fullName: searchTerm.trim() })
      };

      const response = await searchMembers(params);
      const pageData = response?.data || response;

      setMembers(pageData?.content || []);
      setTotalCount(pageData?.totalElements || 0);
    } catch (error) {
      console.error('Error fetching members:', error);
      enqueueSnackbar('خطأ في جلب المستفيدين', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const fetchEmployers = async () => {
    try {
      const response = await axiosClient.get('/employers/selectors');
      setEmployers(response.data?.data || []);
    } catch (error) {
      console.error('Error fetching employers:', error);
    }
  };

  useEffect(() => {
    fetchEmployers();
  }, []);

  useEffect(() => {
    fetchMembers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, rowsPerPage, filters, searchTerm, sortBy, sortDirection, showDeleted]);

  // ════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ════════════════════════════════════════════════════════════════════════
  const handleSort = (columnId, direction) => {
    setSortBy(columnId);
    setSortDirection(direction);
    setPage(0);
  };

  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setPage(0);
  };

  const handleResetFilters = () => {
    setFilters({ organizationId: '', type: '', status: '' });
    setSearchTerm('');
    setPage(0);
  };

  // Import/Export Handlers
  const handleDownloadTemplate = async () => {
    try {
      const blob = await downloadTemplate();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'members_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      enqueueSnackbar('تم تحميل القالب بنجاح', { variant: 'success' });
    } catch (error) {
      console.error('Error downloading template:', error);
      enqueueSnackbar('فشل تحميل القالب', { variant: 'error' });
    }
  };

  const handleImportClick = () => {
    setImportDialogOpen(true);
  };

  const handleCloseImportDialog = () => {
    setImportDialogOpen(false);
    fetchMembers();
  };

  const performExport = async (params) => {
    return await exportMembers(params);
  };

  // Delete/Restore Handlers
  const closeDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, open: false }));
  };

  const handleConfirmAction = async (actionFn, successMessage, errorMessage) => {
    try {
      await actionFn();
      enqueueSnackbar(successMessage, { variant: 'success' });
      fetchMembers();
      closeDialog();
    } catch (error) {
      console.error(errorMessage, error);
      enqueueSnackbar(errorMessage, { variant: 'error' });
    }
  };

  const handleDeleteClick = (member) => {
    setConfirmDialog({
      open: true,
      title: 'هل أنت متأكد؟',
      content:
        member.type === 'PRINCIPAL'
          ? `سيتم حذف المستفيد ${member.fullName}. سيتم حذف جميع التابعين المرتبطين به أيضاً!`
          : `سيتم حذف المستفيد ${member.fullName}.`,
      severity: 'error',
      confirmText: 'نعم، احذفه',
      onConfirm: () => handleConfirmAction(() => deleteMember(member.id), 'تم حذف المستفيد بنجاح', 'خطأ في حذف المستفيد')
    });
  };

  const handleRestoreClick = (member) => {
    setConfirmDialog({
      open: true,
      title: 'استعادة المستفيد؟',
      content: `سيتم استعادة المستفيد ${member.fullName} وإعادته للقائمة النشطة.`,
      severity: 'success',
      confirmText: 'نعم، استعده',
      onConfirm: () => handleConfirmAction(() => restoreMember(member.id), 'تم استعادة المستفيد بنجاح', 'خطأ في استعادة المستفيد')
    });
  };

  const handleHardDeleteClick = (member) => {
    setConfirmDialog({
      open: true,
      title: 'حذف نهائي؟',
      content: `سيتم حذف المستفيد ${member.fullName} نهائياً من قاعدة البيانات. هذا الإجراء لا يمكن التراجع عنه!`,
      severity: 'error',
      confirmText: 'نعم، احذف نهائياً',
      onConfirm: () => handleConfirmAction(() => hardDeleteMember(member.id), 'تم الحذف النهائي بنجاح', 'خطأ في الحذف النهائي')
    });
  };

  // ════════════════════════════════════════════════════════════════════════
  // TABLE COLUMNS DEFINITION
  // ════════════════════════════════════════════════════════════════════════
  const columns = [
    { id: 'index', label: '#', minWidth: 50, sortable: false, align: 'center' },
    {
      id: 'cardNumber',
      label: 'رقم البطاقة',
      minWidth: 130,
      align: 'center',
      sortable: true
    },
    {
      id: 'fullName',
      label: 'الاسم',
      minWidth: 180,
      align: 'center',
      sortable: true
    },
    { id: 'type', label: 'النوع', minWidth: 100, sortable: true, align: 'center' },
    { id: 'status', label: 'الحالة', minWidth: 100, sortable: true, align: 'center' },
    {
      id: 'employerName',
      label: 'جهة العمل',
      minWidth: 150,
      align: 'center',
      sortable: true
    },
    { id: 'dependentsCount', label: 'التبعية / التابعون', minWidth: 120, sortable: false, align: 'center' },
    { id: 'actions', label: 'إجراءات', minWidth: 150, sortable: false, align: 'center' }
  ];

  // ════════════════════════════════════════════════════════════════════════
  // TABLE CELL RENDERER
  // ════════════════════════════════════════════════════════════════════════
  const renderCell = (member, column, rowIndex) => {
    switch (column.id) {
      case 'index':
        return <Typography variant="body2" color="textSecondary" fontWeight="bold">{(page * rowsPerPage) + rowIndex + 1}</Typography>;

      case 'cardNumber':
        return (
          <Chip
            label={member.cardNumber || '-'}
            variant="outlined"
            size="small"
            color="secondary"
            sx={{ fontWeight: 'medium', fontFamily: 'monospace' }}
          />
        );

      case 'fullName':
        return (
          <Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" fontWeight="500">
                {member.fullName || '-'}
              </Typography>
              {member.isVip && (
                <Tooltip title="VIP member">
                  <VIPIcon sx={{ color: '#ffc107', fontSize: 18 }} />
                </Tooltip>
              )}
              {member.isUrgent && (
                <Tooltip title="Urgent case">
                  <FlashIcon sx={{ color: '#ff5722', fontSize: 18 }} />
                </Tooltip>
              )}
            </Stack>
          </Box>
        );

      case 'type': {
        if (member.type === MEMBER_TYPES.PRINCIPAL) {
          return <Chip label="رئيسي" color="primary" size="small" sx={{ width: 80, minWidth: 80, fontWeight: 600, justifyContent: 'center' }} />;
        }
        const relConfig = RELATIONSHIP_CONFIG[member.relationship];
        const labelAr = relConfig ? relConfig.labelAr : 'تابع';
        const badgeColor = relConfig ? relConfig.color : 'default';
        return <Chip label={labelAr} color={badgeColor} size="small" variant="outlined" sx={{ width: 80, minWidth: 80, fontWeight: 600, justifyContent: 'center' }} />;
      }

      case 'status':
        const statusConfig = {
          ACTIVE: { label: 'نشط', color: 'success' },
          SUSPENDED: { label: 'معلق', color: 'warning' },
          TERMINATED: { label: 'منتهي', color: 'error' },
          PENDING: { label: 'قيد المراجعة', color: 'warning' }
        };
        const config = statusConfig[member.status] || { label: member.status, color: 'default' };
        return <Chip label={config.label} color={config.color} size="small" />;

      case 'employerName':
        return <Typography variant="body2">{member.employerName || '-'}</Typography>;

      case 'dependentsCount':
        if (member.type === MEMBER_TYPES.DEPENDENT) {
          return (
            <Tooltip title={`عرض المستفيد الرئيسي (${member.parentFullName || 'غير محدد'})`}>
              <IconButton
                size="small"
                color="info"
                onClick={(e) => {
                  e.stopPropagation();
                  if (member.parentId) navigate(`/members/${member.parentId}`);
                }}
                sx={{ border: '1px solid', borderColor: 'info.main', borderRadius: 1 }}
              >
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          );
        }

        // PRINCIPAL member dependents badge
        return (
          <Tooltip title={member.dependentsCount > 0 ? "يملك تابعين (اضغط عرض لمعرفتهم)" : "لا يوجد تابعين"}>
            <Chip
              label={member.dependentsCount || 0}
              size="small"
              variant="outlined"
              sx={{
                minWidth: 28,
                height: 20,
                borderRadius: '6px',
                bgcolor: member.dependentsCount > 0 ? 'secondary.lighter' : 'transparent',
                borderColor: member.dependentsCount > 0 ? 'secondary.light' : 'divider',
                color: member.dependentsCount > 0 ? 'secondary.main' : 'text.disabled',
                fontWeight: member.dependentsCount > 0 ? 600 : 400
              }}
            />
          </Tooltip>
        );

      case 'actions':
        if (showDeleted) {
          // Actions for deleted members
          return (
            <Stack direction="row" spacing={0.5}>
              <Tooltip title="استعادة">
                <IconButton size="small" color="success" onClick={() => handleRestoreClick(member)}>
                  <UndoIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="حذف نهائي">
                <IconButton size="small" color="error" onClick={() => handleHardDeleteClick(member)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          );
        }

        // Actions for active members
        return (
          <Stack direction="row" spacing={0.5}>
            {member.status === MEMBER_STATUSES.PENDING && (
              <Tooltip title="اعتماد العضوية">
                <IconButton size="small" color="success">
                  <CheckCircleIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            <Tooltip title="عرض التفاصيل">
              <IconButton size="small" color="info" onClick={() => navigate(`/members/${member.id}`)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="تعديل">
              <IconButton size="small" color="primary" onClick={() => navigate(`/members/${member.id}/edit`)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="حذف">
              <IconButton size="small" color="error" onClick={() => handleDeleteClick(member)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        );

      default:
        return member[column.id];
    }
  };

  // ════════════════════════════════════════════════════════════════════════
  // RENDER
  // ════════════════════════════════════════════════════════════════════════
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}>
      {/* Page Header */}
      <ModernPageHeader
        title="قائمة المستفيدين"
        subtitle="المعيار الموحد لجميع الجداول في النظام"
        icon={<PersonIcon />}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المستفيدين' }]}
        actions={
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {/* Excel Buttons Group */}
            <Button
              variant="outlined"
              onClick={handleDownloadTemplate}
              startIcon={<DownloadIcon />}
              sx={{
                minWidth: '155px',
                color: '#1b5e20',
                borderColor: '#1b5e20',
                '&:hover': {
                  backgroundColor: '#1b5e2010',
                  borderColor: '#1b5e20'
                }
              }}
            >
              تحميل القالب
            </Button>
            <Button
              variant="outlined"
              onClick={handleImportClick}
              startIcon={<UploadFileIcon />}
              sx={{
                minWidth: '155px',
                color: '#1b5e20',
                borderColor: '#1b5e20',
                '&:hover': {
                  backgroundColor: '#1b5e2010',
                  borderColor: '#1b5e20'
                }
              }}
            >
              استيراد من إكسل
            </Button>
            <Button
              variant="outlined"
              onClick={() => setExportWizardOpen(true)}
              startIcon={<FileDownloadIcon />}
              sx={{
                minWidth: '155px',
                color: '#1b5e20',
                borderColor: '#1b5e20',
                '&:hover': {
                  backgroundColor: '#1b5e2010',
                  borderColor: '#1b5e20'
                }
              }}
            >
              تصدير لإكسل
            </Button>

            {/* Deleted Members Toggle */}
            <SoftDeleteToggle showDeleted={showDeleted} onToggle={() => setShowDeleted(!showDeleted)} />


            <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/members/add')}>
              إضافة مستفيد
            </Button>
          </Stack>
        }
        sx={{ mb: 0.5 }}
      />

      <MainCard sx={{ mb: 1, flexShrink: 0 }}>
        {/* FILTERS AND SEARCH ROW */}
        <Box>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems="center" sx={{ width: '100%' }}>
            {/* Refresh */}
            <Tooltip title="تحديث">
              <IconButton onClick={fetchMembers} color="primary" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: 40, height: 40 }}>
                <RefreshIcon />
              </IconButton>
            </Tooltip>

            {/* Total Count */}
            <Chip
              icon={<PersonIcon fontSize="small" />}
              label={`${totalCount} مستفيد`}
              variant="outlined"
              color="primary"
              sx={{ height: 40, borderRadius: 1, fontWeight: 'bold', fontSize: '14px', px: 1 }}
            />

            {/* Search Input */}
            <TextField
              sx={{ flexGrow: 1, minWidth: { md: '200px' } }}
              size="small"
              placeholder="بحث بالاسم أو رقم البطاقة..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: searchTerm && (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setSearchTerm('')}>
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ),
                sx: { height: 40 }
              }}
            />

            {/* Employer Filter */}
            <TextField
              select
              size="small"
              label="جهة العمل"
              value={filters.organizationId}
              onChange={(e) => handleFilterChange('organizationId', e.target.value)}
              sx={{ minWidth: 140, bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: 40 } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              {employers.map((emp) => (
                <MenuItem key={emp.id} value={emp.id}>
                  {emp.label}
                </MenuItem>
              ))}
            </TextField>

            {/* Type Filter */}
            <TextField
              select
              size="small"
              label="النوع"
              value={filters.type}
              onChange={(e) => handleFilterChange('type', e.target.value)}
              sx={{ minWidth: 110, bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: 40 } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              <MenuItem value={MEMBER_TYPES.PRINCIPAL}>رئيسي</MenuItem>
              <MenuItem value={MEMBER_TYPES.DEPENDENT}>تابع (الكل)</MenuItem>
              <MenuItem value="WIFE">تابع - زوجة</MenuItem>
              <MenuItem value="HUSBAND">تابع - زوج</MenuItem>
              <MenuItem value="SON">تابع - ابن</MenuItem>
              <MenuItem value="DAUGHTER">تابع - ابنة</MenuItem>
              <MenuItem value="FATHER">تابع - أب</MenuItem>
              <MenuItem value="MOTHER">تابع - أم</MenuItem>
            </TextField>

            {/* Status Filter */}
            <TextField
              select
              size="small"
              label="الحالة"
              value={filters.status}
              onChange={(e) => handleFilterChange('status', e.target.value)}
              sx={{ minWidth: 110, bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: 40 } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              <MenuItem value={MEMBER_STATUSES.ACTIVE}>نشط</MenuItem>
              <MenuItem value={MEMBER_STATUSES.SUSPENDED}>معلق</MenuItem>
              <MenuItem value={MEMBER_STATUSES.TERMINATED}>منتهي</MenuItem>
            </TextField>

            {/* Reset Button */}
            <Button variant="outlined" color="secondary" onClick={handleResetFilters} startIcon={<FilterAltOffIcon />} sx={{ minWidth: 120, height: 40 }}>
              إعادة ضبط
            </Button>
          </Stack>
        </Box>
      </MainCard>

      <UnifiedMedicalTable
        columns={columns}
        rows={members}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(newPage) => setPage(newPage)}
        onRowsPerPageChange={(newSize) => setRowsPerPage(newSize)}
        sortBy={sortBy}
        sortDirection={sortDirection}
        onSort={handleSort}
        renderCell={renderCell}
        getRowKey={(member) => member.id}
        emptyMessage={showDeleted ? 'لا توجد مستفيدين محذوفين' : 'لا توجد مستفيدين'}
        loadingMessage="جارِ التحميل..."
        sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: 0, mb: 1 }}
        tableContainerSx={{ flexGrow: 1, minHeight: 0 }}
      />

      {/* Import Dialog */}
      <MembersBulkUploadDialog open={importDialogOpen} onClose={handleCloseImportDialog} />

      {/* Export Wizard */}
      <DataExportWizard
        open={exportWizardOpen}
        onClose={() => setExportWizardOpen(false)}
        onExport={performExport}
        title="تصدير بيانات المستفيدين"
        fileName={`members-export-${new Date().toISOString().split('T')[0]}.xlsx`}
        params={{
          searchTerm,
          organizationId: filters.organizationId || undefined,
          status: filters.status || undefined,
          type: filters.type || undefined,
          deleted: showDeleted
        }}
      />

      {/* Confirmation Dialog */}
      <Dialog open={confirmDialog.open} onClose={closeDialog}>
        <DialogTitle>{confirmDialog.title}</DialogTitle>
        <DialogContent>
          <DialogContentText>{confirmDialog.content}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog} color="inherit">
            {confirmDialog.cancelText}
          </Button>
          <Button onClick={confirmDialog.onConfirm} color={confirmDialog.severity === 'error' ? 'error' : 'primary'} autoFocus>
            {confirmDialog.confirmText}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UnifiedMembersList;
