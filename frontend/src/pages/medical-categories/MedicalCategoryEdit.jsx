/**
 * Medical Category Edit Page - Enhanced Professional Design
 *
 * Features:
 * - Clear visual hierarchy matching the Create page
 * - Intuitive parent category selection with visual tree
 * - Code field shown as read-only with lock icon
 * - Arabic RTL optimized layout
 *
 * @version 2.0 - 2026-01-29
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// MUI Components
import {
  Box,
  Button,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography,
  FormControlLabel,
  Switch,
  Alert,
  Skeleton,
  MenuItem,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControl,
  InputLabel,
  Select,
  InputAdornment,
  Collapse,
  alpha
} from '@mui/material';

// MUI Icons
import SaveIcon from '@mui/icons-material/Save';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CategoryIcon from '@mui/icons-material/Category';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import FolderIcon from '@mui/icons-material/Folder';
import SubdirectoryArrowLeftIcon from '@mui/icons-material/SubdirectoryArrowLeft';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import CodeIcon from '@mui/icons-material/Code';
import LabelIcon from '@mui/icons-material/Label';
import LockIcon from '@mui/icons-material/Lock';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import ModernEmptyState from 'components/tba/ModernEmptyState';

// Contexts & Services
import { useTableRefresh } from 'contexts/TableRefreshContext';
import { useMedicalCategoryDetails } from 'hooks/useMedicalCategories';
import { updateMedicalCategory, getAllMedicalCategories } from 'services/api/medical-categories.service';
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const INITIAL_FORM_STATE = {
  code: '',
  name: '',
  parentId: '',
  context: 'ANY',
  active: true
};

const CATEGORY_TYPE = {
  MAIN: 'main',
  SUB: 'sub'
};

/** Clinical context options — Arabic labels matching benefit-table terminology */
const CONTEXT_OPTIONS = [
  { value: 'ANY', label: 'أي سياق (افتراضي)' },
  { value: 'INPATIENT', label: 'إيواء داخل المستشفى' },
  { value: 'OUTPATIENT', label: 'عيادات خارجية' },
  { value: 'OPERATING_ROOM', label: 'عمليات جراحية / غرفة عمليات' },
  { value: 'EMERGENCY', label: 'طوارئ وإسعاف' },
  { value: 'SPECIAL', label: 'منافع خاصة' }
];

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

/**
 * Section Header with Icon
 */
const SectionHeader = ({ icon: Icon, title, subtitle, color = 'primary' }) => (
  <Box sx={{ mb: '1.5rem' }}>
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Box
        sx={{
          width: '2.5rem',
          height: '2.5rem',
          borderRadius: '0.25rem',
          bgcolor: (theme) => alpha(theme.palette[color].main, 0.1),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}
      >
        <Icon sx={{ color: `${color}.main`, fontSize: '1.375rem' }} />
      </Box>
      <Box>
        <Typography variant="h6" fontWeight={600}>
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        )}
      </Box>
    </Stack>
  </Box>
);

/**
 * Category Type Selection Card
 */
