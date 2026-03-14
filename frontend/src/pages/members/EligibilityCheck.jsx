/**
 * Eligibility Check Page
 *
 * Checks family eligibility using Principal's Barcode.
 * Displays all family members with their eligibility status.
 * Allows selection of member for service/visit.
 *
 * @module EligibilityCheck
 * @since 2026-01-11
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Alert,
  IconButton,
  Divider
} from '@mui/material';
import {
  QrCodeScanner as QrCodeScannerIcon,
  Search as SearchIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Visibility as VisibilityIcon,
  PersonAdd as PersonAddIcon,
  QrCode as QrCodeIcon,
  CreditCard as CreditCardIcon,
  Badge as BadgeIcon,
  Savings as SavingsIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon,
  TrendingUp as TrendingUpIcon,
  Payments as PaymentsIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import MemberAvatar from 'components/tba/MemberAvatar';
import { checkEligibility, GENDERS } from 'services/api/unified-members.service';
import { openSnackbar } from 'api/snackbar';

/**
 * Eligibility Check Component
 */
const EligibilityCheck = () => {
  const navigate = useNavigate();

  const [barcode, setBarcode] = useState('');
  const [loading, setLoading] = useState(false);
  const [familyData, setFamilyData] = useState(null);
  const [error, setError] = useState('');

  /**
   * Handle barcode input change
   */
  const handleBarcodeChange = (e) => {
    const value = e.target.value;
    setBarcode(value);
    setError('');
  };

  /**
   * Check eligibility
   */
  const handleCheckEligibility = async () => {
    // Validation
    if (!barcode.trim()) {
      setError('يرجى إدخال Barcode');
      return;
    }

    // Barcode format validation (WAHA-YYYY-NNNNNN)
    const barcodePattern = /^WAHA-\d{4}-\d{6}$/;
    if (!barcodePattern.test(barcode.trim())) {
      setError('تنسيق Barcode غير صحيح. الصيغة المطلوبة: WAHA-YYYY-NNNNNN');
      return;
    }

    setLoading(true);
    setFamilyData(null);
    setError('');

    try {
      const response = await checkEligibility(barcode.trim());
      console.log('Eligibility response:', response);

      if (response.data) {
        setFamilyData(response.data);
        openSnackbar({
          open: true,
          message: 'تم جلب بيانات العائلة بنجاح',
          variant: 'alert',
          alert: { color: 'success' }
        });
      }
    } catch (error) {
      console.error('Error checking eligibility:', error);

      const errorMessage = error.response?.data?.message || 'خطأ في فحص الأهلية';
      setError(errorMessage);

      openSnackbar({
        open: true,
        message: errorMessage,
        variant: 'alert',
        alert: { color: 'error' }
      });
    } finally {
      setLoading(false);
    }
  };

  /**
   * Handle Enter key press
   */
  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleCheckEligibility();
    }
  };

  /**
   * Select member for service
   */
  const handleSelectMember = (member) => {
    openSnackbar({
      open: true,
      message: `تم اختيار: ${member.fullName}`,
      variant: 'alert',
      alert: { color: 'info' }
    });
    // Navigate to create visit/claim/service page with selected member
    // navigate(`/visits/create?memberId=${member.id}`);
  };

  /**
   * Reset form
   */
  const handleReset = () => {
    setBarcode('');
    setFamilyData(null);
    setError('');
  };

  return (
    <>
      <ModernPageHeader
        title="فحص الأهلية"
        subtitle="التحقق من أهلية المنتفع وأسرته عبر Barcode"
        icon={<QrCodeScannerIcon />}
        breadcrumbs={[{ label: 'الرئيسية', href: '/' }, { label: 'المنتفعين', href: '/members' }, { label: 'فحص الأهلية' }]}
      />

      <Grid container spacing={3}>
        {/* Barcode Input Card */}
        <Grid size={12}>
          <MainCard>
            <Stack spacing={3}>
              <Alert severity="info" icon={<QrCodeIcon />}>
                أدخل Barcode المنتفع الرئيسي للتحقق من أهلية جميع أفراد العائلة (الصيغة: WAHA-YYYY-NNNNNN)
              </Alert>

              <Grid container spacing={2} alignItems="flex-start">
                <Grid size={{ xs: 12, md: 8 }}>
                  <TextField
                    fullWidth
                    label="Barcode"
                    placeholder="WAHA-2026-000001"
                    value={barcode}
                    onChange={handleBarcodeChange}
                    onKeyPress={handleKeyPress}
                    error={!!error}
                    helperText={error || 'مثال: WAHA-2026-000001'}
                    InputProps={{
                      startAdornment: <QrCodeIcon sx={{ mr: 1, color: 'action.active' }} />
                    }}
                    disabled={loading}
                  />
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <Stack direction="row" spacing={1}>
                    <Button
                      fullWidth
                      variant="contained"
                      size="large"
                      startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
                      onClick={handleCheckEligibility}
                      disabled={loading}
                    >
                      {loading ? 'جاري الفحص...' : 'فحص الأهلية'}
                    </Button>
                    {familyData && (
                      <Button variant="outlined" onClick={handleReset}>
                        جديد
                      </Button>
                    )}
                  </Stack>
                </Grid>
              </Grid>
            </Stack>
          </MainCard>
        </Grid>

        {/* Results */}
        {familyData && (
          <>
            {/* Principal Member Card */}
            <Grid size={12}>
              <Card elevation={3}>
                <CardContent>
                  <Grid container spacing={3}>
                    <Grid size={12}>
                      <Stack direction="row" spacing={2} alignItems="center">
                        <MemberAvatar member={familyData.principal} size={64} />
                        <Box>
                          <Typography variant="h5" gutterBottom>
                            {familyData.principal?.fullName}
                          </Typography>
                          <Stack direction="row" spacing={1}>
                            <Chip label="منتفع رئيسي" color="primary" size="small" />
                            <Chip
                              label={familyData.principal?.eligible ? 'مؤهل' : 'غير مؤهل'}
                              color={familyData.principal?.eligible ? 'success' : 'error'}
                              icon={familyData.principal?.eligible ? <CheckCircleIcon /> : <CancelIcon />}
                              size="small"
                            />
                          </Stack>
                        </Box>
                      </Stack>
                    </Grid>

                    <Grid size={12}>
                      <Divider />
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper elevation={1} sx={{ p: '1.0rem', bgcolor: 'primary.lighter' }}>
                        <Stack direction="row" spacing={2} alignItems="center">
                          <QrCodeIcon color="primary" />
                          <Box>
                            <Typography variant="caption" color="text.secondary">
                              Barcode
                            </Typography>
                            <Typography variant="h6" color="primary.main" fontWeight="bold">
                              {familyData.principal?.barcode}
                            </Typography>
                          </Box>
                        </Stack>
                      </Paper>
                    </Grid>

                    <Grid size={{ xs: 12, md: 6 }}>
                      <Paper elevation={1} sx={{ p: '1.0rem' }}>
                        <Stack direction="row" spacing={2} alignItems="center">
                          <CreditCardIcon color="secondary" />
                          <Box>
                            <Typography variant="caption" color="text.secondary">
                              رقم البطاقة
                            </Typography>
                            <Typography variant="h6" fontWeight="medium">
                              {familyData.principal?.cardNumber}
                            </Typography>
                          </Box>
                        </Stack>
                      </Paper>
                    </Grid>

                    {/* Financial Summary */}
                    <Grid size={12}>
                      <Typography variant="subtitle1" gutterBottom sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                        <AccountBalanceWalletIcon fontSize="small" color="primary" />
                        الملخص المالي للمنتفع
                      </Typography>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Paper elevation={0} variant="outlined" sx={{ p: '0.75rem', bgcolor: 'success.lighter', borderLeft: '4px solid', borderLeftColor: 'success.main' }}>
                        <Stack direction="row" spacing={1.5} alignItems="center">
                          <SavingsIcon color="success" />
                          <Box>
                            <Typography variant="caption" color="text.secondary" display="block">
                              الحد السنوي
                            </Typography>
                            <Typography variant="h6">
                              {familyData.principal?.annualLimit?.toLocaleString() || '0'} د.ل
                            </Typography>
                          </Box>
                        </Stack>
                      </Paper>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Paper elevation={0} variant="outlined" sx={{ p: '0.75rem', bgcolor: 'warning.lighter', borderLeft: '4px solid', borderLeftColor: 'warning.main' }}>
                        <Stack direction="row" spacing={1.5} alignItems="center">
                          <PaymentsIcon color="warning" />
                          <Box>
                            <Typography variant="caption" color="text.secondary" display="block">
                              المستهلك
                            </Typography>
                            <Typography variant="h6">
                              {familyData.principal?.usedAmount?.toLocaleString() || '0'} د.ل
                            </Typography>
                          </Box>
                        </Stack>
                      </Paper>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Paper elevation={0} variant="outlined" sx={{ p: '0.75rem', bgcolor: 'info.lighter', borderLeft: '4px solid', borderLeftColor: 'info.main' }}>
                        <Stack direction="row" spacing={1.5} alignItems="center">
                          <TrendingUpIcon color="info" />
                          <Box>
                            <Typography variant="caption" color="text.secondary" display="block">
                              المتبقي
                            </Typography>
                            <Typography variant="h6" color="info.dark" fontWeight="bold">
                              {familyData.principal?.remainingLimit?.toLocaleString() || '0'} د.ل
                            </Typography>
                          </Box>
                        </Stack>
                      </Paper>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          تاريخ الميلاد
                        </Typography>
                        <Typography variant="body1">{familyData.principal?.birthDate || '-'}</Typography>
                      </Stack>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          الجنس
                        </Typography>
                        <Typography variant="body1">{familyData.principal?.gender === GENDERS.MALE ? 'ذكر' : 'أنثى'}</Typography>
                      </Stack>
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                      <Stack spacing={0.5}>
                        <Typography variant="caption" color="text.secondary">
                          جهة العمل
                        </Typography>
                        <Typography variant="body1">{familyData.principal?.employerName || '-'}</Typography>
                      </Stack>
                    </Grid>

                    {!familyData.principal?.eligible && (
                      <Grid size={12}>
                        <Alert severity="warning">
                          <strong>سبب عدم الأهلية:</strong> {familyData.principal?.eligibilityReason || 'غير محدد'}
                        </Alert>
                      </Grid>
                    )}

                    <Grid size={12}>
                      <Button
                        fullWidth
                        variant="contained"
                        color="primary"
                        startIcon={<PersonAddIcon />}
                        onClick={() => handleSelectMember(familyData.principal)}
                        disabled={!familyData.principal?.eligible}
                      >
                        اختيار هذا المنتفع للخدمة
                      </Button>
                    </Grid>
                  </Grid>
                </CardContent>
              </Card>
            </Grid>

            {/* Dependents Table */}
            {familyData.dependents && familyData.dependents.length > 0 && (
              <Grid size={12}>
                <MainCard
                  title={
                    <Stack direction="row" spacing={2} alignItems="center">
                      <Typography variant="h5">التابعون</Typography>
                      <Chip label={`${familyData.dependents.length} تابع`} color="success" size="small" />
                    </Stack>
                  }
                >
                  <TableContainer component={Paper} elevation={0} variant="outlined">
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableCell>#</TableCell>
                          <TableCell align="center">الصورة</TableCell>
                          <TableCell>الاسم</TableCell>
                          <TableCell>القرابة</TableCell>
                          <TableCell>رقم البطاقة</TableCell>
                          <TableCell>تاريخ الميلاد</TableCell>
                          <TableCell>الجنس</TableCell>
                          <TableCell>الحد السنوي</TableCell>
                          <TableCell>المتبقي</TableCell>
                          <TableCell>الحالة</TableCell>
                          <TableCell>الأهلية</TableCell>
                          <TableCell align="center">إجراءات</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {familyData.dependents.map((dep, index) => (
                          <TableRow key={dep.id} hover>
                            <TableCell>{index + 1}</TableCell>
                            <TableCell align="center">
                              <MemberAvatar member={dep} size={32} />
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" fontWeight="medium">
                                {dep.fullName}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Chip label={dep.relationship} size="small" color="primary" variant="outlined" />
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" fontFamily="monospace">
                                {dep.cardNumber}
                              </Typography>
                            </TableCell>
                            <TableCell>{dep.birthDate || '-'}</TableCell>
                            <TableCell>{dep.gender === GENDERS.MALE ? 'ذكر' : 'أنثى'}</TableCell>
                            <TableCell>{dep.annualLimit?.toLocaleString() || '0'} د.ل</TableCell>
                            <TableCell>
                              <Typography variant="body2" color="info.main" fontWeight="bold">
                                {dep.remainingLimit?.toLocaleString() || '0'} د.ل
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Chip label={dep.status || 'ACTIVE'} size="small" color={dep.status === 'ACTIVE' ? 'success' : 'default'} />
                            </TableCell>
                            <TableCell>
                              {dep.eligible ? (
                                <Chip icon={<CheckCircleIcon />} label="مؤهل" color="success" size="small" />
                              ) : (
                                <Chip icon={<CancelIcon />} label="غير مؤهل" color="error" size="small" />
                              )}
                            </TableCell>
                            <TableCell align="center">
                              <Stack direction="row" spacing={1} justifyContent="center">
                                <IconButton
                                  size="small"
                                  color="primary"
                                  onClick={() => navigate(`/members/${dep.id}`)}
                                  title="عرض التفاصيل"
                                >
                                  <VisibilityIcon fontSize="small" />
                                </IconButton>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="primary"
                                  startIcon={<PersonAddIcon />}
                                  onClick={() => handleSelectMember(dep)}
                                  disabled={!dep.eligible}
                                >
                                  اختيار
                                </Button>
                              </Stack>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </MainCard>
              </Grid>
            )}

            {/* Summary */}
            <Grid size={12}>
              <Card>
                <CardContent>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Box textAlign="center">
                        <Typography variant="h3" color="primary.main">
                          {familyData.totalFamilyMembers || 0}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          إجمالي أفراد العائلة
                        </Typography>
                      </Box>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Box textAlign="center">
                        <Typography variant="h3" color="success.main">
                          {familyData.eligibleMembersCount || 0}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          مؤهلون
                        </Typography>
                      </Box>
                    </Grid>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <Box textAlign="center">
                        <Typography variant="h3" color="error.main">
                          {(familyData.totalFamilyMembers || 0) - (familyData.eligibleMembersCount || 0)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          غير مؤهلين
                        </Typography>
                      </Box>
                    </Grid>
                  </Grid>
                </CardContent>
              </Card>
            </Grid>
          </>
        )}
      </Grid>
    </>
  );
};

export default EligibilityCheck;


