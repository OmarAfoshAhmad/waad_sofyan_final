import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSnackbar } from 'notistack';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import {
  Box,
  Button,
  Grid,
  TextField,
  MenuItem,
  Typography,
  Tabs,
  Tab,
  Divider,
  Alert,
  InputAdornment,
  Chip,
  Stack,
  IconButton,
  RadioGroup,
  Badge,
  CircularProgress,
  FormControlLabel,
  Radio,
  Paper,
  Autocomplete,
  Avatar,
  Switch,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination
} from '@mui/material';
import {
  ArrowBack,
  Save,
  LocalHospital as ProviderIcon,
  Business,
  LocationOn,
  Phone,
  People,
  Person,
  Lock,
  Visibility,
  VisibilityOff,
  Link as LinkIcon,
  PersonAdd,
  Handshake,
  Info
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useCreateProvider } from 'hooks/useProviders';
import { usersService } from 'services/rbac/users.service';
import { refreshToken } from 'services/auth/tokenRefresh.service';
import { getEmployerSelectors } from 'services/api/employers.service';
import { providersService } from 'services/api/providers.service';
import GregorianDatePicker from 'components/common/GregorianDatePicker';
import {
  providerFormSchema,
  accountCreationSchema,
  providerDefaultValues,
  accountCreationDefaultValues,
  sanitizeProviderPayload,
  getPasswordStrength
} from 'schemas/providerSchema';

const PROVIDER_TYPES = [
  { value: 'HOSPITAL', label: 'مستشفى', icon: '🏥' },
  { value: 'CLINIC', label: 'عيادة', icon: '🏥' },
  { value: 'LAB', label: 'مختبر', icon: '🔬' },
  { value: 'PHARMACY', label: 'صيدلية', icon: '💊' },
  { value: 'RADIOLOGY', label: 'مركز أشعة', icon: '📷' }
];

const NETWORK_STATUS_OPTIONS = [
  { value: 'IN_NETWORK', label: 'داخل الشبكة', description: 'مقدم خدمة معتمد داخل الشبكة' },
  { value: 'OUT_OF_NETWORK', label: 'خارج الشبكة', description: 'مقدم خدمة خارج الشبكة' },
  { value: 'PREFERRED', label: 'مزود مفضل', description: 'مقدم خدمة مفضل بخصومات أعلى' }
];

