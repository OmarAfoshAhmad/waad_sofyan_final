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
  Autocomplete,
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
  Search as SearchIcon,
  Close as CloseIcon,
  UploadFile as UploadFileIcon,
  FileDownload as FileDownloadIcon,
  Undo as UndoIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import { ModernPageHeader, MemberAvatar, SoftDeleteToggle, ActionConfirmDialog } from 'components/tba';
import { UnifiedMedicalTable } from 'components/common';
import MembersBulkUploadDialog from 'components/members/MembersBulkUploadDialog';
import DataExportWizard from 'components/tba/DataExportWizard';
import {
  searchMembers,
  exportMembers,
  exportReimportableMembers,
  terminateMembership,
  bulkDeleteMembers,
  restoreMember,
  hardDeleteMember,
  toggleMemberActive,
  MEMBER_TYPES
} from 'services/api/unified-members.service';
import axiosClient from 'utils/axios';
import { RELATIONSHIP_CONFIG } from 'components/insurance/MemberTypeIndicator';
import { formatDate } from 'utils/formatters';
import MemberLifecycleDialog from './MemberLifecycleDialog';
import useAuth from 'hooks/useAuth';
import { getMemberCapabilities } from './memberCapabilities';

const MIN_MEMBER_SEARCH_LENGTH = 3;

/**
 * Unified Members List Component
 */
