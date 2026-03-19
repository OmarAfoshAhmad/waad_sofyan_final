/**
 * Providers List Page - ENHANCED IMPLEMENTATION
 * Healthcare Providers (Hospitals, Clinics, Labs, Pharmacies)
 */

import { useMemo, useCallback, useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
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
  ListItemAvatar // Added DialogActions, TextField, InputAdornment, Avatar, ListItemAvatar
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

// Project Components
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import PermissionGuard from 'components/PermissionGuard';
import { UnifiedMedicalTable } from 'components/common';

// Hooks
import useTableState from 'hooks/useTableState';

// Insurance UX Components
import { NetworkBadge } from 'components/insurance';

// Services
import { providersService } from 'services/api';

// Snackbar
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const QUERY_KEY = 'providers';
const MODULE_NAME = 'providers';
const DEFAULT_SORT = { field: 'id', direction: 'desc' };

// Provider Type Labels (Arabic)
const PROVIDER_TYPE_LABELS_AR = {
  HOSPITAL: 'مستشفى',
  CLINIC: 'عيادة',
  LAB: 'مختبر',
  LABORATORY: 'مختبر',
  PHARMACY: 'صيدلية',
  RADIOLOGY: 'مركز أشعة'
};

// Provider Type Colors
const PROVIDER_TYPE_COLORS = {
  HOSPITAL: 'error',
  CLINIC: 'primary',
  LAB: 'warning',
  LABORATORY: 'warning',
  PHARMACY: 'success',
  RADIOLOGY: 'info'
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
  return 'ACTIVE';
};

// ============================================================================
// SUB-COMPONENTS
// ============================================================================

/**
 * Employers Cell Component
 * Fetches and displays the list of allowed employers for a specific provider
 */
const ProviderEmployersCell = ({ providerId, providerName }) => {
  const [showDialog, setShowDialog] = useState(false);
  const [dialogSearchTerm, setDialogSearchTerm] = useState('');

  // Always fetch allowed employers to show count in button
  const {
    data: allowedEmployers,
    isLoading,
    error
  } = useQuery({
    queryKey: ['provider-allowed-employers', providerId],
    queryFn: () => providersService.getAllowedEmployerIds(providerId),
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
    retry: 1
  });

  // Extract employer names
  // API returns List<AllowedEmployerDto>
  const employerNames = useMemo(() => {
    if (!allowedEmployers) return [];

    // Check if Global Network is enabled
    const globalNet = allowedEmployers.find((e) => e.isGlobal);
    if (globalNet) return ['الشبكة العامة (جميع الشركات)'];

    // Otherwise return list of active employers
    return allowedEmployers.filter((e) => e.isActive).map((e) => e.name || e.nameEn || 'مجهول');
  }, [allowedEmployers]);

  // Filter for search
  const filteredNames = dialogSearchTerm
    ? employerNames.filter((name) => name.toLowerCase().includes(dialogSearchTerm.toLowerCase()))
    : employerNames;

  const count = employerNames.length;
  // Handle Global Network special case for count display
  const isGlobal = employerNames.length === 1 && employerNames[0].includes('الشبكة العامة');

  // Show loading spinner while fetching
  if (isLoading) {
    return <CircularProgress size={20} color="secondary" />;
  }

  // Show error state
  if (error) {
    return (
      <Tooltip title={error.message || 'فشل تحميل البيانات'}>
        <Typography variant="caption" color="error">
          خطأ
        </Typography>
      </Tooltip>
    );
  }

  // If no contracts, show empty state
  if (count === 0) {
    return (
      <Typography variant="caption" color="text.secondary">
        -
      </Typography>
    );
  }

  // Show button with count
  return (
    <>
      <Tooltip title="اضغط لعرض جهات العمل المتعاقدة">
        <Button
          size="small"
          variant="text"
          color={isGlobal ? 'success' : 'primary'}
          onClick={(e) => {
            e.stopPropagation();
            setShowDialog(true);
          }}
          startIcon={
            isGlobal ? <VerifiedUserIcon sx={{ fontSize: '1rem !important' }} /> : <BusinessIcon sx={{ fontSize: '1rem !important' }} />
          }
          sx={{ fontWeight: 'bold' }}
        >
          {isGlobal ? 'شبكة عامة' : `${count} ${count > 10 ? 'جهة' : count === 1 ? 'جهة واحدة' : count === 2 ? 'جهتان' : 'جهات'}`}
        </Button>
      </Tooltip>

      {/* Dialog to show full list */}
      <Dialog
        open={showDialog}
        onClose={() => {
          setShowDialog(false);
          setDialogSearchTerm('');
        }}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: '0.25rem' } }}
      >
        <DialogTitle sx={{ m: 0, p: '1.0rem', pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Box>
            <Typography variant="h6" fontWeight="bold">
              جهات العمل المتعاقدة
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {providerName}
            </Typography>
          </Box>
          <IconButton onClick={() => setShowDialog(false)} size="small">
            <CloseIcon />
          </IconButton>
        </DialogTitle>

        <Box sx={{ px: '1.0rem', pb: '1.0rem' }}>
          <TextField
            fullWidth
            size="small"
            placeholder="بحث عن جهة عمل..."
            value={dialogSearchTerm}
            onChange={(e) => setDialogSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              )
            }}
            sx={{ bgcolor: 'background.paper' }}
          />
        </Box>

        <Divider />

        <DialogContent sx={{ p: 0, maxHeight: '25.0rem', overflowY: 'auto' }}>
          <List dense sx={{ py: 0 }}>
            {filteredNames.length > 0 ? (
              filteredNames.map((name, index) => (
                <ListItem key={index} divider={index < filteredNames.length - 1} sx={{ py: '0.75rem', px: '1.0rem' }}>
                  <ListItemAvatar>
                    <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main', width: '2.0rem', height: '2.0rem' }}>
                      <BusinessIcon fontSize="small" />
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText primary={name} primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }} />
                </ListItem>
              ))
            ) : (
              <Box sx={{ p: '2.0rem', textAlign: 'center' }}>
                <Typography color="text.secondary" variant="body2">
                  {dialogSearchTerm ? 'لا توجد نتائج مطابقة' : 'لا توجد جهات متعاقدة'}
                </Typography>
              </Box>
            )}
          </List>
        </DialogContent>

        <Divider />

        <DialogActions sx={{ p: '0.75rem', justifyContent: 'center' }}>
          <Button onClick={() => setShowDialog(false)} color="inherit">
            إغلاق
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

