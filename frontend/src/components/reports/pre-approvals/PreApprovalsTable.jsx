import PropTypes from 'prop-types';
import { useMemo, useState } from 'react';
import { Typography } from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import NumbersIcon from '@mui/icons-material/Numbers';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import EventIcon from '@mui/icons-material/Event';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import PreAuthStatusChip from './PreAuthStatusChip';
import { formatCurrency } from 'utils/formatters';
import { UnifiedMedicalTable } from 'components/common';

/**
 * PreApprovalsTable Component
 *
 * Displays pre-approvals in a paginated data grid
 */
const PreApprovalsTable = ({ preApprovals, loading, totalCount, page, rowsPerPage, onPageChange, onRowsPerPageChange }) => {
  const [sortBy, setSortBy] = useState('referenceNumber');
  const [sortDirection, setSortDirection] = useState('desc');

  const columns = [
    {
      id: 'referenceNumber',
      label: 'رقم المرجع',
      minWidth: '9.375rem',
      sortable: true,
      icon: <NumbersIcon fontSize="small" />
    },
    {
      id: 'memberName',
      label: 'المؤمن عليه',
      minWidth: '11.25rem',
      sortable: true,
      icon: <PersonIcon fontSize="small" />
    },
    {
      id: 'employerName',
      label: 'الشريك',
      minWidth: '10.625rem',
      sortable: true,
      icon: <BusinessIcon fontSize="small" />
    },
    {
      id: 'providerName',
      label: 'مقدم الخدمة',
      minWidth: '10.625rem',
      sortable: true,
      icon: <LocalHospitalIcon fontSize="small" />
    },
    {
      id: 'serviceName',
      label: 'الخدمة الطبية',
      minWidth: '10.625rem',
      sortable: true,
      icon: <MedicalServicesIcon fontSize="small" />
    },
    {
      id: 'status',
      label: 'الحالة',
      minWidth: '8.75rem',
      align: 'center',
      sortable: true
    },
    {
      id: 'requestedAmount',
      label: 'المبلغ المطلوب',
      minWidth: '8.75rem',
      align: 'right',
      sortable: true,
      icon: <AttachMoneyIcon fontSize="small" />
    },
    {
      id: 'approvedAmount',
      label: 'المبلغ المعتمد',
      minWidth: '8.75rem',
      align: 'right',
      sortable: true,
      icon: <AttachMoneyIcon fontSize="small" />
    },
    {
      id: 'requestDate',
      label: 'تاريخ الطلب',
      minWidth: '8.125rem',
      align: 'center',
      sortable: true,
      icon: <EventIcon fontSize="small" />
    },
    {
      id: 'validUntil',
      label: 'صالح حتى',
      minWidth: '8.125rem',
      align: 'center',
      sortable: true,
      icon: <EventIcon fontSize="small" />
    }
  ];

  const formatDate = (value) => {
    if (!value) return '—';
    try {
      return new Date(value).toLocaleDateString('en-US');
    } catch {
      return value;
    }
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

  const getComparator = (orderKey, direction) => {
    return direction === 'desc' ? (a, b) => descendingComparator(a, b, orderKey) : (a, b) => -descendingComparator(a, b, orderKey);
  };

  const sortedRows = useMemo(() => {
    return [...preApprovals].sort(getComparator(sortBy, sortDirection));
  }, [preApprovals, sortBy, sortDirection]);

  const paginatedRows = sortedRows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const handleSort = (columnId, direction) => {
    setSortBy(columnId);
    setSortDirection(direction);
  };

  const renderCell = (row, column) => {
    const value = row[column.id];

    if (column.id === 'referenceNumber') {
      return (
        <Typography variant="body2" fontWeight={500}>
          {value || '—'}
        </Typography>
      );
    }

    if (column.id === 'status') {
      return <PreAuthStatusChip status={value} />;
    }

    if (column.id === 'requestedAmount' || column.id === 'approvedAmount') {
      return <Typography variant="body2">{value != null ? formatCurrency(value) : '—'}</Typography>;
    }

    if (column.id === 'requestDate' || column.id === 'validUntil') {
      return formatDate(value);
    }

    return value ?? '—';
  };

  return (
    <UnifiedMedicalTable
        columns={columns}
        rows={paginatedRows}
        loading={loading}
        totalCount={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={onPageChange}
        onRowsPerPageChange={onRowsPerPageChange}
        rowsPerPageOptions={[10, 25, 50, 100]}
        sortBy={sortBy}
        sortDirection={sortDirection}
        onSort={handleSort}
        renderCell={renderCell}
        getRowKey={(row) => row.id}
        emptyMessage="لا توجد موافقات مسبقة متاحة حاليًا"
        emptyIcon={DescriptionIcon}
        loadingMessage="جارِ تحميل الموافقات المسبقة..."
        size="small"
      />
  );
};

PreApprovalsTable.propTypes = {
  preApprovals: PropTypes.array.isRequired,
  loading: PropTypes.bool,
  totalCount: PropTypes.number.isRequired,
  page: PropTypes.number.isRequired,
  rowsPerPage: PropTypes.number.isRequired,
  onPageChange: PropTypes.func.isRequired,
  onRowsPerPageChange: PropTypes.func.isRequired
};

PreApprovalsTable.defaultProps = {
  loading: false
};

export default PreApprovalsTable;
