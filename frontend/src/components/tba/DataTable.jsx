import PropTypes from 'prop-types';
import { useMemo, useState } from 'react';

// material-ui
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Chip from '@mui/material/Chip';

// third-party
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getPaginationRowModel,
  getFilteredRowModel,
  useReactTable
} from '@tanstack/react-table';

// project imports
import MainCard from 'components/MainCard';

// assets
import EditOutlined from '@ant-design/icons/EditOutlined';
import DeleteOutlined from '@ant-design/icons/DeleteOutlined';
import PlusOutlined from '@ant-design/icons/PlusOutlined';
import ArrowUpOutlined from '@ant-design/icons/ArrowUpOutlined';
import ArrowDownOutlined from '@ant-design/icons/ArrowDownOutlined';

// ==============================|| DATA TABLE - TBA MODULE ||============================== //

export default function DataTable({
  title,
  data = [],
  columns = [],
  loading = false,
  onAdd,
  onEdit,
  onDelete,
  onRowClick,
  addButtonLabel = 'Add New',
  showActions = true,
  enableSorting = true,
  enableFiltering = true,
  enablePagination = true,
  pageSize = 10
}) {
  const [globalFilter, setGlobalFilter] = useState('');
  const [sorting, setSorting] = useState([]);
  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: pageSize
  });

  // Add actions column if needed
  const tableColumns = useMemo(() => {
    const cols = [...columns];

    if (showActions && (onEdit || onDelete)) {
      cols.push({
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => (
          <Stack direction="row" spacing={0}>
            {onEdit && (
              <Tooltip title="Edit">
                <IconButton
                  color="primary"
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    onEdit(row.original);
                  }}
                >
                  <EditOutlined />
                </IconButton>
              </Tooltip>
            )}
            {onDelete && (
              <Tooltip title="Delete">
                <IconButton
                  color="error"
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(row.original);
                  }}
                >
                  <DeleteOutlined />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        ),
        enableSorting: false
      });
    }

    return cols;
  }, [columns, showActions, onEdit, onDelete]);

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
    enableSorting,
    enableFiltering,
    manualPagination: false
  });

  return (
    <MainCard
      title={title}
      content={false}
      secondary={
        onAdd && (
          <Button variant="contained" startIcon={<PlusOutlined />} onClick={onAdd} size="small">
            {addButtonLabel}
          </Button>
        )
      }
    >
      {enableFiltering && (
        <Box sx={{ p: '1.0rem' }}>
          <TextField
            value={globalFilter ?? ''}
            onChange={(e) => setGlobalFilter(e.target.value)}
            placeholder="Search all columns..."
            size="small"
            fullWidth
          />
        </Box>
      )}

      <Box sx={{ overflowX: 'auto' }}>
        <TableContainer>
          <Table>
            <TableHead>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => {
                    const isSortable = header.column.getCanSort();
                    const sortDirection = header.column.getIsSorted();

                    return (
                      <TableCell
                        key={header.id}
                        onClick={isSortable ? header.column.getToggleSortingHandler() : undefined}
                        sx={{
                          cursor: isSortable ? 'pointer' : 'default',
                          userSelect: 'none'
                        }}
                      >
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Box>{header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}</Box>
                          {isSortable && (
                            <Box sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                              {sortDirection === 'asc' && <ArrowUpOutlined />}
                              {sortDirection === 'desc' && <ArrowDownOutlined />}
                            </Box>
                          )}
                        </Stack>
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))}
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={tableColumns.length} align="center">
                    <Typography variant="body2" color="text.secondary">
                      Loading...
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : table.getRowModel().rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={tableColumns.length} align="center">
                    <Typography variant="body2" color="text.secondary">
                      No data available
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    hover={!!onRowClick}
                    onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                    sx={{ cursor: onRowClick ? 'pointer' : 'default' }}
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
      </Box>

      {enablePagination && !loading && data.length > 0 && (
        <TablePagination
          component="div"
          count={table.getFilteredRowModel().rows.length}
          page={pagination.pageIndex}
          onPageChange={(_, newPage) => setPagination((prev) => ({ ...prev, pageIndex: newPage }))}
          rowsPerPage={pagination.pageSize}
          onRowsPerPageChange={(e) => setPagination((prev) => ({ ...prev, pageSize: parseInt(e.target.value, 10), pageIndex: 0 }))}
          rowsPerPageOptions={[5, 10, 25, 50]}
        />
      )}
    </MainCard>
  );
}

DataTable.propTypes = {
  title: PropTypes.string.isRequired,
  data: PropTypes.array,
  columns: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  onAdd: PropTypes.func,
  onEdit: PropTypes.func,
  onDelete: PropTypes.func,
  onRowClick: PropTypes.func,
  addButtonLabel: PropTypes.string,
  showActions: PropTypes.bool,
  enableSorting: PropTypes.bool,
  enableFiltering: PropTypes.bool,
  enablePagination: PropTypes.bool,
  pageSize: PropTypes.number
};


