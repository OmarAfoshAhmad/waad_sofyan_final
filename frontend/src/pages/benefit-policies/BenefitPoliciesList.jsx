import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import dayjs from 'dayjs';
import {
  Add as AddIcon,
  Close as CloseIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  FilterAltOff as FilterAltOffIcon,
  People as PeopleIcon,
  Policy as PolicyIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  Undo as UndoIcon,
  Visibility as VisibilityIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import PermissionGuard from 'components/PermissionGuard';
import { UnifiedMedicalTable } from 'components/common';
import { ActionConfirmDialog, ModernPageHeader, SoftDeleteToggle } from 'components/tba';
import axiosClient from 'utils/axios';
import {
  deleteBenefitPolicy,
  getBenefitPolicies,
  getDeletedBenefitPolicies,
  permanentDeleteBenefitPolicy,
  restoreBenefitPolicy
} from 'services/api/benefit-policies.service';

const STATUS_CONFIG = {
  DRAFT:     { label: 'مسودة',      color: 'default' },
  ACTIVE:    { label: 'نشط',        color: 'success' },
  INACTIVE:  { label: 'غير نشط',    color: 'default' },
  EXPIRED:   { label: 'منتهية',     color: 'warning' },
  SUSPENDED: { label: 'معلقة',      color: 'warning' },
  CANCELLED: { label: 'ملغاة',      color: 'error'   }
};

const BenefitPoliciesList = () => {
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDirection, setSortDirection] = useState('desc');
  const [showDeleted, setShowDeleted] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filters, setFilters] = useState({ employerId: '', status: '' });
  const [employers, setEmployers] = useState([]);
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    title: '',
    message: '',
    confirmColor: 'primary',
    onConfirm: null
  });

  const fetchEmployers = useCallback(async () => {
    try {
      const response = await axiosClient.get('/employers/selectors');
      setEmployers(response.data?.data || []);
    } catch (error) {
      console.error('[BenefitPolicies] Failed to load employer selectors:', error);
    }
  }, []);

  const fetchPolicies = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page: 0,
        size: 200,
        sortBy,
        sortDir: sortDirection.toUpperCase(),
        ...(filters.employerId && { employerId: filters.employerId })
      };

      const response = showDeleted ? await getDeletedBenefitPolicies(params) : await getBenefitPolicies(params);
      let content = response?.content || [];

      if (searchTerm.trim()) {
        const query = searchTerm.trim().toLowerCase();
        content = content.filter(
          (item) =>
            item.name?.toLowerCase().includes(query) ||
            item.policyCode?.toLowerCase().includes(query) ||
            item.employerName?.toLowerCase().includes(query)
        );
      }

      if (filters.status) {
        content = content.filter((item) => item.status === filters.status);
      }

      if (sortBy) {
        content = [...content].sort((left, right) => {
          let leftValue = left?.[sortBy] ?? '';
          let rightValue = right?.[sortBy] ?? '';

          if (typeof leftValue === 'string') leftValue = leftValue.toLowerCase();
          if (typeof rightValue === 'string') rightValue = rightValue.toLowerCase();

          if (leftValue < rightValue) return sortDirection === 'asc' ? -1 : 1;
          if (leftValue > rightValue) return sortDirection === 'asc' ? 1 : -1;
          return 0;
        });
      }

      const startIndex = page * rowsPerPage;
      setTotalCount(content.length);
      setPolicies(content.slice(startIndex, startIndex + rowsPerPage));
    } catch (error) {
      console.error('[BenefitPolicies] Failed to fetch policies:', error);
      const apiMessage = error?.response?.data?.message || error?.message;
      enqueueSnackbar(apiMessage || 'فشل تحميل سياسات المنافع', { variant: 'error' });
      setPolicies([]);
      setTotalCount(0);
    } finally {
      setLoading(false);
    }
  }, [enqueueSnackbar, filters.employerId, filters.status, page, rowsPerPage, searchTerm, showDeleted, sortBy, sortDirection]);

  useEffect(() => {
    fetchEmployers();
  }, [fetchEmployers]);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  const handleNavigateAdd = useCallback(() => navigate('/benefit-policies/create'), [navigate]);
  const handleNavigateView = useCallback((id) => navigate(`/benefit-policies/${id}`), [navigate]);
  const handleNavigateEdit = useCallback((id) => navigate(`/benefit-policies/edit/${id}`), [navigate]);

  const handleSort = useCallback((columnId) => {
    setSortDirection((prev) => (sortBy === columnId ? (prev === 'asc' ? 'desc' : 'asc') : 'asc'));
    setSortBy(columnId);
    setPage(0);
  }, [sortBy]);

  const handleResetFilters = useCallback(() => {
    setSearchTerm('');
    setFilters({ employerId: '', status: '' });
    setPage(0);
  }, []);

  const closeDialog = useCallback(() => {
    setConfirmDialog((prev) => ({ ...prev, open: false }));
  }, []);

  const handleDelete = useCallback(
    (policy) => {
      setConfirmDialog({
        open: true,
        title: 'تأكيد الحذف',
        message: `هل أنت متأكد من حذف سياسة المنافع "${policy.name || policy.policyCode}"؟\n\nسيتم نقلها إلى المحذوفات ويمكن استعادتها لاحقاً.`,
        confirmColor: 'error',
        onConfirm: async () => {
          try {
            await deleteBenefitPolicy(policy.id);
            enqueueSnackbar('تم حذف سياسة المنافع بنجاح', { variant: 'success' });
            closeDialog();
            fetchPolicies();
          } catch (error) {
            console.error('[BenefitPolicies] Delete failed:', error);
            const apiMessage = error?.response?.data?.message || error?.message;
            enqueueSnackbar(apiMessage || 'فشل حذف سياسة المنافع', { variant: 'error' });
          }
        }
      });
    },
    [closeDialog, enqueueSnackbar, fetchPolicies]
  );

  const handleRestore = useCallback(
    (policy) => {
      setConfirmDialog({
        open: true,
        title: 'استعادة السياسة',
        message: `هل تريد استعادة سياسة المنافع "${policy.name || policy.policyCode}"؟`,
        confirmColor: 'success',
        onConfirm: async () => {
          try {
            await restoreBenefitPolicy(policy.id);
            enqueueSnackbar('تمت استعادة سياسة المنافع بنجاح', { variant: 'success' });
            closeDialog();
            fetchPolicies();
          } catch (error) {
            console.error('[BenefitPolicies] Restore failed:', error);
            const apiMessage = error?.response?.data?.message || error?.message;
            enqueueSnackbar(apiMessage || 'فشل استعادة سياسة المنافع', { variant: 'error' });
          }
        }
      });
    },
    [closeDialog, enqueueSnackbar, fetchPolicies]
  );

  const handlePermanentDelete = useCallback(
    (policy) => {
      setConfirmDialog({
        open: true,
        title: 'حذف نهائي',
        message: `تحذير: هل أنت متأكد من الحذف النهائي لـ "${policy.name || policy.policyCode}"؟\n\nلا يمكن التراجع عن هذا الإجراء.`,
        confirmColor: 'error',
        onConfirm: async () => {
          try {
            await permanentDeleteBenefitPolicy(policy.id);
            enqueueSnackbar('تم الحذف النهائي بنجاح', { variant: 'success' });
            closeDialog();
            fetchPolicies();
          } catch (error) {
            console.error('[BenefitPolicies] Permanent delete failed:', error);
            const apiMessage = error?.response?.data?.message || error?.message;
            enqueueSnackbar(apiMessage || 'فشل الحذف النهائي', { variant: 'error' });
          }
        }
      });
    },
    [closeDialog, enqueueSnackbar, fetchPolicies]
  );

  const columns = useMemo(
    () => [
      { id: 'policyCode', label: 'رمز السياسة', minWidth: '8.75rem', align: 'center', sortable: true },
      { id: 'name', label: 'اسم السياسة', minWidth: '16.25rem', align: 'right', sortable: true },
      { id: 'employerName', label: 'الشريك', minWidth: '13.75rem', align: 'right', sortable: true },
      { id: 'startDate', label: 'تاريخ البدء', minWidth: '8.125rem', align: 'center', sortable: true },
      { id: 'endDate', label: 'تاريخ الانتهاء', minWidth: '8.125rem', align: 'center', sortable: true },
      { id: 'status', label: 'الحالة', minWidth: '7.5rem', align: 'center', sortable: true },
      { id: 'actions', label: 'الإجراءات', minWidth: '8.125rem', align: 'center', sortable: false }
    ],
    []
  );

  const renderCell = useCallback(
    (row, column) => {
      switch (column.id) {
        case 'policyCode':
          return <Chip label={row.policyCode || '-'} size="small" variant="outlined" color="primary" />;
        case 'name':
          return (
            <Typography variant="body2" fontWeight={600}>
              {row.name || '-'}
            </Typography>
          );
        case 'employerName':
          return <Typography variant="body2">{row.employerName || '-'}</Typography>;
        case 'startDate':
          return row.startDate ? <Chip label={dayjs(row.startDate).format('YYYY-MM-DD')} size="small" variant="outlined" /> : '-';
        case 'endDate':
          return row.endDate ? <Chip label={dayjs(row.endDate).format('YYYY-MM-DD')} size="small" variant="outlined" /> : '-';
        case 'status': {
          const status = STATUS_CONFIG[row.status] || { label: row.status || '-', color: 'default' };
          return <Chip label={status.label} color={status.color} size="small" />;
        }
        case 'actions':
          return (
            <Stack direction="row" spacing={0.5} justifyContent="center">
              {showDeleted ? (
                <PermissionGuard resource="benefit_policies" action="delete">
                  <Tooltip title="استعادة">
                    <IconButton size="small" color="success" onClick={() => handleRestore(row)}>
                      <UndoIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="حذف نهائي">
                    <IconButton size="small" color="error" onClick={() => handlePermanentDelete(row)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </PermissionGuard>
              ) : (
                <>
                  <Tooltip title="عرض">
                    <IconButton size="small" color="primary" onClick={() => handleNavigateView(row.id)}>
                      <VisibilityIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <PermissionGuard resource="benefit_policies" action="update">
                    <Tooltip title="تعديل">
                      <IconButton size="small" color="info" onClick={() => handleNavigateEdit(row.id)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </PermissionGuard>
                  <PermissionGuard resource="benefit_policies" action="delete">
                    <Tooltip title="حذف">
                      <IconButton size="small" color="error" onClick={() => handleDelete(row)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </PermissionGuard>
                </>
              )}
            </Stack>
          );
        default:
          return row[column.id] ?? '-';
      }
    },
    [handleDelete, handleNavigateEdit, handleNavigateView, handlePermanentDelete, handleRestore, showDeleted]
  );

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', px: { xs: 2, sm: 3 } }}>
      <ModernPageHeader
        title="سياسات المنافع"
        subtitle="إدارة سياسات المنافع والتغطية التأمينية"
        icon={PolicyIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'سياسات المنافع' }]}
        actions={
          <Stack direction="row" spacing={1.5}>
            <SoftDeleteToggle
              showDeleted={showDeleted}
              onToggle={() => {
                setShowDeleted((prev) => !prev);
                setPage(0);
              }}
            />
            <PermissionGuard resource="benefit_policies" action="create">
              <Button variant="contained" startIcon={<AddIcon />} onClick={handleNavigateAdd}>
                إنشاء سياسة جديدة
              </Button>
            </PermissionGuard>
          </Stack>
        }
      />

      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <MainCard sx={{ mb: 1, flexShrink: 0 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems="center" sx={{ width: '100%' }}>
            <Tooltip title="تحديث">
              <IconButton onClick={fetchPolicies} color="primary" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}>
                <RefreshIcon />
              </IconButton>
            </Tooltip>

            <Chip
              icon={<PolicyIcon fontSize="small" />}
              label={`${totalCount} سياسة`}
              variant="outlined"
              color="primary"
              sx={{ height: '2.5rem', borderRadius: 1, fontWeight: 'bold', fontSize: '0.875rem', px: 1 }}
            />

            <TextField
              sx={{ flexGrow: 1, minWidth: { md: 240 } }}
              size="small"
              placeholder="بحث برمز السياسة أو الاسم أو الشريك..."
              value={searchTerm}
              onChange={(event) => {
                setSearchTerm(event.target.value);
                setPage(0);
              }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: searchTerm ? (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => {
                      setSearchTerm('');
                      setPage(0);
                    }}>
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ) : null,
                sx: { height: '2.5rem' }
              }}
            />

            <TextField
              select
              size="small"
              label="الشريك"
              value={filters.employerId}
              onChange={(event) => {
                setFilters((prev) => ({ ...prev, employerId: event.target.value }));
                setPage(0);
              }}
              sx={{ minWidth: '11.25rem', bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: '2.5rem' } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              {employers.map((employer) => (
                <MenuItem key={employer.id || employer.value} value={employer.id || employer.value}>
                  {employer.name || employer.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              size="small"
              label="الحالة"
              value={filters.status}
              onChange={(event) => {
                setFilters((prev) => ({ ...prev, status: event.target.value }));
                setPage(0);
              }}
              sx={{ minWidth: '8.125rem', bgcolor: 'background.paper' }}
              InputProps={{ sx: { height: '2.5rem' } }}
              InputLabelProps={{ shrink: true }}
            >
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              {Object.entries(STATUS_CONFIG).map(([value, config]) => (
                <MenuItem key={value} value={value}>
                  {config.label}
                </MenuItem>
              ))}
            </TextField>

            <Button variant="outlined" color="secondary" onClick={handleResetFilters} startIcon={<FilterAltOffIcon />} sx={{ minWidth: '7.5rem', height: '2.5rem' }}>
              إعادة ضبط
            </Button>
          </Stack>
        </MainCard>

        <UnifiedMedicalTable
          columns={columns}
          data={policies}
          totalCount={totalCount}
          loading={loading}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={setPage}
          onRowsPerPageChange={(value) => {
            setRowsPerPage(value);
            setPage(0);
          }}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={handleSort}
          renderCell={renderCell}
          emptyMessage={showDeleted ? 'لا توجد سياسات محذوفة' : 'لا توجد سياسات منافع مسجلة'}
          getRowKey={(row) => row.id}
          sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: 0, mb: 1 }}
          tableContainerSx={{ flexGrow: 1, minHeight: 0 }}
        />
      </Box>

      <ActionConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmColor={confirmDialog.confirmColor}
        onConfirm={confirmDialog.onConfirm}
        onClose={closeDialog}
      />
    </Box>
  );
};

export default BenefitPoliciesList;

