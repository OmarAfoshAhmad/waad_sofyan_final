import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Grid,
  Typography,
  Chip,
  Stack,
  Alert,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Divider,
  Tooltip
} from '@mui/material';
import {
  Edit as EditIcon,
  ArrowBack as ArrowBackIcon,
  LocalHospital as LocalHospitalIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Person as PersonIcon,
  Business as BusinessIcon,
  CalendarMonth as CalendarMonthIcon,
  MedicalServices as MedicalServicesIcon,
  Notes as NotesIcon,
  Info as InfoIcon,
  Assignment as AssignmentIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useVisitDetails } from 'hooks/useVisits';

// Insurance UX Components - Phase B3
import { NetworkBadge, CardStatusBadge } from 'components/insurance';

// ============ VISIT CONFIGURATION ============
// Visit Type Labels (Arabic) - Synced with Backend VisitType Enum
const VISIT_TYPE_LABELS_AR = {
  EMERGENCY: 'طوارئ',
  OUTPATIENT: 'عيادة خارجية',
  INPATIENT: 'إقامة داخلية',
  ROUTINE: 'روتينية',
  FOLLOW_UP: 'متابعة',
  PREVENTIVE: 'وقائية',
  SPECIALIZED: 'تخصصية',
  HOME_CARE: 'رعاية منزلية',
  TELECONSULTATION: 'استشارة عن بُعد',
  DAY_SURGERY: 'جراحة يومية'
};

