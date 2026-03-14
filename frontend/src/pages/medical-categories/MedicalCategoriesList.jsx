/**
 * Medical Categories List Page
 *
 * Pattern: UnifiedPageHeader → External Filters → MainCard → GenericDataTable
 *
 * Features:
 * - External parent category filter
 * - No column filters (clean table)
 * - Professional Excel export with ALL data
 * - Multi-column sorting
 * - Sticky headers
 * - Pagination
 */

import { useMemo, useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Chip, IconButton, Stack, Tooltip, Typography, Button, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import CategoryIcon from '@mui/icons-material/Category';
import RefreshIcon from '@mui/icons-material/Refresh';
import FileDownloadIcon from '@mui/icons-material/FileDownload';

import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import GenericDataTable from 'components/GenericDataTable';
import TableErrorBoundary from 'components/TableErrorBoundary';
import PermissionGuard from 'components/PermissionGuard';
import useTableState from 'hooks/useTableState';
import { useTableRefresh } from 'contexts/TableRefreshContext';
import { getMedicalCategories, deleteMedicalCategory, getAllMedicalCategories } from 'services/api/medical-categories.service';
import { exportMedicalCategoriesToExcel } from 'utils/excelExport';
import { openSnackbar } from 'api/snackbar';

const QUERY_KEY = 'medical-categories';

const MedicalCategoriesList = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { refreshSignal } = useTableRefresh();

  // ========================================
  // LOCAL STATE
  // ========================================

  // Parent category filter
  const [parentFilter, setParentFilter] = useState('');

  // Excel export loading state
  const [isExporting, setIsExporting] = useState(false);

  // ========================================
  // TABLE STATE
  // ========================================

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: { field: 'id', direction: 'desc' },
    initialFilters: {}
  });

  // ========================================
  // FETCH PARENT CATEGORIES (for filter dropdown)
  // ========================================

  const { data: allCategories = [] } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: getAllMedicalCategories,
    staleTime: 5 * 60 * 1000 // Cache for 5 minutes
  });

  // Get only parent categories (those without parentId or root level)
  const parentCategories = useMemo(() => {
    return allCategories.filter((cat) => !cat.parentId);
  }, [allCategories]);

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================

  const handleNavigateAdd = useCallback(() => navigate('/medical-categories/add'), [navigate]);
  const handleNavigateView = useCallback((id) => navigate(`/medical-categories/${id}`), [navigate]);
  const handleNavigateEdit = useCallback((id) => navigate(`/medical-categories/edit/${id}`), [navigate]);

  const handleDelete = useCallback(
    async (id, name) => {
      if (!window.confirm(`هل أنت متأكد من حذف التصنيف "${name}"؟`)) return;
      try {
        await deleteMedicalCategory(id);
        openSnackbar({ message: 'تم حذف التصنيف بنجاح', variant: 'success' });
        queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      } catch (err) {
        console.error('[MedicalCategories] Delete failed:', err);
        const errorMsg = err?.response?.data?.message || 'فشل حذف التصنيف - قد يكون مرتبطاً بخدمات طبية';
        openSnackbar({ message: errorMsg, variant: 'error' });
      }
    },
    [queryClient]
  );

  const { data, isLoading, refetch } = useQuery({
    queryKey: [QUERY_KEY, tableState.page, tableState.pageSize, tableState.sorting, parentFilter],
    queryFn: async () => {
      const params = { page: tableState.page, size: tableState.pageSize };

      // Add sorting
      if (tableState.sorting.length > 0) {
        const sort = tableState.sorting[0];
        params.sort = `${sort.id},${sort.desc ? 'desc' : 'asc'}`;
      }

      // Add parent filter
      if (parentFilter) {
        params.parentId = parentFilter;
      }

      return await getMedicalCategories(params);
    },
    keepPreviousData: true
  });

  // Listen to global refresh signal
  useEffect(() => {
    refetch();
  }, [refreshSignal, refetch]);

  // ========================================
  // EXCEL EXPORT - Export ALL data
  // ========================================

  const handleExcelExport = useCallback(async () => {
    try {
      setIsExporting(true);
      // Fetch ALL categories for export (not just visible page)
      const allCats = await getAllMedicalCategories();
      await exportMedicalCategoriesToExcel(allCats || []);
      openSnackbar({
        message: `تم تصدير ${allCats?.length || 0} تصنيف بنجاح`,
        variant: 'success'
      });
    } catch (error) {
      console.error('[MedicalCategories] Excel export failed:', error);
      openSnackbar({
        message: 'فشل تصدير البيانات. يرجى المحاولة لاحقاً',
        variant: 'error'
      });
    } finally {
      setIsExporting(false);
    }
  }, []);

  // ========================================
  // COLUMN DEFINITIONS - No filters, just sorting
  // ========================================

  const columns = useMemo(
    () => [
      {
        accessorKey: 'code',
        header: 'الرمز',
        enableSorting: true,
        enableColumnFilter: false,
        minWidth: '6.25rem',
        align: 'right',
        cell: ({ getValue }) => (
          <Typography variant="body2" fontWeight="medium">
            {getValue() || '-'}
          </Typography>
        )
      },
      {
        accessorKey: 'name',
        header: 'الاسم',
        enableSorting: true,
        enableColumnFilter: false,
        minWidth: '11.25rem',
        align: 'right',
        cell: ({ getValue }) => <Typography variant="body2">{getValue() || '-'}</Typography>
      },
      {
        accessorKey: 'parentName',
        header: 'التصنيف الأب',
        enableSorting: true,
        enableColumnFilter: false,
        minWidth: '9.375rem',
        align: 'right',
        cell: ({ getValue }) => (
          <Typography variant="body2" color="text.secondary">
            {getValue() || '-'}
          </Typography>
        )
      },
      {
        accessorKey: 'active',
        header: 'الحالة',
        enableSorting: true,
        enableColumnFilter: false,
        minWidth: '6.25rem',
        align: 'center',
        cell: ({ row }) => (
          <Chip
            label={row.original?.active ? 'نشط' : 'غير نشط'}
            color={row.original?.active ? 'success' : 'default'}
            size="small"
            variant="light"
          />
        )
      },
      {
        id: 'actions',
        header: 'الإجراءات',
        enableSorting: false,
        enableColumnFilter: false,
        minWidth: '8.125rem',
        align: 'center',
        cell: ({ row }) => (
          <Stack direction="row" spacing={0.5} justifyContent="center">
            <Tooltip title="عرض">
              <IconButton size="small" color="primary" onClick={() => handleNavigateView(row.original?.id)}>
                <VisibilityIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="تعديل">
              <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.original?.id)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="حذف">
              <PermissionGuard requires="medical-categories.delete">
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleDelete(row.original?.id, row.original?.name || row.original?.code)}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </PermissionGuard>
            </Tooltip>
          </Stack>
        )
      }
    ],
    [handleNavigateView, handleNavigateEdit, handleDelete]
  );

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box>
      <PermissionGuard requires="medical-categories.view">
        <UnifiedPageHeader
          title="التصنيفات الطبية"
          subtitle="إدارة التصنيفات الطبية في النظام"
          icon={CategoryIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'التصنيفات الطبية' }]}
          showAddButton={true}
          addButtonLabel="إضافة تصنيف جديد"
          onAddClick={handleNavigateAdd}
          additionalActions={
            <Stack direction="row" spacing={1} alignItems="center">
              {/* Excel Export Button */}
              <Tooltip title="تصدير جميع التصنيفات إلى Excel">
                <Button
                  variant="outlined"
                  color="primary"
                  size="small"
                  startIcon={isExporting ? null : <FileDownloadIcon />}
                  onClick={handleExcelExport}
                  disabled={isExporting}
                >
                  {isExporting ? 'جاري التصدير...' : 'تصدير Excel'}
                </Button>
              </Tooltip>

              {/* Refresh Button */}
              <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => refetch()} disabled={isLoading}>
                تحديث
              </Button>
            </Stack>
          }
        />
      </PermissionGuard>

      {/* ====== FILTER BAR ====== */}
      <MainCard sx={{ mb: '1.0rem' }}>
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
          {/* Parent Category Filter */}
          <FormControl size="small" sx={{ minWidth: '15.625rem' }}>
            <InputLabel id="parent-filter-label">فلترة حسب التصنيف الأب</InputLabel>
            <Select
              labelId="parent-filter-label"
              id="parent-filter"
              value={parentFilter}
              label="فلترة حسب التصنيف الأب"
              onChange={(e) => {
                setParentFilter(e.target.value);
                tableState.setPage(0); // Reset to first page
              }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              {parentCategories.map((cat) => (
                <MenuItem key={cat.id} value={cat.id}>
                  {cat.name || cat.code}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Stats Chips */}
          <Stack direction="row" spacing={1}>
            <Chip label={`الإجمالي: ${data?.total || 0}`} size="small" variant="outlined" />
          </Stack>
        </Stack>
      </MainCard>

      {/* ====== MAIN TABLE ====== */}
      <MainCard>
        <TableErrorBoundary>
          <GenericDataTable
            columns={columns}
            data={data?.items || []}
            totalCount={data?.total || 0}
            isLoading={isLoading}
            tableState={tableState}
            enableFiltering={false}
            enableSorting={true}
            enablePagination={true}
            stickyHeader={true}
            minHeight={400}
            maxHeight="calc(100vh - 400px)"
            onRowClick={(row) => handleNavigateView(row.id)}
            emptyMessage="لا توجد تصنيفات طبية"
            rowsPerPageOptions={[5, 10, 25, 50]}
          />
        </TableErrorBoundary>
      </MainCard>
    </Box>
  );
};

export default MedicalCategoriesList;
