import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Typography,
  Skeleton,
  TablePagination,
  TableSortLabel,
  Chip
} from '@mui/material';
import EventIcon from '@mui/icons-material/Event';

/**
 * Safe string renderer with fallback
 */
const safeString = (value) => {
  if (value == null || value === '') return '—';
  return String(value);
};

/**
 * Format date for display (null-safe)
 */
const formatDate = (dateString) => {
  if (!dateString) return '—';
  try {
    const d = new Date(dateString);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch {
    return dateString;
  }
};

/**
 * Table columns configuration
 * All columns are null-safe with fallback rendering
 */
const COLUMNS = [
  { id: 'id', label: 'رقم الزيارة', minWidth: '5.625rem', align: 'center', format: safeString, sortable: false },
  { id: 'visitDate', label: 'تاريخ الزيارة', minWidth: '6.875rem', align: 'center', format: formatDate, sortable: true },
  { id: 'memberName', label: 'اسم العضو', minWidth: '8.75rem', format: safeString, sortable: true },
  { id: 'employerName', label: 'الشريك', minWidth: '8.75rem', format: safeString, sortable: true },
  { id: 'providerName', label: 'مقدم الخدمة', minWidth: '8.75rem', format: safeString, sortable: true },
  { id: 'servicesCount', label: 'عدد الخدمات', minWidth: '5.625rem', align: 'center', format: safeString, sortable: true },
  { id: 'diagnosis', label: 'التشخيص', minWidth: '9.375rem', format: safeString, sortable: false },
  { id: 'hasClaim', label: 'مطالبة', minWidth: '5.0rem', align: 'center', sortable: false },
  { id: 'createdAt', label: 'تاريخ الإنشاء', minWidth: '6.875rem', align: 'center', format: formatDate, sortable: true }
];

/**
 * Loading skeleton rows
 */
const SkeletonRows = ({ count = 5 }) => (
  <>
    {Array.from({ length: count }).map((_, index) => (
      <TableRow key={index}>
        {COLUMNS.map((column) => (
          <TableCell key={column.id} align={column.align}>
            <Skeleton variant="text" width={column.minWidth - 20} />
          </TableCell>
        ))}
      </TableRow>
    ))}
  </>
);

/**
 * Empty state component
 */
const EmptyState = () => (
  <TableRow>
    <TableCell colSpan={COLUMNS.length} align="center" sx={{ py: '4.0rem' }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1.0rem' }}>
        <EventIcon sx={{ fontSize: '4.0rem', color: 'text.disabled' }} />
        <Typography variant="h6" color="text.secondary">
          لا توجد زيارات
        </Typography>
        <Typography variant="body2" color="text.disabled">
          لم يتم العثور على زيارات مطابقة للفلاتر المحددة
        </Typography>
      </Box>
    </TableCell>
  </TableRow>
);

/**
 * VisitsTable Component
 *
 * MUI Table for displaying visits with sticky header and sorting
 *
 * @param {Array} visits - Visits data
 * @param {boolean} loading - Loading state
 * @param {number} totalCount - Total filtered count
 * @param {number} page - Current page
 * @param {number} rowsPerPage - Rows per page
 * @param {Function} onPageChange - Page change handler
 * @param {Function} onRowsPerPageChange - Rows per page change handler
 */
const VisitsTable = ({ visits, loading, totalCount, page, rowsPerPage, onPageChange, onRowsPerPageChange }) => {
  // Sorting state
  const [orderBy, setOrderBy] = useState('visitDate');
  const [order, setOrder] = useState('desc');

  /**
   * Handle sort request
   */
  const handleRequestSort = (property) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  /**
   * Sort comparator
   */
  const getComparator = (order, orderBy) => {
    return order === 'desc' ? (a, b) => descendingComparator(a, b, orderBy) : (a, b) => -descendingComparator(a, b, orderBy);
  };

  const descendingComparator = (a, b, orderBy) => {
    const aVal = a[orderBy] ?? '';
    const bVal = b[orderBy] ?? '';
    if (bVal < aVal) return -1;
    if (bVal > aVal) return 1;
    return 0;
  };

  /**
   * Render cell value based on column type
   */
  const renderCellValue = (visit, column) => {
    const value = visit[column.id];

    // Has Claim column - render chip
    if (column.id === 'hasClaim') {
      return value ? (
        <Chip label="نعم" size="small" color="success" variant="outlined" />
      ) : (
        <Chip label="لا" size="small" color="default" variant="outlined" />
      );
    }

    // Apply format function if exists
    if (column.format) {
      return column.format(value);
    }

    return value ?? '—';
  };

  // Sort and paginate visits
  const sortedVisits = [...visits].sort(getComparator(order, orderBy));
  const paginatedVisits = sortedVisits.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  return (
    <Paper sx={{ width: '100%', overflow: 'hidden' }}>
      <TableContainer sx={{ maxHeight: '31.25rem' }}>
        <Table stickyHeader aria-label="visits table" size="small">
          <TableHead>
            <TableRow>
              {COLUMNS.map((column) => (
                <TableCell
                  key={column.id}
                  align={column.align}
                  sx={{
                    minWidth: column.minWidth,
                    fontWeight: 600,
                    backgroundColor: 'background.paper',
                    borderBottom: '1.0rem',
                    borderColor: 'divider'
                  }}
                >
                  {column.sortable ? (
                    <TableSortLabel
                      active={orderBy === column.id}
                      direction={orderBy === column.id ? order : 'asc'}
                      onClick={() => handleRequestSort(column.id)}
                    >
                      {column.label}
                    </TableSortLabel>
                  ) : (
                    column.label
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <SkeletonRows count={rowsPerPage} />
            ) : paginatedVisits.length === 0 ? (
              <EmptyState />
            ) : (
              paginatedVisits.map((visit) => (
                <TableRow
                  hover
                  key={visit.id}
                  sx={{
                    '&:hover': {
                      backgroundColor: 'action.hover'
                    }
                  }}
                >
                  {COLUMNS.map((column) => (
                    <TableCell key={column.id} align={column.align}>
                      {renderCellValue(visit, column)}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Pagination */}
      <TablePagination
        component="div"
        count={totalCount}
        page={page}
        onPageChange={onPageChange}
        rowsPerPage={rowsPerPage}
        onRowsPerPageChange={onRowsPerPageChange}
        rowsPerPageOptions={[10, 25, 50, 100]}
        labelRowsPerPage="صفوف لكل صفحة:"
        labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
        sx={{
          borderTop: 1,
          borderColor: 'divider',
          '& .MuiTablePagination-selectLabel, & .MuiTablePagination-displayedRows': {
            fontWeight: 500
          }
        }}
      />
    </Paper>
  );
};

VisitsTable.propTypes = {
  visits: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number.isRequired,
  page: PropTypes.number.isRequired,
  rowsPerPage: PropTypes.number.isRequired,
  onPageChange: PropTypes.func.isRequired,
  onRowsPerPageChange: PropTypes.func.isRequired
};

export default VisitsTable;