// Status Labels (Arabic)
const STATUS_LABELS_AR = {
  ACTIVE: 'نشطة',
  INACTIVE: 'غير نشطة',
  COMPLETED: 'مكتملة',
  CANCELLED: 'ملغاة'
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

/**
 * Visit View Page
 * Read-only detailed view of a visit
 */
const VisitView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: visit, loading, error } = useVisitDetails(id);

  const breadcrumbs = [{ title: 'الزيارات', path: '/visits' }, { title: 'عرض الزيارة' }];

  if (loading) {
    return (
      <>
        <ModernPageHeader title="عرض الزيارة" subtitle="تحميل بيانات الزيارة..." icon={LocalHospitalIcon} breadcrumbs={breadcrumbs} />
        <MainCard>
          <Grid container spacing={3}>
            {[1, 2, 3, 4].map((i) => (
              <Grid key={i} size={{ xs: 12, md: 6 }}>
                <Skeleton variant="rectangular" height={120} />
              </Grid>
            ))}
          </Grid>
        </MainCard>
      </>
    );
  }

  if (error || !visit) {
    return (
      <>
        <ModernPageHeader title="خطأ" subtitle="فشل تحميل بيانات الزيارة" icon={LocalHospitalIcon} breadcrumbs={breadcrumbs} />
        <MainCard>
          <Stack spacing={3} alignItems="center" sx={{ py: '2.0rem' }}>
            <LocalHospitalIcon sx={{ fontSize: '3.0rem', color: '#ff4d4f' }} />
            <Typography variant="h5" color="error">
              الزيارة غير موجودة
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {error?.message ?? 'تأكد من صحة الرابط أو أن الزيارة لم يتم حذفها'}
            </Typography>
            <Button variant="contained" startIcon={<ArrowBackIcon />} onClick={() => navigate('/visits')}>
              العودة إلى القائمة
            </Button>
          </Stack>
        </MainCard>
      </>
    );
  }

  // Derive values defensively
  const visitStatus = getVisitStatus(visit);
  const networkTier = getNetworkTier(visit?.provider);
  const memberName = visit?.member?.fullName ?? '—';
  const providerName = visit?.provider?.name ?? '—';
  const services = Array.isArray(visit?.services) ? visit.services : [];

  // Enhanced InfoRow with icon support and defensive coding
  const InfoRow = ({ label, value, icon: Icon }) => (
    <Box sx={{ mb: '1.0rem' }}>
      <Stack direction="row" spacing={1} alignItems="center">
        {Icon && <Icon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />}
        <Typography variant="caption" color="text.secondary" display="block">
          {label ?? '—'}
        </Typography>
      </Stack>
      <Typography variant="body1" sx={{ mt: 0.5 }}>
        {value ?? '—'}
      </Typography>
    </Box>
  );

  return (
    <>
      <ModernPageHeader
        title={
          <Stack direction="row" spacing={2} alignItems="center">
            <LocalHospitalIcon sx={{ fontSize: '1.75rem' }} />
            <Box>
              <Typography variant="h5">زيارة #{visit?.id ?? id}</Typography>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }}>
                {/* Visit Type */}
                {visit?.visitType && (
                  <Chip label={VISIT_TYPE_LABELS_AR[visit.visitType] ?? visit.visitType} size="small" color="primary" variant="outlined" />
                )}
                {/* Status Badge */}
                <CardStatusBadge
                  status={visitStatus}
                  customLabel={STATUS_LABELS_AR[visitStatus] ?? 'غير محدد'}
                  size="small"
                  variant="chip"
                />
                {/* Pre-Approval hint if exists */}
                {(visit?.preApprovalId ?? visit?.preApproval?.id) && (
                  <Tooltip title="مرتبطة بموافقة مسبقة">
                    <Chip
                      icon={<AssignmentIcon sx={{ fontSize: '0.875rem' }} />}
                      label={`موافقة #${visit?.preApprovalId ?? visit?.preApproval?.id}`}
                      size="small"
                      color="info"
                      variant="outlined"
                    />
                  </Tooltip>
                )}
              </Stack>
            </Box>
          </Stack>
        }
        subtitle={`${memberName} - ${visit?.visitDate ? new Date(visit.visitDate).toLocaleDateString('en-GB') : '—'}`}
        icon={LocalHospitalIcon}
        breadcrumbs={breadcrumbs}
        actions={
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/visits')}>
              رجوع
            </Button>
            <Button variant="contained" startIcon={<EditIcon />} onClick={() => navigate(`/visits/edit/${id}`)}>
              تعديل
            </Button>
          </Stack>
        }
      />

      <Grid container spacing={3}>
        {/* Visit Information */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <CalendarMonthIcon color="primary" />
                <span>معلومات الزيارة</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <InfoRow
              label="تاريخ الزيارة"
              value={visit?.visitDate ? new Date(visit.visitDate).toLocaleDateString('en-GB') : '—'}
              icon={CalendarMonthIcon}
            />
            {visit?.visitType && <InfoRow label="نوع الزيارة" value={VISIT_TYPE_LABELS_AR[visit.visitType] ?? visit.visitType} />}
            <InfoRow label="معرف الزيارة" value={visit?.id ?? '—'} />
          </MainCard>
        </Grid>

        {/* Member Information */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <PersonIcon color="primary" />
                <span>المؤمَّن عليه</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <InfoRow label="المؤمَّن عليه" value={memberName} icon={PersonIcon} />
            <InfoRow label="معرف المؤمَّن عليه" value={visit?.memberId ?? visit?.member?.id ?? '—'} />
            {visit?.member?.membershipNumber && <InfoRow label="رقم العضوية" value={visit.member.membershipNumber} />}
          </MainCard>
        </Grid>

        {/* Provider Information */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <BusinessIcon color="primary" />
                <span>مقدم الخدمة الصحية</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <InfoRow label="الاسم" value={visit?.provider?.name ?? '—'} icon={BusinessIcon} />
            <InfoRow label="معرف المقدم" value={visit?.providerId ?? visit?.provider?.id ?? '—'} />
            {/* Network Status */}
            <Box sx={{ mb: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" display="block">
                حالة الشبكة
              </Typography>
              <Box sx={{ mt: 0.5 }}>
                {networkTier ? (
                  <NetworkBadge networkTier={networkTier} showLabel={true} size="small" language="ar" />
                ) : (
                  <Typography variant="body1">—</Typography>
                )}
              </Box>
            </Box>
          </MainCard>
        </Grid>

        {/* Services */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <MedicalServicesIcon color="primary" />
                <span>الخدمات الطبية</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
            secondary={
              <Chip
                label={`${services.length} خدمة`}
                color="primary"
                size="small"
                sx={{ backgroundColor: 'primary.lighter', color: 'primary.main' }}
              />
            }
          >
            {services.length > 0 ? (
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: 'grey.50' }}>
                      <TableCell sx={{ fontWeight: 600 }}>الرمز</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>الاسم</TableCell>
                      <TableCell align="center" sx={{ fontWeight: 600 }}>
                        السعر
                      </TableCell>
                      <TableCell align="center" sx={{ fontWeight: 600 }}>
                        يتطلب موافقة
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {services.map((service, idx) => (
                      <TableRow key={service?.id ?? idx}>
                        <TableCell>{service?.code ?? '—'}</TableCell>
                        <TableCell>{service?.name ?? '—'}</TableCell>
                        <TableCell align="center">{typeof service?.price === 'number' ? `${service.price.toFixed(2)} د.ل` : '—'}</TableCell>
                        <TableCell align="center">
                          {service?.requiresApproval ? (
                            <CheckCircleIcon color="success" fontSize="small" />
                          ) : (
                            <CancelIcon color="disabled" fontSize="small" />
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Alert severity="info">لا توجد خدمات مسجلة لهذه الزيارة</Alert>
            )}
          </MainCard>
        </Grid>

        {/* Notes & Diagnosis */}
        <Grid size={12}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <NotesIcon color="primary" />
                <span>الملاحظات والتشخيص</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow
                  label="الملاحظات"
                  icon={NotesIcon}
                  value={
                    visit?.notes ? (
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                        {visit.notes}
                      </Typography>
                    ) : (
                      '—'
                    )
                  }
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <InfoRow
                  label="التشخيص"
                  value={
                    visit?.diagnosis ? (
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                        {visit.diagnosis}
                      </Typography>
                    ) : (
                      '—'
                    )
                  }
                />
              </Grid>
            </Grid>
          </MainCard>
        </Grid>

        {/* Status */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <InfoIcon color="primary" />
                <span>حالة الزيارة</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <Stack direction="row" spacing={2} alignItems="center">
              <CardStatusBadge
                status={visitStatus}
                customLabel={STATUS_LABELS_AR[visitStatus] ?? 'غير محدد'}
                size="medium"
                variant="chip"
              />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  {STATUS_LABELS_AR[visitStatus] ?? 'غير محدد'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {visitStatus === 'ACTIVE' ? 'هذه الزيارة مفعلة في النظام' : 'هذه الزيارة غير مفعلة'}
                </Typography>
              </Box>
            </Stack>
          </MainCard>
        </Grid>

        {/* System Metadata */}
        <Grid size={{ xs: 12, md: 6 }}>
          <MainCard
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <InfoIcon color="primary" />
                <span>معلومات النظام</span>
              </Stack>
            }
            contentSX={{ pt: '1.0rem' }}
          >
            <InfoRow label="المعرف" value={visit?.id ?? '—'} />
            <InfoRow
              label="تاريخ الإنشاء"
              value={
                visit?.createdAt ? new Date(visit.createdAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
              }
            />
            <InfoRow
              label="آخر تحديث"
              value={
                visit?.updatedAt ? new Date(visit.updatedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
              }
            />
          </MainCard>
        </Grid>
      </Grid>
    </>
  );
};

export default VisitView;
