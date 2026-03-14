import PropTypes from 'prop-types';
import { useMemo, useState } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TablePagination,
  Paper,
  Typography,
  Chip,
  Skeleton
} from '@mui/material';
import { visuallyHidden } from '@mui/utils';
import { formatNumber, formatDate, formatPercentage } from 'utils/formatters';

import { STATUS_CONFIG } from 'hooks/useBenefitPolicyReport';

/**
 * Table Column Configuration
 */
const COLUMNS = [
  { id: 'policyCode', label: 'رمز الوثيقة', labelEn: 'Code', minWidth: '6.25rem' },
  { id: 'name', label: 'اسم الوثيقة', labelEn: 'Policy Name', minWidth: '11.25rem' },
  { id: 'employerName', label: 'الشريك', labelEn: 'Employer', minWidth: '9.375rem' },
  { id: 'status', label: 'الحالة', labelEn: 'Status', minWidth: '6.25rem' },
  { id: 'memberCount', label: 'عدد الأعضاء', labelEn: 'Members', minWidth: '6.25rem', align: 'center' },
  { id: 'defaultCoveragePercent', label: 'نسبة التغطية', labelEn: 'Coverage %', minWidth: '6.25rem', align: 'center' },
  { id: 'startDate', label: 'تاريخ البدء', labelEn: 'Start Date', minWidth: '7.5rem' },
  { id: 'endDate', label: 'تاريخ الانتهاء', labelEn: 'End Date', minWidth: '7.5rem' }
];

/**
 * Comparator functions for sorting
 */
const descendingComparator = (a, b, orderBy) => {
  const aVal = a[orderBy] ?? '';
  const bVal = b[orderBy] ?? '';

  if (bVal < aVal) return -1;
  if (bVal > aVal) return 1;
  return 0;
};

const getComparator = (order, orderBy) => {
  return order === 'desc' ? (a, b) => descendingComparator(a, b, orderBy) : (a, b) => -descendingComparator(a, b, orderBy);
};

/**
 * Status Chip Component
 */
const StatusChip = ({ status }) => {
  const config = STATUS_CONFIG[status] || { label: status, color: 'default' };
  return <Chip label={config.label} color={config.color} size="small" sx={{ fontWeight: 500 }} />;
};

StatusChip.propTypes = {
  status: PropTypes.string
};

/**
 * Loading Skeleton Row
 */
const SkeletonRow = () => (
  <TableRow>
    {COLUMNS.map((col) => (
      <TableCell key={col.id}>
        <Skeleton variant="text" width="80%" />
      </TableCell>
    ))}
  </TableRow>
);

/**
 * BenefitPolicy Table Component
 *
 * Displays benefit policies in a sortable, paginated table.
 * Columns: Code, Name, Employer, Status, Members, Coverage %, Start Date, End Date
 */
const BenefitPolicyTable = ({ policies = [], loading = false }) => {
  // Sorting state
  const [order, setOrder] = useState('desc');
  const [orderBy, setOrderBy] = useState('memberCount');

  // Pagination state
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  /**
   * Handle sort request
   */
  const handleRequestSort = (property) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  /**
   * Handle page change
   */
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  /**
   * Handle rows per page change
   */
  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  /**
   * Sort and paginate data
   */
  const displayedPolicies = useMemo(() => {
    return [...policies].sort(getComparator(order, orderBy)).slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
  }, [policies, order, orderBy, page, rowsPerPage]);

  /**
   * Render cell value based on column type
   */
  const renderCellValue = (policy, columnId) => {
    switch (columnId) {
      case 'policyCode':
        return (
          <Typography variant="body2" fontWeight={500}>
            {policy.policyCode || '—'}
          </Typography>
        );
      case 'name':
        return policy.name || '—';
      case 'employerName':
        return policy.employerName || '—';
      case 'status':
        return <StatusChip status={policy.status} />;
      case 'memberCount':
        return (
          <Typography variant="body2" fontWeight={600} color={policy.memberCount === 0 ? 'warning.main' : 'text.primary'}>
            {formatNumber(policy.memberCount ?? 0)}
          </Typography>
        );
      case 'defaultCoveragePercent':
        return formatPercentage(policy.defaultCoveragePercent ?? 0);
      case 'startDate':
        return formatDate(policy.startDate);
      case 'endDate':
        return formatDate(policy.endDate);
      default:
        return policy[columnId] ?? '—';
    }
  };

  // Empty state
  if (!loading && policies.length === 0) {
    return (
      <Paper
        elevation={0}
        sx={{
          p: '2.0rem',
          textAlign: 'center',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: '0.125rem'
        }}
      >
        <Typography variant="body1" color="text.secondary">
          لا توجد وثائق منافع للعرض
        </Typography>
        <Typography variant="caption" color="text.disabled">
          No benefit policies to display
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper
      elevation={0}
      sx={{
        width: '100%',
        overflow: 'hidden',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: '0.125rem'
      }}
    >
      <TableContainer sx={{ maxHeight: '31.25rem' }}>
        <Table stickyHeader size="small" aria-label="benefit policies table">
          <TableHead>
            <TableRow>
              {COLUMNS.map((column) => (
                <TableCell
                  key={column.id}
                  align={column.align || 'right'}
                  style={{ minWidth: column.minWidth }}
                  sortDirection={orderBy === column.id ? order : false}
                  sx={{
                    fontWeight: 600,
                    backgroundColor: 'background.paper',
                    borderBottom: '2px solid',
                    borderColor: 'divider'
                  }}
                >
                  <TableSortLabel
                    active={orderBy === column.id}
                    direction={orderBy === column.id ? order : 'asc'}
                    onClick={() => handleRequestSort(column.id)}
                  >
                    <Box>
                      <Typography variant="caption" fontWeight={600}>
                        {column.label}
                      </Typography>
                      <Typography variant="caption" display="block" color="text.disabled" sx={{ fontSize: '0.75rem' }}>
                        {column.labelEn}
                      </Typography>
                    </Box>
                    {orderBy === column.id && (
                      <Box component="span" sx={visuallyHidden}>
                        {order === 'desc' ? 'sorted descending' : 'sorted ascending'}
                      </Box>
                    )}
                  </TableSortLabel>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading
              ? // Loading skeleton
                Array.from({ length: 5 }).map((_, index) => <SkeletonRow key={index} />)
              : displayedPolicies.map((policy) => (
                  <TableRow
                    hover
                    key={policy.id}
                    sx={{
                      '&:last-child td, &:last-child th': { border: 0 },
                      cursor: 'pointer'
                    }}
                  >
                    {COLUMNS.map((column) => (
                      <TableCell key={column.id} align={column.align || 'right'}>
                        {renderCellValue(policy, column.id)}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        rowsPerPageOptions={[5, 10, 25, 50]}
        component="div"
        count={policies.length}
        rowsPerPage={rowsPerPage}
        page={page}
        onPageChange={handleChangePage}
        onRowsPerPageChange={handleChangeRowsPerPage}
        labelRowsPerPage="صفوف في الصفحة:"
        labelDisplayedRows={({ from, to, count }) => `${from}–${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
        sx={{ borderTop: '1px solid', borderColor: 'divider' }}
      />
    </Paper>
  );
};

BenefitPolicyTable.propTypes = {
  policies: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.number,
      policyCode: PropTypes.string,
      name: PropTypes.string,
      employerName: PropTypes.string,
      status: PropTypes.string,
      memberCount: PropTypes.number,
      defaultCoveragePercent: PropTypes.number,
      startDate: PropTypes.string,
      endDate: PropTypes.string
    })
  ),
  loading: PropTypes.bool
};

export default BenefitPolicyTable;

