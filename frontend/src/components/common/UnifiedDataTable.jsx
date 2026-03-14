/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNIFIED DATA TABLE SYSTEM - TBA WAAD MEDICAL TPA
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Enterprise-grade unified table component for consistent data presentation.
 * Built with MUI X DataGrid for professional HIS/TPA systems.
 *
 * DESIGN PRINCIPLES:
 * - Single source of truth for ALL tables in the system
 * - Soft mint green header (professional medical color scheme)
 * - Toolbar-based search & filters (NO in-table inputs)
 * - Desktop-first, 100% width occupation
 * - RTL support for Arabic medical systems
 * - Consistent UX matching Epic/Cerner/SAP Fiori standards
 *
 * ARCHITECTURE RULES:
 * ❌ NO custom tables in pages
 * ❌ NO filter inputs inside table headers
 * ❌ NO multiple table libraries
 * ✅ ONE component for ALL data lists
 * ✅ Toolbar for search/filters/actions
 * ✅ Professional healthcare visual identity
 *
 * @author TBA WAAD Development Team
 * @version 1.0.0 - Unified System Launch
 * @since 2026-02-08
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Box, Paper, TextField, InputAdornment, IconButton, Button, Stack, Chip, alpha, useTheme } from '@mui/material';
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarFilterButton,
  GridToolbarDensitySelector,
  GridToolbarExport
} from '@mui/x-data-grid';
import { Search as SearchIcon, Clear as ClearIcon, Add as AddIcon, Refresh as RefreshIcon } from '@mui/icons-material';

// ═══════════════════════════════════════════════════════════════════════════════
// VISUAL THEME CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

