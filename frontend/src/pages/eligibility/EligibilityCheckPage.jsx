/**
 * Unified Eligibility Check Page
 * Simple, deterministic eligibility verification
 *
 * Supported Methods:
 * 1. QR/Barcode Scan (camera or hardware scanner)
 * 2. Card Number Entry
 *
 * NOT Supported:
 * - Name search (removed by architectural decision)
 * - Autocomplete
 * - Multiple results
 *
 * @version 2.0 - Refactored for deterministic behavior
 * @since 2026-01-10
 */

import { useState, useRef, useCallback, useEffect } from 'react';
import { formatCurrency } from 'utils/currency-formatter';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Stack,
  Divider,
  Alert,
  Chip,
  CircularProgress,
  Paper,
  InputAdornment,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid
} from '@mui/material';
import {
  QrCodeScanner as QrIcon,
  CreditCard as CardIcon,
  CheckCircle as CheckIcon,
  Cancel as CancelIcon,
  Close as CloseIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import { Html5Qrcode } from 'html5-qrcode';
import axiosClient from 'utils/axios';
import MainCard from 'components/MainCard';

const EligibilityCheckPage = () => {
  // ========================================
  // STATE
  // ========================================
  const [cardNumber, setCardNumber] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [errorCode, setErrorCode] = useState(null);

  // QR Scanner State
  const [scannerOpen, setScannerOpen] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [cameraError, setCameraError] = useState(null);
  const scannerRef = useRef(null);
  const html5QrCodeRef = useRef(null);

  // ========================================
  // API CALL
  // ========================================

  /**
   * Check eligibility using unified endpoint
   * Auto-detects card number or barcode format
   */
  const checkEligibility = useCallback(async (query) => {
    if (!query || !query.trim()) {
      setError('الرجاء إدخال رقم البطاقة أو مسح الباركود');
      return;
    }

    // Clear previous results
    setResult(null);
    setError(null);
    setErrorCode(null);
    setLoading(true);

    try {
      const response = await axiosClient.get('/members/eligibility', {
        params: { query: query.trim() }
      });

      const data = response.data?.data;

      if (data) {
        setResult(data);
        setError(null);
        setErrorCode(null);
      }
    } catch (err) {
      console.error('[Eligibility] Check failed:', err);

      // Handle specific error codes
      const code = err.response?.data?.errorCode || err.response?.data?.code;
      const message = err.response?.data?.message;

      setErrorCode(code);

      switch (code) {
        case 'INVALID_ELIGIBILITY_INPUT':
          setError('تنسيق غير صحيح. الرجاء إدخال رقم بطاقة صحيح أو مسح باركود صحيح');
          break;
        case 'MEMBER_NOT_FOUND':
          setError('العضو غير موجود. الرجاء التأكد من الرقم');
          break;
        default:
          setError(message || 'حدث خطأ أثناء فحص الأهلية. الرجاء المحاولة مرة أخرى');
      }

      setResult(null);
    } finally {
      setLoading(false);
    }
  }, []);

  // ========================================
  // CARD NUMBER HANDLERS
  // ========================================

  const handleCardNumberChange = (e) => {
    const value = e.target.value;
    // Allow only digits
    if (/^\d*$/.test(value)) {
      setCardNumber(value);
    }
  };

  const handleCardNumberSubmit = () => {
    checkEligibility(cardNumber);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && cardNumber.trim()) {
      handleCardNumberSubmit();
    }
  };

  // ========================================
  // QR SCANNER HANDLERS
  // ========================================

  const startQrScanner = async () => {
    setScannerOpen(true);
    setCameraError(null);
    setScanning(true);

    try {
      // Initialize Html5Qrcode
      const html5QrCode = new Html5Qrcode('qr-reader');
      html5QrCodeRef.current = html5QrCode;

      // Start scanning
      await html5QrCode.start(
        { facingMode: 'environment' }, // Use back camera
        {
          fps: 10,
          qrbox: { width: '15.625rem', height: '15.625rem' }
        },
        (decodedText) => {
          // QR Code successfully scanned
          stopQrScanner();
          setScannerOpen(false);
          // Immediately check eligibility
          checkEligibility(decodedText);
        },
        (errorMessage) => {
          // Scanning error (not critical, happens continuously)
          // Don't show to user
        }
      );
    } catch (err) {
      console.error('[QR Scanner] Failed to start:', err);
      setCameraError('فشل الوصول إلى الكاميرا. يمكنك استخدام ماسح الباركود المتصل بالجهاز');
      setScanning(false);
    }
  };

  const stopQrScanner = async () => {
    if (html5QrCodeRef.current) {
      try {
        await html5QrCodeRef.current.stop();
        html5QrCodeRef.current = null;
      } catch (err) {
        console.error('[QR Scanner] Failed to stop:', err);
      }
    }
    setScanning(false);
  };

  const handleCloseScannerDialog = () => {
    stopQrScanner();
    setScannerOpen(false);
    setCameraError(null);
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (html5QrCodeRef.current) {
        html5QrCodeRef.current.stop().catch(() => {});
      }
    };
  }, []);

  // ========================================
  // HARDWARE SCANNER SUPPORT
  // ========================================

  /**
   * Handle input from hardware barcode scanner
   * Scanners typically send value + Enter key
   */
  useEffect(() => {
    let buffer = '';
    let timeout = null;

    const handleKeyDown = (e) => {
      // If scanner mode is active (input is focused and scanning)
      if (document.activeElement?.id === 'scanner-input') {
        clearTimeout(timeout);

        if (e.key === 'Enter' && buffer.trim()) {
          e.preventDefault();
          checkEligibility(buffer.trim());
          buffer = '';
        } else if (e.key.length === 1) {
          buffer += e.key;

          // Auto-submit after 100ms of no input (scanner typically very fast)
          timeout = setTimeout(() => {
            if (buffer.trim()) {
              checkEligibility(buffer.trim());
              buffer = '';
            }
          }, 100);
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
  // UI HELPERS
  // ========================================

  const handleReset = () => {
    setCardNumber('');
    setResult(null);
    setError(null);
    setErrorCode(null);
  };

  const getStatusColor = (eligible) => {
    return eligible ? 'success' : 'error';
  };

  const getStatusIcon = (eligible) => {
    return eligible ? <CheckIcon /> : <CancelIcon />;
  };

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box sx={{ p: '1.5rem' }}>
      {/* Page Header */}
      <Box sx={{ mb: '2.0rem' }}>
        <Typography variant="h4" gutterBottom>
          فحص أهلية المنتفع
        </Typography>
        <Typography variant="body2" color="text.secondary">
          تحقق من أهلية المنتفع باستخدام رقم البطاقة أو مسح الباركود/QR
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Input Section */}
        <Grid size={{ xs: 12, lg: 6 }}>
          <MainCard title="طرق التحقق" sx={{ height: '100%' }}>
            <Stack spacing={3}>
              {/* Method 1: QR/Barcode Scanner */}
              <Box>
                <Typography variant="subtitle2" gutterBottom sx={{ mb: '1.0rem' }}>
                  1. مسح الباركود / QR Code
                </Typography>

                <Stack spacing={2}>
                  {/* Camera Scanner */}
                  <Button
                    variant="contained"
                    color="primary"
                    size="large"
                    startIcon={<QrIcon />}
                    onClick={startQrScanner}
                    disabled={loading || scanning}
                    fullWidth
                  >
                    مسح باستخدام الكاميرا
                  </Button>

                  {/* Hardware Scanner */}
                  <TextField
                    id="scanner-input"
                    fullWidth
                    placeholder="أو وجّه الماسح الضوئي هنا..."
                    disabled={loading}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <QrIcon color="action" />
                        </InputAdornment>
                      )
                    }}
                    helperText="الماسحات الضوئية (Scanners) تعمل تلقائياً عند تركيز المؤشر هنا"
                  />
                </Stack>
              </Box>

              <Divider>
                <Chip label="أو" size="small" />
              </Divider>

              {/* Method 2: Card Number */}
              <Box>
                <Typography variant="subtitle2" gutterBottom sx={{ mb: '1.0rem' }}>
                  2. إدخال رقم البطاقة يدوياً
                </Typography>

                <Stack spacing={2}>
                  <TextField
                    fullWidth
                    label="رقم البطاقة"
                    placeholder="أدخل رقم البطاقة"
                    value={cardNumber}
                    onChange={handleCardNumberChange}
                    onKeyPress={handleKeyPress}
                    disabled={loading}
                    autoFocus
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <CardIcon color="action" />
                        </InputAdornment>
                      ),
                      inputProps: {
                        inputMode: 'numeric',
                        pattern: '[0-9]*'
                      }
                    }}
                    sx={{ direction: 'ltr' }}
                  />

                  <Button
                    variant="contained"
                    color="secondary"
                    size="large"
                    onClick={handleCardNumberSubmit}
                    disabled={loading || !cardNumber.trim()}
                    startIcon={loading ? <CircularProgress size={20} /> : <CheckIcon />}
                    fullWidth
                  >
                    {loading ? 'جاري الفحص...' : 'فحص الأهلية'}
                  </Button>
                </Stack>
              </Box>

              {/* Error Display */}
              {error && (
                <Alert severity="error" onClose={() => setError(null)} sx={{ mt: '1.0rem' }}>
                  {error}
                </Alert>
              )}
            </Stack>
          </MainCard>
        </Grid>

        {/* Result Section */}
        <Grid size={{ xs: 12, lg: 6 }}>
          {result ? (
            <MainCard
              title="نتيجة الفحص"
              secondary={
                <IconButton onClick={handleReset} size="small">
                  <RefreshIcon />
                </IconButton>
              }
            >
              <Stack spacing={3}>
                {/* Member Info */}
                <Box>
                  <Typography variant="h5" gutterBottom>
                    {result.fullName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    رقم البطاقة: {result.cardNumber || 'غير متوفر'}
                  </Typography>
                </Box>

                {/* Eligibility Status */}
                <Paper
                  elevation={0}
                  sx={{
                    p: '1.5rem',
                    bgcolor: result.eligible ? 'success.lighter' : 'error.lighter',
                    border: 1,
                    borderColor: result.eligible ? 'success.main' : 'error.main'
                  }}
                >
                  <Stack direction="row" alignItems="center" spacing={2}>
                    {getStatusIcon(result.eligible)}
                    <Box flex={1}>
                      <Typography variant="h6" color={getStatusColor(result.eligible)}>
                        {result.eligible ? 'مؤهل للخدمات' : 'غير مؤهل للخدمات'}
                      </Typography>
                      {result.ineligibilityReason && (
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          السبب: {result.ineligibilityReason}
                        </Typography>
                      )}
                    </Box>
                  </Stack>
                </Paper>

                {/* Additional Info */}
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      جهة العمل
                    </Typography>
                    <Typography variant="body1">{result.employerName || 'غير متوفر'}</Typography>
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      السياسة الطبية
                    </Typography>
                    <Typography variant="body1">{result.policyName || 'غير متوفر'}</Typography>
                  </Box>

                  {result.copayAmount != null && (
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        نسبة التغطية
                      </Typography>
                      <Typography variant="h6" color="primary">
                        {result.copayAmount}%
                      </Typography>
                    </Box>
                  )}

                  {result.coverageLimit && (
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        الحد السنوي
                      </Typography>
                      <Typography variant="h6" color="primary">
                        {formatCurrency(result.coverageLimit)}
                      </Typography>
                    </Box>
                  )}

                  {/* Status Details */}
                  <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: '1.0rem' }}>
                    <Chip
                      label={`حالة العضو: ${result.memberStatus}`}
                      size="small"
                      color={result.memberStatus === 'ACTIVE' ? 'success' : 'default'}
                    />
                    <Chip
                      label={`حالة البطاقة: ${result.cardStatus}`}
                      size="small"
                      color={result.cardStatus === 'ACTIVE' ? 'success' : 'default'}
                    />
                  </Stack>
                </Stack>

                {/* Message */}
                {result.message && <Alert severity={result.eligible ? 'success' : 'warning'}>{result.message}</Alert>}
              </Stack>
            </MainCard>
          ) : (
            <Paper
              sx={{
                p: '3.0rem',
                textAlign: 'center',
                bgcolor: 'background.default',
                border: '1px dashed',
                borderColor: 'divider',
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <Box>
                <QrIcon sx={{ fontSize: '5.0rem', color: 'text.disabled', mb: '1.0rem' }} />
                <Typography variant="h6" color="text.secondary" gutterBottom>
                  في انتظار الفحص
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  استخدم إحدى الطرق لفحص أهلية المنتفع
                </Typography>
              </Box>
            </Paper>
          )}
        </Grid>
      </Grid>

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
                id="qr-reader"
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
};

export default EligibilityCheckPage;


