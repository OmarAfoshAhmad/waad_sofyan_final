import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Grid,
  Typography,
  Chip,
  Stack,
  Alert,
  Skeleton,
  Paper,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow
} from '@mui/material';
import { Edit as EditIcon, ArrowBack as ArrowBackIcon, CardGiftcard as CardGiftcardIcon } from '@mui/icons-material';

import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useBenefitPackageDetails } from 'hooks/useBenefitPackages';

/**
 * Benefit Package View Page
 * Displays read-only details of a benefit package
 */
const BenefitPackageView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: pkg, loading, error } = useBenefitPackageDetails(id);

  const handleEdit = () => {
    navigate(`/benefit-packages/edit/${id}`);
  };

  const handleBack = () => {
    navigate('/benefit-packages');
  };

  const breadcrumbs = [{ title: 'باقات المنافع', path: '/benefit-packages' }, { title: 'تفاصيل الباقة' }];

  if (loading) {
    return (
      <>
        <ModernPageHeader title="تفاصيل الباقة" subtitle="تحميل البيانات..." icon={CardGiftcardIcon} breadcrumbs={breadcrumbs} />
        <MainCard>
          <Grid container spacing={3}>
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <Grid key={i} size={{ xs: 12, md: 6 }}>
                <Skeleton variant="rectangular" height={60} />
              </Grid>
            ))}
          </Grid>
        </MainCard>
      </>
    );
  }

  if (error || !pkg) {
    return (
      <>
        <ModernPageHeader title="خطأ" subtitle="فشل تحميل بيانات الباقة" icon={CardGiftcardIcon} breadcrumbs={breadcrumbs} />
        <MainCard>
          <Alert severity="error">
            {error?.message || 'لم يتم العثور على الباقة'}
            <Button onClick={handleBack} sx={{ mt: '1.0rem' }}>
              العودة إلى القائمة
            </Button>
          </Alert>
        </MainCard>
      </>
    );
  }

  return (
    <>
      <ModernPageHeader
        title="تفاصيل باقة المنافع"
        subtitle={pkg.name}
        icon={CardGiftcardIcon}
        breadcrumbs={breadcrumbs}
        actions={
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={handleBack}>
              رجوع
            </Button>
            <Button variant="contained" startIcon={<EditIcon />} onClick={handleEdit}>
              تعديل
            </Button>
          </Stack>
        }
      />

      <MainCard>
        <Grid container spacing={3}>
          {/* Basic Information */}
          <Grid size={12}>
            <Typography variant="h5" gutterBottom>
              المعلومات الأساسية
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                الكود
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {pkg.code || '-'}
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                الاسم
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {pkg.name || '-'}
              </Typography>
            </Paper>
          </Grid>

          <Grid size={12}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                الوصف
              </Typography>
              <Typography variant="body1">{pkg.description || '-'}</Typography>
            </Paper>
          </Grid>

          {/* Coverage & Validity */}
          <Grid size={12}>
            <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
              التغطية والصلاحية
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                حد التغطية
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {pkg.coverageLimit ? `${pkg.coverageLimit.toFixed(2)} LYD` : '-'}
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                صلاحية الباقة
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {pkg.validityDays ? `${pkg.validityDays} يوم` : '-'}
              </Typography>
            </Paper>
          </Grid>

          {/* Medical Packages */}
          <Grid size={12}>
            <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
              الباقات الطبية المشمولة
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          <Grid size={12}>
            {Array.isArray(pkg.medicalPackages) && pkg.medicalPackages.length > 0 ? (
              <TableContainer component={Paper} variant="outlined">
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>الكود</TableCell>
                      <TableCell>الاسم</TableCell>
                      <TableCell align="center">عدد الخدمات</TableCell>
                      <TableCell align="right">السعر</TableCell>
                      <TableCell align="center">الحالة</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {pkg.medicalPackages.map((medicalPkg) => (
                      <TableRow key={medicalPkg.id}>
                        <TableCell>{medicalPkg.code}</TableCell>
                        <TableCell>{medicalPkg.name || '-'}</TableCell>
                        <TableCell align="center">
                          <Chip label={medicalPkg.services?.length || 0} size="small" color="primary" variant="outlined" />
                        </TableCell>
                        <TableCell align="right">{medicalPkg.priceLyd ? `${medicalPkg.priceLyd.toFixed(2)}` : '-'}</TableCell>
                        <TableCell align="center">
                          <Chip
                            label={medicalPkg.active ? 'نشط' : 'غير نشط'}
                            color={medicalPkg.active ? 'success' : 'default'}
                            size="small"
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Alert severity="info">لا توجد باقات طبية مشمولة في هذه الباقة</Alert>
            )}
          </Grid>

          {/* Status */}
          <Grid size={12}>
            <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
              الحالة
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                حالة الباقة
              </Typography>
              <Box sx={{ mt: 1 }}>
                <Chip label={pkg.active ? 'نشط' : 'غير نشط'} color={pkg.active ? 'success' : 'default'} />
              </Box>
            </Paper>
          </Grid>

          {/* System Metadata */}
          <Grid size={12}>
            <Typography variant="h5" gutterBottom sx={{ mt: '1.0rem' }}>
              معلومات النظام
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                تاريخ الإنشاء
              </Typography>
              <Typography variant="body1">{pkg.createdAt ? new Date(pkg.createdAt).toLocaleString('en-US') : '-'}</Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                تاريخ آخر تحديث
              </Typography>
              <Typography variant="body1">{pkg.updatedAt ? new Date(pkg.updatedAt).toLocaleString('en-US') : '-'}</Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                أنشئ بواسطة
              </Typography>
              <Typography variant="body1">{pkg.createdBy || '-'}</Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary">
                آخر تحديث بواسطة
              </Typography>
              <Typography variant="body1">{pkg.updatedBy || '-'}</Typography>
            </Paper>
          </Grid>
        </Grid>
      </MainCard>
    </>
  );
};

export default BenefitPackageView;
