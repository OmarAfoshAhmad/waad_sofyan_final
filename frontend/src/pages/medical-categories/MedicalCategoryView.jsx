/**
 * Medical Category View Page - Phase D2.4 (Golden Reference Clone)
 * Cloned from Medical Services Golden Reference
 *
 * ⚠️ This is a REFERENCE implementation for all CRUD view pages.
 * Pattern: ModernPageHeader → MainCard → Read-only Sections
 *
 * Rules Applied:
 * 1. icon={Component} - NEVER JSX
 * 2. Arabic only - No English labels
 * 3. Defensive optional chaining
 * 4. Proper error states (403 صلاحيات, 404 غير موجود, 500 خطأ تقني)
 */

import { useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// MUI Components
import { Box, Button, Grid, Paper, Stack, Typography, Chip, Divider, Skeleton } from '@mui/material';

// MUI Icons - Always as Component, NEVER as JSX
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import CategoryIcon from '@mui/icons-material/Category';
import LockIcon from '@mui/icons-material/Lock';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import ModernEmptyState from 'components/tba/ModernEmptyState';

// Hooks
import { useMedicalCategoryDetails } from 'hooks/useMedicalCategories';

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Parse error response and return appropriate Arabic message
 */
const getErrorInfo = (error) => {
  const status = error?.response?.status || error?.status;

  if (status === 403) {
    return {
      type: 'permission',
      title: 'غير مصرح',
      message: 'ليس لديك صلاحية للوصول إلى هذا التصنيف',
      icon: LockIcon
    };
  }

  if (status === 404) {
    return {
      type: 'notfound',
      title: 'غير موجود',
      message: 'التصنيف المطلوب غير موجود',
      icon: ErrorOutlineIcon
    };
  }

  if (status >= 500) {
    return {
      type: 'server',
      title: 'خطأ تقني',
      message: 'حدث خطأ في الخادم. يرجى المحاولة لاحقاً',
      icon: ErrorOutlineIcon
    };
  }

  return {
    type: 'generic',
    title: 'خطأ',
    message: error?.message || 'فشل تحميل بيانات التصنيف',
    icon: ErrorOutlineIcon
  };
};

/**
 * Format date for Arabic display
 */
const formatDate = (dateString) => {
  if (!dateString) return '-';
  try {
    return new Date(dateString).toLocaleString('en-US');
  } catch {
    return '-';
  }
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const MedicalCategoryView = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  // ========================================
  // DATA FETCHING
  // ========================================

  const { data: category, loading, error } = useMedicalCategoryDetails(id);

  // ========================================
  // HANDLERS
  // ========================================

  const handleBack = useCallback(() => {
    navigate('/medical-categories');
  }, [navigate]);

  const handleEdit = useCallback(() => {
    navigate(`/medical-categories/edit/${id}`);
  }, [navigate, id]);

  // ========================================
  // RENDER - LOADING STATE
  // ========================================

  if (loading) {
    return (
      <Box>
        <ModernPageHeader
          title="عرض التصنيف الطبي"
          subtitle="تفاصيل التصنيف الطبي"
          icon={CategoryIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'عرض' }]}
        />
        <MainCard>
          <Stack spacing={3}>
            <Skeleton variant="rectangular" height={80} />
            <Skeleton variant="rectangular" height={80} />
            <Skeleton variant="rectangular" height={80} />
          </Stack>
        </MainCard>
      </Box>
    );
  }

  // ========================================
  // RENDER - ERROR STATE
  // ========================================

  if (error || !category) {
    const errorInfo = getErrorInfo(error);
    const ErrorIcon = errorInfo.icon;

    return (
      <Box>
        <ModernPageHeader
          title="عرض التصنيف الطبي"
          subtitle="تفاصيل التصنيف الطبي"
          icon={CategoryIcon}
          breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'عرض' }]}
        />
        <MainCard>
          <ModernEmptyState
            icon={ErrorIcon}
            title={errorInfo.title}
            description={errorInfo.message}
            action={
              <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={handleBack}>
                رجوع للقائمة
              </Button>
            }
          />
        </MainCard>
      </Box>
    );
  }

  // ========================================
  // RENDER - MAIN VIEW
  // ========================================

  return (
    <Box>
      {/* ====== PAGE HEADER ====== */}
      <ModernPageHeader
        title="عرض التصنيف الطبي"
        subtitle={category?.name || ''}
        icon={CategoryIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'التصنيفات الطبية', path: '/medical-categories' },
          { label: category?.code || 'عرض' }
        ]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={handleBack}>
              رجوع
            </Button>
            <Button variant="contained" startIcon={<EditIcon />} onClick={handleEdit}>
              تعديل
            </Button>
          </Stack>
        }
      />

      {/* ====== MAIN CARD ====== */}
      <MainCard>
        <Grid container spacing={3}>
          {/* ====== BASIC INFORMATION SECTION ====== */}
          <Grid size={12}>
            <Typography variant="h6" gutterBottom>
              المعلومات الأساسية
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          {/* Code */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                الرمز
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {category?.code || '-'}
              </Typography>
            </Paper>
          </Grid>

          {/* Parent Category */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                التصنيف الأب
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {category?.parentName || '-'}
                {category?.parentId && category?.parentId !== category?.parentName && (
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                    (#{category.parentId})
                  </Typography>
                )}
              </Typography>
            </Paper>
          </Grid>

          {/* Name */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                الاسم
              </Typography>
              <Typography variant="body1" fontWeight="medium">
                {category?.name || '-'}
              </Typography>
            </Paper>
          </Grid>

          {/* Status */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                الحالة
              </Typography>
              <Chip label={category?.active ? 'نشط' : 'غير نشط'} color={category?.active ? 'success' : 'default'} size="medium" />
            </Paper>
          </Grid>

          {/* Description (Not in contract strictly but usually good to keep if backend sends it) */}
          {category?.description && (
            <Grid size={12}>
              <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                  الوصف
                </Typography>
                <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                  {category.description}
                </Typography>
              </Paper>
            </Grid>
          )}

          {/* ====== METADATA SECTION ====== */}
          <Grid size={12}>
            <Typography variant="h6" gutterBottom sx={{ mt: '1.0rem' }}>
              معلومات النظام
            </Typography>
            <Divider sx={{ mb: '1.0rem' }} />
          </Grid>

          {/* Created At */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                تاريخ الإنشاء
              </Typography>
              <Typography variant="body1">{formatDate(category?.createdAt)}</Typography>
            </Paper>
          </Grid>

          {/* Updated At */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: '1.0rem' }}>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                آخر تحديث
              </Typography>
              <Typography variant="body1">{formatDate(category?.updatedAt)}</Typography>
            </Paper>
          </Grid>

          {/* Created By */}
          {category?.createdBy && (
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                  أنشئ بواسطة
                </Typography>
                <Typography variant="body1">{category.createdBy}</Typography>
              </Paper>
            </Grid>
          )}

          {/* Updated By */}
          {category?.updatedBy && (
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper variant="outlined" sx={{ p: '1.0rem' }}>
                <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                  آخر تحديث بواسطة
                </Typography>
                <Typography variant="body1">{category.updatedBy}</Typography>
              </Paper>
            </Grid>
          )}
        </Grid>
      </MainCard>
    </Box>
  );
};

export default MedicalCategoryView;
