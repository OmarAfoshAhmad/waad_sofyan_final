import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Stack,
  Paper,
  Alert,
  Chip,
  IconButton,
  CircularProgress,
  InputAdornment,
  Autocomplete,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  useTheme
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { Html5Qrcode } from 'html5-qrcode';

// Icons
import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner';
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import RefreshIcon from '@mui/icons-material/Refresh';
import CloseIcon from '@mui/icons-material/Close';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom';
import AssignmentIcon from '@mui/icons-material/Assignment';
import HistoryIcon from '@mui/icons-material/History';
import TipsAndUpdatesIcon from '@mui/icons-material/TipsAndUpdates';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';

// Components
import MainCard from '../../components/MainCard';
import { ModernPageHeader, MemberAvatar } from '../../components/tba';

// Services
import providerApi from '../../services/providerService';

// Visit Types
const VISIT_TYPE_OPTIONS = [
  { value: 'EMERGENCY', label: 'عمليات' },
  { value: 'OUTPATIENT', label: 'عيادات خارجية' },
  { value: 'INPATIENT', label: 'إيواء' },
  { value: 'ROUTINE', label: 'تحاليل طبية' },
  { value: 'FOLLOW_UP', label: 'اسنان وقائي' },
  { value: 'PREVENTIVE', label: 'اسنان تجميلي' },
  { value: 'SPECIALIZED', label: 'اشعة' },
  { value: 'HOME_CARE', label: 'علاج طبيعي' }
];

const translateRelationship = (rel) => {
  if (!rel) return 'الموظف';
  const mapping = {
    SELF: 'الموظف',
    SON: 'ابن',
    DAUGHTER: 'ابنة',
    HUSBAND: 'زوج',
    WIFE: 'زوجة',
    FATHER: 'أب',
    MOTHER: 'أم',
    BROTHER: 'أخ',
    SISTER: 'أخت',
    PRINCIPAL: 'الموظف'
  };
  return mapping[rel.toUpperCase()] || rel;
};

const getRelationshipColor = (rel) => {
  if (!rel) return 'primary';
  const mapping = {
    SELF: 'primary',
    SON: 'info',
    DAUGHTER: 'info',
    HUSBAND: 'secondary',
    WIFE: 'secondary',
    FATHER: 'warning',
    MOTHER: 'warning',
    BROTHER: 'success',
    SISTER: 'success',
    PRINCIPAL: 'primary'
  };
  return mapping[rel.toUpperCase()] || 'default';
};

