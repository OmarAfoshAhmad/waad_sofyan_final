import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Chip, IconButton, Stack, TextField, Typography, InputAdornment, Tooltip, Alert } from '@mui/material';
import {
  Add as AddIcon,
  Search as SearchIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  LocalHospital as LocalHospitalIcon,
  Refresh as RefreshIcon,
  MedicalServices as MedicalServicesIcon,
  Person as PersonIcon,
  Receipt as ReceiptIcon,
  Assignment as AssignmentIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import ModernEmptyState from 'components/tba/ModernEmptyState';
import { UnifiedMedicalTable } from 'components/common';
import { useVisitsList } from 'hooks/useVisits';
import visitsService from 'services/api/visits.service';

// Insurance UX Components - Phase B3
import { NetworkBadge, CardStatusBadge } from 'components/insurance';
import { projectSettings } from 'config';

// ============ VISIT CONFIGURATION ============
// Visit Type Labels (Arabic) - Synced with Backend VisitType Enum
const VISIT_TYPE_LABELS_AR = {
  OPERATIONS: 'عمليات',
  HOSPITALIZATION: 'إيواء',
  OUTPATIENT_CLINIC: 'عيادات خارجية',
  MEDICAL_LAB_TESTS: 'تحاليل طبية',
  DENTAL_PREVENTIVE: 'اسنان وقائي',
  DENTAL_COSMETIC: 'اسنان تجميلي',
  RADIOLOGY: 'اشعة',
  PHYSIOTHERAPY: 'علاج طبيعي',

  // Backward compatibility with existing backend enum values
  EMERGENCY: 'عمليات',
  OUTPATIENT: 'عيادات خارجية',
  INPATIENT: 'إيواء',
  ROUTINE: 'تحاليل طبية',
  FOLLOW_UP: 'اسنان وقائي',
  PREVENTIVE: 'اسنان تجميلي',
  SPECIALIZED: 'اشعة',
  HOME_CARE: 'علاج طبيعي',
  TELECONSULTATION: 'عيادات خارجية',
  DAY_SURGERY: 'عمليات'
};

// Visit Type Colors
const VISIT_TYPE_COLORS = {
  OPERATIONS: 'error',
  HOSPITALIZATION: 'warning',
  OUTPATIENT_CLINIC: 'primary',
  MEDICAL_LAB_TESTS: 'default',
  DENTAL_PREVENTIVE: 'info',
  DENTAL_COSMETIC: 'success',
  RADIOLOGY: 'secondary',
  PHYSIOTHERAPY: 'default',

  // Backward compatibility with existing backend enum values
  EMERGENCY: 'error',
  OUTPATIENT: 'primary',
  INPATIENT: 'warning',
  ROUTINE: 'default',
  FOLLOW_UP: 'info',
  PREVENTIVE: 'success',
  SPECIALIZED: 'secondary',
  HOME_CARE: 'default',
  TELECONSULTATION: 'info',
  DAY_SURGERY: 'warning'
};

// Status Labels (Arabic)
const STATUS_LABELS_AR = {
  ACTIVE: 'نشطة',
  INACTIVE: 'غير نشطة',
  COMPLETED: 'مكتملة',
  CANCELLED: 'ملغاة'
};

// Claim Status Colors and Labels
const CLAIM_STATUS_CONFIG = {
  DRAFT: { color: 'default', label: 'مسودة', bgColor: '#f5f5f5' },
  SUBMITTED: { color: 'info', label: 'مقدمة', bgColor: '#e3f2fd' },
  UNDER_REVIEW: { color: 'warning', label: 'قيد المراجعة', bgColor: '#fff3e0' },
  APPROVAL_IN_PROGRESS: { color: 'warning', label: 'جاري معالجة الموافقة', bgColor: '#fff3e0' },
  APPROVED: { color: 'success', label: 'موافق عليها', bgColor: '#e8f5e9' },
  BATCHED: { color: 'secondary', label: 'ضمن دفعة تسوية', bgColor: '#ede7f6' },
  REJECTED: { color: 'error', label: 'مرفوضة', bgColor: '#ffebee' },
  NEEDS_CORRECTION: { color: 'secondary', label: 'تحتاج تصحيح', bgColor: '#f3e5f5' },
  SETTLED: { color: 'primary', label: 'مسددة', bgColor: '#e8eaf6' }
};

// Pre-Auth Status Colors and Labels
const PREAUTH_STATUS_CONFIG = {
  PENDING: { color: 'default', label: 'قيد الانتظار', bgColor: '#f5f5f5' },
  UNDER_REVIEW: { color: 'warning', label: 'قيد المراجعة', bgColor: '#fff3e0' },
  APPROVED: { color: 'success', label: 'موافق عليها', bgColor: '#e8f5e9' },
  REJECTED: { color: 'error', label: 'مرفوضة', bgColor: '#ffebee' },
  EXPIRED: { color: 'default', label: 'منتهية', bgColor: '#eeeeee' },
  CANCELLED: { color: 'error', label: 'ملغاة', bgColor: '#ffebee' },
  USED: { color: 'primary', label: 'مستخدمة', bgColor: '#e8eaf6' }
};

// Network Status mapping
const getNetworkTier = (provider) => {
  if (!provider) return null;
  if (provider?.networkStatus) return provider.networkStatus;
  if (provider?.inNetwork === true) return 'IN_NETWORK';
  if (provider?.inNetwork === false) return 'OUT_OF_NETWORK';
  if (provider?.contracted === true) return 'IN_NETWORK';
  if (provider?.contracted === false) return 'OUT_OF_NETWORK';
  return null;
};

// Get visit status
const getVisitStatus = (visit) => {
  if (visit?.status) return visit.status;
  if (visit?.active === true) return 'ACTIVE';
  if (visit?.active === false) return 'INACTIVE';
  return 'ACTIVE';
};

// ============ DEFENSIVE DATA EXTRACTION ============
// Handle all possible API response shapes
const extractItems = (data) => {
  if (!data) return [];
  if (data?.data?.items && Array.isArray(data.data.items)) return data.data.items;
  if (data?.data?.content && Array.isArray(data.data.content)) return data.data.content;
  if (data?.items && Array.isArray(data.items)) return data.items;
  if (data?.content && Array.isArray(data.content)) return data.content;
  if (data?.data && Array.isArray(data.data)) return data.data;
  if (Array.isArray(data)) return data;
  return [];
};

// Extract total count defensively
const extractTotal = (data) => {
  if (!data) return 0;
  if (typeof data?.data?.total === 'number') return data.data.total;
  if (typeof data?.data?.totalElements === 'number') return data.data.totalElements;
  if (typeof data?.total === 'number') return data.total;
  if (typeof data?.totalElements === 'number') return data.totalElements;
  return extractItems(data).length;
};

// Extract page info defensively
const extractPage = (data) => {
  if (!data) return 1;
  if (typeof data?.data?.page === 'number') return data.data.page;
  if (typeof data?.page === 'number') return data.page;
  if (typeof data?.data?.number === 'number') return data.data.number + 1;
  if (typeof data?.number === 'number') return data.number + 1;
  return 1;
};

const extractSize = (data, defaultSize = 20) => {
  if (!data) return defaultSize;
  if (typeof data?.data?.size === 'number') return data.data.size;
  if (typeof data?.size === 'number') return data.size;
  return defaultSize;
};

/**
 * Visits List Page
 * Displays paginated list of visits with search, sort, and CRUD operations
 */
const VisitsList = () => {
  const navigate = useNavigate();
  const [searchInput, setSearchInput] = useState('');
  const [orderBy, setOrderBy] = useState('visitDate');
  const [order, setOrder] = useState('desc');
  const [apiError, setApiError] = useState(null);

  const { data, loading, error, params, setParams, refresh } = useVisitsList({
    sortBy: orderBy,
    sortDir: order
  });

  const handleSearch = useCallback(() => {
    setParams((prev) => ({ ...prev, search: searchInput, page: 1 }));
  }, [searchInput, setParams]);

  const handleSearchKeyPress = (e) => {
    if (e.key === 'Enter') handleSearch();
  };

  const handleSort = (column) => {
    const isAsc = orderBy === column && order === 'asc';
    const newOrder = isAsc ? 'desc' : 'asc';
    setOrder(newOrder);
    setOrderBy(column);
    setParams((prev) => ({ ...prev, sortBy: column, sortDir: newOrder, page: 1 }));
  };

  const handlePageChange = (event, newPage) => {
    setParams((prev) => ({ ...prev, page: newPage + 1 }));
  };

  const handleRowsPerPageChange = (event) => {
    const newSize = parseInt(event.target.value, 10);
    setParams((prev) => ({ ...prev, size: newSize, page: 1 }));
  };

  const handleEdit = (id) => {
    navigate(`/visits/edit/${id}`);
  };

  const handleDelete = async (id) => {
    if (window.confirm('هل أنت متأكد من حذف هذه الزيارة؟')) {
      try {
        await visitsService.remove(id);
        setApiError(null);
        refresh();
      } catch (err) {
        console.error('Failed to delete visit:', err);
        setApiError(err.message || 'فشل حذف الزيارة');
      }
    }
  };

  const handleCreateClaim = (visitId) => {
    navigate(`/visits/${visitId}/create-claim`);
  };

  const handleCreatePreAuth = (visitId) => {
    navigate(`/visits/${visitId}/create-preauth`);
  };

  const breadcrumbs = [{ title: 'الزيارات' }];

  // ════════════════════════════════════════════════════════════════════════
  // TABLE COLUMNS DEFINITION
  // ════════════════════════════════════════════════════════════════════════
  const columns = [
    {
      id: 'visitDate',
      label: 'تاريخ الزيارة',
      minWidth: '9.375rem',
      sortable: true
    },
    {
      id: 'member',
      label: 'المؤمَّن عليه',
      minWidth: '10.0rem',
      icon: <PersonIcon fontSize="small" />,
      sortable: false
    },
    {
      id: 'provider',
      label: 'مقدم الخدمة',
      minWidth: '11.25rem',
      sortable: false
    },
    {
      id: 'services',
      label: 'الخدمات المقدمة',
      minWidth: '12.5rem',
      sortable: false
    },
    {
      id: 'claimPreAuth',
      label: 'حالة المطالبة / الموافقة',
      minWidth: '12.5rem',
      align: 'center',
      sortable: false
    },
    {
      id: 'status',
      label: 'الحالة',
      minWidth: '6.875rem',
      align: 'center',
      sortable: false
    },
    {
      id: 'actions',
      label: 'الإجراءات',
      minWidth: '20.0rem',
      align: 'center',
      sortable: false
    }
  ];

  // ════════════════════════════════════════════════════════════════════════
  // TABLE CELL RENDERER
  // ════════════════════════════════════════════════════════════════════════
  const renderCell = (visit, column) => {
    if (!visit) return null;
    const visitId = visit?.id ?? Math.random();
    const visitStatus = getVisitStatus(visit);
    const networkTier = getNetworkTier(visit?.provider);
    const services = Array.isArray(visit?.services) ? visit.services : [];

    switch (column.id) {
      case 'visitDate':
        return (
          <Stack spacing={0.5}>
            <Typography variant="body2" fontWeight="medium">
              {visit?.visitDate ? new Date(visit.visitDate).toLocaleDateString('en-GB') : '—'}
            </Typography>
            {visit?.visitType && (
              <Chip
                label={VISIT_TYPE_LABELS_AR[visit.visitType] ?? visit.visitType}
                color={VISIT_TYPE_COLORS[visit.visitType] ?? 'default'}
                size="small"
                variant="outlined"
              />
            )}
          </Stack>
        );

      case 'member':
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            <PersonIcon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />
            <Typography variant="body2">{visit?.member?.fullName ?? '—'}</Typography>
          </Stack>
        );

      case 'provider':
        return (
          <Stack spacing={0.5}>
            <Typography variant="body2">{visit?.provider?.name ?? '—'}</Typography>
            {networkTier && <NetworkBadge networkTier={networkTier} showLabel={true} size="small" language="ar" />}
          </Stack>
        );

      case 'services':
        if (services.length > 0) {
          return (
            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
              {services.slice(0, 3).map((service, idx) => (
                <Tooltip key={service?.id ?? idx} title={service?.name ?? ''}>
                  <Chip
                    icon={<MedicalServicesIcon sx={{ fontSize: '0.875rem' }} />}
                    label={service?.code ?? service?.name?.substring(0, 10) ?? `خدمة ${idx + 1}`}
                    size="small"
                    variant="outlined"
                    color="primary"
                    sx={{ mb: 0.5 }}
                  />
                </Tooltip>
              ))}
              {services.length > 3 && <Chip label={`+${services.length - 3}`} size="small" color="default" sx={{ mb: 0.5 }} />}
            </Stack>
          );
        }
        return (
          <Typography variant="body2" color="text.secondary">
            لا توجد خدمات
          </Typography>
        );

      case 'claimPreAuth':
        return (
          <Stack spacing={0.5} alignItems="center">
            {visit?.latestClaimStatus ? (
              <Tooltip title={`مطالبة #${visit.latestClaimId} - ${visit.latestClaimStatusLabel || visit.latestClaimStatus}`}>
                <Chip
                  icon={<ReceiptIcon sx={{ fontSize: '0.875rem' }} />}
                  label={visit.latestClaimStatusLabel || CLAIM_STATUS_CONFIG[visit.latestClaimStatus]?.label || visit.latestClaimStatus}
                  color={CLAIM_STATUS_CONFIG[visit.latestClaimStatus]?.color || 'default'}
                  size="small"
                  onClick={() => navigate(`/claims/${visit.latestClaimId}`)}
                  sx={{ cursor: 'pointer', '&:hover': { opacity: 0.8 } }}
                />
              </Tooltip>
            ) : (
              <Typography variant="caption" color="text.secondary">
                لا مطالبة
              </Typography>
            )}
            {visit?.latestPreAuthStatus ? (
              <Tooltip title={`موافقة مسبقة #${visit.latestPreAuthId} - ${visit.latestPreAuthStatusLabel || visit.latestPreAuthStatus}`}>
                <Chip
                  icon={<AssignmentIcon sx={{ fontSize: '0.875rem' }} />}
                  label={
                    visit.latestPreAuthStatusLabel || PREAUTH_STATUS_CONFIG[visit.latestPreAuthStatus]?.label || visit.latestPreAuthStatus
                  }
                  color={PREAUTH_STATUS_CONFIG[visit.latestPreAuthStatus]?.color || 'default'}
                  size="small"
                  variant="outlined"
                  onClick={() => navigate(`/pre-authorizations/${visit.latestPreAuthId}`)}
                  sx={{ cursor: 'pointer', '&:hover': { opacity: 0.8 } }}
                />
              </Tooltip>
            ) : (
              <Typography variant="caption" color="text.secondary">
                لا موافقة مسبقة
              </Typography>
            )}
            {(visit?.claimCount > 1 || visit?.preAuthCount > 1) && (
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.75rem' }}>
                {visit?.claimCount > 1 && `${visit.claimCount} مطالبات`}
                {visit?.claimCount > 1 && visit?.preAuthCount > 1 && ' | '}
                {visit?.preAuthCount > 1 && `${visit.preAuthCount} موافقات`}
              </Typography>
            )}
          </Stack>
        );

      case 'status':
        return (
          <CardStatusBadge status={visitStatus} customLabel={STATUS_LABELS_AR[visitStatus] ?? 'غير محدد'} size="small" variant="chip" />
        );

      case 'actions':
        return (
          <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
            {projectSettings.features.directClaimSubmissionEnabled && (
              <Button
                size="small"
                variant="contained"
                onClick={() => handleCreateClaim(visitId)}
                sx={{
                  bgcolor: '#2e7d32',
                  color: 'white',
                  minWidth: '5.0rem',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  '&:hover': {
                    bgcolor: '#1b5e20'
                  }
                }}
              >
                مطالبة
              </Button>
            )}
            {projectSettings.features.directClaimSubmissionEnabled && (
              <Button
                size="small"
                variant="contained"
                onClick={() => handleCreatePreAuth(visitId)}
                sx={{
                  bgcolor: '#2e7d32',
                  color: 'white',
                  minWidth: '6.875rem',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  '&:hover': {
                    bgcolor: '#1b5e20'
                  }
                }}
              >
                موافقة مسبقة
              </Button>
            )}
            <Tooltip title="تعديل">
              <IconButton size="small" color="primary" onClick={() => handleEdit(visitId)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="حذف">
              <IconButton size="small" color="error" onClick={() => handleDelete(visitId)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        );

      default:
        return null;
    }
  };

  return (
    <>
      <ModernPageHeader
        title="الزيارات"
        subtitle="إدارة زيارات الأعضاء لمقدمي الخدمة"
        icon={LocalHospitalIcon}
        breadcrumbs={breadcrumbs}
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/eligibility')}>
            فحص الأهلية
          </Button>
        }
      />

      <MainCard>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center" justifyContent="space-between" sx={{ mb: '1.5rem' }}>
          <TextField
            placeholder="بحث..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyPress={handleSearchKeyPress}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              )
            }}
            sx={{ minWidth: { xs: '100%', sm: '150.0rem' } }}
          />
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<SearchIcon />} onClick={handleSearch}>
              بحث
            </Button>
            <IconButton onClick={refresh} color="primary">
              <RefreshIcon />
            </IconButton>
          </Stack>
        </Stack>

        <UnifiedMedicalTable
          columns={columns}
          data={extractItems(data)}
          loading={loading}
          error={error || apiError}
          onErrorClose={() => setApiError(null)}
          renderCell={renderCell}
          totalItems={extractTotal(data)}
          page={extractPage(data) - 1}
          rowsPerPage={extractSize(data)}
          onPageChange={handlePageChange}
          onRowsPerPageChange={handleRowsPerPageChange}
          emptyStateConfig={{
            icon: LocalHospitalIcon,
            title: 'لا توجد زيارات طبية مسجلة حاليًا',
            description: params.search ? 'لم يتم العثور على نتائج للبحث' : 'ابدأ بإضافة زيارة طبية جديدة',
            action: !params.search ? (
              <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/provider/eligibility-check')}>
                تسجيل زيارة جديدة
              </Button>
            ) : undefined
          }}
          sortConfig={{
            orderBy,
            order,
            onSort: handleSort
          }}
        />
      </MainCard>
    </>
  );
};

export default VisitsList;



