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
  const [selectedVisitType, setSelectedVisitType] = useState('');
  const [registeringVisit, setRegisteringVisit] = useState(false);
  const [checkHistory, setCheckHistory] = useState([]);

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

  const checkEligibility = useCallback(async (barcodeOrCardNumber) => {
    // Validate input
    const trimmedValue = barcodeOrCardNumber?.trim();

    if (!trimmedValue) {
      setError('يرجى إدخال رقم البطاقة أو الباركود أو رقم العضو');
      return;
    }

    // Only reject single "0", allow other numbers (including leading zeros like "000001")
    if (trimmedValue === '0') {
      setError('يرجى إدخال رقم صحيح (رقم البطاقة أو الباركود أو رقم العضو)');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    setSelectedMember(null);
    setSelectedVisitType('');

    try {
      // Send as barcode (API accepts card number, barcode, or member ID in this field)
      const response = await providerApi.checkEligibility({ barcode: trimmedValue });

      // API returns the DTO directly (ProviderEligibilityResponse)
      if (response && (response.eligible !== undefined || response.statusCode)) {
        setResult(response);
        const firstMember = response.familyMembers?.[0] || response.principalMember;
        setCheckHistory((prev) => [
          {
            id: `${Date.now()}-${trimmedValue}`,
            input: trimmedValue,
            eligible: !!response.eligible,
            memberName: firstMember?.fullName || 'غير محدد',
            checkedAt: new Date().toISOString(),
            dateKey: new Date().toLocaleDateString('en-CA')
          },
          ...prev
        ].slice(0, 50));
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
    setSelectedVisitType('');
    lastAutoSubmittedRef.current = '';
  };

  useEffect(() => {
    const trimmed = searchValue?.trim();

    if (!trimmed || trimmed.length < 6) {
      return;
    }

    if (loading || trimmed === lastAutoSubmittedRef.current) {
      return;
    }

    if (autoCheckTimerRef.current) {
      clearTimeout(autoCheckTimerRef.current);
    }

    autoCheckTimerRef.current = setTimeout(() => {
      lastAutoSubmittedRef.current = trimmed;
      checkEligibility(trimmed);
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
    let timeout;

    const handleKeyDown = (e) => {
      const target = e.target;
      if (target.id === 'scanner-input-provider' || target.tagName === 'INPUT') {
        clearTimeout(timeout);

        if (e.key === 'Enter' && buffer.trim()) {
          e.preventDefault();
          setSearchValue(buffer.trim());
          checkEligibility(buffer.trim());
          buffer = '';
        } else if (e.key.length === 1) {
          buffer += e.key;

          // Scanner typically sends all characters within 50ms, so 300ms timeout
          // allows distinguishing between scanner and manual typing
          timeout = setTimeout(() => {
            if (buffer.trim() && buffer.trim().length >= 3) {
              setSearchValue(buffer.trim());
              checkEligibility(buffer.trim());
              buffer = '';
            } else {
              // Reset buffer for short inputs (manual typing)
              buffer = '';
            }
          }, 300);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      clearTimeout(timeout);
    };
  }, [checkEligibility]);

  // ========================================
  // VISIT REGISTRATION HANDLER
  // ========================================

  const handleRegisterVisit = async () => {
    if (!selectedVisitType) {
      setError('يجب اختيار نوع الزيارة');
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
        setError(visitResponse.message || 'فشل في تسجيل الزيارة');
      }
    } catch (err) {
      console.error('Failed to register visit:', err);
      setError(err.message || 'فشل في تسجيل الزيارة');
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
    <Box sx={{ bgcolor: '#F5F7FA', minHeight: 'calc(100vh - 80px)', p: { xs: 1, md: 2 }, borderRadius: '0.25rem' }}>
      <ModernPageHeader title="فحص الأهلية" subtitle="التحقق من أهلية المؤمن عليه وتسجيل الزيارة" icon={LocalHospitalIcon} />


      <Box sx={{ display: 'flex', gap: '1.5rem', mt: 1 }}>
        {/* LEFT COLUMN: Member Profile Panel (Fixed Width - Desktop Only) */}
        {selectedMember && (
          <Paper
            elevation={2}
            sx={{
              width: '18.75rem',
              flexShrink: 0,
              bgcolor: 'background.paper',
              borderRadius: '0.25rem',
              overflow: 'hidden',
              height: 'fit-content',
              position: 'sticky',
              top: '40.0rem',
              display: { xs: 'none', lg: 'block' }
            }}
          >
            {/* Header */}
            <Box sx={{ bgcolor: 'primary.main', color: 'white', p: '1.0rem', textAlign: 'center' }}>
              <Typography variant="h6" fontWeight={600}>
                ملف المنتفع
              </Typography>
            </Box>

            <Box sx={{ p: '1.5rem' }}>
              <Stack spacing={2} alignItems="center">
                {/* Profile Image */}
                <MemberAvatar
                  member={{
                    id: selectedMember.memberId,
                    fullName: selectedMember.fullName,
                    photoUrl: selectedMember.profileImage
                  }}
                  size={120}
                  refreshTrigger={`${selectedMember.memberId || ''}-${selectedMember.profileImage || ''}`}
                  sx={{
                    border: 4,
                    borderColor: selectedMember.eligible ? 'success.main' : 'error.main',
                    bgcolor: 'grey.300',
                    fontSize: '3.0rem',
                    fontWeight: 600
                  }}
                />

                {/* Member Name */}
                <Box sx={{ textAlign: 'center', width: '100%' }}>
                  <Typography variant="h6" fontWeight={600} gutterBottom>
                    {selectedMember.fullName}
                  </Typography>
                  {selectedMember.isPrincipal && <Chip label="عضو رئيسي" color="primary" size="small" sx={{ mb: 1 }} />}
                </Box>

                <Divider sx={{ width: '100%' }} />

                {/* Member Details */}
                <Stack spacing={1.5} sx={{ width: '100%' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block">
                      رقم العضوية
                    </Typography>
                    <Typography variant="body2" fontWeight={500}>
                      {selectedMember.memberId}
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block">
                      رقم البوليصة
                    </Typography>
                    <Typography variant="body2" fontWeight={500}>
                      {result?.barcode || 'غير متوفر'}
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block">
                      الصلة
                    </Typography>
                    <Typography variant="body2" fontWeight={500}>
                      {selectedMember.relationship || 'SELF'}
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block">
                      العمر
                    </Typography>
                    <Typography variant="body2" fontWeight={500}>
                      {selectedMember.age || '-'} سنة
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block">
                      حالة الأهلية
                    </Typography>
                    <Chip
                      label={selectedMember.eligible ? 'مؤهل للخدمة' : 'غير مؤهل'}
                      color={selectedMember.eligible ? 'success' : 'error'}
                      size="small"
                      sx={{ mt: 0.5 }}
                    />
                  </Box>

                  <Divider />

                  {/* Coverage Summary */}
                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
                      الحد السنوي المتبقي
                    </Typography>
                    <Typography variant="h5" color="success.main" fontWeight={600}>
                      {formatCurrency(selectedMember.remainingLimit)}
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
                      نسبة الاستخدام
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress
                        variant="determinate"
                        value={selectedMember.usagePercentage || 0}
                        color={getUsageColor(selectedMember.usagePercentage || 0)}
                        sx={{ height: '0.375rem', borderRadius: 1, flex: 1 }}
                      />
                      <Typography variant="body2" fontWeight={500}>
                        {(selectedMember.usagePercentage || 0).toFixed(0)}%
                      </Typography>
                    </Box>
                  </Box>
                </Stack>
              </Stack>
            </Box>
          </Paper>
        )}

        {/* RIGHT COLUMN: Search & Results */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', justifyContent: 'center' }}>
              <Paper
                elevation={0}
                sx={{
                  width: '100%',
                  maxWidth: '62.5rem',
                  p: { xs: 2, md: 3 },
                  borderRadius: '0.1875rem',
                  bgcolor: 'common.white',
                  border: '1px solid',
                  borderColor: 'divider',
                  boxShadow: '0 8px 24px rgba(15, 23, 42, 0.06)'
                }}
              >
                <Stack spacing={2}>
                  <Typography variant="subtitle1" fontWeight={700} textAlign="center">
                    أدخل رقم الهوية أو امسح الباركود للتحقق
                  </Typography>

                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems="stretch">
                    <Box sx={{ flex: 1 }}>
                      <TextField
                        fullWidth
                        size="medium"
                        label="رقم البطاقة / الباركود / رقم العضو"
                        placeholder="ابدأ الكتابة أو المسح... يبدأ الفحص تلقائياً"
                        value={searchValue}
                        onChange={handleInputChange}
                        disabled={loading}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <QrCodeScannerIcon color="primary" />
                            </InputAdornment>
                          ),
                          endAdornment: (
                            <InputAdornment position="end">{loading ? <CircularProgress size={20} /> : <CreditCardIcon color="action" />}</InputAdornment>
                          )
                        }}
                        sx={{
                          direction: 'ltr',
                          '& .MuiOutlinedInput-root': {
                            borderRadius: '0.375rem',
                            bgcolor: 'common.white',
                            minHeight: '3.5rem',
                            transition: 'all 0.2s ease',
                            '& fieldset': {
                              borderColor: 'divider'
                            },
                            '&:hover': {
                              boxShadow: (theme) => `0 0 0 3px ${theme.palette.success.light}33`
                            },
                            '&:hover fieldset': {
                              borderColor: 'success.main'
                            },
                            '&.Mui-focused': {
                              boxShadow: (theme) => `0 0 0 3px ${theme.palette.primary.light}33`
                            }
                          }
                        }}
                      />
                    </Box>

                    <Button
                      variant="outlined"
                      color="primary"
                      startIcon={<QrCodeScannerIcon />}
                      onClick={handleOpenScannerDialog}
                      disabled={loading}
                      sx={{ minWidth: { md: 150 }, borderRadius: '0.375rem', whiteSpace: 'nowrap' }}
                    >
                      مسح الكاميرا
                    </Button>

                    <Button
                      variant="contained"
                      color="success"
                      onClick={handleSubmit}
                      disabled={loading || !searchValue.trim()}
                      startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <CheckCircleIcon />}
                      sx={{ minWidth: { md: 120 }, borderRadius: '0.375rem', fontWeight: 700 }}
                    >
                      فحص
                    </Button>
                  </Stack>

                  <Typography variant="caption" color="text.secondary" textAlign="center">
                    يتم الفحص تلقائياً عند إدخال 6 أحرف أو أكثر
                  </Typography>

                  <Box sx={{ height: 0, overflow: 'hidden', opacity: 0 }}>
                    <TextField id="scanner-input-provider" ref={scannerInputRef} fullWidth size="small" />
                  </Box>

                  {error && (
                    <Alert severity="error" onClose={() => setError(null)}>
                      {error}
                    </Alert>
                  )}
                </Stack>
              </Paper>
            </Box>

            {!result && (
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <HistoryIcon color="primary" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>تاريخ فحوصات اليوم</Typography>
                        <Typography variant="body2" color="text.secondary">{todayChecks.length} عملية فحص</Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TaskAltIcon color="success" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>آخر منتفع تم فحصه</Typography>
                        <Typography variant="body2" color="text.secondary" noWrap>{checkHistory[0]?.memberName || 'لا يوجد بعد'}</Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', height: '100%', bgcolor: 'common.white' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <TipsAndUpdatesIcon color="warning" />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>تعليمات سريعة</Typography>
                        <Typography variant="body2" color="text.secondary">مرّر الباركود أو أدخل رقم البطاقة</Typography>
                      </Box>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: 'success.lighter', border: '1px solid', borderColor: 'success.light' }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="success.dark" fontWeight={700}>حالات مقبولة اليوم</Typography>
                      <Typography variant="h4" color="success.dark" fontWeight={800}>{todayAcceptedCount}</Typography>
                    </Stack>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem', bgcolor: 'error.lighter', border: '1px solid', borderColor: 'error.light' }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="error.dark" fontWeight={700}>حالات مرفوضة اليوم</Typography>
                      <Typography variant="h4" color="error.dark" fontWeight={800}>{todayRejectedCount}</Typography>
                    </Stack>
                  </Paper>
                </Grid>
              </Grid>
            )}

            {/* Results Section */}
            {result ? (
              <MainCard
                title={
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Typography variant="h5">نتيجة الفحص</Typography>
                    <IconButton onClick={handleReset} size="small" title="إعادة ضبط">
                      <RefreshIcon />
                    </IconButton>
                  </Box>
                }
                contentSX={{ p: '1.0rem' }}
              >
                <Stack spacing={2}>
                  {/* Eligibility Status */}
                  <Paper
                    elevation={0}
                    sx={{
                      p: '0.75rem',
                      bgcolor: result.eligible ? 'success.lighter' : 'error.lighter',
                      border: 1,
                      borderColor: result.eligible ? 'success.main' : 'error.main',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '1.0rem'
                    }}
                  >
                    {result.eligible ? (
                      <CheckCircleIcon sx={{ fontSize: '1.75rem', color: 'success.main' }} />
                    ) : (
                      <CancelIcon sx={{ fontSize: '1.75rem', color: 'error.main' }} />
                    )}
                    <Box flex={1}>
                      <Typography variant="h6" color={result.eligible ? 'success.dark' : 'error.dark'}>
                        {result.message}
                      </Typography>
                      {result.eligible && (
                        <Typography variant="body2" color="text.secondary">
                          {result.policyNumber && `رقم البوليصة: ${result.policyNumber}`}
                          {result.planName && ` | الخطة: ${result.planName}`}
                        </Typography>
                      )}
                    </Box>
                    <Box>
                      {result.barcode && (
                        <Typography variant="caption" display="block" color="text.secondary">
                          #{result.barcode}
                        </Typography>
                      )}
                    </Box>
                  </Paper>

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

                  {/* Principal Member Info */}
                  {result.principalMember && (
                    <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'grey.50' }}>
                      <Stack direction="row" alignItems="center" spacing={2}>
                        <PersonIcon color="primary" />
                        <Box>
                          <Typography variant="subtitle1" fontWeight="bold">
                            {result.principalMember.fullName}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            العضو الرئيسي - {result.employerName || 'جهة العمل غير محددة'}
                          </Typography>
                        </Box>
                      </Stack>
                    </Paper>
                  )}

                  {/* Coverage Info */}
                  <Grid container spacing={2}>
                    <Grid xs={6} md={3}>
                      <Paper elevation={0} sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'primary.lighter' }}>
                        <Typography variant="body2" color="text.secondary">
                          الحد السنوي
                        </Typography>
                        <Typography variant="h6" color="primary.dark">
                          {formatCurrency(result.principalAnnualLimit)}
                        </Typography>
                      </Paper>
                    </Grid>
                    <Grid xs={6} md={3}>
                      <Paper elevation={0} sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'warning.lighter' }}>
                        <Typography variant="body2" color="text.secondary">
                          المستخدم
                        </Typography>
                        <Typography variant="h6" color="warning.dark">
                          {formatCurrency(result.principalUsedAmount)}
                        </Typography>
                      </Paper>
                    </Grid>
                    <Grid xs={6} md={3}>
                      <Paper elevation={0} sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'success.lighter' }}>
                        <Typography variant="body2" color="text.secondary">
                          المتبقي
                        </Typography>
                        <Typography variant="h6" color="success.dark">
                          {formatCurrency(result.principalRemainingLimit)}
                        </Typography>
                      </Paper>
                    </Grid>
                    <Grid xs={6} md={3}>
                      <Paper elevation={0} sx={{ p: '1.0rem', textAlign: 'center', bgcolor: 'grey.100' }}>
                        <Typography variant="body2" color="text.secondary">
                          نسبة الاستخدام
                        </Typography>
                        <Typography variant="h6">{(result.principalUsagePercentage || 0).toFixed(1)}%</Typography>
                      </Paper>
                    </Grid>
                  </Grid>

                  {/* Family Members Table */}
                  {result.familyMembers && result.familyMembers.length > 0 && (
                    <Box>
                      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: '1.0rem' }}>
                        <FamilyRestroomIcon color="primary" />
                        <Typography variant="h6">أفراد العائلة ({result.totalFamilyMembers || result.familyMembers.length})</Typography>
                      </Stack>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
                        يرجى اختيار المنتفع من القائمة أدناه للمتابعة
                      </Typography>

                      <TableContainer component={Paper} variant="outlined">
                        <Table size="small">
                          <TableHead>
                            <TableRow sx={{ bgcolor: tableHeaderBg }}>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>الاسم</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>الصلة</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>العمر</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>الحالة</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>المتبقي</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }}>النسبة</TableCell>
                              <TableCell sx={{ color: tableHeaderColor, fontWeight: 600 }} align="center">
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
                                        <Chip label="رئيسي" size="small" color="primary" sx={{ height: '1.125rem', fontSize: '0.75rem' }} />
                                      )}
                                    </Box>
                                  </Stack>
                                </TableCell>
                                <TableCell>{member.relationship || 'SELF'}</TableCell>
                                <TableCell>{member.age || '-'}</TableCell>
                                <TableCell>
                                  <Chip
                                    label={member.eligible ? 'مؤهل' : 'غير مؤهل'}
                                    color={member.eligible ? 'success' : 'error'}
                                    size="small"
                                  />
                                </TableCell>
                                <TableCell>{formatCurrency(member.remainingLimit)}</TableCell>
                                <TableCell>
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

                        {/* Visit Type Selection */}
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

                        <Button
                          variant="contained"
                          color="primary"
                          size="large"
                          fullWidth
                          startIcon={registeringVisit ? <CircularProgress size={20} color="inherit" /> : <AssignmentIcon />}
                          disabled={registeringVisit || !selectedVisitType}
                          onClick={handleRegisterVisit}
                        >
                          {registeringVisit ? 'جاري التسجيل...' : 'تسجيل زيارة'}
                        </Button>
                      </Stack>
                    </Paper>
                  )}

                  {/* Covered Services */}
                  {result.coveredServices && result.coveredServices.length > 0 && (
                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        الخدمات المغطاة:
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        {result.coveredServices.map((service, index) => (
                          <Chip key={index} label={service} size="small" color="primary" variant="outlined" />
                        ))}
                      </Stack>
                    </Box>
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