export default function ProviderEligibilityCheck() {
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  // ========================================
  // STATE
  // ========================================

  const [searchValue, setSearchValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const [selectedMember, setSelectedMember] = useState(null);
  const [selectedVisitType, setSelectedVisitType] = useState('OUTPATIENT');
  const [registeringVisit, setRegisteringVisit] = useState(false);
  const [visitError, setVisitError] = useState(null);
  const [checkHistory, setCheckHistory] = useState([]);

  // Autocomplete
  const [searchOptions, setSearchOptions] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);

  // Scanner
  const [scannerOpen, setScannerOpen] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [cameraError, setCameraError] = useState(null);
  const html5QrCodeRef = useRef(null);
  const scannerInputRef = useRef(null);
  const autoCheckTimerRef = useRef(null);
  const lastAutoSubmittedRef = useRef('');

  // ========================================
  // HELPERS
  // ========================================

  const formatCurrency = (amount) => {
    if (!amount && amount !== 0) return '-';
    // Use Western Arabic numerals with English locale, but add custom Libyan Dinar suffix
    return (
      Number(amount).toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }) + ' د.ل'
    );
  };

  const getUsageColor = (percentage) => {
    if (percentage >= 90) return 'error';
    if (percentage >= 70) return 'warning';
    return 'success';
  };

  const todayDateKey = new Date().toLocaleDateString('en-CA');

  const todayChecks = useMemo(() => {
    return checkHistory.filter((item) => item.dateKey === todayDateKey);
  }, [checkHistory, todayDateKey]);

  const todayAcceptedCount = useMemo(() => todayChecks.filter((item) => item.eligible).length, [todayChecks]);
  const todayRejectedCount = useMemo(() => todayChecks.filter((item) => !item.eligible).length, [todayChecks]);
  const recentSuccessfulChecks = useMemo(() => checkHistory.filter((item) => item.eligible).slice(0, 5), [checkHistory]);

  // ========================================
  // ELIGIBILITY CHECK API
  // ========================================

  const checkEligibility = useCallback(async (barcodeOrCardNumber, memberId = null) => {
    // Validate input
    const trimmedValue = barcodeOrCardNumber?.trim();

    if (!trimmedValue) {
      setError('يرجى إدخال اسم المستفيد أو رقم البطاقة أو الباركود');
      return;
    }

    // Only reject single "0", allow other numbers (including leading zeros like "000001")
    if (trimmedValue === '0') {
      setError('يرجى إدخال بيانات صحيحة (الاسم، رقم البطاقة، أو الباركود)');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    setSelectedMember(null);
    setSelectedVisitType('OUTPATIENT');

    try {
      // Send as barcode (API accepts card number, barcode, or member ID in this field)
      const payload = { barcode: trimmedValue };
      if (memberId) payload.memberId = memberId;
      const response = await providerApi.checkEligibility(payload);

      // API returns the DTO directly (ProviderEligibilityResponse)
      if (response && (response.eligible !== undefined || response.statusCode)) {
        setResult(response);
        const firstMember = response.familyMembers?.[0] || response.principalMember;
        setCheckHistory((prev) =>
          [
            {
              id: `${Date.now()}-${trimmedValue}`,
              input: trimmedValue,
              eligible: !!response.eligible,
              memberName: firstMember?.fullName || 'غير محدد',
              checkedAt: new Date().toISOString(),
              dateKey: new Date().toLocaleDateString('en-CA')
            },
            ...prev
          ].slice(0, 50)
        );
        // Auto-select first eligible member
        if (response.familyMembers && response.familyMembers.length > 0) {
          const firstEligible = response.familyMembers.find((m) => m.eligible);
          if (firstEligible) {
            setSelectedMember(firstEligible);
          }
        }
      } else {
        setError(response?.message || 'فشل في التحقق من الأهلية - استجابة غير صالحة');
      }
    } catch (err) {
      console.error('Eligibility check failed:', err);
      setError(err.message || 'فشل في الاتصال بالخادم');
    } finally {
      setLoading(false);
    }
  }, []);

  // ========================================
  // EVENT HANDLERS
  // ========================================

  const handleInputChange = (e) => {
    setSearchValue(e.target.value);
  };

  const handleSubmit = () => {
    checkEligibility(searchValue);
  };

  const handleReset = () => {
    setSearchValue('');
    setResult(null);
    setError(null);
    setSelectedMember(null);
    setSelectedVisitType('OUTPATIENT');
    lastAutoSubmittedRef.current = '';
  };

  useEffect(() => {
    const trimmed = searchValue?.trim();

    if (!trimmed || trimmed.length < 3) {
      setSearchOptions([]);
      return;
    }

    // Identify if the input looks like a complete barcode (WAD-...) or card number (long numeric)
    // If it looks complete, don't trigger autocomplete dropdown, but do trigger eligibility if it's long enough
    const isCompleteBarcode = trimmed.startsWith('WAD-') && trimmed.length > 10;
    const isScannerInput = trimmed.length > 12 && !isNaN(trimmed); // likely scanned card number

    if (autoCheckTimerRef.current) {
      clearTimeout(autoCheckTimerRef.current);
    }

    autoCheckTimerRef.current = setTimeout(async () => {
      // If it's a direct barcode or scanner input, auto-submit instead of showing suggestions
      if (isCompleteBarcode || isScannerInput) {
        if (!loading && trimmed !== lastAutoSubmittedRef.current) {
          lastAutoSubmittedRef.current = trimmed;
          checkEligibility(trimmed);
          setSearchOptions([]);
        }
      } else {
        // Fetch autocomplete suggestions
        setSearchLoading(true);
        try {
          const results = await providerApi.searchMembers(trimmed);
          setSearchOptions(results || []);
        } catch (err) {
          console.error('Failed to fetch autocomplete options', err);
        } finally {
          setSearchLoading(false);
        }
      }
    }, 450);

    return () => {
      if (autoCheckTimerRef.current) {
        clearTimeout(autoCheckTimerRef.current);
      }
    };
  }, [searchValue, loading, checkEligibility]);

  // ========================================
  // QR SCANNER HANDLERS
  // ========================================

  const startQrScanner = async () => {
    setScannerOpen(true);
    setCameraError(null);
    setScanning(true);

    try {
      const html5QrCode = new Html5Qrcode('qr-reader-provider');
      html5QrCodeRef.current = html5QrCode;

      await html5QrCode.start(
        { facingMode: 'environment' },
        {
          fps: 10,
          qrbox: { width: '15.625rem', height: '15.625rem' }
        },
        (decodedText) => {
          stopQrScanner();
          setScannerOpen(false);
          setSearchValue(decodedText);
          checkEligibility(decodedText);
        },
        (errorMessage) => {
          // Ignore scan errors (continuous scanning)
        }
      );
    } catch (err) {
      console.error('Failed to start QR scanner:', err);
      setCameraError('فشل في تشغيل الكاميرا. تأكد من منح الإذن للوصول إلى الكاميرا.');
      setScanning(false);
    }
  };

  const stopQrScanner = async () => {
    if (html5QrCodeRef.current && scanning) {
      try {
        await html5QrCodeRef.current.stop();
        html5QrCodeRef.current = null;
      } catch (err) {
        console.error('Failed to stop scanner:', err);
      } finally {
        setScanning(false);
      }
    }
  };

  const handleOpenScannerDialog = () => {
    startQrScanner();
  };

  const handleCloseScannerDialog = () => {
    stopQrScanner();
    setScannerOpen(false);
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopQrScanner();
    };
  }, []);

  // Hardware Scanner Support (focus on hidden input)
  useEffect(() => {
    let buffer = '';
    let lastTime = Date.now();

    const handleKeyDown = (e) => {
      const target = e.target;
      if (target.id === 'scanner-input-provider' || target.tagName === 'INPUT') {
        const currentTime = Date.now();
        // If time since last key is > 50ms, clear buffer (human typing)
        if (currentTime - lastTime > 50) {
          buffer = '';
        }
        lastTime = currentTime;

        if (e.key === 'Enter' && buffer.trim().length >= 5) {
          e.preventDefault();
          const scannedValue = buffer.trim();
          setSearchValue(scannedValue);
          lastAutoSubmittedRef.current = scannedValue;
          checkEligibility(scannedValue);
          buffer = '';
          return;
        }

        if (e.key.length === 1) {
          buffer += e.key;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [checkEligibility]);

  // ========================================
  // VISIT REGISTRATION HANDLER
  // ========================================

  const handleRegisterVisit = async () => {
    if (!selectedMember || !selectedVisitType) return;
    
    if (selectedMember.hasOpenVisit) {
      // Navigate to the visit log passing the member's civil ID or card number for an exact match
      const exactIdentifier = selectedMember.civilId || selectedMember.cardNumber || selectedMember.fullName;
      navigate('/provider/visits', { state: { memberName: exactIdentifier } });
      return;
    }

    setRegisteringVisit(true);
    try {
      const visitResponse = await providerApi.registerVisit({
        memberId: selectedMember.memberId,
        eligibilityCheckId: result.eligibilityCheckId,
        visitType: selectedVisitType
      });

      if (visitResponse.success) {
        navigate('/provider/visits', {
          state: {
            successMessage: `تم تسجيل الزيارة بنجاح للمنتفع ${selectedMember.fullName}`,
            newVisitId: visitResponse.visitId
          }
        });
      } else {
        setVisitError(visitResponse.message || 'فشل في تسجيل الزيارة');
      }
    } catch (err) {
      console.error('Failed to register visit:', err);
      setVisitError(err.message || 'فشل في تسجيل الزيارة');
    } finally {
      setRegisteringVisit(false);
    }
  };

  // ========================================
  // RENDER: TABLE HEADER COLORS
  // ========================================

  const tableHeaderBg = isDark ? 'rgba(66, 66, 66, 0.5)' : 'grey.100';
  const tableHeaderColor = isDark ? 'rgba(255, 255, 255, 0.87)' : 'text.primary';

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box
      sx={{
        bgcolor: '#F5F7FA',
        height: { xs: 'auto', lg: 'calc(100vh - 80px)' },
        display: 'flex',
        flexDirection: 'column',
        p: { xs: 1, md: 2 },
        borderRadius: '0.25rem'
      }}
    >
      <ModernPageHeader
        title="فحص الأهلية"
        subtitle="التحقق من أهلية المؤمن عليه وتسجيل الزيارة"
        icon={LocalHospitalIcon}
        actions={
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            {['المنطقة الحرة جليانة', 'الشركة الليبية للموانئ'].map((company, index) => (
              <Chip
                key={index}
                label={company}
                size="small"
                sx={{
                  fontWeight: 'bold',
                  minWidth: '140px',
                  justifyContent: 'center',
                  bgcolor: 'primary.main',
                  color: 'common.white',
                  borderRadius: '4px',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.15)'
                }}
              />
            ))}
          </Stack>
        }
      />

      {/* MAIN CONTENT AREA */}
      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', lg: 'row' }, gap: '1.5rem', mt: 1, flex: 1, overflow: 'hidden' }}>
        {/* Side Panel: Scanner & Search Area */}
        <Paper
          elevation={2}
          sx={{
            width: { xs: '100%', lg: '22rem' },
            flexShrink: 0,
            bgcolor: 'background.paper',
            borderRadius: '0.25rem',
            overflowY: 'auto',
            height: '100%',
            p: 2
          }}
        >
          <Stack spacing={1.5}>
            <Box sx={{ textAlign: 'center', mb: 1 }}>
              <Typography
                variant="body2"
                fontWeight="bold"
                sx={{ color: isDark ? 'grey.300' : 'grey.700', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
              >
                أدخل الاسم، رقم البطاقة، أو امسح الباركود
              </Typography>
            </Box>

            <Stack direction="column" spacing={2} alignItems="stretch">
              <Box sx={{ flex: 1 }}>
                <Autocomplete
                  freeSolo
                  options={searchOptions}
                  getOptionLabel={(option) => {
                    if (typeof option === 'string') return option;
                    return `${option.fullName} - ${option.cardNumber || option.barcode}`;
                  }}
                  filterOptions={(x) => x}
                  loading={searchLoading}
                  inputValue={searchValue}
                  onInputChange={(e, newInputValue) => {
                    setSearchValue(newInputValue);
                    if (error) setError(null);
                  }}
                  onChange={(e, newValue) => {
                    if (!newValue) return;
                    if (typeof newValue === 'object') {
                      setSearchValue(newValue.barcode || newValue.cardNumber);
                      checkEligibility(newValue.barcode || newValue.cardNumber);
                    } else {
                      checkEligibility(newValue);
                    }
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      inputRef={scannerInputRef}
                      fullWidth
                      autoFocus
                      placeholder="بحث..."
                      disabled={loading}
                      InputProps={{
                        ...params.InputProps,
                        startAdornment: (
                          <InputAdornment position="start">
                            <QrCodeScannerIcon color="primary" />
                          </InputAdornment>
                        ),
                        endAdornment: (
                          <InputAdornment position="end">
                            {loading ? <CircularProgress size={20} /> : <CreditCardIcon color="action" />}
                          </InputAdornment>
                        )
                      }}
                      sx={{
                        '& .MuiOutlinedInput-root': {
                          borderRadius: '0.375rem',
                          bgcolor: 'common.white',
                          minHeight: '3.5rem',
                          '& fieldset': { borderColor: 'divider' },
                          '&:hover fieldset': { borderColor: 'success.main' },
                          '&.Mui-focused fieldset': { borderColor: 'primary.main' }
                        }
                      }}
                    />
                  )}
                  renderOption={(props, option) => (
                    <li {...props} key={option.id}>
                      <Grid container alignItems="center">
                        <Grid item sx={{ display: 'flex', width: 44 }}>
                          <PersonIcon sx={{ color: 'text.secondary' }} />
                        </Grid>
                        <Grid item sx={{ width: 'calc(100% - 44px)', wordWrap: 'break-word' }}>
                          <Typography variant="body1" color="text.primary">
                            {option.fullName}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            رقم البطاقة: {option.cardNumber || '-'}
                          </Typography>
                        </Grid>
                      </Grid>
                    </li>
                  )}
                />
              </Box>

              <Button
                variant="outlined"
                color="primary"
                startIcon={<QrCodeScannerIcon />}
                onClick={handleOpenScannerDialog}
                disabled={loading}
                sx={{ borderRadius: '0.375rem', py: 1 }}
                fullWidth
              >
                مسح الكاميرا
              </Button>

              <Button
                variant="contained"
                color="success"
                onClick={handleSubmit}
                disabled={loading || !searchValue.trim()}
                startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <CheckCircleIcon />}
                sx={{ borderRadius: '0.375rem', fontWeight: 700, py: 1 }}
                fullWidth
              >
                فحص
              </Button>
            </Stack>

            <Box sx={{ height: 0, overflow: 'hidden', opacity: 0 }}>
              <TextField id="scanner-input-provider" ref={scannerInputRef} fullWidth size="small" />
            </Box>

            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            {/* Beneficiary Info in Side Panel */}
            {result && (
              <Box sx={{ pt: 3, borderTop: '1px solid', borderColor: 'divider' }}>
                {/* Avatar */}
                {selectedMember && (
                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', mb: 3 }}>
                    <MemberAvatar
                      member={{ id: selectedMember.memberId, fullName: selectedMember.fullName, photoUrl: selectedMember.profileImage }}
                      size={95}
                      refreshTrigger={`${selectedMember.memberId || ''}-${selectedMember.profileImage || ''}`}
                      sx={{
                        border: 3,
                        borderColor: selectedMember.eligible ? 'success.main' : 'error.main',
                        bgcolor: 'grey.300',
                        fontSize: '1.75rem',
                        fontWeight: 600
                      }}
                    />
                    <Typography variant="subtitle1" sx={{ mt: 0.5, textAlign: 'center', fontWeight: 'bold' }}>
                      {selectedMember.fullName.split(' ')[0]} {selectedMember.fullName.split(' ').slice(1, 3).join(' ') || ''}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
                      {selectedMember.cardNumber || '-'}
                    </Typography>
                    <Chip
                      label={selectedMember.eligible ? 'مؤهل' : 'غير مؤهل'}
                      color={selectedMember.eligible ? 'success' : 'error'}
                      size="small"
                      sx={{ mt: 0.25 }}
                    />
                  </Box>
                )}

                {/* Balance Cards */}
                <Grid container spacing={1}>
                  <Grid item xs={3}>
                    <Paper
                      elevation={0}
                      sx={{
                        p: 1,
                        textAlign: 'center',
                        bgcolor: 'primary.lighter',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                      }}
                    >
                      <Typography sx={{ fontSize: '0.65rem', color: 'text.secondary', whiteSpace: 'nowrap' }}>الحد السنوي</Typography>
                      <Typography sx={{ fontSize: '0.75rem', color: 'primary.dark', fontWeight: 'bold' }}>
                        {formatCurrency(result.principalAnnualLimit)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid item xs={3}>
                    <Paper
                      elevation={0}
                      sx={{
                        p: 1,
                        textAlign: 'center',
                        bgcolor: 'warning.lighter',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                      }}
                    >
                      <Typography sx={{ fontSize: '0.65rem', color: 'text.secondary', whiteSpace: 'nowrap' }}>المستخدم</Typography>
                      <Typography sx={{ fontSize: '0.75rem', color: 'warning.dark', fontWeight: 'bold' }}>
                        {formatCurrency(result.principalUsedAmount)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid item xs={3}>
                    <Paper
                      elevation={0}
                      sx={{
                        p: 1,
                        textAlign: 'center',
                        bgcolor: 'success.lighter',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                      }}
                    >
                      <Typography sx={{ fontSize: '0.65rem', color: 'text.secondary', whiteSpace: 'nowrap' }}>المتبقي</Typography>
                      <Typography sx={{ fontSize: '0.75rem', color: 'success.dark', fontWeight: 'bold' }}>
                        {formatCurrency(result.principalRemainingLimit)}
                      </Typography>
                    </Paper>
                  </Grid>
                  <Grid item xs={3}>
                    <Paper
                      elevation={0}
                      sx={{
                        p: 1,
                        textAlign: 'center',
                        bgcolor: 'grey.100',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center'
                      }}
                    >
                      <Typography sx={{ fontSize: '0.65rem', color: 'text.secondary', whiteSpace: 'nowrap' }}>نسبة الاستخدام</Typography>
                      <Typography sx={{ fontSize: '0.75rem', fontWeight: 'bold' }}>
                        {(result.principalUsagePercentage || 0).toFixed(0)}%
                      </Typography>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
            )}
          </Stack>
        </Paper>

        {/* Results & History Area */}
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <Stack spacing={2} sx={{ flex: 1, overflow: 'hidden' }}>
            {!result && (
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <HistoryIcon color="primary" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          تاريخ فحوصات اليوم
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {todayChecks.length} عملية فحص
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TaskAltIcon color="success" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          آخر منتفع تم فحصه
                        </Typography>
                        <Typography variant="body2" color="text.secondary" noWrap>
                          {checkHistory[0]?.memberName || 'لا يوجد بعد'}
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TipsAndUpdatesIcon color="warning" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          تعليمات سريعة
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          مرّر الباركود أو أدخل رقم البطاقة
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: '1.0rem',
                      borderRadius: '0.25rem',
                      bgcolor: 'success.lighter',
                      border: '1px solid',
                      borderColor: 'success.light'
                    }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="success.dark" fontWeight={700}>
                        حالات مقبولة اليوم
                      </Typography>
                      <Typography variant="h4" color="success.dark" fontWeight={800}>
                        {todayAcceptedCount}
                      </Typography>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Paper
                    variant="outlined"
                    sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: 'error.lighter', border: '1px solid', borderColor: 'error.light' }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="error.dark" fontWeight={700}>
                        حالات مرفوضة اليوم
                      </Typography>
                      <Typography variant="h4" color="error.dark" fontWeight={800}>
                        {todayRejectedCount}
                      </Typography>
                    </Stack>
                  </Paper>
                </Grid>
              </Grid>
            )}

            {/* Results Section */}
            {result ? (
              <MainCard
                sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}
                title={
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                      <Typography variant="h5" fontWeight="bold">
                        نتيجة الفحص
                      </Typography>

                      <Box
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 1.5,
                          px: 2,
                          py: 0.75,
                          borderRadius: '0.5rem',
                          bgcolor: result.eligible ? 'success.lighter' : 'error.lighter',
                          border: '1px solid',
                          borderColor: result.eligible ? 'success.main' : 'error.main'
                        }}
                      >
                        {result.eligible ? <CheckCircleIcon sx={{ color: 'success.main' }} /> : <CancelIcon sx={{ color: 'error.main' }} />}
                        <Box>
                          <Typography variant="subtitle2" color={result.eligible ? 'success.dark' : 'error.dark'} fontWeight="bold">
                            {result.message}{' '}
                            {result.familyMembers && `(عدد الأفراد: ${result.totalFamilyMembers || result.familyMembers.length})`}
                          </Typography>
                          {result.eligible && (result.policyNumber || result.planName) && (
                            <Typography
                              variant="caption"
                              color={result.eligible ? 'success.dark' : 'error.dark'}
                              sx={{ display: 'block', mt: 0.5 }}
                            >
                              {result.policyNumber && `رقم البوليصة: ${result.policyNumber}`}
                              {result.planName && ` | الخطة: ${result.planName}`}
                            </Typography>
                          )}
                        </Box>
                      </Box>
                    </Box>

                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>

                      <IconButton onClick={handleReset} size="small" title="إعادة ضبط" sx={{ bgcolor: 'grey.100' }}>
                        <RefreshIcon />
                      </IconButton>
                    </Box>
                  </Box>
                }
                contentSX={{ p: '1.0rem', flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
              >
                <Stack spacing={2} sx={{ flex: 1, overflow: 'hidden' }}>
                  {/* Warnings */}
                  {result.warnings && result.warnings.length > 0 && (
                    <Box sx={{ mt: '1.0rem' }}>
                      {result.warnings.map((warning, index) => (
                        <Alert key={index} severity="warning" sx={{ mb: 1 }}>
                          {warning}
                        </Alert>
                      ))}
                    </Box>
                  )}

                  {/* Family Members Table */}
                  {result.familyMembers && result.familyMembers.length > 0 && (
                    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                      <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflowY: 'auto' }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow sx={{ bgcolor: tableHeaderBg }}>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                الاسم
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                رقم البطاقة
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                الصلة
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                الحالة
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                المتبقي
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                النسبة
                              </TableCell>
                              <TableCell align="center" sx={{ color: tableHeaderColor, fontWeight: 600 }}>
                                اختيار
                              </TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {result.familyMembers.map((member) => (
                              <TableRow
                                key={member.memberId}
                                selected={selectedMember?.memberId === member.memberId}
                                hover
                                sx={{
                                  cursor: member.eligible ? 'pointer' : 'default',
                                  bgcolor: member.isPrincipal ? (isDark ? 'rgba(13, 71, 161, 0.15)' : 'primary.lighter') : 'inherit',
                                  opacity: member.eligible ? 1 : 0.6
                                }}
                                onClick={() => member.eligible && setSelectedMember(member)}
                              >
                                <TableCell>
                                  <Stack direction="row" alignItems="center" spacing={1}>
                                    <PersonIcon fontSize="small" color={member.isPrincipal ? 'primary' : 'action'} />
                                    <Box>
                                      <Typography variant="body2" fontWeight={member.isPrincipal ? 'bold' : 'normal'}>
                                        {member.fullName}
                                      </Typography>
                                      {member.isPrincipal && (
                                        <Chip label="موظف" size="small" color="primary" sx={{ height: '1.125rem', fontSize: '0.75rem' }} />
                                      )}
                                    </Box>
                                  </Stack>
                                </TableCell>
                                <TableCell align="center">{member.cardNumber || '-'}</TableCell>
                                <TableCell align="center">
                                  <Chip
                                    label={translateRelationship(member.relationship || 'SELF')}
                                    color={getRelationshipColor(member.relationship || 'SELF')}
                                    size="small"
                                    variant="outlined"
                                    sx={{
                                      minWidth: '70px',
                                      fontWeight: 'bold',
                                      bgcolor: (theme) =>
                                        theme.palette[getRelationshipColor(member.relationship || 'SELF')]?.lighter || 'transparent'
                                    }}
                                  />
                                </TableCell>
                                <TableCell align="center">
                                  <Chip
                                    label={member.eligible ? 'مؤهل' : 'غير مؤهل'}
                                    color={member.eligible ? 'success' : 'error'}
                                    size="small"
                                  />
                                </TableCell>
                                <TableCell align="center">{formatCurrency(member.remainingLimit)}</TableCell>
                                <TableCell align="center">
                                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    <LinearProgress
                                      variant="determinate"
                                      value={member.usagePercentage || 0}
                                      color={getUsageColor(member.usagePercentage || 0)}
                                      sx={{ height: '0.375rem', borderRadius: 1, flex: 1, minWidth: '3.125rem' }}
                                    />
                                    <Typography variant="caption">{(member.usagePercentage || 0).toFixed(0)}%</Typography>
                                  </Box>
                                </TableCell>
                                <TableCell align="center">
                                  <Button
                                    variant={selectedMember?.memberId === member.memberId ? 'contained' : 'outlined'}
                                    size="small"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setSelectedMember(member);
                                    }}
                                    disabled={!member.eligible}
                                    color={selectedMember?.memberId === member.memberId ? 'primary' : 'inherit'}
                                  >
                                    {selectedMember?.memberId === member.memberId ? 'محدد ✓' : 'اختيار'}
                                  </Button>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    </Box>
                  )}

                  {/* Selected Member Action - Register Visit */}
                  {selectedMember && (
                    <Paper elevation={0} sx={{ p: '1.5rem', bgcolor: 'info.lighter', border: 1, borderColor: 'info.main' }}>
                      <Stack spacing={2}>
                        {/* Mobile Member Summary (visible on small screens) */}
                        <Box sx={{ display: { xs: 'block', lg: 'none' } }}>
                          <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: '1.0rem' }}>
                            <MemberAvatar
                              member={{
                                id: selectedMember.memberId,
                                fullName: selectedMember.fullName,
                                photoUrl: selectedMember.profileImage
                              }}
                              size={60}
                              refreshTrigger={`${selectedMember.memberId || ''}-${selectedMember.profileImage || ''}`}
                              sx={{
                                border: 2,
                                borderColor: selectedMember.eligible ? 'success.main' : 'error.main'
                              }}
                            />
                            <Box>
                              <Typography variant="subtitle1" fontWeight={600}>
                                {selectedMember.fullName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                المتبقي: {formatCurrency(selectedMember.remainingLimit)}
                              </Typography>
                            </Box>
                          </Stack>
                          <Divider sx={{ mb: '1.0rem' }} />
                        </Box>

                        {/* Visit Type Selection (Hide if has open visit) */}
                        {!selectedMember.hasOpenVisit && (
                          <FormControl fullWidth size="medium" required error={!selectedVisitType}>
                            <InputLabel id="visit-type-label">نوع الزيارة *</InputLabel>
                            <Select
                              labelId="visit-type-label"
                              value={selectedVisitType}
                              onChange={(e) => setSelectedVisitType(e.target.value)}
                              label="نوع الزيارة *"
                            >
                              {VISIT_TYPE_OPTIONS.map((opt) => (
                                <MenuItem key={opt.value} value={opt.value}>
                                  {opt.label}
                                </MenuItem>
                              ))}
                            </Select>
                            {!selectedVisitType && (
                              <Typography variant="caption" color="error" sx={{ mt: 0.5 }}>
                                يجب اختيار نوع الزيارة قبل التسجيل
                              </Typography>
                            )}
                          </FormControl>
                        )}

                        {visitError && (
                          <Alert severity="error" onClose={() => setVisitError(null)} sx={{ mb: 1 }}>
                            {visitError}
                          </Alert>
                        )}

                        <Button
                          variant="contained"
                          color={selectedMember.hasOpenVisit ? 'info' : 'primary'}
                          size="large"
                          fullWidth
                          startIcon={
                            selectedMember.hasOpenVisit ? (
                              <OpenInNewIcon />
                            ) : registeringVisit ? (
                              <CircularProgress size={20} color="inherit" />
                            ) : (
                              <AssignmentIcon />
                            )
                          }
                          disabled={(!selectedMember.hasOpenVisit && registeringVisit) || (!selectedMember.hasOpenVisit && !selectedVisitType)}
                          onClick={handleRegisterVisit}
                        >
                          {selectedMember.hasOpenVisit
                            ? 'فتح الزيارة الحالية'
                            : registeringVisit
                            ? 'جاري التسجيل...'
                            : 'تسجيل زيارة'}
                        </Button>
                      </Stack>
                    </Paper>
                  )}
                </Stack>
              </MainCard>
            ) : (
              <Paper
                sx={{
                  p: '2.0rem',
                  textAlign: 'center',
                  bgcolor: 'common.white',
                  border: '1px dashed',
                  borderColor: 'divider',
                  minHeight: '8.75rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <Box>
                  <LocalHospitalIcon sx={{ fontSize: '4.0rem', color: 'text.disabled', mb: '0.75rem' }} />
                  <Typography variant="h6" color="text.secondary" gutterBottom>
                    في انتظار الفحص
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    أدخل رقم البطاقة أو استخدم مسح الكاميرا للبدء
                  </Typography>
                </Box>
              </Paper>
            )}
          </Stack>
        </Box>
      </Box>

      <Paper
        variant="outlined"
        sx={{
          mt: '1.0rem',
          p: '0.75rem',
          borderRadius: '0.25rem',
          bgcolor: 'common.white',
          display: 'flex',
          alignItems: 'center',
          gap: '0.625rem',
          overflowX: 'auto'
        }}
      >
        <Typography variant="body2" fontWeight={700} sx={{ whiteSpace: 'nowrap' }}>
          آخر 5 عمليات فحص ناجحة:
        </Typography>
        <Stack direction="row" spacing={1}>
          {recentSuccessfulChecks.length > 0 ? (
            recentSuccessfulChecks.map((item) => (
              <Chip
                key={item.id}
                size="small"
                color="success"
                variant="outlined"
                label={`${item.memberName} • ${new Date(item.checkedAt).toLocaleTimeString('ar-LY', { hour: '2-digit', minute: '2-digit' })}`}
              />
            ))
          ) : (
            <Typography variant="caption" color="text.secondary" sx={{ py: 0.5 }}>
              لا توجد عمليات ناجحة حتى الآن
            </Typography>
          )}
        </Stack>
      </Paper>

      {/* QR Scanner Dialog */}
      <Dialog open={scannerOpen} onClose={handleCloseScannerDialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          مسح الباركود / QR Code
          <IconButton onClick={handleCloseScannerDialog} sx={{ position: 'absolute', right: '0.375rem', top: '4.0rem' }}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          {cameraError ? (
            <Alert severity="warning" sx={{ mb: '1.0rem' }}>
              {cameraError}
            </Alert>
          ) : (
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
                وجّه الكاميرا نحو الباركود أو QR Code
              </Typography>
              <Box
                id="qr-reader-provider"
                sx={{
                  width: '100%',
                  '& video': {
                    width: '100%',
                    borderRadius: 1
                  }
                }}
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseScannerDialog}>إلغاء</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
