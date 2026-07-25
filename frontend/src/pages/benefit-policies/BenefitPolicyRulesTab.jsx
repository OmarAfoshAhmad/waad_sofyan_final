import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  InputAdornment,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
  MenuItem,
  Select,
  InputLabel,
  FormControl,
  Checkbox
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Replay as ReplayIcon,
  Category as CategoryIcon,
  MedicalServices as ServiceIcon,
  Search as SearchIcon,
  Clear as ClearIcon,
  Save as SaveIcon,
  Refresh as RefreshIcon,
  Link as LinkIcon,
  LinkOff as LinkOffIcon,
  AutoAwesome as AutoAwesomeIcon,
  FileDownload as FileDownloadIcon,
  FileUpload as FileUploadIcon
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSnackbar } from 'notistack';

import MainCard from 'components/MainCard';
import { UnifiedMedicalTable } from 'components/common';

import {
  getPolicyRules,
  createPolicyRule,
  updatePolicyRule,
  togglePolicyRuleActive,
  restorePolicyRule,
  deletePolicyRule,
  hardDeletePolicyRule,
  applyPolicyTemplate,
  copyPolicyRules,
  getAvailableTemplates,
  downloadPolicyRulesTemplate,
  importPolicyRulesFromExcel
} from 'services/api/benefit-policy-rules.service';
import { getBenefitStructure, upsertIndividualBenefitLimit } from 'services/api/benefit-structure.service';
import { getMedicalCategories } from 'services/api/medical-categories.service';
import { lookupMedicalServices } from 'services/api/medical-services.service';
import { getBenefitPoliciesSelector } from 'services/api/benefit-policies.service';

// ═══════════════════════════════════════════════════════════════════════════
// RULE FORM COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const INITIAL_FORM_STATE = {
  mainMedicalCategoryId: '',
  childMedicalCategoryId: '',
  serviceName: '',
  encounterType: 'OUTPATIENT',
  coveragePercent: '',
  waitingPeriodDays: '0',
  requiresPreApproval: false,
  notes: ''
};

