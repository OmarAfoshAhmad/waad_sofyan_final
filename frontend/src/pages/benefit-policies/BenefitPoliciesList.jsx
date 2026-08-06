import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  List,
  ListItem,
  ListItemText,
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
  Visibility as VisibilityIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon
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
  restoreBenefitPolicy,
  bulkDeleteBenefitPolicies,
  bulkRestoreBenefitPolicies
} from 'services/api/benefit-policies.service';

const STATUS_CONFIG = {
  DRAFT: { label: 'مسودة', color: 'default' },
  ACTIVE: { label: 'نشط', color: 'success' },
  INACTIVE: { label: 'غير نشط', color: 'default' },
  EXPIRED: { label: 'منتهية', color: 'warning' },
  SUSPENDED: { label: 'معلقة', color: 'warning' },
  CANCELLED: { label: 'ملغاة', color: 'error' }
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
  const deferredSearchTerm = useDeferredValue(searchTerm);
  const location = useLocation();
  const initialEmployerId = location.state?.employerId || '';
  const [filters, setFilters] = useState({ employerId: initialEmployerId, status: '' });
  const [employers, setEmployers] = useState([]);
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    title: '',
    message: '',
    confirmColor: 'primary',
    onConfirm: null
  });
  const [selectedIds, setSelectedIds] = useState([]);
  const [bulkResultDialog, setBulkResultDialog] = useState({ open: false, result: null });

  const fetchEmployers = useCallback(async () => {
    try {
      const response = await axiosClient.get('/benefit-policies/employer-selectors', { params: { deleted: showDeleted } });
      const availableEmployers = response.data?.data || [];
      setEmployers(availableEmployers);
      setFilters((previous) => {
        if (!previous.employerId) return previous;
        const stillAvailable = availableEmployers.some(
          (employer) => String(employer.id || employer.value) === String(previous.employerId)
        );
        return stillAvailable ? previous : { ...previous, employerId: '' };
      });
    } catch (error) {
      console.error('[BenefitPolicies] Failed to load employer selectors:', error);
    }
  }, [showDeleted]);

  const fetchPolicies = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: rowsPerPage,
        sortBy,
        sortDir: sortDirection.toUpperCase(),
        q: deferredSearchTerm.trim(),
        ...(filters.employerId && { employerId: filters.employerId }),
        ...(filters.status && { status: filters.status })
      };

      const response = showDeleted ? await getDeletedBenefitPolicies(params) : await getBenefitPolicies(params);
      setTotalCount(response?.totalElements || 0);
      setPolicies(response?.content || []);
    } catch (error) {
      console.error('[BenefitPolicies] Failed to fetch policies:', error);
      const apiMessage = error?.response?.data?.message || error?.message;
      enqueueSnackbar(apiMessage || 'فشل تحميل سياسات المنافع', { variant: 'error' });
      setPolicies([]);
      setTotalCount(0);
    } finally {
      setLoading(false);
    }
  }, [deferredSearchTerm, enqueueSnackbar, filters.employerId, filters.status, page, rowsPerPage, showDeleted, sortBy, sortDirection]);

  useEffect(() => {
    fetchEmployers();
  }, [fetchEmployers]);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  const handleNavigateAdd = useCallback(() => navigate('/benefit-policies/create'), [navigate]);
  const handleNavigateView = useCallback((id) => navigate(`/benefit-policies/${id}`), [navigate]);
  const handleNavigateEdit = useCallback((id) => navigate(`/benefit-policies/edit/${id}`), [navigate]);

  const handleSort = useCallback(
    (columnId) => {
      setSortDirection((prev) => (sortBy === columnId ? (prev === 'asc' ? 'desc' : 'asc') : 'asc'));
      setSortBy(columnId);
      setPage(0);
    },
    [sortBy]
  );

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

  const handleBulkResult = useCallback(
    (result) => {
      setSelectedIds([]);
      fetchPolicies();
      if (result && result.failedCount > 0) {
        setBulkResultDialog({ open: true, result });
      } else {
        enqueueSnackbar(`تمت العملية على ${result?.successCount ?? 0} من ${result?.totalCount ?? 0} سياسة بنجاح`, {
          variant: 'success'
        });
      }
    },
    [fetchPolicies, enqueueSnackbar]
  );

  const handleBulkDelete = useCallback(() => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد حذف جماعي',
      message: `هل تريد حذف ${selectedIds.length} سياسة محددة؟ سيتم تخطي أي سياسة بها مستفيدون نشطون مع توضيح السبب.`,
      confirmColor: 'error',
      onConfirm: async () => {
        try {
          const result = await bulkDeleteBenefitPolicies(selectedIds);
          closeDialog();
          handleBulkResult(result);
        } catch (error) {
          enqueueSnackbar(error?.response?.data?.message || 'فشل الحذف الجماعي', { variant: 'error' });
          closeDialog();
        }
      }
    });
  }, [selectedIds, closeDialog, handleBulkResult, enqueueSnackbar]);

  const handleBulkRestore = useCallback(() => {
    setConfirmDialog({
      open: true,
      title: 'تأكيد استعادة جماعية',
      message: `هل تريد استعادة ${selectedIds.length} سياسة محددة؟`,
      confirmColor: 'success',
      onConfirm: async () => {
        try {
          const result = await bulkRestoreBenefitPolicies(selectedIds);
          closeDialog();
          handleBulkResult(result);
        } catch (error) {
          enqueueSnackbar(error?.response?.data?.message || 'فشل الاستعادة الجماعية', { variant: 'error' });
          closeDialog();
        }
      }
    });
  }, [selectedIds, closeDialog, handleBulkResult, enqueueSnackbar]);

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
          return row.startDate ? <Chip label={dayjs(row.startDate).format('DD-MM-YYYY')} size="small" variant="outlined" /> : '-';
        case 'endDate':
          return row.endDate ? <Chip label={dayjs(row.endDate).format('DD-MM-YYYY')} size="small" variant="outlined" /> : '-';
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
    [handleDelete, handleNavigateEdit, handleNavigateView, handleRestore, showDeleted]
  );

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column', overflow: 'hidden', width: '100%', px: { xs: 2, sm: 3 } }}>
      <ModernPageHeader
        title="سياسات المنافع"
        subtitle="إدارة سياسات المنافع والتغطية التأمينية"
        icon={PolicyIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'سياسات المنافع' }]}
        actions={
          <Stack direction="row" spacing={1.5}>
            {selectedIds.length > 0 && !showDeleted && (
              <Button variant="contained" color="error" startIcon={<DeleteIcon />} onClick={handleBulkDelete}>
                حذف جماعي ({selectedIds.length})
              </Button>
            )}
            {selectedIds.length > 0 && showDeleted && (
              <Button variant="contained" color="success" startIcon={<UndoIcon />} onClick={handleBulkRestore}>
                استعادة جماعية ({selectedIds.length})
              </Button>
            )}
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
              <IconButton
                onClick={fetchPolicies}
                color="primary"
                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, width: '2.5rem', height: '2.5rem' }}
              >
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
                    <IconButton
                      size="small"
                      onClick={() => {
                        setSearchTerm('');
                        setPage(0);
                      }}
                    >
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ) : null,
                sx: { height: '2.5rem' }
              }}
            />

            <Autocomplete
              size="small"
              options={employers}
              getOptionLabel={(opt) => opt.name || opt.label || ''}
              isOptionEqualToValue={(opt, val) => String(opt.id || opt.value) === String(val?.id || val?.value)}
              value={employers.find((employer) => String(employer.id || employer.value) === String(filters.employerId)) || null}
              onChange={(event, newValue) => {
                setFilters((prev) => ({ ...prev, employerId: newValue?.id || newValue?.value || '' }));
                setPage(0);
              }}
              sx={{ minWidth: '11.25rem', bgcolor: 'background.paper' }}
              noOptionsText="لا توجد جهات عمل"
              renderInput={(params) => <TextField {...params} label="الشريك" InputLabelProps={{ shrink: true }} />}
            />

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

            <Button
              variant="outlined"
              color="secondary"
              onClick={handleResetFilters}
              startIcon={<FilterAltOffIcon />}
              sx={{ minWidth: '7.5rem', height: '2.5rem' }}
            >
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
          enableRowSelection
          selectedRowIds={selectedIds}
          onRowSelectionChange={setSelectedIds}
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

      {/* Bulk Operation Result Dialog — never collapse partial success into a generic toast */}
      <Dialog open={bulkResultDialog.open} onClose={() => setBulkResultDialog({ open: false, result: null })} maxWidth="sm" fullWidth>
        <DialogTitle>نتيجة العملية الجماعية</DialogTitle>
        <DialogContent>
          <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
            <Chip color="success" label={`نجح: ${bulkResultDialog.result?.successCount ?? 0}`} icon={<CheckCircleIcon />} />
            <Chip color="error" label={`فشل: ${bulkResultDialog.result?.failedCount ?? 0}`} icon={<ErrorIcon />} />
            <Chip variant="outlined" label={`الإجمالي: ${bulkResultDialog.result?.totalCount ?? 0}`} />
          </Stack>
          <List dense>
            {(bulkResultDialog.result?.results || []).map((r) => (
              <ListItem key={r.policyId}>
                <ListItemText
                  primary={r.policyName || `سياسة #${r.policyId}`}
                  secondary={r.message}
                  secondaryTypographyProps={{ color: r.success ? 'success.main' : 'error.main' }}
                />
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkResultDialog({ open: false, result: null })}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default BenefitPoliciesList;
