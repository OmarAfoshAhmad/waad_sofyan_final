/**
 * Employers List Page - UNIFIED IMPLEMENTATION
 * Pattern: UnifiedPageHeader → MainCard → GenericDataTable
 */

import { useMemo, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { Box, Button, Chip, IconButton, Stack, Tooltip, Typography, TextField, InputAdornment, MenuItem } from '@mui/material';

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

// Project Components
import MainCard from 'components/MainCard';
import { UnifiedMedicalTable } from 'components/common';
import { ModernPageHeader, SoftDeleteToggle, DataExportWizard, ActionConfirmDialog } from 'components/tba';
import PermissionGuard from 'components/PermissionGuard';

// Services
import { getEmployers, archiveEmployer, restoreEmployer, deleteEmployer, exportEmployers } from 'services/api/employers.service';
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
  const [filters, setFilters] = useState({ active: '' });
  const [showArchived, setShowArchived] = useState(false);
  const [exportWizardOpen, setExportWizardOpen] = useState(false);
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', message: '', onConfirm: null, confirmColor: 'primary' });

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
            setConfirmDialog(prev => ({ ...prev, open: false }));
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
            setConfirmDialog(prev => ({ ...prev, open: false }));
          }
        }
      });
    },
    [queryClient]
  );

  const handlePermanentDelete = useCallback(
    (id, name) => {
      setConfirmDialog({
        open: true,
        title: 'حذف نهائي',
        message: `⚠️ سيتم حذف جهة العمل "${name}" نهائياً ولا يمكن التراجع عن هذا الإجراء.\n\nهل أنت متأكد؟`,
        confirmColor: 'error',
        onConfirm: async () => {
          try {
            await deleteEmployer(id);
            enqueueSnackbar('تم الحذف النهائي بنجاح', { variant: 'success' });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Employers] Permanent delete failed:', err);
            const apiMsg = err?.response?.data?.message || err?.message;
            enqueueSnackbar(apiMsg || 'فشل الحذف النهائي', { variant: 'error' });
          } finally {
            setConfirmDialog(prev => ({ ...prev, open: false }));
          }
        }
      });
    },
    [queryClient]
  );

  const { data, isLoading, refetch } = useQuery({
    queryKey: [QUERY_KEY],
    queryFn: async () => {
      const result = await getEmployers();
      if (Array.isArray(result)) {
        return { content: result, totalElements: result.length };
      }
      return result;
    },
    keepPreviousData: true
  });

  // Calculate processed data (sorting + pagination + filtering)
  const processedData = useMemo(() => {
    let rawData = [...(data?.content || [])];

    // Search Filtering
    if (searchTerm) {
      const lowerSearch = searchTerm.toLowerCase();
      rawData = rawData.filter(
        (item) =>
          item.name?.toLowerCase().includes(lowerSearch) ||
          item.code?.toLowerCase().includes(lowerSearch) ||
          item.email?.toLowerCase().includes(lowerSearch)
      );
    }

    // Archived Filtering — use active field directly (no separate archived column in DB)
    rawData = rawData.filter((item) => (showArchived ? item.active === false : item.active !== false));

    // Active Status Filtering
    if (filters.active !== '') {
      rawData = rawData.filter((item) => {
        if (filters.active === 'active') return item.active === true;
        if (filters.active === 'inactive') return item.active === false;
        return true;
      });
    }

    // Sorting
    if (sortBy) {
      rawData.sort((a, b) => {
        let valA = a[sortBy] || '';
        let valB = b[sortBy] || '';
        if (typeof valA === 'string') valA = valA.toLowerCase();
        if (typeof valB === 'string') valB = valB.toLowerCase();

        if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
        if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
        return 0;
      });
    }

    const totalFiltered = rawData.length;

    // Pagination
    const startIndex = page * rowsPerPage;
    return {
      content: rawData.slice(startIndex, startIndex + rowsPerPage),
      totalCount: totalFiltered
    };
  }, [data, page, rowsPerPage, sortBy, sortDirection, searchTerm, filters, showArchived]);

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================

  const columns = useMemo(
    () => [
      { id: 'code', label: 'الرمز', minWidth: 100, align: 'center', sortable: true },
      { id: 'name', label: 'جهة العمل', minWidth: 250, align: 'right', sortable: true },
      { id: 'email', label: 'البريد الإلكتروني', minWidth: 200, align: 'right', sortable: true },
      { id: 'phone', label: 'رقم الهاتف', minWidth: 150, align: 'right', sortable: true },
      { id: 'address', label: 'العنوان', minWidth: 200, align: 'right', sortable: true },
      { id: 'membersCount', label: 'المستفيدون', minWidth: 110, align: 'center', sortable: true },
      { id: 'active', label: 'الحالة', minWidth: 100, align: 'center', sortable: true },
      { id: 'actions', label: 'الإجراءات', minWidth: 130, align: 'center', sortable: false }
    ],
    []
  );

  const renderCell = (row, column) => {
    switch (column.id) {
      case 'code':
        return <Chip label={row.code || '-'} size="small" variant="outlined" color="primary" />;
      case 'name':
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2" fontWeight={500}>
              {row.name || '-'}
            </Typography>
            {row.archived && <Chip label="محذوف" size="small" color="error" variant="outlined" />}
          </Stack>
        );
      case 'email':
        return <Typography variant="body2" color="text.secondary">{row.email || '-'}</Typography>;
      case 'phone':
        return <Typography variant="body2" color="text.secondary" dir="ltr" sx={{ textAlign: 'right' }}>{row.phone || '-'}</Typography>;
      case 'address':
        return <Typography variant="body2" color="text.secondary">{row.address || '-'}</Typography>;
      case 'membersCount':
        return (
          <Chip
            label={row.membersCount ?? 0}
            size="small"
            color={row.membersCount > 0 ? 'info' : 'default'}
            icon={<PeopleIcon sx={{ fontSize: '14px !important' }} />}
          />
        );
      case 'active':
        return (
          <Chip
            label={row.active ? 'نشط' : 'غير نشط'}
            color={row.active ? 'success' : 'error'}
            size="small"
          />
        );
      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            {row.active === false ? (
              /* ── سجل المحذوفات: استعادة + حذف نهائي ── */
              <>
                <PermissionGuard resource="employers" action="delete">
                  <Tooltip title="استعادة">
                    <IconButton
                      size="small"
                      color="success"
                      onClick={() => handleRestore(row.id, row.name || row.code)}
                    >
                      <UndoIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
                <PermissionGuard resource="employers" action="delete">
                  <Tooltip title="حذف نهائي">
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => handlePermanentDelete(row.id, row.name || row.code)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
              </>
            ) : (
              /* ── الوضع النشط: تعديل + حذف ── */
              <>
                <PermissionGuard resource="employers" action="update">
                  <Tooltip title="تعديل">
                    <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.id)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
                <PermissionGuard resource="employers" action="delete">
                  <Tooltip title="حذف">
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => handleArchive(row.id, row.name || row.code)}
                    >
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
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', px: { xs: 2, sm: 3 } }}>
      <PermissionGuard resource="employers" action="view">
        <ModernPageHeader
          title="جهات العمل"
          subtitle="إدارة جهات العمل ومعلوماتهم"
          icon={BusinessCenterIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'جهات العمل' }]}
          actions={
            <Stack direction="row" spacing={1.5}>
              <Button
                variant="outlined"
                onClick={() => setExportWizardOpen(true)}
                startIcon={<FileDownloadIcon />}
                sx={{
                  minWidth: '130px',
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
                <IconButton onClick={() => refetch()} color="primary" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: 40, height: 40 }}>
                  <RefreshIcon />
                </IconButton>
              </Tooltip>

              {/* Total Count */}
              <Chip
                icon={<BusinessCenterIcon fontSize="small" />}
                label={`${processedData.totalCount} جهة عمل`}
                variant="outlined"
                color="primary"
                sx={{ height: 40, borderRadius: 1, fontWeight: 'bold', fontSize: '14px', px: 1 }}
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
                      <IconButton size="small" onClick={() => {
                        setSearchTerm('');
                        setPage(0);
                      }}>
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </InputAdornment>
                  ),
                  sx: { height: 40 }
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
                sx={{ minWidth: 110, bgcolor: 'background.paper' }}
                InputProps={{ sx: { height: 40 } }}
                InputLabelProps={{ shrink: true }}
              >
                <MenuItem value="">
                  <em>الكل</em>
                </MenuItem>
                <MenuItem value="active">نشط</MenuItem>
                <MenuItem value="inactive">غير نشط</MenuItem>
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

      {/* Action Confirmation Dialog */}
      <ActionConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.confirmColor}
        onConfirm={confirmDialog.onConfirm}
        onClose={() => setConfirmDialog(prev => ({ ...prev, open: false }))}
      />
    </Box>
  );
};

export default EmployersList;
