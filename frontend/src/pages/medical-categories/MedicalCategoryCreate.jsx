/**
 * Medical Category Create Page - Enhanced Professional Design
 *
 * Features:
 * - Clear separation between main category and sub-category creation
 * - Visual hierarchy with cards and icons
 * - Intuitive parent category selection with visual tree
 * - Arabic RTL optimized layout
 *
 * @version 2.0 - 2026-01-29
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

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

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// Contexts & Services
import { useTableRefresh } from 'contexts/TableRefreshContext';
import { createMedicalCategory, getAllMedicalCategories } from 'services/api/medical-categories.service';
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const INITIAL_FORM_STATE = {
  code: '',
  name: '',
  parentId: '',
  multiParentIds: [],
  context: 'ANY',
  active: true
};

const CATEGORY_TYPE = {
  MAIN: 'main',
  SUB: 'sub'
};

/** Clinical context options — Arabic labels matching benefit-table terminology */
const CONTEXT_OPTIONS = [
  { value: 'ANY', label: 'أي سياق (افتراضي)', color: 'default' },
  { value: 'INPATIENT', label: 'إيواء داخل المستشفى', color: 'primary' },
  { value: 'OUTPATIENT', label: 'عيادات خارجية', color: 'success' },
  { value: 'OPERATING_ROOM', label: 'عمليات جراحية / غرفة عمليات', color: 'warning' },
  { value: 'EMERGENCY', label: 'طوارئ وإسعاف', color: 'error' },
  { value: 'SPECIAL', label: 'منافع خاصة', color: 'secondary' }
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
            سيتم إضافته تحت:
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

const MedicalCategoryCreate = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { triggerRefresh } = useTableRefresh();

  // Form State
  const [form, setForm] = useState(INITIAL_FORM_STATE);
  const [categoryType, setCategoryType] = useState(CATEGORY_TYPE.MAIN);
  const [categories, setCategories] = useState([]);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState(null);
  const [parentSelectOpen, setParentSelectOpen] = useState(false);

  // Load parent categories
  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const data = await getAllMedicalCategories();
        if (Array.isArray(data)) {
          // Filter only active categories for parent selection
          setCategories(data.filter((c) => c.active !== false));
        }
      } catch (error) {
        console.error('Failed to load parent categories', error);
      }
    };
    fetchCategories();
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const parentIdParam = params.get('parentId');
    if (!parentIdParam) return;

    const parentId = Number(parentIdParam);
    if (!Number.isFinite(parentId) || parentId <= 0) return;

    setCategoryType(CATEGORY_TYPE.SUB);
    setForm((prev) => ({ ...prev, parentId, multiParentIds: [parentId] }));
  }, [location.search]);

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

  // Auto-generate preview code (actual code is generated by backend)
  const autoCode = useMemo(() => {
    const regex = /^CAT(\d+)$/i;
    const maxSeq = categories.reduce((max, c) => {
      const match = regex.exec((c.code || '').trim());
      if (!match) return max;
      const current = Number(match[1]);
      return Number.isFinite(current) ? Math.max(max, current) : max;
    }, 0);

    return `CAT${String(maxSeq + 1).padStart(3, '0')}`;
  }, [categories]);

  // Handlers
  const handleChange = useCallback(
    (field) => (e) => {
      const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
      setForm((prev) => {
        const next = { ...prev, [field]: value };

        // Keep parentId in sync with multi-select parent picker
        if (field === 'multiParentIds') {
          const selectedIds = Array.isArray(value) ? value : [];
          next.parentId = selectedIds.length ? selectedIds[0] : '';
          setParentSelectOpen(false);
        }

        return next;
      });
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

    if (categoryType === CATEGORY_TYPE.SUB && !form.parentId && !(form.multiParentIds?.length)) {
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
        const selectedParentId =
          categoryType === CATEGORY_TYPE.SUB
            ? (form.parentId || (form.multiParentIds?.length ? form.multiParentIds[0] : null))
            : null;

        const payload = {
          code: autoCode,
          name: form.name?.trim(),
          parentId: selectedParentId,
          multiParentIds: categoryType === CATEGORY_TYPE.SUB ? (form.multiParentIds || []) : [],
          context: form.context || 'ANY',
          active: form.active
        };

        await createMedicalCategory(payload);
        openSnackbar({ message: 'تم إنشاء التصنيف بنجاح', variant: 'success', alert: { color: 'success', variant: 'filled' } });
        triggerRefresh();
        navigate('/medical-categories');
      } catch (err) {
        console.error('[MedicalCategoryCreate] Submit failed:', err);
        const errorMsg = err?.response?.data?.message || err?.message || 'حدث خطأ أثناء إنشاء التصنيف';
        setApiError(errorMsg);
      } finally {
        setSubmitting(false);
      }
    },
    [form, categoryType, navigate, validate, triggerRefresh]
  );

  const handleBack = useCallback(() => navigate('/medical-categories'), [navigate]);

  // ========================================
  // RENDER
  // ========================================

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader
        title="إضافة تصنيف طبي جديد"
        subtitle="أضف تصنيفاً رئيسياً أو فرعياً للخدمات الطبية"
        icon={CategoryIcon}
        breadcrumbs={[{ label: 'التصنيفات الطبية', path: '/medical-categories' }, { label: 'إضافة جديد' }]}
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
              <SectionHeader icon={AccountTreeIcon} title="نوع التصنيف" subtitle="اختر نوع التصنيف الذي تريد إنشاءه" color="primary" />

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
                  <InputLabel>اختر التصنيفات الأم (متعدد) *</InputLabel>
                  <Select
                    multiple
                    open={parentSelectOpen}
                    onOpen={() => setParentSelectOpen(true)}
                    onClose={() => setParentSelectOpen(false)}
                    value={form.multiParentIds || []}
                    onChange={handleChange('multiParentIds')}
                    label="اختر التصنيفات الأم (متعدد) *"
                    disabled={submitting}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => {
                          const cat = categories.find((c) => c.id === value);
                          return <Chip key={value} label={cat?.name || value} size="small" />;
                        })}
                      </Box>
                    )}
                    sx={{ '& .MuiSelect-select': { py: '0.75rem' } }}
                  >
                    {organizedCategories.map((mainCat) => (
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
                      </MenuItem>
                    ))}
                  </Select>

                  {errors.parentId && (
                    <Typography variant="caption" color="error" sx={{ mt: 0.5, mr: '0.75rem' }}>
                      {errors.parentId}
                    </Typography>
                  )}
                </FormControl>

                <ParentPreview parent={selectedParent} />
              </Collapse>
            </Box>

            {/* ===== العمود الأيسر: البيانات + الإجراءات ===== */}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <SectionHeader icon={LabelIcon} title="بيانات التصنيف" subtitle="أدخل المعلومات الأساسية للتصنيف" color="info" />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="رمز التصنيف (تلقائي)"
                    value={autoCode}
                    disabled
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <CodeIcon sx={{ color: 'text.disabled' }} />
                        </InputAdornment>
                      ),
                      sx: { fontFamily: 'monospace', letterSpacing: 1, bgcolor: 'action.hover' }
                    }}
                    helperText="يُولَّد تلقائياً من الخادم بصيغة CAT001, CAT002... (عدم تكرار مضمون)"
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
                <Button type="submit" variant="contained" size="large" startIcon={<SaveIcon />} disabled={submitting} sx={{ minWidth: '8.75rem' }}>
                  {submitting ? 'جارِ الحفظ...' : 'حفظ التصنيف'}
                </Button>
              </Stack>
            </Box>
          </Stack>
        </Box>
      </MainCard>
    </Box>
  );
};

export default MedicalCategoryCreate;


