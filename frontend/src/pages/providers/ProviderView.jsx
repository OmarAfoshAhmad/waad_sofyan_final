import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Grid,
  InputAdornment,
  Paper,
  Typography,
  Chip,
  CircularProgress,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Stack,
  Divider,
  TextField,
  Tooltip
} from '@mui/material';
import {
  ArrowBack,
  Edit,
  Phone,
  Email,
  ExpandMore as ExpandMoreIcon,
  LocationOn,
  Business,
  Badge,
  Category as CategoryIcon,
  Search as SearchIcon,
  VerifiedUser,
  LocalHospital as ProviderIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useProviderDetails } from 'hooks/useProviders';
import { providersService } from 'services/api';
import axiosClient from 'utils/axios';

const fetchProviderServices = async (providerId) => {
  const res = await axiosClient.get(`/api/v1/providers/${providerId}/services`);
  return res.data?.data ?? [];
};

// Insurance UX Components - Phase B2 Step 6
import { NetworkBadge, CardStatusBadge } from 'components/insurance';

// ============ PROVIDER CONFIGURATION ============
const PROVIDER_TYPE_LABELS = {
  HOSPITAL: 'مستشفى',
  CLINIC: 'عيادة',
  LAB: 'مختبر',
  LABORATORY: 'مختبر',
  PHARMACY: 'صيدلية',
  RADIOLOGY: 'مركز أشعة'
};

const PROVIDER_TYPE_COLORS = {
  HOSPITAL: 'error',
  CLINIC: 'primary',
  LAB: 'warning',
  LABORATORY: 'warning',
  PHARMACY: 'success',
  RADIOLOGY: 'info'
};

// Status Labels (Arabic)
const STATUS_LABELS_AR = {
  ACTIVE: 'نشط',
  INACTIVE: 'غير نشط',
  SUSPENDED: 'موقوف',
  EXPIRED: 'منتهي'
};

// Network Status mapping
const getNetworkTier = (provider) => {
  if (provider?.networkStatus) return provider.networkStatus;
  if (provider?.inNetwork === true) return 'IN_NETWORK';
  if (provider?.inNetwork === false) return 'OUT_OF_NETWORK';
  if (provider?.contracted === true) return 'IN_NETWORK';
  if (provider?.contracted === false) return 'OUT_OF_NETWORK';
  return null;
};

// Get provider status
const getProviderStatus = (provider) => {
  if (provider?.status) return provider.status;
  if (provider?.active === true) return 'ACTIVE';
  if (provider?.active === false) return 'INACTIVE';
  return 'ACTIVE';
};

