import PropTypes from 'prop-types';
import { useState, useMemo } from 'react';
import { Typography, Box, Stack, Chip, Table, TableHead, TableBody, TableRow, TableCell } from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import EventIcon from '@mui/icons-material/Event';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import UpdateIcon from '@mui/icons-material/Update';

import ClaimStatusChip from './ClaimStatusChip';
import { UnifiedMedicalTable } from 'components/common';

/**
 * Format currency in LYD
 */
const formatCurrency = (amount) => {
  if (amount == null) return '—';
  return (
    new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount) + ' د.ل'
  );
};

/**
 * Format date for display
 */
const formatDate = (dateString) => {
  if (!dateString) return '—';
  try {
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date(dateString));
  } catch {
    return dateString;
  }
};

/**
 * Safe string renderer with fallback
 */
const safeString = (value) => {
  if (value == null || value === '') return '—';
  return String(value);
};

/**
 * Table columns configuration
 * All columns are null-safe with fallback rendering
 */
const COLUMNS = [
  {
    id: 'id',
    label: 'رقم المطالبة',
    minWidth: '7.5rem',
    align: 'center',
    format: safeString,
    sortable: true,
    icon: <ReceiptLongIcon fontSize="small" />
  },
  {
    id: 'memberName',
    label: 'اسم المؤمن عليه',
    minWidth: '11.25rem',
    format: safeString,
    sortable: true,
    icon: <PersonIcon fontSize="small" />
  },
  {
    id: 'employerName',
    label: 'الشريك',
    minWidth: '10.625rem',
    format: safeString,
    sortable: true,
    icon: <BusinessIcon fontSize="small" />
  },
  {
    id: 'providerName',
    label: 'مقدم الخدمة',
    minWidth: '10.625rem',
    format: safeString,
    sortable: true,
    icon: <LocalHospitalIcon fontSize="small" />
  },
  { id: 'status', label: 'الحالة', minWidth: '8.125rem', align: 'center', sortable: true },
  {
    id: 'requestedAmount',
    label: 'المبلغ المطلوب',
    minWidth: '8.75rem',
    align: 'right',
    format: formatCurrency,
    sortable: true,
    icon: <AttachMoneyIcon fontSize="small" />
  },
  {
    id: 'approvedAmount',
    label: 'المبلغ المعتمد',
    minWidth: '8.75rem',
    align: 'right',
    format: formatCurrency,
    sortable: true,
    icon: <AttachMoneyIcon fontSize="small" />
  },
  {
    id: 'visitDate',
    label: 'تاريخ الزيارة',
    minWidth: '8.125rem',
    align: 'center',
    format: formatDate,
    sortable: true,
    icon: <EventIcon fontSize="small" />
  },
  {
    id: 'updatedAt',
    label: 'آخر تحديث',
    minWidth: '8.125rem',
    align: 'center',
    format: formatDate,
    sortable: true,
    icon: <UpdateIcon fontSize="small" />
  }
];

/**
 * ClaimsTable Component
 *
 * MUI Table for displaying claims with sticky header
 *
 * @param {Array} claims - Claims data
 * @param {boolean} loading - Loading state
 * @param {number} totalCount - Total filtered count
 * @param {number} page - Current page
 * @param {number} rowsPerPage - Rows per page
 * @param {Function} onPageChange - Page change handler
 * @param {Function} onRowsPerPageChange - Rows per page change handler
 */