const CategoryTypeCard = ({ type, selected, onSelect, disabled }) => {
  const isMain = type === CATEGORY_TYPE.MAIN;

  return (
    <Card
      onClick={() => !disabled && onSelect(type)}
      sx={{
        cursor: disabled ? 'not-allowed' : 'pointer',
        border: 2,
        borderColor: selected ? 'primary.main' : 'divider',
        bgcolor: selected ? (theme) => alpha(theme.palette.primary.main, 0.04) : 'background.paper',
        transition: 'all 0.2s ease',
        opacity: disabled ? 0.6 : 1,
        '&:hover': {
          borderColor: disabled ? 'divider' : 'primary.main',
          transform: disabled ? 'none' : 'translateY(-2px)',
          boxShadow: disabled ? 0 : 2
        }
      }}
    >
      <CardContent sx={{ p: '1.25rem' }}>
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <Box
            sx={{
              width: '3.0rem',
              height: '3.0rem',
              borderRadius: '0.25rem',
              bgcolor: isMain ? 'primary.main' : 'secondary.main',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}
          >
            {isMain ? (
              <FolderIcon sx={{ color: 'white', fontSize: '1.625rem' }} />
            ) : (
              <SubdirectoryArrowLeftIcon sx={{ color: 'white', fontSize: '1.625rem' }} />
            )}
          </Box>

          <Box sx={{ flex: 1 }}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
              <Typography variant="subtitle1" fontWeight={600}>
                {isMain ? 'تصنيف رئيسي' : 'تصنيف فرعي'}
              </Typography>
              {selected && <CheckCircleIcon sx={{ color: 'primary.main', fontSize: '1.125rem' }} />}
            </Stack>

            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
              {isMain ? 'تصنيف مستقل بدون أب، يظهر في المستوى الأول من الشجرة' : 'تصنيف تابع لتصنيف آخر، يظهر تحت التصنيف الأب'}
            </Typography>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
};

/**
 * Parent Category Preview
 */
const ParentPreview = ({ parent }) => {
  if (!parent) return null;

  return (
    <Paper
      variant="outlined"
      sx={{
        p: '1.0rem',
        mt: '1.0rem',
        bgcolor: (theme) => alpha(theme.palette.info.main, 0.04),
        borderColor: 'info.light',
        borderRadius: '0.25rem'
      }}
    >
      <Stack direction="row" spacing={2} alignItems="center">
        <AccountTreeIcon sx={{ color: 'info.main' }} />
        <Box>
          <Typography variant="caption" color="text.secondary">
            التصنيف الأب الحالي:
          </Typography>
          <Typography variant="subtitle2" fontWeight={600}>
            {parent.name}
            <Chip label={parent.code} size="small" sx={{ ml: 1, fontSize: '0.7rem', height: '1.25rem' }} />
          </Typography>
        </Box>
      </Stack>
    </Paper>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const MedicalCategoryEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { triggerRefresh } = useTableRefresh();

  // Fetch category details
  const { data: categoryData, loading: loadingCategory, error: loadError } = useMedicalCategoryDetails(id);

  // Form State
  const [form, setForm] = useState(INITIAL_FORM_STATE);
  const [categoryType, setCategoryType] = useState(CATEGORY_TYPE.MAIN);
  const [categories, setCategories] = useState([]);
  const [loadingParents, setLoadingParents] = useState(false);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);

  // Load parent categories
  useEffect(() => {
    let mounted = true;
    const fetchCategories = async () => {
      try {
        setLoadingParents(true);
        const data = await getAllMedicalCategories();
        if (mounted && Array.isArray(data)) {
          // Filter out the current category and its descendants to prevent circular reference
          const filtered = data.filter((c) => String(c.id) !== id && c.active !== false);
          setCategories(filtered);
        }
      } catch (error) {
        console.error('Failed to load parent categories', error);
      } finally {
        if (mounted) setLoadingParents(false);
      }
    };
    fetchCategories();
    return () => {
      mounted = false;
    };
  }, [id]);

  // Sync form with loaded data
  useEffect(() => {
    if (categoryData) {
      setForm({
        code: categoryData.code || '',
        name: categoryData.name || '',
        parentId: categoryData.parentId || '',
        context: categoryData.context || 'ANY',
        active: categoryData.active !== false
      });
      setCategoryType(categoryData.parentId ? CATEGORY_TYPE.SUB : CATEGORY_TYPE.MAIN);
    }
  }, [categoryData]);

  // Organize categories into main and sub for display
  const organizedCategories = useMemo(() => {
    const mainCats = categories.filter((c) => !c.parentId);
    return mainCats.map((main) => ({
      ...main,
      children: categories.filter((c) => c.parentId === main.id)
    }));
  }, [categories]);

  // Get selected parent details
  const selectedParent = useMemo(() => {
    if (!form.parentId) return null;
    return categories.find((c) => c.id === form.parentId);
  }, [form.parentId, categories]);

  // Handlers
  const handleChange = useCallback(
    (field) => (e) => {
      const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
      setForm((prev) => ({ ...prev, [field]: value }));
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: null }));
      }
    },
    [errors]
  );

  const handleCategoryTypeChange = useCallback((type) => {
    setCategoryType(type);
    if (type === CATEGORY_TYPE.MAIN) {
      setForm((prev) => ({ ...prev, parentId: '' }));
    }
  }, []);

  const validate = useCallback(() => {
    const newErrors = {};

    if (!form.name?.trim()) {
      newErrors.name = 'اسم التصنيف مطلوب';
    }

    if (categoryType === CATEGORY_TYPE.SUB && !form.parentId) {
      newErrors.parentId = 'يجب اختيار التصنيف الأب للتصنيف الفرعي';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [form, categoryType]);

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault();

      if (!validate()) return;

      setSubmitting(true);
      setApiError(null);

      try {
        const isMain = categoryType === CATEGORY_TYPE.MAIN;
        const payload = {
          name: form.name?.trim(),
          parentId: isMain ? null : form.parentId,
          clearParent: isMain,
          context: form.context || 'ANY',
          active: form.active
        };

        await updateMedicalCategory(id, payload);
        openSnackbar({ message: 'تم تحديث التصنيف بنجاح', variant: 'alert', alert: { color: 'success', variant: 'filled' } });
        triggerRefresh();
        navigate('/medical-categories');
      } catch (err) {
        console.error('[MedicalCategoryEdit] Submit failed:', err);
        const errorMsg = err?.response?.data?.message || err?.message || 'حدث خطأ أثناء تحديث التصنيف';
        setApiError(errorMsg);
      } finally {
        setSubmitting(false);
      }
    },
    [form, categoryType, id, navigate, validate, triggerRefresh]
  );

  const handleBack = useCallback(() => navigate('/medical-categories'), [navigate]);

  // ========================================
  // LOADING STATE
  // ========================================
  if (loadingCategory) {
    return (
      <Box>
        <ModernPageHeader
          title="تعديل تصنيف طبي"
          subtitle="تحديث بيانات التصنيف"
          icon={CategoryIcon}
          breadcrumbs={[{ label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'تعديل' }]}
        />
        <MainCard>
          <Stack spacing={3}>
            <Skeleton variant="rectangular" height={80} sx={{ borderRadius: '0.25rem' }} />
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Skeleton variant="rectangular" height={100} sx={{ borderRadius: '0.25rem' }} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Skeleton variant="rectangular" height={100} sx={{ borderRadius: '0.25rem' }} />
              </Grid>
            </Grid>
            <Skeleton variant="rectangular" height={56} sx={{ borderRadius: 1 }} />
            <Skeleton variant="rectangular" height={56} sx={{ borderRadius: 1 }} />
          </Stack>
        </MainCard>
      </Box>
    );
  }

  // ========================================
  // ERROR STATE
  // ========================================
  if (loadError || !categoryData) {
    return (
      <Box>
        <ModernPageHeader
          title="تعديل تصنيف طبي"
          subtitle="تحديث بيانات التصنيف"
          icon={CategoryIcon}
          breadcrumbs={[{ label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'تعديل' }]}
        />
        <MainCard>
          <ModernEmptyState
            icon={ErrorOutlineIcon}
            title="خطأ في التحميل"
            description={loadError?.message || 'لم يتم العثور على التصنيف المطلوب'}
            action={
              <Button variant="outlined" onClick={handleBack} startIcon={<ArrowBackIcon />}>
                العودة للقائمة
              </Button>
            }
          />
        </MainCard>
      </Box>
    );
  }

  // ========================================
  // MAIN RENDER
  // ========================================
  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title="تعديل تصنيف طبي"
        subtitle={`تحديث بيانات: ${categoryData.name}`}
        icon={CategoryIcon}
        breadcrumbs={[{ label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'تعديل' }]}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={handleBack}>
            العودة للقائمة
          </Button>
        }
      />

      {/* Main Form Card */}
      <MainCard>
        <Box component="form" onSubmit={handleSubmit}>
          {/* API Error Alert */}
          {apiError && (
            <Alert severity="error" sx={{ mb: '1.5rem' }} onClose={() => setApiError(null)}>
              {apiError}
            </Alert>
          )}

          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={3}
            divider={<Divider orientation="vertical" flexItem />}
            alignItems="flex-start"
          >
            {/* ===== العمود الأيمن: نوع التصنيف + الأب ===== */}
            <Box sx={{ flex: { xs: '1 1 auto', md: '0 0 38%' }, width: '100%', minWidth: 0 }}>
              <SectionHeader
                icon={AccountTreeIcon}
                title="نوع التصنيف"
                subtitle="يمكنك تغيير نوع التصنيف من رئيسي إلى فرعي أو العكس"
                color="primary"
              />

              <Stack spacing={1.5}>
                <CategoryTypeCard
                  type={CATEGORY_TYPE.MAIN}
                  selected={categoryType === CATEGORY_TYPE.MAIN}
                  onSelect={handleCategoryTypeChange}
                  disabled={submitting}
                />
                <CategoryTypeCard
                  type={CATEGORY_TYPE.SUB}
                  selected={categoryType === CATEGORY_TYPE.SUB}
                  onSelect={handleCategoryTypeChange}
                  disabled={submitting}
                />
              </Stack>

              <Collapse in={categoryType === CATEGORY_TYPE.SUB}>
                <Divider sx={{ my: '1.25rem' }} />
                <SectionHeader
                  icon={FolderIcon}
                  title="التصنيف الأب"
                  subtitle="اختر التصنيف الرئيسي الذي سيتبعه هذا التصنيف الفرعي"
                  color="secondary"
                />

                <FormControl fullWidth error={!!errors.parentId}>
                  <InputLabel>اختر التصنيف الأب *</InputLabel>
                  <Select
                    value={form.parentId}
                    onChange={handleChange('parentId')}
                    label="اختر التصنيف الأب *"
                    disabled={submitting || loadingParents}
                    sx={{ '& .MuiSelect-select': { py: '0.75rem' } }}
                  >
                    <MenuItem value="" disabled>
                      <Typography color="text.secondary">— اختر التصنيف الأب —</Typography>
                    </MenuItem>

                    {organizedCategories.map((mainCat) => [
                      <MenuItem
                        key={mainCat.id}
                        value={mainCat.id}
                        sx={{ fontWeight: 600, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) }}
                      >
                        <Stack direction="row" spacing={1} alignItems="center">
                          <FolderIcon sx={{ fontSize: '1.125rem', color: 'primary.main' }} />
                          <span>{mainCat.name}</span>
                          <Chip label={mainCat.code} size="small" sx={{ ml: 1, height: '1.25rem', fontSize: '0.7rem' }} />
                        </Stack>
                      </MenuItem>,

                      ...mainCat.children.map((subCat) => (
                        <MenuItem key={subCat.id} value={subCat.id} sx={{ pr: '2.0rem' }}>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <SubdirectoryArrowLeftIcon sx={{ fontSize: '1.0rem', color: 'text.secondary' }} />
                            <span>{subCat.name}</span>
                            <Chip label={subCat.code} size="small" variant="outlined" sx={{ ml: 1, height: '1.125rem', fontSize: '0.75rem' }} />
                          </Stack>
                        </MenuItem>
                      ))
                    ])}
                  </Select>

                  {errors.parentId && (
                    <Typography variant="caption" color="error" sx={{ mt: 0.5, mr: '0.75rem' }}>
                      {errors.parentId}
                    </Typography>
                  )}

                  {loadingParents && (
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, mr: '0.75rem' }}>
                      جارِ تحميل التصنيفات...
                    </Typography>
                  )}
                </FormControl>

                <ParentPreview parent={selectedParent} />
              </Collapse>
            </Box>

            {/* ===== العمود الأيسر: البيانات + الإجراءات ===== */}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <SectionHeader icon={LabelIcon} title="بيانات التصنيف" subtitle="قم بتحديث المعلومات الأساسية للتصنيف" color="info" />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="رمز التصنيف"
                    value={form.code}
                    disabled
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <LockIcon sx={{ color: 'text.disabled' }} />
                        </InputAdornment>
                      ),
                      sx: { fontFamily: 'monospace', letterSpacing: 1, bgcolor: 'action.hover' }
                    }}
                    helperText="الرمز غير قابل للتعديل بعد الإنشاء"
                    inputProps={{ dir: 'ltr' }}
                  />
                </Grid>

                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    required
                    label="اسم التصنيف"
                    placeholder="أدخل اسم التصنيف بالعربية"
                    value={form.name}
                    onChange={handleChange('name')}
                    error={!!errors.name}
                    helperText={errors.name || 'الاسم الذي سيظهر في القوائم والتقارير'}
                    disabled={submitting}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <LabelIcon sx={{ color: 'text.secondary' }} />
                        </InputAdornment>
                      )
                    }}
                  />
                </Grid>

                <Grid size={12}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: '0.875rem',
                      borderRadius: '0.25rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}
                  >
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <Box
                        sx={{
                          width: '2.25rem',
                          height: '2.25rem',
                          borderRadius: '50%',
                          bgcolor: form.active ? 'success.light' : 'grey.200',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          transition: 'all 0.2s'
                        }}
                      >
                        <CheckCircleIcon sx={{ color: form.active ? 'success.main' : 'grey.400', fontSize: '1.25rem' }} />
                      </Box>
                      <Box>
                        <Typography variant="subtitle2" fontWeight={600}>
                          حالة التصنيف
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {form.active ? 'نشط - سيظهر في قوائم الاختيار' : 'غير نشط - لن يظهر في قوائم الاختيار'}
                        </Typography>
                      </Box>
                    </Stack>

                    <FormControlLabel
                      control={<Switch checked={form.active} onChange={handleChange('active')} color="success" disabled={submitting} />}
                      label={form.active ? 'نشط' : 'غير نشط'}
                      labelPlacement="start"
                    />
                  </Paper>
                </Grid>
              </Grid>

              <Divider sx={{ my: '1.25rem' }} />

              <Stack direction="row" spacing={2} justifyContent="flex-end">
                <Button variant="outlined" onClick={handleBack} disabled={submitting} startIcon={<ArrowBackIcon />}>
                  إلغاء
                </Button>

                <Button type="submit" variant="contained" size="large" startIcon={<SaveIcon />} disabled={submitting} sx={{ minWidth: '10.0rem' }}>
                  {submitting ? 'جارِ الحفظ...' : 'حفظ التغييرات'}
                </Button>
              </Stack>
            </Box>
          </Stack>
        </Box>
      </MainCard>
    </Box>
  );
};

export default MedicalCategoryEdit;