const ProviderView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { provider, loading } = useProviderDetails(id);
  const [contracts, setContracts] = useState([]);
  const [loadingContracts, setLoadingContracts] = useState(false);
  const [activeTab, setActiveTab] = useState(0);
  const [serviceSearch, setServiceSearch] = useState('');
  const [expandedCategories, setExpandedCategories] = useState(new Set());

  const {
    data: providerServices = [],
    isLoading: loadingServices,
    error: servicesError
  } = useQuery({
    queryKey: ['provider-services', id],
    queryFn: () => fetchProviderServices(id),
    enabled: !!id && activeTab === 1,
    staleTime: 5 * 60 * 1000
  });

  // Group services by category
  const servicesByCategory = useMemo(() => {
    const q = serviceSearch.trim().toLowerCase();
    const filtered = q
      ? providerServices.filter(
          (s) =>
            s.service_code?.toLowerCase().includes(q) ||
            s.service_name?.toLowerCase().includes(q) ||
            s.category_name?.toLowerCase().includes(q)
        )
      : providerServices;

    const map = new Map();
    filtered.forEach((svc) => {
      const catKey = svc.category_code || 'uncategorized';
      const catName = svc.category_name || 'غير مصنفة';
      if (!map.has(catKey)) map.set(catKey, { code: catKey, name: catName, services: [] });
      map.get(catKey).services.push(svc);
    });
    return [...map.values()].sort((a, b) => a.code.localeCompare(b.code));
  }, [providerServices, serviceSearch]);

  const toggleCategory = (code) =>
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      next.has(code) ? next.delete(code) : next.add(code);
      return next;
    });

  useEffect(() => {
    const fetchContracts = async () => {
      if (id) {
        setLoadingContracts(true);
        try {
          // Fetch contracts for this provider
          const response = await axiosClient.get(`/provider-contracts/provider/${id}`);
          // Handle paginated response (Page object) or array
          const data = response.data?.data?.content || response.data?.data || response.data?.content || [];
          setContracts(Array.isArray(data) ? data : [data]);
        } catch (error) {
          console.error('Failed to fetch contracts:', error);
          setContracts([]);
        } finally {
          setLoadingContracts(false);
        }
      }
    };
    fetchContracts();
  }, [id]);

  if (loading) {
    return (
      <MainCard>
        <Box sx={{ display: 'flex', justifyContent: 'center', p: '1.5rem' }}>
          <CircularProgress />
        </Box>
      </MainCard>
    );
  }

  if (!provider) {
    return (
      <MainCard>
        <Stack spacing={3} alignItems="center" sx={{ py: '2.0rem' }}>
          <Business sx={{ fontSize: '3.0rem', color: '#ff4d4f' }} />
          <Typography variant="h5" color="error">
            مقدم الخدمة غير موجود
          </Typography>
          <Typography variant="body2" color="text.secondary">
            تأكد من صحة الرابط أو أن مقدم الخدمة لم يتم حذفه
          </Typography>
          <Button variant="contained" startIcon={<ArrowBack />} onClick={() => navigate('/providers')}>
            العودة إلى القائمة
          </Button>
        </Stack>
      </MainCard>
    );
  }

  // Derive values defensively
  const providerName = provider?.name ?? '—';
  const providerDisplayName = providerName;
  const providerStatus = getProviderStatus(provider);
  const networkTier = getNetworkTier(provider);

  return (
    <>
      <ModernPageHeader
        title={`مقدم الخدمة: ${providerDisplayName}`}
        subtitle={`نوع مقدم الخدمة: ${PROVIDER_TYPE_LABELS[provider?.providerType] ?? provider?.providerType ?? '—'}`}
        icon={ProviderIcon}
        breadcrumbs={[{ label: 'مقدمو الخدمات', path: '/providers' }, { label: providerDisplayName }]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="contained" startIcon={<Edit />} onClick={() => navigate(`/providers/edit/${id}`)}>
              تعديل
            </Button>
            <Button startIcon={<ArrowBack />} onClick={() => navigate('/providers')}>
              عودة
            </Button>
          </Stack>
        }
      />

      <MainCard>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.0rem' }}>
          {/* Provider Type Chip */}
          <Chip
            label={PROVIDER_TYPE_LABELS[provider?.providerType] ?? provider?.providerType ?? '—'}
            color={PROVIDER_TYPE_COLORS[provider?.providerType] || 'default'}
            size="small"
            variant="outlined"
          />
          {/* Network Status Badge */}
          {networkTier && <NetworkBadge networkTier={networkTier} showLabel={true} size="small" language="ar" />}
          {/* Status Badge */}
          <CardStatusBadge
            status={providerStatus}
            customLabel={STATUS_LABELS_AR[providerStatus] ?? 'غير محدد'}
            size="small"
            variant="chip"
          />
        </Stack>

        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{ borderBottom: 1, borderColor: 'divider', mb: '1.5rem' }}
        >
          <Tab label="بيانات مقدم الخدمة" />
          <Tab
            label={
              providerServices.length > 0
                ? `الخدمات المقدمة (${providerServices.length})`
                : 'الخدمات المقدمة'
            }
            icon={<CategoryIcon fontSize="small" />}
            iconPosition="start"
          />
        </Tabs>

        {/* ── Tab 0: Provider info + contracts ── */}
        {activeTab === 0 && (
        <Grid container spacing={3}>
          {/* Basic Information */}
          <Grid size={12}>
            <Paper sx={{ p: '1.5rem' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.0rem' }}>
                <Badge sx={{ color: '#1890ff' }} />
                <Typography variant="h5">البيانات الأساسية</Typography>
              </Stack>
              <Divider sx={{ mb: '1.0rem' }} />
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    الرمز التلقائي
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.id ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    اسم مقدم الخدمة
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {providerName}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    رقم الترخيص
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.licenseNumber ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    الرقم الضريبي
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.taxNumber ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    نوع مقدم الخدمة
                  </Typography>
                  <Box sx={{ mt: 0.5 }}>
                    <Chip
                      label={PROVIDER_TYPE_LABELS[provider?.providerType] ?? provider?.providerType ?? '—'}
                      color={PROVIDER_TYPE_COLORS[provider?.providerType] || 'default'}
                      size="small"
                    />
                  </Box>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    الحالة التشغيلية
                  </Typography>
                  <Box sx={{ mt: 0.5 }}>
                    <CardStatusBadge
                      status={providerStatus}
                      customLabel={STATUS_LABELS_AR[providerStatus] ?? 'غير محدد'}
                      size="small"
                      variant="chip"
                    />
                  </Box>
                </Grid>
              </Grid>
            </Paper>
          </Grid>

          {/* Location & Contact Information */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper sx={{ p: '1.5rem', height: '100%' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.0rem' }}>
                <Phone sx={{ color: '#52c41a' }} />
                <Typography variant="h5">بيانات التواصل</Typography>
              </Stack>
              <Divider sx={{ mb: '1.0rem' }} />
              <Grid container spacing={2}>
                <Grid size={12}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <LocationOn sx={{ fontSize: '1.125rem', color: '#8c8c8c' }} />
                    <Typography variant="body2" color="text.secondary">
                      المدينة
                    </Typography>
                  </Stack>
                  <Typography variant="body1" fontWeight={500} sx={{ mt: 0.5, mr: '1.5rem' }}>
                    {provider?.city ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <LocationOn sx={{ fontSize: '1.125rem', color: '#8c8c8c' }} />
                    <Typography variant="body2" color="text.secondary">
                      العنوان
                    </Typography>
                  </Stack>
                  <Typography variant="body1" fontWeight={500} sx={{ mt: 0.5, mr: '1.5rem' }}>
                    {provider?.address ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Phone sx={{ fontSize: '1.125rem', color: '#8c8c8c' }} />
                    <Typography variant="body2" color="text.secondary">
                      رقم الهاتف
                    </Typography>
                  </Stack>
                  <Typography variant="body1" fontWeight={500} sx={{ mt: 0.5, mr: '1.5rem' }}>
                    {provider?.phone ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Email sx={{ fontSize: '1.125rem', color: '#8c8c8c' }} />
                    <Typography variant="body2" color="text.secondary">
                      البريد الإلكتروني
                    </Typography>
                  </Stack>
                  <Typography variant="body1" fontWeight={500} sx={{ mt: 0.5, mr: '1.5rem' }}>
                    {provider?.email ?? '—'}
                  </Typography>
                </Grid>
              </Grid>
            </Paper>
          </Grid>

          {/* Contract Information */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper sx={{ p: '1.5rem', height: '100%' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.0rem' }}>
                <VerifiedUser sx={{ color: '#faad14' }} />
                <Typography variant="h5">معلومات العقد والتشغيل</Typography>
              </Stack>
              <Divider sx={{ mb: '1.0rem' }} />
              <Grid container spacing={2}>
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    تاريخ بداية العقد
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.contractStartDate ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    تاريخ نهاية العقد
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.contractEndDate ?? '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    نسبة الخصم الافتراضية
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.defaultDiscountRate ? `${provider.defaultDiscountRate}%` : '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    تاريخ الإنشاء
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.createdAt ? new Date(provider.createdAt).toLocaleDateString('en-US') : '—'}
                  </Typography>
                </Grid>
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    آخر تحديث
                  </Typography>
                  <Typography variant="body1" fontWeight={500}>
                    {provider?.updatedAt ? new Date(provider.updatedAt).toLocaleDateString('en-US') : '—'}
                  </Typography>
                </Grid>
              </Grid>
            </Paper>
          </Grid>

          {/* Provider Contracts */}
          <Grid size={12}>
            <Paper sx={{ p: '1.5rem' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.0rem' }}>
                <VerifiedUser sx={{ color: '#1890ff' }} />
                <Typography variant="h5">عقود مقدم الخدمة</Typography>
              </Stack>
              <Divider sx={{ mb: '1.0rem' }} />
              {loadingContracts ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', p: '1.5rem' }}>
                  <CircularProgress />
                </Box>
              ) : !Array.isArray(contracts) || contracts.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: '1.5rem' }}>
                  لا توجد عقود لهذا المزود
                </Typography>
              ) : (
                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>رقم العقد</TableCell>
                        <TableCell>تاريخ البداية</TableCell>
                        <TableCell>تاريخ النهاية</TableCell>
                        <TableCell>نسبة الخصم</TableCell>
                        <TableCell>التجديد التلقائي</TableCell>
                        <TableCell>الحالة</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {Array.isArray(contracts) &&
                        contracts.map((contract) => (
                          <TableRow key={contract.id}>
                            <TableCell>{contract.contractCode || contract.contractNumber || `#${contract.id}`}</TableCell>
                            <TableCell>{contract.startDate ? new Date(contract.startDate).toLocaleDateString('ar-LY') : '—'}</TableCell>
                            <TableCell>{contract.endDate ? new Date(contract.endDate).toLocaleDateString('ar-LY') : '—'}</TableCell>
                            <TableCell>{contract.discountPercent ? `${contract.discountPercent}%` : '—'}</TableCell>
                            <TableCell>
                              <Chip
                                label={contract.autoRenew ? 'نعم' : 'لا'}
                                color={contract.autoRenew ? 'success' : 'default'}
                                size="small"
                              />
                            </TableCell>
                            <TableCell>
                              <Chip
                                label={contract.statusLabel || (contract.isCurrentlyEffective ? 'ساري' : 'غير ساري')}
                                color={contract.isCurrentlyEffective ? 'success' : 'default'}
                                size="small"
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Paper>
          </Grid>
        </Grid>
        )} {/* end Tab 0 */}

        {/* ── Tab 1: Services by category ── */}
        {activeTab === 1 && (
          <Box>
            <Box sx={{ mb: '1.0rem' }}>
              <TextField
                size="small"
                placeholder="بحث في الخدمات أو التصنيفات..."
                value={serviceSearch}
                onChange={(e) => setServiceSearch(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  )
                }}
                sx={{ width: { xs: '100%', sm: '160.0rem' } }}
              />
            </Box>

            {loadingServices && (
              <Box sx={{ display: 'flex', justifyContent: 'center', p: '2.0rem' }}>
                <CircularProgress />
              </Box>
            )}

            {servicesError && (
              <Alert severity="error" sx={{ mb: '1.0rem' }}>
                حدث خطأ أثناء تحميل الخدمات
              </Alert>
            )}

            {!loadingServices && !servicesError && servicesByCategory.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: '2.0rem' }}>
                {serviceSearch ? 'لا توجد نتائج مطابقة للبحث' : 'لا توجد خدمات مسجلة لهذا المزود'}
              </Typography>
            )}

            {servicesByCategory.map((cat) => (
              <Accordion
                key={cat.code}
                expanded={expandedCategories.has(cat.code)}
                onChange={() => toggleCategory(cat.code)}
                sx={{ mb: 1 }}
              >
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CategoryIcon fontSize="small" sx={{ color: '#1890ff' }} />
                    <Typography fontWeight={600}>{cat.name}</Typography>
                    <Chip label={cat.services.length} size="small" color="primary" variant="outlined" />
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ p: 0 }}>
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>رمز الخدمة</TableCell>
                          <TableCell>اسم الخدمة</TableCell>
                          <TableCell>يتطلب موافقة مسبقة</TableCell>
                          <TableCell>الحالة</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {cat.services.map((svc) => (
                          <TableRow key={svc.id ?? svc.serviceCode ?? svc.service_code}>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                              {svc.serviceCode ?? svc.service_code ?? '—'}
                            </TableCell>
                            <TableCell>{svc.serviceName ?? svc.service_name ?? '—'}</TableCell>
                            <TableCell>
                              <Chip
                                label={(svc.requiresPreAuth ?? svc.requires_pre_auth) ? 'نعم' : 'لا'}
                                color={(svc.requiresPreAuth ?? svc.requires_pre_auth) ? 'warning' : 'default'}
                                size="small"
                              />
                            </TableCell>
                            <TableCell>
                              <Chip
                                label={(svc.active ?? true) ? 'نشط' : 'غير نشط'}
                                color={(svc.active ?? true) ? 'success' : 'default'}
                                size="small"
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </AccordionDetails>
              </Accordion>
            ))}
          </Box>
        )} {/* end Tab 1 */}
      </MainCard>
    </>
  );
};

export default ProviderView;
