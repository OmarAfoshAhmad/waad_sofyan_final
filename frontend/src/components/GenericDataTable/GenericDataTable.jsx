/**
 * GenericDataTable - Reusable Table Component with TanStack React Table
 *
 * A fully-featured, customizable data table component built with @tanstack/react-table
 * and Material-UI. Supports:
 * - Column-based filtering (text, number, select)
 * - Multi-column sorting
 * - Pagination
 * - Sticky headers
 * - Responsive design
 * - Row actions
 * - Custom cell renderers
 *
 * @example
 * <GenericDataTable
 *   columns={columns}
 *   data={data}
 *   totalCount={100}
 *   isLoading={false}
 *   tableState={tableState}
 *   onRowClick={(row) => console.log(row)}
 * />
 */

import { useMemo, useState, Fragment } from 'react';
import PropTypes from 'prop-types';

// TanStack React Table
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  useReactTable
} from '@tanstack/react-table';

// MUI Components
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  TableSortLabel,
  TextField,
  Paper,
  Typography,
  CircularProgress,
  Chip,
  Stack,
  IconButton,
  InputAdornment
} from '@mui/material';

// MUI Icons
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ClearIcon from '@mui/icons-material/Clear';
import SearchIcon from '@mui/icons-material/Search';

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

/**
 * Column Filter Component
 */
const ColumnFilter = ({ column, value, onChange }) => {
  const filterType = column.meta?.filterType || 'text';

  if (filterType === 'none' || column.enableColumnFilter === false) {
    return null;
  }

  const handleChange = (e) => {
    onChange(e.target.value);
  };

  const handleClear = () => {
    onChange('');
  };

  return (
    <TextField
      size="small"
      fullWidth
      placeholder={`بحث ${column.header}...`}
      value={value || ''}
      onChange={handleChange}
      type={filterType === 'number' ? 'number' : 'text'}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon fontSize="small" color="action" />
          </InputAdornment>
        ),
        endAdornment: value ? (
          <InputAdornment position="end">
            <IconButton size="small" onClick={handleClear}>
              <ClearIcon fontSize="small" />
            </IconButton>
          </InputAdornment>
        ) : null
      }}
      sx={{
        '& .MuiOutlinedInput-root': {
          backgroundColor: 'background.paper'
        }
      }}
    />
  );
};