const FIXED_RULE_CHIP_SX = {
  code: { width: '9rem', justifyContent: 'center' },
  context: { width: '7rem', justifyContent: 'center' },
  coverage: { width: '7.25rem', justifyContent: 'center', fontWeight: 700 },
  preApproval: { width: '4.25rem', justifyContent: 'center' },
  bucket: {
    width: '14rem',
    justifyContent: 'flex-start',
    '& .MuiChip-label': {
      display: 'block',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }
};

/**
 * Rule Form Modal
 */
const RuleFormModal = ({
  open,
  onClose,
  onSubmit,
  initialData,
  isEdit,
  loading,
  categories,
  loadingCategories,
  policyDefaultCoveragePercent
}) => {
  const [formData, setFormData] = useState(INITIAL_FORM_STATE);
  const [errors, setErrors] = useState({});

  // Defense-in-depth: exclude inactive / soft-deleted categories even if API returns them
  const activeCategories = useMemo(() => categories.filter((cat) => cat?.active !== false && cat?.deleted !== true), [categories]);

  const mainCategories = useMemo(() => activeCategories.filter((cat) => !cat.parentId), [activeCategories]);

  const childCategories = useMemo(
    () => activeCategories.filter((cat) => Number(cat.parentId) === Number(formData.mainMedicalCategoryId)),
    [activeCategories, formData.mainMedicalCategoryId]
  );

  const selectedMainCategory = useMemo(
    () => mainCategories.find((cat) => Number(cat.id) === Number(formData.mainMedicalCategoryId)) || null,
    [mainCategories, formData.mainMedicalCategoryId]
  );

  const selectedChildCategory = useMemo(
    () => childCategories.find((cat) => Number(cat.id) === Number(formData.childMedicalCategoryId)) || null,
    [childCategories, formData.childMedicalCategoryId]
  );

  const getCategoryOptionLabel = useCallback((option) => {
    if (!option) return '';
    return `${option.nameAr || option.name} (${option.code || '-'})`;
  }, []);

  const selectedTargetCategoryId = useMemo(
    () => formData.childMedicalCategoryId || formData.mainMedicalCategoryId,
    [formData.childMedicalCategoryId, formData.mainMedicalCategoryId]
  );

  const { data: similarServices = [], isFetching: searchingServices } = useQuery({
    queryKey: ['rule-form-service-lookup', formData.serviceName, selectedTargetCategoryId],
    queryFn: () =>
      lookupMedicalServices({
        q: formData.serviceName || '',
        categoryId: selectedTargetCategoryId ? Number(selectedTargetCategoryId) : undefined
      }),
    enabled: !!selectedTargetCategoryId && !!formData.serviceName && formData.serviceName.trim().length >= 2,
    staleTime: 15000
  });

  const exactNameMatch = useMemo(() => {
    const term = (formData.serviceName || '').trim().toLowerCase();
    if (!term) return null;
    return (
      similarServices.find((s) => {
        const ar = (s.nameAr || s.name || '').trim().toLowerCase();
        const en = (s.nameEn || '').trim().toLowerCase();
        return ar === term || en === term;
      }) || null
    );
  }, [formData.serviceName, similarServices]);

  // Initialize form data when modal opens
  useEffect(() => {
    if (open) {
      if (isEdit && initialData) {
        const selectedCategory = activeCategories.find((cat) => Number(cat.id) === Number(initialData.medicalCategoryId));
        const parentId = selectedCategory?.parentId ? String(selectedCategory.parentId) : String(initialData.medicalCategoryId || '');
        const childId = selectedCategory?.parentId ? String(selectedCategory.id) : '';

        setFormData({
          mainMedicalCategoryId: parentId,
          childMedicalCategoryId: childId,
          serviceName: initialData.medicalServiceName || '',
          encounterType: initialData.encounterType || 'OUTPATIENT',
          coveragePercent: initialData.coveragePercent ?? '',
          waitingPeriodDays: initialData.waitingPeriodDays ?? '0',
          requiresPreApproval: initialData.requiresPreApproval || false,
          notes: initialData.notes || ''
        });
      } else {
        const defaultCoverage =
          policyDefaultCoveragePercent !== null && policyDefaultCoveragePercent !== undefined && policyDefaultCoveragePercent !== ''
            ? String(policyDefaultCoveragePercent)
            : '';

        setFormData({
          ...INITIAL_FORM_STATE,
          coveragePercent: defaultCoverage
        });
      }
      setErrors({});
    }
  }, [open, isEdit, initialData, activeCategories, policyDefaultCoveragePercent]);

  const handleChange = useCallback(
    (field) => (event) => {
      const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;

      setFormData((prev) => {
        return { ...prev, [field]: value };
      });

      // Clear error for this field
      setErrors((prev) => ({ ...prev, [field]: null }));
    },
    []
  );

  const handleMainCategoryChange = useCallback((_, option) => {
    setFormData((prev) => ({
      ...prev,
      mainMedicalCategoryId: option ? String(option.id) : '',
      childMedicalCategoryId: '',
      serviceName: ''
    }));
    setErrors((prev) => ({ ...prev, mainMedicalCategoryId: null }));
  }, []);

  const handleChildCategoryChange = useCallback((_, option) => {
    setFormData((prev) => ({
      ...prev,
      childMedicalCategoryId: option ? String(option.id) : '',
      serviceName: ''
    }));
  }, []);

  const validate = useCallback(() => {
    const newErrors = {};

    if (!formData.mainMedicalCategoryId) {
      newErrors.mainMedicalCategoryId = 'يجب اختيار التصنيف الرئيسي';
    }

    // Coverage percent validation
    if (formData.coveragePercent !== '' && formData.coveragePercent !== null) {
      const coverage = Number(formData.coveragePercent);
      if (isNaN(coverage) || coverage < 0 || coverage > 100) {
        newErrors.coveragePercent = 'نسبة التغطية يجب أن تكون بين 0 و 100';
      }
    }

    // Waiting period validation
    if (formData.waitingPeriodDays !== '' && formData.waitingPeriodDays !== null) {
      const days = Number(formData.waitingPeriodDays);
      if (isNaN(days) || days < 0 || !Number.isInteger(days)) {
        newErrors.waitingPeriodDays = 'فترة الانتظار يجب أن تكون رقم صحيح موجب';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(() => {
    if (!validate()) return;

    const payload = {
      medicalCategoryId: Number(selectedTargetCategoryId),
      medicalServiceId: null,
      encounterType: formData.encounterType || 'OUTPATIENT',
      coveragePercent: formData.coveragePercent !== '' ? Number(formData.coveragePercent) : null,
      amountLimit: null,
      timesLimit: null,
      waitingPeriodDays: formData.waitingPeriodDays !== '' ? Number(formData.waitingPeriodDays) : 0,
      requiresPreApproval: formData.requiresPreApproval,
      notes: formData.notes || null
    };

    onSubmit(payload);
  }, [formData, validate, onSubmit, selectedTargetCategoryId]);

  const handleClose = useCallback(() => {
    setFormData(INITIAL_FORM_STATE);
    setErrors({});
    onClose();
  }, [onClose]);

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{isEdit ? 'تعديل قاعدة التغطية' : 'إضافة قاعدة تغطية جديدة'}</DialogTitle>
      <DialogContent sx={{ pt: 1.5 }}>
        <Grid container spacing={2}>
          {/* Main Category Selector */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Autocomplete
              options={mainCategories}
              value={selectedMainCategory}
              onChange={handleMainCategoryChange}
              getOptionLabel={getCategoryOptionLabel}
              isOptionEqualToValue={(option, value) => Number(option.id) === Number(value.id)}
              disableClearable
              disabled={loadingCategories}
              noOptionsText={loadingCategories ? 'جاري التحميل...' : 'لا توجد نتائج'}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="التصنيف الرئيسي *"
                  error={!!errors.mainMedicalCategoryId}
                  helperText={errors.mainMedicalCategoryId}
                />
              )}
            />
          </Grid>

          {/* Child Category Selector */}
          <Grid size={{ xs: 12, md: 6 }}>
            <Autocomplete
              options={childCategories}
              value={selectedChildCategory}
              onChange={handleChildCategoryChange}
              getOptionLabel={getCategoryOptionLabel}
              isOptionEqualToValue={(option, value) => Number(option.id) === Number(value.id)}
              disabled={loadingCategories || !formData.mainMedicalCategoryId}
              noOptionsText={
                !formData.mainMedicalCategoryId ? 'اختر التصنيف الرئيسي أولاً' : loadingCategories ? 'جاري التحميل...' : 'لا توجد نتائج'
              }
              clearText="إزالة"
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="التصنيف التابع (اختياري)"
                  helperText="بعد اختيار التصنيف الرئيسي ستظهر قائمة التصنيفات التابعة له فقط"
                />
              )}
            />
          </Grid>

          {/* Service Name Search (Optional - does not block free typing) */}
          <Grid size={12}>
            <TextField
              label="اسم الخدمة (اختياري)"
              value={formData.serviceName}
              onChange={handleChange('serviceName')}
              placeholder="اكتب اسم الخدمة..."
              disabled={loadingCategories || !selectedTargetCategoryId}
              helperText="اختياري: للبحث عن خدمات مشابهة وتجنب التكرار. (تطبيق الاستثناء على خدمة بعينها يتطلب تفعيل دعم الخدمة في الخلفية)"
              fullWidth
            />
          </Grid>

          {!!formData.serviceName && !!selectedTargetCategoryId && (
            <Grid size={12}>
              <Box>
                {searchingServices ? (
                  <Typography variant="caption" color="text.secondary">
                    جاري البحث عن خدمات مشابهة...
                  </Typography>
                ) : (
                  <Stack spacing={1}>
                    {exactNameMatch && (
                      <Alert severity="warning" sx={{ py: 0.25 }}>
                        توجد خدمة مطابقة تقريبًا: {exactNameMatch.code} - {exactNameMatch.nameAr || exactNameMatch.name}
                      </Alert>
                    )}
                    {similarServices.length > 0 ? (
                      <Box>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          خدمات مشابهة موجودة مسبقًا:
                        </Typography>
                        <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                          {similarServices.slice(0, 6).map((svc) => (
                            <Chip key={svc.id} size="small" variant="outlined" label={`${svc.code} - ${svc.nameAr || svc.name}`} />
                          ))}
                        </Stack>
                      </Box>
                    ) : (
                      <Typography variant="caption" color="text.secondary">
                        لا توجد خدمات مشابهة ضمن التصنيف المختار.
                      </Typography>
                    )}
                  </Stack>
                )}
              </Box>
            </Grid>
          )}

          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              select
              fullWidth
              label="سياق القاعدة"
              value={formData.encounterType}
              onChange={handleChange('encounterType')}
              helperText="السياق يحدد القاعدة ولا يغيّر تصنيف الخدمة"
            >
              <MenuItem value="OUTPATIENT">عيادات خارجية</MenuItem>
              <MenuItem value="INPATIENT">إيواء</MenuItem>
              <MenuItem value="ANY">عام (ANY)</MenuItem>
            </TextField>
          </Grid>

          {/* Coverage Percent */}
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              label="نسبة التغطية"
              type="number"
              value={formData.coveragePercent}
              onChange={handleChange('coveragePercent')}
              error={!!errors.coveragePercent}
              helperText={errors.coveragePercent || 'اتركه فارغاً لاستخدام النسبة الافتراضية للوثيقة'}
              InputProps={{
                endAdornment: <InputAdornment position="end">%</InputAdornment>,
                inputProps: { min: 0, max: 100 }
              }}
              fullWidth
            />
          </Grid>

          {/* Waiting Period */}
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              label="فترة الانتظار"
              type="number"
              value={formData.waitingPeriodDays}
              onChange={handleChange('waitingPeriodDays')}
              error={!!errors.waitingPeriodDays}
              helperText={errors.waitingPeriodDays || 'عدد الأيام قبل سريان التغطية'}
              InputProps={{
                endAdornment: <InputAdornment position="end">يوم</InputAdornment>,
                inputProps: { min: 0, step: 1 }
              }}
              fullWidth
            />
          </Grid>

          {/* Requires Pre-Approval */}
          <Grid size={{ xs: 12, md: 6 }}>
            <FormControlLabel
              control={<Switch checked={formData.requiresPreApproval} onChange={handleChange('requiresPreApproval')} color="primary" />}
              label="تتطلب موافقة مسبقة"
            />
          </Grid>

          {/* Notes */}
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField label="ملاحظات" value={formData.notes} onChange={handleChange('notes')} multiline rows={1} fullWidth />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          إلغاء
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={loading}
          startIcon={loading && <CircularProgress size={16} color="inherit" />}
        >
          {isEdit ? 'حفظ التعديلات' : 'إضافة القاعدة'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

RuleFormModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  initialData: PropTypes.object,
  isEdit: PropTypes.bool,
  loading: PropTypes.bool,
  categories: PropTypes.array,
  loadingCategories: PropTypes.bool,
  policyDefaultCoveragePercent: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
};

// ═══════════════════════════════════════════════════════════════════════════
// DELETE CONFIRMATION DIALOG
// ═══════════════════════════════════════════════════════════════════════════

const DeleteConfirmDialog = ({ open, ruleName, onConfirm, onCancel, loading, hardDeleteMode }) => (
  <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
    <DialogTitle>{hardDeleteMode ? 'حذف نهائي لقاعدة التغطية' : 'حذف ناعم لقاعدة التغطية'}</DialogTitle>
    <DialogContent>
      <DialogContentText>
        {hardDeleteMode
          ? `هل أنت متأكد من الحذف النهائي لقاعدة التغطية "${ruleName}"؟`
          : `هل أنت متأكد من تعطيل قاعدة التغطية "${ruleName}"؟`}
        <br />
        {hardDeleteMode
          ? 'سيتم حذف القاعدة نهائيًا ولا يمكن استعادتها.'
          : 'سيتم تنفيذ حذف ناعم (إلغاء التفعيل) ويمكن إعادة التفعيل لاحقًا.'}
      </DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={onCancel} disabled={loading}>
        إلغاء
      </Button>
      <Button
        onClick={onConfirm}
        color="error"
        variant="contained"
        disabled={loading}
        startIcon={loading && <CircularProgress size={16} color="inherit" />}
      >
        {hardDeleteMode ? 'حذف نهائي' : 'تعطيل'}
      </Button>
    </DialogActions>
  </Dialog>
);

DeleteConfirmDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  ruleName: PropTypes.string,
  onConfirm: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  loading: PropTypes.bool,
  hardDeleteMode: PropTypes.bool
};

// ═══════════════════════════════════════════════════════════════════════════
// CATEGORY COVERAGE MODAL
// ═══════════════════════════════════════════════════════════════════════════

const CategoryCoverageModal = ({
  open,
  onClose,
  canEdit,
  bulkSavingCoverage,
  categoriesCoverageRows,
  handleCoverageInputChange,
  saveCategoryCoverage,
  saveAllCategoryCoverage,
  deleteRule,
  createMutation,
  updateMutation,
  isLoading
}) => (
  <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
    <DialogTitle>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h5">القواعد الأساسية — نسب التغطية حسب التصنيف</Typography>
        <Button
          size="small"
          variant="contained"
          color="primary"
          startIcon={bulkSavingCoverage ? <CircularProgress size={14} color="inherit" /> : <SaveIcon fontSize="small" />}
          onClick={saveAllCategoryCoverage}
          disabled={!canEdit || bulkSavingCoverage || isLoading}
        >
          حفظ جماعي
        </Button>
      </Stack>
    </DialogTitle>
    <DialogContent dividers sx={{ p: 0 }}>
      <Typography variant="body2" color="text.secondary" sx={{ px: '1.0rem', py: 1 }}>
        حدّد قرار ونسبة التغطية هنا. السقف الفردي يُدار من عمود «سقف المنفعة»، والسقف المشترك من تبويب «مجموعات المنافع».
      </Typography>
      <TableContainer sx={{ maxHeight: '32.5rem' }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>التصنيف</TableCell>
              <TableCell align="center" sx={{ width: '7.5rem' }}>
                النسبة الحالية
              </TableCell>
              <TableCell align="center" sx={{ width: '8.75rem' }}>
                نسبة التغطية (اختياري)
              </TableCell>
              <TableCell align="center" sx={{ width: '8.75rem' }}>
                الإجراءات
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {categoriesCoverageRows.map((row) => {
              const isRowSaving = createMutation.isPending || updateMutation.isPending;
              return (
                <TableRow key={row.category.id} hover>
                  <TableCell>
                    <Stack spacing={0.25}>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Chip
                          label={row.category.code || '-'}
                          size="small"
                          variant="outlined"
                          sx={{ width: 'fit-content', fontFamily: 'monospace' }}
                        />
                        {row.serviceRulesCount > 0 && (
                          <Tooltip title={`${row.serviceRulesCount} قاعدة خدمة مخصصة تُعدّل هذا التصنيف`}>
                            <Chip label={`${row.serviceRulesCount} خدمة`} size="small" color="secondary" variant="filled" />
                          </Tooltip>
                        )}
                      </Stack>
                      <Typography variant="body2" fontWeight={500}>
                        {row.category.nameAr || row.category.name || '-'}
                      </Typography>
                      {row.category.nameEn && (
                        <Typography variant="caption" color="text.secondary">
                          {row.category.nameEn}
                        </Typography>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell align="center">
                    {row.effectiveCoveragePercent !== null && row.effectiveCoveragePercent !== undefined
                      ? `${row.effectiveCoveragePercent}%`
                      : 'افتراضي الوثيقة'}
                  </TableCell>
                  <TableCell align="center" sx={{ width: '8.75rem' }}>
                    <TextField
                      size="small"
                      type="number"
                      value={row.coverageInputValue}
                      onChange={(e) => handleCoverageInputChange(row.category.id, 'coveragePercent', e.target.value)}
                      inputProps={{ min: 0, max: 100 }}
                      InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                      placeholder="افتراضي"
                      fullWidth
                      disabled={!canEdit || bulkSavingCoverage}
                    />
                  </TableCell>
                  <TableCell align="center" sx={{ width: '8.75rem' }}>
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                      <Button
                        size="small"
                        variant="contained"
                        startIcon={isRowSaving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon fontSize="small" />}
                        onClick={() => saveCategoryCoverage(row)}
                        disabled={!canEdit || isLoading || isRowSaving || bulkSavingCoverage}
                      >
                        حفظ
                      </Button>
                      {row.existingRule?.id && (
                        <Tooltip title="حذف ناعم (تعطيل) لقاعدة هذا التصنيف">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => deleteRule(row.existingRule)}
                              disabled={!canEdit || isLoading || isRowSaving || bulkSavingCoverage}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>إغلاق</Button>
    </DialogActions>
  </Dialog>
);

CategoryCoverageModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  canEdit: PropTypes.bool,
  bulkSavingCoverage: PropTypes.bool,
  categoriesCoverageRows: PropTypes.array,
  handleCoverageInputChange: PropTypes.func.isRequired,
  saveCategoryCoverage: PropTypes.func.isRequired,
  saveAllCategoryCoverage: PropTypes.func.isRequired,
  deleteRule: PropTypes.func.isRequired,
  createMutation: PropTypes.object.isRequired,
  updateMutation: PropTypes.object.isRequired,
  isLoading: PropTypes.bool
};

// ═══════════════════════════════════════════════════════════════════════════
// MAIN RULES TAB COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Benefit Policy Rules Tab
 *
 * Displays and manages coverage rules for a benefit policy
 */
const BenefitPolicyRulesTab = ({ policyId, policyStatus, policyDefaultCoveragePercent, onOpenStructure }) => {
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // Modal states
  const [formModal, setFormModal] = useState({ open: false, data: null, isEdit: false });
  const [deleteDialog, setDeleteDialog] = useState({ open: false, rule: null });
  const [ruleSearch, setRuleSearch] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const [showDeleted, setShowDeleted] = useState(false);
  const [categoryCoverageInputs, setCategoryCoverageInputs] = useState({});
  const [bulkSavingCoverage, setBulkSavingCoverage] = useState(false);
  const [categoryCoverageModalOpen, setCategoryCoverageModalOpen] = useState(false);
  const [individualLimitDialog, setIndividualLimitDialog] = useState({
    open: false, rule: null, amountLimit: '', timesLimit: '', daysLimit: '', periodType: 'POLICY_PERIOD'
  });
  // Pagination state
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(6);
  // Sort state
  const [sortBy, setSortBy] = useState(null);
  const [sortDirection, setSortDirection] = useState('asc');
  const defaultOrderRef = useRef({ active: [], deleted: [] });

  // ═══════════════════════════════════════════════════════════════════════════
  // DATA FETCHING
  // ═══════════════════════════════════════════════════════════════════════════

  // Fetch rules
  const {
    data: rules = [],
    isLoading: loadingRules,
    error: rulesError,
    refetch: refetchRules
  } = useQuery({
    queryKey: ['benefit-policy-rules', policyId],
    queryFn: () => getPolicyRules(policyId),
    enabled: !!policyId,
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnWindowFocus: 'always'
  });

  const {
    data: benefitStructure = { groups: [], buckets: [], links: [] },
    isError: structureLoadFailed,
    isSuccess: structureLoaded
  } = useQuery({
    queryKey: ['benefit-structure', policyId],
    queryFn: () => getBenefitStructure(policyId),
    enabled: !!policyId,
    staleTime: 0
  });

  // Fetch categories for selector from the same source used in MedicalCategoriesList
  const { data: categories = [], isLoading: loadingCategories } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: async () => {
      const result = await getMedicalCategories({
        page: 0,
        size: 500,
        sortBy: 'code',
        sortDir: 'ASC',
        active: true
      });
      return result?.items || [];
    }
  });

  // NOTE: Service name field now performs lightweight lookup while typing (duplicate hint only)

  // ═══════════════════════════════════════════════════════════════════════════
  // MUTATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  const createMutation = useMutation({
    mutationFn: (payload) => createPolicyRule(policyId, payload),
    onSuccess: async () => {
      enqueueSnackbar('تمت إضافة القاعدة بنجاح', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      setFormModal({ open: false, data: null, isEdit: false });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل إضافة القاعدة', { variant: 'error' });
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ ruleId, payload }) => updatePolicyRule(policyId, ruleId, payload),
    onSuccess: async () => {
      enqueueSnackbar('تم تحديث القاعدة بنجاح', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      setFormModal({ open: false, data: null, isEdit: false });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل تحديث القاعدة', { variant: 'error' });
    }
  });

  const toggleMutation = useMutation({
    mutationFn: (ruleId) => togglePolicyRuleActive(policyId, ruleId),
    onSuccess: async () => {
      enqueueSnackbar('تم تغيير حالة القاعدة', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل تغيير الحالة', { variant: 'error' });
    }
  });

  const restoreMutation = useMutation({
    mutationFn: (ruleId) => restorePolicyRule(policyId, ruleId),
    onSuccess: async () => {
      enqueueSnackbar('تمت استعادة القاعدة من سلة المحذوفات', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل استعادة القاعدة', { variant: 'error' });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId) => deletePolicyRule(policyId, ruleId),
    onSuccess: async () => {
      enqueueSnackbar('تم تعطيل القاعدة (حذف ناعم)', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      setDeleteDialog({ open: false, rule: null });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل الحذف الناعم للقاعدة', { variant: 'error' });
    }
  });

  const hardDeleteMutation = useMutation({
    mutationFn: (ruleId) => hardDeletePolicyRule(policyId, ruleId),
    onSuccess: async () => {
      enqueueSnackbar('تم الحذف النهائي للقاعدة', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      setDeleteDialog({ open: false, rule: null });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || 'فشل الحذف النهائي للقاعدة', { variant: 'error' });
    }
  });

  const individualLimitMutation = useMutation({
    mutationFn: ({ ruleId, payload }) => upsertIndividualBenefitLimit(policyId, ruleId, payload),
    onSuccess: async () => {
      enqueueSnackbar('تم حفظ سقف المنفعة الفردي', { variant: 'success' });
      await queryClient.invalidateQueries({ queryKey: ['benefit-structure', policyId] });
      setIndividualLimitDialog({ open: false, rule: null, amountLimit: '', timesLimit: '', daysLimit: '', periodType: 'POLICY_PERIOD' });
    },
    onError: (err) => enqueueSnackbar(err.response?.data?.message || 'تعذر حفظ سقف المنفعة', { variant: 'error' })
  });

  const openIndividualLimit = useCallback((rule) => {
    const bucket = rule.individualLimitLink?.bucket;
    setIndividualLimitDialog({
      open: true, rule, amountLimit: bucket?.amountLimit ?? '', timesLimit: bucket?.timesLimit ?? '',
      daysLimit: bucket?.daysLimit ?? '', periodType: bucket?.periodType || 'POLICY_PERIOD'
    });
  }, []);

  // ═══════════════════════════════════════════════════════════════════════════
  // HANDLERS
  // ═══════════════════════════════════════════════════════════════════════════

  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [templates, setTemplates] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [sourceType, setSourceType] = useState('TEMPLATE');
  const [applyMode, setApplyMode] = useState('UPDATE');
  const [confirmText, setConfirmText] = useState('');
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [applyingTemplate, setApplyingTemplate] = useState(false);

  // ── Excel Import/Export state ──────────────────────────────────────────────
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [clearOld, setClearOld] = useState(false);
  const importFileInputRef = useRef(null);

  const handleDownloadTemplate = useCallback(async () => {
    setDownloadingTemplate(true);
    try {
      const blob = await downloadPolicyRulesTemplate(policyId);
      const url = URL.createObjectURL(new Blob([blob], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `قواعد_التغطية_وثيقة_${policyId}.xlsx`;
      link.click();
      URL.revokeObjectURL(url);
      enqueueSnackbar('تم تحميل قالب الاستيراد بنجاح', { variant: 'success' });
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل تحميل القالب', { variant: 'error' });
    } finally {
      setDownloadingTemplate(false);
    }
  }, [policyId, enqueueSnackbar]);

  const handleImportExcel = useCallback(async () => {
    if (!importFile) {
      enqueueSnackbar('يرجى اختيار ملف Excel أولاً', { variant: 'warning' });
      return;
    }
    setImporting(true);
    setImportResult(null);
    try {
      const result = await importPolicyRulesFromExcel(policyId, importFile, clearOld);
      setImportResult(result);
      if (result?.success) {
        enqueueSnackbar(result.messageAr || 'تم الاستيراد بنجاح', { variant: 'success' });
        await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      } else {
        enqueueSnackbar(result?.messageAr || 'اكتمل الاستيراد مع أخطاء', { variant: 'warning' });
      }
    } catch (err) {
      const msg = err?.response?.data?.message || 'فشل الاستيراد';
      enqueueSnackbar(msg, { variant: 'error' });
      setImportResult({ success: false, messageAr: msg, summary: { totalRows: 0, created: 0, updated: 0, rejected: 0 }, errors: [] });
    } finally {
      setImporting(false);
    }
  }, [importFile, policyId, enqueueSnackbar, queryClient]);

  const handleOpenTemplateDialog = async () => {
    setTemplateDialogOpen(true);
    setLoadingTemplates(true);
    try {
      const [tplData, polData] = await Promise.all([getAvailableTemplates(policyId), getBenefitPoliciesSelector()]);
      setTemplates(tplData || []);
      const filteredPols = (polData || []).filter((p) => String(p.id) !== String(policyId));
      setPolicies(filteredPols);

      const defaultTpl = tplData?.find((t) => t.isDefault) || tplData?.[0];
      if (defaultTpl) {
        setSelectedTemplateId(defaultTpl.id);
        setSourceType('TEMPLATE');
      } else if (filteredPols.length > 0) {
        setSelectedTemplateId(filteredPols[0].id);
        setSourceType('POLICY');
      }
      setApplyMode('UPDATE');
      setConfirmText('');
    } catch (err) {
      enqueueSnackbar('فشل تحميل القوائم', { variant: 'error' });
    } finally {
      setLoadingTemplates(false);
    }
  };

  const handleApplyTemplate = async () => {
    if (!selectedTemplateId) {
      enqueueSnackbar('الرجاء الاختيار أولاً', { variant: 'warning' });
      return;
    }
    setApplyingTemplate(true);
    try {
      if (sourceType === 'TEMPLATE') {
        await applyPolicyTemplate(policyId, selectedTemplateId, applyMode);
      } else {
        await copyPolicyRules(policyId, selectedTemplateId, applyMode);
      }
      enqueueSnackbar('تم تطبيق قواعد التغطية بنجاح', { variant: 'success' });
      setTemplateDialogOpen(false);
      refetchRules();
    } catch (err) {
      enqueueSnackbar(err?.response?.data?.message || 'فشل الاستيراد والتطبيق', { variant: 'error' });
    } finally {
      setApplyingTemplate(false);
    }
  };

  const handleAddRule = useCallback(() => {
    setFormModal({ open: true, data: null, isEdit: false });
  }, []);

  const handleEditRule = useCallback((rule) => {
    setFormModal({ open: true, data: rule, isEdit: true });
  }, []);

  const handleDeleteRule = useCallback((rule) => {
    setDeleteDialog({ open: true, rule });
  }, []);

  const handleRestoreRule = useCallback(
    (rule) => {
      restoreMutation.mutate(rule.id);
    },
    [restoreMutation]
  );

  const handleToggleActive = useCallback(
    (rule) => {
      toggleMutation.mutate(rule.id);
    },
    [toggleMutation]
  );

  const handleFormSubmit = useCallback(
    (payload) => {
      if (formModal.isEdit && formModal.data) {
        updateMutation.mutate({ ruleId: formModal.data.id, payload });
      } else {
        createMutation.mutate(payload);
      }
    },
    [formModal, createMutation, updateMutation]
  );

  const handleFormClose = useCallback(() => {
    setFormModal({ open: false, data: null, isEdit: false });
  }, []);

  const handleDeleteConfirm = useCallback(() => {
    if (deleteDialog.rule) {
      if (showDeleted) {
        hardDeleteMutation.mutate(deleteDialog.rule.id);
      } else {
        deleteMutation.mutate(deleteDialog.rule.id);
      }
    }
  }, [deleteDialog.rule, deleteMutation, hardDeleteMutation, showDeleted]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialog({ open: false, rule: null });
  }, []);

  // ═══════════════════════════════════════════════════════════════════════════
  // COMPUTED
  // ═══════════════════════════════════════════════════════════════════════════

  const canEdit = policyStatus === 'DRAFT';
  const isLoading =
    createMutation.isPending ||
    updateMutation.isPending ||
    deleteMutation.isPending ||
    hardDeleteMutation.isPending ||
    toggleMutation.isPending ||
    restoreMutation.isPending;

  // reset page when search changes
  useEffect(() => {
    setPage(0);
  }, [ruleSearch, showDeleted]);

  const handleSort = useCallback((columnId, direction) => {
    setSortBy(columnId);
    setSortDirection(direction);
    setPage(0);
  }, []);

  // UnifiedMedicalTable column definitions
  const tableColumns = useMemo(
    () => [
      { id: 'code', label: 'الرمز', minWidth: '7.5rem', align: 'center' },
      { id: 'nameAr', label: 'التصنيف الطبي', minWidth: '15rem' },
      { id: 'encounterType', label: 'السياق', minWidth: '8rem', align: 'center' },
      { id: 'coveragePercent', label: 'قرار التغطية', minWidth: '10rem', align: 'center' },
      { id: 'daysLimit', label: 'حد الأيام', minWidth: '7rem', align: 'center' },
      { id: 'requiresPreApproval', label: 'موافقة مسبقة', minWidth: '7.5rem', align: 'center' },
      { id: 'individualLimit', label: 'سقف المنفعة', minWidth: '11rem', align: 'center', sortable: false },
      { id: 'bucketLinks', label: 'السقف أو المجموعة', minWidth: '15rem', align: 'center', sortable: false },
      { id: 'active', label: 'نشط', minWidth: '5rem', align: 'center', sortable: false },
      { id: 'changedAt', label: 'آخر تحديث', minWidth: '8rem', align: 'center', sortable: false }
    ],
    []
  );

  const renderRuleCell = useCallback(
    (rule, column) => {
      switch (column.id) {
        case 'code':
          return (
            <Chip
              label={rule.code}
              size="small"
              variant={rule.groupSource ? 'filled' : 'outlined'}
              color={rule.groupSource ? 'secondary' : 'default'}
              sx={{
                fontFamily: 'monospace',
                fontSize: '0.72rem',
                borderColor: rule.groupSource ? 'secondary.main' : 'primary.main',
                color: rule.groupSource ? 'secondary.contrastText' : 'primary.main',
                ...FIXED_RULE_CHIP_SX.code
              }}
            />
          );
        case 'nameAr':
          return (
            <Stack spacing={0.25}>
              <Typography variant="body2" fontWeight={500}>
                {rule.nameAr}
              </Typography>
              {rule.groupSource && <Chip size="small" color="secondary" label="مجموعة منافع" sx={{ alignSelf: 'flex-start' }} />}
              {rule.nameEn !== '-' && (
                <Typography variant="caption" color="text.secondary">
                  {rule.nameEn}
                </Typography>
              )}
              {rule.parentNameAr !== '-' && (
                <Typography variant="caption" color="text.secondary">
                  يتبع: {rule.parentNameAr}
                </Typography>
              )}
            </Stack>
          );
        case 'coveragePercent':
          {
            if (rule.groupSource) return <Chip size="small" color="secondary" variant="outlined" label="سياسة مجموعة" />;
            const value = rule.coveragePercent ?? rule.effectiveCoveragePercent;
            const inherited = rule.coveragePercent === null || rule.coveragePercent === undefined;
            const label = value === 0 ? 'غير مغطى' : value === 100 ? 'تغطية كاملة' : value == null ? 'غير محدد' : `تغطية جزئية ${value}%`;
            const color = value === 0 ? 'error' : value === 100 ? 'success' : value == null ? 'default' : 'warning';
            return (
              <Tooltip title={inherited && value != null ? `النسبة الافتراضية للوثيقة: ${value}%` : label}>
                <Chip label={label} size="small" color={color} variant={inherited ? 'outlined' : 'filled'} sx={FIXED_RULE_CHIP_SX.coverage} />
              </Tooltip>
            );
          }
        case 'encounterType':
          return (
            <Chip
              size="small"
              variant="outlined"
              label={rule.encounterType === 'INPATIENT' ? 'إيواء' : rule.encounterType === 'ANY' ? 'عام' : 'عيادات خارجية'}
              sx={FIXED_RULE_CHIP_SX.context}
            />
          );
        case 'bucketLinks':
          if (structureLoadFailed) {
            return <Chip size="small" color="warning" variant="outlined" label="تعذر التحقق من الربط" sx={FIXED_RULE_CHIP_SX.bucket} />;
          }
          return rule.bucketLinks.length > 0 ? (
            <Stack spacing={0.5} alignItems="center">
              {rule.bucketLinks.map((link) => (
                <Tooltip key={link.id} title={`المجموعة: ${link.groupName || 'غير محددة'} — اضغط لإدارة الربط`}>
                  <Chip
                    size="small"
                    icon={<LinkIcon />}
                    color="success"
                    variant="outlined"
                    label={rule.groupSource
                      ? `مبلغ: ${link.bucket?.amountLimit ?? 'بلا سقف'} | مرات: ${link.bucket?.timesLimit ?? 'بلا سقف'}`
                      : `${link.groupName || 'مجموعة'} ← ${link.bucket?.nameAr || 'سقف'}`}
                    onClick={onOpenStructure}
                    sx={FIXED_RULE_CHIP_SX.bucket}
                  />
                </Tooltip>
              ))}
            </Stack>
          ) : (
            <Chip
              size="small"
              icon={<LinkOffIcon />}
              color="error"
              variant="outlined"
              label="بلا سقف أو مجموعة"
              onClick={onOpenStructure}
              sx={FIXED_RULE_CHIP_SX.bucket}
            />
          );
        case 'daysLimit':
          return rule.daysLimitLabel || '-';
        case 'requiresPreApproval':
          return rule.requiresPreApproval ? (
            <Chip label="نعم" size="small" color="warning" sx={FIXED_RULE_CHIP_SX.preApproval} />
          ) : (
            <Chip label="لا" size="small" variant="outlined" sx={FIXED_RULE_CHIP_SX.preApproval} />
          );
        case 'individualLimit': {
          if (rule.groupSource) return <Typography variant="caption" color="text.secondary">يُدار كسقف مشترك</Typography>;
          const limit = rule.individualLimitLink?.bucket;
          const label = limit
            ? `${limit.amountLimit != null ? `${limit.amountLimit} د.ل` : ''}${limit.amountLimit != null && limit.timesLimit != null ? ' • ' : ''}${limit.timesLimit != null ? `${limit.timesLimit} مرة` : ''}${limit.daysLimit != null ? ` • ${limit.daysLimit} يوم` : ''}`
            : 'بلا سقف فردي';
          return <Chip size="small" color={limit ? 'info' : 'default'} variant="outlined" label={label} onClick={() => canEdit && openIndividualLimit(rule)} />;
        }
        case 'active':
          if (rule.groupSource) return <Chip label={rule.isActive ? 'نشطة' : 'متوقفة'} size="small" color={rule.isActive ? 'secondary' : 'default'} variant="outlined" />;
          if (rule.isDeleted) {
            return <Chip label="في سلة المحذوفات" size="small" color="error" variant="outlined" />;
          }
          return (
            <Tooltip title={rule.isActive ? 'إيقاف القاعدة مؤقتاً' : 'تنشيط القاعدة'}>
              <span>
                <Switch
                  checked={!!rule.isActive}
                  onChange={() => handleToggleActive(rule)}
                  size="small"
                  disabled={!canEdit || toggleMutation.isPending}
                  sx={{
                    '& .MuiSwitch-switchBase.Mui-checked': {
                      color: '#0f9d76'
                    },
                    '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': {
                      backgroundColor: '#19c18f',
                      opacity: 1
                    },
                    '& .MuiSwitch-track': {
                      backgroundColor: '#b7bfcb',
                      opacity: 1
                    }
                  }}
                />
              </span>
            </Tooltip>
          );
        case 'changedAt':
          return (
            <Typography variant="body2" color="text.secondary">
              {rule.changedAt ? new Date(rule.changedAt).toLocaleDateString('ar-LY') : '-'}
            </Typography>
          );
        default:
          return rule[column.id] ?? '-';
      }
    },
    [handleToggleActive, onOpenStructure, structureLoadFailed, toggleMutation.isPending, canEdit, openIndividualLimit]
  );

  const categoryMap = useMemo(() => {
    const map = new Map();
    categories.forEach((cat) => map.set(cat.id, cat));
    return map;
  }, [categories]);

  const structureLinksByRuleId = useMemo(() => {
    const groupsById = new Map((benefitStructure.groups || []).map((group) => [group.id, group]));
    const linksByRule = new Map();
    (benefitStructure.links || []).forEach((link) => {
      const enriched = {
        ...link,
        groupName: groupsById.get(link.bucket?.benefitGroupId)?.nameAr || null,
        groupCode: groupsById.get(link.bucket?.benefitGroupId)?.code || null,
        aggregationMode: groupsById.get(link.bucket?.benefitGroupId)?.aggregationMode || null
      };
      const current = linksByRule.get(link.ruleId) || [];
      current.push(enriched);
      linksByRule.set(link.ruleId, current);
    });
    return linksByRule;
  }, [benefitStructure.groups, benefitStructure.links]);

  const normalizedRules = useMemo(() => {
    const benefitRows = rules.map((rule) => {
      const isCategory = rule.ruleType === 'CATEGORY';
      const code = isCategory ? rule.medicalCategoryCode || '-' : rule.medicalServiceCode || '-';
      const nameAr = (isCategory ? rule.medicalCategoryName : rule.medicalServiceName) || '-';
      const nameEn = isCategory ? rule.medicalCategoryNameEn || '-' : rule.medicalServiceNameEn || '-';

      let typeLabel = 'خدمة طبية';
      let parentNameAr = '-';
      if (isCategory) {
        const cat = categoryMap.get(rule.medicalCategoryId);
        const isRoot = cat ? !cat.parentId : true;
        typeLabel = isRoot ? 'تصنيف طبي رئيسي' : 'تصنيف طبي فرعي';
        if (cat?.parentId) {
          const parent = categoryMap.get(cat.parentId);
          parentNameAr = parent?.nameAr || parent?.name || '-';
        }
      } else {
        // خدمة طبية — التصنيف الأب هو التصنيف المرتبط بها
        if (rule.medicalCategoryId) {
          const cat = categoryMap.get(rule.medicalCategoryId);
          parentNameAr = cat?.nameAr || cat?.name || '-';
        }
      }

      const changedAt = rule.updatedAt || rule.lastModifiedAt || rule.modifiedAt || rule.createdAt || null;
      const bucketLinks = structureLinksByRuleId.get(rule.id) || [];
      const individualLimitLink = bucketLinks.find((link) => link.aggregationMode === 'INDIVIDUAL' || String(link.groupCode || '').startsWith('AUTO-BEN-')) || null;
      const groupBucketLinks = bucketLinks.filter((link) => link !== individualLimitLink);
      const linkSearch = groupBucketLinks.map((link) => `${link.groupName || ''} ${link.bucket?.nameAr || ''}`).join(' ');
      const linkedDaysLimits = bucketLinks
        .map((link) => link.bucket?.daysLimit)
        .filter((value) => value !== null && value !== undefined);
      const uniqueDaysLimits = [...new Set(linkedDaysLimits)];
      const daysLimitLabel = uniqueDaysLimits.length > 0 ? uniqueDaysLimits.map((value) => `${value} يوم`).join('، ') : null;
      const searchable = `${code} ${nameAr} ${nameEn} ${typeLabel} ${parentNameAr} ${linkSearch}`.toLowerCase();

      // Normalize active state defensively (backend may return boolean/string/number)
      const activeRaw = rule.active;
      const isActive = activeRaw === true || activeRaw === 1 || activeRaw === '1' || String(activeRaw).toLowerCase() === 'true';

      const deletedRaw = rule.deleted;
      const isDeleted = deletedRaw === true || deletedRaw === 1 || deletedRaw === '1' || String(deletedRaw).toLowerCase() === 'true';

      return {
        ...rule,
        code,
        nameAr,
        nameEn,
        typeLabel,
        parentNameAr,
        changedAt,
        bucketLinks: groupBucketLinks,
        individualLimitLink,
        daysLimit: uniqueDaysLimits.length > 0 ? Math.min(...uniqueDaysLimits) : null,
        daysLimitLabel,
        isLinked: bucketLinks.length > 0,
        searchable,
        isActive,
        isDeleted
      };
    });
    const groupRows = (benefitStructure.groups || [])
      .filter((group) => !String(group.code || '').startsWith('AUTO-BEN-'))
      .map((group) => {
        const buckets = (benefitStructure.buckets || []).filter((bucket) => bucket.benefitGroupId === group.id);
        const bucketIds = new Set(buckets.map((bucket) => bucket.id));
        const memberLinks = (benefitStructure.links || []).filter((link) => bucketIds.has(link.bucket?.id));
        const memberNames = memberLinks.map((link) => {
          const member = rules.find((rule) => rule.id === link.ruleId);
          return member?.medicalCategoryName || member?.medicalServiceName || '';
        }).filter(Boolean);
        const bucketLinks = buckets.map((bucket) => ({ id: `group-bucket-${bucket.id}`, groupName: group.nameAr, bucket }));
        const days = buckets.map((bucket) => bucket.daysLimit).filter((value) => value != null);
        return {
          id: `group-${group.id}`, code: group.code, nameAr: group.nameAr, nameEn: '-', parentNameAr: '-',
          typeLabel: 'مجموعة منافع', encounterType: group.contextType, coveragePercent: null,
          effectiveCoveragePercent: null, requiresPreApproval: !!group.requiresPreApproval,
          bucketLinks, daysLimitLabel: days.length ? days.map((value) => `${value} يوم`).join('، ') : null,
          isLinked: bucketLinks.length > 0, isActive: group.active !== false, isDeleted: false, groupSource: true,
          searchable: `${group.code} ${group.nameAr} مجموعة منافع ${memberNames.join(' ')}`.toLowerCase(), changedAt: group.updatedAt || null
        };
      });
    return [...benefitRows, ...groupRows];
  }, [rules, categoryMap, structureLinksByRuleId, benefitStructure.groups, benefitStructure.buckets, benefitStructure.links]);

  const filterStats = useMemo(() => {
    const activeRules = normalizedRules.filter((r) => !r.isDeleted);
    return {
      all: activeRules.length,
      uncovered: activeRules.filter((rule) => (rule.coveragePercent ?? rule.effectiveCoveragePercent) === 0).length,
      linked: structureLoaded ? activeRules.filter((rule) => rule.isLinked).length : 0,
      unlinked: structureLoaded ? activeRules.filter((rule) => !rule.isLinked).length : 0,
      preApproval: activeRules.filter((rule) => rule.requiresPreApproval === true).length
    };
  }, [normalizedRules, structureLoaded]);

  const filteredRules = useMemo(() => {
    const query = ruleSearch.trim().toLowerCase();
    let statusFiltered = normalizedRules.filter((rule) => (showDeleted ? rule.isDeleted : !rule.isDeleted));

    if (!showDeleted && filterType !== 'ALL') {
      if (filterType === 'UNCOVERED') {
        statusFiltered = statusFiltered.filter((r) => (r.coveragePercent ?? r.effectiveCoveragePercent) === 0);
      } else if (filterType === 'LINKED') {
        statusFiltered = statusFiltered.filter((r) => r.isLinked);
      } else if (filterType === 'UNLINKED') {
        statusFiltered = statusFiltered.filter((r) => !r.isLinked);
      } else if (filterType === 'PRE_APPROVAL') {
        statusFiltered = statusFiltered.filter((r) => r.requiresPreApproval === true);
      }
    }

    const filtered = !query ? statusFiltered : statusFiltered.filter((rule) => rule.searchable.includes(query));

    // Default ordering: keep visual order stable unless user explicitly sorts.
    if (!sortBy) {
      const modeKey = showDeleted ? 'deleted' : 'active';
      const previousOrder = defaultOrderRef.current[modeKey] || [];
      const currentIds = filtered.map((rule) => rule.id);
      const currentIdSet = new Set(currentIds);

      const nextOrder = [...previousOrder.filter((id) => currentIdSet.has(id)), ...currentIds.filter((id) => !previousOrder.includes(id))];

      defaultOrderRef.current[modeKey] = nextOrder;
      const rank = new Map(nextOrder.map((id, index) => [id, index]));

      return [...filtered].sort((a, b) => (rank.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.id) ?? Number.MAX_SAFE_INTEGER));
    }

    return [...filtered].sort((a, b) => {
      let aVal = a[sortBy];
      let bVal = b[sortBy];

      // handle nulls
      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      // numeric fields
      if (['coveragePercent', 'daysLimit'].includes(sortBy)) {
        aVal = Number(aVal);
        bVal = Number(bVal);
        return sortDirection === 'asc' ? aVal - bVal : bVal - aVal;
      }

      // string fields
      const cmp = String(aVal).localeCompare(String(bVal), 'ar');
      return sortDirection === 'asc' ? cmp : -cmp;
    });
  }, [normalizedRules, ruleSearch, sortBy, sortDirection, showDeleted, filterType]);

  const pagedRules = useMemo(
    () => filteredRules.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage),
    [filteredRules, page, rowsPerPage]
  );

  const activeRulesCount = useMemo(() => normalizedRules.filter((rule) => !rule.isDeleted && rule.isActive).length, [normalizedRules]);
  const deletedRulesCount = useMemo(() => normalizedRules.filter((rule) => rule.isDeleted).length, [normalizedRules]);

  const categoryRulesByCategoryId = useMemo(() => {
    const map = new Map();
    normalizedRules
      .filter((rule) => rule.ruleType === 'CATEGORY' && !rule.isDeleted)
      .forEach((rule) => {
        if (!map.has(rule.medicalCategoryId)) {
          map.set(rule.medicalCategoryId, rule);
        }
      });
    return map;
  }, [normalizedRules]);

  // Count of service-level rules per category (for badge)
  const serviceRulesCountByCategoryId = useMemo(() => {
    const map = new Map();
    normalizedRules
      .filter((rule) => rule.ruleType === 'SERVICE' && rule.medicalCategoryId && !rule.isDeleted)
      .forEach((rule) => {
        map.set(rule.medicalCategoryId, (map.get(rule.medicalCategoryId) || 0) + 1);
      });
    return map;
  }, [normalizedRules]);

  const categoriesCoverageRows = useMemo(() => {
    // فرعية فقط (parentId موجود) وبدون تكرار
    const seen = new Set();
    const subcategories = categories.filter((cat) => {
      if (!cat.parentId) return false;
      if (seen.has(cat.id)) return false;
      seen.add(cat.id);
      return true;
    });
    return subcategories.map((category) => {
      const existingRule = categoryRulesByCategoryId.get(category.id);
      const existingCoveragePercent = existingRule?.coveragePercent;
      const coverageInputValue =
        categoryCoverageInputs[category.id]?.coveragePercent !== undefined
          ? categoryCoverageInputs[category.id].coveragePercent
          : existingCoveragePercent !== null && existingCoveragePercent !== undefined
            ? String(existingCoveragePercent)
            : '';

      return {
        category,
        existingRule,
        coverageInputValue,
        effectiveCoveragePercent: existingRule?.effectiveCoveragePercent ?? existingCoveragePercent ?? null,
        serviceRulesCount: serviceRulesCountByCategoryId.get(category.id) || 0
      };
    });
  }, [categories, categoryRulesByCategoryId, serviceRulesCountByCategoryId, categoryCoverageInputs, policyDefaultCoveragePercent]);

  const handleCoverageInputChange = useCallback((categoryId, field, value) => {
    setCategoryCoverageInputs((prev) => ({
      ...prev,
      [categoryId]: {
        ...prev[categoryId],
        [field]: value
      }
    }));
  }, []);

  const saveCategoryCoverage = useCallback(
    (row) => {
      const rawCoverage = (row.coverageInputValue ?? '').trim();

      if (rawCoverage === '') {
        enqueueSnackbar('يجب تحديد نسبة التغطية من 0 إلى 100', { variant: 'warning' });
        return;
      }

      const coveragePercent = rawCoverage !== '' ? Number(rawCoverage) : null;
      if (coveragePercent !== null && (Number.isNaN(coveragePercent) || coveragePercent < 0 || coveragePercent > 100)) {
        enqueueSnackbar('نسبة التغطية يجب أن تكون بين 0 و 100', { variant: 'warning' });
        return;
      }

      const payload = {
        medicalCategoryId: Number(row.category.id),
        medicalServiceId: null,
        coveragePercent,
        amountLimit: null,
        timesLimit: null,
        waitingPeriodDays: row.existingRule?.waitingPeriodDays ?? 0,
        requiresPreApproval: row.existingRule?.requiresPreApproval ?? false,
        notes: row.existingRule?.notes ?? null
      };

      if (row.existingRule?.id) {
        updateMutation.mutate({ ruleId: row.existingRule.id, payload });
      } else {
        createMutation.mutate(payload);
      }
    },
    [createMutation, enqueueSnackbar, updateMutation]
  );

  const saveAllCategoryCoverage = useCallback(async () => {
    const changedRows = categoriesCoverageRows.filter((row) => categoryCoverageInputs[row.category.id] !== undefined);

    if (changedRows.length === 0) {
      enqueueSnackbar('لا توجد تعديلات جديدة للحفظ', { variant: 'info' });
      return;
    }

    for (const row of changedRows) {
      const rawCoverage = (row.coverageInputValue ?? '').trim();
      const catName = row.category.nameAr || row.category.name || row.category.code;

      if (rawCoverage === '') {
        enqueueSnackbar(`يجب تحديد نسبة التغطية للتصنيف: ${catName}`, {
          variant: 'warning'
        });
        return;
      }

      if (rawCoverage !== '') {
        const cov = Number(rawCoverage);
        if (Number.isNaN(cov) || cov < 0 || cov > 100) {
          enqueueSnackbar(`قيمة التغطية غير صحيحة في التصنيف: ${catName}`, { variant: 'warning' });
          return;
        }
      }
    }

    setBulkSavingCoverage(true);
    try {
      const results = await Promise.allSettled(
        changedRows.map(async (row) => {
          const rawCoverage = (row.coverageInputValue ?? '').trim();

          const coveragePercent = rawCoverage !== '' ? Number(rawCoverage) : null;

          const payload = {
            medicalCategoryId: Number(row.category.id),
            medicalServiceId: null,
            coveragePercent,
            amountLimit: null,
            timesLimit: null,
            waitingPeriodDays: row.existingRule?.waitingPeriodDays ?? 0,
            requiresPreApproval: row.existingRule?.requiresPreApproval ?? false,
            notes: row.existingRule?.notes ?? null
          };

          if (row.existingRule?.id) {
            return updatePolicyRule(policyId, row.existingRule.id, payload);
          } else {
            return createPolicyRule(policyId, payload);
          }
        })
      );

      const succeeded = results.filter((r) => r.status === 'fulfilled').length;
      const failed = results.filter((r) => r.status === 'rejected').length;

      if (succeeded > 0) {
        setCategoryCoverageInputs({});
        await queryClient.invalidateQueries({ queryKey: ['benefit-policy-rules', policyId], exact: true });
      }
      if (failed === 0) {
        enqueueSnackbar(`تم حفظ ${succeeded} تصنيف بنجاح`, { variant: 'success' });
      } else if (succeeded === 0) {
        enqueueSnackbar(`فشل حفظ جميع التصنيفات (${failed})`, { variant: 'error' });
      } else {
        enqueueSnackbar(`تم حفظ ${succeeded} تصنيف، وفشل ${failed} تصنيف`, { variant: 'warning' });
      }
    } finally {
      setBulkSavingCoverage(false);
    }
  }, [categoriesCoverageRows, categoryCoverageInputs, enqueueSnackbar, policyId, queryClient]);

  // ═══════════════════════════════════════════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════════════════════════════════════════

  if (loadingRules) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={200}>
        <CircularProgress color="primary" />
      </Box>
    );
  }

  if (rulesError) {
    return <Alert severity="error">فشل تحميل قواعد التغطية: {rulesError.response?.data?.message || rulesError.message}</Alert>;
  }

  return (
    <>
      {/* ═══════════════════════════════════════════════════════════════════
          قواعد التغطية التفصيلية
      ═══════════════════════════════════════════════════════════════════ */}
      <MainCard
        key={showDeleted ? 'rules-mode-deleted' : 'rules-mode-active'}
        sx={{ minHeight: 'calc(100vh - 310px)', display: 'flex', flexDirection: 'column' }}
        title={
          <Stack direction="row" alignItems="center" spacing={2} flexWrap="wrap">
            <Stack direction="row" alignItems="center" spacing={1}>
              <ServiceIcon sx={{ color: 'primary.main', fontSize: '1.25rem' }} />
              <Typography variant="h5" fontWeight={600} sx={{ mr: 2 }}>
                قواعد التغطية التفصيلية
              </Typography>
            </Stack>
          </Stack>
        }
        secondary={
          canEdit && (
            <Stack direction="row" spacing={1}>
              <Tooltip title="إدارة نسب التغطية حسب التصنيف">
                <IconButton
                  color="primary"
                  onClick={() => setCategoryCoverageModalOpen(true)}
                  sx={{ border: '1px solid', borderColor: 'divider', width: '2.25rem', height: '2.25rem', borderRadius: 1 }}
                >
                  <CategoryIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              
              <Tooltip title={showDeleted ? `عرض النشطة (${activeRulesCount})` : `عرض المحذوفات (${deletedRulesCount})`}>
                <IconButton
                  onClick={() => setShowDeleted((prev) => !prev)}
                  sx={{
                    border: '1px solid',
                    borderColor: showDeleted ? 'error.main' : 'divider',
                    width: '2.25rem',
                    height: '2.25rem',
                    borderRadius: 1,
                    color: showDeleted ? 'error.main' : 'action.active'
                  }}
                >
                  {showDeleted ? <ReplayIcon fontSize="small" /> : <DeleteIcon fontSize="small" />}
                </IconButton>
              </Tooltip>

              <Tooltip title="استيراد الوثيقة وإدارة المجموعات والأوعية والروابط">
                <IconButton
                  color="success"
                  onClick={onOpenStructure}
                  sx={{ border: '1px solid', borderColor: 'divider', width: '2.25rem', height: '2.25rem', borderRadius: 1 }}
                >
                  <LinkIcon fontSize="small" />
                </IconButton>
              </Tooltip>

              <Tooltip title="إضافة قاعدة">
                <IconButton
                  onClick={handleAddRule}
                  sx={{
                    border: '1px solid',
                    borderColor: 'primary.main',
                    bgcolor: 'primary.main',
                    color: 'white',
                    width: '2.25rem',
                    height: '2.25rem',
                    borderRadius: 1,
                    '&:hover': { bgcolor: 'primary.dark' }
                  }}
                >
                  <AddIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          )
        }
      >
        {structureLoadFailed && (
          <Alert severity="warning" sx={{ mb: 1.5 }}>
            تعذر تحميل حالة ربط القواعد بالأوعية؛ أعد المحاولة بعد التأكد من تشغيل الخلفية.
          </Alert>
        )}
        {/* ── Filter bar ── */}
        <Stack direction="row" spacing={1.5} alignItems="center" useFlexGap flexWrap="wrap" sx={{ mb: '1.0rem' }}>
          <Tooltip title="تحديث">
            <IconButton
              size="small"
              onClick={() => refetchRules()}
              color="primary"
              sx={{ border: '1px solid', borderColor: 'divider', width: '2.5rem', height: '2.5rem' }}
            >
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Chip
            size="small"
            label={`${normalizedRules.length} قاعدة`}
            color="primary"
            variant="outlined"
            sx={{ height: '2.5rem', px: 0.5, fontWeight: 600 }}
          />
          <Chip size="small" label={`${activeRulesCount} نشطة`} color="primary" sx={{ height: '2.5rem', px: 0.5, fontWeight: 600 }} />
          <Chip
            size="small"
            label={showDeleted ? `وضع العرض: المحذوفات (${deletedRulesCount})` : 'وضع العرض: النشطة/الموقوفة'}
            color={showDeleted ? 'error' : 'primary'}
            variant={showDeleted ? 'filled' : 'outlined'}
            sx={{ height: '2.5rem', px: 0.5, fontWeight: 600 }}
          />
          <TextField
            placeholder="بحث بالرمز أو الاسم أو النوع..."
            value={ruleSearch}
            onChange={(e) => setRuleSearch(e.target.value)}
            size="small"
            sx={{ flexGrow: 1, maxWidth: 420, '& .MuiOutlinedInput-root': { height: '2.5rem' } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
              ),
              endAdornment: ruleSearch ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setRuleSearch('')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
          {!showDeleted && (
            <Stack direction="row" spacing={1} alignItems="center">
              {[
                { id: 'ALL', label: 'الكل', count: filterStats.all },
                { id: 'UNCOVERED', label: 'غير مغطى', count: filterStats.uncovered },
                { id: 'LINKED', label: 'ضمن مجموعة', count: filterStats.linked },
                { id: 'UNLINKED', label: 'ليست ضمن مجموعة', count: filterStats.unlinked },
                { id: 'PRE_APPROVAL', label: 'موافقة مسبقة', count: filterStats.preApproval }
              ].map((item) => (
                <Chip
                  key={item.id}
                  label={`${item.label} (${item.count})`}
                  color="primary"
                  variant={filterType === item.id ? 'filled' : 'outlined'}
                  onClick={() => {
                    setFilterType(item.id);
                    setPage(0);
                  }}
                  sx={{ fontWeight: 600, cursor: 'pointer', height: '2.5rem', px: 0.5 }}
                />
              ))}
            </Stack>
          )}
        </Stack>

        {/* ── Unified Table ── */}
        <UnifiedMedicalTable
          columns={tableColumns}
          rows={pagedRules}
          loading={false}
          totalCount={filteredRules.length}
          page={page}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[5, 6, 10, 15, 20, 25, 50, 100]}
          onPageChange={(newPage) => setPage(newPage)}
          onRowsPerPageChange={(newSize) => {
            setRowsPerPage(newSize);
            setPage(0);
          }}
          renderCell={renderRuleCell}
          getRowKey={(row) => row.id}
          getRowSx={() => ({})}
          emptyMessage={ruleSearch ? 'لا توجد نتائج مطابقة للبحث' : 'لا توجد قواعد تغطية. استخدم إدارة نسب التصنيفات أو أضف قاعدة جديدة.'}
          hover
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={handleSort}
          tableContainerSx={{ flexGrow: 1 }}
        />
      </MainCard>

      {/* Rule Form Modal */}
      <Dialog open={individualLimitDialog.open} onClose={() => !individualLimitMutation.isPending && setIndividualLimitDialog((prev) => ({ ...prev, open: false }))} maxWidth="sm" fullWidth>
        <DialogTitle>سقف المنفعة الفردي — {individualLimitDialog.rule?.nameAr}</DialogTitle>
        <DialogContent dividers>
          <Alert severity="info" sx={{ mb: 2 }}>اترك جميع الحقول فارغة إذا كانت المنفعة بلا سقف فردي. سقف المجموعة والسقف السنوي سيبقيان مطبقين.</Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth type="number" label="السقف المالي" value={individualLimitDialog.amountLimit} onChange={(e) => setIndividualLimitDialog((prev) => ({ ...prev, amountLimit: e.target.value }))} inputProps={{ min: 0 }} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth type="number" label="حد المرات" value={individualLimitDialog.timesLimit} onChange={(e) => setIndividualLimitDialog((prev) => ({ ...prev, timesLimit: e.target.value }))} inputProps={{ min: 0, step: 1 }} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth type="number" label="حد الأيام" value={individualLimitDialog.daysLimit} onChange={(e) => setIndividualLimitDialog((prev) => ({ ...prev, daysLimit: e.target.value }))} inputProps={{ min: 0, step: 1 }} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth select label="المدة الزمنية" value={individualLimitDialog.periodType} onChange={(e) => setIndividualLimitDialog((prev) => ({ ...prev, periodType: e.target.value }))}>
              <MenuItem value="POLICY_PERIOD">مدة الوثيقة</MenuItem><MenuItem value="ANNUAL">سنوي</MenuItem><MenuItem value="PER_VISIT">لكل زيارة</MenuItem><MenuItem value="LIFETIME">مدى الحياة</MenuItem>
            </TextField></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button disabled={individualLimitMutation.isPending} onClick={() => setIndividualLimitDialog((prev) => ({ ...prev, open: false }))}>إلغاء</Button>
          <Button variant="contained" disabled={individualLimitMutation.isPending} onClick={() => individualLimitMutation.mutate({
            ruleId: individualLimitDialog.rule.id,
            payload: {
              amountLimit: individualLimitDialog.amountLimit === '' ? null : Number(individualLimitDialog.amountLimit),
              timesLimit: individualLimitDialog.timesLimit === '' ? null : Number(individualLimitDialog.timesLimit),
              daysLimit: individualLimitDialog.daysLimit === '' ? null : Number(individualLimitDialog.daysLimit),
              periodType: individualLimitDialog.periodType,
              countingMethod: 'EACH_UNIT'
            }
          })}>حفظ السقف</Button>
        </DialogActions>
      </Dialog>
      <RuleFormModal
        open={formModal.open}
        onClose={handleFormClose}
        onSubmit={handleFormSubmit}
        initialData={formModal.data}
        isEdit={formModal.isEdit}
        loading={createMutation.isPending || updateMutation.isPending}
        categories={categories}
        loadingCategories={loadingCategories}
        policyDefaultCoveragePercent={policyDefaultCoveragePercent}
      />

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={deleteDialog.open}
        ruleName={deleteDialog.rule?.label || deleteDialog.rule?.medicalCategoryName || deleteDialog.rule?.medicalServiceName}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        loading={deleteMutation.isPending || hardDeleteMutation.isPending}
        hardDeleteMode={showDeleted}
      />

      {/* Category Coverage Modal */}
      <CategoryCoverageModal
        open={categoryCoverageModalOpen}
        onClose={() => setCategoryCoverageModalOpen(false)}
        canEdit={canEdit}
        bulkSavingCoverage={bulkSavingCoverage}
        categoriesCoverageRows={categoriesCoverageRows}
        handleCoverageInputChange={handleCoverageInputChange}
        saveCategoryCoverage={saveCategoryCoverage}
        saveAllCategoryCoverage={saveAllCategoryCoverage}
        deleteRule={handleDeleteRule}
        createMutation={createMutation}
        updateMutation={updateMutation}
        isLoading={isLoading}
      />

      {/* ═══════════════════════════════════════════════════════════════════
           Excel Import Dialog
      ═══════════════════════════════════════════════════════════════════ */}
      <Dialog
        open={importDialogOpen}
        onClose={() => {
          if (!importing) {
            setImportDialogOpen(false);
            setImportFile(null);
            setImportResult(null);
            setClearOld(false);
          }
        }}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <FileUploadIcon color="info" />
            <Typography variant="h5">استيراد قواعد التغطية من Excel</Typography>
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <Alert severity="info" sx={{ fontSize: '0.85rem' }}>
              <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>
                تعليمات الاستيراد:
              </Typography>
              <ol style={{ margin: 0, paddingRight: '1.2rem' }}>
                <li>حمّل قالب Excel أولاً بالضغط على «تحميل قالب Excel» أدناه</li>
                <li>عبّئ نسب التغطية والسقوف في الأعمدة القابلة للتعديل</li>
                <li>ارفع الملف هنا — القواعد الجديدة تُضاف والموجودة تُحدَّث تلقائياً</li>
              </ol>
              <Alert severity="warning" sx={{ mt: 1 }}>
                هذه النافذة تقبل قالب <strong>قواعد_التغطية</strong> فقط. ملفات <strong>استيراد_اسم الوثيقة</strong> الخاصة
                بالأوعية تُرفع من تبويب «مجموعات المنافع والسقوف» عبر «فحص الملف».
              </Alert>
              <Box sx={{ mt: 2, display: 'flex', justifyContent: 'flex-start' }}>
                <Button
                  variant="outlined"
                  size="small"
                  color="info"
                  startIcon={downloadingTemplate ? <CircularProgress size={14} color="inherit" /> : <FileDownloadIcon />}
                  onClick={handleDownloadTemplate}
                  disabled={downloadingTemplate}
                >
                  تحميل قالب Excel
                </Button>
              </Box>
            </Alert>

            {/* File selector */}
            <Box
              sx={{
                border: '2px dashed',
                borderColor: importFile ? 'success.main' : 'divider',
                borderRadius: 2,
                p: 2.5,
                textAlign: 'center',
                cursor: 'pointer',
                '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' }
              }}
              onClick={() => importFileInputRef.current?.click()}
            >
              <input
                ref={importFileInputRef}
                type="file"
                accept=".xlsx"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) {
                    setImportFile(f);
                    setImportResult(null);
                  }
                }}
              />
              <FileUploadIcon sx={{ fontSize: '2.5rem', color: importFile ? 'success.main' : 'text.secondary', mb: 1 }} />
              {importFile ? (
                <Typography variant="body2" color="success.main" fontWeight={600}>
                  {importFile.name}
                </Typography>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  اضغط لاختيار ملف Excel (.xlsx)
                </Typography>
              )}
            </Box>

            {!importResult && (
              <Box sx={{ mt: 1 }}>
                <FormControlLabel
                  control={<Checkbox checked={clearOld} onChange={(e) => setClearOld(e.target.checked)} color="error" />}
                  label={
                    <Typography variant="body2" color="error.main" fontWeight="bold">
                      مسح القواعد القديمة والبدء بنظافة
                    </Typography>
                  }
                />
                <Typography variant="caption" color="text.secondary" display="block" sx={{ ml: 4 }}>
                  سيتم مسح جميع قواعد التغطية الحالية للوثيقة، والاعتماد كلياً على القواعد المرفوعة في الملف. (لا يمكن التراجع عن هذا الإجراء)
                </Typography>
              </Box>
            )}

            {/* Import result summary */}
            {importResult && (
              <Alert severity={importResult.success ? 'success' : importResult.summary?.rejected > 0 ? 'warning' : 'error'}>
                <Typography variant="body2" fontWeight={600}>
                  {importResult.messageAr}
                </Typography>
                {importResult.summary && (
                  <Stack direction="row" spacing={2} sx={{ mt: 0.75 }} flexWrap="wrap">
                    {importResult.summary.totalRows > 0 && <Chip size="small" label={`الإجمالي: ${importResult.summary.totalRows}`} />}
                    {importResult.summary.created > 0 && (
                      <Chip size="small" color="success" label={`جديد: ${importResult.summary.created}`} />
                    )}
                    {importResult.summary.updated > 0 && (
                      <Chip size="small" color="info" label={`محدَّث: ${importResult.summary.updated}`} />
                    )}
                    {importResult.summary.rejected > 0 && (
                      <Chip size="small" color="error" label={`مرفوض: ${importResult.summary.rejected}`} />
                    )}
                  </Stack>
                )}
              </Alert>
            )}

            {/* Error list */}
            {importResult?.errors?.length > 0 && (
              <Box sx={{ maxHeight: 340, overflowY: 'auto', border: '1px solid', borderColor: 'error.light', borderRadius: 1 }}>
                <Typography variant="caption" color="error" fontWeight={600} sx={{ display: 'block', mb: 0.5 }}>
                  تفاصيل الأخطاء:
                </Typography>
                <Table size="small" stickyHeader aria-label="تفاصيل أخطاء استيراد قواعد التغطية">
                  <TableHead><TableRow>
                    <TableCell>الصف</TableCell><TableCell>الحقل</TableCell><TableCell>القيمة المرفوضة</TableCell>
                    <TableCell>سبب الرفض</TableCell><TableCell>كيفية المعالجة</TableCell>
                  </TableRow></TableHead>
                  <TableBody>{importResult.errors.map((err, idx) => {
                    const message = err.messageAr || err.errorMessage || err.message || 'خطأ غير موضح من الخادم';
                    const value = err.value ?? err.fieldValue ?? '—';
                    const suggestion = err.errorType === 'LOOKUP_FAILED'
                      ? 'استخدم رمزًا موجودًا في ورقة التصنيفات المرجعية أو في قائمة التصنيفات المعتمدة.'
                      : err.errorType === 'INVALID_FORMAT'
                        ? 'صحّح تنسيق الخلية وفق الوصف، ثم أعد رفع الملف.'
                        : 'راجع الخلية المشار إليها وعدّلها وفق سبب الرفض.';
                    return <TableRow key={`${err.rowNumber}-${err.fieldName}-${idx}`}>
                      <TableCell>{err.rowNumber ?? '—'}</TableCell>
                      <TableCell>{err.fieldName || err.columnName || '—'}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', direction: 'ltr' }}>{value}</TableCell>
                      <TableCell>{message}</TableCell><TableCell>{suggestion}</TableCell>
                    </TableRow>;
                  })}</TableBody>
                </Table>
              </Box>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button
            onClick={() => {
              setImportDialogOpen(false);
              setImportFile(null);
              setImportResult(null);
              setClearOld(false);
            }}
            disabled={importing}
            color="inherit"
          >
            إغلاق
          </Button>
          <Button
            onClick={handleImportExcel}
            variant="contained"
            color="primary"
            disabled={!importFile || importing}
            startIcon={importing ? <CircularProgress size={16} color="inherit" /> : <FileUploadIcon />}
          >
            {importing ? 'جاري الاستيراد...' : 'استيراد'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Apply Template Dialog */}
      <Dialog open={templateDialogOpen} onClose={() => setTemplateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <AutoAwesomeIcon color="primary" />
            <Typography variant="h5">تطبيق قواعد التغطية</Typography>
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          <DialogContentText sx={{ mb: 3 }}>يمكنك تطبيق القواعد من قوالب قياسية أو نسخ القواعد من وثائق شركات أخرى.</DialogContentText>

          {loadingTemplates ? (
            <Box display="flex" justifyContent="center" p={3}>
              <CircularProgress size={30} />
            </Box>
          ) : (
            <Stack spacing={3}>
              <FormControl component="fieldset">
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  المصدر
                </Typography>
                <Stack direction="row" spacing={2}>
                  <Chip
                    label="قالب قياسي"
                    color="primary"
                    variant={sourceType === 'TEMPLATE' ? 'filled' : 'outlined'}
                    onClick={() => {
                      setSourceType('TEMPLATE');
                      setSelectedTemplateId(templates[0]?.id || '');
                    }}
                    sx={{ cursor: 'pointer', flex: 1, height: '36px', fontSize: '1rem' }}
                  />
                  <Chip
                    label="وثيقة شركة أخرى"
                    color="primary"
                    variant={sourceType === 'POLICY' ? 'filled' : 'outlined'}
                    onClick={() => {
                      setSourceType('POLICY');
                      setSelectedTemplateId(policies[0]?.id || '');
                    }}
                    sx={{ cursor: 'pointer', flex: 1, height: '36px', fontSize: '1rem' }}
                  />
                </Stack>
              </FormControl>

              <FormControl fullWidth size="medium">
                <InputLabel id="template-select-label">{sourceType === 'TEMPLATE' ? 'اختر القالب' : 'اختر الوثيقة'}</InputLabel>
                <Select
                  labelId="template-select-label"
                  value={selectedTemplateId}
                  onChange={(e) => setSelectedTemplateId(e.target.value)}
                  label={sourceType === 'TEMPLATE' ? 'اختر القالب' : 'اختر الوثيقة'}
                >
                  {sourceType === 'TEMPLATE' &&
                    templates.map((tpl) => (
                      <MenuItem key={tpl.id} value={tpl.id}>
                        {tpl.name} {tpl.isDefault ? '(افتراضي)' : ''}
                      </MenuItem>
                    ))}
                  {sourceType === 'POLICY' &&
                    policies.map((pol) => (
                      <MenuItem key={pol.id} value={pol.id}>
                        {pol.label}
                      </MenuItem>
                    ))}
                  {(sourceType === 'TEMPLATE' ? templates : policies).length === 0 && (
                    <MenuItem disabled value="">
                      لا توجد بيانات متاحة
                    </MenuItem>
                  )}
                </Select>
              </FormControl>

              <FormControl component="fieldset">
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  طريقة التطبيق
                </Typography>
                <Stack direction="row" spacing={2}>
                  <Chip
                    label="تحديث (إضافة وتعديل المتشابه)"
                    color="success"
                    variant={applyMode === 'UPDATE' ? 'filled' : 'outlined'}
                    onClick={() => {
                      setApplyMode('UPDATE');
                      setConfirmText('');
                    }}
                    sx={{ cursor: 'pointer', flex: 1, height: '36px' }}
                  />
                  <Chip
                    label="استبدال شامل لكافة القواعد"
                    color="error"
                    variant={applyMode === 'REPLACE' ? 'filled' : 'outlined'}
                    onClick={() => setApplyMode('REPLACE')}
                    sx={{ cursor: 'pointer', flex: 1, height: '36px' }}
                  />
                </Stack>
              </FormControl>

              {rules.length > 0 && applyMode === 'REPLACE' && (
                <Alert severity="error">
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    هذا الخيار سيقوم بمسح كافة القواعد الموجودة مسبقاً. لتأكيد الاستبدال، يرجى كتابة عبارة{' '}
                    <strong>"استبدال القواعد"</strong>:
                  </Typography>
                  <TextField
                    fullWidth
                    size="small"
                    placeholder="استبدال القواعد"
                    value={confirmText}
                    onChange={(e) => setConfirmText(e.target.value)}
                    color="error"
                    sx={{ bgcolor: 'background.paper', borderRadius: 1 }}
                  />
                </Alert>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2, pt: 1 }}>
          <Button onClick={() => setTemplateDialogOpen(false)} disabled={applyingTemplate} color="inherit">
            إلغاء
          </Button>
          <Button
            onClick={handleApplyTemplate}
            variant="contained"
            color="primary"
            disabled={
              applyingTemplate || !selectedTemplateId || (rules.length > 0 && applyMode === 'REPLACE' && confirmText !== 'استبدال القواعد')
            }
            startIcon={applyingTemplate ? <CircularProgress size={16} /> : <AutoAwesomeIcon />}
          >
            {applyingTemplate ? 'جاري التطبيق...' : 'تطبيق'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

BenefitPolicyRulesTab.propTypes = {
  policyId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  policyStatus: PropTypes.string,
  policyDefaultCoveragePercent: PropTypes.number,
  onOpenStructure: PropTypes.func.isRequired
};

export default BenefitPolicyRulesTab;
