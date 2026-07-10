/**
 * Providers List Page - ENHANCED IMPLEMENTATION
 * Healthcare Providers (Hospitals, Clinics, Labs, Pharmacies)
 */

import { useMemo, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';

// MUI Components
import {
  Box,
  IconButton,
  Stack,
  Tooltip,
  Typography,
  Chip,
  Button,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  List,
  ListItem,
  ListItemText,
  Divider,
  DialogActions,
  TextField,
  InputAdornment,
  Avatar,
  ListItemAvatar,
  Grid,
  MenuItem,
  Select,
  FormControl,
  InputLabel
} from '@mui/material';

// MUI Icons
import AddIcon from '@mui/icons-material/Add';
import VisibilityIcon from '@mui/icons-material/Visibility';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import RefreshIcon from '@mui/icons-material/Refresh';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import CloseIcon from '@mui/icons-material/Close';
import HandshakeIcon from '@mui/icons-material/Handshake';
import SearchIcon from '@mui/icons-material/Search'; // Added SearchIcon
import BusinessIcon from '@mui/icons-material/Business'; // Added BusinessIcon
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import DeleteForeverIcon from '@mui/icons-material/DeleteForever';
import FileUploadIcon from '@mui/icons-material/FileUpload';

// Project Components
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import PermissionGuard from 'components/PermissionGuard';
import { UnifiedMedicalTable } from 'components/common';
import { ActionConfirmDialog, SoftDeleteToggle } from 'components/tba';
import ExcelImportDialog from 'components/ExcelImport/ExcelImportDialog';

// Hooks
import useTableState from 'hooks/useTableState';

// Insurance UX Components
import { NetworkBadge } from 'components/insurance';

// Services
import { providersService } from 'services/api';
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const QUERY_KEY = 'providers';
const MODULE_NAME = 'providers';
const DEFAULT_SORT = { field: 'id', direction: 'desc' };

const PROVIDER_TYPES = [
  { value: 'HOSPITAL', label: 'مستشفى' },
  { value: 'CLINIC', label: 'عيادة تخصصية' },
  { value: 'CLINIC_DEN', label: 'عيادة أسنان' },
  { value: 'LAB', label: 'مختبر طبي' },
  { value: 'PHARMACY', label: 'صيدلية' },
  { value: 'RADIOLOGY', label: 'مركز أشعة' },
  { value: 'PHYSIOTHERAPY', label: 'علاج طبيعي' }
];

// Provider Type Labels (Arabic)
const PROVIDER_TYPE_LABELS_AR = {
  HOSPITAL: 'مستشفى',
  CLINIC: 'عيادة تخصصية',
  CLINIC_DEN: 'عيادة أسنان',
  LAB: 'مختبر تحاليل',
  LABORATORY: 'مختبر تحاليل',
  PHARMACY: 'صيدلية',
  RADIOLOGY: 'مركز أشعة',
  PHYSIOTHERAPY: 'مركز علاج طبيعي'
};

// Provider Type Colors
const PROVIDER_TYPE_COLORS = {
  HOSPITAL: 'error',
  CLINIC: 'primary',
  CLINIC_DEN: 'primary',
  LAB: 'warning',
  LABORATORY: 'warning',
  PHARMACY: 'success',
  RADIOLOGY: 'info',
  PHYSIOTHERAPY: 'secondary'
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get network tier from provider
 */
const getNetworkTier = (provider) => {
  if (provider?.networkStatus) return provider.networkStatus;
  if (provider?.inNetwork === true) return 'IN_NETWORK';
  if (provider?.inNetwork === false) return 'OUT_OF_NETWORK';
  if (provider?.contracted === true) return 'IN_NETWORK';
  if (provider?.contracted === false) return 'OUT_OF_NETWORK';
  return null;
};

/**
 * Get provider status
 */
const getProviderStatus = (provider) => {
  if (provider?.status) return provider.status;
  if (provider?.active === true) return 'ACTIVE';
  if (provider?.active === false) return 'INACTIVE';
  return 'PENDING';
};

// ============================================================================
// SUB-COMPONENTS
// ============================================================================

const ProviderEmployersCell = ({ providerId, providerName }) => {
  const [showDialog, setShowDialog] = useState(false);
  const [dialogSearchTerm, setDialogSearchTerm] = useState('');

  const {
    data: allowedEmployers,
    isLoading,
    error
  } = useQuery({
    queryKey: ['provider-allowed-employers', providerId],
    queryFn: () => providersService.getAllowedEmployerIds(providerId),
    staleTime: 0,
    retry: 1
  });

  const employerNames = useMemo(() => {
    if (!allowedEmployers) return [];
    const globalNet = allowedEmployers.find((e) => e.isGlobal);
    if (globalNet) return ['الشبكة العامة (جميع الشركات)'];
    return allowedEmployers.filter((e) => e.isActive).map((e) => e.name || e.nameEn || 'مجهول');
  }, [allowedEmployers]);

  const filteredNames = dialogSearchTerm
    ? employerNames.filter((name) => name.toLowerCase().includes(dialogSearchTerm.toLowerCase()))
    : employerNames;

  const count = employerNames.length;
  const isGlobal = employerNames.length === 1 && employerNames[0].includes('الشبكة العامة');

  if (isLoading) return <CircularProgress size={20} color="secondary" />;
  if (error) return <Typography variant="caption" color="error">خطأ</Typography>;
  if (count === 0) return <Typography variant="caption" color="text.secondary">-</Typography>;

  return (
    <>
      <Button
        size="small"
        variant="text"
        color={isGlobal ? 'success' : 'primary'}
        onClick={(e) => { e.stopPropagation(); setShowDialog(true); }}
        startIcon={isGlobal ? <VerifiedUserIcon sx={{ fontSize: '1rem !important' }} /> : <BusinessIcon sx={{ fontSize: '1rem !important' }} />}
        sx={{ fontWeight: 'bold' }}
      >
        {isGlobal ? 'شبكة عامة' : `${count} جهة`}
      </Button>

      <Dialog open={showDialog} onClose={() => setShowDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Box>
            <Typography variant="h6">جهات العمل المتعاقدة</Typography>
            <Typography variant="caption">{providerName}</Typography>
          </Box>
          <IconButton onClick={() => setShowDialog(false)} size="small"><CloseIcon /></IconButton>
        </DialogTitle>
        <Box sx={{ px: '1.0rem', pb: '1.0rem' }}>
          <TextField
            fullWidth
            size="small"
            placeholder="بحث عن جهة عمل..."
            value={dialogSearchTerm}
            onChange={(e) => setDialogSearchTerm(e.target.value)}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
          />
        </Box>
        <DialogContent sx={{ p: 0, maxHeight: '25.0rem', overflowY: 'auto' }}>
          <List dense>
            {filteredNames.map((name, index) => (
              <ListItem key={index} divider>
                <ListItemText primary={name} />
              </ListItem>
            ))}
          </List>
        </DialogContent>
      </Dialog>
    </>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

export default function ProvidersList() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // Local state
  const [showDeleted, setShowDeleted] = useState(false);
  const [selectedIds, setSelectedIds] = useState([]);
  const [isImportDialogOpen, setIsImportDialogOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('');
  
  const [confirmState, setConfirmState] = useState({
    open: false,
    title: '',
    message: '',
    confirmText: 'تأكيد',
    cancelText: 'إلغاء',
    confirmColor: 'warning',
    onConfirm: null
  });

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: DEFAULT_SORT
  });

  const { page, pageSize: rowsPerPage, sorting } = tableState;
  const sortColumn = sorting?.[0]?.id;
  const sortDirection = sorting?.[0]?.desc ? 'desc' : 'asc';

  const handleNavigateAdd = useCallback(() => {
    navigate('/providers/add');
  }, [navigate]);

  const handleNavigateEdit = useCallback(
    (id) => {
      navigate(`/providers/edit/${id}`);
    },
    [navigate]
  );

  const handleNavigateView = useCallback(
    (id) => {
      navigate(`/providers/${id}`);
    },
    [navigate]
  );

  const handleDelete = useCallback(
    async (id, name) => {
      setConfirmState({
        open: true,
        title: 'تأكيد حذف مقدم الخدمة',
        message: `هل أنت متأكد من حذف مقدم الخدمة "${name}"؟`,
        confirmText: 'حذف',
        cancelText: 'إلغاء',
        confirmColor: 'warning',
        onConfirm: async () => {
          try {
            await providersService.remove(id);
            queryClient.setQueriesData({ queryKey: [QUERY_KEY] }, (oldData) => {
              if (!oldData) return oldData;
              const list = oldData.content || oldData.items;
              if (!Array.isArray(list)) return oldData;
              const nextList = list.filter((item) => item?.id !== id);
              const nextTotal = Math.max((oldData.totalElements ?? oldData.total ?? nextList.length) - 1, 0);
              return {
                ...oldData,
                ...(oldData.content ? { content: nextList, totalElements: nextTotal } : { items: nextList, total: nextTotal })
              };
            });
            openSnackbar({
              message: 'تم حذف مقدم الخدمة بنجاح',
              variant: 'success'
            });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Providers] Delete failed:', err);
            openSnackbar({
              message: err?.response?.data?.message || 'فشل حذف مقدم الخدمة. يرجى المحاولة لاحقاً',
              variant: 'error'
            });
          } finally {
            setConfirmState((prev) => ({ ...prev, open: false, onConfirm: null }));
          }
        }
      });
    },
    [queryClient]
  );

  const handleRestore = useCallback(
    async (id, name) => {
      setConfirmState({
        open: true,
        title: 'تأكيد الاستعادة',
        message: `هل تريد استعادة مقدم الخدمة "${name}" من سجل المحذوفات؟`,
        confirmText: 'استعادة',
        cancelText: 'إلغاء',
        confirmColor: 'info',
        onConfirm: async () => {
          try {
            await providersService.restore(id);
            queryClient.setQueriesData({ queryKey: [QUERY_KEY] }, (oldData) => {
              if (!oldData) return oldData;
              const list = oldData.content || oldData.items;
              if (!Array.isArray(list)) return oldData;
              const nextList = list.filter((item) => item?.id !== id);
              const nextTotal = Math.max((oldData.totalElements ?? oldData.total ?? nextList.length) - 1, 0);
              return {
                ...oldData,
                ...(oldData.content ? { content: nextList, totalElements: nextTotal } : { items: nextList, total: nextTotal })
              };
            });
            openSnackbar({ message: 'تمت استعادة مقدم الخدمة بنجاح', variant: 'success' });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Providers] Restore failed:', err);
            openSnackbar({ message: err?.response?.data?.message || 'فشلت استعادة مقدم الخدمة', variant: 'error' });
          } finally {
            setConfirmState((prev) => ({ ...prev, open: false, onConfirm: null }));
          }
        }
      });
    },
    [queryClient]
  );

  const handleHardDelete = useCallback(
    async (id, name) => {
      setConfirmState({
        open: true,
        title: 'تأكيد الحذف النهائي',
        message: `سيتم حذف مقدم الخدمة "${name}" نهائياً ولا يمكن التراجع. هل تريد المتابعة؟`,
        confirmText: 'حذف نهائي',
        cancelText: 'إلغاء',
        confirmColor: 'error',
        onConfirm: async () => {
          try {
            await providersService.hardDelete(id);
            queryClient.setQueriesData({ queryKey: [QUERY_KEY] }, (oldData) => {
              if (!oldData) return oldData;
              const list = oldData.content || oldData.items;
              if (!Array.isArray(list)) return oldData;
              const nextList = list.filter((item) => item?.id !== id);
              const nextTotal = Math.max((oldData.totalElements ?? oldData.total ?? nextList.length) - 1, 0);
              return {
                ...oldData,
                ...(oldData.content ? { content: nextList, totalElements: nextTotal } : { items: nextList, total: nextTotal })
              };
            });
            openSnackbar({ message: 'تم الحذف النهائي لمقدم الخدمة بنجاح', variant: 'success' });
            queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
          } catch (err) {
            console.error('[Providers] Hard delete failed:', err);
            openSnackbar({ message: err?.response?.data?.message || 'فشل الحذف النهائي لمقدم الخدمة', variant: 'error' });
          } finally {
            setConfirmState((prev) => ({ ...prev, open: false, onConfirm: null }));
          }
        }
      });
    },
    [queryClient]
  );


  // ========================================
  // DATA FETCHING WITH REACT QUERY
  // ========================================

  // ========================================
  // COLUMNS DEFINITION
  // ========================================

  const columns = useMemo(
    () => [
      {
        id: 'index',
        label: '#',
        minWidth: '3.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'name',
        label: 'اسم مقدم الخدمة',
        minWidth: '12.5rem',
        sortable: true
      },
      {
        id: 'providerType',
        label: 'النوع',
        minWidth: '7.5rem',
        align: 'center',
        sortable: true
      },
      {
        id: 'city',
        label: 'المدينة',
        minWidth: '7.5rem',
        align: 'center',
        sortable: true
      },
      {
        id: 'phone',
        label: 'الهاتف',
        minWidth: '8.125rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'network',
        label: 'الشبكة',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'employers',
        label: 'جهات العمل المتعاقدة',
        minWidth: '12.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'documents',
        label: 'المستندات',
        minWidth: '6.25rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'status',
        label: 'الحالة',
        minWidth: '7.5rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'actions',
        label: 'الإجراءات',
        minWidth: '10.0rem',
        align: 'center',
        sortable: false
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================

  const renderCell = useCallback(
    (provider, column, rowIndex) => {
      if (!provider) return null;

      switch (column.id) {
        case 'index':
          return (
            <Typography variant="body2" color="textSecondary" fontWeight="bold">
              {page * rowsPerPage + rowIndex + 1}
            </Typography>
          );

        case 'name':
          return (
            <Typography variant="body2" fontWeight={500}>
              {provider.name || '-'}
            </Typography>
          );

        case 'providerType':
          return (
            <Chip
              label={PROVIDER_TYPE_LABELS_AR[provider.providerType] ?? provider.providerType ?? '-'}
              color={PROVIDER_TYPE_COLORS[provider.providerType] || 'default'}
              size="small"
              variant="outlined"
              sx={{ width: '130px', justifyContent: 'center', fontWeight: 600 }}
            />
          );

        case 'city':
          return <Typography variant="body2">{provider.city ?? provider.region ?? '-'}</Typography>;

        case 'phone':
          return (
            <Typography variant="body2" color="text.secondary" dir="ltr">
              {provider.phone ?? provider.contactPhone ?? '-'}
            </Typography>
          );

        case 'network':
          const tier = getNetworkTier(provider);
          return tier ? (
            <NetworkBadge networkTier={tier} showLabel={true} size="small" language="ar" />
          ) : (
            <Typography variant="body2" color="text.secondary">
              -
            </Typography>
          );

        case 'employers':
          return <ProviderEmployersCell providerId={provider.id} providerName={provider.name} />;

        case 'documents':
          const hasDocs = provider.hasDocuments || provider.documentsCount > 0;
          const hasContract = !!provider.contractStartDate;

          return (
            <Stack direction="row" spacing={1} justifyContent="center" alignItems="center">
              {/* Document Indicator */}
              <Tooltip title={hasDocs ? 'توجد مستندات مرفوعة' : 'لا توجد مستندات'}>
                <Box sx={{ position: 'relative', display: 'inline-flex' }}>
                  <DescriptionIcon sx={{ color: hasDocs ? 'primary.main' : 'text.disabled' }} fontSize="small" />
                  {hasDocs && (
                    <CheckCircleIcon
                      color="success"
                      sx={{
                        fontSize: '0.75rem',
                        position: 'absolute',
                        bottom: -2,
                        right: -2,
                        bgcolor: 'white',
                        borderRadius: '50%'
                      }}
                    />
                  )}
                </Box>
              </Tooltip>

              {/* Contract Indicator (if contract date exists or explicitly marked) */}
              {(hasContract || provider.hasContractDocument) && (
                <Tooltip title="يوجد عقد">
                  <HandshakeIcon color="secondary" fontSize="small" />
                </Tooltip>
              )}
            </Stack>
          );

        case 'status': {
          const providerStatusConfig = {
            ACTIVE: { label: 'نشط', color: 'success' },
            INACTIVE: { label: 'غير نشط', color: 'error' },
            SUSPENDED: { label: 'معلق', color: 'warning' },
            PENDING: { label: 'قيد المراجعة', color: 'warning' }
          };
          const sc = providerStatusConfig[getProviderStatus(provider)] || { label: getProviderStatus(provider), color: 'default' };
          return (
            <Chip label={sc.label} color={sc.color} size="small" sx={{ minWidth: '5.5rem', justifyContent: 'center', fontWeight: 600 }} />
          );
        }

        case 'actions':
          return (
            <Stack direction="row" spacing={0.5} justifyContent="center" onClick={(e) => e.stopPropagation()}>
              {provider.active === false || showDeleted ? (
                <PermissionGuard resource="providers" action="delete">
                  <>
                    <Tooltip title="استعادة">
                      <IconButton
                        size="small"
                        color="success"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleRestore(provider.id, provider.name);
                        }}
                      >
                        <RefreshIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="حذف نهائي">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleHardDelete(provider.id, provider.name);
                        }}
                      >
                        <DeleteForeverIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </>
                </PermissionGuard>
              ) : (
                <>
                  <Tooltip title="عرض">
                    <IconButton
                      size="small"
                      color="primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleNavigateView(provider.id);
                      }}
                    >
                      <VisibilityIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>

                  <Tooltip title="تعديل">
                    <IconButton
                      size="small"
                      color="info"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleNavigateEdit(provider.id);
                      }}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>

                  <PermissionGuard resource="providers" action="delete">
                    <Tooltip title="حذف">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(provider.id, provider.name);
                        }}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </PermissionGuard>
                </>
              )}
            </Stack>
          );

        default:
          return null;
      }
    },
    [handleNavigateView, handleNavigateEdit, handleDelete, handleRestore, handleHardDelete, page, rowsPerPage, showDeleted]
  );

  // ========================================
  // DATA FETCHING WITH REACT QUERY
  // ========================================

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, showDeleted, page, rowsPerPage, sortColumn, sortDirection, tableState.columnFilters],
    queryFn: async () => {
      console.log('[ProvidersList] Fetching providers - page:', page + 1, 'size:', rowsPerPage);

      const params = {
        page: page + 1, // Backend uses 1-based pages
        size: rowsPerPage,
        sort: sortColumn ? `${sortColumn},${sortDirection}` : 'id,desc',
        active: showDeleted ? false : true,
        search: tableState.columnFilters.q || undefined,
        providerType: tableState.columnFilters.providerType || undefined,
        networkStatus: tableState.columnFilters.networkStatus || undefined,
        status: tableState.columnFilters.status || undefined
      };

      const result = await providersService.getAll(params);
      return result;
    },
    staleTime: 30 * 1000,
    refetchOnMount: true
  });

  // Reset function when switching tabs
  useMemo(() => {
    setSelectedIds([]);
  }, [showDeleted]);

  // Extract data
  const providers = useMemo(() => {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.items)) return data.items;
    return [];
  }, [data]);

  const totalCount = useMemo(() => {
    if (typeof data?.totalElements === 'number') return data.totalElements;
    if (typeof data?.total === 'number') return data.total;
    return providers.length;
  }, [data, providers.length]);

  const handleSelectAllClick = useCallback(
    async (event) => {
      if (event.target.checked) {
        try {
          const params = {
            page: 1,
            size: 100000,
            sort: sortColumn ? `${sortColumn},${sortDirection}` : 'id,desc',
            active: showDeleted ? false : true,
            search: tableState.columnFilters.q || undefined,
            providerType: tableState.columnFilters.providerType || undefined,
            networkStatus: tableState.columnFilters.networkStatus || undefined,
            status: tableState.columnFilters.status || undefined
          };
          const allResult = await providersService.getAll(params);
          const allProviders = allResult?.content || allResult?.items || [];
          setSelectedIds(allProviders.map(p => p.id));
        } catch (error) {
          console.error("Failed to fetch all providers for selection", error);
          setSelectedIds(providers.map((n) => n.id));
        }
        return;
      }
      setSelectedIds([]);
    },
    [providers, showDeleted, sortColumn, sortDirection, tableState.columnFilters]
  );

  const handleSelectRow = useCallback(
    (event, id) => {
      const selectedIndex = selectedIds.indexOf(id);
      let newSelected = [];

      if (selectedIndex === -1) {
        newSelected = newSelected.concat(selectedIds, id);
      } else if (selectedIndex === 0) {
        newSelected = newSelected.concat(selectedIds.slice(1));
      } else if (selectedIndex === selectedIds.length - 1) {
        newSelected = newSelected.concat(selectedIds.slice(0, -1));
      } else if (selectedIndex > 0) {
        newSelected = newSelected.concat(selectedIds.slice(0, selectedIndex), selectedIds.slice(selectedIndex + 1));
      }
      setSelectedIds(newSelected);
    },
    [selectedIds]
  );

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box>
      {/* ====== UNIFIED PAGE HEADER ====== */}
      <PermissionGuard resource="providers" action="view">
        <UnifiedPageHeader
          title="مقدمي الخدمات الصحية"
          subtitle="إدارة المستشفيات والعيادات والمختبرات والصيدليات"
          icon={LocalHospitalIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'مقدمي الخدمات' }]}
          pdfModule={MODULE_NAME}
          showAddButton={true}
          addButtonLabel="إضافة مقدم خدمة"
          onAddClick={handleNavigateAdd}
          additionalActions={
            <Stack direction="row" spacing={1} alignItems="center">
              <Button variant="outlined" color="secondary" startIcon={<FileUploadIcon />} onClick={() => setIsImportDialogOpen(true)}>
                استيراد من إكسل
              </Button>
              <SoftDeleteToggle showDeleted={showDeleted} onToggle={() => setShowDeleted((v) => !v)} />
            </Stack>
          }
        />
      </PermissionGuard>

      {/* ====== FILTERS & BULK ACTIONS ====== */}
      <Box sx={{ p: 2, mb: 2 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              fullWidth
              size="small"
              placeholder="البحث باسم المرفق أو المدينة أو رقم الترخيص..."
              value={tableState.columnFilters.q || ''}
              onChange={(e) => tableState.setFilter('q', e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                )
              }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="provider-type-label">نوع المرفق</InputLabel>
              <Select
                labelId="provider-type-label"
                value={tableState.columnFilters.providerType || ''}
                label="نوع المرفق"
                onChange={(e) => tableState.setFilter('providerType', e.target.value)}
              >
                <MenuItem value="">الكل</MenuItem>
                {PROVIDER_TYPES.map((type) => (
                  <MenuItem key={type.value} value={type.value}>
                    {type.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="provider-network-label">الشبكة</InputLabel>
              <Select
                labelId="provider-network-label"
                value={tableState.columnFilters.networkStatus || ''}
                label="الشبكة"
                onChange={(e) => tableState.setFilter('networkStatus', e.target.value)}
              >
                <MenuItem value="">الكل</MenuItem>
                <MenuItem value="IN_NETWORK">داخل الشبكة</MenuItem>
                <MenuItem value="OUT_OF_NETWORK">خارج الشبكة</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="provider-status-label">الحالة</InputLabel>
              <Select
                labelId="provider-status-label"
                value={tableState.columnFilters.status || ''}
                label="الحالة"
                onChange={(e) => tableState.setFilter('status', e.target.value)}
              >
                <MenuItem value="">الكل</MenuItem>
                <MenuItem value="ACTIVE">نشط</MenuItem>
                <MenuItem value="INACTIVE">غير نشط</MenuItem>
                <MenuItem value="PENDING">قيد المراجعة</MenuItem>
                <MenuItem value="SUSPENDED">معلق</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 2 }}>
            <Button
              variant="outlined"
              color="secondary"
              startIcon={<RefreshIcon />}
              onClick={() => {
                tableState.setFilter('q', '');
                tableState.setFilter('providerType', '');
                tableState.setFilter('networkStatus', '');
                tableState.setFilter('status', '');
              }}
              fullWidth
              sx={{ height: '40px' }}
            >
              إعادة ضبط
            </Button>
          </Grid>

          {selectedIds.length > 0 && (
            <Grid size={{ xs: 12 }}>
              <Stack direction="row" spacing={2} alignItems="center" sx={{ bgcolor: 'rgba(0, 0, 0, 0.04)', p: 1, borderRadius: 1 }}>
                <Typography variant="body2" fontWeight="bold">
                  تم تحديد {selectedIds.length} مرفق
                </Typography>
                
                {showDeleted ? (
                  <>
                    <PermissionGuard resource="providers" action="delete">
                      <Button
                        size="small"
                        color="success"
                        variant="contained"
                        startIcon={<RefreshIcon />}
                        onClick={() => providersService.bulkRestore(selectedIds).then(() => {
                          openSnackbar({ message: 'تم استعادة المرافق المحددة', variant: 'success' });
                          setSelectedIds([]);
                          queryClient.invalidateQueries([QUERY_KEY]);
                        }).catch(e => openSnackbar({ message: e.message || 'خطأ', variant: 'error' }))}
                      >
                        استعادة المحدد
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        variant="contained"
                        startIcon={<DeleteForeverIcon />}
                        onClick={() => providersService.bulkHardDelete(selectedIds).then(() => {
                          openSnackbar({ message: 'تم الحذف النهائي بنجاح', variant: 'success' });
                          setSelectedIds([]);
                          queryClient.invalidateQueries([QUERY_KEY]);
                        }).catch(e => openSnackbar({ message: e.message || 'خطأ', variant: 'error' }))}
                      >
                        حذف نهائي للمحدد
                      </Button>
                    </PermissionGuard>
                  </>
                ) : (
                  <PermissionGuard resource="providers" action="delete">
                    <Button
                      size="small"
                      color="primary"
                      variant="contained"
                      startIcon={<VerifiedUserIcon />}
                      onClick={() => providersService.bulkAllowAllEmployers(selectedIds).then(() => {
                        openSnackbar({ message: 'تم تفعيل جميع الجهات للمرافق المحددة بنجاح', variant: 'success' });
                        setSelectedIds([]);
                        queryClient.invalidateQueries([QUERY_KEY]);
                      }).catch(e => openSnackbar({ message: e.message || 'حدث خطأ', variant: 'error' }))}
                    >
                      تفعيل جميع الجهات للمحدد
                    </Button>
                    <Button
                      size="small"
                      color="warning"
                      variant="contained"
                      startIcon={<CloseIcon />}
                      onClick={() => providersService.bulkRemoveEmployers(selectedIds).then(() => {
                        openSnackbar({ message: 'تمت إزالة جهات العمل من المرافق المحددة بنجاح', variant: 'success' });
                        setSelectedIds([]);
                        queryClient.invalidateQueries([QUERY_KEY]);
                      }).catch(e => openSnackbar({ message: e.message || 'حدث خطأ', variant: 'error' }))}
                    >
                      إزالة جهات العمل من المحدد
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      variant="contained"
                      startIcon={<DeleteIcon />}
                      onClick={() => providersService.bulkDeactivate(selectedIds).then(() => {
                        openSnackbar({ message: 'تم حذف المرافق المحددة وعقودها الفارغة بنجاح', variant: 'success' });
                        setSelectedIds([]);
                        queryClient.invalidateQueries([QUERY_KEY]);
                      }).catch(e => openSnackbar({ message: e.message || 'يوجد مرفق يحتوي على عقد مسعر يمنع الحذف', variant: 'error' }))}
                    >
                      حذف المحدد (مع عقوده)
                    </Button>
                  </PermissionGuard>
                )}
              </Stack>
            </Grid>
          )}
        </Grid>
      </Box>

      {/* ====== DATA TABLE ====== */}
      <MainCard content={false} sx={{ height: 'calc(100vh - 250px)', display: 'flex', flexDirection: 'column' }}>
        <UnifiedMedicalTable
          columns={columns}
          rows={providers}
          loading={isLoading}
          renderCell={renderCell}
          totalCount={totalCount}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(newPage) => tableState.setPage(newPage)}
          onRowsPerPageChange={(newSize) => tableState.setPageSize(newSize)}
          sortBy={sortColumn}
          sortDirection={sortDirection}
          onSort={(col, dir) => tableState.setSorting([{ id: col, desc: dir === 'desc' }])}
          emptyIcon={LocalHospitalIcon}
          emptyMessage="لا يوجد مقدمو خدمات صحية مسجلين حالياً"
          selectable={true}
          selectedRows={selectedIds}
          onSelectAllClick={handleSelectAllClick}
          onSelectRow={handleSelectRow}
        />
      </MainCard>

      <ActionConfirmDialog
        open={confirmState.open}
        title={confirmState.title}
        message={confirmState.message}
        confirmText={confirmState.confirmText}
        cancelText={confirmState.cancelText}
        confirmColor={confirmState.confirmColor}
        onClose={() => setConfirmState((prev) => ({ ...prev, open: false, onConfirm: null }))}
        onConfirm={() => confirmState.onConfirm?.()}
      />

      {/* ====== EXCEL IMPORT DIALOG ====== */}
      <ExcelImportDialog
        open={isImportDialogOpen}
        onClose={() => setIsImportDialogOpen(false)}
        title="استيراد مقدمي الخدمات من إكسل"
        templateFilename="Providers_Import_Template.xlsx"
        onDownloadTemplate={() => providersService.downloadImportTemplate()}
        onImport={async (file) => {
          const result = await providersService.importProvidersFromExcel(file);
          openSnackbar({
            open: true,
            message: 'تم استيراد البيانات بنجاح',
            variant: 'success'
          });
          queryClient.invalidateQueries([QUERY_KEY]);
          return result;
        }}
      />
    </Box>
  );
}