export default function ProvidersList() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  // ========================================
  // TABLE STATE
  // ========================================

  const tableState = useTableState({
    initialPageSize: 10,
    defaultSort: DEFAULT_SORT
  });

  const { page, pageSize: rowsPerPage, sorting } = tableState;

  const sortColumn = sorting?.[0]?.id;
  const sortDirection = sorting?.[0]?.desc ? 'desc' : 'asc';

  // ========================================
  // AUTO-REFRESH ON NAVIGATION BACK
  // ========================================

  useEffect(() => {
    // Invalidate cache when navigating back to this page
    // This ensures newly created providers appear immediately
    console.log('[ProvidersList] Page mounted/navigated - refreshing data');
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  }, [location.key, queryClient]);

  // ========================================
  // NAVIGATION HANDLERS
  // ========================================

  const handleNavigateAdd = useCallback(() => {
    navigate('/providers/add');
  }, [navigate]);

  const handleNavigateView = useCallback(
    (id) => {
      navigate(`/providers/${id}`);
    },
    [navigate]
  );

  const handleNavigateEdit = useCallback(
    (id) => {
      navigate(`/providers/edit/${id}`);
    },
    [navigate]
  );

  const handleDelete = useCallback(
    async (id, name) => {
      const confirmMessage = `هل أنت متأكد من حذف مقدم الخدمة "${name}"؟`;
      if (!window.confirm(confirmMessage)) return;

      try {
        await providersService.remove(id);
        openSnackbar({
          message: 'تم حذف مقدم الخدمة بنجاح',
          variant: 'success'
        });
        queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      } catch (err) {
        console.error('[Providers] Delete failed:', err);
        openSnackbar({
          message: 'فشل حذف مقدم الخدمة. يرجى المحاولة لاحقاً',
          variant: 'error'
        });
      }
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
              sx={{ minWidth: '5.5rem', justifyContent: 'center', fontWeight: 600 }}
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
            ACTIVE:   { label: 'نشط',    color: 'success' },
            INACTIVE: { label: 'غير نشط', color: 'error' },
            SUSPENDED:{ label: 'معلق',   color: 'warning' },
            PENDING:  { label: 'قيد المراجعة', color: 'warning' }
          };
          const sc = providerStatusConfig[getProviderStatus(provider)] || { label: getProviderStatus(provider), color: 'default' };
          return <Chip label={sc.label} color={sc.color} size="small" sx={{ minWidth: '5.5rem', justifyContent: 'center', fontWeight: 600 }} />;
        }

        case 'actions':
          return (
            <Stack direction="row" spacing={0.5} justifyContent="center" onClick={(e) => e.stopPropagation()}>
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
            </Stack>
          );

        default:
          return null;
      }
    },
    [handleNavigateView, handleNavigateEdit, handleDelete, page, rowsPerPage]
  );

  // ========================================
  // DATA FETCHING WITH REACT QUERY
  // ========================================

  const { data, isLoading, refetch } = useQuery({
    queryKey: [QUERY_KEY, page, rowsPerPage, sortColumn, sortDirection],
    queryFn: async () => {
      console.log('[ProvidersList] Fetching providers - page:', page + 1, 'size:', rowsPerPage);

      const params = {
        page: page + 1, // Backend uses 1-based pages
        size: rowsPerPage,
        sort: sortColumn ? `${sortColumn},${sortDirection}` : 'id,desc'
      };

      const result = await providersService.getAll(params);
      return result;
    },
    staleTime: 30 * 1000, // 30 seconds
    refetchOnMount: 'always' // Always refetch when component mounts
  });

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
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => refetch()} size="small">
              تحديث
            </Button>
          }
        />
      </PermissionGuard>

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
        />
      </MainCard>
    </Box>
  );
}


