import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

// Material UI
import {
  Box,
  Paper,
  Typography,
  Chip,
  Button,
  IconButton,
  TextField,
  InputAdornment,
  Grid,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
  Stack,
  Alert,
  Collapse
} from '@mui/material';
import { DatePicker, LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';

// Icons
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import ReceiptIcon from '@mui/icons-material/Receipt';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import FilterAltIcon from '@mui/icons-material/FilterAlt';
import FilterAltOffIcon from '@mui/icons-material/FilterAltOff';
import CloseIcon from '@mui/icons-material/Close';
import FolderIcon from '@mui/icons-material/Folder';
import PersonIcon from '@mui/icons-material/Person';
import BadgeIcon from '@mui/icons-material/Badge';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import EventIcon from '@mui/icons-material/Event';

// Services
import { providerApi } from 'services/providerService';

// Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { UnifiedMedicalTable } from 'components/common';

// ========================= LABELS (Arabic) =========================
const LABELS = {
  pageTitle: 'سجل الزيارات',
  pageSubtitle: 'إدارة ومتابعة زيارات المرضى',
  visitId: 'رقم الزيارة',
  memberName: 'اسم المؤمن عليه',
  civilId: 'الرقم المدني',
  cardNumber: 'رقم بطاقة المؤمن عليه',
  visitDate: 'تاريخ الزيارة',
  visitType: 'نوع الزيارة',
  status: 'حالة الزيارة',
  employer: 'جهة العمل',
  diagnosis: 'التشخيص',
  actions: 'إجراءات الزيارة',
  createClaim: 'إنشاء مطالبة',
  reopenDraftClaim: 'فتح المسودة',
  createPreAuth: 'إنشاء موافقة مسبقة',
  viewDetails: 'عرض التفاصيل',
  viewDocuments: 'عرض المستندات',
  refresh: 'تحديث',
  search: 'بحث...',
  searchByName: 'بحث بالاسم أو الرقم المدني',
  filters: 'فلاتر البحث',
  hideFilters: 'إخفاء الفلاتر',
  showFilters: 'عرض الفلاتر',
  dateFrom: 'من تاريخ',
  dateTo: 'إلى تاريخ',
  allStatuses: 'جميع الحالات',
  allTypes: 'جميع الأنواع',
  noData: 'لا توجد زيارات مسجلة',
  loading: 'جارِ التحميل...',
  error: 'حدث خطأ أثناء جلب البيانات',
  rowsPerPage: 'صفوف لكل صفحة',
  close: 'إغلاق',
  visitSummary: 'ملخص الزيارة',
  memberInfo: 'بيانات المؤمن عليه',
  visitInfo: 'بيانات الزيارة',
  medicalInfo: 'البيانات الطبية',
  linkedRecords: 'السجلات المرتبطة',
  claims: 'المطالبات',
  preAuths: 'الموافقات المسبقة',
  claimStatus: 'حالة المطالبة',
  preAuthStatus: 'حالة الموافقة',
  noClaim: 'لا توجد',
  noPreAuth: 'لا توجد'
};

// ========================= STATUS CONFIGS =========================
const STATUS_COLORS = {
  REGISTERED: 'info',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'error',
  PENDING: 'default'
};

const STATUS_LABELS = {
  REGISTERED: 'مسجلة',
  IN_PROGRESS: 'قيد المعالجة',
  COMPLETED: 'مكتملة',
  CANCELLED: 'ملغاة',
  PENDING: 'معلقة'
};

// ========================= CLAIM STATUS CONFIGS =========================
const CLAIM_STATUS_COLORS = {
  DRAFT: 'default',
  SUBMITTED: 'info',
  UNDER_REVIEW: 'warning',
  APPROVAL_IN_PROGRESS: 'warning',
  APPROVED: 'success',
  BATCHED: 'secondary',
  NEEDS_CORRECTION: 'warning',
  REJECTED: 'error',
  SETTLED: 'success',
  PAID: 'success',
  CANCELLED: 'default'
};

const CLAIM_STATUS_LABELS = {
  DRAFT: 'مسودة',
  SUBMITTED: 'مقدمة',
  UNDER_REVIEW: 'قيد المراجعة',
  APPROVAL_IN_PROGRESS: 'جاري معالجة الموافقة',
  APPROVED: 'موافق عليها',
  BATCHED: 'ضمن دفعة تسوية',
  NEEDS_CORRECTION: 'تحتاج تصحيح',
  REJECTED: 'مرفوضة',
  SETTLED: 'مسددة',
  PAID: 'مدفوعة',
  CANCELLED: 'ملغاة'
};

// ========================= PRE-AUTH STATUS CONFIGS =========================
const PREAUTH_STATUS_COLORS = {
  PENDING: 'warning',
  UNDER_REVIEW: 'warning',
  APPROVAL_IN_PROGRESS: 'warning',
  APPROVED: 'success',
  ACKNOWLEDGED: 'info',
  REJECTED: 'error',
  NEEDS_CORRECTION: 'warning',
  EXPIRED: 'default',
  CANCELLED: 'default',
  USED: 'secondary'
};

const PREAUTH_STATUS_LABELS = {
  PENDING: 'معلقة',
  UNDER_REVIEW: 'قيد المراجعة',
  APPROVAL_IN_PROGRESS: 'جاري معالجة الموافقة',
  APPROVED: 'موافق عليها',
  ACKNOWLEDGED: 'تم الاطلاع',
  REJECTED: 'مرفوضة',
  NEEDS_CORRECTION: 'تحتاج تصحيح',
  EXPIRED: 'منتهية',
  CANCELLED: 'ملغاة',
  USED: 'مستخدمة'
};

const VISIT_TYPE_LABELS = {
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

// ========================= MAIN COMPONENT =========================
const ProviderVisitLog = () => {
  const navigate = useNavigate();

  // Data state
  const [visits, setVisits] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Pagination
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalCount, setTotalCount] = useState(0);

  // Filters - Default date to TODAY
  const [showFilters, setShowFilters] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [dateFrom, setDateFrom] = useState(dayjs()); // TODAY
  const [dateTo, setDateTo] = useState(dayjs()); // TODAY
  const [statusFilter, setStatusFilter] = useState('');
  const [visitTypeFilter, setVisitTypeFilter] = useState('');
  const [sortBy, setSortBy] = useState('visitId');
  const [sortDir, setSortDir] = useState('asc');

  // Fetch visits
  const fetchVisits = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // ════════════════════════════════════════════════════════════════════════
      // BUILD PARAMS WITH ALL FILTERS
      // Backend automatically handles provider isolation via ProviderContextGuard
      // ════════════════════════════════════════════════════════════════════════
      const params = {
        page: page,
        size: rowsPerPage,
        sortBy: sortBy === 'visitId' ? 'id' : sortBy,
        sortDir,
        // Search by member name/card/civilId
        memberName: searchQuery?.trim() || undefined,
        // Status filter
        status: statusFilter || undefined,
        // Date filters
        fromDate: dateFrom ? dateFrom.format('YYYY-MM-DD') : undefined,
        toDate: dateTo ? dateTo.format('YYYY-MM-DD') : undefined,
        // Visit type (optional)
        visitType: visitTypeFilter || undefined
      };

      // Remove undefined values
      Object.keys(params).forEach((key) => params[key] === undefined && delete params[key]);

      const response = await providerApi.getVisitLog(params);

      setVisits(response.content || response.items || []);
      setTotalCount(response.totalElements || response.totalCount || 0);
    } catch (err) {
      console.error('Error fetching visits:', err);
      setError(LABELS.error);
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage, sortBy, sortDir, searchQuery, dateFrom, dateTo, statusFilter, visitTypeFilter]);

  useEffect(() => {
    fetchVisits();
  }, [fetchVisits]);

  // Handlers
  const handlePageChange = (newPage) => setPage(newPage);

  const handleRowsPerPageChange = (newSize) => {
    setRowsPerPage(newSize);
    setPage(0);
  };

  const handleSearchChange = (event) => {
    setSearchQuery(event.target.value);
    setPage(0);
  };

  const handleCreateClaim = (visit) => {
    if (visit.latestClaimId && ['DRAFT', 'NEEDS_CORRECTION'].includes(visit.latestClaimStatus)) {
      const params = new URLSearchParams({
        fromVisitLog: 'true',
        claimId: visit.latestClaimId,
        visitId: visit.visitId,
        memberId: visit.memberId,
        memberName: visit.memberName || '',
        memberCivilId: visit.memberCivilId || '',
        cardNumber: visit.memberCardNumber || '',
        employer: visit.employerName || '',
        phone: visit.memberPhone || '',
        email: visit.memberEmail || '',
        visitDate: visit.visitDate || '',
        visitTime: visit.createdAt?.split('T')[1]?.substring(0, 5) || '',
        visitType: visit.visitType || 'OUTPATIENT',
        providerName: visit.providerName || ''
      });
      navigate(`/provider/claims/submit?${params.toString()}`);
      return;
    }

    // Use URL params to preserve data across redirects and page refreshes
    const params = new URLSearchParams({
      fromVisitLog: 'true',
      visitId: visit.visitId,
      memberId: visit.memberId,
      memberName: visit.memberName || '',
      memberCivilId: visit.memberCivilId || '',
      cardNumber: visit.memberCardNumber || '',
      employer: visit.employerName || '',
      phone: visit.memberPhone || '',
      email: visit.memberEmail || '',
      visitDate: visit.visitDate || '',
      visitTime: visit.createdAt?.split('T')[1]?.substring(0, 5) || '',
      visitType: visit.visitType || 'OUTPATIENT',
      providerName: visit.providerName || ''
    });
    navigate(`/provider/claims/submit?${params.toString()}`);
  };

  const handleCreatePreAuth = (visit) => {
    // Use URL params to preserve data across redirects and page refreshes
    const params = new URLSearchParams({
      fromVisitLog: 'true',
      visitId: visit.visitId,
      memberId: visit.memberId,
      memberName: visit.memberName || '',
      memberCivilId: visit.memberCivilId || '',
      cardNumber: visit.memberCardNumber || '',
      employer: visit.employerName || '',
      phone: visit.memberPhone || '',
      email: visit.memberEmail || '',
      visitDate: visit.visitDate || '',
      visitTime: visit.createdAt?.split('T')[1]?.substring(0, 5) || '',
      visitType: visit.visitType || 'OUTPATIENT',
      providerId: visit.providerId || '',
      providerName: visit.providerName || '',
      location: visit.providerLocation || ''
    });
    navigate(`/provider/pre-approvals/submit?${params.toString()}`);
  };

  const handleResetFilters = () => {
    setSearchQuery('');
    setDateFrom(dayjs()); // Reset to TODAY
    setDateTo(dayjs()); // Reset to TODAY
    setStatusFilter('');
    setVisitTypeFilter('');
    setSortBy('visitId');
    setSortDir('asc');
    setPage(0);
  };

  const handleSort = (columnId, direction) => {
    const allowedSortableColumns = ['visitId', 'visitDate', 'status', 'visitType'];
    if (!allowedSortableColumns.includes(columnId)) return;

    setSortBy(columnId);
    setSortDir(direction);
    setPage(0);
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // TABLE COLUMNS DEFINITION
  // ═══════════════════════════════════════════════════════════════════════════
  const columns = [
    { id: 'visitId', label: LABELS.visitId, minWidth: '5.0rem', icon: <BadgeIcon fontSize="small" />, sortable: true },
    { id: 'memberName', label: LABELS.memberName, minWidth: '10.0rem', icon: <PersonIcon fontSize="small" /> },
    { id: 'memberCivilId', label: LABELS.civilId, minWidth: '6.875rem', icon: <CreditCardIcon fontSize="small" /> },
    { id: 'memberCardNumber', label: LABELS.cardNumber, minWidth: '6.25rem', icon: <CreditCardIcon fontSize="small" /> },
    { id: 'visitDate', label: LABELS.visitDate, minWidth: '6.25rem', icon: <EventIcon fontSize="small" />, sortable: true },
    { id: 'visitType', label: LABELS.visitType, minWidth: '6.25rem', sortable: true },
    { id: 'status', label: LABELS.status, minWidth: '5.625rem', sortable: true },
    { id: 'claimStatus', label: LABELS.claimStatus, minWidth: '6.875rem' },
    { id: 'preAuthStatus', label: LABELS.preAuthStatus, minWidth: '6.875rem' },
    { id: 'actions', label: LABELS.actions, minWidth: '8.75rem' }
  ];

  // ═══════════════════════════════════════════════════════════════════════════
  // TABLE CELL RENDERER
  // ═══════════════════════════════════════════════════════════════════════════
  const renderCell = (visit, column) => {
    switch (column.id) {
      case 'visitId':
        return (
          <Chip
            label={`#${visit.visitId}`}
            size="small"
            color="primary"
            variant="outlined"
            sx={{ fontWeight: 'bold', fontFamily: 'monospace' }}
          />
        );

      case 'memberName':
        return (
          <Box>
            <Typography variant="body2" fontWeight="500">
              {visit.memberName || '-'}
            </Typography>
            {visit.employerName && (
              <Typography variant="caption" color="textSecondary" display="block">
                {visit.employerName}
              </Typography>
            )}
          </Box>
        );

      case 'memberCivilId':
        return (
          <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
            {visit.memberCivilId || '-'}
          </Typography>
        );

      case 'memberCardNumber':
        return (
          <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
            {visit.memberCardNumber || '-'}
          </Typography>
        );

      case 'visitDate':
        return <Typography variant="body2">{visit.visitDate ? dayjs(visit.visitDate).format('DD/MM/YYYY') : '-'}</Typography>;

      case 'visitType':
        return (
          <Chip
            label={VISIT_TYPE_LABELS[visit.visitType] || visit.visitTypeLabel || visit.visitType || '-'}
            size="small"
            variant="outlined"
            color="default"
          />
        );

      case 'status':
        return (
          <Chip
            label={STATUS_LABELS[visit.status] || visit.statusLabel || visit.status || '-'}
            size="small"
            color={STATUS_COLORS[visit.status] || 'default'}
          />
        );

      case 'claimStatus':
        if (visit.claimCount > 0 || visit.latestClaimStatus) {
          return (
            <Stack spacing={0.5}>
              <Chip
                label={CLAIM_STATUS_LABELS[visit.latestClaimStatus] || visit.latestClaimStatusLabel || LABELS.noClaim}
                size="small"
                color={CLAIM_STATUS_COLORS[visit.latestClaimStatus] || 'default'}
                variant="outlined"
              />
              {visit.claimCount > 1 && (
                <Typography variant="caption" color="text.secondary">
                  +{visit.claimCount - 1} أخرى
                </Typography>
              )}
            </Stack>
          );
        }
        return (
          <Typography variant="caption" color="text.secondary">
            {LABELS.noClaim}
          </Typography>
        );

      case 'preAuthStatus':
        if (visit.preAuthCount > 0 || visit.latestPreAuthStatus) {
          return (
            <Stack spacing={0.5}>
              <Chip
                label={PREAUTH_STATUS_LABELS[visit.latestPreAuthStatus] || visit.latestPreAuthStatusLabel || LABELS.noPreAuth}
                size="small"
                color={PREAUTH_STATUS_COLORS[visit.latestPreAuthStatus] || 'default'}
                variant="outlined"
              />
              {visit.preAuthCount > 1 && (
                <Typography variant="caption" color="text.secondary">
                  +{visit.preAuthCount - 1} أخرى
                </Typography>
              )}
            </Stack>
          );
        }
        return (
          <Typography variant="caption" color="text.secondary">
            {LABELS.noPreAuth}
          </Typography>
        );

      case 'actions':
        return (
          <Box>
            <Stack direction="row" spacing={0.5}>
              {/* Create Claim */}
              {visit.canCreateClaim !== false && (
                <Tooltip title={['DRAFT', 'NEEDS_CORRECTION'].includes(visit.latestClaimStatus) ? LABELS.reopenDraftClaim : LABELS.createClaim}>
                  <IconButton
                    size="medium"
                    color={['DRAFT', 'NEEDS_CORRECTION'].includes(visit.latestClaimStatus) ? 'warning' : 'success'}
                    onClick={() => handleCreateClaim(visit)}
                    sx={{ p: 0.8 }}
                  >
                    <ReceiptIcon fontSize="medium" />
                  </IconButton>
                </Tooltip>
              )}

              {/* Create Pre-Auth */}
              {visit.canCreatePreAuth !== false && (
                <Tooltip title={LABELS.createPreAuth}>
                  <IconButton size="medium" color="info" onClick={() => handleCreatePreAuth(visit)} sx={{ p: 0.8 }}>
                    <CheckCircleOutlineIcon fontSize="medium" />
                  </IconButton>
                </Tooltip>
              )}

            </Stack>

            {/* Linked counts badges */}
            {(visit.claimCount > 0 || visit.preAuthCount > 0) && (
              <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                {visit.claimCount > 0 && (
                  <Chip
                    size="small"
                    label={`${visit.claimCount} مطالبة`}
                    sx={{ fontSize: '0.75rem', height: '1.125rem' }}
                    color="success"
                    variant="outlined"
                  />
                )}
                {visit.preAuthCount > 0 && (
                  <Chip
                    size="small"
                    label={`${visit.preAuthCount} موافقة`}
                    sx={{ fontSize: '0.75rem', height: '1.125rem' }}
                    color="info"
                    variant="outlined"
                  />
                )}
              </Stack>
            )}
          </Box>
        );

      default:
        return visit[column.id];
    }
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <ModernPageHeader
        title={LABELS.pageTitle}
        subtitle={LABELS.pageSubtitle}
        icon={LocalHospitalIcon}
        breadcrumbs={[{ label: 'بوابة مقدم الخدمة' }, { label: LABELS.pageTitle }]}
        actions={
          <Stack direction="row" spacing={1}>
            <Tooltip title="مركز المستندات">
              <Button
                variant="outlined"
                size="small"
                onClick={() => navigate('/provider/documents')}
                startIcon={<FolderIcon />}
                color="secondary"
              >
                المستندات
              </Button>
            </Tooltip>
            <Tooltip title={showFilters ? LABELS.hideFilters : LABELS.showFilters}>
              <Button
                variant="outlined"
                size="small"
                onClick={() => setShowFilters(!showFilters)}
                startIcon={showFilters ? <FilterAltOffIcon /> : <FilterAltIcon />}
              >
                {showFilters ? LABELS.hideFilters : LABELS.showFilters}
              </Button>
            </Tooltip>
            <Tooltip title={LABELS.refresh}>
              <span>
                <IconButton onClick={fetchVisits} color="primary" disabled={loading}>
                  <RefreshIcon />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
        }
      />

      <MainCard>
        {/* Error Alert */}
        {error && (
          <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* Quick Search Bar */}
        <Box sx={{ mb: '1.0rem' }}>
          <TextField
            fullWidth
            placeholder={LABELS.searchByName}
            value={searchQuery}
            onChange={handleSearchChange}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
              ),
              endAdornment: searchQuery && (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={() => {
                      setSearchQuery('');
                      setPage(0);
                    }}
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              )
            }}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: 'background.paper'
              }
            }}
          />
        </Box>

        {/* Advanced Filters */}
        <Collapse in={showFilters}>
          <Paper variant="outlined" sx={{ p: '1.0rem', mb: '1.0rem', bgcolor: '#fafafa' }}>
            <Grid container spacing={2} alignItems="center">
              <Grid xs={12} sm={6} md={3}>
                <DatePicker
                  label={LABELS.dateFrom}
                  value={dateFrom}
                  onChange={setDateFrom}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'medium',
                      sx: {
                        '& .MuiOutlinedInput-root': {
                          minHeight: '2.875rem'
                        }
                      },
                      InputProps: {
                        startAdornment: <EventIcon sx={{ mr: 1, color: 'action.active' }} />
                      }
                    }
                  }}
                />
              </Grid>
              <Grid xs={12} sm={6} md={3}>
                <DatePicker
                  label={LABELS.dateTo}
                  value={dateTo}
                  onChange={setDateTo}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      size: 'medium',
                      sx: {
                        '& .MuiOutlinedInput-root': {
                          minHeight: '2.875rem'
                        }
                      },
                      InputProps: {
                        startAdornment: <EventIcon sx={{ mr: 1, color: 'action.active' }} />
                      }
                    }
                  }}
                />
              </Grid>
              <Grid xs={12} sm={6} md={2}>
                <FormControl
                  fullWidth
                  size="medium"
                  sx={{
                    '& .MuiOutlinedInput-root': {
                      minHeight: '2.875rem'
                    }
                  }}
                >
                  <InputLabel>{LABELS.status}</InputLabel>
                  <Select
                    value={statusFilter}
                    onChange={(e) => {
                      setStatusFilter(e.target.value);
                      setPage(0);
                    }}
                    label={LABELS.status}
                  >
                    <MenuItem value="">{LABELS.allStatuses}</MenuItem>
                    {Object.entries(STATUS_LABELS).map(([key, label]) => (
                      <MenuItem key={key} value={key}>
                        {label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid xs={12} sm={6} md={2}>
                <FormControl
                  fullWidth
                  size="medium"
                  sx={{
                    '& .MuiOutlinedInput-root': {
                      minHeight: '2.875rem'
                    }
                  }}
                >
                  <InputLabel>{LABELS.visitType}</InputLabel>
                  <Select
                    value={visitTypeFilter}
                    onChange={(e) => {
                      setVisitTypeFilter(e.target.value);
                      setPage(0);
                    }}
                    label={LABELS.visitType}
                  >
                    <MenuItem value="">{LABELS.allTypes}</MenuItem>
                    {Object.entries(VISIT_TYPE_LABELS).map(([key, label]) => (
                      <MenuItem key={key} value={key}>
                        {label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid xs={12} sm={6} md={2}>
                <Button fullWidth variant="outlined" color="secondary" size="large" onClick={handleResetFilters} startIcon={<FilterAltOffIcon />}>
                  إعادة ضبط
                </Button>
              </Grid>
            </Grid>
          </Paper>
        </Collapse>

        {/* Stats Summary */}
        <Box sx={{ mb: '1.0rem' }}>
          <Stack direction="row" spacing={2} flexWrap="wrap">
            <Chip icon={<LocalHospitalIcon />} label={`إجمالي: ${totalCount} زيارة`} variant="outlined" color="primary" />
            {searchQuery && <Chip label={`نتائج البحث: "${searchQuery}"`} onDelete={() => setSearchQuery('')} color="info" size="small" />}
            {statusFilter && (
              <Chip label={`الحالة: ${STATUS_LABELS[statusFilter]}`} onDelete={() => setStatusFilter('')} color="warning" size="small" />
            )}
            {visitTypeFilter && (
              <Chip
                label={`نوع الزيارة: ${VISIT_TYPE_LABELS[visitTypeFilter] || visitTypeFilter}`}
                onDelete={() => setVisitTypeFilter('')}
                color="secondary"
                size="small"
              />
            )}
          </Stack>
        </Box>

        {/* Unified Medical Table */}
        <UnifiedMedicalTable
          columns={columns}
          rows={visits}
          loading={loading}
          totalCount={totalCount}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={handlePageChange}
          onRowsPerPageChange={handleRowsPerPageChange}
          sortBy={sortBy}
          sortDirection={sortDir}
          onSort={handleSort}
          renderCell={renderCell}
          getRowKey={(visit) => visit.visitId}
          emptyMessage={LABELS.noData}
          loadingMessage={LABELS.loading}
        />
      </MainCard>
    </LocalizationProvider>
  );
};

export default ProviderVisitLog;