const TABLE_THEME = {
  // Header colors - Soft Mint Green (Professional Medical)
  header: {
    background: '#e8f5e9', // Soft mint green
    text: '#1b5e20', // Dark green for contrast
    border: '#c8e6c9'
  },
  // Row colors
  row: {
    hover: alpha('#4caf50', 0.08),
    selected: alpha('#4caf50', 0.12)
  },
  // Toolbar
  toolbar: {
    background: '#fafafa',
    border: '#e0e0e0'
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED TOOLBAR COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Unified Toolbar - Search, Filters, Actions at top (NO in-table filters)
 */
const UnifiedToolbar = ({
  searchTerm,
  onSearchChange,
  onRefresh,
  onAdd,
  addButtonText,
  showAddButton,
  showRefreshButton,
  customActions,
  filterComponents,
  totalCount
}) => {
  return (
    <GridToolbarContainer
      sx={{
        p: '1.0rem',
        gap: '1.0rem',
        backgroundColor: TABLE_THEME.toolbar.background,
        borderBottom: `1px solid ${TABLE_THEME.toolbar.border}`,
        flexWrap: 'wrap'
      }}
    >
      {/* Search Field */}
      <TextField
        size="small"
        placeholder="بحث..."
        value={searchTerm}
        onChange={(e) => onSearchChange(e.target.value)}
        sx={{
          minWidth: '15.625rem',
          backgroundColor: 'background.paper',
          '& .MuiOutlinedInput-root': {
            borderRadius: 1
          }
        }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" color="action" />
            </InputAdornment>
          ),
          endAdornment: searchTerm && (
            <InputAdornment position="end">
              <IconButton size="small" onClick={() => onSearchChange('')}>
                <ClearIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          )
        }}
      />

      {/* Custom Filter Components */}
      {filterComponents && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', flex: 1 }}>{filterComponents}</Box>}

      {/* Spacer */}
      <Box sx={{ flex: 1 }} />

      {/* DataGrid Built-in Tools */}
      <Stack direction="row" spacing={0.5}>
        <GridToolbarColumnsButton />
        <GridToolbarFilterButton />
        <GridToolbarDensitySelector />
        <GridToolbarExport />
      </Stack>

      {/* Total Count */}
      {totalCount !== undefined && (
        <Chip label={`${totalCount} سجل`} size="small" color="primary" variant="outlined" sx={{ fontWeight: 600 }} />
      )}

      {/* Refresh Button */}
      {showRefreshButton && (
        <IconButton size="small" onClick={onRefresh} color="primary">
          <RefreshIcon />
        </IconButton>
      )}

      {/* Add Button */}
      {showAddButton && (
        <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={onAdd} sx={{ whiteSpace: 'nowrap' }}>
          {addButtonText || 'إضافة'}
        </Button>
      )}

      {/* Custom Actions */}
      {customActions}
    </GridToolbarContainer>
  );
};

UnifiedToolbar.propTypes = {
  searchTerm: PropTypes.string,
  onSearchChange: PropTypes.func,
  onRefresh: PropTypes.func,
  onAdd: PropTypes.func,
  addButtonText: PropTypes.string,
  showAddButton: PropTypes.bool,
  showRefreshButton: PropTypes.bool,
  customActions: PropTypes.node,
  filterComponents: PropTypes.node,
  totalCount: PropTypes.number
};

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN UNIFIED DATA TABLE COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * UnifiedDataTable - The ONLY table component used in TBA WAAD system
 *
 * @example
 * <UnifiedDataTable
 *   columns={columns}
 *   rows={data}
 *   loading={false}
 *   totalCount={100}
 *   paginationModel={{ page: 0, pageSize: 10 }}
 *   onPaginationModelChange={handlePageChange}
 *   onRowClick={handleRowClick}
 *   searchTerm={searchTerm}
 *   onSearchChange={setSearchTerm}
 * />
 */
const UnifiedDataTable = ({
  // Data
  columns = [],
  rows = [],
  loading = false,
  totalCount,

  // Pagination
  paginationModel = { page: 0, pageSize: 10 },
  onPaginationModelChange,
  pageSizeOptions = [8, 16, 24, 32, 50],

  // Sorting
  sortModel,
  onSortModelChange,

  // Row interaction
  onRowClick,
  getRowId = (row) => row.id,

  // Toolbar
  searchTerm = '',
  onSearchChange,
  onRefresh,
  onAdd,
  addButtonText,
  showAddButton = false,
  showRefreshButton = true,
  customActions,
  filterComponents,

  // Styling
  height = 'calc(100vh - 280px)',
  minHeight = 400,
  autoHeight = false,
  disableRowSelectionOnClick = true,
  density = 'standard',

  // Selection
  checkboxSelection = false,
  onRowSelectionModelChange,
  rowSelectionModel,

  // Custom props
  ...otherProps
}) => {
  const theme = useTheme();

  // Internal search filtering
  const filteredRows = useMemo(() => {
    if (!searchTerm) return rows;

    const lowerSearch = searchTerm.toLowerCase();
    return rows.filter((row) => {
      return columns.some((col) => {
        const value = row[col.field];
        if (value == null) return false;
        return String(value).toLowerCase().includes(lowerSearch);
      });
    });
  }, [rows, searchTerm, columns]);

  // Calculate total for display
  const displayTotal = totalCount !== undefined ? totalCount : filteredRows.length;

  return (
    <Paper
      elevation={0}
      sx={{
        width: '100%',
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 1,
        overflow: 'hidden'
      }}
    >
      <DataGrid
        rows={filteredRows}
        columns={columns}
        loading={loading}
        getRowId={getRowId}
        // Pagination
        paginationModel={paginationModel}
        onPaginationModelChange={onPaginationModelChange}
        pageSizeOptions={pageSizeOptions}
        paginationMode="server"
        rowCount={displayTotal}
        // Sorting
        sortModel={sortModel}
        onSortModelChange={onSortModelChange}
        sortingMode="server"
        // Row interaction
        onRowClick={onRowClick}
        disableRowSelectionOnClick={disableRowSelectionOnClick}
        // Selection
        checkboxSelection={checkboxSelection}
        rowSelectionModel={rowSelectionModel}
        onRowSelectionModelChange={onRowSelectionModelChange}
        // Density
        density={density}
        // Layout
        autoHeight={autoHeight}
        sx={{
          height: autoHeight ? 'auto' : height,
          minHeight: minHeight,
          width: '100%',
          border: 'none',
          // Header Styling - SOFT MINT GREEN
          '& .MuiDataGrid-columnHeaders': {
            backgroundColor: TABLE_THEME.header.background,
            color: TABLE_THEME.header.text,
            borderBottom: `2px solid ${TABLE_THEME.header.border}`,
            fontWeight: 700,
            fontSize: '0.875rem',
            '& .MuiDataGrid-columnHeaderTitle': {
              fontWeight: 700
            }
          },
          // Column separators
          '& .MuiDataGrid-columnSeparator': {
            color: TABLE_THEME.header.border
          },
          // Row hover
          '& .MuiDataGrid-row:hover': {
            backgroundColor: TABLE_THEME.row.hover,
            cursor: onRowClick ? 'pointer' : 'default'
          },
          // Selected row
          '& .MuiDataGrid-row.Mui-selected': {
            backgroundColor: TABLE_THEME.row.selected,
            '&:hover': {
              backgroundColor: alpha(TABLE_THEME.row.selected, 1.2)
            }
          },
          // Cell styling
          '& .MuiDataGrid-cell': {
            borderBottom: `1px solid ${alpha(theme.palette.divider, 0.5)}`,
            fontSize: '0.875rem'
          },
          // Footer
          '& .MuiDataGrid-footerContainer': {
            borderTop: `1px solid ${theme.palette.divider}`,
            backgroundColor: theme.palette.background.paper
          },
          // RTL support
          direction: theme.direction
        }}
        // Localization (Arabic)
        localeText={{
          noRowsLabel: 'لا توجد بيانات',
          noResultsOverlayLabel: 'لم يتم العثور على نتائج',
          errorOverlayDefaultLabel: 'حدث خطأ',
          toolbarDensity: 'الكثافة',
          toolbarDensityLabel: 'الكثافة',
          toolbarDensityCompact: 'مضغوط',
          toolbarDensityStandard: 'قياسي',
          toolbarDensityComfortable: 'مريح',
          toolbarColumns: 'الأعمدة',
          toolbarColumnsLabel: 'اختر الأعمدة',
          toolbarFilters: 'الفلاتر',
          toolbarFiltersLabel: 'إظهار الفلاتر',
          toolbarFiltersTooltipHide: 'إخفاء الفلاتر',
          toolbarFiltersTooltipShow: 'إظهار الفلاتر',
          toolbarExport: 'تصدير',
          toolbarExportLabel: 'تصدير',
          toolbarExportCSV: 'تحميل كـ CSV',
          toolbarExportPrint: 'طباعة',
          columnsPanelTextFieldLabel: 'البحث عن عمود',
          columnsPanelTextFieldPlaceholder: 'عنوان العمود',
          columnsPanelShowAllButton: 'إظهار الكل',
          columnsPanelHideAllButton: 'إخفاء الكل',
          filterPanelAddFilter: 'إضافة فلتر',
          filterPanelDeleteIconLabel: 'حذف',
          filterPanelOperators: 'المعاملات',
          filterPanelOperatorAnd: 'و',
          filterPanelOperatorOr: 'أو',
          filterPanelColumns: 'الأعمدة',
          filterPanelInputLabel: 'القيمة',
          filterPanelInputPlaceholder: 'قيمة الفلتر',
          filterOperatorContains: 'يحتوي',
          filterOperatorEquals: 'يساوي',
          filterOperatorStartsWith: 'يبدأ بـ',
          filterOperatorEndsWith: 'ينتهي بـ',
          filterOperatorIsEmpty: 'فارغ',
          filterOperatorIsNotEmpty: 'ليس فارغاً',
          filterOperatorIsAnyOf: 'أي من',
          columnMenuLabel: 'القائمة',
          columnMenuShowColumns: 'إظهار الأعمدة',
          columnMenuFilter: 'فلتر',
          columnMenuHideColumn: 'إخفاء',
          columnMenuUnsort: 'إلغاء الترتيب',
          columnMenuSortAsc: 'ترتيب تصاعدي',
          columnMenuSortDesc: 'ترتيب تنازلي',
          footerRowSelected: (count) => `${count} صف محدد`,
          footerTotalRows: 'إجمالي الصفوف:',
          MuiTablePagination: {
            labelRowsPerPage: 'عدد الصفوف:',
            labelDisplayedRows: ({ from, to, count }) => `${from}–${to} من ${count !== -1 ? count : `أكثر من ${to}`}`
          }
        }}
        // Toolbar
        slots={{
          toolbar: () => (
            <UnifiedToolbar
              searchTerm={searchTerm}
              onSearchChange={onSearchChange}
              onRefresh={onRefresh}
              onAdd={onAdd}
              addButtonText={addButtonText}
              showAddButton={showAddButton}
              showRefreshButton={showRefreshButton}
              customActions={customActions}
              filterComponents={filterComponents}
              totalCount={displayTotal}
            />
          )
        }}
        {...otherProps}
      />
    </Paper>
  );
};

UnifiedDataTable.propTypes = {
  // Data
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      field: PropTypes.string.isRequired,
      headerName: PropTypes.string,
      width: PropTypes.number,
      flex: PropTypes.number,
      renderCell: PropTypes.func,
      valueGetter: PropTypes.func,
      sortable: PropTypes.bool,
      filterable: PropTypes.bool
    })
  ).isRequired,
  rows: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number,

  // Pagination
  paginationModel: PropTypes.shape({
    page: PropTypes.number,
    pageSize: PropTypes.number
  }),
  onPaginationModelChange: PropTypes.func,
  pageSizeOptions: PropTypes.arrayOf(PropTypes.number),

  // Sorting
  sortModel: PropTypes.array,
  onSortModelChange: PropTypes.func,

  // Row interaction
  onRowClick: PropTypes.func,
  getRowId: PropTypes.func,

  // Toolbar
  searchTerm: PropTypes.string,
  onSearchChange: PropTypes.func,
  onRefresh: PropTypes.func,
  onAdd: PropTypes.func,
  addButtonText: PropTypes.string,
  showAddButton: PropTypes.bool,
  showRefreshButton: PropTypes.bool,
  customActions: PropTypes.node,
  filterComponents: PropTypes.node,

  // Styling
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  minHeight: PropTypes.number,
  autoHeight: PropTypes.bool,
  disableRowSelectionOnClick: PropTypes.bool,
  density: PropTypes.oneOf(['compact', 'standard', 'comfortable']),

  // Selection
  checkboxSelection: PropTypes.bool,
  onRowSelectionModelChange: PropTypes.func,
  rowSelectionModel: PropTypes.array
};

export default UnifiedDataTable;