const ProviderCreate = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { enqueueSnackbar } = useSnackbar();
  const { create, creating } = useCreateProvider();

  // ============================================================================
  // React Hook Form - Provider Form
  // ============================================================================
  const {
    control: providerControl,
    handleSubmit: handleProviderSubmit,
    formState: { errors: providerErrors },
    watch
  } = useForm({
    resolver: yupResolver(providerFormSchema),
    defaultValues: providerDefaultValues,
    mode: 'onBlur'
  });

  // ============================================================================
  // React Hook Form - Account Creation Form
  // ============================================================================
  const {
    control: accountControl,
    formState: { errors: accountErrors },
    watch: watchAccount,
    setValue: setAccountValue
  } = useForm({
    resolver: yupResolver(accountCreationSchema),
    defaultValues: accountCreationDefaultValues,
    mode: 'onBlur'
  });

  // Watch form values for auto-generation and validation
  const providerName = watch('name');
  const providerType = watch('providerType');
  const accountPassword = watchAccount('password');

  // ============================================================================
  // UI State (not form data)
  // ============================================================================
  const [activeTab, setActiveTab] = useState(() => {
    const tabParam = searchParams.get('tab');
    return tabParam ? parseInt(tabParam, 10) : 0;
  });
  const [showPassword, setShowPassword] = useState(false);
  const [accountMode, setAccountMode] = useState('CREATE');
  const [unassignedUsers, setUnassignedUsers] = useState([]);
  const [selectedUserToLink, setSelectedUserToLink] = useState(null);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [payers, setPayers] = useState([]);
  const [loadingPayers, setLoadingPayers] = useState(false);
  const [payersPage, setPayersPage] = useState(0);
  const [payersRowsPerPage, setPayersRowsPerPage] = useState(10);
  const [submitting, setSubmitting] = useState(false);
  const [savingStep, setSavingStep] = useState('');

  // ============================================================================
  // Computed Values
  // ============================================================================

  // Auto-generated provider code
  const autoCode = useMemo(() => {
    if (providerType && providerName) {
      const typePrefix = providerType.substring(0, 3).toUpperCase();
      const nameInitials = providerName
        .split(' ')
        .slice(0, 2)
        .map((w) => w[0])
        .join('');
      const timestamp = Date.now().toString().slice(-4);
      return `${typePrefix}-${nameInitials || 'XX'}-${timestamp}`;
    }
    return 'AUTO-GENERATED';
  }, [providerType, providerName]);

  // Password strength indicator
  const passwordStrength = useMemo(() => {
    return getPasswordStrength(accountPassword || '');
  }, [accountPassword]);

  // ✅ Count errors per tab for badges
  const getTabErrors = (tabIndex) => {
    let count = 0;
    if (tabIndex === 0) {
      if (providerErrors.name) count++;
      if (providerErrors.licenseNumber) count++;
      if (providerErrors.providerType) count++;
      if (providerErrors.taxNumber) count++;
    }
    return count;
  };

  // ============================================================================
  // Effects
  // ============================================================================

  // Load partners data on mount
  useEffect(() => {
    const loadPartnersData = async () => {
      setLoadingPayers(true);
      try {
        const employersRes = await getEmployerSelectors();
        const allEmployers = Array.isArray(employersRes) ? employersRes : employersRes?.data || [];
        const mapped = allEmployers.map((emp) => ({
          id: emp.id || emp.value,
          name: emp.label || emp.name,
          code: emp.code || 'EMP',
          enabled: false
        }));
        setPayers(mapped);
      } catch (error) {
        console.error('Error loading partners:', error);
        enqueueSnackbar('فشل تحميل بيانات الشركاء', { variant: 'error' });
      } finally {
        setLoadingPayers(false);
      }
    };
    loadPartnersData();
  }, [enqueueSnackbar]);

  // Load unassigned users when switching to LINK mode
  const loadUnassignedUsers = async () => {
    setLoadingUsers(true);
    try {
      const users = await usersService.getUnassignedProviders();
      setUnassignedUsers(users || []);
    } catch (error) {
      console.error('Error loading unassigned users:', error);
      enqueueSnackbar('فشل تحميل المستخدمين غير المرتبطين', { variant: 'error' });
    } finally {
      setLoadingUsers(false);
    }
  };

  useEffect(() => {
    // Account Manager isolation - tab 4 removed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  // Auto-populate account fullName and username from provider name
  useEffect(() => {
    if (providerName && accountMode === 'CREATE') {
      const currentFullName = watchAccount('fullName');
      const currentUsername = watchAccount('username');

      if (!currentFullName) {
        setAccountValue('fullName', providerName);
      }

      if (!currentUsername) {
        const generatedUsername = providerName.trim().toLowerCase().replace(/\s+/g, '_');
        setAccountValue('username', generatedUsername);
      }
    }
  }, [providerName, accountMode, setAccountValue, watchAccount]);

  // ==============================================================================================
  // Handlers
  // ============================================================================

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
    setSearchParams({ tab: newValue.toString() });
  };

  const handleNext = () => {
    setActiveTab((prev) => Math.min(prev + 1, 3));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleBack = () => {
    setActiveTab((prev) => Math.max(prev - 1, 0));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Navigate to tab with errors
  const navigateToErrorTab = (errors) => {
    // Check provider form errors
    const basicInfoErrors = ['name', 'licenseNumber', 'providerType', 'taxNumber', 'networkStatus'];
    if (basicInfoErrors.some((field) => errors[field])) {
      setActiveTab(0); // Basic Info
      enqueueSnackbar('يرجى تصحيح الأخطاء في البيانات الأساسية', { variant: 'error' });
      return;
    }

    const locationErrors = ['city', 'address', 'phone', 'email'];
    if (locationErrors.some((field) => errors[field])) {
      setActiveTab(1); // Location
      enqueueSnackbar('يرجى تصحيح الأخطاء في بيانات الموقع والتواصل', { variant: 'error' });
      return;
    }

  };

  const renderFooterActions = (currentTab) => (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: '2.0rem', pt: '1.0rem', borderTop: 1, borderColor: 'divider' }}>
      {currentTab > 0 ? (
        <Button onClick={handleBack} startIcon={<ArrowBack sx={{ transform: 'rotate(180deg)' }} />}>
          السابق
        </Button>
      ) : (
        <div />
      )}

      {currentTab < 2 ? (
        <Button variant="contained" onClick={handleNext} endIcon={<ArrowBack sx={{ transform: 'rotate(0deg)' }} />}>
          التالي
        </Button>
      ) : (
        <Button
          variant="contained"
          color="primary"
          onClick={handleProviderSubmit(onSubmit, navigateToErrorTab)}
          startIcon={<Save />}
          disabled={creating || submitting}
        >
          {creating || submitting ? 'جاري الحفظ...' : 'حفظ مقدم الخدمة'}
        </Button>
      )}
    </Box>
  );

  // ============================================================================
  // Form Submission
  // ============================================================================

  const onSubmit = async (providerData) => {
    setSubmitting(true);
    setSavingStep('التحقق من البيانات...');

    // ✅ Simplified Entry: Account creation isolated from main wizard

    // Sanitize payload (convert empty strings to null, ensure numeric types)
    const payload = sanitizeProviderPayload(providerData);

    try {
      setSavingStep('حفظ بيانات المزود...');
      const result = await create(payload);
      if (result.success) {
        const newProviderId = result.data?.id || result.data?.data?.id || result.data;

        if (newProviderId) {
          // Step 1: Save Partners
          setSavingStep('حفظ الشركاء...');
          const enabledPartnerIds = payers.filter((p) => p.enabled).map((p) => p.id);
          if (enabledPartnerIds.length > 0) {
            try {
              await providersService.updateAllowedEmployers(newProviderId, enabledPartnerIds);
            } catch (partnerError) {
              console.error('Failed to save partners:', partnerError);
              enqueueSnackbar('تم إنشاء المزود ولكن فشل حفظ الشركاء', { variant: 'warning' });
            }
          }

          // Account creation isolated - skipped in create wizard
        }

        setSavingStep('اكتمل!');
        enqueueSnackbar('تم إنشاء مقدم الخدمة بنجاح', { variant: 'success' });
        navigate('/providers');
      } else {
        enqueueSnackbar(result.error || 'فشل إنشاء مقدم الخدمة', { variant: 'error' });
      }
    } catch (error) {
      console.error('Provider creation error:', error);
      enqueueSnackbar('عذراً، فشلت عملية الحفظ بسبب خطأ غیر متوقع. يرجى إعادة المحاولة', { variant: 'error' });
    } finally {
      setSubmitting(false);
      setSavingStep('');
    }
  };

  const createNewAccount = async (providerId, accountData, providerName) => {
    try {
      const userPayload = {
        username: accountData.username,
        password: accountData.password,
        fullName: accountData.fullName || providerName,
        email: `${accountData.username}@provider.local`,
        providerId: providerId,
        userType: 'PROVIDER_STAFF',
        enabled: true
      };

      const userRes = await usersService.createUser(userPayload);
      const userId = userRes?.data?.data?.id || userRes?.data?.id || userRes?.id;

      if (userId) {
        console.log('Provider user created with PROVIDER_STAFF type, id:', userId);
      }
    } catch (error) {
      console.error('Account creation error:', error);
      enqueueSnackbar('تم إنشاء المزود لكن فشلت عملية إنشاء الحساب. يرجى الربط يدوياً.', { variant: 'warning' });
    }
  };

  const linkExistingAccount = async (providerId, userId) => {
    try {
      await usersService.updateUser(userId, { providerId });
      enqueueSnackbar('تم ربط المستخدم بنجاح', { variant: 'success' });
    } catch (error) {
      console.error('User linking error:', error);
      enqueueSnackbar('تم إنشاء المزود لكن فشل ربط المستخدم. يرجى الربط يدوياً.', { variant: 'warning' });
    }
  };

  // ============================================================================
  // Render Functions - Tab Content
  // ============================================================================

  const renderBasicInfo = () => (
    <Box sx={{ p: 1 }}>
      {/* ✅ Section Header */}
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.5rem', pb: '1.0rem', borderBottom: 1, borderColor: 'divider' }}>
        <Business color="primary" fontSize="large" />
        <Box>
          <Typography variant="h5" fontWeight={600}>
            البيانات الأساسية
          </Typography>
          <Typography variant="caption" color="text.secondary">
            اسم ونوع وترخيص مقدم الخدمة
          </Typography>
        </Box>
      </Stack>
      <Grid container spacing={3}>
        {/* Auto-generated Code */}
        <Grid size={12}>
          <Alert severity="info" sx={{ mb: '1.0rem' }}>
            <Typography variant="body2">
              <strong>الرمز التلقائي:</strong> سيتم إنشاء رمز تلقائي لمقدم الخدمة عند الحفظ
            </Typography>
          </Alert>
          <TextField
            fullWidth
            label="الرمز التلقائي"
            value={autoCode}
            disabled
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Chip label="AUTO" size="small" color="primary" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Provider Name */}
        <Grid size={12}>
          <Controller
            name="name"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField
                {...field}
                fullWidth
                required
                label="اسم مقدم الخدمة"
                placeholder="مثال: مستشفى الواحة"
                error={!!error}
                helperText={error?.message}
              />
            )}
          />
        </Grid>

        {/* Provider Type */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="providerType"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField {...field} fullWidth required select label="نوع مقدم الخدمة" error={!!error} helperText={error?.message}>
                {PROVIDER_TYPES.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <span>{option.icon}</span>
                      <span>{option.label}</span>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>

        {/* Network Status */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="networkStatus"
            control={providerControl}
            render={({ field }) => (
              <TextField {...field} fullWidth select label="حالة الشبكة">
                <MenuItem value="">
                  <em>غير محدد</em>
                </MenuItem>
                {NETWORK_STATUS_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>

        {/* License Number */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="licenseNumber"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField {...field} fullWidth required label="رقم الترخيص" error={!!error} helperText={error?.message} />
            )}
          />
        </Grid>

        {/* Tax Number */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="taxNumber"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth label="الرقم الضريبي (اختياري)" />}
          />
        </Grid>
      </Grid>
    </Box>
  );

  const renderLocationContact = () => (
    <Box sx={{ p: 1 }}>
      {/* ✅ Section Header */}
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.5rem', pb: '1.0rem', borderBottom: 1, borderColor: 'divider' }}>
        <LocationOn color="primary" fontSize="large" />
        <Box>
          <Typography variant="h5" fontWeight={600}>
            الموقع والتواصل
          </Typography>
          <Typography variant="caption" color="text.secondary">
            عنوان وهاتف وبريد مقدم الخدمة
          </Typography>
        </Box>
      </Stack>
      <Grid container spacing={3}>
        {/* City */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="city"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth label="المدينة" placeholder="مثال: طرابلس" />}
          />
        </Grid>

        {/* Address */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="address"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth label="العنوان" placeholder="مثال: شارع الجمهورية" />}
          />
        </Grid>

        {/* Phone */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="phone"
            control={providerControl}
            render={({ field }) => (
              <TextField
                {...field}
                fullWidth
                label="رقم الهاتف"
                placeholder="+218-xx-xxxxxxx"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Phone fontSize="small" />
                    </InputAdornment>
                  )
                }}
              />
            )}
          />
        </Grid>

        {/* Email */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="email"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField
                {...field}
                fullWidth
                label="البريد الإلكتروني"
                placeholder="info@example.ly"
                error={!!error}
                helperText={error?.message}
              />
            )}
          />
        </Grid>
      </Grid>
    </Box>
  );

  const renderPartners = () => (
    <Box sx={{ p: 1 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: '1.5rem' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Handshake color="primary" fontSize="large" />
          <Box>
            <Typography variant="h5" fontWeight={600}>
              الشركاء (شركات التأمين)
            </Typography>
            <Typography variant="caption" color="text.secondary">
              حدد الجهات المسموح لها بالتعامل مع هذا المزود
            </Typography>
          </Box>
        </Box>
        <Controller
          name="allowAllEmployers"
          control={providerControl}
          render={({ field: { value, onChange } }) => (
            <FormControlLabel
              control={<Switch checked={!!value} onChange={(e) => onChange(e.target.checked)} />}
              label="شبكة عامة (السماح لجميع الجهات)"
            />
          )}
        />
      </Box>

      {watch('allowAllEmployers') ? (
        <Alert severity="info">وضع الشبكة العامة مفعل. جميع الجهات مسموح بها.</Alert>
      ) : loadingPayers ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: '2.0rem' }}>
          <CircularProgress />
        </Box>
      ) : payers.length === 0 ? (
        <Paper variant="outlined" sx={{ p: '2.0rem', textAlign: 'center', bgcolor: 'grey.50' }}>
          <Info sx={{ fontSize: '3.0rem', color: 'text.secondary', mb: '1.0rem' }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            لا توجد جهات تأمين متاحة
          </Typography>
          <Typography variant="body2" color="text.secondary">
            يرجى إضافة جهات أولاً من صفحة الجهات المؤمنة
          </Typography>
        </Paper>
      ) : (
        <>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>جهة العمل</TableCell>
                  <TableCell align="center">الرمز</TableCell>
                  <TableCell align="right">الحالة</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {payers.slice(payersPage * payersRowsPerPage, payersPage * payersRowsPerPage + payersRowsPerPage).map((payer) => (
                  <TableRow key={payer.id} hover>
                    <TableCell>{payer.name}</TableCell>
                    <TableCell align="center">
                      <Typography variant="caption" color="text.secondary">{payer.code}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Switch
                        checked={payer.enabled}
                        onChange={() =>
                          setPayers((prev) => prev.map((p) => (p.id === payer.id ? { ...p, enabled: !p.enabled } : p)))
                        }
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            rowsPerPageOptions={[5, 10, 25]}
            component="div"
            count={payers.length}
            rowsPerPage={payersRowsPerPage}
            page={payersPage}
            onPageChange={(_, p) => setPayersPage(p)}
            onRowsPerPageChange={(e) => {
              setPayersRowsPerPage(parseInt(e.target.value));
              setPayersPage(0);
            }}
          />
        </>
      )}
    </Box>
  );

  const renderAccountManager = () => (
    <Box sx={{ p: 1 }}>
      {/* ✅ Section Header */}
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: '1.5rem', pb: '1.0rem', borderBottom: 1, borderColor: 'divider' }}>
        <People color="primary" fontSize="large" />
        <Box>
          <Typography variant="h5" fontWeight={600}>
            مدير الحساب
          </Typography>
          <Typography variant="caption" color="text.secondary">
            ربط مستخدم لإدارة بيانات مقدم الخدمة
          </Typography>
        </Box>
      </Stack>

      <Paper variant="outlined" sx={{ p: '1.5rem', mb: '1.5rem', bgcolor: 'primary.50' }}>
        <Typography variant="subtitle2" gutterBottom fontWeight={600}>
          اختر طريقة ربط المسؤول:
        </Typography>
        <RadioGroup row value={accountMode} onChange={(e) => setAccountMode(e.target.value)}>
          <FormControlLabel
            value="CREATE"
            control={<Radio />}
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <PersonAdd fontSize="small" />
                <Typography>إنشاء حساب جديد</Typography>
              </Box>
            }
            sx={{ mr: '2.0rem' }}
          />
          <FormControlLabel
            value="LINK"
            control={<Radio />}
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <LinkIcon fontSize="small" />
                <Typography>ربط مستخدم موجود (حر)</Typography>
              </Box>
            }
            sx={{ mr: '2.0rem' }}
          />
          <FormControlLabel value="SKIP" control={<Radio />} label="تخطي (بدون مسؤول حالياً)" />
        </RadioGroup>
      </Paper>

      {/* CREATE Mode: New Account Form */}
      {accountMode === 'CREATE' && (
        <Grid container spacing={3} maxWidth="md">
          {/* Full Name */}
          <Grid size={12}>
            <Controller
              name="fullName"
              control={accountControl}
              render={({ field }) => <TextField {...field} fullWidth label="الاسم الكامل" />}
            />
          </Grid>

          {/* Username */}
          <Grid size={12}>
            <Controller
              name="username"
              control={accountControl}
              render={({ field, fieldState: { error } }) => (
                <TextField
                  {...field}
                  fullWidth
                  required
                  label="اسم المستخدم"
                  error={!!error}
                  helperText={error?.message}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Person />
                      </InputAdornment>
                    )
                  }}
                />
              )}
            />
          </Grid>

          {/* Password */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Controller
              name="password"
              control={accountControl}
              render={({ field, fieldState: { error } }) => (
                <TextField
                  {...field}
                  fullWidth
                  required
                  label="كلمة المرور"
                  type={showPassword ? 'text' : 'password'}
                  error={!!error}
                  helperText={error?.message}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock />
                      </InputAdornment>
                    ),
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                          {showPassword ? <VisibilityOff /> : <Visibility />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }}
                />
              )}
            />
          </Grid>

          {/* Confirm Password */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Controller
              name="confirmPassword"
              control={accountControl}
              render={({ field, fieldState: { error } }) => (
                <TextField
                  {...field}
                  fullWidth
                  required
                  label="تأكيد كلمة المرور"
                  type={showPassword ? 'text' : 'password'}
                  error={!!error}
                  helperText={error?.message}
                />
              )}
            />
          </Grid>

          {/* Password Strength Indicator */}
          {accountPassword && (
            <Grid size={12}>
              <Box sx={{ mb: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  قوة كلمة المرور: <strong>{passwordStrength.label}</strong>
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={passwordStrength.strength}
                color={passwordStrength.strength < 40 ? 'error' : passwordStrength.strength < 80 ? 'warning' : 'success'}
                sx={{ height: '0.375rem', borderRadius: '0.1875rem' }}
              />
            </Grid>
          )}
        </Grid>
      )}

      {/* LINK Mode: Select Existing User */}
      {accountMode === 'LINK' && (
        <Box sx={{ maxWidth: 'md' }}>
          <Typography variant="body2" color="text.secondary" paragraph>
            ابحث عن مستخدم لديه صلاحية (PROVIDER) وغير مرتبط بأي مستشفى حالياً.
          </Typography>

          {/* ✅ Empty State when loading */}
          {loadingUsers ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: '2.0rem' }}>
              <CircularProgress />
            </Box>
          ) : unassignedUsers.length === 0 ? (
            <Paper variant="outlined" sx={{ p: '2.0rem', textAlign: 'center', bgcolor: 'grey.50' }}>
              <Info sx={{ fontSize: '3.0rem', color: 'text.secondary', mb: '1.0rem' }} />
              <Typography variant="h6" color="text.secondary" gutterBottom>
                لا توجد مستخدمين متاحين
              </Typography>
              <Typography variant="body2" color="text.secondary">
                جميع المستخدمين مرتبطين بمقدمي خدمة آخرين. يمكنك إنشاء مستخدم جديد بدلاً من ذلك.
              </Typography>
            </Paper>
          ) : (
            <Autocomplete
              options={unassignedUsers}
              getOptionLabel={(option) => option.fullName || option.username || ''}
              value={selectedUserToLink}
              onChange={(e, newValue) => setSelectedUserToLink(newValue)}
              loading={loadingUsers}
              disabled={loadingUsers}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="اختر مستخدم"
                  placeholder="ابحث..."
                  helperText={unassignedUsers.length === 0 ? 'لا يوجد مستخدمين متاحين' : `${unassignedUsers.length} مستخدم متاح`}
                />
              )}
              renderOption={(props, option) => (
                <li {...props} key={option.id}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: '0.75rem', width: '100%' }}>
                    <Avatar sx={{ width: '2.0rem', height: '2.0rem', bgcolor: 'primary.light' }}>{option.fullName?.charAt(0)}</Avatar>
                    <Box>
                      <Typography variant="body1">{option.fullName}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {option.email}
                      </Typography>
                    </Box>
                    <Chip label="متاح" size="small" color="success" variant="outlined" sx={{ ml: 'auto' }} />
                  </Box>
                </li>
              )}
            />
          )}
        </Box>
      )}

      {/* SKIP Mode: Information */}
      {accountMode === 'SKIP' && (
        <Alert severity="warning">سيتم إنشاء مقدم الخدمة بدون مستخدم مسؤول. يمكنك ربط مستخدم لاحقاً من صفحة التعديل.</Alert>
      )}
    </Box>
  );

  // ============================================================================
  // Main Render
  // ============================================================================

  return (
    <>
      <ModernPageHeader
        title="إضافة مقدم خدمة صحية جديد"
        subtitle="إنشاء سجل جديد وتعيين المسؤول"
        icon={ProviderIcon}
        breadcrumbs={[{ label: 'مقدمو الخدمات', path: '/providers' }, { label: 'إضافة جديد' }]}
        actions={
          <Stack direction="row" spacing={2}>
            <Button startIcon={<ArrowBack />} onClick={() => navigate('/providers')} disabled={creating} color="inherit">
              عودة
            </Button>

            <Button
              variant="contained"
              startIcon={<Save />}
              onClick={handleProviderSubmit(onSubmit, navigateToErrorTab)}
              disabled={creating}
            >
              {creating ? 'جاري الحفظ...' : 'حفظ مقدم الخدمة'}
            </Button>

          </Stack>
        }
      />

      <MainCard>
        {/* ✅ Saving Progress Indicator */}
        {submitting && savingStep && (
          <Alert severity="info" icon={<CircularProgress size={20} />} sx={{ mb: '1.5rem' }}>
            <Typography variant="body2">{savingStep}</Typography>
          </Alert>
        )}

        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: '1.5rem' }}>
          <Tabs value={activeTab} onChange={handleTabChange}>
            {/* ✅ Tab with error badge */}
            <Tab
              icon={
                <Badge badgeContent={getTabErrors(0)} color="error">
                  <Business />
                </Badge>
              }
              label="البيانات الأساسية"
              iconPosition="start"
            />
            <Tab
              icon={
                <Badge badgeContent={getTabErrors(1)} color="error">
                  <LocationOn />
                </Badge>
              }
              label="الموقع والتواصل"
              iconPosition="start"
            />
            <Tab icon={<Handshake />} label="الشركاء" iconPosition="start" />
          </Tabs>
        </Box>

        <Box sx={{ mb: '2.0rem', minHeight: '25.0rem' }}>
          <Box role="tabpanel" hidden={activeTab !== 0}>
            {activeTab === 0 && renderBasicInfo()}
          </Box>
          <Box role="tabpanel" hidden={activeTab !== 1}>
            {activeTab === 1 && renderLocationContact()}
          </Box>
          <Box role="tabpanel" hidden={activeTab !== 2}>
            {activeTab === 2 && renderPartners()}
          </Box>
        </Box>

        {/* ✅ Wizard Navigation Actions */}
        {renderFooterActions(activeTab)}
      </MainCard>
    </>
  );
};

export default ProviderCreate;



