/**
 * Employers List Page - UNIFIED IMPLEMENTATION
 * Pattern: UnifiedPageHeader → MainCard → GenericDataTable
 */

import { useMemo, useCallback, useDeferredValue, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  Tooltip,
  Typography,
  TextField,
  InputAdornment,
  MenuItem
} from '@mui/material';

// MUI Icons - Always as Component, NEVER as JSX
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import UndoIcon from '@mui/icons-material/Undo';
import PeopleIcon from '@mui/icons-material/People';
import BusinessCenterIcon from '@mui/icons-material/BusinessCenter';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import AssignmentIcon from '@mui/icons-material/Assignment';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';

// Project Components
import MainCard from 'components/MainCard';
import { UnifiedMedicalTable } from 'components/common';
import { ModernPageHeader, SoftDeleteToggle, DataExportWizard, ActionConfirmDialog } from 'components/tba';
import PermissionGuard from 'components/PermissionGuard';
import EmployerImportDialog from './components/EmployerImportDialog';

// Services
import {
  getEmployersPage,
  archiveEmployer,
  restoreEmployer,
  bulkArchiveEmployers,
  bulkRestoreEmployers,
  exportEmployers
} from 'services/api/employers.service';
import { getEffectiveBenefitPolicy } from 'services/api/benefit-policies.service';
import { useSnackbar } from 'notistack';

// ============================================================================
// CONSTANTS
// ============================================================================

