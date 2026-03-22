import { useState, useEffect } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import { getEmployerSelectors } from 'services/api/employers.service';
import { providersService } from 'services/api/providers.service';
import { usersService } from 'services/rbac/users.service';
import { refreshToken } from 'services/auth/tokenRefresh.service';
import {
  Box,
  Button,
  Grid,
  TextField,
  MenuItem,
  Typography,
  Tabs,
  Tab,
  Paper,
  Divider,
  Alert,
  InputAdornment,
  Chip,
  Switch,
  FormControlLabel,
  Avatar,
  Stack,
  IconButton,
  CircularProgress,
  Autocomplete,
  Card,
  CardContent,
  CardActions,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  RadioGroup,
  Radio,
  LinearProgress,
  Badge
} from '@mui/material';
import {
  ArrowBack,
  Save,
  LocalHospital as ProviderIcon,
  Business,
  LocationOn,
  Handshake,
  People,
  Description,
  Person,
  Lock,
  Visibility,
  VisibilityOff,
  Warning,
  Add as AddIcon,
  Delete as DeleteIcon,
  Info
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useProviderDetails, useUpdateProvider } from 'hooks/useProviders';
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
  { value: 'HOSPITAL', label: 'مستشفى' },
  { value: 'CLINIC', label: 'عيادة' },
  { value: 'LAB', label: 'مختبر' },
  { value: 'PHARMACY', label: 'صيدلية' },
  { value: 'RADIOLOGY', label: 'مركز أشعة' }
];

const NETWORK_STATUS_OPTIONS = [
  { value: 'IN_NETWORK', label: 'داخل الشبكة' },
  { value: 'OUT_OF_NETWORK', label: 'خارج الشبكة' },
  { value: 'PREFERRED', label: 'مزود مفضل' }
];

const DOC_TYPE_LABELS = {
  LICENSE: 'رخصة مزاولة مهنة',
  COMMERCIAL_REGISTER: 'سجل تجاري',
  TAX_CERTIFICATE: 'شهادة ضريبية',
  CONTRACT_COPY: 'نسخة العقد',
  OTHER: 'أخرى'
};

const getTodayIsoDate = () => new Date().toISOString().slice(0, 10);

const ProviderEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();
  const { provider, loading } = useProviderDetails(id);
  const { update, updating } = useUpdateProvider();

  // ============================================================================
  // React Hook Form - Provider Form
  // ============================================================================
  const {
    control: providerControl,
    handleSubmit: handleProviderSubmit,
    formState: { errors: providerErrors },
    reset: resetProviderForm,
    watch
  } = useForm({
    resolver: yupResolver(providerFormSchema),
    mode: 'onBlur',
    defaultValues: providerDefaultValues
  });

  // ============================================================================
  // React Hook Form - New User Creation (when linking from Edit)
  // ============================================================================
  const {
    control: newUserControl,
    handleSubmit: handleNewUserSubmit,
    formState: { errors: newUserErrors },
    reset: resetNewUserForm,
    watch: watchNewUser
  } = useForm({
    resolver: yupResolver(accountCreationSchema),
    mode: 'onBlur',
    defaultValues: accountCreationDefaultValues
  });

  const newUserPassword = watchNewUser('password');

  // ============================================================================
  // UI State
  // ============================================================================
  const [activeTab, setActiveTab] = useState(() => {
    const tabParam = searchParams.get('tab');
    return tabParam ? parseInt(tabParam, 10) : 0;
  });
  const [initialized, setInitialized] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [savingStep, setSavingStep] = useState('');

  // Partners State
  const [payers, setPayers] = useState([]);
  const [loadingPayers, setLoadingPayers] = useState(false);
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    payerId: null,
    action: 'enable',
    payerName: ''
  });
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);

  // User Management State
  const [activeUser, setActiveUser] = useState(null);
  const [loadingUser, setLoadingUser] = useState(false);
  const [linkMode, setLinkMode] = useState('LINK');
  const [unassignedUsers, setUnassignedUsers] = useState([]);
  const [selectedUserToLink, setSelectedUserToLink] = useState(null);
  const [loadingUnassigned, setLoadingUnassigned] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [unlinkDialog, setUnlinkDialog] = useState({
    open: false,
    confirmationText: ''
  });

  // Documents State
  const [documents, setDocuments] = useState([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [docDialog, setDocDialog] = useState({
    open: false,
    type: 'LICENSE',
    expiryDate: getTodayIsoDate(),
    notes: '',
    fileName: '',
    file: null
  });
  const [deleteDocDialog, setDeleteDocDialog] = useState({
    open: false,
    docId: null
  });
  const [previewDialog, setPreviewDialog] = useState({
    open: false,
    url: '',
    title: ''
  });
  const [docPage, setDocPage] = useState(0);
  const [docRowsPerPage, setDocRowsPerPage] = useState(5);

  // ============================================================================
  // Effects - Load Provider Data and Initialize Form
  // ============================================================================
  useEffect(() => {
    // ✅ FIX FLOW-003: Only initialize form once when provider loads
    if (provider && !initialized) {
      resetProviderForm({
        name: provider.name || '',
        licenseNumber: provider.licenseNumber || '',
        taxNumber: provider.taxNumber || '',
        providerType: provider.providerType || '',
        networkStatus: provider.networkStatus || '',
        city: provider.city || '',
        address: provider.address || '',
        phone: provider.phone || '',
        email: provider.email || '',
        contractStartDate: provider.contractStartDate || null,
        contractEndDate: provider.contractEndDate || null,
        defaultDiscountRate: provider.defaultDiscountRate || 0,
        active: provider.active !== undefined ? provider.active : true,
        allowAllEmployers: provider.allowAllEmployers || false
      });
      setInitialized(true);
    }
  }, [provider, resetProviderForm, initialized]);

  // Load Partners
  useEffect(() => {
    if (!id || activeTab !== 2) return;

    const loadPartnersData = async () => {
      setLoadingPayers(true);
      try {
        const employersRes = await getEmployerSelectors();
        const allEmployers = Array.isArray(employersRes) ? employersRes : employersRes?.data || [];

        const allowedIds = await providersService.getAllowedEmployerIds(id);
        const rawList = Array.isArray(allowedIds) ? allowedIds : [];
        // Response may be plain IDs or AllowedEmployerDto objects — normalise to IDs
        const allowedSet = new Set(rawList.map((a) => (typeof a === 'object' && a !== null ? a.id : a)));

        const mapped = allEmployers.map((emp) => ({
          id: emp.id || emp.value,
          name: emp.label || emp.name,
          code: emp.code || 'EMP',
          enabled: allowedSet.has(emp.id || emp.value)
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
  }, [activeTab, id, enqueueSnackbar]);

  // Load Documents
  useEffect(() => {
    if (activeTab === 3 && id) {
      fetchDocuments();
    }
  }, [activeTab, id]);

  // ============================================================================
  // Handlers
  // ============================================================================

  // ✅ Count errors per tab for badges
  const getTabErrors = (tabIndex) => {
    let count = 0;
    if (tabIndex === 0) {
      if (providerErrors.name) count++;
      if (providerErrors.licenseNumber) count++;
      if (providerErrors.providerType) count++;
    } else if (tabIndex === 1) {
      if (providerErrors.city) count++;
      if (providerErrors.address) count++;
      if (providerErrors.phone) count++;
      if (providerErrors.email) count++;
    } else if (tabIndex === 2) {
      if (providerErrors.contractStartDate) count++;
      if (providerErrors.contractEndDate) count++;
      if (providerErrors.defaultDiscountRate) count++;
    }
    return count;
  };

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
    setSearchParams({ tab: newValue.toString() });
  };

  const fetchLinkedUser = async () => {
    if (!id) return;
    setLoadingUser(true);
    try {
      const users = await usersService.getUsersByProvider(id);
      if (Array.isArray(users) && users.length > 0) {
        setActiveUser(users[0]);
      } else {
        setActiveUser(null);
        fetchUnassignedUsers();
      }
    } catch (error) {
      console.error('Error fetching linked user:', error);
    } finally {
      setLoadingUser(false);
    }
  };

  const fetchUnassignedUsers = async () => {
    setLoadingUnassigned(true);
    try {
      const users = await usersService.getUnassignedProviders();
      setUnassignedUsers(users || []);
    } catch (error) {
      console.error('Error fetching unassigned users:', error);
    } finally {
      setLoadingUnassigned(false);
    }
  };

  const fetchDocuments = async () => {
    try {
      setLoadingDocs(true);
      const docs = await providersService.getDocuments(id);
      setDocuments(docs || []);
    } catch (error) {
      console.error('Error fetching documents:', error);
    } finally {
      setLoadingDocs(false);
    }
  };

  // Form Submission
  const onSubmit = async (formData) => {
    setSubmitting(true);
    setSavingStep('حفظ التغييرات...');

    const payload = sanitizeProviderPayload(formData);

    const result = await update(id, payload);

    setSavingStep('');
    setSubmitting(false);

    if (result.success) {
      enqueueSnackbar('تم التحديث بنجاح', { variant: 'success' });
      navigate('/providers');
    } else {
      enqueueSnackbar(result.error || 'فشل التحديث', { variant: 'error' });
    }
  };

  // Partners Handlers
  const handlePayerToggleRequest = (payer) => {
    setConfirmDialog({
      open: true,
      payerId: payer.id,
      action: payer.enabled ? 'disable' : 'enable',
      payerName: payer.name
    });
  };

  const handleConfirmToggle = async () => {
    const { payerId } = confirmDialog;

    const newPayersState = payers.map((p) => (p.id === payerId ? { ...p, enabled: !p.enabled } : p));

    const enabledIds = newPayersState.filter((p) => p.enabled).map((p) => p.id);

    try {
      await providersService.updateAllowedEmployers(id, enabledIds);
      setPayers(newPayersState);
      // Immediately update the list page cache with correct data (no flash on navigate back)
      const updatedCacheData = newPayersState
        .filter((p) => p.enabled)
        .map((p) => ({ id: p.id, name: p.name, nameEn: p.name, isGlobal: false, isActive: true }));
      queryClient.setQueryData(['provider-allowed-employers', Number(id)], updatedCacheData);
      enqueueSnackbar('تم تحديث قائمة الشركاء بنجاح', { variant: 'success' });
    } catch (error) {
      console.error('Failed to update allowed employers:', error);
      enqueueSnackbar('فشل تحديث الشركاء', { variant: 'error' });
    } finally {
      setConfirmDialog({ ...confirmDialog, open: false });
    }
  };

  // User Handlers
  const handleOpenUnlinkDialog = () => {
    setUnlinkDialog({ open: true, confirmationText: '' });
  };

  const handleConfirmUnlink = async () => {
    if (!activeUser) return;
    if (unlinkDialog.confirmationText !== activeUser.username) {
      enqueueSnackbar('النص المدخل غير صحيح. يجب كتابة اسم المستخدم للتأكيد.', { variant: 'error' });
      return;
    }

    try {
      setLoadingUser(true);
      await usersService.updateUser(activeUser.id, { providerId: null });
      enqueueSnackbar('تم فك الارتباط بنجاح', { variant: 'success' });
      setActiveUser(null);
      setUnlinkDialog({ open: false, confirmationText: '' });
      fetchUnassignedUsers();
    } catch (error) {
      console.error('Error unlinking user:', error);
      enqueueSnackbar('فشل فك الارتباط: ' + (error.response?.data?.message || error.message), { variant: 'error' });
    } finally {
      setLoadingUser(false);
    }
  };

  const handleLinkExistingUser = async () => {
    if (!selectedUserToLink) {
      enqueueSnackbar('يرجى اختيار مستخدم', { variant: 'warning' });
      return;
    }

    try {
      setLoadingUser(true);
      await usersService.updateUser(selectedUserToLink.id, { providerId: id });

      // ✨ AUTO-REFRESH TOKEN: If linking current user to provider
      const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
      if (currentUser.id === selectedUserToLink.id) {
        try {
          await refreshToken();
          enqueueSnackbar('تم ربط المستخدم وتحديث بياناتك بنجاح', {
            variant: 'success'
          });
        } catch (refreshErr) {
          console.warn('⚠️ Failed to auto-refresh token:', refreshErr);
          enqueueSnackbar('تم ربط المستخدم بنجاح. يرجى تسجيل الخروج والدخول لتحديث بياناتك.', { variant: 'warning' });
        }
      } else {
        enqueueSnackbar('تم ربط المستخدم بنجاح', { variant: 'success' });
      }

      setSelectedUserToLink(null);
      fetchLinkedUser();
    } catch (error) {
      console.error('Error linking user:', error);
      enqueueSnackbar('فشل ربط المستخدم', { variant: 'error' });
    } finally {
      setLoadingUser(false);
    }
  };

  const handleCreateAndLinkUser = async (newUserData) => {
    try {
      setLoadingUser(true);
      const userPayload = {
        username: newUserData.username,
        password: newUserData.password,
        fullName: newUserData.fullName || newUserData.username,
        email: `${newUserData.username}@provider.local`,
        providerId: id,
        userType: 'PROVIDER_STAFF',
        enabled: true
      };

      const userRes = await usersService.createUser(userPayload);
      const userId = userRes?.data?.data?.id || userRes?.data?.id || userRes?.id;

      if (userId) {
        console.log('Provider user created with PROVIDER_STAFF type, id:', userId);
      }

      enqueueSnackbar('تم إنشاء وربط المستخدم بنجاح', { variant: 'success' });
      resetNewUserForm();
      fetchLinkedUser();
    } catch (error) {
      console.error('Error creating user:', error);
      enqueueSnackbar('فشل إنشاء المستخدم', { variant: 'error' });
    } finally {
      setLoadingUser(false);
    }
  };

  // Document Handlers
  const handleAddDocument = async () => {
    if (!docDialog.fileName || !docDialog.type) {
      enqueueSnackbar('بيانات المستند ناقصة', { variant: 'warning' });
      return;
    }

    try {
      const formDataDocs = new FormData();
      const dto = {
        providerId: id,
        type: docDialog.type,
        fileName: docDialog.fileName,
        expiryDate: docDialog.expiryDate || null,
        notes: docDialog.notes,
        documentNumber: `DOC-${Date.now()}`
      };
      formDataDocs.append('data', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
      if (docDialog.file) {
        formDataDocs.append('file', docDialog.file);
      }

      await providersService.addDocument(id, formDataDocs);
      enqueueSnackbar('تم الإضافة بنجاح', { variant: 'success' });
      setDocDialog({
        open: false,
        type: 'LICENSE',
        expiryDate: getTodayIsoDate(),
        notes: '',
        fileName: '',
        file: null
      });
      fetchDocuments();
    } catch (error) {
      console.error('Error adding document:', error);
      enqueueSnackbar('فشل إضافة المستند', { variant: 'error' });
    }
  };

  const handlePreview = (doc) => {
    setPreviewDialog({
      open: true,
      url: doc.fileUrl || doc.filePath,
      title: doc.fileName
    });
  };

  const handleConfirmDeleteDoc = async () => {
    try {
      await providersService.deleteDocument(id, deleteDocDialog.docId);
      enqueueSnackbar('تم الحذف بنجاح', { variant: 'success' });
      fetchDocuments();
    } catch (error) {
      console.error('Error deleting document:', error);
      enqueueSnackbar('فشل الحذف', { variant: 'error' });
    } finally {
      setDeleteDocDialog({ open: false, docId: null });
    }
  };

  // ============================================================================
  // Render Functions
  // ============================================================================
  const renderBasicInfo = () => (
    <Box sx={{ p: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
        <Business color="primary" />
        <Typography variant="h5">البيانات الأساسية</Typography>
      </Box>
      <Grid container spacing={3}>
        <Grid size={12}>
          <Controller
            name="name"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField {...field} fullWidth required label="اسم مقدم الخدمة" error={!!error} helperText={error?.message} />
            )}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="licenseNumber"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth required label="رقم الترخيص" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="taxNumber"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth label="الرقم الضريبي" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="providerType"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField {...field} fullWidth required select label="نوع مقدم الخدمة" error={!!error} helperText={error?.message}>
                {PROVIDER_TYPES.map((op) => (
                  <MenuItem key={op.value} value={op.value}>
                    {op.label}
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="networkStatus"
            control={providerControl}
            render={({ field }) => (
              <TextField {...field} fullWidth select label="حالة الشبكة">
                {NETWORK_STATUS_OPTIONS.map((op) => (
                  <MenuItem key={op.value} value={op.value}>
                    {op.label}
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="active"
            control={providerControl}
            render={({ field: { value, ...field } }) => (
              <TextField {...field} fullWidth select label="الحالة التشغيلية" value={value === true || value === 'true' ? 'true' : 'false'}>
                <MenuItem value="true">نشط</MenuItem>
                <MenuItem value="false">غير نشط</MenuItem>
              </TextField>
            )}
          />
        </Grid>
      </Grid>
    </Box>
  );

  const renderLocationContact = () => (
    <Box sx={{ p: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
        <LocationOn color="primary" />
        <Typography variant="h5">الموقع والتواصل</Typography>
      </Box>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller name="city" control={providerControl} render={({ field }) => <TextField {...field} fullWidth label="المدينة" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller name="address" control={providerControl} render={({ field }) => <TextField {...field} fullWidth label="العنوان" />} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="phone"
            control={providerControl}
            render={({ field }) => <TextField {...field} fullWidth label="رقم الهاتف" />}
          />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Controller
            name="email"
            control={providerControl}
            render={({ field, fieldState: { error } }) => (
              <TextField {...field} fullWidth label="البريد الإلكتروني" error={!!error} helperText={error?.message} />
            )}
          />
        </Grid>
      </Grid>
    </Box>
  );

  const renderPartners = () => (
    <Box sx={{ p: 1 }}>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          mb: '1.5rem'
        }}
      >
        <Box sx={{ display: 'flex' }}>
          <Handshake color="primary" sx={{ mr: 1 }} />
          <Typography variant="h5">الشركاء</Typography>
        </Box>
        <Controller
          name="allowAllEmployers"
          control={providerControl}
          render={({ field: { value, onChange } }) => (
            <FormControlLabel
              control={<Switch checked={value} onChange={(e) => onChange(e.target.checked)} />}
              label="شبكة عامة (السماح لجميع الجهات)"
            />
          )}
        />
      </Box>

      {watch('allowAllEmployers') ? (
        <Alert severity="success">وضع الشبكة العامة مفعل. جميع الجهات مسموح بها.</Alert>
      ) : loadingPayers ? (
        <CircularProgress />
      ) : (
        <>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>شريك التأمين</TableCell>
                  <TableCell align="right">الحالة</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {payers.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((payer) => (
                  <TableRow key={payer.id}>
                    <TableCell>{payer.name}</TableCell>
                    <TableCell align="right">
                      <Switch checked={payer.enabled} onChange={() => handlePayerToggleRequest(payer)} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            rowsPerPageOptions={[5, 10]}
            component="div"
            count={payers.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={(_, p) => setPage(p)}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(parseInt(e.target.value));
              setPage(0);
            }}
          />
        </>
      )}
    </Box>
  );

  const renderResponsibleUser = () => (
    <Box sx={{ p: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: '1.5rem' }}>
        <People color="primary" />
        <Typography variant="h5">المستخدم المسؤول</Typography>
      </Box>
      {loadingUser ? (
        <CircularProgress />
      ) : activeUser ? (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" spacing={2} alignItems="center">
              <Avatar sx={{ width: '3.5rem', height: '3.5rem', bgcolor: 'primary.main' }}>
                {activeUser.fullName?.charAt(0) || activeUser.username?.charAt(0)}
              </Avatar>
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6">{activeUser.fullName || activeUser.username}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {activeUser.username}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {activeUser.email}
                </Typography>
              </Box>
              <Chip label="مرتبط" color="success" size="small" />
            </Stack>
          </CardContent>
          <CardActions>
            <Button color="error" onClick={handleOpenUnlinkDialog}>
              فك الارتباط
            </Button>
          </CardActions>
        </Card>
      ) : (
        <Paper variant="outlined" sx={{ p: '1.5rem' }}>
          <Alert severity="warning" sx={{ mb: '1.5rem' }}>
            لا يوجد مستخدم مرتبط حالياً
          </Alert>
          <Typography variant="subtitle1" gutterBottom>
            اختر طريقة الربط:
          </Typography>
          <RadioGroup value={linkMode} onChange={(e) => setLinkMode(e.target.value)} sx={{ mb: '1.5rem' }}>
            <FormControlLabel value="LINK" control={<Radio />} label="ربط مستخدم موجود" />
            <FormControlLabel value="CREATE" control={<Radio />} label="إنشاء مستخدم جديد" />
          </RadioGroup>

          {linkMode === 'LINK' ? (
            <>
              <Autocomplete
                options={unassignedUsers}
                getOptionLabel={(option) => option.fullName || option.username || ''}
                value={selectedUserToLink}
                onChange={(e, newValue) => setSelectedUserToLink(newValue)}
                loading={loadingUnassigned}
                disabled={loadingUnassigned}
                renderInput={(params) => <TextField {...params} label="اختر مستخدم" placeholder="ابحث..." />}
                sx={{ mb: '1.0rem' }}
              />
              <Button fullWidth variant="contained" onClick={handleLinkExistingUser} disabled={!selectedUserToLink}>
                ربط المستخدم
              </Button>
            </>
          ) : (
            <Grid container spacing={2}>
              <Grid size={12}>
                <Controller
                  name="username"
                  control={newUserControl}
                  render={({ field, fieldState: { error } }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="اسم المستخدم"
                      required
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
              <Grid size={12}>
                <Controller
                  name="fullName"
                  control={newUserControl}
                  render={({ field }) => <TextField {...field} fullWidth label="الاسم الكامل" />}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="password"
                  control={newUserControl}
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
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller
                  name="confirmPassword"
                  control={newUserControl}
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
              {newUserPassword && (
                <Grid size={12}>
                  <Box sx={{ mb: 1 }}>
                    <Typography variant="caption" color="text.secondary">
                      قوة كلمة المرور: <strong>{getPasswordStrength(newUserPassword).label}</strong>
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={getPasswordStrength(newUserPassword).strength}
                    color={
                      getPasswordStrength(newUserPassword).strength < 40
                        ? 'error'
                        : getPasswordStrength(newUserPassword).strength < 80
                          ? 'warning'
                          : 'success'
                    }
                    sx={{ height: '0.375rem', borderRadius: '0.1875rem' }}
                  />
                </Grid>
              )}
              <Grid size={12}>
                <Button fullWidth variant="contained" onClick={handleNewUserSubmit(handleCreateAndLinkUser)}>
                  إنشاء وربط
                </Button>
              </Grid>
            </Grid>
          )}
        </Paper>
      )}
    </Box>
  );

  const renderDocuments = () => (
    <Box sx={{ p: 1 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: '1.5rem' }}>
        <Box sx={{ display: 'flex' }}>
          <Description color="primary" sx={{ mr: 1 }} />
          <Typography variant="h5">المستندات</Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setDocDialog({ ...docDialog, open: true, expiryDate: docDialog.expiryDate || getTodayIsoDate() })}
        >
          إضافة
        </Button>
      </Box>
      {loadingDocs ? (
        <CircularProgress />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>اسم الملف</TableCell>
                <TableCell>النوع</TableCell>
                <TableCell>تاريخ الانتهاء</TableCell>
                <TableCell>إجراءات</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {documents.slice(docPage * docRowsPerPage, docPage * docRowsPerPage + docRowsPerPage).map((doc) => (
                <TableRow key={doc.id}>
                  <TableCell>{doc.fileName}</TableCell>
                  <TableCell>
                    <Chip label={DOC_TYPE_LABELS[doc.type] || doc.type} size="small" />
                  </TableCell>
                  <TableCell>{doc.expiryDate || '-'}</TableCell>
                  <TableCell>
                    <IconButton size="small" onClick={() => handlePreview(doc)}>
                      <Visibility fontSize="small" />
                    </IconButton>
                    <IconButton size="small" color="error" onClick={() => setDeleteDocDialog({ open: true, docId: doc.id })}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {documents.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    لا يوجد مستندات
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <TablePagination
        rowsPerPageOptions={[5, 10]}
        component="div"
        count={documents.length}
        rowsPerPage={docRowsPerPage}
        page={docPage}
        onPageChange={(_, p) => setDocPage(p)}
        onRowsPerPageChange={(e) => setDocRowsPerPage(parseInt(e.target.value))}
      />
    </Box>
  );

  // ============================================================================
  // Main Render
  // ============================================================================
  if (loading) return <CircularProgress />;

  return (
    <>
      <ModernPageHeader
        title="تعديل مقدم الخدمة"
        subtitle={provider?.name}
        icon={ProviderIcon}
        breadcrumbs={[{ label: 'مقدمو الخدمات', path: '/providers' }, { label: 'تعديل' }]}
        actions={
          <Stack direction="row" spacing={2}>
            <Button startIcon={<ArrowBack />} onClick={() => navigate('/providers')}>
              عودة
            </Button>
            
              <Button variant="contained" startIcon={<Save />} onClick={handleProviderSubmit(onSubmit)} disabled={submitting || updating}>
                {submitting || updating ? 'جاري الحفظ...' : 'حفظ'}
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

        <Tabs value={activeTab} onChange={handleTabChange} sx={{ borderBottom: 1, borderColor: 'divider', mb: '1.5rem' }}>
          {/* ✅ Tabs with error badges */}
          <Tab
            icon={
              <Badge badgeContent={getTabErrors(0)} color="error">
                <Business />
              </Badge>
            }
            label="أساسي"
            iconPosition="start"
          />
          <Tab
            icon={
              <Badge badgeContent={getTabErrors(1)} color="error">
                <LocationOn />
              </Badge>
            }
            label="الموقع و التواصل"
            iconPosition="start"
          />
          <Tab icon={<Handshake />} label="شركاء" iconPosition="start" />
          <Tab icon={<Description />} label="مستندات" iconPosition="start" />
        </Tabs>
        <Box sx={{ minHeight: '25.0rem' }}>
          {activeTab === 0 && renderBasicInfo()}
          {activeTab === 1 && renderLocationContact()}
          {activeTab === 2 && renderPartners()}
          {activeTab === 3 && renderDocuments()}
        </Box>
      </MainCard>

      {/* Dialogs */}
      <Dialog open={unlinkDialog.open} onClose={() => setUnlinkDialog({ open: false, confirmationText: '' })}>
        <DialogTitle sx={{ color: 'error.main', display: 'flex', alignItems: 'center', gap: 1 }}>
          <Warning />
          تأكيد فك ارتباط المسؤول
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.0rem' }}>
            هل أنت متأكد من رغبتك في فك ارتباط المستخدم <strong>{activeUser?.fullName}</strong> ({activeUser?.username})؟
          </DialogContentText>
          <Alert severity="warning" sx={{ mb: '1.0rem' }}>
            للتأكيد، يرجى كتابة اسم المستخدم: <strong>{activeUser?.username}</strong>
          </Alert>
          <TextField
            fullWidth
            autoFocus
            label="اكتب اسم المستخدم للتأكيد"
            value={unlinkDialog.confirmationText}
            onChange={(e) => setUnlinkDialog({ ...unlinkDialog, confirmationText: e.target.value })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUnlinkDialog({ open: false, confirmationText: '' })}>إلغاء</Button>
          <Button color="error" onClick={handleConfirmUnlink}>
            تأكيد الفك
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={confirmDialog.open} onClose={() => setConfirmDialog({ ...confirmDialog, open: false })}>
        <DialogTitle>تأكيد تغيير الصلاحية</DialogTitle>
        <DialogContent>
          <DialogContentText>هل أنت متأكد من تغيير الصلاحية؟</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialog({ ...confirmDialog, open: false })}>إلغاء</Button>
          <Button onClick={handleConfirmToggle} variant="contained">
            تأكيد
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteDocDialog.open} onClose={() => setDeleteDocDialog({ open: false, docId: null })}>
        <DialogTitle>حذف المستند</DialogTitle>
        <DialogContent>
          <DialogContentText>هل أنت متأكد؟</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDocDialog({ open: false, docId: null })}>إلغاء</Button>
          <Button onClick={handleConfirmDeleteDoc} color="error" variant="contained">
            حذف
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={docDialog.open} onClose={() => setDocDialog({ ...docDialog, open: false })}>
        <DialogTitle>إضافة مستند</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1, minWidth: '25.0rem' }}>
            <TextField select label="النوع" value={docDialog.type} onChange={(e) => setDocDialog({ ...docDialog, type: e.target.value })}>
              {Object.entries(DOC_TYPE_LABELS).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
            <Button variant="outlined" component="label">
              {docDialog.fileName || 'اختر ملف'}
              <input
                type="file"
                hidden
                onChange={(e) =>
                  e.target.files[0] &&
                  setDocDialog({
                    ...docDialog,
                    fileName: e.target.files[0].name,
                    file: e.target.files[0]
                  })
                }
              />
            </Button>
            <TextField
              type="date"
              label="تاريخ الانتهاء"
              InputLabelProps={{ shrink: true }}
              value={docDialog.expiryDate}
              onChange={(e) => setDocDialog({ ...docDialog, expiryDate: e.target.value })}
            />
            <TextField
              label="ملاحظات"
              multiline
              rows={3}
              value={docDialog.notes}
              onChange={(e) => setDocDialog({ ...docDialog, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDocDialog({ ...docDialog, open: false })}>إلغاء</Button>
          <Button onClick={handleAddDocument} variant="contained">
            حفظ
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={previewDialog.open} onClose={() => setPreviewDialog({ ...previewDialog, open: false })} maxWidth="lg" fullWidth>
        <DialogTitle>{previewDialog.title}</DialogTitle>
        <DialogContent sx={{ height: '80vh' }}>
          {previewDialog.url && (
            <iframe src={previewDialog.url} style={{ width: '100%', height: '100%', border: 'none' }} title="preview" />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewDialog({ ...previewDialog, open: false })}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ProviderEdit;
