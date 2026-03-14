/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 CLAIM REVIEW PANEL - Left Panel (60%)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * ✓ Member Card
 * ✓ Visit Summary
 * ✓ Medical Context (Diagnosis, Services)
 * ✓ Financial Snapshot
 * ✓ Warnings/Alerts
 * ✓ Read-only for reviewers
 *
 * Used for:
 * - Claims Review
 * - Pre-Approval Review
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

import {
  Box,
  Card,
  CardContent,
  Typography,
  Stack,
  Grid,
  Chip,
  Divider,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableRow
} from '@mui/material';
import {
  Person as PersonIcon,
  LocalHospital as HospitalIcon,
  CalendarToday as CalendarIcon,
  MedicalInformation as MedicalIcon,
  AttachMoney as MoneyIcon,
  Warning as WarningIcon,
  CheckCircle as CheckIcon,
  Error as ErrorIcon
} from '@mui/icons-material';
import { MEDICAL_THEME } from '../../theme/medical-theme';
import { format } from 'date-fns';
import { ar } from 'date-fns/locale';

const ClaimReviewPanel = ({ claim, type = 'claim' }) => {
  const isPreAuth = type === 'preauth';

  // Format currency
  const formatCurrency = (amount) => {
    if (!amount && amount !== 0) return '-';
    return new Intl.NumberFormat('ar-SA', {
      style: 'currency',
      currency: 'SAR',
      minimumFractionDigits: 2
    }).format(amount);
  };

  // Format date
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    try {
      return format(new Date(dateString), 'dd MMMM yyyy', { locale: ar });
    } catch {
      return dateString;
    }
  };

  // Get status color
  const getStatusColor = (status) => {
    const colors = {
      APPROVED: MEDICAL_THEME.colors.status.approved.main,
      REJECTED: MEDICAL_THEME.colors.status.rejected.main,
      PENDING: MEDICAL_THEME.colors.status.pending.main,
      HOLD: MEDICAL_THEME.colors.status.hold.main,
      PROCESSING: MEDICAL_THEME.colors.status.processing.main,
      APPROVAL_IN_PROGRESS: MEDICAL_THEME.colors.status.processing.main, // Same as PROCESSING
      UNDER_REVIEW: MEDICAL_THEME.colors.status.pending.main,
      SUBMITTED: MEDICAL_THEME.colors.status.pending.main
    };
    return colors[status] || MEDICAL_THEME.colors.neutral.medium;
  };

  // Get status label
  const getStatusLabel = (status) => {
    const labels = {
      APPROVED: 'موافق عليه',
      REJECTED: 'مرفوض',
      PENDING: 'معلق',
      APPROVAL_IN_PROGRESS: 'جاري المعالجة...',
      UNDER_REVIEW: 'قيد المراجعة',
      SUBMITTED: 'مقدم',
      HOLD: 'معلق',
      PROCESSING: 'جاري المعالجة'
    };
    return labels[status] || status;
  };

  if (!claim) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100%',
          padding: MEDICAL_THEME.spacing.xl
        }}
      >
        <Typography variant="body1" color="text.secondary">
          اختر {isPreAuth ? 'موافقة مسبقة' : 'مطالبة'} للمراجعة
        </Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        height: '100%',
        overflow: 'auto',
        padding: MEDICAL_THEME.spacing.lg,
        background: MEDICAL_THEME.colors.background.default
      }}
    >
      <Stack spacing={3}>
        {/* ════════════════════════════════════════════════════════════ */}
        {/* APPROVAL_IN_PROGRESS Alert */}
        {/* ════════════════════════════════════════════════════════════ */}
        {claim.status === 'APPROVAL_IN_PROGRESS' && (
          <Alert
            severity="info"
            icon={<HospitalIcon />}
            sx={{
              background: `linear-gradient(135deg, ${MEDICAL_THEME.colors.status.processing.main}15 0%, ${MEDICAL_THEME.colors.status.processing.main}05 100%)`,
              border: `1px solid ${MEDICAL_THEME.colors.status.processing.main}`,
              '& .MuiAlert-icon': {
                color: MEDICAL_THEME.colors.status.processing.main
              }
            }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              🔄 جاري معالجة الموافقة...
            </Typography>
            <Typography variant="caption" color="text.secondary">
              يتم حالياً إجراء الحسابات المالية والتحقق من التغطية. سيتم تحديث الحالة تلقائياً عند الانتهاء.
            </Typography>
          </Alert>
        )}

        {/* ════════════════════════════════════════════════════════════ */}
        {/* Member Card */}
        {/* ════════════════════════════════════════════════════════════ */}
        <Card elevation={0} sx={{ border: `1px solid ${MEDICAL_THEME.colors.border.light}` }}>
          <CardContent>
            <Stack direction="row" spacing={2} alignItems="center" mb={2}>
              <PersonIcon sx={{ color: MEDICAL_THEME.colors.primary.main, fontSize: '2.0rem' }} />
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {claim.memberName || claim.member?.name || 'غير محدد'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  رقم البطاقة: {claim.memberId || claim.member?.cardNumber || '-'}
                </Typography>
              </Box>
              <Chip
                label={getStatusLabel(claim.status)}
                sx={{
                  background: getStatusColor(claim.status),
                  color: 'white',
                  fontWeight: 600
                }}
              />
            </Stack>

            <Grid container spacing={2}>
              <Grid size={6}>
                <Typography variant="caption" color="text.secondary">
                  الوثيقة
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {claim.policyNumber || claim.member?.policyNumber || '-'}
                </Typography>
              </Grid>
              <Grid size={6}>
                <Typography variant="caption" color="text.secondary">
                  التغطية
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {claim.coverageType || claim.member?.coverageType || '-'}
                </Typography>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* ════════════════════════════════════════════════════════════ */}
        {/* Visit Summary */}
        {/* ════════════════════════════════════════════════════════════ */}
        <Card elevation={0} sx={{ border: `1px solid ${MEDICAL_THEME.colors.border.light}` }}>
          <CardContent>
            <Stack direction="row" spacing={1} alignItems="center" mb={2}>
              <HospitalIcon sx={{ color: MEDICAL_THEME.colors.secondary.main }} />
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                ملخص الزيارة
              </Typography>
            </Stack>

            <Table size="small">
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, width: '40%' }}>رقم الزيارة</TableCell>
                  <TableCell>{claim.visitId || claim.visitNumber || '-'}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>تاريخ الزيارة</TableCell>
                  <TableCell>{formatDate(claim.visitDate || claim.serviceDate)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>مقدم الخدمة</TableCell>
                  <TableCell>{claim.providerName || claim.provider?.name || '-'}</TableCell>
                </TableRow>
                {!isPreAuth && claim.claimNumber && (
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>رقم المطالبة</TableCell>
                    <TableCell>{claim.claimNumber}</TableCell>
                  </TableRow>
                )}
                {isPreAuth && claim.preAuthNumber && (
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>رقم الموافقة</TableCell>
                    <TableCell>{claim.preAuthNumber}</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* ════════════════════════════════════════════════════════════ */}
        {/* Medical Context */}
        {/* ════════════════════════════════════════════════════════════ */}
        <Card elevation={0} sx={{ border: `1px solid ${MEDICAL_THEME.colors.border.light}` }}>
          <CardContent>
            <Stack direction="row" spacing={1} alignItems="center" mb={2}>
              <MedicalIcon sx={{ color: MEDICAL_THEME.colors.primary.main }} />
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                السياق الطبي
              </Typography>
            </Stack>

            {/* Diagnosis */}
            <Box mb={2}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                التشخيص
              </Typography>
              {claim.diagnosis ? (
                <Chip
                  label={`${claim.diagnosis.code || ''} - ${claim.diagnosis.description || claim.diagnosis}`}
                  sx={{
                    background: MEDICAL_THEME.colors.background.selected,
                    fontWeight: 500
                  }}
                />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  غير محدد
                </Typography>
              )}
            </Box>

            {/* Medical Category */}
            {claim.medicalCategory && (
              <Box mb={2}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  الفئة الطبية
                </Typography>
                <Chip label={claim.medicalCategory} size="small" sx={{ background: MEDICAL_THEME.colors.background.hover }} />
              </Box>
            )}

            {/* Medical Services */}
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                الخدمات الطبية ({claim.services?.length || 0})
              </Typography>
              {claim.services && claim.services.length > 0 ? (
                <Table size="small">
                  <TableBody>
                    {claim.services.map((service, index) => (
                      <TableRow key={index}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {service.name || service.serviceName}
                          </Typography>
                          {service.code && (
                            <Typography variant="caption" color="text.secondary">
                              الرمز: {service.code}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">{formatCurrency(service.amount || service.price)}</Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  لا توجد خدمات محددة
                </Typography>
              )}
            </Box>
          </CardContent>
        </Card>

        {/* ════════════════════════════════════════════════════════════ */}
        {/* Financial Snapshot (Claims only) */}
        {/* ════════════════════════════════════════════════════════════ */}
        {!isPreAuth && (
          <Card elevation={0} sx={{ border: `1px solid ${MEDICAL_THEME.colors.border.light}` }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                <MoneyIcon sx={{ color: MEDICAL_THEME.colors.status.pending.main }} />
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  الملخص المالي
                </Typography>
              </Stack>

              <Grid container spacing={2}>
                <Grid size={4}>
                  <Box
                    sx={{
                      textAlign: 'center',
                      padding: MEDICAL_THEME.spacing.md,
                      background: MEDICAL_THEME.colors.background.sidebar,
                      borderRadius: MEDICAL_THEME.radius.base
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      المبلغ المطلوب
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: MEDICAL_THEME.colors.primary.main }}>
                      {formatCurrency(claim.totalAmount || claim.requestedAmount)}
                    </Typography>
                  </Box>
                </Grid>

                <Grid size={4}>
                  <Box
                    sx={{
                      textAlign: 'center',
                      padding: MEDICAL_THEME.spacing.md,
                      background: MEDICAL_THEME.colors.status.approved.bg,
                      borderRadius: MEDICAL_THEME.radius.base
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      المبلغ المعتمد
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: MEDICAL_THEME.colors.status.approved.main }}>
                      {formatCurrency(claim.approvedAmount || 0)}
                    </Typography>
                  </Box>
                </Grid>

                <Grid size={4}>
                  <Box
                    sx={{
                      textAlign: 'center',
                      padding: MEDICAL_THEME.spacing.md,
                      background: MEDICAL_THEME.colors.status.pending.bg,
                      borderRadius: MEDICAL_THEME.radius.base
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      حصة المريض
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: MEDICAL_THEME.colors.status.pending.main }}>
                      {formatCurrency(claim.patientShare || 0)}
                    </Typography>
                  </Box>
                </Grid>
              </Grid>

              {claim.netPayable !== undefined && (
                <Box
                  sx={{
                    mt: '1.0rem',
                    padding: MEDICAL_THEME.spacing.md,
                    background: MEDICAL_THEME.colors.background.selected,
                    borderRadius: MEDICAL_THEME.radius.base,
                    textAlign: 'center'
                  }}
                >
                  <Typography variant="caption" color="text.secondary">
                    صافي المبلغ المستحق
                  </Typography>
                  <Typography variant="h5" sx={{ fontWeight: 700, color: MEDICAL_THEME.colors.primary.dark }}>
                    {formatCurrency(claim.netPayable)}
                  </Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        )}

        {/* ════════════════════════════════════════════════════════════ */}
        {/* Warnings/Alerts */}
        {/* ════════════════════════════════════════════════════════════ */}
        {claim.warnings && claim.warnings.length > 0 && (
          <Card elevation={0} sx={{ border: `2px solid ${MEDICAL_THEME.colors.alert.warning}` }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                <WarningIcon sx={{ color: MEDICAL_THEME.colors.alert.warning }} />
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  تنبيهات
                </Typography>
              </Stack>

              <Stack spacing={1}>
                {claim.warnings.map((warning, index) => (
                  <Alert key={index} severity="warning" sx={{ fontWeight: 500 }}>
                    {warning}
                  </Alert>
                ))}
              </Stack>
            </CardContent>
          </Card>
        )}

        {/* Notes/Comments */}
        {claim.notes && (
          <Card elevation={0} sx={{ border: `1px solid ${MEDICAL_THEME.colors.border.light}` }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                ملاحظات
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {claim.notes}
              </Typography>
            </CardContent>
          </Card>
        )}
      </Stack>
    </Box>
  );
};

export default ClaimReviewPanel;
