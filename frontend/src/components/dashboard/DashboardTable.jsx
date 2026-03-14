import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Stack,
  IconButton,
  Tooltip,
  Skeleton,
  Typography,
  TablePagination,
  MenuItem,
  Select,
  FormControl,
  InputLabel
} from '@mui/material';
import { Card, CardContent, CardHeader } from '@mui/material';
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getPaginationRowModel,
  getFilteredRowModel,
  useReactTable
} from '@tanstack/react-table';
import { ArrowUpward, ArrowDownward, Search, GetApp } from '@mui/icons-material';

/**
 * Advanced Dashboard Table Component
 * Using React Table with filtering, sorting, and pagination
 * Inspired by Mantis React Table Filtering
 */
const DashboardTable = ({ title, subtitle, data = [], columns = [], loading = false, onRowClick, enableExport = false, onExport }) => {
  const [globalFilter, setGlobalFilter] = useState('');
  const [sorting, setSorting] = useState([]);
  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10
  });

  // Transform columns to include sorting
  const tableColumns = useMemo(() => {
    return columns.map((col) => ({
      ...col,
      enableSorting: col.enableSorting !== false,
      header: ({ column }) => {
        const canSort = column.getCanSort();
        const sortDirection = column.getIsSorted();

        return (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              cursor: canSort ? 'pointer' : 'default',
              userSelect: 'none'
            }}
            onClick={column.getToggleSortingHandler()}
          >
            <Typography variant="subtitle2" fontWeight={600}>
              {col.header || col.accessorKey}
            </Typography>
            {canSort && (
              <Box sx={{ ml: 1, display: 'flex', flexDirection: 'column' }}>
                {sortDirection === 'asc' ? (
                  <ArrowUpward sx={{ fontSize: '1.0rem', color: 'primary.main' }} />
                ) : sortDirection === 'desc' ? (
                  <ArrowDownward sx={{ fontSize: '1.0rem', color: 'primary.main' }} />
                ) : (
                  <Box sx={{ display: 'flex', flexDirection: 'column', opacity: 0.3 }}>
                    <ArrowUpward sx={{ fontSize: '0.75rem' }} />
                    <ArrowDownward sx={{ fontSize: '0.75rem', mt: -0.5 }} />
                  </Box>
                )}
              </Box>
            )}
          </Box>
        );
      }
    }));
  }, [columns]);

  const table = useReactTable({
    data,
    columns: tableColumns,
    state: {
      sorting,
      globalFilter,
      pagination
    },
    onSortingChange: setSorting,
    onGlobalFilterChange: setGlobalFilter,
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    enableSorting: true,
    enableFiltering: true
  });

  if (loading) {
    return (
      <Card>
        <CardHeader title={title} subheader={subtitle} />
        <CardContent>
          <Skeleton variant="rectangular" height={400} />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader
        title={title}
        subheader={subtitle}
        action={
          enableExport &&
          onExport && (
            <Tooltip title="تصدير البيانات">
              <IconButton onClick={onExport} size="small">
                <GetApp />
              </IconButton>
            </Tooltip>
          )
        }
      />
      <CardContent sx={{ p: 0 }}>
        {/* Global Search */}
        <Box sx={{ p: '1.0rem', borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction="row" spacing={2} alignItems="center">
            <TextField
              fullWidth
              size="small"
              placeholder="بحث في جميع الأعمدة..."
              value={globalFilter ?? ''}
              onChange={(e) => setGlobalFilter(e.target.value)}
              InputProps={{
                startAdornment: <Search sx={{ mr: 1, color: 'text.secondary' }} />
              }}
            />
            <FormControl size="small" sx={{ minWidth: '7.5rem' }}>
              <InputLabel>عدد الصفوف</InputLabel>
              <Select
                value={pagination.pageSize}
                label="عدد الصفوف"
                onChange={(e) => {
                  setPagination({ ...pagination, pageSize: e.target.value, pageIndex: 0 });
                }}
              >
                <MenuItem value={10}>10</MenuItem>
                <MenuItem value={25}>25</MenuItem>
                <MenuItem value={50}>50</MenuItem>
                <MenuItem value={100}>100</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </Box>

        {/* Table */}
        <TableContainer>
          <Table stickyHeader>
            <TableHead>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableCell key={header.id} sx={{ bgcolor: 'action.hover' }}>
                      {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableHead>
            <TableBody>
              {table.getRowModel().rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length} align="center" sx={{ py: '2.0rem' }}>
                    <Typography variant="body2" color="text.secondary">
                      لا توجد بيانات
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    hover
                    sx={{
                      cursor: onRowClick ? 'pointer' : 'default',
                      '&:hover': onRowClick ? { bgcolor: 'action.hover' } : {}
                    }}
                    onClick={() => onRowClick && onRowClick(row.original)}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Pagination */}
        <Box sx={{ p: '1.0rem', borderTop: 1, borderColor: 'divider' }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="body2" color="text.secondary">
              عرض {table.getState().pagination.pageIndex * table.getState().pagination.pageSize + 1} -{' '}
              {Math.min(
                (table.getState().pagination.pageIndex + 1) * table.getState().pagination.pageSize,
                table.getFilteredRowModel().rows.length
              )}{' '}
              من {table.getFilteredRowModel().rows.length}
            </Typography>
            <TablePagination
              component="div"
              count={table.getFilteredRowModel().rows.length}
              page={pagination.pageIndex}
              onPageChange={(e, newPage) => setPagination({ ...pagination, pageIndex: newPage })}
              rowsPerPage={pagination.pageSize}
              onRowsPerPageChange={(e) => {
                setPagination({ ...pagination, pageSize: parseInt(e.target.value, 10), pageIndex: 0 });
              }}
              rowsPerPageOptions={[10, 25, 50, 100]}
              labelRowsPerPage="صفوف لكل صفحة:"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`}
            />
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
};

DashboardTable.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  data: PropTypes.array.isRequired,
  columns: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  onRowClick: PropTypes.func,
  enableExport: PropTypes.bool,
  onExport: PropTypes.func
};

export default DashboardTable;


