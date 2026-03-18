/**
 * Medical Categories List Page
 *
 * Pattern: ModernPageHeader → External Filters → UnifiedMedicalTable (no card)
 */

import { useCallback, useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box, Chip, IconButton, Stack, Tooltip, Typography, Button,
  TextField, MenuItem, InputAdornment
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import DeleteForeverIcon from '@mui/icons-material/DeleteForever';
import ReplayIcon from '@mui/icons-material/Replay';
import CategoryIcon from '@mui/icons-material/Category';
import RefreshIcon from '@mui/icons-material/Refresh';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';

import MainCard from 'components/MainCard';
import { ModernPageHeader, ActionConfirmDialog, SoftDeleteToggle } from 'components/tba';
import { UnifiedMedicalTable } from 'components/common';
import PermissionGuard from 'components/PermissionGuard';
import { useTableRefresh } from 'contexts/TableRefreshContext';
import { getMedicalCategories, deleteMedicalCategory, hardDeleteMedicalCategory, getAllMedicalCategories, restoreMedicalCategory } from 'services/api/medical-categories.service';
import { exportMedicalCategoriesToExcel } from 'utils/excelExport';
import { openSnackbar } from 'api/snackbar';

const QUERY_KEY = 'medical-categories';

// Colors palette for parent category badges (deterministic by parentId) — no green (reserved for active status)
const PARENT_BADGE_COLORS = ['primary', 'secondary', 'info', 'warning', 'error'];
const getParentColor = (parentId) => {
  if (!parentId) return 'info';
  return PARENT_BADGE_COLORS[Number(parentId) % PARENT_BADGE_COLORS.length];
};

const MedicalCategoriesList = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { refreshSignal } = useTableRefresh();

  // ========================================
  // LOCAL STATE
  // ========================================
  const [searchTerm, setSearchTerm] = useState('');
  const [parentFilter, setParentFilter] = useState('');
  const [showDeleted, setShowDeleted] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  // Confirmation dialog
  const [confirmDialog, setConfirmDialog] = useState({
    open: false, title: '', message: '', onConfirm: null, confirmColor: 'error', confirmText: 'نعم، احذف'
  });

  // ========================================
  // TABLE STATE
  // ========================================
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sortBy, setSortBy] = useState('id');
  const [sortDirection, setSortDirection] = useState('desc');

  // ========================================
  // FETCH PARENT CATEGORIES
  // ========================================
  const { data: allCategories = [] } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: getAllMedicalCategories,
    staleTime: 5 * 60 * 1000
  });

  const parentCategories = useMemo(() => allCategories.filter((cat) => !cat.parentId), [allCategories]);

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================
  const handleNavigateAdd = useCallback(() => navigate('/medical-categories/add'), [navigate]);
  const handleNavigateEdit = useCallback((id) => navigate(`/medical-categories/edit/${id}`), [navigate]);

  // ========================================
  // DELETE with ActionConfirmDialog
  // ========================================
  const handleDeleteClick = useCallback((row) => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد حذف التصنيف',
      message: `هل أنت متأكد من حذف التصنيف "${row.name || row.code}"؟\nسيتم إيقاف تشغيله ولن يظهر في القوائم.`,
      confirmColor: 'error',
      confirmText: 'نعم، احذف',
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }));
        try {
          await deleteMedicalCategory(row.id);
          openSnackbar({ message: 'تم حذف التصنيف بنجاح', variant: 'alert', alert: { color: 'success', variant: 'filled' } });
          queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
        } catch (err) {
          console.error('[MedicalCategories] Delete failed:', err);
          const errorMsg = err?.response?.data?.message || 'فشل حذف التصنيف - قد يكون مرتبطاً بخدمات طبية';
          openSnackbar({ message: errorMsg, variant: 'alert', alert: { color: 'error', variant: 'filled' } });
        }
      }
    });
  }, [queryClient]);

  const closeDialog = () => setConfirmDialog((prev) => ({ ...prev, open: false }));

  // ========================================
  // HARD DELETE (permanent — only in showDeleted view)
  // ========================================
  const handleHardDeleteClick = useCallback((row) => {
    setConfirmDialog({
      open: true,
      title: '⚠️ حذف نهائي — لا يمكن التراجع',
      message: `سيتم حذف التصنيف "${row.name || row.code}" نهائياً من قاعدة البيانات.\n\nهذا الإجراء لا يمكن التراجع عنه ولا يمكن استعادة السجل بعد الحذف.\n\nهل أنت متأكد تماماً؟`,
      confirmColor: 'error',
      confirmText: 'نعم، احذف نهائياً',
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }));
        try {
          await hardDeleteMedicalCategory(row.id);
          openSnackbar({ message: 'تم الحذف النهائي للتصنيف', variant: 'alert', alert: { color: 'success', variant: 'filled' } });
          queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
        } catch (err) {
          console.error('[MedicalCategories] Hard delete failed:', err);
          const errorMsg = err?.response?.data?.message || 'فشل الحذف النهائي - قد يكون التصنيف مرتبطاً ببيانات أخرى';
          openSnackbar({ message: errorMsg, variant: 'alert', alert: { color: 'error', variant: 'filled' } });
        }
      }
    });
  }, [queryClient]);

  // ========================================
  // RESTORE (for soft-deleted items)
  // ========================================
  const handleRestoreClick = useCallback((row) => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد استعادة التصنيف',
      message: `هل تريد استعادة التصنيف "${row.name || row.code}"؟\nسيتم تفعيله مجدداً وسيظهر في القوائم.`,
      confirmColor: 'success',
      confirmText: 'نعم، استعد',
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, open: false }));
        try {
          await restoreMedicalCategory(row.id);
          openSnackbar({ message: 'تم استعادة التصنيف بنجاح', variant: 'alert', alert: { color: 'success', variant: 'filled' } });
          queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
        } catch (err) {
          console.error('[MedicalCategories] Restore failed:', err);
          const errorMsg = err?.response?.data?.message || 'فشل استعادة التصنيف';
          openSnackbar({ message: errorMsg, variant: 'alert', alert: { color: 'error', variant: 'filled' } });
        }
      }
    });
  }, [queryClient]);

  // ========================================
  // MAIN DATA QUERY
  // ========================================
  const { data, isLoading, refetch } = useQuery({
    queryKey: [QUERY_KEY, page, rowsPerPage, sortBy, sortDirection, parentFilter, showDeleted, searchTerm],
    queryFn: async () => {
      const params = { page, size: rowsPerPage };
      params.sortBy = sortBy;
      params.sortDir = sortDirection.toUpperCase();
      if (parentFilter) params.parentId = parentFilter;
      params.active = showDeleted ? 'false' : 'true';
      if (searchTerm.trim()) params.search = searchTerm.trim();
      return await getMedicalCategories(params);
    },
    keepPreviousData: true
  });

  useEffect(() => { refetch(); }, [refreshSignal, refetch]);

  // ========================================
  // RESET FILTERS
  // ========================================
  const handleResetFilters = useCallback(() => {
    setSearchTerm('');
    setParentFilter('');
    setShowDeleted(false);
    setPage(0);
  }, []);

  // ========================================
  // EXCEL EXPORT
  // ========================================
  const handleExcelExport = useCallback(async () => {
    try {
      setIsExporting(true);
      const allCats = await getAllMedicalCategories();
      await exportMedicalCategoriesToExcel(allCats || []);
      openSnackbar({
        message: `تم تصدير ${allCats?.length || 0} تصنيف بنجاح`,
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' }
      });
    } catch (error) {
      console.error('[MedicalCategories] Excel export failed:', error);
      openSnackbar({
        message: 'فشل تصدير البيانات. يرجى المحاولة لاحقاً',
        variant: 'alert',
        alert: { color: 'error', variant: 'filled' }
      });
    } finally {
      setIsExporting(false);
    }
  }, []);

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================
  const columns = useMemo(() => [
    { id: 'index',      label: '#',              minWidth: '3.125rem',  sortable: false, align: 'center' },
    { id: 'code',       label: 'الرمز',          minWidth: '8rem',      sortable: true,  align: 'center' },
    { id: 'name',       label: 'الاسم',          minWidth: '11.25rem',  sortable: true,  align: 'right'  },
    { id: 'parentName', label: 'التصنيف الأب',   minWidth: '9.375rem',  sortable: true,  align: 'center' },
    { id: 'active',     label: 'الحالة',         minWidth: '6.25rem',   sortable: true,  align: 'center' },
    { id: 'actions',    label: 'الإجراءات',      minWidth: '8.125rem',  sortable: false, align: 'center' }
  ], []);

  // ========================================
  // CELL RENDERER
  // ========================================
  const renderCell = useCallback((row, column, rowIndex) => {
    switch (column.id) {
      case 'index':
        return <Typography variant="body2" color="textSecondary" fontWeight="bold">{page * rowsPerPage + rowIndex + 1}</Typography>;

      case 'code':
        return (
          <Chip
            label={row.code || '-'}
            variant="outlined"
            size="small"
            color="secondary"
            sx={{ fontWeight: 'medium', fontFamily: 'monospace', minWidth: '7rem', justifyContent: 'center' }}
          />
        );

      case 'name':
        return <Typography variant="body2" fontWeight="500" sx={{ textAlign: 'right', width: '100%' }}>{row.name || '-'}</Typography>;

      case 'parentName':
        return row.parentName
          ? (
            <Chip
              label={row.parentName}
              size="small"
              color={getParentColor(row.parentId)}
              sx={{ fontWeight: 500, minWidth: '5.5rem', justifyContent: 'center' }}
            />
          )
          : <Typography variant="body2" color="text.disabled">—</Typography>;

      case 'active':
        return (
          <Chip
            label={row.active ? 'نشط' : 'غير نشط'}
            color={row.active ? 'success' : 'default'}
            size="small"
            sx={{ minWidth: '5.5rem', justifyContent: 'center', fontWeight: 600 }}
          />
        );

      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
            {showDeleted ? (
              <>
                {/* زر الاستعادة */}
                <PermissionGuard requires="medical-categories.delete">
                  <Tooltip title="استعادة" arrow>
                    <IconButton size="small" color="success" onClick={() => handleRestoreClick(row)}>
                      <ReplayIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
                {/* زر الحذف القاسي */}
                <PermissionGuard requires="medical-categories.delete">
                  <Tooltip title="حذف نهائي — لا يمكن الاسترجاع" arrow>
                    <IconButton size="small" color="error" onClick={() => handleHardDeleteClick(row)}>
                      <DeleteForeverIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
              </>
            ) : (
              <>
                <Tooltip title="تعديل">
                  <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.id)}>
                    <EditIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="حذف (يمكن استعادته لاحقاً)">
                  <PermissionGuard requires="medical-categories.delete">
                    <IconButton size="small" color="error" onClick={() => handleDeleteClick(row)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </PermissionGuard>
                </Tooltip>
              </>
            )}
          </Stack>
        );

      default:
        return row[column.id];
    }
  }, [page, rowsPerPage, handleNavigateEdit, handleDeleteClick, handleHardDeleteClick, handleRestoreClick, showDeleted]);

  // ========================================
  // RENDER
  // ========================================
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}>

      {/* ====== PAGE HEADER ====== */}
      <PermissionGuard requires="medical-categories.view">
        <ModernPageHeader
          title="التصنيفات الطبية"
          subtitle="إدارة التصنيفات الطبية في النظام"
          icon={<CategoryIcon />}
          breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'التصنيفات الطبية' }]}
          actions={
            <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
              {/* Soft Delete Toggle */}
              <SoftDeleteToggle
                showDeleted={showDeleted}
                onToggle={() => { setShowDeleted((prev) => !prev); setPage(0); }}
              />

              {/* Excel Export */}
              <Button
                variant="outlined"
                startIcon={<FileDownloadIcon />}
                onClick={handleExcelExport}
                disabled={isExporting}
                sx={{
                  minWidth: '9.6875rem',
                  height: '2.25rem',
                  color: '#1b5e20',
                  borderColor: '#1b5e20',
                  '&:hover': { backgroundColor: '#1b5e2010', borderColor: '#1b5e20' }
                }}
              >
                {isExporting ? 'جاري التصدير...' : 'تصدير Excel'}
              </Button>

              {/* Add Button */}
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={handleNavigateAdd}
                sx={{ height: '2.25rem' }}
              >
                إضافة تصنيف جديد
              </Button>
            </Stack>
          }
          sx={{ mb: 0.5 }}
        />
      </PermissionGuard>

      {/* ====== FILTER BAR ====== */}
      <MainCard sx={{ mb: 1, flexShrink: 0 }}>
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

          {/* Total Count Badge */}
          <Chip
            icon={<CategoryIcon fontSize="small" />}
            label={`${data?.total || 0} تصنيف`}
            variant="outlined"
            color="primary"
            sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: 1 }}
          />

          {/* Search */}
          <TextField
            sx={{ flexGrow: 1, minWidth: { md: '200px' } }}
            size="small"
            placeholder="بحث بالاسم أو الرمز..."
            value={searchTerm}
            onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start"><SearchIcon color="action" /></InputAdornment>
              ),
              endAdornment: searchTerm && (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => { setSearchTerm(''); setPage(0); }}>
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ),
              sx: { height: '2.5rem' }
            }}
          />

          {/* Parent Category Filter */}
          <TextField
            select
            size="small"
            label="التصنيف الأب"
            value={parentFilter}
            onChange={(e) => { setParentFilter(e.target.value); setPage(0); }}
            sx={{ minWidth: '10rem', bgcolor: 'background.paper' }}
            InputProps={{ sx: { height: '2.5rem' } }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value=""><em>الكل</em></MenuItem>
            {parentCategories.map((cat) => (
              <MenuItem key={cat.id} value={cat.id}>{cat.name || cat.code}</MenuItem>
            ))}
          </TextField>

          {/* Reset */}
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
      </MainCard>

      {/* ====== MAIN TABLE ====== */}
      <UnifiedMedicalTable
        columns={columns}
        rows={data?.items || []}
        loading={isLoading}
        totalCount={data?.total || 0}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(newPage) => setPage(newPage)}
        onRowsPerPageChange={(newSize) => { setRowsPerPage(newSize); setPage(0); }}
        sortBy={sortBy}
        sortDirection={sortDirection}
        onSort={(columnId, direction) => { setSortBy(columnId); setSortDirection(direction); setPage(0); }}
        renderCell={renderCell}
        getRowKey={(row) => row.id}
        emptyMessage="لا توجد تصنيفات طبية"
        rowsPerPageOptions={[5, 10, 25, 50]}
        sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: 0, mb: 1 }}
        tableContainerSx={{ flexGrow: 1, minHeight: 0 }}
      />

      {/* ====== CONFIRM DELETE DIALOG ====== */}
      <ActionConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.confirmColor}
        confirmText={confirmDialog.confirmText}
        cancelText="إلغاء الأمر"
        onConfirm={confirmDialog.onConfirm}
        onClose={closeDialog}
      />
    </Box>
  );
};

export default MedicalCategoriesList;