const QUERY_KEY = 'employers';
const MODULE_NAME = 'employers';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const EmployersList = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sortBy, setSortBy] = useState('code');
  const [sortDirection, setSortDirection] = useState('asc');

  const [searchTerm, setSearchTerm] = useState('');
  const deferredSearchTerm = useDeferredValue(searchTerm);
  const [filters, setFilters] = useState({ active: '' });
  const [showArchived, setShowArchived] = useState(false);
  const [exportWizardOpen, setExportWizardOpen] = useState(false);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', message: '', onConfirm: null, confirmColor: 'primary' });
  const [selectedIds, setSelectedIds] = useState([]);
  const [bulkResultDialog, setBulkResultDialog] = useState({ open: false, result: null });

  const handleResetFilters = () => {
    setFilters({ active: '' });
    setSearchTerm('');
    setPage(0);
  };

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================

  const handleNavigateAdd = useCallback(() => {
    navigate('/employers/create');
  }, [navigate]);

  const handleNavigateEdit = useCallback(
    (id) => {
      navigate(`/employers/edit/${id}`);
    },
    [navigate]
  );

  const handleViewPolicy = useCallback(
    async (employerId) => {
      try {
        const policy = await getEffectiveBenefitPolicy(employerId);
        if (policy && policy.id) {
          navigate(`/benefit-policies/${policy.id}`);
        } else {
          enqueueSnackbar('لا توجد وثيقة منافع فعالة لهذه الجهة', { variant: 'warning' });
        }
      } catch (error) {
        console.error(error);
        enqueueSnackbar('حدث خطأ أثناء البحث عن وثيقة المنافع', { variant: 'error' });
      }
    },
    [navigate, enqueueSnackbar]
  );

  const handleArchive = useCallback(
    (id, name) => {
      setConfirmDialog({
        open: true,
        title: 'تأكيد الحذف',
        message: `هل أنت متأكد من حذف جهة العمل "${name}"؟\n\nملاحظة: الحذف لا يمسح البيانات نهائياً، بل يخفيها من القوائم مع الحفاظ على جميع العلاقات.`,
        confirmColor: 'error',
        onConfirm: async () => {
          try {
            await archiveEmployer(id);
            enqueueSnackbar('تم حذف جهة العمل بنجاح', { variant: 'success' });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Employers] Archive failed:', err);
            const apiMsg = err?.response?.data?.message;
            enqueueSnackbar(apiMsg || err.message || 'فشل حذف جهة العمل', { variant: 'error' });
          } finally {
            setConfirmDialog((prev) => ({ ...prev, open: false }));
          }
        }
      });
    },
    [queryClient]
  );

  const handleRestore = useCallback(
    (id, name) => {
      setConfirmDialog({
        open: true,
        title: 'استعادة السجل',
        message: `هل تريد استعادة جهة العمل "${name}" من سجل المحذوفات؟`,
        confirmColor: 'success',
        onConfirm: async () => {
          try {
            await restoreEmployer(id);
            enqueueSnackbar('تم استعادة جهة العمل بنجاح', { variant: 'success' });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Employers] Restore failed:', err);
            enqueueSnackbar('فشل استعادة جهة العمل', { variant: 'error' });
          } finally {
            setConfirmDialog((prev) => ({ ...prev, open: false }));
          }
        }
      });
    },
    [queryClient]
  );

  const handleBulkResult = (result) => {
    setSelectedIds([]);
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    if (result && result.failedCount > 0) {
      setBulkResultDialog({ open: true, result });
    } else {
      enqueueSnackbar(`تمت العملية على ${result?.successCount ?? 0} من ${result?.totalCount ?? 0} جهة عمل بنجاح`, {
        variant: 'success'
      });
    }
  };

  const handleBulkArchive = useCallback(() => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد حذف جماعي',
      message: `هل تريد حذف ${selectedIds.length} جهة عمل محددة؟ سيتم تخطي أي جهة عمل بها مستفيدون/وثائق نشطة مع توضيح السبب.`,
      confirmColor: 'error',
      onConfirm: async () => {
        try {
          const result = await bulkArchiveEmployers(selectedIds);
          handleBulkResult(result);
        } catch (err) {
          enqueueSnackbar(err?.response?.data?.message || 'فشل الحذف الجماعي', { variant: 'error' });
        } finally {
          setConfirmDialog((prev) => ({ ...prev, open: false }));
        }
      }
    });
  }, [selectedIds, enqueueSnackbar, queryClient]);

  const handleBulkRestore = useCallback(() => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد استعادة جماعية',
      message: `هل تريد استعادة ${selectedIds.length} جهة عمل محددة؟`,
      confirmColor: 'success',
      onConfirm: async () => {
        try {
          const result = await bulkRestoreEmployers(selectedIds);
          handleBulkResult(result);
        } catch (err) {
          enqueueSnackbar(err?.response?.data?.message || 'فشل الاستعادة الجماعية', { variant: 'error' });
        } finally {
          setConfirmDialog((prev) => ({ ...prev, open: false }));
        }
      }
    });
  }, [selectedIds, enqueueSnackbar, queryClient]);

  const { data, isLoading, refetch } = useQuery({
    queryKey: [QUERY_KEY, page, rowsPerPage, sortBy, sortDirection, deferredSearchTerm, filters.active, showArchived],
    queryFn: () =>
      getEmployersPage({
        page,
        size: rowsPerPage,
        sortBy,
        sortDir: sortDirection,
        q: deferredSearchTerm.trim(),
        archivedOnly: showArchived || filters.active === 'inactive'
      }),
    staleTime: 0,
    refetchOnMount: 'always',
    keepPreviousData: true
  });

  // Filtering, sorting and pagination are executed by the server so totals remain exact.
  const processedData = useMemo(() => {
    return {
      content: data?.content || [],
      totalCount: data?.totalElements || 0
    };
  }, [data]);

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================

  const columns = useMemo(
    () => [
      { id: 'code', label: 'الرمز', minWidth: '6.25rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'name', label: 'جهة العمل', minWidth: '15.625rem', align: 'left', headerAlign: 'center', sortable: true },
      { id: 'email', label: 'البريد الإلكتروني', minWidth: '12.5rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'phone', label: 'رقم الهاتف', minWidth: '9.375rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'address', label: 'العنوان', minWidth: '12.5rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'membersCount', label: 'المستفيدون', minWidth: '6.875rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'active', label: 'الحالة', minWidth: '6.25rem', align: 'center', headerAlign: 'center', sortable: true },
      { id: 'actions', label: 'الإجراءات', minWidth: '8.125rem', align: 'center', headerAlign: 'center', sortable: false }
    ],
    []
  );

  const renderCell = (row, column) => {
    switch (column.id) {
      case 'code':
        return (
          <Chip
            label={row.code || '-'}
            size="small"
            variant="outlined"
            color="primary"
            sx={{ minWidth: '4.5rem', justifyContent: 'center' }}
          />
        );
      case 'name':
        return (
          <Stack direction="row" spacing={1} alignItems="center" sx={{ justifyContent: 'flex-start', width: '100%' }}>
            <Typography variant="body2" fontWeight={500} sx={{ textAlign: 'right' }}>
              {row.name || '-'}
            </Typography>
            {row.archived && <Chip label="محذوف" size="small" color="error" variant="outlined" />}
          </Stack>
        );
      case 'email':
        return (
          <Typography variant="body2" color="text.secondary">
            {row.email || '-'}
          </Typography>
        );
      case 'phone':
        return (
          <Typography variant="body2" color="text.secondary" dir="ltr" sx={{ textAlign: 'right' }}>
            {row.phone || '-'}
          </Typography>
        );
      case 'address':
        return (
          <Typography variant="body2" color="text.secondary">
            {row.address || '-'}
          </Typography>
        );
      case 'membersCount':
        return (
          <Chip
            label={row.membersCount ?? 0}
            size="small"
            color={row.membersCount > 0 ? 'info' : 'default'}
            icon={<PeopleIcon sx={{ fontSize: '14px !important' }} />}
            sx={{ minWidth: '4.5rem', justifyContent: 'center' }}
          />
        );
      case 'active':
        return <Chip label={row.active ? 'نشط' : 'غير نشط'} color={row.active ? 'success' : 'error'} size="small" />;
      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            {row.active === false ? (
              /* السجل المؤرشف: الاستعادة فقط، فلا حذف نهائي للسجل التأميني */
              <>
                <PermissionGuard resource="employers" action="delete">
                  <Tooltip title="استعادة">
                    <IconButton size="small" color="success" onClick={() => handleRestore(row.id, row.name || row.code)}>
                      <UndoIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
              </>
            ) : (
              /* ── الوضع النشط: تعديل + حذف ── */
              <>
                <PermissionGuard resource="employers" action="read">
                  <Tooltip title="وثائق جهة العمل">
                    <IconButton size="small" color="primary" onClick={() => handleViewPolicy(row.id)}>
                      <AssignmentIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
                <PermissionGuard resource="employers" action="update">
                  <Tooltip title="تعديل">
                    <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.id)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
                <PermissionGuard resource="employers" action="delete">
                  <Tooltip title="حذف">
                    <IconButton size="small" color="error" onClick={() => handleArchive(row.id, row.name || row.code)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
              </>
            )}
          </Stack>
        );
      default:
        return row[column.id];
    }
  };

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box
      sx={{
        height: 'calc(100vh - 120px)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        width: '100%',
        px: { xs: 2, sm: 3 }
      }}
    >
      <PermissionGuard resource="employers" action="view">
        <ModernPageHeader
          title="جهات العمل"
          subtitle="إدارة جهات العمل ومعلوماتهم"
          icon={BusinessCenterIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'جهات العمل' }]}
          actions={
            <Stack direction="row" spacing={1.5}>
              {selectedIds.length > 0 && !showArchived && (
                <Button variant="contained" color="error" startIcon={<DeleteIcon />} onClick={handleBulkArchive}>
                  حذف جماعي ({selectedIds.length})
                </Button>
              )}
              {selectedIds.length > 0 && showArchived && (
                <Button variant="contained" color="success" startIcon={<UndoIcon />} onClick={handleBulkRestore}>
                  استعادة جماعية ({selectedIds.length})
                </Button>
              )}
              <Button
                variant="outlined"
                onClick={() => setExportWizardOpen(true)}
                startIcon={<FileDownloadIcon />}
                sx={{
                  minWidth: '8.125rem',
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

              <PermissionGuard resource="employers" action="create">
                <Button
                  variant="outlined"
                  onClick={() => setImportDialogOpen(true)}
                  startIcon={<FileUploadIcon />}
                  sx={{ minWidth: '8.125rem' }}
                >
                  استيراد من إكسل
                </Button>
              </PermissionGuard>

              <SoftDeleteToggle showDeleted={showArchived} onToggle={() => setShowArchived(!showArchived)} />

              <PermissionGuard resource="employers" action="create">
                <Button variant="contained" startIcon={<AddIcon />} onClick={handleNavigateAdd}>
                  إضافة جهة عمل
                </Button>
              </PermissionGuard>
            </Stack>
          }
        />
      </PermissionGuard>

      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <MainCard sx={{ mb: 1, flexShrink: 0 }}>
          {/* FILTERS AND SEARCH ROW */}
          <Box>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems="center" sx={{ width: '100%' }}>
              {/* Refresh */}
              <Tooltip title="تحديث">
                <IconButton
                  onClick={() => refetch()}
                  color="primary"
                  sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
                >
                  <RefreshIcon />
                </IconButton>
              </Tooltip>

              {/* Total Count */}
              <Chip
                icon={<BusinessCenterIcon fontSize="small" />}
                label={`${processedData.totalCount} جهة عمل`}
                variant="outlined"
                color="primary"
                sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: 1 }}
              />

              {/* Search Input */}
              <TextField
                sx={{ flexGrow: 1, minWidth: { md: '200px' } }}
                size="small"
                placeholder="بحث بالاسم، الرمز، أو البريد الإلكتروني..."
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(0);
                }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon color="action" />
                    </InputAdornment>
                  ),
                  endAdornment: searchTerm && (
                    <InputAdornment position="end">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setSearchTerm('');
                          setPage(0);
                        }}
                      >
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </InputAdornment>
                  ),
                  sx: { height: '2.5rem' }
                }}
              />

              {/* Status Filter */}
              <TextField
                select
                size="small"
                label="الحالة"
                value={filters.active}
                onChange={(e) => {
                  setFilters((prev) => ({ ...prev, active: e.target.value }));
                  setPage(0);
                }}
                sx={{ minWidth: '6.875rem', bgcolor: 'background.paper' }}
                InputProps={{ sx: { height: '2.5rem' } }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="">
                  <em>الكل</em>
                </MenuItem>
                <MenuItem value="active">نشط</MenuItem>
                <MenuItem value="inactive">غير نشط</MenuItem>
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
          data={processedData.content}
          totalCount={processedData.totalCount}
          loading={isLoading}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={setPage}
          onRowsPerPageChange={setRowsPerPage}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={(col, dir) => {
            setSortBy(col);
            setSortDirection(dir);
          }}
          renderCell={renderCell}
          emptyMessage="لا توجد جهات عمل مسجلة"
          getRowKey={(row) => row.id}
          enableRowSelection
          selectedRowIds={selectedIds}
          onRowSelectionChange={setSelectedIds}
          sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: 0, mb: 1 }}
          tableContainerSx={{ flexGrow: 1, minHeight: 0 }}
        />
      </Box>

      {/* Export Wizard */}
      <DataExportWizard
        open={exportWizardOpen}
        onClose={() => setExportWizardOpen(false)}
        onExport={async () => {
          return await exportEmployers({
            searchTerm,
            status: filters.active,
            showArchived
          });
        }}
        title="تصدير جهات العمل"
        fileName={`TBA_Employers_${new Date().toISOString().split('T')[0]}.xlsx`}
        params={{ searchTerm, status: filters.active, deleted: showArchived }}
      />

      {/* Bulk Import Dialog */}
      <EmployerImportDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        onImportComplete={() => queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })}
      />

      {/* Action Confirmation Dialog */}
      <ActionConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.confirmColor}
        onConfirm={confirmDialog.onConfirm}
        onClose={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}
      />

      {/* Bulk Operation Result Dialog — never collapse partial success into a generic toast */}
      <Dialog open={bulkResultDialog.open} onClose={() => setBulkResultDialog({ open: false, result: null })} maxWidth="sm" fullWidth>
        <DialogTitle>نتيجة العملية الجماعية</DialogTitle>
        <DialogContent>
          <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
            <Chip color="success" label={`نجح: ${bulkResultDialog.result?.successCount ?? 0}`} icon={<CheckCircleIcon />} />
            <Chip color="error" label={`فشل: ${bulkResultDialog.result?.failedCount ?? 0}`} icon={<ErrorIcon />} />
            <Chip variant="outlined" label={`الإجمالي: ${bulkResultDialog.result?.totalCount ?? 0}`} />
          </Stack>
          <List dense>
            {(bulkResultDialog.result?.results || []).map((r) => (
              <ListItem key={r.employerId}>
                <ListItemText
                  primary={r.employerName || `جهة عمل #${r.employerId}`}
                  secondary={r.message}
                  secondaryTypographyProps={{ color: r.success ? 'success.main' : 'error.main' }}
                />
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkResultDialog({ open: false, result: null })}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EmployersList;