const ClaimsTable = ({ claims, loading, totalCount, page, rowsPerPage, onPageChange, onRowsPerPageChange }) => {
  // Sorting state
  const [orderBy, setOrderBy] = useState('id');
  const [order, setOrder] = useState('desc');

  const handleSort = (property) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const descendingComparator = (a, b, orderKey) => {
    const aVal = a[orderKey];
    const bVal = b[orderKey];

    if (aVal == null && bVal == null) return 0;
    if (aVal == null) return 1;
    if (bVal == null) return -1;

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return bVal - aVal;
    }

    return String(bVal).localeCompare(String(aVal), 'ar');
  };

  const getComparator = (orderKey, orderDirection) => {
    return orderDirection === 'desc' ? (a, b) => descendingComparator(a, b, orderKey) : (a, b) => -descendingComparator(a, b, orderKey);
  };

  const sortedClaims = useMemo(() => {
    return [...claims].sort(getComparator(orderBy, order));
  }, [claims, orderBy, order]);

  const renderCellValue = (claim, column) => {
    const value = claim[column.id];
    if (column.id === 'status') {
      return <ClaimStatusChip status={value} />;
    }
    if (column.format) {
      return column.format(value);
    }
    return value ?? '—';
  };

  const renderExpandedRow = (row) => {
    const lines = row._raw?.lines || [];
    if (!lines.length) {
      return <Typography variant="caption" color="text.secondary">لا توجد خدمات مسجلة</Typography>;
    }
    
    return (
      <Box sx={{ width: '100%', mb: 1, border: '1px solid rgba(0,0,0,0.05)', borderRadius: 1, overflow: 'hidden' }}>
          <Box sx={{
              flexShrink: 0, px: '1.25rem', py: 0.75, bgcolor: '#E8F5F1',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              borderBottom: `1px solid rgba(0,0,0,0.12)`
          }}>
              <Typography variant="subtitle2" fontWeight={900} color="#0D4731" sx={{ fontSize: '0.85rem' }}>
                  بنود الخدمة
              </Typography>
              <Chip size="small" variant="outlined" label={`${lines.length} بند`} sx={{ fontWeight: 700, fontSize: '0.75rem' }} />
          </Box>
          <Table size="small" sx={{ minWidth: '43.75rem' }}>
              <TableHead>
                  <TableRow>
                      <TableCell align="right" width={280} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>الخدمة الطبية</TableCell>
                      <TableCell align="center" width={45} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>الكمية</TableCell>
                      <TableCell align="center" width={70} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>سعر الوحدة</TableCell>
                      <TableCell align="center" width={60} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>التحمل %</TableCell>
                      <TableCell align="center" width={90} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>سقف المنفعة</TableCell>
                      <TableCell align="center" width={90} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>الرصيد المتبقي</TableCell>
                      <TableCell align="center" width={100} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>شركة / مشترك</TableCell>
                      <TableCell align="center" width={75} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>المرفوض</TableCell>
                      <TableCell align="center" width={80} sx={{ fontWeight: 800, py: 1, bgcolor: '#fcfcfc' }}>الإجمالي</TableCell>
                  </TableRow>
              </TableHead>
              <TableBody>
                  {lines.map((line) => (
                      <TableRow key={line.id} hover sx={{ '& td': { py: 0.6, px: '0.75rem' }, ...(line.rejected && { bgcolor: 'rgba(211, 47, 47, 0.05)' }) }}>
                          <TableCell align="right">
                              <Stack spacing={0.5} alignItems="flex-start">
                                  <Typography variant="body2" sx={{ fontWeight: 800, fontSize: '0.8rem', color: line.rejected ? 'error.main' : 'text.primary' }}>
                                      {line.serviceName}
                                  </Typography>
                                  {line.serviceCategoryName && (
                                      <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'text.secondary', fontWeight: 700 }}>
                                          {line.serviceCategoryName}
                                      </Typography>
                                  )}
                                  {line.rejectionReason && (
                                       <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'error.main', fontWeight: 800 }}>
                                           السبب: {line.rejectionReason}
                                       </Typography>
                                  )}
                              </Stack>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontWeight: 800, fontSize: '0.85rem' }}>{line.quantity}</Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontWeight: 800, fontSize: '0.85rem' }}>{line.unitPrice?.toFixed(2)}</Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 700, color: 'text.secondary' }}>
                                  {line.coveragePercent != null ? `${line.coveragePercent}%` : '100%'}
                              </Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 800, color: 'error.main' }}>
                                  {line.benefitLimit > 0 ? line.benefitLimit.toFixed(2) : '0.00'}
                              </Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 800, color: 'primary.main' }}>
                                  {line.benefitLimit > 0 ? (line.remainingAmount ?? 0).toFixed(2) : '0.00'}
                              </Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Stack spacing={0} alignItems="center">
                                  <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 900, color: 'success.main', lineHeight: 1.2 }}>
                                      {line.companyShare?.toFixed(2) || '0.00'}
                                  </Typography>
                                  <Typography variant="caption" sx={{ fontSize: '0.75rem', fontWeight: 900, color: 'warning.dark', lineHeight: 1.2 }}>
                                      {line.patientShare?.toFixed(2) || '0.00'}
                                  </Typography>
                              </Stack>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 800, color: 'error.main' }}>
                                  {(line.rejected ? line.totalPrice : line.refusedAmount)?.toFixed(2) || '0.00'}
                              </Typography>
                          </TableCell>
                          <TableCell align="center">
                              <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 900, color: 'text.primary' }}>
                                  {line.totalPrice?.toFixed(2) || '0.00'}
                              </Typography>
                          </TableCell>
                      </TableRow>
                  ))}
              </TableBody>
          </Table>
      </Box>
    );
  };

  const paginatedClaims = sortedClaims.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  return (
    <UnifiedMedicalTable
      columns={COLUMNS}
      rows={paginatedClaims}
      loading={loading}
      totalCount={totalCount}
      page={page}
      rowsPerPage={rowsPerPage}
      onPageChange={onPageChange}
      onRowsPerPageChange={onRowsPerPageChange}
      rowsPerPageOptions={[10, 25, 50, 100]}
      sortBy={orderBy}
      sortDirection={order}
      onSort={handleSort}
      renderCell={renderCellValue}
      renderExpandedRow={renderExpandedRow}
      isRowExpandable={(row) => row._raw?.lines?.length > 0}
      getRowKey={(claim) => claim.id}
      emptyMessage="لا توجد مطالبات مطابقة للفلاتر المحددة"
      emptyIcon={DescriptionIcon}
      loadingMessage="جارِ تحميل المطالبات..."
      size="small"
    />
  );
};

ClaimsTable.propTypes = {
  claims: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number.isRequired,
  page: PropTypes.number.isRequired,
  rowsPerPage: PropTypes.number.isRequired,
  onPageChange: PropTypes.func.isRequired,
  onRowsPerPageChange: PropTypes.func.isRequired
};

export default ClaimsTable;