ColumnFilter.propTypes = {
  column: PropTypes.object.isRequired,
  value: PropTypes.any,
  onChange: PropTypes.func.isRequired
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const GenericDataTable = ({
  columns = [],
  data = [],
  totalCount = 0,
  isLoading = false,
  tableState: externalTableState,
  enableFiltering = true,
  enableSorting = true,
  enablePagination = true,
  stickyHeader = true,
  minHeight = 400,
  maxHeight = 'calc(100vh - 300px)',
  disableInternalScroll = false,
  compact = false,
  tableSize = 'medium',
  onRowClick,
  emptyMessage = 'لا توجد بيانات',
  rowsPerPageOptions = [5, 10, 25, 50, 100]
}) => {
  // ========================================
  // INTERNAL STATE (used when no external tableState is provided)
  // ========================================
  const [internalPage, setInternalPage] = useState(0);
  const [internalPageSize, setInternalPageSize] = useState(10);
  const [internalSorting, setInternalSorting] = useState([]);
  const [internalColumnFilters, setInternalColumnFilters] = useState({});

  // Create internal tableState object
  const internalTableState = useMemo(
    () => ({
      page: internalPage,
      pageSize: internalPageSize,
      sorting: internalSorting,
      columnFilters: internalColumnFilters,
      setPage: setInternalPage,
      setPageSize: setInternalPageSize,
      setSorting: setInternalSorting,
      setColumnFilter: (columnId, value) => {
        setInternalColumnFilters((prev) => ({ ...prev, [columnId]: value }));
      },
      clearFilters: () => setInternalColumnFilters({})
    }),
    [internalPage, internalPageSize, internalSorting, internalColumnFilters]
  );

  // Use external tableState if provided, otherwise use internal
  const tableState = externalTableState || internalTableState;

  // ========================================
  // TABLE CONFIGURATION
  // ========================================

  const tableColumns = useMemo(() => {
    return columns.map((col) => ({
      ...col,
      enableSorting: col.enableSorting !== false && enableSorting,
      enableColumnFilter: col.enableColumnFilter !== false && enableFiltering
    }));
  }, [columns, enableSorting, enableFiltering]);

  // ========================================
  // REACT TABLE INSTANCE
  // ========================================

  const table = useReactTable({
    data,
    columns: tableColumns,
    pageCount: Math.ceil(totalCount / tableState.pageSize),
    state: {
      sorting: tableState.sorting,
      columnFilters: Object.entries(tableState.columnFilters).map(([id, value]) => ({
        id,
        value
      })),
      pagination: {
        pageIndex: tableState.page,
        pageSize: tableState.pageSize
      }
    },
    onSortingChange: tableState.setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    manualPagination: true,
    manualSorting: true,
    manualFiltering: true
  });

  // ========================================
  // EVENT HANDLERS
  // ========================================

  const handlePageChange = (event, newPage) => {
    tableState.setPage(newPage);
  };

  const handlePageSizeChange = (event) => {
    tableState.setPageSize(parseInt(event.target.value, 10));
  };

  const handleRowClickInternal = (row) => {
    if (onRowClick) {
      onRowClick(row.original);
    }
  };

  // ========================================
  // RENDER HELPERS
  // ========================================

  const renderTableHeader = () => (
    <TableHead
      sx={{
        position: stickyHeader ? 'sticky' : 'static',
        top: 0,
        zIndex: 10
      }}
    >
      {table.getHeaderGroups().map((headerGroup) => (
        <Fragment key={headerGroup.id}>
          {/* Header Row */}
          <TableRow>
            {headerGroup.headers.map((header) => (
              <TableCell
                key={header.id}
                align={header.column.columnDef.align || 'center'}
                sx={{
                  fontWeight: 'bold',
                  backgroundColor: 'var(--tba-th-bg)',
                  color: 'var(--tba-th-text)',
                  py: compact ? 1.5 : 1.5,
                  px: compact ? 1 : 2,
                  minWidth: header.column.columnDef.minWidth || 100,
                  width: header.column.columnDef.width,
                  maxWidth: header.column.columnDef.maxWidth,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden'
                }}
              >
                {header.isPlaceholder ? null : (
                  header.column.getCanSort() ? (
                    <TableSortLabel
                      active={header.column.getIsSorted() !== false}
                      direction={header.column.getIsSorted() || 'asc'}
                      onClick={header.column.getToggleSortingHandler()}
                      hideSortIcon
                      sx={{
                        width: '100%',
                        justifyContent: 'inherit',
                        cursor: 'pointer',
                        color: 'inherit !important',
                        '& .MuiTableSortLabel-icon': { display: 'none' }
                      }}
                    >
                      <Typography variant="subtitle2" component="span" sx={{ color: 'inherit' }}>
                        {flexRender(header.column.columnDef.header, header.getContext())}
                      </Typography>
                    </TableSortLabel>
                  ) : (
                    <Typography variant="subtitle2" sx={{ color: 'inherit' }}>
                      {flexRender(header.column.columnDef.header, header.getContext())}
                    </Typography>
                  )
                )}
              </TableCell>
            ))}
          </TableRow>

          {/* Filter Row */}
          {enableFiltering && (
            <TableRow sx={{ backgroundColor: 'grey.50' }}>
              {headerGroup.headers.map((header) => (
                <TableCell key={`filter-${header.id}`} sx={{ py: 1, px: '1.0rem' }}>
                  {header.column.getCanFilter() ? (
                    <ColumnFilter
                      column={header.column.columnDef}
                      value={tableState.columnFilters[header.column.id] || ''}
                      onChange={(value) => tableState.setFilter(header.column.id, value)}
                    />
                  ) : null}
                </TableCell>
              ))}
            </TableRow>
          )}
        </Fragment>
      ))}
    </TableHead>
  );

  const renderTableBody = () => {
    if (isLoading) {
      return (
        <TableBody>
          <TableRow>
            <TableCell colSpan={columns.length} align="center" sx={{ py: '5.0rem' }}>
              <CircularProgress />
              <Typography variant="body2" color="text.secondary" sx={{ mt: '1.0rem' }}>
                جاري التحميل...
              </Typography>
            </TableCell>
          </TableRow>
        </TableBody>
      );
    }

    if (!data || data.length === 0) {
      return (
        <TableBody>
          <TableRow>
            <TableCell colSpan={columns.length} align="center" sx={{ py: '5.0rem' }}>
              <Typography variant="h6" color="text.secondary">
                {emptyMessage}
              </Typography>
            </TableCell>
          </TableRow>
        </TableBody>
      );
    }

    return (
      <TableBody>
        {table.getRowModel().rows.map((row) => (
          <TableRow
            key={row.id}
            hover
            onClick={() => handleRowClickInternal(row)}
            sx={{
              cursor: onRowClick ? 'pointer' : 'default',
              '&:hover': {
                backgroundColor: onRowClick ? 'action.hover' : 'inherit'
              }
            }}
          >
            {row.getVisibleCells().map((cell) => (
              <TableCell
                key={cell.id}
                align={cell.column.columnDef.align || 'center'}
                sx={{
                  py: compact ? 1.3 : 1.2,
                  px: compact ? 1 : 2,
                  whiteSpace: 'normal',
                  overflowWrap: 'anywhere',
                  overflow: 'hidden'
                }}
              >
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    );
  };

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box>
      {/* Active Filters Display */}
      {enableFiltering && tableState.hasActiveFilters && (
        <Stack direction="row" spacing={1} sx={{ mb: '1.0rem', flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center' }}>
            الفلاتر النشطة:
          </Typography>
          {Object.entries(tableState.columnFilters).map(([columnId, value]) => {
            const column = columns.find((col) => col.accessorKey === columnId);
            return (
              <Chip
                key={columnId}
                label={`${column?.header || columnId}: ${value}`}
                size="small"
                onDelete={() => tableState.setFilter(columnId, '')}
                color="primary"
                variant="outlined"
              />
            );
          })}
          <Chip label="مسح الكل" size="small" onClick={tableState.clearFilters} color="error" variant="outlined" icon={<ClearIcon />} />
        </Stack>
      )}

      {/* Table Container */}
      <TableContainer
        component={Paper}
        sx={{
          minHeight: disableInternalScroll ? 'auto' : minHeight,
          maxHeight: disableInternalScroll ? 'none' : maxHeight,
          overflowX: 'visible',
          overflowY: disableInternalScroll ? 'visible' : 'auto',
          '&::-webkit-scrollbar': {
            width: '0.375rem',
            height: '0.375rem'
          },
          '&::-webkit-scrollbar-thumb': {
            backgroundColor: 'grey.400',
            borderRadius: '0.375rem'
          }
        }}
      >
        <Table
          stickyHeader={disableInternalScroll ? false : stickyHeader}
          size={compact ? 'small' : tableSize}
          sx={{ tableLayout: 'auto', width: '100%', borderCollapse: 'collapse', borderSpacing: 0, minWidth: '100%' }}
        >
          {renderTableHeader()}
          {renderTableBody()}
        </Table>
      </TableContainer>

      {/* Pagination */}
      {enablePagination && !isLoading && data.length > 0 && (
        <TablePagination
          component="div"
          count={totalCount}
          page={tableState.page}
          onPageChange={handlePageChange}
          rowsPerPage={tableState.pageSize}
          onRowsPerPageChange={handlePageSizeChange}
          rowsPerPageOptions={rowsPerPageOptions}
          labelRowsPerPage="عدد الصفوف:"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
          sx={{
            borderTop: 1,
            borderColor: 'divider',
            '.MuiTablePagination-selectLabel, .MuiTablePagination-displayedRows': {
              mb: 0
            }
          }}
        />
      )}
    </Box>
  );
};

// ============================================================================
// PROP TYPES
// ============================================================================

GenericDataTable.propTypes = {
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      accessorKey: PropTypes.string.isRequired,
      header: PropTypes.string.isRequired,
      cell: PropTypes.func,
      enableSorting: PropTypes.bool,
      enableColumnFilter: PropTypes.bool,
      align: PropTypes.oneOf(['left', 'center', 'right']),
      minWidth: PropTypes.number,
      width: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      maxWidth: PropTypes.number,
      meta: PropTypes.shape({
        filterType: PropTypes.oneOf(['text', 'number', 'none'])
      })
    })
  ).isRequired,
  data: PropTypes.array.isRequired,
  totalCount: PropTypes.number.isRequired,
  isLoading: PropTypes.bool,
  tableState: PropTypes.shape({
    page: PropTypes.number.isRequired,
    pageSize: PropTypes.number.isRequired,
    sorting: PropTypes.array.isRequired,
    columnFilters: PropTypes.object.isRequired,
    setPage: PropTypes.func.isRequired,
    setPageSize: PropTypes.func.isRequired,
    setSorting: PropTypes.func.isRequired,
    setFilter: PropTypes.func.isRequired,
    clearFilters: PropTypes.func.isRequired,
    hasActiveFilters: PropTypes.bool.isRequired
  }).isRequired,
  enableFiltering: PropTypes.bool,
  enableSorting: PropTypes.bool,
  enablePagination: PropTypes.bool,
  stickyHeader: PropTypes.bool,
  minHeight: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  maxHeight: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  disableInternalScroll: PropTypes.bool,
  onRowClick: PropTypes.func,
  emptyMessage: PropTypes.string,
  rowsPerPageOptions: PropTypes.arrayOf(PropTypes.number)
};

export default GenericDataTable;