const UnifiedMembersList = () => {
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();
  const { user } = useAuth();
  // Mirrors MemberCommandAccessPolicy/MemberImportAccessPolicy. These flags
  // improve the UI only; backend resource-scope checks remain authoritative.
  const capabilities = getMemberCapabilities(user);

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
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
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
  const [lifecycleDialog, setLifecycleDialog] = useState({ open: false, action: 'SUSPEND', member: null });
  const [lifecycleLoading, setLifecycleLoading] = useState(false);

  // Selection
  const [selectedMembers, setSelectedMembers] = useState([]);

  // Lookups
  const [employers, setEmployers] = useState([]);

  // ════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ════════════════════════════════════════════════════════════════════════
  const getEffectiveSearchTerm = () => {
    const trimmed = debouncedSearchTerm.trim();
    return trimmed.length >= MIN_MEMBER_SEARCH_LENGTH ? trimmed : '';
  };

  const fetchMembers = async () => {
    setLoading(true);
    try {
      const effectiveSearchTerm = getEffectiveSearchTerm();
      const hasSearch = !!effectiveSearchTerm;
      const params = {
        page,
        size: rowsPerPage,
        sort: sortBy,
        direction: sortDirection.toUpperCase(),
        deleted: showDeleted,
        ...(filters.organizationId && { employerId: filters.organizationId }),
        ...(filters.type && { type: filters.type }),
        ...(filters.status && { status: filters.status }),
        ...(hasSearch && { fullName: effectiveSearchTerm })
      };

      const response = await searchMembers(params);
      const pageData = response?.data || response;

      setMembers(pageData?.content || []);
      setTotalCount(pageData?.totalElements || 0);
      setSelectedMembers([]); // Clear selection on page change or fetch
    } catch (error) {
      console.error('Error fetching members:', error);
      enqueueSnackbar('خطأ في جلب المستفيدين', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const fetchEmployers = async () => {
    try {
      const response = await axiosClient.get('/employers/selectors/with-members');
      setEmployers(response.data?.data || []);
    } catch (error) {
      console.error('Error fetching employers:', error);
    }
  };

  useEffect(() => {
    fetchEmployers();
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
      setPage(0);
    }, 400);

    return () => clearTimeout(timer);
  }, [searchTerm]);

  useEffect(() => {
    fetchMembers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, rowsPerPage, filters, debouncedSearchTerm, sortBy, sortDirection, showDeleted]);

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
    setDebouncedSearchTerm('');
    setPage(0);
  };

  // Import/Export Handlers
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

  const handleReimportableExport = async () => {
    try {
      const blob = await exportReimportableMembers({
        searchTerm,
        organizationId: filters.organizationId || undefined,
        status: filters.status || undefined,
        type: filters.type || undefined,
        deleted: showDeleted
      });
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `members-reimportable-${new Date().toISOString().split('T')[0]}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      enqueueSnackbar('تم إنشاء ملف صالح للمعاينة وإعادة الاستيراد', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar(error?.response?.data?.message || 'فشل إنشاء ملف إعادة الاستيراد', { variant: 'error' });
    }
  };

  // Delete/Restore Handlers
  const closeDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, open: false }));
  };

  const handleConfirmAction = async (actionFn, defaultSuccessMessage, defaultErrorMessage) => {
    try {
      const result = await actionFn();
      const message = typeof result === 'string' ? result : result?.message || defaultSuccessMessage;
      enqueueSnackbar(message, { variant: 'success' });
    } catch (error) {
      console.error('Action failed:', error);
      const apiMessage = error?.response?.data?.message || error?.message;
      enqueueSnackbar(apiMessage || defaultErrorMessage || 'حدث خطأ غير متوقع', { variant: 'error' });
    } finally {
      await fetchMembers();
      closeDialog();
    }
  };

  // Selection Handlers
  const handleSelectAllClick = (event) => {
    if (event.target.checked) {
      const pageIds = members.map((member) => member.id);
      setSelectedMembers(pageIds);
      enqueueSnackbar(`تم تحديد الصفحة الحالية (${pageIds.length} مستفيد)`, { variant: 'info' });
      return;
    }
    setSelectedMembers([]);
  };

  const handleSelectRow = (event, id) => {
    const selectedIndex = selectedMembers.indexOf(id);
    let newSelected = [];

    if (selectedIndex === -1) {
      newSelected = newSelected.concat(selectedMembers, id);
    } else if (selectedIndex === 0) {
      newSelected = newSelected.concat(selectedMembers.slice(1));
    } else if (selectedIndex === selectedMembers.length - 1) {
      newSelected = newSelected.concat(selectedMembers.slice(0, -1));
    } else if (selectedIndex > 0) {
      newSelected = newSelected.concat(selectedMembers.slice(0, selectedIndex), selectedMembers.slice(selectedIndex + 1));
    }
    setSelectedMembers(newSelected);
  };

  const handleBulkDelete = () => {
    setConfirmDialog({
      open: true,
      title: 'حذف المستفيدين المحددين',
      content: `هل أنت متأكد من حذف ${selectedMembers.length} مستفيد؟ سيتم حذف التابعين أيضاً إذا كان هناك موظف محدد.`,
      severity: 'error',
      confirmText: 'حذف',
      cancelText: 'إلغاء',
      onConfirm: () =>
        handleConfirmAction(
          async () => {
            const res = await bulkDeleteMembers(selectedMembers);
            setSelectedMembers([]); // clear selection
            if (res?.message && res.message.includes('فشل حذف')) {
              throw new Error(res.message);
            }
            return res?.message || 'تم إرسال طلب الحذف بنجاح';
          },
          'تم إرسال طلب الحذف بنجاح',
          'فشل طلب الحذف المتعدد'
        )
    });
  };

  const handleDeleteClick = (member) => {
    setLifecycleDialog({ open: true, action: 'TERMINATE', member });
  };

  const handleRestoreClick = (member) => {
    setLifecycleDialog({ open: true, action: 'RESTORE', member });
  };

  const handleHardDeleteClick = (member) => {
    setLifecycleDialog({ open: true, action: 'HARD_DELETE', member });
  };

  const handleToggleActiveClick = (member) => {
    const newActive = member.active === false ? true : false;
    setLifecycleDialog({ open: true, action: newActive ? 'RESTORE' : 'SUSPEND', member });
  };

  const executeLifecycleAction = async (reason) => {
    const { action, member } = lifecycleDialog;
    if (!member) return;
    setLifecycleLoading(true);
    try {
      if (action === 'TERMINATE') await terminateMembership(member.id, reason);
      else if (action === 'HARD_DELETE') await hardDeleteMember(member.id, reason);
      else if (action === 'RESTORE') await restoreMember(member.id, reason);
      else await toggleMemberActive(member.id, false, reason);
      enqueueSnackbar('تم تنفيذ العملية بنجاح', { variant: 'success' });
      setLifecycleDialog((prev) => ({ ...prev, open: false }));
      await fetchMembers();
    } catch (error) {
      enqueueSnackbar(error?.response?.data?.message || 'تعذر تنفيذ العملية', { variant: 'error' });
    } finally {
      setLifecycleLoading(false);
    }
  };

  // ════════════════════════════════════════════════════════════════════════
  // TABLE COLUMNS DEFINITION
  // ════════════════════════════════════════════════════════════════════════
  const columns = [
    { id: 'index', label: '#', minWidth: '3.125rem', sortable: false, align: 'center' },
    {
      id: 'cardNumber',
      label: 'رقم البطاقة',
      minWidth: '8.125rem',
      align: 'center',
      sortable: true
    },
    {
      id: 'fullName',
      label: 'الاسم',
      minWidth: '11.25rem',
      align: 'center',
      sortable: true
    },
    {
      id: 'birthDate',
      label: 'المواليد',
      minWidth: '6.25rem',
      align: 'center',
      sortable: true
    },
    { id: 'relationship', label: 'صلة القرابة', minWidth: '6.25rem', sortable: true, align: 'center' },
    { id: 'status', label: 'الحالة', minWidth: '6.25rem', sortable: true, align: 'center' },
    {
      id: 'employerName',
      label: 'جهة العمل',
      minWidth: '9.375rem',
      align: 'center',
      sortable: true
    },
    { id: 'dependentsCount', label: 'التبعية / التابعون', minWidth: '7.5rem', sortable: false, align: 'center' },
    { id: 'actions', label: 'إجراءات', minWidth: '9.375rem', sortable: false, align: 'center' }
  ];

  // ════════════════════════════════════════════════════════════════════════
  // TABLE CELL RENDERER
  // ════════════════════════════════════════════════════════════════════════
  const renderCell = (member, column, rowIndex) => {
    switch (column.id) {
      case 'index':
        return (
          <Typography variant="body2" color="textSecondary" fontWeight="bold">
            {page * rowsPerPage + rowIndex + 1}
          </Typography>
        );

      case 'cardNumber':
        return (
          <Chip
            label={member.cardNumber || '-'}
            variant="outlined"
            size="small"
            color="secondary"
            sx={{ fontWeight: 'medium', fontFamily: 'monospace', minWidth: '10.0rem', justifyContent: 'center' }}
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
                  <VIPIcon sx={{ color: '#ffc107', fontSize: '1.125rem' }} />
                </Tooltip>
              )}
              {member.isUrgent && (
                <Tooltip title="Urgent case">
                  <FlashIcon sx={{ color: '#ff5722', fontSize: '1.125rem' }} />
                </Tooltip>
              )}
            </Stack>
          </Box>
        );

      case 'birthDate':
        return (
          <Typography variant="body2" dir="ltr">
            {formatDate(member.birthDate)}
          </Typography>
        );

      case 'relationship': {
        if (member.type === MEMBER_TYPES.PRINCIPAL) {
          return (
            <Chip
              label="موظف"
              color="primary"
              size="small"
              sx={{ width: '5.0rem', minWidth: '5.0rem', fontWeight: 600, justifyContent: 'center' }}
            />
          );
        }
        const relConfig = RELATIONSHIP_CONFIG[member.relationship];
        const labelAr = relConfig ? relConfig.labelAr : 'تابع';
        const badgeColor = relConfig ? relConfig.color : 'default';
        return (
          <Chip
            label={labelAr}
            color={badgeColor}
            size="small"
            variant="outlined"
            sx={{ width: '5.0rem', minWidth: '5.0rem', fontWeight: 600, justifyContent: 'center' }}
          />
        );
      }

      case 'status':
        const statusConfig = {
          ACTIVE: { label: 'نشط', color: 'success' },
          SUSPENDED: { label: 'موقوف', color: 'warning' },
          TERMINATED: { label: 'منتهي', color: 'error' },
          PENDING: { label: 'قيد المراجعة', color: 'warning' }
        };
        const config = statusConfig[member.status] || { label: member.status, color: 'default' };
        const statusChip = <Chip label={config.label} color={config.color} size="small" />;
        return member.status === 'SUSPENDED' && member.blockedReason ? (
          <Tooltip title={`سبب الإيقاف: ${member.blockedReason}`}>{statusChip}</Tooltip>
        ) : (
          statusChip
        );

      case 'employerName':
        return <Typography variant="body2">{member.employerName || '-'}</Typography>;

      case 'dependentsCount':
        if (member.type === MEMBER_TYPES.DEPENDENT) {
          return (
            <Tooltip title={`عرض الموظف (${member.parentFullName || 'غير محدد'})`}>
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
          <Tooltip title={member.dependentsCount > 0 ? 'يملك تابعين (اضغط عرض لمعرفتهم)' : 'لا يوجد تابعين'}>
            <Chip
              label={member.dependentsCount || 0}
              size="small"
              variant="outlined"
              sx={{
                minWidth: '1.75rem',
                height: '1.25rem',
                borderRadius: '0.375rem',
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
              {capabilities.lifecycle && <Tooltip title="استعادة">
                <IconButton size="small" color="success" onClick={() => handleRestoreClick(member)}>
                  <UndoIcon fontSize="small" />
                </IconButton>
              </Tooltip>}
              {capabilities.hardDelete && <Tooltip title="حذف نهائي">
                <IconButton size="small" color="error" onClick={() => handleHardDeleteClick(member)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>}
            </Stack>
          );
        }

        // Actions for active members
        return (
          <Stack direction="row" spacing={0.5}>
            <Tooltip title="عرض التفاصيل">
              <IconButton size="small" color="info" onClick={() => navigate(`/members/${member.id}`)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            {capabilities.edit && <Tooltip title="تعديل">
              <IconButton size="small" color="primary" onClick={() => navigate(`/members/${member.id}/edit`)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>}
            {capabilities.lifecycle && <Tooltip title="إنهاء العضوية">
              <IconButton size="small" color="error" onClick={() => handleDeleteClick(member)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>}
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
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', overflow: 'hidden', width: '100%' }}>
      {/* Page Header */}
      <ModernPageHeader
        title="قائمة المستفيدين"
        subtitle="المعيار الموحد لجميع الجداول في النظام"
        icon={<PersonIcon />}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المستفيدين' }]}
        actions={
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {/* Bulk Action Buttons */}
            {capabilities.bulkTerminate && selectedMembers.length > 0 && (
              <Button
                variant="contained"
                color="error"
                onClick={handleBulkDelete}
                startIcon={<DeleteIcon />}
                sx={{
                  minWidth: '9.6875rem'
                }}
              >
                حذف المحدد ({selectedMembers.length})
              </Button>
            )}

            {/* Excel Buttons Group — template download is available inside the import dialog. */}
            {capabilities.import && <Button
              variant="outlined"
              onClick={handleImportClick}
              startIcon={<UploadFileIcon />}
              sx={{
                minWidth: '9.6875rem',
                color: '#1b5e20',
                borderColor: '#1b5e20',
                '&:hover': {
                  backgroundColor: '#1b5e2010',
                  borderColor: '#1b5e20'
                }
              }}
            >
              استيراد من إكسل
            </Button>}
            {capabilities.export && <Button
              variant="outlined"
              onClick={() => setExportWizardOpen(true)}
              startIcon={<FileDownloadIcon />}
              sx={{
                minWidth: '9.6875rem',
                color: '#1b5e20',
                borderColor: '#1b5e20',
                '&:hover': {
                  backgroundColor: '#1b5e2010',
                  borderColor: '#1b5e20'
                }
              }}
            >
              تصدير لإكسل
            </Button>}
            {capabilities.export && <Button variant="outlined" onClick={handleReimportableExport} startIcon={<FileDownloadIcon />} sx={{ minWidth: '12rem' }}>
              تصدير قابل لإعادة الاستيراد
            </Button>}

            {/* Deleted Members Toggle */}
            <SoftDeleteToggle showDeleted={showDeleted} onToggle={() => setShowDeleted(!showDeleted)} />

            {capabilities.create && <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/members/add')}>
              إضافة مستفيد
            </Button>}
          </Stack>
        }
        sx={{ mb: 0.5 }}
      />

      <MainCard sx={{ mb: 1, flexShrink: 0 }}>
        {/* FILTERS AND SEARCH ROW */}
        <Box>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', md: 'center' }} sx={{ width: '100%' }}>
            {/* Refresh */}
            <Tooltip title="تحديث">
              <IconButton
                onClick={fetchMembers}
                color="primary"
                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
              >
                <RefreshIcon />
              </IconButton>
            </Tooltip>

            {/* Total Count */}
            <Chip
              icon={<PersonIcon fontSize="small" />}
              label={`${totalCount} مستفيد`}
              variant="outlined"
              color="primary"
              sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: 1 }}
            />

            {/* Search Input */}
            {/* Tooltip (not helperText) so this field reserves the same height as its sibling
                filter dropdowns, none of which have helper text — helperText was pushing this
                field out of vertical alignment with the rest of the row. */}
            <Tooltip
              title={`أدخل ${MIN_MEMBER_SEARCH_LENGTH} أحرف على الأقل للبحث`}
              open={Boolean(searchTerm.trim() && searchTerm.trim().length < MIN_MEMBER_SEARCH_LENGTH)}
              placement="bottom-start"
            >
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
                  sx: { height: '2.5rem' }
                }}
              />
            </Tooltip>

            {/* Employer Filter */}
            <Autocomplete
              size="small"
              options={employers}
              getOptionLabel={(opt) => opt.label || ''}
              isOptionEqualToValue={(opt, val) => String(opt.id) === String(val?.id)}
              value={employers.find((emp) => String(emp.id) === String(filters.organizationId)) || null}
              onChange={(e, newValue) => handleFilterChange('organizationId', newValue?.id || '')}
              sx={{ minWidth: '13.75rem', bgcolor: 'background.paper' }}
              noOptionsText="لا توجد جهات عمل"
              renderInput={(params) => <TextField {...params} label="جهة العمل" InputLabelProps={{ shrink: true }} />}
            />

            {/* Type Filter */}
            <TextField
              select
              size="small"
              label="صلة القرابة"
              value={filters.type}
              onChange={(e) => handleFilterChange('type', e.target.value)}
              sx={{ minWidth: '6.875rem', bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: '2.5rem' } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              <MenuItem value={MEMBER_TYPES.PRINCIPAL}>موظف</MenuItem>
              <MenuItem value={MEMBER_TYPES.DEPENDENT}>عائلة</MenuItem>
              <MenuItem value="WIFE">زوجة</MenuItem>
              <MenuItem value="HUSBAND">زوج</MenuItem>
              <MenuItem value="SON">ابن</MenuItem>
              <MenuItem value="DAUGHTER">ابنة</MenuItem>
              <MenuItem value="FATHER">أب</MenuItem>
              <MenuItem value="MOTHER">أم</MenuItem>
            </TextField>

            {/* Status Filter */}
            <TextField
              select
              size="small"
              label="الحالة"
              value={filters.status}
              onChange={(e) => handleFilterChange('status', e.target.value)}
              sx={{ minWidth: '6.875rem', bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: '2.5rem' } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              <MenuItem value={MEMBER_STATUSES.ACTIVE}>نشط</MenuItem>
              <MenuItem value={MEMBER_STATUSES.SUSPENDED}>موقوف</MenuItem>
              <MenuItem value={MEMBER_STATUSES.PENDING}>قيد المراجعة</MenuItem>
              <MenuItem value={MEMBER_STATUSES.TERMINATED}>منتهي</MenuItem>
            </TextField>

            {/* Reset Button */}
            <Button
              variant="outlined"
              color="secondary"
              onClick={handleResetFilters}
              startIcon={<FilterAltOffIcon />}
              sx={{ minWidth: '7.5rem', height: '2.5rem' }}
            >
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
        selectable={!showDeleted && capabilities.bulkTerminate}
        selectedRows={selectedMembers}
        onSelectAllClick={handleSelectAllClick}
        onSelectRow={handleSelectRow}
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
      <ActionConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.content}
        confirmColor={confirmDialog.severity === 'error' ? 'error' : 'primary'}
        confirmText={confirmDialog.confirmText}
        cancelText={confirmDialog.cancelText}
        onConfirm={confirmDialog.onConfirm}
        onClose={closeDialog}
      />
      <MemberLifecycleDialog
        open={lifecycleDialog.open}
        action={lifecycleDialog.action}
        member={lifecycleDialog.member}
        affectedDependents={lifecycleDialog.member?.dependentsCount || 0}
        loading={lifecycleLoading}
        onClose={() => setLifecycleDialog((prev) => ({ ...prev, open: false }))}
        onConfirm={executeLifecycleAction}
      />
    </Box>
  );
};

export default UnifiedMembersList;
